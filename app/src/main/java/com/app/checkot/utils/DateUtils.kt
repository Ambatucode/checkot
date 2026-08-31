package com.app.checkot.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    // DateTimeFormatter is immutable and thread-safe. We can safely reuse instances.
    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    private val timeFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Instant.ofEpochMilli(timestamp))
    }
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Instant.ofEpochMilli(timestamp))
    }
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Instant.ofEpochMilli(timestamp))
    }
}
