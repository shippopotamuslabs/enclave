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
 * Enforces [contracts/ARCHITECTURE_RULES.md] §4.4 — ViewModels depend on use cases, not ports.
 */
class ForbiddenPortInViewModel(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenPortInViewModel",
            severity = Severity.CodeSmell,
            description =
                "ViewModels must not take *Port constructor parameters. " +
                    "Inject *UseCase from :core:domain instead.",
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
            if (!typeText.endsWith(PORT_SUFFIX)) {
                return@forEach
            }
            // Exempt infrastructure ports (ENC-22): logging is cross-cutting
            // telemetry, and the download orchestrator is the assisted,
            // session-scoped runtime its ViewModel exists to drive (ENC-21).
            // Neither is a repository/data-source port.
            if (typeText in EXEMPT_PORTS) {
                return@forEach
            }

            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(parameter),
                    message =
                        "ViewModel `$className` must not depend on `$typeText`. " +
                            "Inject a *UseCase from :core:domain instead. See contracts/ARCHITECTURE_RULES.md §4.4.",
                ),
            )
        }
    }

    companion object {
        private const val VIEW_MODEL_SUFFIX = "ViewModel"
        private const val PORT_SUFFIX = "Port"

        val EXEMPT_PORTS: Set<String> = setOf("LoggingPort", "ModelDownloadOrchestratorPort")
    }
}
