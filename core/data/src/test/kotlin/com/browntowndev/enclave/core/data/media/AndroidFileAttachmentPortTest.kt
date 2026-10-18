package com.browntowndev.enclave.core.data.media

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34], shadows = [AndroidFileAttachmentPortTest.FileExtensionMimeTypeShadow::class])
class AndroidFileAttachmentPortTest {
    private lateinit var context: Context
    private lateinit var provider: FakeMediaProvider
    private lateinit var port: AndroidFileAttachmentPort

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = FakeMediaProvider()
        // registerProviderInternal alone only records the provider in the
        // resolver's map; the provider's own authority stays null, so the
        // framework's authority validation rejects every call with
        // "The authority media does not match the one of the contentProvider:
        // null". Attach a ProviderInfo first (the same sequence Robolectric's
        // ContentProviderController performs) so the provider knows its
        // authority is "media".
        val providerInfo =
            ProviderInfo().apply {
                authority = "media"
                packageName = context.packageName
                name = FakeMediaProvider::class.java.name
            }
        provider.attachInfo(context, providerInfo)
        ShadowContentResolver.registerProviderInternal("media", provider)
        port = AndroidFileAttachmentPort(context)
    }

    @Test
    fun `readFileContent returns metadata and text for text-plain file`() =
        runTest {
            val uriStr = "content://media/external/file/1"
            val fileName = "test.txt"
            val fileContent = "Hello World"
            val mimeType = "text/plain"
            provider.register(uriStr, fileName, mimeType, fileContent.toByteArray())

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(fileContent, result.content)
        }

    @Test
    fun `readFileContent returns metadata and text for csv file`() =
        runTest {
            val uriStr = "content://media/external/file/2"
            val fileName = "test.csv"
            val fileContent = "col1,col2\nval1,val2"
            val mimeType = "text/csv"
            provider.register(uriStr, fileName, mimeType, fileContent.toByteArray())

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(fileContent, result.content)
        }

    @Test
    fun `readFileContent returns metadata and text for json file`() =
        runTest {
            val uriStr = "content://media/external/file/4"
            val fileName = "test.json"
            val fileContent = "{\"key\": \"value\"}"
            val mimeType = "application/json"
            provider.register(uriStr, fileName, mimeType, fileContent.toByteArray())

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(fileContent, result.content)
        }

    @Test
    fun `readFileContent returns Base64 encoded string for unsupported mime type`() =
        runTest {
            val uriStr = "content://media/external/file/3"
            val mimeType = "application/pdf"
            val fileContent = "PDF content"
            val fileName = "test.pdf"
            provider.register(uriStr, fileName, mimeType, fileContent.toByteArray())

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(
                "BASE64:" + android.util.Base64.encodeToString(fileContent.toByteArray(), android.util.Base64.NO_WRAP),
                result.content,
            )
        }

    @Test
    fun `readFileContent handles file scheme URIs`() =
        runTest {
            // FileExtensionMimeTypeShadow types file:// URIs by extension via
            // MimeTypeMap; Robolectric's extension table starts empty, so
            // register the .txt mapping here.
            Shadows
                .shadowOf(MimeTypeMap.getSingleton())
                .addExtensionMimeTypeMapping("txt", "text/plain")

            val fileContent = "File content"
            val file = File.createTempFile("test", ".txt")
            file.writeText(fileContent)
            val uriStr = file.toURI().toString()

            val result = port.readFileContent(uriStr)

            assertEquals(file.name, result.name)
            assertEquals("File content", result.content)
        }

    /**
     * In-memory [ContentProvider] standing in for the media store: serves the
     * display name, MIME type, and bytes registered per URI.
     */
    private class FakeMediaProvider : ContentProvider() {
        private data class Entry(
            val displayName: String?,
            val mimeType: String?,
            val bytes: ByteArray,
        )

        private val entries = mutableMapOf<String, Entry>()

        fun register(
            uri: String,
            displayName: String?,
            mimeType: String?,
            bytes: ByteArray,
        ) {
            entries[uri] = Entry(displayName, mimeType, bytes)
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            val entry = entries[uri.toString()] ?: return null
            val displayName = entry.displayName ?: return null
            val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
            cursor.addRow(arrayOf(displayName))
            return cursor
        }

        override fun getType(uri: Uri): String? = entries[uri.toString()]?.mimeType

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor? {
            val entry = entries[uri.toString()] ?: return null
            val file = File.createTempFile("attachment", null)
            file.writeBytes(entry.bytes)
            file.deleteOnExit()
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    /**
     * Supplies, at the framework boundary, the answer the original MockK test
     * stubbed: a MIME type for a file:// URI. Production asks the
     * [ContentResolver] for the type and treats an unresolvable type as binary;
     * a file:// URI has no provider to ask, so getType returns null (true of the
     * stock Robolectric shadow and of the real framework alike) and the port's
     * file-scheme handling — name from the path, text decoding — would never be
     * exercised. This shadow resolves the type from the file extension via
     * [MimeTypeMap] (the platform's extension-typing idiom) only when no
     * provider answered, so the file-scheme test runs with the same text/plain
     * typing the MockK stub specified, without mocking the resolver.
     * Content-scheme URIs are untouched: the provider's answer always wins.
     *
     * Nested inside the test class on purpose: as a top-level class, JUnit
     * discovery scans it as a test candidate and fails resolving its
     * Android-typed supertype outside the Robolectric sandbox.
     */
    @Implements(ContentResolver::class)
    class FileExtensionMimeTypeShadow : ShadowContentResolver() {
        @Implementation
        override fun getType(uri: Uri): String? {
            val provided = super.getType(uri)
            if (provided != null) return provided
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) return null
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()) ?: return null
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}
