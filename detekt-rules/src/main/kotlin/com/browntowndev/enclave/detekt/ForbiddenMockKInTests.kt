package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Enforces [contracts/UNIT_TESTING.md] §1 — prefer fakes over MockK for Enclave-owned code.
 * MockK is allowed only with `@file:Suppress("ForbiddenMockKInTests")` and a justification comment.
 */
class ForbiddenMockKInTests(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenMockKInTests",
            severity = Severity.CodeSmell,
            description =
                "Do not import MockK in Enclave tests. Use fakes from :core:testing. " +
                    "MockK is allowed only for third-party SDKs with an explicit suppress + comment.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val path = importDirective.containingKtFile.virtualFilePath ?: return
        if (!TestPathFilters.isTestSource(path)) {
            return
        }

        val importPath = importDirective.importPath?.pathStr ?: return
        if (!importPath.startsWith(MOCKK_PACKAGE_PREFIX)) {
            return
        }

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(importDirective),
                message =
                    "Forbidden MockK import `$importPath`. Use a Fake from :core:testing. " +
                        "See contracts/UNIT_TESTING.md §1 and golden-test-examples/.",
            ),
        )
    }

    companion object {
        private const val MOCKK_PACKAGE_PREFIX = "io.mockk"
    }
}
