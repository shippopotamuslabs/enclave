package com.browntowndev.enclave.feature.moa.service

import android.content.Context
import android.os.PowerManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPowerManager

/**
 * Recording [InferenceServiceStarter] fake (ENC-28). The production class is
 * `open` for exactly this seam; the fake never touches the real Context.
 */
class FakeInferenceServiceStarter(
    context: Context,
) : InferenceServiceStarter(context) {
    data class StartCall(
        val chatId: String,
        val userMessage: String,
        val stateJson: String?,
    )

    val startCalls = mutableListOf<StartCall>()
    var startFailure: Throwable? = null

    var stopCount = 0
        private set

    override fun startService(
        chatId: String,
        userMessage: String,
        stateJson: String?,
        userMessageId: String?,
        assistantMessageId: String?,
    ) {
        startCalls += StartCall(chatId, userMessage, stateJson)
        startFailure?.let { throw it }
    }

    override fun stopService() {
        stopCount++
    }
}

/**
 * Robolectric shadow extending the stock [ShadowPowerManager] with a settable
 * thermal-headroom forecast (the stock shadow does not implement
 * `PowerManager.getThermalHeadroom`). Tests set [headroomForecast] per case.
 */
@Implements(PowerManager::class)
class HeadroomPowerManagerShadow : ShadowPowerManager() {
    @Implementation
    fun getThermalHeadroom(forecastSeconds: Int): Float = headroomForecast

    companion object {
        @JvmField
        var headroomForecast: Float = 0.5f
    }
}
