package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.UtilityType
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UtilityModelFileResolverTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun resolveUtilityModelPath_returnsDownloadedUtilityPath() = runTest {
        val modelsDir = File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
        val modelFile = File(modelsDir, "ggml-base.en.bin").apply {
            writeBytes(ByteArray(4))
        }
        val fetcher = fakeFetcher(
            listOf(
                utilityAsset(sizeInBytes = 4L),
            ),
        )
        val resolver = UtilityModelFileResolver(
            context = fakeContext(),
            modelConfigFetcher = fetcher,
        )

        val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

        assertEquals(modelFile.absolutePath, path)
    }

    @Test
    fun resolveUtilityModelPath_returnsNullWhenFileMissing() = runTest {
        val fetcher = fakeFetcher(listOf(utilityAsset(sizeInBytes = 4L)))
        val resolver = UtilityModelFileResolver(
            context = fakeContext(),
            modelConfigFetcher = fetcher,
        )

        val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

        assertNull(path)
    }

    @Test
    fun resolveUtilityModelPath_returnsNullWhenSizeMismatches() = runTest {
        File(tempDir, ModelConfig.MODELS_DIR).apply { mkdirs() }
            .resolve("ggml-base.en.bin")
            .writeBytes(ByteArray(2))
        val fetcher = fakeFetcher(listOf(utilityAsset(sizeInBytes = 4L)))
        val resolver = UtilityModelFileResolver(
            context = fakeContext(),
            modelConfigFetcher = fetcher,
        )

        val path = resolver.resolveUtilityModelPath(UtilityType.WHISPER)

        assertNull(path)
    }

    private fun fakeContext(): Context {
        return mockk {
            every { getExternalFilesDir(null) } returns tempDir
        }
    }

    private fun fakeFetcher(assets: List<LocalModelAsset>): ModelConfigFetcherPort {
        return mockk {
            coEvery { fetchRemoteConfig() } returns Result.success(assets)
        }
    }

    private fun utilityAsset(sizeInBytes: Long): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = LocalModelId(""),
                huggingFaceModelName = "ggerganov/whisper.cpp",
                remoteFileName = "ggml-base.en.bin",
                localFileName = "ggml-base.en.bin",
                sha256 = "sha",
                sizeInBytes = sizeInBytes,
                modelFileFormat = ModelFileFormat.BIN,
                source = DownloadSource.HUGGING_FACE,
                utilityType = UtilityType.WHISPER,
            ),
            configurations = emptyList(),
        )
    }
}
