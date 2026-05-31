package com.app.checkot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "Car Wash Update",
                message = notification.body ?: "Your booking has been updated"
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("New FCM token: $token")
        // Save token to Firestore for the current user
        saveFcmToken(token)
    }

    private fun saveFcmToken(token: String) {
        val user = Firebase.auth.currentUser ?: return
        Firebase.firestore.collection("users").document(user.uid)
            .update("fcmToken", token)
            .addOnSuccessListener { println("✅ FCM token saved to Firestore") }
            .addOnFailureListener { println("❌ Failed to save FCM token: ${it.message}") }
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "checkot_bookings"
        val notificationId = System.currentTimeMillis().toInt()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Booking Updates",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
