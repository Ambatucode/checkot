package com.app.checkot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.app.checkot.model.BookingStatus

object NotificationHelper {

    private const val CHANNEL_ID = "checkot_bookings"
    private const val CHANNEL_NAME = "Booking Updates"
    private const val CHANNEL_DESC = "Notifications for booking status updates and new bookings"
    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(0)

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
            .setSmallIcon(com.app.checkot.R.drawable.ic_notification)
            .setContentTitle("Booking Pending!")
            .setContentText("Your booking for $serviceSummary has been submitted.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your booking for $serviceSummary has been submitted. The shop owner will review it shortly."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationIdCounter.incrementAndGet(), notification)
    }

    fun showStatusChangeNotification(context: Context, serviceSummary: String, newStatus: BookingStatus) {
        val (title, body) = when (newStatus) {
            BookingStatus.CONFIRMED -> Pair(
                "Booking Confirmed!",
                "Your booking for $serviceSummary has been confirmed by the shop."
            )
            BookingStatus.IN_PROGRESS -> Pair(
                "Service In Progress",
                "Your $serviceSummary is now being worked on!"
            )
            BookingStatus.COMPLETED -> Pair(
                "Service Completed!",
                "Your $serviceSummary is done. Your car is ready for pickup!"
            )
            BookingStatus.CANCELLED -> Pair(
                "Booking Cancelled",
                "Your booking for $serviceSummary has been cancelled."
            )
            else -> return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.app.checkot.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationIdCounter.incrementAndGet(), notification)
    }

    fun showNewBookingForOwnerNotification(context: Context, serviceSummary: String, carDetails: String) {
        val body = "New booking received: $serviceSummary — $carDetails"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.app.checkot.R.drawable.ic_notification)
            .setContentTitle("New Booking Received!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationIdCounter.incrementAndGet(), notification)
    }

    /**
     * Show a generic notification from an FCM data payload.
     */
    fun showFCMNotification(context: Context, title: String, body: String, pendingIntent: PendingIntent? = null) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.app.checkot.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        val notification = builder.build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationIdCounter.incrementAndGet(), notification)
    }
}
