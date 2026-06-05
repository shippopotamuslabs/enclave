package com.browntowndev.enclave.feature.inference.media

import android.app.ActivityManager
import android.content.Context
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import com.browntowndev.enclave.feature.inference.media.local.SnapdragonDeviceClassifier
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocalImageGenerationDevicePolicyTest {
    private val context = mockk<Context>(relaxed = true)
    private val nativeRuntime = mockk<NativeLocalImageGenRuntime>(relaxed = true)
    private val activityManager = mockk<ActivityManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(SnapdragonDeviceClassifier)
        every { context.getSystemService(ActivityManager::class.java) } returns activityManager
        val memoryInfo = ActivityManager.MemoryInfo().apply { totalMem = 16L * 1024 * 1024 * 1024 }
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memoryInfo.totalMem
        }
        every { nativeRuntime.probe(any()) } returns 2
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `on-device image generation feature flag is enabled for StableDream stack`() {
        assertTrue(LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED)
    }

    @Test
    fun `CAP-005 Gen2 QNN supports SD15 and blocks SDXL with exact reason`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN2

        val port = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime)
        val caps = port.getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, caps.sdxlNpuSupported)
        assertEquals(
            "Requires Snapdragon 8 Gen 3+ with QNN.",
            caps.disabledReasonsByModelId[LocalImageGenerationModelId.SDXL_QNN]
        )
    }

    @Test
    fun `CAP-006 Gen3plus QNN supports SD15 and SDXL`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN3_PLUS

        val port = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime)
        val caps = port.getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.YES, caps.sdxlNpuSupported)
    }

    @Test
    fun `CAP-007 QNN probe failure blocks all local image pack downloads`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN3_PLUS
        every { nativeRuntime.probe(LocalImageGenerationBackendKind.QNN_NPU) } returns 0

        val port = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime)
        val caps = port.getCapabilities()

        assertEquals(LocalImageGenTriState.NO, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, caps.sdxlNpuSupported)
    }

    @Test
    fun `CAP-008 insufficient RAM blocks all local image pack downloads with the literal diagnostic`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN3_PLUS
        val memoryInfo = ActivityManager.MemoryInfo().apply { totalMem = 4L * 1024 * 1024 * 1024 }
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memoryInfo.totalMem
        }

        val port = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime)
        val caps = port.getCapabilities()

        assertTrue(caps.diagnostics.any { it.contains("Insufficient RAM for local diffusion (need >= 8 GiB).") })
        assertEquals(LocalImageGenTriState.NO, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, caps.sdxlNpuSupported)
    }
}
