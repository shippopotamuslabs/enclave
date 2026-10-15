package com.browntowndev.enclave.detekt

internal object TestPathFilters {
    private val TEST_PATH_MARKERS =
        listOf(
            "/test/",
            "/androidTest/",
        )

    fun isTestSource(path: String): Boolean =
        TEST_PATH_MARKERS.any { marker -> path.contains(marker, ignoreCase = true) }

    fun isViewModelTest(path: String): Boolean =
        isTestSource(path) && path.endsWith("ViewModelTest.kt", ignoreCase = true)
}
