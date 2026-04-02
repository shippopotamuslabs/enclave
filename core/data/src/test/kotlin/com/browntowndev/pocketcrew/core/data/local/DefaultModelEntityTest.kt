package com.browntowndev.pocketcrew.core.data.local

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import org.junit.Test

class DefaultModelEntityTest {
    @Test(expected = IllegalArgumentException::class)
    fun `both FKs null throws IllegalArgumentException`() {
        DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = null, apiConfigId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `both FKs non-null throws IllegalArgumentException`() {
        DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = 5L, apiConfigId = 3L)
    }
}