package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

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
    
    @ColumnInfo(name = "localAssetName")
    val localAssetName: String?,
    
    @ColumnInfo(name = "apiAssetName")
    val apiAssetName: String?,
    
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

    @ColumnInfo(name = "mediaCapability")
    val mediaCapability: MediaCapability?
)
