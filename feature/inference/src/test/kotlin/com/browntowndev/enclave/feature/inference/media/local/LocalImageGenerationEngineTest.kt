package com.browntowndev.enclave.feature.inference.media.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.feature.inference.FakeMnnCpuStableDiffusionBackend
import com.browntowndev.enclave.feature.inference.FakeQnnStableDiffusionBackend
import com.browntowndev.enclave.feature.inference.ShadowFakeNativeLocalImageGenRuntime
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], shadows = [ShadowFakeNativeLocalImageGenRuntime::class])
class LocalImageGenerationEngineTest {
    @Test
    fun cancel_forwardsToNativeRuntimeAndBackends() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val nativeRuntime = NativeLocalImageGenRuntime(context)
        val qnnBackend = FakeQnnStableDiffusionBackend(nativeRuntime)
        val mnnBackend = FakeMnnCpuStableDiffusionBackend(nativeRuntime)
        val engine = LocalImageGenerationEngine(qnnBackend, mnnBackend, nativeRuntime)

        engine.cancel()

        assertEquals(1, ShadowFakeNativeLocalImageGenRuntime.cancelCount)
        assertEquals(1, qnnBackend.cancelCount)
        assertEquals(1, mnnBackend.cancelCount)
    }
}
