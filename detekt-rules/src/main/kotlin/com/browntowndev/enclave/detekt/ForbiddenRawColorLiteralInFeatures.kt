package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Enforces [contracts/UI_DESIGN_SPEC.md] §3 — semantic colors via EnclaveTheme, not hex literals
 * in feature/app UI code.
 */
class ForbiddenRawColorLiteralInFeatures(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenRawColorLiteralInFeatures",
            severity = Severity.CodeSmell,
            description =
                "Feature and app modules must not use raw Color(0x…) literals. " +
                    "Use EnclaveTheme.colors semantic roles from :core:ui/theme.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val path = expression.containingKtFile.virtualFilePath ?: return
        if (!EnclavePathFilters.isEnforced(path)) {
            return
        }

        val callee = expression.calleeExpression?.text ?: return
        if (callee != "Color") {
            return
        }

        val firstArg = expression.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
        if (!HEX_LITERAL.matches(firstArg)) {
            return
        }

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message =
                    "Raw color literal `$callee($firstArg)` in enforced module. " +
                        "Use EnclaveTheme.colors.* — see contracts/UI_DESIGN_SPEC.md §3.",
            ),
        )
    }

    companion object {
        private val HEX_LITERAL = Regex("""0[xX][0-9a-fA-F]{6,8}""")
    }
}
