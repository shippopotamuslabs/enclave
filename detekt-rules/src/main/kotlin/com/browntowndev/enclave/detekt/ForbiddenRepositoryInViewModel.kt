package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass

/**
 * Enforces [contracts/ARCHITECTURE_RULES.md] — ViewModels depend on use cases, not repositories.
 */
class ForbiddenRepositoryInViewModel(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenRepositoryInViewModel",
            severity = Severity.CodeSmell,
            description =
                "ViewModels must not take *Repository constructor parameters. " +
                    "Inject use cases from :core:domain instead.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val path = klass.containingKtFile.virtualFilePath ?: return
        if (!EnclavePathFilters.isEnforced(path)) {
            return
        }

        val className = klass.name ?: return
        if (!className.endsWith(VIEW_MODEL_SUFFIX)) {
            return
        }

        val constructor = klass.primaryConstructor ?: return
        constructor.valueParameters.forEach { parameter ->
            val typeText = parameter.typeReference?.text ?: return@forEach
            if (!typeText.endsWith(REPOSITORY_SUFFIX)) {
                return@forEach
            }

            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(parameter),
                    message =
                        "ViewModel `$className` must not depend on `$typeText`. " +
                            "Inject a use case from :core:domain. See contracts/ARCHITECTURE_RULES.md.",
                ),
            )
        }
    }

    companion object {
        private const val VIEW_MODEL_SUFFIX = "ViewModel"
        private const val REPOSITORY_SUFFIX = "Repository"
    }
}
