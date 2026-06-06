package com.browntowndev.enclave.feature.inference.media

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilityPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.feature.inference.media.local.SnapdragonDeviceClassifier
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalImageGenerationCapabilityPortImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val nativeRuntime: NativeLocalImageGenRuntime,
    ) : LocalImageGenerationCapabilityPort {
        override fun getCapabilities(): LocalImageGenerationCapabilities {
            val diagnostics = mutableListOf<String>()
            val arm64 = "arm64-v8a" in Build.SUPPORTED_ABIS.toSet()
            if (!arm64) {
                diagnostics += "Requires arm64-v8a ABI."
            }

            val totalMem = readTotalMemoryBytes()
            val minRam = 8L * 1024 * 1024 * 1024
            if (totalMem < minRam) {
                diagnostics += "Insufficient RAM for local diffusion (need >= 8 GiB)."
            }

            val tier = SnapdragonDeviceClassifier.tier()
            val snapdragon = tier != SnapdragonTier.UNKNOWN
            diagnostics += "Snapdragon tier: $tier."

            val qnnProbe = probeBackend(LocalImageGenerationBackendKind.QNN_NPU, diagnostics, "QNN")
            val mnnProbe = probeBackend(LocalImageGenerationBackendKind.CPU_MNN, diagnostics, "MNN")

            val npuAvailable = resolveNpuAvailable(snapdragon, qnnProbe, diagnostics)
            val sd15Npu = resolveSd15Npu(arm64, totalMem, minRam, npuAvailable, snapdragon)
            val sd15Cpu = resolveSd15Cpu(arm64, totalMem, minRam, mnnProbe)
            val sdxlNpu = resolveSdxlNpu(arm64, totalMem, minRam, tier, npuAvailable)

            val (recommendedBackend, recommendedModelId) = recommendBackend(sd15Npu, sd15Cpu)

            val disabledReasons =
                buildMap {
                    if (sdxlNpu == LocalImageGenTriState.NO &&
                        npuAvailable == LocalImageGenTriState.YES &&
                        tier != SnapdragonTier.GEN3_PLUS
                    ) {
                        put(
                            LocalImageGenerationModelId.SDXL_QNN,
                            LocalImageGenerationCapabilityPolicy.SDXL_BLOCKED_REASON,
                        )
                    }
                }

            return LocalImageGenerationCapabilities(
                snapdragonTier = tier,
                npuAvailable = npuAvailable,
                sd15NpuSupported = sd15Npu,
                sd15CpuSupported = sd15Cpu,
                sdxlNpuSupported = sdxlNpu,
                recommendedBackend = recommendedBackend,
                recommendedModelId = recommendedModelId,
                diagnostics = diagnostics,
                disabledReasonsByModelId = disabledReasons,
            )
        }

        private fun readTotalMemoryBytes(): Long {
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return 0L
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            return memoryInfo.totalMem
        }

        private fun probeBackend(
            backend: LocalImageGenerationBackendKind,
            diagnostics: MutableList<String>,
            label: String,
        ): Int =
            runCatching { nativeRuntime.probe(backend) }
                .getOrElse {
                    diagnostics += "$label probe failed: ${it.message}"
                    0
                }

        private fun resolveNpuAvailable(
            snapdragon: Boolean,
            qnnProbe: Int,
            diagnostics: MutableList<String>,
        ): LocalImageGenTriState =
            when {
                !snapdragon -> LocalImageGenTriState.NO
                qnnProbe >= 2 -> LocalImageGenTriState.YES
                qnnProbe == 1 -> LocalImageGenTriState.UNKNOWN
                else -> {
                    diagnostics += "QNN libraries not packaged (probe=0)."
                    LocalImageGenTriState.NO
                }
            }

        private fun resolveSd15Npu(
            arm64: Boolean,
            totalMem: Long,
            minRam: Long,
            npuAvailable: LocalImageGenTriState,
            snapdragon: Boolean,
        ): LocalImageGenTriState =
            when {
                !arm64 || totalMem < minRam -> LocalImageGenTriState.NO
                npuAvailable == LocalImageGenTriState.YES -> LocalImageGenTriState.YES
                npuAvailable == LocalImageGenTriState.UNKNOWN -> LocalImageGenTriState.UNKNOWN
                snapdragon -> LocalImageGenTriState.UNKNOWN
                else -> LocalImageGenTriState.NO
            }

        private fun resolveSd15Cpu(
            arm64: Boolean,
            totalMem: Long,
            minRam: Long,
            mnnProbe: Int,
        ): LocalImageGenTriState =
            when {
                !arm64 || totalMem < minRam -> LocalImageGenTriState.NO
                mnnProbe >= 2 -> LocalImageGenTriState.YES
                mnnProbe == 1 -> LocalImageGenTriState.UNKNOWN
                else -> LocalImageGenTriState.NO
            }

        private fun resolveSdxlNpu(
            arm64: Boolean,
            totalMem: Long,
            minRam: Long,
            tier: SnapdragonTier,
            npuAvailable: LocalImageGenTriState,
        ): LocalImageGenTriState =
            when {
                !arm64 || totalMem < minRam -> LocalImageGenTriState.NO
                tier == SnapdragonTier.GEN3_PLUS && npuAvailable == LocalImageGenTriState.YES ->
                    LocalImageGenTriState.YES
                tier == SnapdragonTier.GEN3_PLUS && npuAvailable == LocalImageGenTriState.UNKNOWN ->
                    LocalImageGenTriState.UNKNOWN
                else -> LocalImageGenTriState.NO
            }

        private fun recommendBackend(
            sd15Npu: LocalImageGenTriState,
            sd15Cpu: LocalImageGenTriState,
        ): Pair<LocalImageGenerationBackendKind, LocalImageGenerationModelId?> =
            when {
                sd15Npu == LocalImageGenTriState.YES ->
                    LocalImageGenerationBackendKind.QNN_NPU to LocalImageGenerationModelId.SD15_QNN
                sd15Cpu == LocalImageGenTriState.YES ->
                    LocalImageGenerationBackendKind.CPU_MNN to LocalImageGenerationModelId.SD15_CPU
                else -> LocalImageGenerationBackendKind.NONE to null
            }
    }
