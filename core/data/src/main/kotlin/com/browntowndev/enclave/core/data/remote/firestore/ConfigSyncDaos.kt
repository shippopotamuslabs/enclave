package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
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
        val utilityModelsDao: UtilityModelsDao,
    )
