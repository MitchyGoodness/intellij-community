// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtClassType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicator
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLInspection
import org.jetbrains.kotlin.idea.fir.api.applicator.*
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.search.isPotentiallyOperator
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.idea.inspections.KtInspectionUtils

class KotlinHLReplaceGetOrSetInspection :
    AbstractHLInspection<KtDotQualifiedExpression, KotlinHLReplaceGetOrSetInspection.ReplaceGetOrSetInput>(
        KtDotQualifiedExpression::class
    ) {
    data class ReplaceGetOrSetInput(val calleeName: String) : HLApplicatorInput

    override val presentation: HLPresentation<KtDotQualifiedExpression> = presentation { highlightType(ProblemHighlightType.INFORMATION) }
    override val applicabilityRange: HLApplicabilityRange<KtDotQualifiedExpression> = ApplicabilityRanges.SELF
    override val inputProvider: HLApplicatorInputProvider<KtDotQualifiedExpression, ReplaceGetOrSetInput> = inputProvider {
        val receiver = it.receiverExpression
        if (receiver is KtSuperExpression || (receiver.getKtType() as? KtClassType)?.isUnit != false) return@inputProvider null
        val callExpression = it.callExpression ?: return@inputProvider null
        if (callExpression.resolveCall() !is KtSuccessCallInfo) return@inputProvider null
        if (callExpression.calleeExpression?.references?.all { reference ->
                reference.resolve().isPotentiallyOperator()
            } != true) return@inputProvider null
        val calleeName =
            (callExpression.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName() ?: return@inputProvider null
        if (calleeName == OperatorNameConventions.SET &&
            (callExpression.getKtType() as? KtClassType)?.isUnit != true &&
            it.isExpressionResultValueUsed()
        ) return@inputProvider null
        return@inputProvider ReplaceGetOrSetInput(calleeName.asString())
    }
    override val applicator: HLApplicator<KtDotQualifiedExpression, ReplaceGetOrSetInput> = applicator {
        familyName(KotlinBundle.lazyMessage(("inspection.replace.get.or.set.display.name")))
        actionName { _, input ->
            KotlinBundle.message("replace.0.call.with.indexing.operator", input.calleeName)
        }
        isApplicableByPsi { expression -> KtInspectionUtils.isTargetOfReplaceGetOrSetInspection(expression) }
        applyTo { expression, input, _, editor ->
            KtInspectionUtils.replaceGetOrSetWithPropertyAccessor(
                expression,
                input.calleeName == OperatorNameConventions.SET.identifier,
                editor
            )
        }
    }

    private fun KtExpression.isExpressionResultValueUsed(): Boolean {
        return when (parent) {
            is KtOperationExpression,
            is KtCallExpression,
            is KtReferenceExpression,
            is KtVariableLikeSymbol,
            is KtReturnExpression,
            is KtThrowExpression -> {
                true
            }
            is KtIfExpression,
            is KtWhenExpression,
            is KtLoopExpression,
            is KtDotQualifiedExpression -> {
                (parent as KtExpression).isExpressionResultValueUsed()
            }
            else -> false
        }
    }
}