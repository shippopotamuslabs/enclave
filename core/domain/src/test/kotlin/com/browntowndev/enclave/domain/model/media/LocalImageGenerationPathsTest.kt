package com.browntowndev.enclave.domain.model.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LocalImageGenerationPathsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun resolveQnnModelDir_prefersNestedSd15Qnn() {
        val parent = tempDir.toFile()
        val nested = File(parent, LocalImageGenerationPaths.SD15_QNN_SUBDIR).apply { mkdirs() }
        touchQnn(nested)
        assertEquals(nested.absolutePath, LocalImageGenerationPaths.resolveQnnModelDir(parent.absolutePath))
    }

    @Test
    fun resolveQnnModelDir_prefersPackArtifactChildDirectory() {
        val parent = tempDir.toFile()
        val packDir = File(parent, LocalImageGenerationPaths.SD15_QNN_SUBDIR).apply { mkdirs() }
        val artifactDir = File(packDir, "qnn_models_8gen2").apply { mkdirs() }
        touchQnn(artifactDir)

        assertEquals(artifactDir.absolutePath, LocalImageGenerationPaths.resolveQnnModelDir(parent.absolutePath))
    }

    @Test
    fun resolveQnnModelDir_fallsBackToParentWhenBinsInParent() {
        val parent = tempDir.toFile()
        touchQnn(parent)
        assertEquals(parent.absolutePath, LocalImageGenerationPaths.resolveQnnModelDir(parent.absolutePath))
    }

    @Test
    fun resolveMnnModelDir_prefersNestedSd15Cpu() {
        val parent = tempDir.toFile()
        val nested = File(parent, LocalImageGenerationPaths.SD15_CPU_SUBDIR).apply { mkdirs() }
        touchMnn(nested)
        assertEquals(nested.absolutePath, LocalImageGenerationPaths.resolveMnnModelDir(parent.absolutePath))
    }

    @Test
    fun hasQnnArtifacts_falseWhenMissingFiles() {
        val dir = File(tempDir.toFile(), "empty").apply { mkdirs() }
        assertFalse(LocalImageGenerationPaths.hasQnnArtifacts(dir))
    }

    @Test
    fun hasMnnArtifacts_trueWhenPresent() {
        val dir = tempDir.toFile()
        touchMnn(dir)
        assertTrue(LocalImageGenerationPaths.hasMnnArtifacts(dir))
    }

    private fun touchQnn(dir: File) {
        listOf("clip_v2.mnn", "token_emb.bin", "pos_emb.bin", "unet.bin", "vae_decoder.bin", "tokenizer.json").forEach {
            File(dir, it).writeText("x")
        }
    }

    private fun touchMnn(dir: File) {
        listOf("clip.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.json").forEach {
            File(dir, it).writeText("x")
        }
    }
}
