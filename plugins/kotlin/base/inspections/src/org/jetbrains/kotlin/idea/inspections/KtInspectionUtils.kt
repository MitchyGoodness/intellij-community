// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.psi.PsiElement
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.util.OperatorNameConventions

class KtInspectionUtils {
    companion object {
        private val operatorNames = setOf(OperatorNameConventions.GET, OperatorNameConventions.SET)

        fun isTargetOfReplaceGetOrSetInspection(expression: KtDotQualifiedExpression): Boolean {
            val callExpression = (expression.selectorExpression as? KtCallExpression) ?: return false
            val calleeName = (callExpression.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName()
            if (calleeName !in operatorNames) return false
            if (callExpression.typeArgumentList != null) return false
            val arguments = callExpression.valueArguments
            if (arguments.isEmpty()) return false
            if (arguments.any { it.isNamed() || it.isSpread }) return false
            return true
        }

        fun replaceGetOrSetWithPropertyAccessor(expression: KtDotQualifiedExpression, isSet: Boolean, editor: Editor?) {
            val callExpression = (expression.selectorExpression as? KtCallExpression) ?: return
            val allArguments = callExpression.valueArguments
            assert(allArguments.isNotEmpty())

            val newExpression = KtPsiFactory(expression).buildExpression {
                appendExpression(expression.receiverExpression)

                appendFixedText("[")

                val arguments = if (isSet) allArguments.dropLast(1) else allArguments
                appendExpressions(arguments.map { it.getArgumentExpression() })

                appendFixedText("]")

                if (isSet) {
                    appendFixedText("=")
                    appendExpression(allArguments.last().getArgumentExpression())
                }
            }

            val newElement = expression.replace(newExpression)

            if (editor != null) {
                moveCaret(editor, isSet, newElement)
            }
        }

        private fun moveCaret(editor: Editor, isSet: Boolean, newElement: PsiElement) {
            val arrayAccessExpression = if (isSet) {
                newElement.getChildOfType()
            } else {
                newElement as? KtArrayAccessExpression
            } ?: return

            arrayAccessExpression.leftBracket?.startOffset?.let { editor.caretModel.moveToOffset(it) }
        }
    }
}