package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiCredentialsUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.DeleteApiModelConfigurationUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelDetailUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.FetchApiProviderModelsUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiCredentialsUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCaseImpl
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ByokUseCasesModule {
    @Binds
    @Singleton
    abstract fun bindGetApiModelAssetsUseCase(impl: GetApiModelAssetsUseCaseImpl): GetApiModelAssetsUseCase

    @Binds
    @Singleton
    abstract fun bindGetDefaultModelsUseCase(impl: GetDefaultModelsUseCaseImpl): GetDefaultModelsUseCase

    @Binds
    @Singleton
    abstract fun bindSaveApiCredentialsUseCase(impl: SaveApiCredentialsUseCaseImpl): SaveApiCredentialsUseCase

    @Binds
    @Singleton
    abstract fun bindFetchApiProviderModelsUseCase(impl: FetchApiProviderModelsUseCaseImpl): FetchApiProviderModelsUseCase

    @Binds
    @Singleton
    abstract fun bindFetchApiProviderModelDetailUseCase(
        impl: FetchApiProviderModelDetailUseCaseImpl
    ): FetchApiProviderModelDetailUseCase

    @Binds
    @Singleton
    abstract fun bindDeleteApiCredentialsUseCase(impl: DeleteApiCredentialsUseCaseImpl): DeleteApiCredentialsUseCase

    @Binds
    @Singleton
    abstract fun bindSaveApiModelConfigurationUseCase(impl: SaveApiModelConfigurationUseCaseImpl): SaveApiModelConfigurationUseCase

    @Binds
    @Singleton
    abstract fun bindDeleteApiModelConfigurationUseCase(impl: DeleteApiModelConfigurationUseCaseImpl): DeleteApiModelConfigurationUseCase

    @Binds
    @Singleton
    abstract fun bindSetDefaultModelUseCase(impl: SetDefaultModelUseCaseImpl): SetDefaultModelUseCase
}
