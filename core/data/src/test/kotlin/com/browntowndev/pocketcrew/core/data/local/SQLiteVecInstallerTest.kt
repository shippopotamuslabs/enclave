package com.browntowndev.pocketcrew.core.data.local

import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SQLiteVecInstallerTest {
    @Test
    fun installSQLiteVecExtension_apkContainsSupportedAbi_extractsLibraryAndReturnsSuffixlessPath(): Unit {
        val tempDir = Files.createTempDirectory("sqlite-vec-installer").toFile()
        val apkFile = File(tempDir, "base.apk")
        val outputDir = File(tempDir, "no_backup")
        val libraryBytes = byteArrayOf(1, 2, 3, 4)
        writeZipEntry(
            zipFile = apkFile,
            entryName = "lib/arm64-v8a/libsqlite_vec.so",
            bytes = libraryBytes,
        )

        val path = SQLiteVecInstaller.installSQLiteVecExtension(
            apkSourceFiles = listOf(apkFile),
            outputDirectory = outputDir,
            supportedAbis = listOf("arm64-v8a"),
        )

        assertEquals(File(outputDir, "libsqlite_vec").absolutePath, path)
        assertEquals(libraryBytes.toList(), File(outputDir, "libsqlite_vec.so").readBytes().toList())
    }

    @Test
    fun configureSQLiteVecExtension_configuration_addsNativeExtension(): Unit {
        val configuration = SQLiteDatabaseConfiguration(
            "/data/data/com.browntowndev.pocketcrew/databases/pocketcrew.db",
            SQLiteDatabase.OPEN_READWRITE,
        )
        val extensionPath = "/data/app/example/lib/arm64/libsqlite_vec"

        val updatedConfiguration = SQLiteVecInstaller.configureSQLiteVecExtension(
            configuration = configuration,
            extensionPath = extensionPath,
        )

        assertSame(configuration, updatedConfiguration)
        assertEquals(1, updatedConfiguration.customExtensions.size)
        assertEquals(extensionPath, updatedConfiguration.customExtensions.single().path)
        assertEquals(
            SQLiteVecInstaller.SQLITE_VEC_ENTRY_POINT,
            updatedConfiguration.customExtensions.single().entryPoint,
        )
    }

    private fun writeZipEntry(
        zipFile: File,
        entryName: String,
        bytes: ByteArray,
    ) {
        ZipOutputStream(zipFile.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(bytes)
            output.closeEntry()
        }
    }
}
