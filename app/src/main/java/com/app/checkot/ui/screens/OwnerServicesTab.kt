package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.theme.CheckotCardSurface
import com.app.checkot.ui.theme.CheckotTeal
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

const val MIN_SERVICE_DURATION_MIN = 20
const val MAX_SERVICE_DURATION_MIN = 180
const val MAX_SERVICE_DESCRIPTION_LEN = 150
// A shop must stay open at least this long — prevents absurdly short windows
// that would confuse clients (sanity floor for the no-active-bookings case).
const val MIN_WORKING_WINDOW_MIN = 60
internal const val SLOT_STEP_MIN = 30

/** Rounds [value] up to the next multiple of [step] (e.g. 9:45 → 10:00 on a 30-min grid). */
internal fun ceilToStep(value: Int, step: Int): Int = ((value + step - 1) / step) * step

/** Built-in default duration for a predefined service; 0 for custom services. */
internal fun defaultDurationMinutes(config: CustomServiceConfig): Int =
    ServiceType.values().find { it.name == config.serviceName }
        ?.let { BookingUtils.parseDurationMinutes(it.duration) } ?: 0

/**
 * Repairs legacy configs whose isCustom flag was lost by the old Firestore
 * field-name mismatch (stored as "custom", read as "isCustom"): a service
 * with no matching ServiceType is custom by definition. Saving persists the
 * repaired flag under the correct field name.
 */
internal fun normalizeConfigs(services: List<CustomServiceConfig>): List<CustomServiceConfig> =
    services.map { config ->
        val isCustom = config.isCustom || ServiceType.values().none { it.name == config.serviceName }
        config.copy(
            isCustom = isCustom,
            customName = if (isCustom && config.customName.isBlank()) config.displayName else config.customName
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerServicesTab(
    ownerViewModel: OwnerDashboardViewModel,
    paddingValues: PaddingValues,
    navController: NavController
) {
    val customization by ownerViewModel.shopCustomization.collectAsState()
    val allBookings by ownerViewModel.allBookings.collectAsState()
    var editedServices by remember { mutableStateOf<List<CustomServiceConfig>>(normalizeConfigs(customization.services)) }
    var bayCountText by remember { mutableStateOf(customization.bayCount.toString()) }
    var editedStaff by remember { mutableStateOf(customization.staffNames) }
    var staffNameInput by remember { mutableStateOf("") }
    // Service currently being edited in the modal (null = no dialog open).
    var editingService by remember { mutableStateOf<CustomServiceConfig?>(null) }
    var openMinutes by remember { mutableStateOf(customization.openMinutes) }
    var closeMinutes by remember { mutableStateOf(customization.closeMinutes) }
    var closedDates by remember { mutableStateOf(customization.closedDates) }
    var dayOverrides by remember { mutableStateOf(customization.dayOverrides) }
    var showAddDropdown by remember { mutableStateOf(false) }
    var showCustomNameDialog by remember { mutableStateOf(false) }
    var customServiceNameInput by remember { mutableStateOf("") }
    var isSavingServices by remember { mutableStateOf(false) }
    // Confirm before applying an hours change (friction against rapid re-saves)
    var showHoursConfirm by remember { mutableStateOf(false) }
    // Services whose duration field currently holds invalid/empty text
    var invalidDurationKeys by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val maxServices = 15

    // A price is invalid if below 150, above 5000, or 0.0 for custom services (no default)
    val hasInvalidPrice = editedServices.any { config ->
        (config.customPrice > 0.0 && config.customPrice < 150) ||
        config.customPrice > 5000 ||
        (config.isCustom && config.customPrice == 0.0)
    }
    // A duration is invalid if the field text is invalid, or the effective
    // value (saved value, else the built-in default) is outside 20..180
    val hasInvalidDuration = invalidDurationKeys.isNotEmpty() || editedServices.any { config ->
        val effective = if (config.durationMinutes > 0) config.durationMinutes
                        else defaultDurationMinutes(config)
        effective < MIN_SERVICE_DURATION_MIN || effective > MAX_SERVICE_DURATION_MIN
    }
    val bayCountChanged = bayCountText.toIntOrNull() != customization.bayCount
    // Every service must have a description so clients know what it is.
    val hasBlankDescription = editedServices.any { it.description.isBlank() }

    // Active bookings constrain how far hours can be narrowed — same spirit as
    // "can't delete a service that has active bookings". Opening can't move past
    // the earliest booking's START, and closing can't move before the latest
    // booking's START. closeMinutes is the last bookable slot START, so a
    // service may legitimately finish after close — only new slot starts are
    // blocked (consistent with slot generation).
    val activeBookingWindow: Pair<Int, Int>? = remember(allBookings) {
        val todayStart = BookingUtils.startOfDay(System.currentTimeMillis())
        // Only bookings from today onward constrain the hours — stale/past
        // bookings (e.g. a never-cancelled PENDING from last week) shouldn't
        // block an owner from adjusting their schedule.
        val active = allBookings.filter { b ->
            (b.status == BookingStatus.PENDING ||
                b.status == BookingStatus.CONFIRMED ||
                b.status == BookingStatus.IN_PROGRESS) &&
                b.bookingDate >= todayStart
        }
        val starts = active.mapNotNull { b ->
            val hm = runCatching { BookingUtils.parseTimeSlotToHourMinute(b.timeSlot) }.getOrNull()
                ?: return@mapNotNull null
            hm.first * 60 + hm.second
        }
        if (starts.isEmpty()) null else starts.min() to starts.max()
    }
    val earliestBookingStart = activeBookingWindow?.first
    val latestBookingStart = activeBookingWindow?.second

    // Guardrails: window must be at least MIN_WORKING_WINDOW_MIN long, opening
    // can't start after an existing booking's start, closing can't move before
    // an existing booking's start.
    val windowValid = closeMinutes - openMinutes >= MIN_WORKING_WINDOW_MIN
    val openCoversBookings = earliestBookingStart == null || openMinutes <= earliestBookingStart
    val closeCoversBookings = latestBookingStart == null || closeMinutes >= latestBookingStart
    val hoursValid = windowValid && openCoversBookings && closeCoversBookings

    val hoursChanged = openMinutes != customization.openMinutes || closeMinutes != customization.closeMinutes
    val closedDatesChanged = closedDates != customization.closedDates
    val dayOverridesChanged = dayOverrides != customization.dayOverrides
    // The booking guardrail only matters when the permanent hours actually
    // change. Closing a date or adding an hours override must never be blocked
    // by it — that was a deadlock: a booking at the last slot made close < end,
    // which disabled the whole Save button.
    val canSave = (editedServices != customization.services || bayCountChanged || hoursChanged ||
        closedDatesChanged || dayOverridesChanged || editedStaff != customization.staffNames) &&
        !hasInvalidPrice && !hasInvalidDuration && !hasBlankDescription &&
        (!hoursChanged || hoursValid)

    LaunchedEffect(customization) {
        editedServices = normalizeConfigs(customization.services)
        bayCountText = customization.bayCount.toString()
        openMinutes = customization.openMinutes
        closeMinutes = customization.closeMinutes
        closedDates = customization.closedDates
        dayOverrides = customization.dayOverrides
        editedStaff = customization.staffNames
        invalidDurationKeys = emptySet()
    }

    val atMaxLimit = editedServices.size >= maxServices
    val availableTypesToAdd = ServiceType.values().filter { type ->
        type != ServiceType.CUSTOM && editedServices.none { it.serviceName == type.name }
    }

    // Persist the current edits. Shared by the direct-save path and the
    // hours-change confirmation path.
    val performSave: () -> Unit = {
        isSavingServices = true
        val bayCount = bayCountText.toIntOrNull() ?: customization.bayCount
        // Persist the effective duration for legacy services the owner didn't
        // touch (their field shows the default)
        val normalizedServices = editedServices.map { config ->
            if (config.durationMinutes > 0) config
            else config.copy(durationMinutes = defaultDurationMinutes(config))
        }
        val updated = customization.copy(
            services = normalizedServices,
            bayCount = bayCount,
            openMinutes = openMinutes,
            closeMinutes = closeMinutes,
            closedDates = closedDates,
            dayOverrides = dayOverrides,
            staffNames = editedStaff
        )
        ownerViewModel.saveShopCustomization(updated)
        scope.launch {
            kotlinx.coroutines.delay(1500)
            isSavingServices = false
        }
    }

    if (showHoursConfirm) {
        AlertDialog(
            onDismissRequest = { showHoursConfirm = false },
            title = { Text("Update working hours?") },
            text = {
                Text(
                    "Your new hours will be ${BookingUtils.minutesToSlotLabel(openMinutes)} – " +
                    "${BookingUtils.minutesToSlotLabel(closeMinutes)}.\n\n" +
                    "Clients will see the new hours immediately. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = { showHoursConfirm = false; performSave() }) {
                    Text("Yes, update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHoursConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomNameDialog) {
        AlertDialog(
            onDismissRequest = { showCustomNameDialog = false },
            title = { Text("Custom Service Name") },
            text = {
                OutlinedTextField(
                    value = customServiceNameInput,
                    onValueChange = { if (it.length <= 30) customServiceNameInput = it },
                    label = { Text("Service name") },
                    placeholder = { Text("e.g. Headlight Polish") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = customServiceNameInput.trim()
                        if (name.isNotEmpty()) {
                            val id = "custom_${System.currentTimeMillis()}"
                            editedServices = editedServices + CustomServiceConfig(
                                serviceName = id,
                                displayName = name,
                                customName = name,
                                customPrice = 0.0,
                                isCustom = true
                            )
                            customServiceNameInput = ""
                            showCustomNameDialog = false
                        }
                    },
                    enabled = customServiceNameInput.trim().isNotEmpty()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
        item {
        // Manage Services — page title + count + add button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Manage Services", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${editedServices.size}/$maxServices services",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (atMaxLimit) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Box {
                OutlinedButton(
                    onClick = { showAddDropdown = true },
                    enabled = !atMaxLimit,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Service", style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(
                    expanded = showAddDropdown,
                    onDismissRequest = { showAddDropdown = false },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    availableTypesToAdd.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                editedServices = editedServices + CustomServiceConfig(
                                    serviceName = type.name,
                                    displayName = type.displayName,
                                    customPrice = type.price,
                                    durationMinutes = BookingUtils.parseDurationMinutes(type.duration),
                                    description = type.defaultDescription
                                )
                                showAddDropdown = false
                            }
                        )
                    }
                    if (availableTypesToAdd.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Others (Custom Service)")
                            }
                        },
                        onClick = {
                            showAddDropdown = false
                            customServiceNameInput = ""
                            showCustomNameDialog = true
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Service Bays — its own compact card, separated from the page title.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CheckotCardSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Garage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CheckotTeal
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Service Bays",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val current = bayCountText.toIntOrNull() ?: 1
                        if (current > 1) {
                            bayCountText = (current - 1).toString()
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    enabled = (bayCountText.toIntOrNull() ?: 1) > 1
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease bay count", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = bayCountText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = {
                        val current = bayCountText.toIntOrNull() ?: 1
                        if (current < 10) {
                            bayCountText = (current + 1).toString()
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    enabled = (bayCountText.toIntOrNull() ?: 1) < 10
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase bay count", modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        }

        if (editedServices.isEmpty()) {
            item {
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No services configured",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap \"Add Service\" to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
            }
        } else {
            items(
                items = editedServices,
                key = { it.serviceName }
            ) { config ->
                val isInUse = allBookings.any { booking ->
                    val status = booking.status
                    val isActive = status == BookingStatus.PENDING
                        || status == BookingStatus.CONFIRMED
                        || status == BookingStatus.IN_PROGRESS
                    if (!isActive) return@any false
                    // Check if this booking uses the service being deleted
                    if (config.isCustom) {
                        // Custom service: check customServiceNames
                        config.customName.isNotEmpty() && booking.customServiceNames.contains(config.customName)
                    } else {
                        // Predefined service: check ServiceType list
                        booking.services.any { it.name == config.serviceName }
                    }
                }
                ServiceRow(
                    config = config,
                    canDelete = !isInUse,
                    deleteReason = "Cannot delete — service has active bookings",
                    onEdit = { editingService = config },
                    onDelete = {
                        invalidDurationKeys = invalidDurationKeys - config.serviceName
                        editedServices = editedServices.filter { it.serviceName != config.serviceName }
                    }
                )
            }
        }
        }

        // Sticky action bar — Reset / Save always visible above the bottom nav.
        Surface(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        editedServices = normalizeConfigs(customization.services)
                        openMinutes = customization.openMinutes
                        closeMinutes = customization.closeMinutes
                        closedDates = customization.closedDates
                        dayOverrides = customization.dayOverrides
                        editedStaff = customization.staffNames
                        invalidDurationKeys = emptySet()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = {
                        // Confirm only when the hours actually changed; other edits
                        // (services, bays) save straight through.
                        if (hoursChanged) showHoursConfirm = true else performSave()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(24.dp),
                    enabled = canSave && !isSavingServices,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E6C3),
                        contentColor = Color(0xFF0B1921),
                        disabledContainerColor = Color(0xFF1E293B),
                        disabledContentColor = Color(0xFF64748B)
                    )
                ) {
                    if (isSavingServices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF0B1921),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    // Modal editor for a service (price/duration/description/unavailable dates).
    val editing = editingService
    if (editing != null) {
        EditServiceDialog(
            service = editing,
            onSave = { updated ->
                editedServices = editedServices.map {
                    if (it.serviceName == updated.serviceName) updated else it
                }
                invalidDurationKeys = invalidDurationKeys - updated.serviceName
                editingService = null
            },
            onDismiss = { editingService = null }
        )
    }
}

/** Compact view-mode row for a service: title + price/duration, actions right. */
@Composable
private fun ServiceRow(
    config: CustomServiceConfig,
    canDelete: Boolean,
    deleteReason: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val defaultPrice = ServiceType.values().find { it.name == config.serviceName }?.price ?: 0.0
    val price = if (config.customPrice > 0) config.customPrice else defaultPrice
    val durationLabel = if (config.durationMinutes % 60 == 0) {
        "${config.durationMinutes / 60} hr"
    } else "${config.durationMinutes} mins"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.displayName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${BookingUtils.formatPrice(price)} • $durationLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )

                // Inline validation warnings
                val isDescriptionBlank = config.description.isBlank()
                val isPriceInvalid = (config.customPrice > 0.0 && config.customPrice < 150) ||
                        config.customPrice > 5000 ||
                        (config.isCustom && config.customPrice == 0.0)
                val effectiveDuration = if (config.durationMinutes > 0) config.durationMinutes
                                         else defaultDurationMinutes(config)
                val isDurationInvalid = effectiveDuration < MIN_SERVICE_DURATION_MIN || effectiveDuration > MAX_SERVICE_DURATION_MIN

                if (isDescriptionBlank || isPriceInvalid || isDurationInvalid) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (isDescriptionBlank) {
                            Text("⚠️ Description is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isPriceInvalid) {
                            Text("⚠️ Price must be ₱150 - ₱5,000", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isDurationInvalid) {
                            Text("⚠️ Duration must be 20 - 180 mins", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${config.displayName}", modifier = Modifier.size(18.dp), tint = Color.White)
            }
            IconButton(
                onClick = {
                    if (canDelete) onDelete()
                    else Toast.makeText(context, deleteReason ?: "Cannot delete this service", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${config.displayName}",
                    modifier = Modifier.size(18.dp),
                    tint = if (canDelete) MaterialTheme.colorScheme.error
                           else Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/** Modal editor for a service's price, duration, description and unavailable dates. */
@Composable
private fun EditServiceDialog(
    service: CustomServiceConfig,
    onSave: (CustomServiceConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultPrice = ServiceType.values().find { it.name == service.serviceName }?.price ?: 0.0
    var priceText by remember(service) {
        mutableStateOf(if (service.customPrice > 0) service.customPrice.toString() else "")
    }
    var durationText by remember(service) {
        mutableStateOf(if (service.durationMinutes > 0) "${service.durationMinutes} mins" else "")
    }
    var descriptionText by remember(service) { mutableStateOf(service.description) }
    var unavailableDates by remember(service) { mutableStateOf(service.unavailableDates) }
    var showDatePicker by remember { mutableStateOf(false) }
    val parsedDuration = remember(durationText) { BookingUtils.parseDurationMinutes(durationText) }
    val price = priceText.toDoubleOrNull()
    val isPriceValid = price != null && price >= 150 && price <= 5000
    val isDurationValid = parsedDuration != null && parsedDuration >= MIN_SERVICE_DURATION_MIN && parsedDuration <= MAX_SERVICE_DURATION_MIN
    val isDescriptionValid = descriptionText.isNotBlank()
    val valid = isPriceValid && isDurationValid && isDescriptionValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${service.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Price (₱)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (priceText.isNotEmpty() && !isPriceValid) {
                        Text("Price must be between ₱150 and ₱5,000", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("Duration") },
                        placeholder = { Text("e.g. 30 mins, 1 hour, 1.5 hours") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (durationText.isNotEmpty() && !isDurationValid) {
                        Text("Duration must be between 20 and 180 mins", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column {
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { if (it.length <= MAX_SERVICE_DESCRIPTION_LEN) descriptionText = it },
                        label = { Text("Description") },
                        minLines = 2,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (descriptionText.isBlank()) {
                        Text("Description is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Unavailable dates",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showDatePicker = true }) { Text("Add date") }
                }
                if (unavailableDates.isEmpty()) {
                    Text(
                        "Available every day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        unavailableDates.sorted().forEach { date ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(DateUtils.formatDate(date), style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    IconButton(
                                        onClick = { unavailableDates = unavailableDates - date },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove date",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (durationText.isNotEmpty() && parsedDuration == null) {
                    Text(
                        "Enter a valid duration like \"30 mins\" or \"1.5 hours\".",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        service.copy(
                            customPrice = price ?: 0.0,
                            durationMinutes = parsedDuration ?: service.durationMinutes,
                            description = descriptionText,
                            unavailableDates = unavailableDates
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CheckotTeal,
                    contentColor = Color(0xFF00332B)
                )
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    if (showDatePicker) {
        UnavailableDatePickerDialog(
            onAdd = { date ->
                unavailableDates = (unavailableDates + date).distinct()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun WorkingHoursSection(
    openMinutes: Int,
    closeMinutes: Int,
    earliestBookingStart: Int?,
    latestBookingStart: Int?,
    windowValid: Boolean,
    openCoversBookings: Boolean,
    closeCoversBookings: Boolean,
    onOpenChange: (Int) -> Unit,
    onCloseChange: (Int) -> Unit
) {
    // 6:00 AM (360) → 9:30 PM (1290) in 30-min steps.
    val allOptions = remember { (360..1290 step SLOT_STEP_MIN).toList() }
    // Opening can't be so late there's no room for a full window, and can't
    // start after an existing booking.
    val maxOpen = 1290 - MIN_WORKING_WINDOW_MIN
    val openOptions = allOptions.filter { opt ->
        opt <= maxOpen && (earliestBookingStart == null || opt <= earliestBookingStart)
    }
    // Closing must leave a full window and stay after the latest booking's START
    // (closeMinutes is the last bookable slot start).
    val minClose = maxOf(
        openMinutes + MIN_WORKING_WINDOW_MIN,
        latestBookingStart?.let { ceilToStep(it, SLOT_STEP_MIN) } ?: 0
    )
    val closeOptions = allOptions.filter { it >= minClose }

    val errorMsg = when {
        !windowValid -> "Opening hours must be at least ${MIN_WORKING_WINDOW_MIN / 60} hour long."
        !openCoversBookings && earliestBookingStart != null ->
            "You have a booking at ${BookingUtils.minutesToSlotLabel(earliestBookingStart)} — opening can't be later."
        !closeCoversBookings && latestBookingStart != null ->
            "You have a booking at ${BookingUtils.minutesToSlotLabel(ceilToStep(latestBookingStart, SLOT_STEP_MIN).coerceAtMost(1290))} — closing can't be earlier than its start."
        else -> null
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // Secondary sub-header — the card already says "Schedule & Availability",
        // so keep this smaller/lighter than a main card title.
        Text(
            "Working Hours",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeDropdown(
                label = "Opens",
                valueMinutes = openMinutes,
                options = openOptions,
                onSelect = onOpenChange,
                modifier = Modifier.weight(1f)
            )
            TimeDropdown(
                label = "Closes",
                valueMinutes = closeMinutes,
                options = closeOptions,
                onSelect = onCloseChange,
                modifier = Modifier.weight(1f)
            )
        }
        if (errorMsg != null) {
            Text(
                errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(
    label: String,
    valueMinutes: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // Consume drag-scrolls over the open time list so the list scrolls instead
    // of the page/dialog behind it (same gesture conflict as the map fix).
    val blockScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset =
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) available
                else androidx.compose.ui.geometry.Offset.Zero
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = BookingUtils.minutesToSlotLabel(valueMinutes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            // No leading icon: it ate horizontal space in the half-width inputs
            // and clipped "09:00 AM" / "04:00 PM". The label + arrow suffice.
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        // Cap the height so a long time list scrolls instead of covering the screen.
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 280.dp)
                .nestedScroll(blockScroll)
        ) {
            options.forEach { m ->
                val selected = m == valueMinutes
                DropdownMenuItem(
                    text = {
                        Text(
                            BookingUtils.minutesToSlotLabel(m),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(m); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun ClosedDatesSection(
    closedDates: List<Long>,
    onClosedDatesChange: (List<Long>) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.EventBusy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Closed Dates", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showPicker = true }) { Text("Add date") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (closedDates.isEmpty()) {
            Text(
                "No closed dates — open every day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        } else {
            closedDates.sorted().forEach { date ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = DateUtils.formatDate(date),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onClosedDatesChange(closedDates - date) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove date",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        if (showPicker) {
            UnavailableDatePickerDialog(
                onAdd = { date ->
                    onClosedDatesChange((closedDates + date).distinct())
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}

/** Date picker for marking the shop / a service unavailable on a specific day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnavailableDatePickerDialog(
    onAdd: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val todayStart = BookingUtils.startOfDay(System.currentTimeMillis())
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayStart,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Allow today..+30 days; block past dates.
                val maxDayStart = todayStart + 30L * 24 * 60 * 60 * 1000
                return utcTimeMillis in todayStart..maxDayStart
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onAdd(BookingUtils.utcMidnightToLocalMidnight(millis))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun HoursOverridesSection(
    dayOverrides: List<DayHoursOverride>,
    defaultOpenMinutes: Int,
    defaultCloseMinutes: Int,
    onDayOverridesChange: (List<DayHoursOverride>) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<Long?>(null) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Hours Overrides", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showDatePicker = true }) { Text("Add override") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (dayOverrides.isEmpty()) {
            Text(
                "No overrides — the regular hours apply every day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        } else {
            dayOverrides.sortedBy { it.date }.forEach { override ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${DateUtils.formatDate(override.date)} · " +
                            "${BookingUtils.minutesToSlotLabel(override.openMinutes)} – " +
                            "${BookingUtils.minutesToSlotLabel(override.closeMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDayOverridesChange(dayOverrides - override) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove override",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
    // Step 1: pick the date for the override.
    if (showDatePicker) {
        UnavailableDatePickerDialog(
            onAdd = { date ->
                pendingDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    // Step 2: choose the hours for that date.
    val dateForEdit = pendingDate
    if (dateForEdit != null) {
        DayHoursOverrideDialog(
            date = dateForEdit,
            defaultOpenMinutes = defaultOpenMinutes,
            defaultCloseMinutes = defaultCloseMinutes,
            onSave = { open, close ->
                onDayOverridesChange(
                    (dayOverrides + DayHoursOverride(dateForEdit, open, close)).distinctBy { it.date }
                )
                pendingDate = null
            },
            onDismiss = { pendingDate = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayHoursOverrideDialog(
    date: Long,
    defaultOpenMinutes: Int,
    defaultCloseMinutes: Int,
    onSave: (openMinutes: Int, closeMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var openMinutes by remember { mutableStateOf(defaultOpenMinutes) }
    var closeMinutes by remember { mutableStateOf(defaultCloseMinutes) }
    val allOptions = remember { (360..1290 step SLOT_STEP_MIN).toList() }
    val openOptions = allOptions.filter { it <= 1290 - MIN_WORKING_WINDOW_MIN }
    val closeOptions = allOptions.filter { it >= openMinutes + MIN_WORKING_WINDOW_MIN }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hours for ${DateUtils.formatDate(date)}") },
        text = {
            Column {
                Text(
                    "Updates apply only to this date. Clients with affected bookings will be notified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeDropdown(
                        label = "Opens",
                        valueMinutes = openMinutes,
                        options = openOptions,
                        onSelect = {
                            openMinutes = it
                            if (closeMinutes < it + MIN_WORKING_WINDOW_MIN) {
                                closeMinutes = it + MIN_WORKING_WINDOW_MIN
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TimeDropdown(
                        label = "Closes",
                        valueMinutes = closeMinutes,
                        options = closeOptions,
                        onSelect = { closeMinutes = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(openMinutes, closeMinutes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CheckotTeal,
                    contentColor = Color(0xFF00332B)
                )
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Grouped settings card used across the Owner dashboard (dark rounded surface). */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CheckotCardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CheckotTeal
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
