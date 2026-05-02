package com.browntowndev.pocketcrew.core.data.media

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidShareMediaAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: AndroidShareMediaAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adapter = AndroidShareMediaAdapter(context)
        shadowOf(context as Application).clearNextStartedActivities()
    }

    @Test
    fun shareMedia_emptyUriList_doesNothing() {
        adapter.shareMedia(emptyList(), "image/png")

        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun shareMedia_singleFileUri_startsChooserWithConvertedContentUri() {
        val sourceFile = File(context.cacheDir, "share-single.png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        adapter.shareMedia(listOf(sourceFile.toURI().toString()), "image/png")

        val chooser = shadowOf(context as Application).nextStartedActivity
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        assertTrue(chooser?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        val targetIntent = chooser?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("image/png", targetIntent?.type)
        assertTrue(targetIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        val expectedUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            sourceFile,
        )
        val sharedUri = targetIntent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals(expectedUri, sharedUri)
    }

    @Test
    fun shareMedia_multipleContentUris_startsChooserWithSendMultipleIntent() {
        val firstUri = Uri.parse("content://example.media/first")
        val secondUri = Uri.parse("content://example.media/second")

        adapter.shareMedia(listOf(firstUri.toString(), secondUri.toString()), "video/*")

        val chooser = shadowOf(context as Application).nextStartedActivity
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        assertTrue(chooser?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        val targetIntent = chooser?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, targetIntent?.action)
        assertEquals("video/*", targetIntent?.type)
        assertTrue(targetIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        val sharedUris = targetIntent?.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals(listOf(firstUri, secondUri), sharedUris)
    }
}
