package com.app.checkot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.atomic.AtomicInteger

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "checkot_bookings"
        private const val CHANNEL_NAME = "Booking Updates"
        private const val CHANNEL_DESC = "Notifications for booking status updates and new bookings"
        private val notificationIdCounter = AtomicInteger(0)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // ----------------------------------------------------------------
    //  TOKEN MANAGEMENT
    // ----------------------------------------------------------------
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveFcmToken(token)
    }

    private fun saveFcmToken(token: String) {
        // onNewToken can fire while signed out (fresh install, token rotation).
        // We can't write without a uid, so the login path (AuthViewModel /
        // OwnerDashboardViewModel) re-uploads the current token after sign-in.
        val user = Firebase.auth.currentUser ?: return
        // merge (not update) so the write succeeds even if the user doc doesn't
        // exist yet and doesn't get rejected by field-shape rules.
        Firebase.firestore.collection("users").document(user.uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "FCM token saved to Firestore") }
            .addOnFailureListener { Log.e(TAG, "Failed to save FCM token: ${it.message}") }
    }

    // ----------------------------------------------------------------
    //  MESSAGE HANDLING
    // ----------------------------------------------------------------
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "onMessageReceived called — from: ${remoteMessage.from}")

        // ----------------------------------------------------------
        // CASE 1: The message has a "notification" block.
        //
        //   • If the app is in the FOREGROUND → onMessageReceived is
        //     called and we build the notification ourselves.
        //   • If the app is in the BACKGROUND or KILLED → the system
        //     shows the notification automatically and the data
        //     payload is delivered in the intent extras when the user
        //     taps the notification.
        //
        // CASE 2: The message is data-only (no "notification" block).
        //
        //   • Foreground / background → onMessageReceived is called.
        //   • KILLED → the message is DROPPED by Android. There is
        //     nothing any app code can do about this.
        //
        // Because of Case 2, you MUST send a combined payload
        // (notification + data) from your server / Cloud Function
        // if you want notifications when the app is swiped away.
        // ----------------------------------------------------------

        val data = remoteMessage.data

        // Extract title/body — prefer data payload, fall back to notification payload
        val title = data["title"]
            ?: remoteMessage.notification?.title
            ?: "Car Wash Update"
        val body  = data["body"]
            ?: remoteMessage.notification?.body
            ?: "Your booking has been updated"
        val bookingId = data["bookingId"]

        Log.d(TAG, "Title=$title  Body=$body  BookingId=$bookingId")

        // If the notification block is present AND the app is NOT in
        // the foreground, the system already showed the notification
        // for us, so we should not duplicate it.
        if (remoteMessage.notification != null && !isAppInForeground()) {
            Log.d(TAG, "System already displayed notification (app not foreground). Skipping.")
            return
        }

        // If we reach here the app IS in the foreground, or it was a
        // data-only message while the app was alive. Show it manually.
        showNotification(title, body, bookingId)
    }

    // ----------------------------------------------------------------
    //  NOTIFICATION BUILDER
    // ----------------------------------------------------------------
    private fun showNotification(title: String, body: String, bookingId: String?) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.app.checkot.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#FF6200EE"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // Deep-link into the app when the notification is tapped
        val tapIntent = Intent(this, com.app.checkot.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!bookingId.isNullOrEmpty()) {
                putExtra("bookingId", bookingId)
            }
        }
        val pending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pending)

        // Use bookingId hash so the same booking updates the same notification;
        // fall back to a counter for messages without a bookingId.
        val notificationId = bookingId?.hashCode() ?: notificationIdCounter.incrementAndGet()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    // ----------------------------------------------------------------
    //  FOREGROUND CHECK
    // ----------------------------------------------------------------
    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
