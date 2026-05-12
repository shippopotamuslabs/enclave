package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultModelRepositorySyncTest {
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val defaultModelsDao = mockk<DefaultModelsDao>(relaxed = true)
    private val localModelConfigurationsDao = mockk<LocalModelConfigurationsDao>(relaxed = true)
    private val localModelsDao = mockk<LocalModelsDao>(relaxed = true)
    private val apiModelConfigurationsDao = mockk<ApiModelConfigurationsDao>(relaxed = true)
    private val apiCredentialsDao = mockk<ApiCredentialsDao>(relaxed = true)
    private val ttsProviderDao = mockk<TtsProviderDao>(relaxed = true)
    private val mediaProviderDao = mockk<MediaProviderDao>(relaxed = true)

    private lateinit var repository: DefaultModelRepositoryImpl

    @BeforeEach
    fun setup() {
        repository =
            DefaultModelRepositoryImpl(
                syncTrigger = syncTrigger,
                defaultModelsDao = defaultModelsDao,
                localModelConfigurationsDao = localModelConfigurationsDao,
                localModelsDao = localModelsDao,
                apiModelConfigurationsDao = apiModelConfigurationsDao,
                apiCredentialsDao = apiCredentialsDao,
                ttsProviderDao = ttsProviderDao,
                mediaProviderDao = mediaProviderDao,
            )
    }

    @Test
    fun `setDefault sets pendingSync to 1 and triggers sync`() =
        runTest {
            repository.setDefault(ModelType.FAST, LocalModelConfigurationId("id"), null, null, null)

            coVerify {
                defaultModelsDao.upsert(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `clearDefault triggers sync`() =
        runTest {
            repository.clearDefault(ModelType.FAST)

            coVerify {
                defaultModelsDao.softDeleteById(ModelType.FAST, any())
                syncTrigger.trigger()
            }
        }
}
