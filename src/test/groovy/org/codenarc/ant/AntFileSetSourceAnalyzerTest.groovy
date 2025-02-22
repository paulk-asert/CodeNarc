/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codenarc.ant

import static org.codenarc.test.TestUtil.shouldFail
import static org.codenarc.test.TestUtil.shouldFailWithMessageContaining

import org.apache.tools.ant.Project
import org.apache.tools.ant.types.FileSet
import org.codenarc.analyzer.AnalyzerException
import org.codenarc.results.FileResults
import org.codenarc.results.Results
import org.codenarc.rule.FakeCountRule
import org.codenarc.rule.FakePathRule
import org.codenarc.rule.StubRule
import org.codenarc.ruleset.ListRuleSet
import org.codenarc.test.AbstractTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for AntFileSetSourceAnalyzer
 *
 * @author Chris Mair
 */
class AntFileSetSourceAnalyzerTest extends AbstractTestCase {

    private static final BASE_DIR = 'src/test/resources'
    private Project project
    private FileSet fileSet
    private ruleSet

    @Test
    void test_Constructor_NullFileSet() {
        shouldFailWithMessageContaining('fileSet') { new AntFileSetSourceAnalyzer(project, (FileSet)null) }
    }

    @Test
    void test_Constructor_NullListOfFileSets() {
        shouldFailWithMessageContaining('fileSet') { new AntFileSetSourceAnalyzer(project, (List)null) }
    }

    @Test
    void test_Constructor_NullProject() {
        shouldFailWithMessageContaining('project') { new AntFileSetSourceAnalyzer(null, fileSet) }
    }

    @Test
    void test_analyze_SimpleDirectory() {
        fileSet.includes = 'source/**/*.groovy'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        def results = analyzer.analyze(ruleSet)
        def sourceFilePaths = results.violations*.message
        assert sourceFilePaths == [
                'src/test/resources/source/SourceFile1.groovy',
                'src/test/resources/source/SourceFile2.groovy' ]
        assertResultsCounts(results, 2, 2)

        assert getAllResultsPaths(results) == [
                'source',
                'source/SourceFile1.groovy',
                'source/SourceFile2.groovy']
    }

    @Test
    void test_analyze_NestedSubdirectories() {
        fileSet.includes = 'sourcewithdirs/**/*.groovy'
        fileSet.excludes = '**/*File2.groovy'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        def results = analyzer.analyze(ruleSet)
        def sourceFilePaths = results.violations*.message
        final EXPECTED_PATHS = [
                'src/test/resources/sourcewithdirs/SourceFile1.groovy',
                'src/test/resources/sourcewithdirs/subdir1/Subdir1File1.groovy',
                //'src/test/resources/sourcewithdirs/subdir1/Subdir1File2.groovy', -- EXCLUDED
                'src/test/resources/sourcewithdirs/subdir2/Subdir2File1.groovy',
                'src/test/resources/sourcewithdirs/subdir2/subdir2a/Subdir2aFile1.groovy'
        ]
        assert sourceFilePaths == EXPECTED_PATHS
        assertResultsCounts(results, 4, 4)

        final ALL_RESULTS_PATHS = [
                'sourcewithdirs',
                'sourcewithdirs/SourceFile1.groovy',
                'sourcewithdirs/subdir1',
                'sourcewithdirs/subdir1/Subdir1File1.groovy',
                'sourcewithdirs/subdir2',
                'sourcewithdirs/subdir2/Subdir2File1.groovy',
                'sourcewithdirs/subdir2/subdir2a',
                'sourcewithdirs/subdir2/subdir2a/Subdir2aFile1.groovy'
        ]
        assert getAllResultsPaths(results) == ALL_RESULTS_PATHS
        assertResultsCounts(results.findResultsForPath('sourcewithdirs/subdir1'), 1, 1)
        assertResultsCounts(results.findResultsForPath('sourcewithdirs/subdir2/subdir2a'), 1, 1)
    }

    @Test
    void test_analyze_NestedSubdirectories_NoViolations() {
        ruleSet = new ListRuleSet([new FakeCountRule()])
        fileSet.includes = 'sourcewithdirs/**/*.groovy'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        def results = analyzer.analyze(ruleSet)

        assertResultsCounts(results, 5, 0)
        assertResultsCounts(results.findResultsForPath('sourcewithdirs/subdir1'), 2, 0)
        assertResultsCounts(results.findResultsForPath('sourcewithdirs/subdir2'), 2, 0)
        assertResultsCounts(results.findResultsForPath('sourcewithdirs/subdir2/subdir2a'), 1, 0)
    }

    @Test
    void test_analyze_MultipleFileSets() {
        final DIR1 = 'src/test/resources/sourcewithdirs/subdir1'
        final DIR2 = 'src/test/resources/sourcewithdirs/subdir2'
        final GROOVY_FILES = '**/*.groovy'
        def fileSet1 = new FileSet(dir:new File(DIR1), project:project, includes:GROOVY_FILES)
        def fileSet2 = new FileSet(dir:new File(DIR2), project:project, includes:GROOVY_FILES)

        def analyzer = new AntFileSetSourceAnalyzer(project, [fileSet1, fileSet2])
        def results = analyzer.analyze(ruleSet)
        def sourceFilePaths = results.violations*.message
        log("sourceFilePaths=$sourceFilePaths")
        final EXPECTED_PATHS = [
                'src/test/resources/sourcewithdirs/subdir1/Subdir1File1.groovy',
                'src/test/resources/sourcewithdirs/subdir1/Subdir1File2.groovy',
                'src/test/resources/sourcewithdirs/subdir2/Subdir2File1.groovy',
                'src/test/resources/sourcewithdirs/subdir2/subdir2a/Subdir2aFile1.groovy'
        ]
        assert sourceFilePaths == EXPECTED_PATHS
        assertResultsCounts(results, 4, 4)
    }

    @Test
    void test_analyze_SourceFilesWithCompileErrors() {
        ruleSet = new ListRuleSet([new FakeCountRule()])
        fileSet.includes = 'sourcewitherrors/**/*.txt'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        def results = analyzer.analyze(ruleSet)
        log(results)
        assertResultsCounts(results, 2, 0)

        assert getAllResultsPaths(results) == ['sourcewitherrors']
    }

    @Test
    void test_analyze_SourceFilesWithCompileErrors_failOnError_true() {
        ruleSet = new ListRuleSet([new FakeCountRule()])
        fileSet.includes = 'sourcewitherrors/**/*.txt'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        analyzer.failOnError = true

        def msg = shouldFail(AnalyzerException) { analyzer.analyze(ruleSet) }
        log(msg)
    }

    @Test
    void test_analyze_EmptyFileSet() {
        fileSet.excludes = '**/*'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        def results = analyzer.analyze(ruleSet)
        assertResultsCounts(results, 0, 0)
    }

    @Test
    void test_analyze_RuleThrowsNullPointerException() {
        def rule = new StubRule(applyToClosure:{ sourceCode, violations -> throw new NullPointerException() })
        ruleSet = new ListRuleSet([rule])
        fileSet.includes = 'source/**/*.groovy'
        def analyzer = new AntFileSetSourceAnalyzer(project, fileSet)
        def results = analyzer.analyze(ruleSet)
        assert results.violations.empty
    }

    @Test
    void test_getSourceDirectories_ReturnsEmptyListForNoFileSets() {
        def analyzer = new AntFileSetSourceAnalyzer(project, [])
        assert analyzer.sourceDirectories == []
    }

    @Test
    void test_getSourceDirectories_ReturnsSingleDirectoryForSingleFileSet() {
        def analyzer = new AntFileSetSourceAnalyzer(project, [fileSet])
        assert analyzer.sourceDirectories == [normalizedPath(BASE_DIR)]
    }

    @Test
    void test_getSourceDirectories_ReturnsDirectoryForEachFileSet() {
        def fileSet1 = new FileSet(dir:new File('abc'), project:project)
        def fileSet2 = new FileSet(dir:new File('def'), project:project)
        def analyzer = new AntFileSetSourceAnalyzer(project, [fileSet1, fileSet2])
        log("sourceDirectories=${analyzer.sourceDirectories}")
        assert analyzer.sourceDirectories == [normalizedPath('abc'), normalizedPath('def')]
    }

    @Test
    void test_getSourceDirectories_ReturnsDirectoryRelativeToBaseDirectory() {
        def currentDir = new File('').absolutePath
        project = new Project(basedir:currentDir)
        fileSet.setProject(project)
        fileSet.dir = new File(currentDir + '/src/main/groovy')
        def analyzer = new AntFileSetSourceAnalyzer(project, [fileSet])
        log("analyzer.sourceDirectories=${analyzer.sourceDirectories}")
        assert analyzer.sourceDirectories == [normalizedPath('src/main/groovy')]
    }

    @BeforeEach
    void before() {
        project = new Project()
        project.basedir = '.'

        fileSet = new FileSet(dir:new File(BASE_DIR), project:project)

        ruleSet = new ListRuleSet([new FakePathRule()])
    }

    private String normalizedPath(String path) {
        new File(path).path
    }

    private void assertResultsCounts(Results results, int totalFiles, int filesWithViolations) {
        assert results.totalNumberOfFiles == totalFiles, "results.totalNumberOfFiles=${results.totalNumberOfFiles}"
        assert results.getNumberOfFilesWithViolations(3) == filesWithViolations, "results.getNumberOfFilesWithViolations(3)=${results.getNumberOfFilesWithViolations(3)}"
    }

    private List getAllResultsPaths(Results results) {
        def paths = []
        resultsPaths(results, paths)
        log("allResultsPaths=$paths")
        paths
    }

    private void resultsPaths(Results results, List paths) {
        if (results.path) {
            paths << results.path
            if (results instanceof FileResults) {
                assert results.sourceCode
            }
        }
        results.children.each { child -> resultsPaths(child, paths) }
    }

}
