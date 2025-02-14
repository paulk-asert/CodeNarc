/*
 * Copyright 2020 the original author or authors.
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
package org.codenarc.rule.formatting

import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.Violation

/**
 * Checks that there is no whitespace at the end of the method name when a method call contains parenthesis or that
 * there is at most one space after the method name if the call does not contain parenthesis
 */
class SpaceAfterMethodCallNameRule extends AbstractAstVisitorRule {

    String name = 'SpaceAfterMethodCallName'
    int priority = 3
    Class astVisitorClass = SpaceAfterMethodCallNameRuleAstVisitor
}

class SpaceAfterMethodCallNameRuleAstVisitor extends AbstractAstVisitor {

    @Override
    void visitConstructorCallExpression(ConstructorCallExpression call) {
        if (isFirstVisit(call) && isNotGeneratedCode(call)) {
            if (call.superCall) {
                String superCallSourceText = sourceLine(call).substring(call.columnNumber, call.arguments.columnNumber)
                if (superCallSourceText.contains(' (')) {
                    addViolation(call, 'There is whitespace between super and parenthesis in a constructor call.')
                }
            } else {
                if (call.lineNumber >= 0 && hasPrecedingWhitespace(call)) {
                    addViolation(call, 'There is whitespace between class name and parenthesis in a constructor call.')
                }
            }
        }
        super.visitConstructorCallExpression(call)
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        def method = call.method
        def arguments = call.arguments
        if (isFirstVisit(call) && method.lineNumber != -1 && method.lineNumber == arguments.lineNumber && !hasSingleLambdaArgument(call)) {
            String methodName = call.methodAsString
            def regex = methodName + /\s+\(/
            def lineNumbers = (method.lineNumber .. method.lastLineNumber)
            for (int lineNumber: lineNumbers) {
                def line = sourceCode.line(lineNumber - 1)
                if (line =~ regex) {
                    def message = 'There is whitespace between the method name and parenthesis in a method call: ' + methodName
                    addViolation(new Violation(rule: rule, lineNumber: lineNumber, sourceLine: line, message: message))
                    break
                }
                else if (line.contains(methodName + '  ')) {
                    def message = 'There is more than one space between the method name and its arguments in a method call: ' + methodName
                    addViolation(new Violation(rule: rule, lineNumber: lineNumber, sourceLine: line, message: message))
                    break
                }
            }
        }

        super.visitMethodCallExpression(call)
    }

    private boolean hasSingleLambdaArgument(MethodCallExpression call) {
        // Can't reliably tell whether opening parentheses are part of lambda or method call parentheses
        def arguments = call.arguments
        return arguments.expressions.size() == 1 && arguments.getExpression(0).getClass().name == 'org.codehaus.groovy.ast.expr.LambdaExpression'
    }

    private boolean hasPrecedingWhitespace(ConstructorCallExpression call) {
        sourceLine(call).substring(call.columnNumber - 1) =~ /^[^(]+\s\(/
    }

}
