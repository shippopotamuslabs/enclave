package com.browntowndev.enclave.core.data.device

import android.os.Build
import com.browntowndev.enclave.domain.model.device.SnapdragonSocClassifier
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.domain.port.device.SnapdragonTierPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapdragonTierPortImpl
    @Inject
    constructor() : SnapdragonTierPort {
        override fun currentTier(): SnapdragonTier {
            val haystack =
                listOfNotNull(
                    Build.HARDWARE,
                    Build.BOARD,
                    Build.SOC_MODEL,
                    Build.SOC_MANUFACTURER,
                    System.getProperty("ro.board.platform"),
                    System.getProperty("ro.soc.model"),
                ).joinToString(" ")
            return SnapdragonSocClassifier.tier(haystack)
        }
    }
