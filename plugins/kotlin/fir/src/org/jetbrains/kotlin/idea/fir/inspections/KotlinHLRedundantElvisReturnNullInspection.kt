// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicator
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLInspection
import org.jetbrains.kotlin.idea.fir.api.applicator.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinHLRedundantElvisReturnNullInspection :
    AbstractHLInspection<KtBinaryExpression, KotlinHLRedundantElvisReturnNullInspection.RedundantElvisReturnNullInspectionInput>(
        KtBinaryExpression::class
    ) {
    class RedundantElvisReturnNullInspectionInput : HLApplicatorInput

    override val presentation: HLPresentation<KtBinaryExpression> = presentation { highlightType(ProblemHighlightType.INFORMATION) }
    override val applicabilityRange: HLApplicabilityRange<KtBinaryExpression> = applicabilityRanges { binaryExpression ->
        val right =
            binaryExpression.right?.let { expression -> KtPsiUtil.safeDeparenthesize(expression) }?.takeIf { it == binaryExpression.right }
                ?: return@applicabilityRanges emptyList()
        listOf(TextRange(binaryExpression.operationReference.startOffset, right.endOffset).shiftLeft(binaryExpression.startOffset))
    }
    override val inputProvider: HLApplicatorInputProvider<KtBinaryExpression, RedundantElvisReturnNullInspectionInput> =
        inputProvider { binaryExpression ->
            // Returns null if LHS of the binary expression is not nullable.
            if (binaryExpression.left?.getKtType()?.isMarkedNullable != true) return@inputProvider null
            return@inputProvider RedundantElvisReturnNullInspectionInput()
        }
    override val applicator: HLApplicator<KtBinaryExpression, RedundantElvisReturnNullInspectionInput> =
        applicator {
            familyName(KotlinBundle.lazyMessage(("remove.redundant.elvis.return.null.text")))
            actionName(KotlinBundle.lazyMessage(("inspection.redundant.elvis.return.null.descriptor")))
            isApplicableByPsi {
                // The binary expression must be in a form of "return <left expression> ?: return null".
                val returnExpression = it.right as? KtReturnExpression ?: return@isApplicableByPsi false

                // Returns false if RHS is not "return null".
                val dparenthesizedReturnExpression =
                    returnExpression.returnedExpression?.let { expression -> KtPsiUtil.safeDeparenthesize(expression) }
                if ((dparenthesizedReturnExpression as? KtConstantExpression)?.text != KtTokens.NULL_KEYWORD.value) return@isApplicableByPsi false

                // Returns whether the parent of this binary expression is a return expression and the binary operator is elvis or not.
                it == it.getStrictParentOfType<KtReturnExpression>()?.returnedExpression?.let { expression ->
                    KtPsiUtil.safeDeparenthesize(
                        expression
                    )
                } && it.operationToken == KtTokens.ELVIS
            }
            applyTo { binaryExpression, _ ->
                val left = binaryExpression.left ?: return@applyTo
                binaryExpression.replace(left)
            }
        }
}