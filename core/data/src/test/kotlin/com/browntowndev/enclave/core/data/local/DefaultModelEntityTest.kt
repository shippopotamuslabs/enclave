package com.browntowndev.enclave.core.data.local

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultModelEntityTest {
    @Test
    fun `both FKs null throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = null)
        }
    }

    @Test
    fun `both FKs non-null throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            DefaultModelEntity(
                modelType = ModelType.MAIN,
                localConfigId = LocalModelConfigurationId("5"),
                apiConfigId = ApiModelConfigurationId("3"),
            )
        }
    }
}
