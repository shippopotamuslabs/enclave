package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

data class ApiCredentials(
    val id: ApiCredentialsId,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String? = null,
    val isMultimodal: Boolean = false,
    val credentialAlias: String,
)