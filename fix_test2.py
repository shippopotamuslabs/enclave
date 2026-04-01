import sys

file_path = "core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/DownloadNotificationManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

import re
# The test failed at line 173 etc because of "Requires Android runtime - use Robolectric for full testing" RuntimeException which might be related to Builder not mockable etc.
# The review said: "Since the newly added tests successfully use mockk for Android dependencies (PendingIntent, Context), they don't actually need Robolectric. You should remove the class-level @Disabled annotation and apply it only to the specific test methods that still depend on the Android runtime, or update those remaining tests to use mocks."
# Wait, they fail with RuntimeException inside `Notification.Builder`! The class tries to use `Notification.Builder(context, CHANNEL_ID)` which isn't mocked and throws `RuntimeException("Stub!")` because of Android.jar being a stub in tests.

# Let's fix `DownloadNotificationManagerTest.kt`. If the tests for the snapshot throw, it's because `createForegroundInfoForSnapshot` internally calls `createForegroundInfoForProgress`, which calls `createNotification`, which uses `NotificationCompat.Builder` or `Notification.Builder` and throws Stub exception without Robolectric.

# The review specifically says:
# "You added three new test cases ... but annotated the entire class with @Disabled. These new tests will never run. Since the newly added tests successfully use mockk for Android dependencies (PendingIntent, Context), they don't actually need Robolectric. You should remove the class-level @Disabled annotation and apply it only to the specific test methods that still depend on the Android runtime, or update those remaining tests to use mocks."

# Oh, the reviewer THOUGHT they don't need Robolectric, but they actually DO need Robolectric because they call into `createNotification` which touches `NotificationCompat.Builder`!
# BUT we can mock `NotificationCompat.Builder` using mockkConstructor or mockkStatic, or just apply `@Disabled` to them? No, we shouldn't `@Disabled` our newly added tests! We can use `spyk` to mock `createForegroundInfoForProgress` so it never touches `Notification.Builder`!
# Let's put `spyk` back and only mock `createForegroundInfoForProgress`.

content = content.replace(
    "notificationManager = DownloadNotificationManager(mockContext, mockNotificationManager)",
    "notificationManager = spyk(DownloadNotificationManager(mockContext, mockNotificationManager))"
)

# And add back the verify with spyk. BUT earlier the reviewer complained about the "spyk antipattern".
# Wait, the other review comment said: "you didn't fix the spyk antipattern mentioned by the Gemini review"
# How to avoid `spyk`? The only other way is to refactor `DownloadNotificationManager` so we can test the formatter, OR we mock the Android components properly.
# Let's mock the Android components.

# Actually, `mockkStatic(NotificationCompat.Builder::class)` might be needed? No, standard `mockkConstructor` works.
