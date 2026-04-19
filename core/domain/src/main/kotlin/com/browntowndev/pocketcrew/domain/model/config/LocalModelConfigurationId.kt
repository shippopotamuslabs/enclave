package com.browntowndev.pocketcrew.domain.model.config

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class LocalModelConfigurationId(override val value: String) : ModelConfigurationId
