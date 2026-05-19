package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import com.google.genai.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class GoogleImageGenerationAdapter
    @Inject
    constructor(
        private val clientProvider: GoogleGenAiClientProviderPort,
    ) {
        suspend fun generateImage(
            prompt: String,
            apiKey: String,
            modelId: String,
            baseUrl: String?,
            settings: GenerationSettings,
            referenceImage: ByteArray? = null,
        ): Result<List<ByteArray>> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val client = clientProvider.getClient(apiKey, baseUrl)
                    val imageSettings = settings as? ImageGenerationSettings

                    val config =
                        GenerateContentConfig
                            .builder()
                            .responseModalities(listOf("IMAGE"))
                            .apply {
                                imageSettings?.generationCount?.let { candidateCount(it) }
                            }.build()

                    val parts = mutableListOf<Part>()
                    parts.add(Part.builder().text(prompt).build())

                    if (referenceImage != null) {
                        parts.add(
                            Part
                                .builder()
                                .inlineData(
                                    Blob
                                        .builder()
                                        .mimeType("image/png")
                                        .data(referenceImage)
                                        .build(),
                                ).build(),
                        )
                    }

                    val content =
                        Content
                            .builder()
                            .role("user")
                            .parts(parts)
                            .build()

                    val response = generateContent(client, modelId, listOf(content), config)
                    val generatedImages =
                        response.parts()?.mapNotNull { part ->
                            part
                                .inlineData()
                                .orElse(null)
                                ?.data()
                                ?.orElse(null)
                        } ?: emptyList()

                    if (generatedImages.isEmpty()) {
                        throw IllegalStateException("No image generated")
                    }
                    generatedImages
                }
            }

        protected open fun generateContent(
            client: Client,
            modelId: String,
            contents: List<Content>,
            config: GenerateContentConfig,
        ): GenerateContentResponse = client.models.generateContent(modelId, contents, config)
    }
