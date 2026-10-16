package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtParameter

/**
 * Enforces [contracts/ARCHITECTURE_RULES.md] §4.3 — presentation data classes must not embed domain types.
 */
class ForbiddenDomainTypeInUiState(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenDomainTypeInUiState",
            severity = Severity.CodeSmell,
            description =
                "Presentation *UiState / *ViewState data classes must not embed domain types. " +
                    "Map domain → presentation in the ViewModel or mapper boundary.",
            debt = Debt.FIVE_MINS,
        )

    private var domainImportedTypes: Set<String> = emptySet()
    private var fileIsPresentationState: Boolean = false

    override fun visitKtFile(file: KtFile) {
        domainImportedTypes = emptySet()
        val path = file.virtualFilePath ?: ""
        fileIsPresentationState =
            EnclavePathFilters.isFeatureModule(path) &&
                (path.endsWith(UI_STATE_FILE_SUFFIX) || path.endsWith(VIEW_STATE_FILE_SUFFIX))
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        domainImportedTypes =
            DomainTypeReference.collectDomainImportedTypes(importDirective, domainImportedTypes)
    }

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (!fileIsPresentationState || domainImportedTypes.isEmpty()) {
            return
        }
        if (!klass.hasModifier(KtTokens.DATA_KEYWORD)) {
            return
        }

        val className = klass.name ?: return
        klass.primaryConstructorParameters.forEach { parameter ->
            val typeText = parameter.typeReference?.text ?: return@forEach
            if (DomainTypeReference.isAllowedType(typeText)) {
                return@forEach
            }
            if (!DomainTypeReference.typeReferencesDomain(typeText, domainImportedTypes)) {
                return@forEach
            }

            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(parameter),
                    message =
                        "Presentation class `$className` must not expose domain type `$typeText`. " +
                            "Map at the ViewModel/mapper boundary. See contracts/ARCHITECTURE_RULES.md §4.3.",
                ),
            )
        }
    }

    companion object {
        private const val UI_STATE_FILE_SUFFIX = "UiState.kt"
        private const val VIEW_STATE_FILE_SUFFIX = "ViewState.kt"
    }
}
