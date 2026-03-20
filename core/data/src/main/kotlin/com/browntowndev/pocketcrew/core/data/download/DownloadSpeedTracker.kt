package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.util.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

data class SpeedSample(
    val timestamp: Long,
    val bytesDownloaded: Long
)

class DownloadSpeedTracker @Inject constructor(
    private val clock: Clock
) : DownloadSpeedTrackerPort {

    // Per-file speed samples - each file gets its own sample list
    private val fileSpeedSamples: ConcurrentHashMap<String, MutableList<SpeedSample>> = ConcurrentHashMap()

    // Aggregate samples for overall speed calculation
    private val aggregateSpeedSamples: MutableList<SpeedSample> = CopyOnWriteArrayList()

    private fun getOrCreateFileSamples(filename: String): MutableList<SpeedSample> {
        return fileSpeedSamples.getOrPut(filename) { CopyOnWriteArrayList() }
    }

    override fun calculateSpeedAndEta(
        filename: String,
        bytesDownloaded: Long,
        totalSize: Long
    ): Pair<Double, Long> {
        val currentTime = clock.currentTimeMillis()
        val samples = getOrCreateFileSamples(filename)

        // Add current sample for this specific file
        samples.add(SpeedSample(currentTime, bytesDownloaded))

        val tenSecondsAgo = currentTime - 10_000L
        val recentSamples = samples.filter { it.timestamp >= tenSecondsAgo }

        if (recentSamples.size < 2) {
            return Pair(0.0, -1L)
        }

        val oldestSample = recentSamples.first()
        val newestSample = recentSamples.last()

        val timeDiffMs = newestSample.timestamp - oldestSample.timestamp
        if (timeDiffMs <= 0) {
            return Pair(0.0, -1L)
        }

        val timeDiffSeconds = timeDiffMs / 1000.0
        val bytesDiff = newestSample.bytesDownloaded - oldestSample.bytesDownloaded

        if (bytesDiff <= 0) {
            return Pair(0.0, -1L)
        }

        val speedMBps = (bytesDiff / (1024.0 * 1024.0)) / timeDiffSeconds

        val remainingBytes = totalSize - bytesDownloaded
        if (remainingBytes <= 0) {
            return Pair(speedMBps, 0L)
        }

        val speedBytesPerSecond = bytesDiff / timeDiffSeconds
        if (speedBytesPerSecond <= 0) {
            return Pair(speedMBps, -1L)
        }

        val etaSeconds = (remainingBytes / speedBytesPerSecond).toLong()

        return Pair(speedMBps, etaSeconds)
    }

    override fun calculateAggregateSpeedAndEta(
        totalBytesDownloaded: Long,
        totalSize: Long
    ): Pair<Double, Long> {
        val currentTime = clock.currentTimeMillis()
        aggregateSpeedSamples.add(SpeedSample(currentTime, totalBytesDownloaded))

        val tenSecondsAgo = currentTime - 10_000L
        val recentSamples = aggregateSpeedSamples.filter { it.timestamp >= tenSecondsAgo }

        if (recentSamples.size < 2) {
            return Pair(0.0, -1L)
        }

        val oldestSample = recentSamples.first()
        val newestSample = recentSamples.last()

        val timeDiffMs = newestSample.timestamp - oldestSample.timestamp
        if (timeDiffMs <= 0) {
            return Pair(0.0, -1L)
        }

        val timeDiffSeconds = timeDiffMs / 1000.0
        val bytesDiff = newestSample.bytesDownloaded - oldestSample.bytesDownloaded

        if (bytesDiff <= 0) {
            return Pair(0.0, -1L)
        }

        val speedMBps = (bytesDiff / (1024.0 * 1024.0)) / timeDiffSeconds

        val remainingBytes = totalSize - totalBytesDownloaded
        if (remainingBytes <= 0) {
            return Pair(speedMBps, 0L)
        }

        val speedBytesPerSecond = bytesDiff / timeDiffSeconds
        if (speedBytesPerSecond <= 0) {
            return Pair(speedMBps, -1L)
        }

        val etaSeconds = (remainingBytes / speedBytesPerSecond).toLong()

        return Pair(speedMBps, etaSeconds)
    }

    override fun formatEta(seconds: Long): String {
        return when {
            seconds < 0 -> "Calculating..."
            seconds < 60 -> "< 1 min"
            seconds <  3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600.0} hours"
        }
    }

    override fun clear(filename: String) {
        fileSpeedSamples.remove(filename)
    }

    override fun clearAll() {
        fileSpeedSamples.clear()
        aggregateSpeedSamples.clear()
    }
}
