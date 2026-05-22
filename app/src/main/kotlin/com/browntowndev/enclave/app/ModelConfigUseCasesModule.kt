package com.browntowndev.enclave.app

import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelMetadataUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelMetadataUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelMetadataUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelMetadataUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelConfigUseCasesModule {
    @Binds
    @Singleton
    abstract fun bindGetLocalModelAssetsUseCase(impl: GetLocalModelAssetsUseCaseImpl): GetLocalModelAssetsUseCase

    @Binds
    @Singleton
    abstract fun bindSaveLocalModelMetadataUseCase(impl: SaveLocalModelMetadataUseCaseImpl): SaveLocalModelMetadataUseCase

    @Binds
    @Singleton
    abstract fun bindDeleteLocalModelMetadataUseCase(impl: DeleteLocalModelMetadataUseCaseImpl): DeleteLocalModelMetadataUseCase

    @Binds
    @Singleton
    abstract fun bindSaveLocalModelConfigurationUseCase(impl: SaveLocalModelConfigurationUseCaseImpl): SaveLocalModelConfigurationUseCase

    @Binds
    @Singleton
    abstract fun bindDeleteLocalModelConfigurationUseCase(
        impl: DeleteLocalModelConfigurationUseCaseImpl,
    ): DeleteLocalModelConfigurationUseCase
}
