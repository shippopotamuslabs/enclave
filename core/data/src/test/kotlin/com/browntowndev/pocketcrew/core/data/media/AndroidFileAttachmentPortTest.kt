package com.browntowndev.pocketcrew.core.data.media

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidFileAttachmentPortTest {

    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private lateinit var port: AndroidFileAttachmentPort

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        port = AndroidFileAttachmentPort(context)
    }

    @Test
    fun `readFileContent returns metadata and text for text-plain file`() = runTest {
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
    fun `readFileContent returns metadata and text for csv file`() = runTest {
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

    @Test(expected = NotImplementedError::class)
    fun `readFileContent throws NotImplementedError for unsupported mime type`() = runTest {
        val uriStr = "content://media/external/file/3"
        val uri = Uri.parse(uriStr)
        val mimeType = "application/pdf"

        every { contentResolver.getType(uri) } returns mimeType

        port.readFileContent(uriStr)
    }

    @Test
    fun `readFileContent handles file scheme URIs`() = runTest {
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
