package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Room entity mapping ModelType slots to either on-device or API sources.
 */
@Entity(
    tableName = "default_models",
    foreignKeys = [
        ForeignKey(
            entity = ApiModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["api_model_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["api_model_id"])],
)
data class DefaultModelEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,

    @ColumnInfo(name = "source")
    val source: ModelSource,

    @ColumnInfo(name = "api_model_id")
    val apiModelId: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
