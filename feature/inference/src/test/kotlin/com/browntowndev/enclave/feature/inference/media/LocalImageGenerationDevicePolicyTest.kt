package com.browntowndev.enclave.feature.inference.media

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import com.browntowndev.enclave.feature.inference.ShadowFakeNativeLocalImageGenRuntime
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class LocalImageGenerationDevicePolicyTest {
    private lateinit var context: Context
    private lateinit var nativeRuntime: NativeLocalImageGenRuntime
    private lateinit var activityManager: ActivityManager

    @BeforeEach
    fun setUp() {
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a"))
        context = ApplicationProvider.getApplicationContext()
        nativeRuntime = NativeLocalImageGenRuntime(context)
        activityManager = context.getSystemService(ActivityManager::class.java)
        setTotalMemory(16L * 1024 * 1024 * 1024)
        // The shadow's @Resetter does not fire between tests under the JUnit 5
        // Robolectric extension, so clear its recorded state explicitly.
        ShadowFakeNativeLocalImageGenRuntime.probeResults.clear()
        ShadowFakeNativeLocalImageGenRuntime.probeCalls.clear()
        ShadowFakeNativeLocalImageGenRuntime.generateCalls.clear()
        ShadowFakeNativeLocalImageGenRuntime.generateHandler = null
        ShadowFakeNativeLocalImageGenRuntime.lastError = null
        ShadowFakeNativeLocalImageGenRuntime.progress = null
        ShadowFakeNativeLocalImageGenRuntime.defaultProbeResult = 2
    }

    private fun setTotalMemory(totalMem: Long) {
        shadowOf(activityManager).setMemoryInfo(
            ActivityManager.MemoryInfo().apply { this.totalMem = totalMem },
        )
    }

    /** Drives the real SnapdragonDeviceClassifier through Build fields (board codename per tier). */
    private fun setTier(tier: SnapdragonTier) {
        val board =
            when (tier) {
                SnapdragonTier.GEN3_PLUS -> "pineapple"
                SnapdragonTier.GEN2 -> "kalama"
                SnapdragonTier.GEN1 -> "lahaina"
                SnapdragonTier.UNKNOWN -> "unknown-board"
            }
        ShadowBuild.setHardware("qcom")
        ShadowBuild.setBoard(board)
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", "")
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", "")
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
        setTier(SnapdragonTier.GEN3_PLUS)
        setTotalMemory(4L * 1024 * 1024 * 1024)

        val port = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime)
        val caps = port.getCapabilities()

        assertTrue(caps.diagnostics.any { it.contains("Insufficient RAM for local diffusion (need >= 8 GiB).") })
        assertEquals(LocalImageGenTriState.NO, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.NO, caps.sdxlNpuSupported)
    }

    @Test
    fun `CAP-010 LocalImageGenerationCapabilityPortImpl exposes juggernautXlNpuSupported NO on Gen2`() {
        setTier(SnapdragonTier.GEN2)

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
        setTier(SnapdragonTier.GEN1)

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.NO, caps.sd15NpuSupported)
        assertEquals(LocalImageGenTriState.YES, caps.sd15CpuSupported)
        assertEquals(LocalImageGenerationBackendKind.CPU_MNN, caps.recommendedBackend)
        assertEquals(LocalImageGenerationModelId.SD15_CPU, caps.recommendedModelId)
        assertTrue(caps.diagnostics.any { it.contains("Hexagon V73") })
    }

    @Test
    fun `CAP-012 GEN2 tier keeps SD15 NPU available when QNN probe passes`() {
        setTier(SnapdragonTier.GEN2)

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15NpuSupported)
        assertEquals(LocalImageGenerationBackendKind.QNN_NPU, caps.recommendedBackend)
        assertEquals(LocalImageGenerationModelId.SD15_QNN, caps.recommendedModelId)
    }

    @Test
    fun `CAP-013 GEN3_PLUS tier keeps SD15 NPU available when QNN probe passes`() {
        setTier(SnapdragonTier.GEN3_PLUS)

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15NpuSupported)
        assertEquals(LocalImageGenerationBackendKind.QNN_NPU, caps.recommendedBackend)
    }

    @Test
    fun `MNN probe one marks SD15 CPU fallback available`() {
        setTier(SnapdragonTier.UNKNOWN)
        ShadowFakeNativeLocalImageGenRuntime.probeResults[LocalImageGenerationBackendKind.QNN_NPU] = 0
        ShadowFakeNativeLocalImageGenRuntime.probeResults[LocalImageGenerationBackendKind.CPU_MNN] = 1

        val caps = LocalImageGenerationCapabilityPortImpl(context, nativeRuntime).getCapabilities()

        assertEquals(LocalImageGenTriState.YES, caps.sd15CpuSupported)
        assertEquals(LocalImageGenerationBackendKind.CPU_MNN, caps.recommendedBackend)
    }
}
