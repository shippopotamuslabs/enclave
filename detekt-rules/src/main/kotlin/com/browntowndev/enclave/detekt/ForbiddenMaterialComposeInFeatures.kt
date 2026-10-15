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
 * Enforces [contracts/UI_DESIGN_SPEC.md] §4 — feature and app modules must use Enclave*
 * components, not raw Material Compose imports.
 */
class ForbiddenMaterialComposeInFeatures(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenMaterialComposeInFeatures",
            severity = Severity.CodeSmell,
            description =
                "Feature and app modules must not import raw Material Compose UI APIs. " +
                    "Use EnclaveTheme and Enclave* components from :core:ui instead.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val path = importDirective.containingKtFile.virtualFilePath ?: return
        if (!EnclavePathFilters.isEnforced(path)) {
            return
        }

        val importPath = importDirective.importPath?.pathStr ?: return
        if (importPath !in FORBIDDEN_IMPORTS) {
            return
        }

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(importDirective),
                message =
                    "Forbidden Material import `$importPath` in enforced module. " +
                        "See contracts/UI_DESIGN_SPEC.md §4.",
            ),
        )
    }

    companion object {
        val FORBIDDEN_IMPORTS: Set<String> =
            setOf(
                // Material 3 — theme
                "androidx.compose.material3.MaterialTheme",
                // Material 3 — surfaces & inputs
                "androidx.compose.material3.Button",
                "androidx.compose.material3.Card",
                "androidx.compose.material3.ElevatedButton",
                "androidx.compose.material3.ElevatedCard",
                "androidx.compose.material3.FilledTonalButton",
                "androidx.compose.material3.FilledTonalIconButton",
                "androidx.compose.material3.IconButton",
                "androidx.compose.material3.OutlinedButton",
                "androidx.compose.material3.OutlinedCard",
                "androidx.compose.material3.OutlinedIconButton",
                "androidx.compose.material3.OutlinedTextField",
                "androidx.compose.material3.Surface",
                "androidx.compose.material3.TextButton",
                "androidx.compose.material3.TextField",
                "androidx.compose.material3.TopAppBar",
                "androidx.compose.material3.CenterAlignedTopAppBar",
                "androidx.compose.material3.SmallTopAppBar",
                "androidx.compose.material3.MediumTopAppBar",
                "androidx.compose.material3.LargeTopAppBar",
                "androidx.compose.material3.Scaffold",
                "androidx.compose.material3.FilterChip",
                "androidx.compose.material3.InputChip",
                "androidx.compose.material3.SuggestionChip",
                "androidx.compose.material3.AssistChip",
                "androidx.compose.material3.ModalBottomSheet",
                "androidx.compose.material3.BottomSheetScaffold",
                // Material 2 (legacy)
                "androidx.compose.material.Button",
                "androidx.compose.material.Card",
                "androidx.compose.material.IconButton",
                "androidx.compose.material.OutlinedTextField",
                "androidx.compose.material.Surface",
                "androidx.compose.material.TextField",
                "androidx.compose.material.TopAppBar",
            )
    }
}
