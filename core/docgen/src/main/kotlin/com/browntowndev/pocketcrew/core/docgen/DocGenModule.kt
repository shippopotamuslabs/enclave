package com.browntowndev.pocketcrew.core.docgen

import com.browntowndev.pocketcrew.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentGeneratorPort
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentValidationPort
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
