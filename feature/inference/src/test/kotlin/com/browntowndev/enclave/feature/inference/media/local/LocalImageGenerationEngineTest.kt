package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class LocalImageGenerationEngineTest {
    @Test
    fun cancel_forwardsToNativeRuntimeAndBackends() {
        val nativeRuntime = mockk<NativeLocalImageGenRuntime>(relaxed = true)
        val qnnBackend = mockk<QnnStableDiffusionBackend>(relaxed = true)
        val mnnBackend = mockk<MnnCpuStableDiffusionBackend>(relaxed = true)
        val engine = LocalImageGenerationEngine(qnnBackend, mnnBackend, nativeRuntime)

        engine.cancel()

        verify(exactly = 1) { nativeRuntime.cancel() }
        verify(exactly = 1) { qnnBackend.cancel() }
        verify(exactly = 1) { mnnBackend.cancel() }
    }
}