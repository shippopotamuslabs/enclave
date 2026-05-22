package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.feature.inference.AnthropicClientProviderPort
import com.browntowndev.enclave.feature.inference.AnthropicInferenceClientProvider
import com.browntowndev.enclave.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.GoogleGenAiInferenceClientProvider
import com.browntowndev.enclave.feature.inference.OpenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.OpenAiInferenceClientProvider
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
    abstract fun bindOpenAiClientProvider(impl: OpenAiInferenceClientProvider): OpenAiClientProviderPort

    @Binds
    @Singleton
    abstract fun bindAnthropicClientProvider(impl: AnthropicInferenceClientProvider): AnthropicClientProviderPort

    @Binds
    @Singleton
    abstract fun bindGoogleGenAiClientProvider(impl: GoogleGenAiInferenceClientProvider): GoogleGenAiClientProviderPort
}
