package com.browntowndev.pocketcrew.feature.inference.di

import com.browntowndev.pocketcrew.feature.inference.AnthropicClientProviderPort
import com.browntowndev.pocketcrew.feature.inference.AnthropicInferenceClientProvider
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiInferenceClientProvider
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.browntowndev.pocketcrew.feature.inference.OpenAiInferenceClientProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApiClientProviderModule {

    @Binds
    @Singleton
    abstract fun bindOpenAiClientProvider(
        impl: OpenAiInferenceClientProvider,
    ): OpenAiClientProviderPort

    @Binds
    @Singleton
    abstract fun bindAnthropicClientProvider(
        impl: AnthropicInferenceClientProvider,
    ): AnthropicClientProviderPort

    @Binds
    @Singleton
    abstract fun bindGoogleGenAiClientProvider(
        impl: GoogleGenAiInferenceClientProvider,
    ): GoogleGenAiClientProviderPort
}
