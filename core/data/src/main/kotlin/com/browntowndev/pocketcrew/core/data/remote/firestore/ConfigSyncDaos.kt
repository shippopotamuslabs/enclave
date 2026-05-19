package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigSyncDaos
    @Inject
    constructor(
        val memoriesDao: MemoriesDao,
        val ttsProviderDao: TtsProviderDao,
        val mediaProviderDao: MediaProviderDao,
        val apiModelConfigurationsDao: ApiModelConfigurationsDao,
        val localModelsDao: LocalModelsDao,
        val apiCredentialsDao: ApiCredentialsDao,
        val localModelConfigurationsDao: LocalModelConfigurationsDao,
        val defaultModelsDao: DefaultModelsDao,
    )
