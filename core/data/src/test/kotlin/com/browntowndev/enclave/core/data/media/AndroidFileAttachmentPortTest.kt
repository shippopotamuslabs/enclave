package com.browntowndev.enclave.core.data.media

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.ByteArrayInputStream
import java.io.InputStream

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class AndroidFileAttachmentPortTest {
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private lateinit var port: AndroidFileAttachmentPort

    @BeforeEach
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        port = AndroidFileAttachmentPort(context)
    }

    @Test
    fun `readFileContent returns metadata and text for text-plain file`() =
        runTest {
            val uriStr = "content://media/external/file/1"
            val uri = Uri.parse(uriStr)
            val fileName = "test.txt"
            val fileContent = "Hello World"
            val mimeType = "text/plain"

            every { contentResolver.getType(uri) } returns mimeType

            // Mocking file name retrieval
            val cursor: Cursor = mockk()
            every { contentResolver.query(uri, null, null, null, null) } returns cursor
            every { cursor.moveToFirst() } returns true
            every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { cursor.getString(0) } returns fileName
            every { cursor.close() } just Runs

            // Mocking file content retrieval
            val inputStream: InputStream = ByteArrayInputStream(fileContent.toByteArray())
            every { contentResolver.openInputStream(uri) } returns inputStream

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(fileContent, result.content)
        }

    @Test
    fun `readFileContent returns metadata and text for csv file`() =
        runTest {
            val uriStr = "content://media/external/file/2"
            val uri = Uri.parse(uriStr)
            val fileName = "test.csv"
            val fileContent = "col1,col2\nval1,val2"
            val mimeType = "text/csv"

            every { contentResolver.getType(uri) } returns mimeType

            val cursor: Cursor = mockk()
            every { contentResolver.query(uri, null, null, null, null) } returns cursor
            every { cursor.moveToFirst() } returns true
            every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { cursor.getString(0) } returns fileName
            every { cursor.close() } just Runs

            val inputStream: InputStream = ByteArrayInputStream(fileContent.toByteArray())
            every { contentResolver.openInputStream(uri) } returns inputStream

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(fileContent, result.content)
        }

    @Test
    fun `readFileContent returns metadata and text for json file`() =
        runTest {
            val uriStr = "content://media/external/file/4"
            val uri = Uri.parse(uriStr)
            val fileName = "test.json"
            val fileContent = "{\"key\": \"value\"}"
            val mimeType = "application/json"

            every { contentResolver.getType(uri) } returns mimeType

            val cursor: Cursor = mockk()
            every { contentResolver.query(uri, null, null, null, null) } returns cursor
            every { cursor.moveToFirst() } returns true
            every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { cursor.getString(0) } returns fileName
            every { cursor.close() } just Runs

            val inputStream: InputStream = ByteArrayInputStream(fileContent.toByteArray())
            every { contentResolver.openInputStream(uri) } returns inputStream

            val result = port.readFileContent(uriStr)

            assertEquals(fileName, result.name)
            assertEquals(mimeType, result.mimeType)
            assertEquals(fileContent, result.content)
        }

    @Test
    fun `readFileContent returns Base64 encoded string for unsupported mime type`() =
        runTest {
            val uriStr = "content://media/external/file/3"
            val uri = Uri.parse(uriStr)
            val mimeType = "application/pdf"
            val fileContent = "PDF content"
            val fileName = "test.pdf"

            every { contentResolver.getType(uri) } returns mimeType

            val cursor: Cursor = mockk()
            every { contentResolver.query(uri, null, null, null, null) } returns cursor
            every { cursor.moveToFirst() } returns true
            every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { cursor.getString(0) } returns fileName
            every { cursor.close() } just Runs

            val inputStream: InputStream = ByteArrayInputStream(fileContent.toByteArray())
            every { contentResolver.openInputStream(uri) } returns inputStream

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
            val uriStr = "file:///storage/emulated/0/Download/test.txt"
            val uri = Uri.parse(uriStr)
            val fileContent = "File content"

            every { contentResolver.getType(uri) } returns "text/plain"
            every { contentResolver.query(uri, any(), any(), any(), any()) } returns null

            val inputStream: InputStream = ByteArrayInputStream(fileContent.toByteArray())
            every { contentResolver.openInputStream(uri) } returns inputStream

            val result = port.readFileContent(uriStr)

            assertEquals("test.txt", result.name)
            assertEquals("File content", result.content)
        }
}
