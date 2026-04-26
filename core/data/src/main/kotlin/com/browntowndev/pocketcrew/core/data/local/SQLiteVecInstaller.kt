package com.browntowndev.pocketcrew.core.data.local

import android.content.Context
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory.ConfigurationOptions
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import java.io.File
import java.util.zip.ZipFile

internal object SQLiteVecInstaller {
    private const val SQLITE_VEC_LIBRARY_NAME = "libsqlite_vec"
    private const val SQLITE_VEC_LIBRARY_FILE_NAME = "$SQLITE_VEC_LIBRARY_NAME.so"
    internal const val SQLITE_VEC_ENTRY_POINT = "sqlite3_vec_init"

    fun createOpenHelperFactory(context: Context): SupportSQLiteOpenHelper.Factory {
        val extensionPath = sqliteVecExtensionPath(context)
        return RequerySQLiteOpenHelperFactory(
            listOf(
                object : ConfigurationOptions {
                    override fun apply(configuration: SQLiteDatabaseConfiguration): SQLiteDatabaseConfiguration {
                        return configureSQLiteVecExtension(configuration, extensionPath)
                    }
                },
            ),
        )
    }

    fun createEmbeddingTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS document_embeddings
            USING vec0(
                id TEXT PRIMARY KEY,
                vector float[384]
            )
            """.trimIndent(),
        )
    }

    fun createMemoryEmbeddingTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS memory_embeddings
            USING vec0(
                id TEXT PRIMARY KEY,
                vector float[384]
            )
            """.trimIndent(),
        )
    }

    internal fun sqliteVecExtensionPath(context: Context): String {
        val nativeLibrary = File(context.applicationInfo.nativeLibraryDir, SQLITE_VEC_LIBRARY_FILE_NAME)
        if (nativeLibrary.exists()) {
            return nativeLibrary.absolutePath.removeSuffix(".so")
        }
        return installSQLiteVecExtension(
            apkSourceFiles = buildList {
                add(File(context.applicationInfo.sourceDir))
                context.applicationInfo.splitSourceDirs
                    ?.map(::File)
                    ?.let(::addAll)
            },
            outputDirectory = context.noBackupFilesDir,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        )
    }

    internal fun configureSQLiteVecExtension(
        configuration: SQLiteDatabaseConfiguration,
        extensionPath: String,
    ): SQLiteDatabaseConfiguration {
        configuration.customExtensions.add(
            SQLiteCustomExtension(
                extensionPath,
                SQLITE_VEC_ENTRY_POINT,
            ),
        )
        return configuration
    }

    internal fun installSQLiteVecExtension(
        apkSourceFiles: List<File>,
        outputDirectory: File,
        supportedAbis: List<String>,
    ): String {
        val extractedLibrary = File(outputDirectory, SQLITE_VEC_LIBRARY_FILE_NAME)
        val apkLibrary = findApkLibrary(apkSourceFiles, supportedAbis)
        if (!extractedLibrary.exists() || extractedLibrary.length() != apkLibrary.size) {
            outputDirectory.mkdirs()
            ZipFile(apkLibrary.apkSourceFile).use { zipFile ->
                zipFile.getInputStream(zipFile.getEntry(apkLibrary.entryName)).use { input ->
                    extractedLibrary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            extractedLibrary.setReadable(true, false)
            extractedLibrary.setExecutable(true, false)
        }
        return extractedLibrary.absolutePath.removeSuffix(".so")
    }

    private fun findApkLibrary(
        apkSourceFiles: List<File>,
        supportedAbis: List<String>,
    ): ApkLibrary {
        val candidateEntries = supportedAbis.map { abi -> "lib/$abi/$SQLITE_VEC_LIBRARY_FILE_NAME" }
        apkSourceFiles
            .filter(File::exists)
            .forEach { apkSourceFile ->
                ZipFile(apkSourceFile).use { zipFile ->
                    candidateEntries.forEach { entryName ->
                        val entry = zipFile.getEntry(entryName)
                        if (entry != null) {
                            return ApkLibrary(
                                apkSourceFile = apkSourceFile,
                                entryName = entryName,
                                size = entry.size,
                            )
                        }
                    }
                }
            }
        throw IllegalStateException("Unable to find $SQLITE_VEC_LIBRARY_FILE_NAME in installed APK sources")
    }

    private data class ApkLibrary(
        val apkSourceFile: File,
        val entryName: String,
        val size: Long,
    )
}
