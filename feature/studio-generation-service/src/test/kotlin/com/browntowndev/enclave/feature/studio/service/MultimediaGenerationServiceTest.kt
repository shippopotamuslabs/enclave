package com.browntowndev.enclave.feature.studio.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.browntowndev.enclave.core.data.R
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34])
class MultimediaGenerationServiceTest {
    private val generateMediaUseCase = mockk<GenerateMediaUseCase>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)

    private lateinit var service: MultimediaGenerationService

    @Before
    fun setUp() {
        service = MultimediaGenerationService()
        attachBaseContext(service, RuntimeEnvironment.getApplication())
        invokeCreateNotificationChannel(service)
        inject("generateMediaUseCase", generateMediaUseCase)
        inject("studioRepository", studioRepository)
        inject("logger", logger)
    }

    @Test
    fun `S-STU-01 IMAGE start invokes GenerateMediaUseCase exactly once`() {
        coEvery { generateMediaUseCase(any(), any()) } returns Result.success(emptyList())

        service.onStartCommand(startImageIntent(prompt = "hello"), 0, 1)
        awaitServiceWork()

        coVerify(exactly = 1) { generateMediaUseCase(any(), any()) }
    }

    @Test
    fun `S-STU-02 Service does not declare MediaProviderRepositoryPort field`() {
        val fieldNames = MultimediaGenerationService::class.java.declaredFields.map { it.name }

        assertFalse(fieldNames.contains("mediaProviderRepository"))
    }

    @Test
    fun `S-STU-03 Success path does not call reportGenerationError`() {
        coEvery { generateMediaUseCase(any(), any()) } returns
            Result.success(
                listOf(
                    GeneratedMediaAsset(
                        id = "id1",
                        localUri = "file:///ok",
                        prompt = "hello",
                        mediaType = "IMAGE",
                        createdAt = 0,
                    ),
                ),
            )

        service.onStartCommand(startImageIntent(prompt = "hello"), 0, 1)
        awaitServiceWork()

        coVerify(exactly = 0) { studioRepository.reportGenerationError(any()) }
    }

    @Test
    fun `S-STU-04 Use case failure calls reportGenerationError with provider message`() {
        coEvery { generateMediaUseCase(any(), any()) } returns
            Result.failure(IllegalStateException("No image generation provider assigned"))

        service.onStartCommand(startImageIntent(prompt = "hello"), 0, 1)
        awaitServiceWork()

        coVerify(exactly = 1) {
            studioRepository.reportGenerationError("No image generation provider assigned")
        }
    }

    @Test
    fun `S-STU-05 Missing prompt does not invoke GenerateMediaUseCase`() {
        val intent =
            Intent().apply {
                action = MultimediaGenerationService.ACTION_START
                putExtra(MultimediaGenerationService.EXTRA_MEDIA_TYPE, MediaCapability.IMAGE.name)
            }

        service.onStartCommand(intent, 0, 1)
        awaitServiceWork()

        coVerify(exactly = 0) { generateMediaUseCase(any(), any()) }
    }

    @Test
    fun `S-STU-06 Intent image settings with generationCount 3 reach use case request`() {
        val requestSlot = slot<MediaGenerationRequest>()
        coEvery { generateMediaUseCase(capture(requestSlot), any()) } returns Result.success(emptyList())

        val intent =
            startImageIntent(prompt = "hello").apply {
                StudioGenerationIntentCodec.writeSettings(
                    this,
                    ImageGenerationSettings(generationCount = 3),
                )
            }

        service.onStartCommand(intent, 0, 1)
        awaitServiceWork()

        val settings = requestSlot.captured.settings
        assertEquals(ImageGenerationSettings::class.java, settings.javaClass)
        assertEquals(3, (settings as ImageGenerationSettings).generationCount)
    }

    @Test
    fun `E-STU-01 ACTION_STOP does not invoke GenerateMediaUseCase`() {
        val intent =
            Intent().apply {
                action = MultimediaGenerationService.ACTION_STOP
            }

        service.onStartCommand(intent, 0, 1)
        awaitServiceWork()

        coVerify(exactly = 0) { generateMediaUseCase(any(), any()) }
    }

    @Test
    fun `S-STU-07 foreground notification uses Enclave ic_notification icon`() {
        val notification = invokeCreateNotification(service, "Generating...")

        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        assertEquals("Enclave", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `M-STU-01 Studio IMAGE uses GenerateMediaUseCase not ImageGenerationPort`() {
        val requestSlot = slot<MediaGenerationRequest>()
        coEvery { generateMediaUseCase(capture(requestSlot), any()) } returns Result.success(emptyList())

        service.onStartCommand(startImageIntent(prompt = "port-check"), 0, 1)
        awaitServiceWork()

        coVerify(exactly = 1) { generateMediaUseCase(any(), any()) }
        assertEquals("port-check", requestSlot.captured.prompt)
        assertEquals(MediaCapability.IMAGE, requestSlot.captured.capability)
    }

    @Test
    fun `S-STU-08 image progress is reported and cleared`() {
        val progressSlot = slot<(ImageGenerationProgress) -> Unit>()
        coEvery { generateMediaUseCase(any(), capture(progressSlot)) } answers {
            progressSlot.captured(ImageGenerationProgress.LoadingModel)
            Result.success(emptyList())
        }

        service.onStartCommand(startImageIntent(prompt = "hello"), 0, 1)
        awaitServiceWork()

        verify(exactly = 1) { studioRepository.reportGenerationProgress(ImageGenerationProgress.LoadingModel) }
        verify(exactly = 1) { studioRepository.clearGenerationProgress() }
    }

    private fun startImageIntent(prompt: String): Intent =
        Intent().apply {
            action = MultimediaGenerationService.ACTION_START
            putExtra(MultimediaGenerationService.EXTRA_PROMPT, prompt)
            putExtra(MultimediaGenerationService.EXTRA_MEDIA_TYPE, MediaCapability.IMAGE.name)
        }

    private fun inject(
        fieldName: String,
        value: Any,
    ) {
        val field: Field = MultimediaGenerationService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun awaitServiceWork() {
        runBlocking {
            repeat(50) {
                delay(100)
            }
        }
    }

    private fun attachBaseContext(
        target: MultimediaGenerationService,
        context: Context,
    ) {
        val method =
            android.app.Service::class.java.getDeclaredMethod(
                "attachBaseContext",
                Context::class.java,
            )
        method.isAccessible = true
        method.invoke(target, context)
    }

    private fun invokeCreateNotificationChannel(target: MultimediaGenerationService) {
        val method =
            MultimediaGenerationService::class.java.getDeclaredMethod(
                "createNotificationChannel",
            )
        method.isAccessible = true
        method.invoke(target)
    }

    private fun invokeCreateNotification(
        target: MultimediaGenerationService,
        content: String,
    ): Notification {
        val method =
            MultimediaGenerationService::class.java.getDeclaredMethod(
                "createNotification",
                String::class.java,
            )
        method.isAccessible = true
        return method.invoke(target, content) as Notification
    }
}
