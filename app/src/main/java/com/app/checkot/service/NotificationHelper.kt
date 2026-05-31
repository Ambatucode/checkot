package com.app.checkot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.app.checkot.model.BookingStatus

object NotificationHelper {

    private const val CHANNEL_ID = "checkot_bookings"
    private const val CHANNEL_NAME = "Booking Updates"
    private const val CHANNEL_DESC = "Notifications for booking status updates and new bookings"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showBookingCreatedNotification(context: Context, serviceSummary: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Booking Confirmed! ✅")
            .setContentText("Your booking for $serviceSummary has been submitted.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your booking for $serviceSummary has been submitted. The shop owner will review it shortly."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showStatusChangeNotification(context: Context, serviceSummary: String, newStatus: BookingStatus) {
        val (title, body) = when (newStatus) {
            BookingStatus.CONFIRMED -> Pair(
                "Booking Confirmed! ✅",
                "Your booking for $serviceSummary has been confirmed by the shop."
            )
            BookingStatus.IN_PROGRESS -> Pair(
                "Service In Progress 🔧",
                "Your $serviceSummary is now being worked on!"
            )
            BookingStatus.COMPLETED -> Pair(
                "Service Completed! 🎉",
                "Your $serviceSummary is done. Your car is ready for pickup!"
            )
            BookingStatus.CANCELLED -> Pair(
                "Booking Cancelled ❌",
                "Your booking for $serviceSummary has been cancelled."
            )
            else -> return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showNewBookingForOwnerNotification(context: Context, serviceSummary: String, carDetails: String) {
        val body = "New booking received: $serviceSummary — $carDetails"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Booking Received! 📋")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
