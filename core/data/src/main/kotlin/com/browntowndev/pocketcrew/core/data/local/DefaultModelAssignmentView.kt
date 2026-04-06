package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

data class DefaultModelAssignmentView(
    @ColumnInfo(name = "modelType")
    val modelType: ModelType,
    
    @ColumnInfo(name = "localConfigId")
    val localConfigId: Long?,
    
    @ColumnInfo(name = "apiConfigId")
    val apiConfigId: Long?,
    
    @ColumnInfo(name = "localPresetName")
    val localPresetName: String?,
    
    @ColumnInfo(name = "apiPresetName")
    val apiPresetName: String?,
    
    @ColumnInfo(name = "localAssetName")
    val localAssetName: String?,
    
    @ColumnInfo(name = "apiAssetName")
    val apiAssetName: String?,
    
    @ColumnInfo(name = "apiProviderName")
    val apiProviderName: ApiProvider?
)
