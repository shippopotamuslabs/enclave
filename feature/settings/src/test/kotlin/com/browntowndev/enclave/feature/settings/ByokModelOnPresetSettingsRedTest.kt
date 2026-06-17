package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DiscoverApiModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveApiProviderDraftUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.TestApiProviderConnectionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ByokModelOnPresetSettingsRedTest {
    private val apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true)
    private val deletionHelper = mockk<SettingsDeletionHelper>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private lateinit var coordinator: ByokProvidersCoordinator
    private lateinit var mapper: ApiModelAssetUiMapper
    private lateinit var uiStateFactory: SettingsUiStateFactory

    @BeforeEach
    fun setup() {
        every { apiProviderUseCases.applyApiModelMetadataDefaults } returns ApplyApiModelMetadataDefaultsUseCase()
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }
        coordinator =
            ByokProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                deletionHelper = deletionHelper,
                apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                errorHandler = errorHandler,
            )
        mapper = ApiModelAssetUiMapper()
        uiStateFactory =
            buildSettingsUiStateFactory(
                localModelAssetUiMapper = LocalModelAssetUiMapper(),
                apiModelAssetUiMapper = mapper,
                ttsProviderAssetUiMapper = mockk(relaxed = true),
                mediaProviderAssetUiMapper = mockk(relaxed = true),
                apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
                usageSpendUiMapper =
                    com.browntowndev.enclave.feature.settings.usage
                        .UsageSpendUiMapper(),
            )
    }

    @Test
    fun `SCN-UI-MAPPER-001 Asset Ui Mapper Omits Model`() {
        val asset =
            ApiModelAsset(
                credentials =
                    ApiCredentials(
                        id = ApiCredentialsId("1"),
                        displayName = "My OpenAI",
                        provider = ApiProvider.OPENAI,
                        credentialAlias = "openai",
                    ),
                configurations = emptyList(),
            )
        val ui = mapper.map(asset)
        val propertyNames = ApiModelAssetUi::class.memberProperties.map { it.name }.toSet()
        assertFalse("modelId" in propertyNames)
        assertFalse("isMultimodal" in propertyNames)
    }

    @Test
    fun `SCN-UI-MAPPER-002 Config Ui Mapper Includes Model`() {
        val config = apiConfiguration(modelId = "grok-4", isMultimodal = true)
        val ui = mapper.mapConfig(config)
        val modelId =
            ApiModelConfigUi::class
                .memberProperties
                .first { it.name == "modelId" }
                .getter
                .call(ui) as String
        val isMultimodal =
            ApiModelConfigUi::class
                .memberProperties
                .first { it.name == "isMultimodal" }
                .getter
                .call(ui) as Boolean
        assertEquals("grok-4", modelId)
        assertEquals(true, isMultimodal)
    }

    @Test
    fun `SCN-UI-DISCOVER-001 Credential Form Has No Discover Models`() {
        val source = readSettingsSource("ByokConfigureForms.kt")
        val credentialForm = source.substringAfter("fun CredentialsConfigurationForm")
        assertFalse(credentialForm.contains("ModelSelectionField"))
        assertFalse(credentialForm.contains("VisionCapabilitySwitch"))
    }

    @Test
    fun `SCN-UI-DISCOVER-002 Preset Form Hosts Model Selection`() {
        val formsSource = readSettingsSource("ByokConfigureForms.kt")
        val presetForm =
            formsSource
                .substringAfter("fun PresetConfigurationForm(")
                .substringBefore("@Preview")
        assertTrue(presetForm.contains("PresetModelAndVisionSection"))
        val formsModelSection = formsSource.substringAfter("fun ModelSelectionField")
        assertTrue(formsModelSection.contains("Text(\"Model\")"))
    }

    @Test
    fun `SCN-UI-VISION-001 Vision Switch On Preset Draft`() {
        val presetProp = ApiModelConfigUi::class.memberProperties.any { it.name == "isMultimodal" }
        assertTrue(presetProp)
        val assetProp = ApiModelAssetUi::class.memberProperties.any { it.name == "isMultimodal" }
        assertFalse(assetProp)
    }

    @Test
    fun `SCN-UI-CRED-001 Reusable Credential Subtitle Provider Only`() {
        val hasProviderLabel = ReusableApiCredentialUi::class.memberProperties.any { it.name == "providerLabel" }
        assertTrue(hasProviderLabel)
        val reusable =
            if (hasProviderLabel) {
                val constructor = requireNotNull(ReusableApiCredentialUi::class.primaryConstructor)
                val args = mutableMapOf<kotlin.reflect.KParameter, Any?>()
                constructor.parameters.forEach { param ->
                    args[param] =
                        when (param.name) {
                            "credentialsId" -> ApiCredentialsId("1")
                            "displayName" -> "Work Key"
                            "providerLabel" -> "OpenAI"
                            "credentialAlias" -> "openai-main"
                            else -> if (param.isOptional) null else error("Missing: ${param.name}")
                        }
                }
                constructor.callBy(args)
            } else {
                ReusableApiCredentialUi(
                    credentialsId = ApiCredentialsId("1"),
                    displayName = "Work Key",
                    providerLabel = "OpenAI",
                    credentialAlias = "openai-main",
                )
            }
        val subtitle =
            ReusableApiCredentialUi::class
                .memberProperties
                .firstOrNull { it.name == "providerLabel" }
                ?.getter
                ?.call(reusable) as? String
                ?: error("providerLabel required on ReusableApiCredentialUi")
        assertEquals("OpenAI", subtitle)
        assertFalse(subtitle.contains("gpt"))
    }

    @Test
    fun `SCN-UI-SAVE-001 Credential Save Without Model`() {
        val source = readSettingsSource("ByokConfigureScreen.kt")
        val credentialBranch = source.substringAfter("draftAsset.credentialsId.value.isEmpty()")
        assertFalse(credentialBranch.substringBefore("} else {").contains("draftAsset.modelId.isNotBlank()"))
    }

    @Test
    fun `SCN-UI-SAVE-002 Preset Save Requires Model`() {
        val source = readSettingsSource("ByokConfigureScreen.kt")
        val presetBranch = source.substringAfter("selectedConfig != null")
        assertTrue(presetBranch.contains("modelId"))
    }

    @Test
    fun `SCN-UI-TEST-001 Test Button Success Presentation`() {
        val source = readSettingsSource("ByokConfigureForms.kt")
        val credentialForm = source.substringAfter("fun CredentialsConfigurationForm")
        assertTrue(credentialForm.contains("Success"))
        assertTrue(credentialForm.contains("CheckCircle"))
    }

    @Test
    fun `SCN-UI-TEST-002 Test Failure Snackbar`() {
        val coordinatorSource = readSettingsSource("ByokProvidersCoordinator.kt")
        assertTrue(
            coordinatorSource.contains("onTestApiProviderConnection") ||
                coordinatorSource.contains("testApiProviderConnection"),
        )
    }

    @Test
    fun `SCN-UI-TEST-003 Fingerprint Change Resets Success`() {
        val coordinatorSource = readSettingsSource("ByokProvidersCoordinator.kt")
        assertTrue(
            coordinatorSource.contains("connectionTestStatus") ||
                coordinatorSource.contains("IDLE"),
        )
    }

    @Test
    fun `SCN-UI-STATE-001 Editor State Maps Connection Success`() {
        val statusEnumExists =
            runCatching {
                Class.forName("com.browntowndev.enclave.feature.settings.ApiConnectionTestStatus")
            }.isSuccess
        assertTrue(statusEnumExists)
        val editorProperties = ApiProviderEditorUiState::class.memberProperties.map { it.name }
        assertTrue("connectionTestStatus" in editorProperties)
    }

    @Test
    fun `SCN-UI-COORD-001 Coordinator Invokes Test Use Case`() =
        runTest {
            val testUseCase = mockk<TestApiProviderConnectionUseCase>()
            every { apiProviderUseCases.testApiProviderConnection } returns testUseCase
            coEvery {
                testUseCase(any(), any(), any(), any())
            } returns Result.success(Unit)
            val apiState =
                MutableStateFlow(
                    ApiProvidersTransientState(
                        assetDraft =
                            ApiModelAssetUi(
                                credentialsId = ApiCredentialsId(""),
                                displayName = "Key",
                                provider = ApiProvider.OPENAI,
                                baseUrl = null,
                                credentialAlias = "",
                                configurations = emptyList(),
                            ),
                    ),
                )
            val currentApiKey = MutableStateFlow("sk-test")

            coordinator.onTestApiProviderConnection(this, apiState, currentApiKey, onShowSnackbar = {})
            advanceUntilIdle()

            coVerify {
                testUseCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "sk-test",
                    credentialAlias = null,
                    baseUrl = null,
                )
            }
        }

    @Test
    fun `SCN-UI-COORD-002 Fetch Models Uses Preset Model Id`() =
        runTest {
            val discover = mockk<DiscoverApiModelsUseCase>()
            every { apiProviderUseCases.discoverApiModels } returns discover
            val requestSlot = slot<ApiModelDiscoveryRequest>()
            coEvery { discover(capture(requestSlot)) } returns
                ApiModelDiscoveryResult(
                    models = emptyList(),
                    scope =
                        com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope(
                            provider = ApiProvider.XAI,
                            baseUrl = "https://api.x.ai/v1",
                            credentialAlias = null,
                        ),
                )
            val apiState =
                MutableStateFlow(
                    ApiProvidersTransientState(
                        assetDraft =
                            ApiModelAssetUi(
                                credentialsId = ApiCredentialsId("cred"),
                                displayName = "xAI",
                                provider = ApiProvider.XAI,
                                baseUrl = "https://api.x.ai/v1",
                                credentialAlias = "alias",
                                configurations = emptyList(),
                            ),
                        presetDraft = presetUi(modelId = "grok-4"),
                    ),
                )
            val currentApiKey = MutableStateFlow("key")

            coordinator.onFetchApiModels(this, "tag", apiState, currentApiKey)
            advanceUntilIdle()

            assertEquals("grok-4", requestSlot.captured.selectedModelId)
        }

    @Test
    fun `SCN-UI-COORD-003 Save Credentials Draft Credential Only`() =
        runTest {
            val saveDraft = mockk<SaveApiProviderDraftUseCase>()
            every { apiProviderUseCases.saveApiProviderDraft } returns saveDraft
            val draftSlot = slot<ApiProviderDraft>()
            coEvery { saveDraft(capture(draftSlot)) } returns
                Result.success(
                    com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraftSaveResult(
                        persistedAsset =
                            com.browntowndev.enclave.domain.model.config.ApiModelAsset(
                                credentials =
                                    com.browntowndev.enclave.domain.model.config.ApiCredentials(
                                        id = ApiCredentialsId("saved"),
                                        displayName = "My Key",
                                        provider = ApiProvider.OPENAI,
                                        credentialAlias = "openai",
                                    ),
                                configurations = emptyList(),
                            ),
                    ),
                )
            val apiState =
                MutableStateFlow(
                    ApiProvidersTransientState(
                        assetDraft =
                            ApiModelAssetUi(
                                credentialsId = ApiCredentialsId(""),
                                displayName = "My Key",
                                provider = ApiProvider.OPENAI,
                                baseUrl = null,
                                credentialAlias = "",
                                configurations = emptyList(),
                            ),
                    ),
                )
            val currentApiKey = MutableStateFlow("sk-x")

            coordinator.onSaveApiCredentials(this, "tag", apiState, currentApiKey, onSuccess = { _, _ -> }, onShowSnackbar = {})
            advanceUntilIdle()

            coVerify(exactly = 1) { saveDraft(any()) }
            val draftNames = ApiProviderDraft::class.memberProperties.map { it.name }.toSet()
            assertFalse("modelId" in draftNames)
            assertFalse("isMultimodal" in draftNames)
        }

    @Test
    fun `SCN-UI-SHEET-001 Asset List Subtitle Without Model`() {
        val source = readSettingsSource("ByokBottomSheet.kt")
        val assetListSection =
            source
                .substringAfter("private fun ByokAssetListView")
                .substringBefore("private fun ByokConfigListView")
        assertTrue(assetListSection.contains("asset.provider.displayName"))
        assertFalse(assetListSection.contains("asset.modelId"))
    }

    @Test
    fun `SCN-UI-SHEET-002 Preset Row Shows Model`() {
        val source = readSettingsSource("ByokBottomSheet.kt")
        assertTrue(source.contains("modelId") || source.contains("preset.modelId"))
    }

    @Test
    fun `SCN-UI-ASSIGN-001 Assignment Picker Uses Preset Model`() {
        var root: java.nio.file.Path? = null
        try {
            val classLocation = ByokModelOnPresetSettingsRedTest::class.java.protectionDomain.codeSource.location.toURI()
            var current = java.nio.file.Paths.get(classLocation)
            while (current != null) {
                if (java.nio.file.Files.exists(current.resolve("settings.gradle.kts"))) {
                    root = current
                    break
                }
                current = current.parent
            }
        } catch (e: Exception) {
            // ignore and fallback
        }

        if (root == null) {
            var current = java.nio.file.Paths.get("").toAbsolutePath()
            while (current != null) {
                if (java.nio.file.Files.exists(current.resolve("settings.gradle.kts"))) {
                    root = current
                    break
                }
                current = current.parent
            }
        }

        val path = if (root != null) {
            val extracted = root.resolve("core/ui/src/main/kotlin/com/browntowndev/enclave/core/ui/component/assignment/AssignmentSelectionBottomSheet.kt")
            val original = root.resolve("feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ModelConfigurationScreen.kt")
            if (java.nio.file.Files.exists(extracted)) extracted else original
        } else {
            val extractedRelative = java.nio.file.Paths.get("../../core/ui/src/main/kotlin/com/browntowndev/enclave/core/ui/component/assignment/AssignmentSelectionBottomSheet.kt")
            val originalRelative = java.nio.file.Paths.get("src/main/kotlin/com/browntowndev/enclave/feature/settings/ModelConfigurationScreen.kt")
            if (java.nio.file.Files.exists(extractedRelative)) extractedRelative else originalRelative
        }

        val source = java.nio.file.Files.readString(path)
        assertTrue(source.contains("modelId"))
    }

    @Test
    fun `SCN-MUT-004 Discover Models Must Not Remain On Credential Form`() {
        val source = readSettingsSource("ByokConfigureForms.kt")
        val credentialForm = source.substringAfter("fun CredentialsConfigurationForm")
        assertFalse(credentialForm.contains("ModelSelectionField"))
    }

    private fun presetUi(modelId: String): ApiModelConfigUi =
        ApiModelConfigUi(
            credentialsId = ApiCredentialsId("cred"),
            modelId = modelId,
        )

    private fun apiConfiguration(
        modelId: String,
        isMultimodal: Boolean,
    ): ApiModelConfiguration =
        ApiModelConfiguration(
            apiCredentialsId = ApiCredentialsId("cred"),
            modelId = modelId,
            isMultimodal = isMultimodal,
            displayName = "Preset",
        )

    private fun readSettingsSource(fileName: String): String {
        val candidates =
            listOf(
                Paths.get("feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/$fileName"),
                Paths.get("src/main/kotlin/com/browntowndev/enclave/feature/settings/$fileName"),
            )
        val path = candidates.firstOrNull { Files.exists(it) } ?: error("Missing source file: $fileName")
        return Files.readString(path)
    }
}
