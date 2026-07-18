package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

/**
 * Revenue reporting period. Boundaries are calendar-based — since midnight,
 * since the start of the week, since the 1st of the month — not a rolling
 * 24h/7d/30d window. An owner asking "what did I make today?" means since
 * midnight, so the totals line up with their till at close of day.
 */
private enum class RevenuePeriod(val label: String) {
    TODAY("Today"),
    WEEK("Week"),
    MONTH("Month");

    /** Start-of-period timestamp in the device's local time zone. */
    fun startOf(now: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        when (this) {
            TODAY -> {}
            WEEK -> cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            MONTH -> cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}

// Revenue Tab
@Composable
fun OwnerRevenueTab(ownerViewModel: OwnerDashboardViewModel, paddingValues: PaddingValues) {
    val allBookings by ownerViewModel.allBookings.collectAsState()
    val allBookingsLoaded by ownerViewModel.allBookingsLoaded.collectAsState()
    var selectedPeriod by remember { mutableStateOf(RevenuePeriod.TODAY) }
    val now = System.currentTimeMillis()
    val filteredBookings = remember(allBookings, selectedPeriod) {
        // Revenue is earned when a wash is completed, not when it was booked, so
        // the period is measured against completedAt.
        val cutoff = selectedPeriod.startOf(now)
        allBookings.filter { it.status == BookingStatus.COMPLETED && (it.completedAt ?: 0L) >= cutoff }
    }
    val totalRevenue = filteredBookings.sumOf { it.price }
    val bookingCount = filteredBookings.size
    val averagePerBooking = if (bookingCount > 0) totalRevenue / bookingCount else 0.0
    // The whole tab is one LazyColumn: the analytics sections below the stat
    // cards don't fit a phone viewport, so the tab body itself must scroll.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                RevenuePeriod.entries.forEach { period ->
                    val isSelected = selectedPeriod == period
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPeriod = period },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        label = { Text(period.label) }
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Total Revenue", style = MaterialTheme.typography.titleLarge)
                    Text(text = "₱${String.format("%,.2f", totalRevenue)}", style = MaterialTheme.typography.displaySmall)
                    Text(text = "${bookingCount} completed bookings", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null)
                        Text(text = "₱${String.format("%,.0f", averagePerBooking)}", style = MaterialTheme.typography.headlineSmall)
                        Text(text = "Avg/Booking", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val pendingRevenue = allBookings.filter { it.status == BookingStatus.PENDING || it.status == BookingStatus.CONFIRMED }.sumOf { it.price }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                        Text(text = "₱${String.format("%,.0f", pendingRevenue)}", style = MaterialTheme.typography.headlineSmall)
                        Text(text = "Pending", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val startOfToday = RevenuePeriod.TODAY.startOf(now)
                val todayCarCount = allBookings.count {
                    it.status == BookingStatus.COMPLETED && it.completedAt != null && it.completedAt >= startOfToday
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null)
                        Text(text = todayCarCount.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text(text = "Cars Today", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            // Average Service Duration
            val completedWithDurations = allBookings.filter {
                it.status == BookingStatus.COMPLETED && it.inProgressAt != null && it.completedAt != null
            }
            val avgDurationMin = if (completedWithDurations.isNotEmpty()) {
                completedWithDurations.sumOf { it.completedAt!! - it.inProgressAt!! } / completedWithDurations.size / 60000
            } else 0L

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Average Service Duration", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(
                            text = if (avgDurationMin > 0) "${avgDurationMin} min" else "N/A",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }
        }
        item {
            // Peak Hours
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Peak Hours", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            val hourLabels = listOf("8AM", "9AM", "10AM", "11AM", "12PM", "1PM", "2PM", "3PM", "4PM", "5PM")
            val hourCounts = hourLabels.mapIndexed { i, _ ->
                val hour = i + 8
                allBookings.count { b ->
                    try {
                        BookingUtils.parseTimeSlotToHourMinute(b.timeSlot).first == hour
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            val maxHourCount = hourCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    hourLabels.forEachIndexed { i, label ->
                        val count = hourCounts[i]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(40.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(18.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = count.toFloat() / maxHourCount),
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small
                                ) {}
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
        item {
            // Service Type Breakdown
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Service Breakdown", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            val serviceCounts = filteredBookings
                .flatMap { it.resolvedServiceNames() }
                .groupingBy { it }.eachCount()
                .toList().sortedByDescending { it.second }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (serviceCounts.isEmpty()) {
                        Text("No completed bookings for this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    } else {
                        serviceCounts.forEach { (name, count) ->
                            val pct = (count.toFloat() / filteredBookings.size * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f))
                                Text("$count ($pct%)", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Recent Transactions", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(
            items = filteredBookings.take(10),
            key = { it.bookingId }
        ) { booking ->
            TransactionItem(booking = booking)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (!allBookingsLoaded) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (filteredBookings.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No completed bookings for this period")
                }
            }
        }
    }
}
@Composable
fun TransactionItem(booking: Booking) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = booking.displayServiceNames(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(text = DateUtils.formatDate(booking.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(text = "₱${booking.price}", style = MaterialTheme.typography.titleLarge)
        }
    }
}
