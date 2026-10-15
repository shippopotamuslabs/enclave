package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class EnclaveRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = RULE_SET_ID

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                ForbiddenMaterialComposeInFeatures(config),
                ForbiddenRawColorLiteralInFeatures(config),
                ForbiddenMockKInTests(config),
                ForbiddenVerifyInViewModelTests(config),
                ForbiddenRepositoryInViewModel(config),
                ForbiddenPortInViewModel(config),
                ForbiddenDomainTypeInComposable(config),
                ForbiddenDomainTypeInUiState(config),
                ForbiddenUseCaseInFeatureMapper(config),
            ),
        )

    companion object {
        const val RULE_SET_ID = "enclave"
    }
}
