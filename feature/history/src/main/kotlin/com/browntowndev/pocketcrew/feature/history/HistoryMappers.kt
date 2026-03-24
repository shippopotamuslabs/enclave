package com.browntowndev.pocketcrew.feature.history

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Maps domain Chat model to presentation HistoryChat model.
 */
fun Chat.toHistoryChat(): HistoryChat {
    return HistoryChat(
        id = id,
        name = name,
        lastMessageDateTime = formatLastModified(lastModified),
        isPinned = pinned
    )
}

/**
 * Formats a Date to a human-readable string for display in the history list.
 */
private fun formatLastModified(date: Date): String {
    val calendar = Calendar.getInstance()
    val today = calendar.clone() as Calendar

    calendar.time = date

    val dateCalendar = Calendar.getInstance().apply { time = date }

    return when {
        isSameDay(dateCalendar, today) -> {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Today, ${timeFormat.format(date)}"
        }
        isYesterday(dateCalendar, today) -> {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Yesterday, ${timeFormat.format(date)}"
        }
        isSameYear(dateCalendar, today) -> {
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            dateFormat.format(date)
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            dateFormat.format(date)
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(date: Calendar, today: Calendar): Boolean {
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(date, yesterday)
}

private fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}
