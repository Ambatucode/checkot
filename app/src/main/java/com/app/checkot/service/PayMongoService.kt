package com.app.checkot.service

import android.util.Base64
import android.util.Log
import com.app.checkot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class PayMongoCheckoutResponse(
    val checkoutId: String,
    val checkoutUrl: String
)

object PayMongoService {
    private const val TAG = "PayMongoService"
    private const val PAYMONGO_CHECKOUT_URL = "https://api.paymongo.com/v1/checkout_sessions"

    /**
     * Creates a PayMongo Checkout Session for a slot reservation fee.
     * @param amountPesos The reservation fee in PHP (e.g. 50.0)
     * @param bookingId The associated booking document ID
     * @param description Brief payment description
     * @param customerName Customer's full name
     * @param customerEmail Customer's email
     * @param customerPhone Customer's phone number
     * @return [PayMongoCheckoutResponse] containing the checkout URL and ID
     */
    suspend fun createCheckoutSession(
        amountPesos: Double,
        bookingId: String,
        description: String = "Slot Reservation Fee",
        customerName: String = "Checkot Customer",
        customerEmail: String = "customer@checkot.app",
        customerPhone: String = "09123456789"
    ): Result<PayMongoCheckoutResponse> = withContext(Dispatchers.IO) {
        val secretKey = BuildConfig.PAYMONGO_SECRET_KEY
        if (secretKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("PayMongo Secret Key is missing in BuildConfig."))
        }

        try {
            val amountCentavos = (amountPesos * 100).roundToInt()
            
            // Build JSON payload
            val root = JSONObject()
            val dataObj = JSONObject()
            val attrObj = JSONObject()

            // Billing info
            val billingObj = JSONObject().apply {
                put("name", customerName.ifBlank { "Checkot Customer" })
                put("email", customerEmail.ifBlank { "customer@checkot.app" })
                put("phone", customerPhone.ifBlank { "09123456789" })
            }
            attrObj.put("billing", billingObj)
            attrObj.put("send_email_receipt", false)
            attrObj.put("show_description", true)
            attrObj.put("show_line_items", true)
            attrObj.put("description", description)

            // Line items
            val lineItem = JSONObject().apply {
                put("currency", "PHP")
                put("amount", amountCentavos)
                put("name", "Slot Reservation Fee")
                put("quantity", 1)
            }
            val lineItemsArray = JSONArray().apply { put(lineItem) }
            attrObj.put("line_items", lineItemsArray)

            // Payment methods
            val paymentMethods = JSONArray().apply {
                put("gcash")
                put("paymaya")
                put("card")
                put("dob")
                put("qrph")
            }
            attrObj.put("payment_method_types", paymentMethods)

            // Redirect URLs
            attrObj.put("success_url", "checkot://payment_success?booking_id=$bookingId")
            attrObj.put("cancel_url", "checkot://payment_cancel?booking_id=$bookingId")

            dataObj.put("attributes", attrObj)
            root.put("data", dataObj)

            val jsonPayload = root.toString()
            Log.d(TAG, "Creating PayMongo checkout session for booking $bookingId ($amountPesos PHP)")

            // HTTP Request
            val url = URL(PAYMONGO_CHECKOUT_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                val authString = "$secretKey:"
                val authEncoded = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $authEncoded")
                connectTimeout = 15000
                readTimeout = 15000
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }

            if (responseCode in 200..299) {
                val responseJson = JSONObject(responseText)
                val data = responseJson.getJSONObject("data")
                val checkoutId = data.getString("id")
                val attributes = data.getJSONObject("attributes")
                val checkoutUrl = attributes.getString("checkout_url")

                Log.d(TAG, "✅ Checkout session created successfully: $checkoutUrl")
                Result.success(PayMongoCheckoutResponse(checkoutId, checkoutUrl))
            } else {
                Log.e(TAG, "❌ PayMongo API Error ($responseCode): $responseText")
                Result.failure(Exception("PayMongo API returned code $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network/JSON error during PayMongo checkout creation", e)
            Result.failure(e)
        }
    }
}
