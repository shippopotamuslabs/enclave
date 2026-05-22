package com.browntowndev.enclave.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class OssLicenseEntry(
    val name: String,
    val version: String,
    val licenseType: String,
    val copyrightHolder: String,
    val licenseText: String,
)
