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
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LocalImageGenerationDevicePolicyTest {
    private val context = mockk<Context>(relaxed = true)
    private val nativeRuntime = mockk<NativeLocalImageGenRuntime>(relaxed = true)
    private val activityManager = mockk<ActivityManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a"))
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

    // CAP-005, CAP-006, CAP-007 and MUT-002 are covered against the pure domain seam
    // `LocalImageGenerationCapabilityPolicy.resolve(...)` in
    // core/domain/.../media/LocalImageGenerationCapabilityPolicyTest.kt per their test_spec
    // "When ... eligibility is resolved" clauses. CAP-008 lives here because its "When" clause
    // names `LocalImageGenerationCapabilityPortImpl.getCapabilities()`.

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

    @Test
    fun `CAP-010 LocalImageGenerationCapabilityPortImpl exposes juggernautXlNpuSupported NO on Gen2`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN2

        val port = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime)
        val caps = port.getCapabilities()

        assertEquals(LocalImageGenTriState.NO, caps.juggernautXlNpuSupported)
        assertEquals(
            "Requires Snapdragon 8 Gen 3+ with QNN.",
            caps.disabledReasonsByModelId[LocalImageGenerationModelId.JUGGERNAUT_XL_QNN],
        )
    }

    @Test
    fun `CAP-011 GEN1 tier blocks SD15 NPU and recommends MNN CPU fallback even when QNN probe passes`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN1

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.NO, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.YES, caps.sd15CpuSupported)
        assertEquals(LocalImageGenerationBackendKind.CPU_MNN, caps.recommendedBackend)
        assertEquals(LocalImageGenerationModelId.SD15_CPU, caps.recommendedModelId)
        assertTrue(caps.diagnostics.any { it.contains("Hexagon V73") })
    }

    @Test
    fun `CAP-012 GEN2 tier keeps SD15 NPU available when QNN probe passes`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN2

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15NpuSupported)
        assertEquals(LocalImageGenerationBackendKind.QNN_NPU, caps.recommendedBackend)
        assertEquals(LocalImageGenerationModelId.SD15_QNN, caps.recommendedModelId)
    }

    @Test
    fun `CAP-013 GEN3_PLUS tier keeps SD15 NPU available when QNN probe passes`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.GEN3_PLUS

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15NpuSupported)
        assertEquals(LocalImageGenerationBackendKind.QNN_NPU, caps.recommendedBackend)
    }

    @Test
    fun `MNN probe one marks SD15 CPU fallback available`() {
        every { SnapdragonDeviceClassifier.tier() } returns SnapdragonTier.UNKNOWN
        every { nativeRuntime.probe(LocalImageGenerationBackendKind.QNN_NPU) } returns 0
        every { nativeRuntime.probe(LocalImageGenerationBackendKind.CPU_MNN) } returns 1

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15CpuSupported)
        assertEquals(LocalImageGenerationBackendKind.CPU_MNN, caps.recommendedBackend)
    }
}
