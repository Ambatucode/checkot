package com.app.misproject

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun formatDate(timestamp: Long): String {
        val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return date.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        return date.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val date = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return date.format(Date(timestamp))
    }
}