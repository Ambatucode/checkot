package com.app.checkot.utils

import java.text.SimpleDateFormat
import java.util.*
object DateUtils {
    // SimpleDateFormat is expensive to construct; reuse one instance per
    // pattern instead of allocating on every list-item composition. All
    // callers are composables, so main-thread-only use is guaranteed
    // (SimpleDateFormat is not thread-safe).
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
}
