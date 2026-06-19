package com.app.checkot.service

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends FCM push notifications directly from the app using the
 * FCM HTTP v1 API with a service account.
 *
 * SETUP:
 * 1. Go to Firebase Console → Project Settings → Service Accounts
 * 2. Click "Generate new private key" → download the JSON file
 * 3. Rename it to "service_account.json"
 * 4. Place it in: app/src/main/res/raw/service_account.json
 *
 * NOTE: For a production app you'd use a backend server instead.
 * This approach is fine for small / personal / school projects.
 */
object FCMSender {

    private const val TAG = "FCMSender"
    private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    // Your Firebase project ID (from google-services.json)
    private const val PROJECT_ID = "checkot-14700"

    private val FCM_URL =
        "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"

    /**
     * Send a push notification to a specific user by their Firestore userId.
     * Looks up their fcmToken from the "users" collection.
     */
    suspend fun sendToUser(
        context: Context,
        userId: String,
        title: String,
        body: String,
        bookingId: String = ""
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Look up the user's FCM token from Firestore
                val userDoc = Firebase.firestore
                    .collection("users")
                    .document(userId)
                    .get()
                    .await()

                val fcmToken = userDoc.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.w(TAG, "User $userId has no fcmToken")
                    return@withContext
                }

                // 2. Get an OAuth2 access token from the service account
                val accessToken = getAccessToken(context)

                // 3. Build the FCM message (COMBINED payload)
                val message = JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("token", fcmToken)

                        // notification block → system shows this even if app is killed
                        put("notification", JSONObject().apply {
                            put("title", title)
                            put("body", body)
                        })

                        // data block → available in onMessageReceived / intent extras
                        put("data", JSONObject().apply {
                            put("title", title)
                            put("body", body)
                            put("bookingId", bookingId)
                        })

                        // Android-specific settings
                        put("android", JSONObject().apply {
                            put("priority", "HIGH")
                            put("notification", JSONObject().apply {
                                put("channel_id", "checkot_bookings")
                                put("sound", "default")
                            })
                        })
                    })
                }

                // 4. Send the HTTP request to FCM
                val url = URL(FCM_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("Content-Type", "application/json; UTF-8")
                conn.doOutput = true

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(message.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    Log.d(TAG, "Push sent successfully to user $userId")
                } else {
                    val errorStream = conn.errorStream ?: conn.inputStream
                    val errorBody = BufferedReader(InputStreamReader(errorStream))
                        .readText()
                    Log.e(TAG, "FCM error ($responseCode): $errorBody")
                }
                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send push to user $userId", e)
            }
        }
    }

    /**
     * Get an OAuth2 access token using the service account JSON
     * stored in res/raw/service_account.json
     */
    private fun getAccessToken(context: Context): String {
        val resourceId = com.app.checkot.R.raw.service_account
        val inputStream = context.resources.openRawResource(resourceId)
        val credentials = GoogleCredentials
            .fromStream(inputStream)
            .createScoped(listOf(FCM_SCOPE))

        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}
