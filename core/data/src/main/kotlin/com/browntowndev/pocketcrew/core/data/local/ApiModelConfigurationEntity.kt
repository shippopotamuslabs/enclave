package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId

@Entity(
    tableName = "api_model_configurations",
    foreignKeys = [
        ForeignKey(
            entity = ApiCredentialsEntity::class,
            parentColumns = ["id"],
            childColumns = ["api_credentials_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["api_credentials_id", "display_name"], unique = true)
    ]
)
data class ApiModelConfigurationEntity(
    @PrimaryKey
    val id: ApiModelConfigurationId,

    @ColumnInfo(name = "api_credentials_id")
    val apiCredentialsId: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "custom_headers")
    val customHeaders: String = "{}",

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 4096,

    @ColumnInfo(name = "context_window")
    val contextWindow: Int = 4096,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.7,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "top_k")
    val topK: Int? = null,

    @ColumnInfo(name = "min_p")
    val minP: Double = 0.05,

    @ColumnInfo(name = "frequency_penalty")
    val frequencyPenalty: Double = 0.0,

    @ColumnInfo(name = "presence_penalty")
    val presencePenalty: Double = 0.0,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String = "",

    @ColumnInfo(name = "reasoning_effort")
    val reasoningEffort: String? = null,

    @ColumnInfo(name = "openrouter_provider_sort")
    val openRouterProviderSort: String? = null,

    @ColumnInfo(name = "openrouter_allow_fallbacks")
    val openRouterAllowFallbacks: Boolean? = null,

    @ColumnInfo(name = "openrouter_require_parameters")
    val openRouterRequireParameters: Boolean? = null,

    @ColumnInfo(name = "openrouter_data_collection")
    val openRouterDataCollectionPolicy: String? = null,

    @ColumnInfo(name = "openrouter_zdr")
    val openRouterZeroDataRetention: Boolean? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
