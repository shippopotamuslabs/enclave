package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType

data class DefaultModelAssignmentView(
    @ColumnInfo(name = "modelType")
    val modelType: ModelType,
    @ColumnInfo(name = "localConfigId")
    val localConfigId: LocalModelConfigurationId?,
    @ColumnInfo(name = "apiConfigId")
    val apiConfigId: ApiModelConfigurationId?,
    @ColumnInfo(name = "localPresetName")
    val localPresetName: String?,
    @ColumnInfo(name = "apiPresetName")
    val apiPresetName: String?,
    @ColumnInfo(name = "apiModelId")
    val apiModelId: String?,
    @ColumnInfo(name = "localAssetName")
    val localAssetName: String?,
    @ColumnInfo(name = "apiAssetName")
    val apiAssetName: String?,
    @ColumnInfo(name = "apiCredentialsId")
    val apiCredentialsId: ApiCredentialsId?,
    @ColumnInfo(name = "apiProviderName")
    val apiProviderName: ApiProvider?,
    @ColumnInfo(name = "ttsProviderId")
    val ttsProviderId: TtsProviderId?,
    @ColumnInfo(name = "ttsAssetName")
    val ttsAssetName: String?,
    @ColumnInfo(name = "ttsProviderName")
    val ttsProviderName: ApiProvider?,
    @ColumnInfo(name = "ttsVoiceName")
    val ttsVoiceName: String?,
    @ColumnInfo(name = "mediaProviderId")
    val mediaProviderId: MediaProviderId?,
    @ColumnInfo(name = "mediaAssetName")
    val mediaAssetName: String?,
    @ColumnInfo(name = "mediaProviderName")
    val mediaProviderName: ApiProvider?,
    @ColumnInfo(name = "mediaModelName")
    val mediaModelName: String?,
    @ColumnInfo(name = "mediaCapability")
    val mediaCapability: MediaCapability?,
    @ColumnInfo(name = "liveVoiceProviderId")
    val liveVoiceProviderId: LiveVoiceProviderId? = null,
    @ColumnInfo(name = "liveVoiceAssetName")
    val liveVoiceAssetName: String? = null,
    @ColumnInfo(name = "liveVoiceProviderName")
    val liveVoiceProviderName: ApiProvider? = null,
    @ColumnInfo(name = "liveVoiceVoiceName")
    val liveVoiceVoiceName: String? = null,
)
