package com.browntowndev.enclave.domain.byok

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

class ByokModelOnPresetDomainTest {
    @Test
    fun `SCN-DOMAIN-001 ApiCredentials Has No Model Fields`() {
        val propertyNames = ApiCredentials::class.memberProperties.map { it.name }.toSet()
        assertFalse("modelId" in propertyNames)
        assertFalse("isMultimodal" in propertyNames)
    }

    @Test
    fun `SCN-DOMAIN-002 ApiModelConfiguration Carries Model Fields`() {
        val modelIdProp = ApiModelConfiguration::class.memberProperties.find { it.name == "modelId" }
        val isMultimodalProp = ApiModelConfiguration::class.memberProperties.find { it.name == "isMultimodal" }
        assertNotNull(modelIdProp)
        assertNotNull(isMultimodalProp)

        val constructor =
            requireNotNull(ApiModelConfiguration::class.primaryConstructor) {
                "ApiModelConfiguration primary constructor required"
            }
        val args = mutableMapOf<kotlin.reflect.KParameter, Any?>()
        constructor.parameters.forEach { param ->
            args[param] =
                when (param.name) {
                    "id" -> ApiModelConfigurationId("")
                    "apiCredentialsId" -> ApiCredentialsId("cred-1")
                    "displayName" -> "Chat"
                    "modelId" -> "deepseek-chat"
                    "isMultimodal" -> false
                    "maxTokens" -> 4096
                    "contextWindow" -> 4096
                    "temperature" -> 0.7
                    "topP" -> 0.95
                    "topK" -> 40
                    "minP" -> 0.05
                    "frequencyPenalty" -> 0.0
                    "presencePenalty" -> 0.0
                    "systemPrompt" -> ""
                    "reasoningEffort" -> null
                    "maxTokensDisabled" -> false
                    "temperatureDisabled" -> false
                    "topPDisabled" -> false
                    "topKDisabled" -> false
                    "minPDisabled" -> false
                    "frequencyPenaltyDisabled" -> false
                    "presencePenaltyDisabled" -> false
                    "customHeaders" -> emptyMap<String, String>()
                    "openRouterRouting" ->
                        com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration()
                    else -> if (param.isOptional) null else error("Missing preset config arg: ${param.name}")
                }
        }
        val instance = constructor.callBy(args)
        assertEquals("deepseek-chat", modelIdProp!!.getter.call(instance))
        assertEquals(false, isMultimodalProp!!.getter.call(instance))
    }

    @Test
    fun `SCN-DRAFT-001 Draft Types Split Asset And Preset Concerns`() {
        val providerDraftNames = ApiProviderDraft::class.memberProperties.map { it.name }.toSet()
        assertFalse("modelId" in providerDraftNames)
        assertFalse("isMultimodal" in providerDraftNames)

        val presetConstructor =
            requireNotNull(ApiPresetDraft::class.primaryConstructor) {
                "ApiPresetDraft primary constructor required"
            }
        assertTrue(presetConstructor.parameters.any { it.name == "modelId" })
        assertTrue(presetConstructor.parameters.any { it.name == "isMultimodal" })

        val preset = ApiPresetDraft(modelId = "gpt-4o", isMultimodal = true)
        val modelIdProp = ApiPresetDraft::class.memberProperties.first { it.name == "modelId" }
        val isMultimodalProp = ApiPresetDraft::class.memberProperties.first { it.name == "isMultimodal" }
        assertEquals("gpt-4o", modelIdProp.getter.call(preset))
        assertEquals(true, isMultimodalProp.getter.call(preset))
    }

    @Test
    fun `SCN-PORT-001 Repository Port Omits Model From findMatchingCredentials`() {
        val function =
            ApiModelRepositoryPort::class.declaredFunctions.first { it.name == "findMatchingCredentials" }
        val paramNames = function.parameters.mapNotNull { it.name }
        assertTrue("provider" in paramNames)
        assertTrue("baseUrl" in paramNames)
        assertTrue("apiKey" in paramNames)
        assertTrue("sourceCredentialAlias" in paramNames)
        assertFalse("modelId" in paramNames)
    }
}
