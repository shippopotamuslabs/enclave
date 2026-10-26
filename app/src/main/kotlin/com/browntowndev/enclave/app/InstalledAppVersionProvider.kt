package com.browntowndev.enclave.app

import android.content.Context
import com.browntowndev.enclave.domain.port.update.InstalledAppVersionPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [InstalledAppVersionPort] read from the package info (ENC-250).
 *
 * Follows the [DistributionConfigurationImpl] precedent: `:app` owns the
 * platform read so domain code never touches `PackageManager`. Unknown version
 * reads as 0, which is older than any real release, so the check stays quiet.
 */
@Singleton
class InstalledAppVersionProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : InstalledAppVersionPort {
        override val installedVersionCode: Int
            get() =
                runCatching {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    packageInfo.longVersionCode.toInt()
                }.getOrDefault(0)
    }
