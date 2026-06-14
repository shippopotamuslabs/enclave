package com.browntowndev.enclave.app

import com.browntowndev.enclave.BuildConfig
import com.browntowndev.enclave.domain.port.config.DistributionConfigurationPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistributionConfigurationImpl
    @Inject
    constructor() : DistributionConfigurationPort {
        override val isPlayStoreFlavor: Boolean = BuildConfig.IS_PLAY_STORE_FLAVOR
    }
