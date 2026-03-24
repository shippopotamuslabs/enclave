package com.browntowndev.pocketcrew.core.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileWriter

class HashingServiceTest {

    private lateinit var hashingService: HashingService

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        hashingService = HashingService()
    }

    @Test
    fun calculateMd5_returnsConsistentHash_forSameContent() {
        // Given: Create temp file with known content
        val testFile = File(tempDir, "test.txt")
        FileWriter(testFile).use { writer ->
            writer.write("hello world")
        }

        // When: Calculate MD5 twice
        val hash1 = hashingService.calculateMd5(testFile)
        val hash2 = hashingService.calculateMd5(testFile)

        // Then: Same content produces same hash
        assertEquals(hash1, hash2)
        // MD5 Base64 encoded is 24 characters
        assertEquals(24, hash1.length)
    }

    @Test
    fun calculateMd5_returnsHash_forEmptyFile() {
        // Given: Create empty temp file
        val testFile = File(tempDir, "empty.txt")
        testFile.createNewFile()

        // When: Calculate MD5
        val hash = hashingService.calculateMd5(testFile)

        // Then: Verify hash is computed (Base64 encoded is 24 chars)
        assertEquals(24, hash.length)
    }

    @Test
    fun calculateMd5_handlesLargeFiles() {
        // Given: Create a larger file (multi-block read with 8192 buffer)
        val testFile = File(tempDir, "large.txt")
        val content = "A".repeat(20000) // Larger than 8192 buffer
        FileWriter(testFile).use { writer ->
            writer.write(content)
        }

        // When: Calculate MD5
        val hash = hashingService.calculateMd5(testFile)

        // Then: Verify hash is computed correctly
        assertTrue(hash.isNotEmpty())
        assertEquals(24, hash.length) // Base64 encoded MD5 is 24 chars
    }

    @Test
    fun calculateMd5_differentContent_differentHash() {
        // Given: Two files with different content
        val file1 = File(tempDir, "file1.txt")
        val file2 = File(tempDir, "file2.txt")

        FileWriter(file1).use { it.write("content A") }
        FileWriter(file2).use { it.write("content B") }

        // When: Calculate hashes
        val hash1 = hashingService.calculateMd5(file1)
        val hash2 = hashingService.calculateMd5(file2)

        // Then: Verify different content produces different hashes
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun calculateMd5_sameContent_sameHash() {
        // Given: Two files with same content
        val file1 = File(tempDir, "file1.txt")
        val file2 = File(tempDir, "file2.txt")

        val content = "identical content"
        FileWriter(file1).use { it.write(content) }
        FileWriter(file2).use { it.write(content) }

        // When: Calculate hashes
        val hash1 = hashingService.calculateMd5(file1)
        val hash2 = hashingService.calculateMd5(file2)

        // Then: Verify same content produces same hash
        assertEquals(hash1, hash2)
    }

    @Test
    fun calculateMd5_handlesBinaryContent() {
        // Given: Create file with binary-like content
        val testFile = File(tempDir, "binary.bin")
        val binaryContent = ByteArray(256) { it.toByte() }
        testFile.writeBytes(binaryContent)

        // When: Calculate hash
        val hash = hashingService.calculateMd5(testFile)

        // Then: Verify hash is computed correctly
        assertTrue(hash.isNotEmpty())
        assertEquals(24, hash.length) // Base64 encoded
    }
}
