package com.browntowndev.enclave.domain.model.config

enum class MediaTask(
    val kind: MediaCapability,
) {
    TEXT_TO_IMAGE(MediaCapability.IMAGE),
    EDIT_IMAGE(MediaCapability.IMAGE),
    TEXT_TO_VIDEO(MediaCapability.VIDEO),
    IMAGE_TO_VIDEO(MediaCapability.VIDEO),
}

fun Set<MediaTask>.toMask(): Int {
    var mask = 0
    for (task in this) {
        mask = mask or (1 shl task.ordinal)
    }
    return mask
}

fun Int.toMediaTasks(): Set<MediaTask> {
    val tasks = mutableSetOf<MediaTask>()
    for (task in MediaTask.entries) {
        if ((this and (1 shl task.ordinal)) != 0) {
            tasks.add(task)
        }
    }
    return tasks
}

/** Pre–tasks_mask installs and Firestore docs missing `tasks_mask` (mask `0`). */
fun MediaCapability.legacyTasksForUnknownMask(): Set<MediaTask> =
    when (this) {
        MediaCapability.IMAGE -> setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE)
        MediaCapability.VIDEO -> setOf(MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO)
        MediaCapability.MUSIC -> emptySet()
    }

fun Int.toMediaTasksOrLegacy(capability: MediaCapability): Set<MediaTask> =
    if (this == 0) {
        capability.legacyTasksForUnknownMask()
    } else {
        toMediaTasks()
    }
