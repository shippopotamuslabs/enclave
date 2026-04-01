import re
file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Make it use MockK's spyk or just avoid the issue. The reviewer explicitly said:
# "you didn't fix the spyk antipattern mentioned by the Gemini review."
# Let's extract the formatter into a top-level function, or just assert on `buildSubText()`?
# Oh! The subText is generated internally. We can just test `buildSubText()` on `DownloadProgressTracker`? No, we are testing `DownloadNotificationManager.createForegroundInfoForSnapshot`.

# How can we mock `ForegroundInfo` creation without spyk?
# Let's mock `Notification.Builder` and `NotificationCompat.Builder` so `createNotification` succeeds.
content = content.replace(
    "class DownloadNotificationManagerTest {",
    """import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import io.mockk.mockkConstructor
import org.junit.jupiter.api.AfterEach
import android.os.Build
class DownloadNotificationManagerTest {"""
)

# And in setup
setup_str = """
    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)

        mockkConstructor(Notification.Builder::class)
        every { anyConstructed<Notification.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        every { anyConstructed<Notification.Builder>().build() } returns mockk(relaxed=true)

        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkStatic(Icon::class)
        every { Icon.createWithResource(any<Context>(), any<Int>()) } returns mockk(relaxed=true)
    }
"""
content = re.sub(r'    @BeforeEach.*?    \}', setup_str, content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)
