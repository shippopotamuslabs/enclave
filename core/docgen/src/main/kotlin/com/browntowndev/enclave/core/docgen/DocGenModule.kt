package com.browntowndev.enclave.core.docgen

import com.browntowndev.enclave.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.enclave.domain.port.docgen.DocumentGeneratorPort
import com.browntowndev.enclave.domain.port.docgen.DocumentValidationPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DocGenModule {
    @Provides
    @Singleton
    fun provideDocumentValidationPort(): DocumentValidationPort = RustDocumentValidationAdapter()

    @Provides
    @Singleton
    fun provideCloudDocumentExtractorPort(adapter: LocalDocumentExtractorAdapter): CloudDocumentExtractorPort = adapter

    @Provides
    @Singleton
    fun provideDocumentGeneratorPort(): DocumentGeneratorPort = LocalDocumentGeneratorAdapter()
}
