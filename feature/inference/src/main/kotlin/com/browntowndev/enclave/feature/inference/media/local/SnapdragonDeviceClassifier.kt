package com.browntowndev.enclave.feature.inference.media.local

import android.os.Build
import com.browntowndev.enclave.domain.model.device.SnapdragonSocClassifier
import com.browntowndev.enclave.domain.model.device.SnapdragonTier

internal object SnapdragonDeviceClassifier {
    fun tier(): SnapdragonTier = SnapdragonSocClassifier.tier(deviceHaystack())

    fun isLikelySnapdragon(): Boolean = tier() != SnapdragonTier.UNKNOWN

    private fun deviceHaystack(): String =
        listOfNotNull(
            Build.HARDWARE,
            Build.BOARD,
            Build.SOC_MODEL,
            Build.SOC_MANUFACTURER,
            System.getProperty("ro.board.platform"),
            System.getProperty("ro.soc.model"),
        ).joinToString(" ")
}
