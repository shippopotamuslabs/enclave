package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelMetadataUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.DeleteLocalModelMetadataUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelMetadataUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SaveLocalModelMetadataUseCaseImpl
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
    abstract fun bindDeleteLocalModelConfigurationUseCase(impl: DeleteLocalModelConfigurationUseCaseImpl): DeleteLocalModelConfigurationUseCase
}