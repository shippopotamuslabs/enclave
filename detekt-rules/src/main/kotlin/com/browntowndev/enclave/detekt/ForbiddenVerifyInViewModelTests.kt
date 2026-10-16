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
 * Enforces [contracts/UNIT_TESTING.md] §1 — ViewModel tests assert state, not interactions.
 */
class ForbiddenVerifyInViewModelTests(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenVerifyInViewModelTests",
            severity = Severity.CodeSmell,
            description =
                "ViewModel tests must not use MockK verify/coVerify. Assert StateFlow / UI state instead.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val path = expression.containingKtFile.virtualFilePath ?: return
        if (!TestPathFilters.isViewModelTest(path)) {
            return
        }

        val callee = expression.calleeExpression?.text ?: return
        if (callee !in FORBIDDEN_CALLEES) {
            return
        }

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message =
                    "Forbidden `$callee()` in ViewModel test. Assert resulting state with Turbine. " +
                        "See golden-test-examples/settings/logic_test.kt.",
            ),
        )
    }

    companion object {
        private val FORBIDDEN_CALLEES = setOf("verify", "coVerify", "verifyOrder", "coVerifyOrder")
    }
}
