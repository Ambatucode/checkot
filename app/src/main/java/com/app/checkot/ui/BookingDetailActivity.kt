package com.app.checkot.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.app.checkot.R

/**
 * Simple placeholder that displays the booking ID passed via the intent.
 * You can replace the UI with your Compose screen or a proper layout later.
 */
class BookingDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // For now we just create a TextView programmatically.
        val textView = TextView(this).apply {
            textSize = 20f
            val id = intent.getStringExtra("bookingId") ?: "(no id)"
            text = "Booking Detail\n\nBooking ID: $id"
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)
        // Optional: set a title in the action bar
        supportActionBar?.title = "Booking Detail"
    }
}
