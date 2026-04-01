import re

file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Remove the @Disabled annotations added in the previous broken state
# Restore the mockkConstructor configuration but this time properly
# ADD the fix for the permission denial check: Explicitly mock ContextCompat.checkSelfPermission!

setup_str = """
    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)

        io.mockk.mockkConstructor(Notification.Builder::class)
        io.mockk.every { anyConstructed<Notification.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setContentText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setSmallIcon(any<Int>()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setOngoing(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setSubText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().setStyle(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().addAction(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(Notification.ProgressStyle::class)
        io.mockk.every { anyConstructed<Notification.ProgressStyle>().setProgress(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<Notification.ProgressStyle>().setStyledByProgress(any()) } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(Notification.Action.Builder::class)
        io.mockk.every { anyConstructed<Notification.Action.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(androidx.core.app.NotificationCompat.Builder::class)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setContentTitle(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setContentText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setSmallIcon(any<Int>()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setOngoing(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setProgress(any(), any(), any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().setSubText(any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().addAction(any<Int>(), any(), any()) } returns mockk(relaxed=true)
        io.mockk.every { anyConstructed<androidx.core.app.NotificationCompat.Builder>().build() } returns mockk(relaxed=true)

        io.mockk.mockkStatic(android.graphics.drawable.Icon::class)
        io.mockk.every { android.graphics.drawable.Icon.createWithResource(any<Context>(), any<Int>()) } returns mockk(relaxed=true)

        io.mockk.mockkConstructor(NotificationChannel::class)
        io.mockk.every { anyConstructed<NotificationChannel>().description = any() } just io.mockk.runs
        io.mockk.every { anyConstructed<NotificationChannel>().setShowBadge(any()) } just io.mockk.runs
        io.mockk.every { anyConstructed<NotificationChannel>().id } returns "model_download_channel"

        io.mockk.mockkStatic(androidx.core.content.ContextCompat::class)
        io.mockk.every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @org.junit.jupiter.api.AfterEach
    fun teardown() {
        io.mockk.unmockkAll()
    }
"""

content = re.sub(r'    @BeforeEach.*?    \}', setup_str, content, flags=re.DOTALL)

# Add the explicit denial to the updateNotification_doesNothing_whenPermissionDenied test
content = content.replace(
"""    @Test
    fun updateNotification_doesNothing_whenPermissionDenied() {
        notificationManager.updateNotification(""",
"""    @Test
    fun updateNotification_doesNothing_whenPermissionDenied() {
        io.mockk.every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_DENIED
        notificationManager.updateNotification("""
)

# And fix the verify block to use exactly = 0
content = content.replace(
    "verify { mockNotificationManager.notify(any(), any<Notification>()) }",
    "verify(exactly = 0) { mockNotificationManager.notify(any(), any<Notification>()) }"
)

# Make sure all @Disabled are removed
content = content.replace('@Disabled("Requires Android runtime - use Robolectric for full testing")\n', '')
content = content.replace("    @Disabled\n", "")

# We don't need RobolectricExtension
content = content.replace("@org.junit.jupiter.api.extension.ExtendWith(org.robolectric.RobolectricExtension::class)\n@org.robolectric.annotation.Config(manifest=org.robolectric.annotation.Config.NONE)\n", "")


with open(file_path, "w") as f:
    f.write(content)
