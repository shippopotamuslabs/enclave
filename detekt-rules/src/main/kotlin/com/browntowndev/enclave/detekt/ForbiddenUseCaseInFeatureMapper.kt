package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Enforces [contracts/ARCHITECTURE_RULES.md] §4.4 — feature mappers are pure presentation transforms;
 * domain validation belongs in use cases invoked by ViewModels.
 */
class ForbiddenUseCaseInFeatureMapper(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenUseCaseInFeatureMapper",
            severity = Severity.CodeSmell,
            description =
                "Feature-layer mappers must not accept *UseCase parameters. " +
                    "Invoke use cases in the ViewModel and pass results into the mapper.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        val path = function.containingKtFile.virtualFilePath ?: return
        if (!EnclavePathFilters.isFeatureModule(path)) {
            return
        }

        val name = function.name ?: return
        if (!isMapperFunction(name)) {
            return
        }

        function.valueParameters.forEach { parameter ->
            val typeText = parameter.typeReference?.text ?: return@forEach
            if (!typeText.endsWith(USE_CASE_SUFFIX)) {
                return@forEach
            }
            if (!typeReferencesDomainUseCase(typeText)) {
                return@forEach
            }

            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(parameter),
                    message =
                        "Mapper `$name` must not accept use case `$typeText`. " +
                            "Call the use case in the ViewModel and pass derived values. " +
                            "See contracts/ARCHITECTURE_RULES.md §4.4.",
                ),
            )
        }
    }

    private fun isMapperFunction(name: String): Boolean =
        name.endsWith(MAPPING_SUFFIX) ||
            name.endsWith(MAPPER_SUFFIX) ||
            (name.startsWith(BUILD_PREFIX) && name.endsWith(UI_STATE_SUFFIX))

    private fun typeReferencesDomainUseCase(typeText: String): Boolean =
        typeText.startsWith(DomainTypeReference.DOMAIN_PACKAGE_PREFIX) ||
            typeText.substringBefore('<').trim().endsWith(USE_CASE_SUFFIX)

    companion object {
        private const val MAPPING_SUFFIX = "Mapping"
        private const val MAPPER_SUFFIX = "Mapper"
        private const val BUILD_PREFIX = "build"
        private const val UI_STATE_SUFFIX = "UiState"
        private const val USE_CASE_SUFFIX = "UseCase"
    }
}
