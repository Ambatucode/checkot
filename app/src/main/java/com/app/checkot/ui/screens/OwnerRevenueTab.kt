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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

// Revenue Tab
@Composable
fun OwnerRevenueTab(ownerViewModel: OwnerDashboardViewModel, paddingValues: PaddingValues) {
    val allBookings by ownerViewModel.allBookings.collectAsState()
    var selectedPeriod by remember { mutableStateOf("today") }
    val now = System.currentTimeMillis()
    val oneDay = 86400000
    val oneWeek = oneDay * 7
    val oneMonth = oneDay * 30
    val filteredBookings = when (selectedPeriod) {
        "today" -> allBookings.filter { it.createdAt > now - oneDay && it.status == BookingStatus.COMPLETED }
        "week" -> allBookings.filter { it.createdAt > now - oneWeek && it.status == BookingStatus.COMPLETED }
        "month" -> allBookings.filter { it.createdAt > now - oneMonth && it.status == BookingStatus.COMPLETED }
        else -> allBookings.filter { it.status == BookingStatus.COMPLETED }
    }
    val totalRevenue = filteredBookings.sumOf { it.price }
    val bookingCount = filteredBookings.size
    val averagePerBooking = if (bookingCount > 0) totalRevenue / bookingCount else 0.0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("Today", "Week", "Month").forEachIndexed { index, period ->
                FilterChip(
                    selected = when (selectedPeriod) {
                        "today" -> index == 0
                        "week" -> index == 1
                        "month" -> index == 2
                        else -> false
                    },
                    onClick = { selectedPeriod = when (index) { 0 -> "today"; 1 -> "week"; 2 -> "month"; else -> "today" } },
                    label = { Text(period) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
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
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShowChart, contentDescription = null)
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
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Recent Transactions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                items = filteredBookings.take(10),
                key = { it.bookingId }
            ) { booking ->
                TransactionItem(booking = booking)
            }
            if (filteredBookings.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No completed bookings for this period")
                    }
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
