package com.browntowndev.enclave.core.data.device

import android.app.ActivityManager
import android.content.Context
import com.browntowndev.enclave.domain.model.device.DeviceMemorySnapshot
import com.browntowndev.enclave.domain.port.device.DeviceMemoryProbePort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeviceMemoryProbePort
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DeviceMemoryProbePort {
        override fun probe(): DeviceMemorySnapshot {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            if (activityManager == null) {
                return constrainedSnapshot()
            }
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            if (memoryInfo.totalMem <= 0L) {
                return constrainedSnapshot()
            }
            val (swapTotalBytes, swapFreeBytes) = readSwapBytes()
            return DeviceMemorySnapshot(
                totalRamBytes = memoryInfo.totalMem,
                availableRamBytes = memoryInfo.availMem,
                swapTotalBytes = swapTotalBytes,
                swapFreeBytes = swapFreeBytes,
                isLowMemory = memoryInfo.lowMemory,
            )
        }

        private fun constrainedSnapshot(): DeviceMemorySnapshot =
            DeviceMemorySnapshot(
                totalRamBytes = 0L,
                availableRamBytes = 0L,
                swapTotalBytes = 0L,
                swapFreeBytes = 0L,
                isLowMemory = true,
            )

        private fun readSwapBytes(): Pair<Long, Long> {
            val lines =
                runCatching { File("/proc/meminfo").readLines() }
                    .getOrElse { return 0L to 0L }
            var swapTotalKb = 0L
            var swapFreeKb = 0L
            for (line in lines) {
                when {
                    line.startsWith("SwapTotal:") ->
                        swapTotalKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") ->
                        swapFreeKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
                }
            }
            return (swapTotalKb * 1024L) to (swapFreeKb * 1024L)
        }
    }
