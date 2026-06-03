package com.browntowndev.enclave.core.data.profile.analysis

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.R
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProfileAnalysisNotificationFactoryTest {
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun build_setsProfileNotificationContentAndIcon() {
        val notification =
            ProfileAnalysisNotificationFactory.build(
                context = context,
                text = "Pass 2 of 4...",
            )

        assertEquals("Building psychological profile", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Pass 2 of 4...", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
