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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
private const val SLOT_STEP_MIN = 30

/** Rounds [value] up to the next multiple of [step] (e.g. 9:45 → 10:00 on a 30-min grid). */
private fun ceilToStep(value: Int, step: Int): Int = ((value + step - 1) / step) * step

/** Built-in default duration for a predefined service; 0 for custom services. */
private fun defaultDurationMinutes(config: CustomServiceConfig): Int =
    ServiceType.values().find { it.name == config.serviceName }
        ?.let { BookingUtils.parseDurationMinutes(it.duration) } ?: 0

/**
 * Repairs legacy configs whose isCustom flag was lost by the old Firestore
 * field-name mismatch (stored as "custom", read as "isCustom"): a service
 * with no matching ServiceType is custom by definition. Saving persists the
 * repaired flag under the correct field name.
 */
private fun normalizeConfigs(services: List<CustomServiceConfig>): List<CustomServiceConfig> =
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
        Column {
        // Working hours — one opening/closing time applied to every day.
        WorkingHoursSection(
            openMinutes = openMinutes,
            closeMinutes = closeMinutes,
            earliestBookingStart = earliestBookingStart,
            latestBookingStart = latestBookingStart,
            windowValid = windowValid,
            openCoversBookings = openCoversBookings,
            closeCoversBookings = closeCoversBookings,
            onOpenChange = { m ->
                openMinutes = m
                // Keep closing at least a full window ahead and past any booking.
                val minClose = maxOf(
                    m + MIN_WORKING_WINDOW_MIN,
                    latestBookingStart?.let { ceilToStep(it, SLOT_STEP_MIN) } ?: 0
                )
                if (closeMinutes < minClose) closeMinutes = minClose
            },
            onCloseChange = { closeMinutes = it }
        )

        // Closed dates — temporary closures / holidays. Clients can't book these.
        ClosedDatesSection(
            closedDates = closedDates,
            onClosedDatesChange = { closedDates = it }
        )

        // Hours overrides — one-off hours for a specific date (e.g. closing
        // early today). Permanent hours are not changed.
        HoursOverridesSection(
            dayOverrides = dayOverrides,
            defaultOpenMinutes = openMinutes,
            defaultCloseMinutes = closeMinutes,
            onDayOverridesChange = { dayOverrides = it }
        )

        // Shop location — opens the map picker. Editable any time (shops relocate).
        val locationSet = customization.latitude != 0.0 || customization.longitude != 0.0
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shop Location", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = if (locationSet) "Location set — clients can see your shop on the map."
                       else "No location set yet. Set it so clients can find you.",
                style = MaterialTheme.typography.bodySmall,
                color = if (locationSet) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            OutlinedButton(
                onClick = { navController.navigate("set_shop_location") },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (locationSet) "Change Location" else "Set Location on Map")
            }
        }

        // Staff — names the owner can assign when starting a service. Display-only:
        // shown to the client and owner, never affects bay count or capacity.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Staff", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Add the people who service cars. When you start a service you can assign one, and the client will see who worked on their car.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = staffNameInput,
                    onValueChange = { if (it.length <= 30) staffNameInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Staff name") },
                    placeholder = { Text("e.g. Juan") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(modifier = Modifier.width(8.dp))
                val trimmedStaff = staffNameInput.trim()
                val canAddStaff = trimmedStaff.isNotEmpty() &&
                    editedStaff.none { it.equals(trimmedStaff, ignoreCase = true) } &&
                    editedStaff.size < 15
                Button(
                    onClick = {
                        editedStaff = editedStaff + trimmedStaff
                        staffNameInput = ""
                    },
                    enabled = canAddStaff,
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add staff", modifier = Modifier.size(18.dp))
                }
            }
            editedStaff.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { editedStaff = editedStaff - name }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove $name",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Garage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Service Bays:", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val current = bayCountText.toIntOrNull() ?: 1
                            if (current > 1) {
                                bayCountText = (current - 1).toString()
                            }
                        },
                        modifier = Modifier.size(48.dp),
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
                        modifier = Modifier.size(48.dp),
                        enabled = (bayCountText.toIntOrNull() ?: 1) < 10
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase bay count", modifier = Modifier.size(16.dp))
                    }
                }
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
                                    durationMinutes = BookingUtils.parseDurationMinutes(type.duration)
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

        }
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
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                    ServiceConfigCard(
                        config = config,
                        canDelete = !isInUse,
                        deleteReason = if (isInUse) "Cannot delete — service has active bookings" else null,
                        onUnavailableDatesChange = { dates ->
                            editedServices = editedServices.map {
                                if (it.serviceName == config.serviceName) it.copy(unavailableDates = dates) else it
                            }
                        },
                        onPriceChange = { newPrice ->
                            editedServices = editedServices.map {
                                if (it.serviceName == config.serviceName) it.copy(customPrice = newPrice) else it
                            }
                        },
                        onNameChange = { newName ->
                            editedServices = editedServices.map {
                                if (it.serviceName == config.serviceName) it.copy(customName = newName, displayName = newName) else it
                            }
                        },
                        onDescriptionChange = { newDesc ->
                            editedServices = editedServices.map {
                                if (it.serviceName == config.serviceName) it.copy(description = newDesc) else it
                            }
                        },
                        onDurationInput = { parsed ->
                            if (parsed != null) {
                                invalidDurationKeys = invalidDurationKeys - config.serviceName
                                editedServices = editedServices.map {
                                    if (it.serviceName == config.serviceName) it.copy(durationMinutes = parsed) else it
                                }
                            } else {
                                invalidDurationKeys = invalidDurationKeys + config.serviceName
                            }
                        },
                        onDelete = {
                            invalidDurationKeys = invalidDurationKeys - config.serviceName
                            editedServices = editedServices.filter { it.serviceName != config.serviceName }
                        }
                    )
                }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    editedServices = normalizeConfigs(customization.services)
                    openMinutes = customization.openMinutes
                    closeMinutes = customization.closeMinutes
                    editedStaff = customization.staffNames
                    invalidDurationKeys = emptySet()
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Reset")
            }
            Button(
                onClick = {
                    // Confirm only when the hours actually changed; other edits
                    // (services, bays) save straight through.
                    if (hoursChanged) showHoursConfirm = true else performSave()
                },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                enabled = canSave && !isSavingServices
            ) {
                if (isSavingServices) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Changes")
            }
        }
    }
}

@Composable
fun ServiceConfigCard(
    config: CustomServiceConfig,
    canDelete: Boolean = true,
    deleteReason: String? = null,
    onUnavailableDatesChange: (List<Long>) -> Unit = {},
    onPriceChange: (Double) -> Unit,
    onNameChange: (String) -> Unit = {},
    onDurationInput: (Int?) -> Unit = {}, // valid minutes, or null while the field is invalid/empty
    onDescriptionChange: (String) -> Unit = {},
    onDelete: () -> Unit
) {
    val defaultPrice = ServiceType.values().find { it.name == config.serviceName }?.price ?: 0.0
    val defaultDurationMin = defaultDurationMinutes(config)
    var priceText by remember(config.customPrice) {
        mutableStateOf(if (config.customPrice > 0) config.customPrice.toString() else "")
    }
    var durationText by remember(config.durationMinutes) {
        mutableStateOf(
            when {
                config.durationMinutes > 0 -> config.durationMinutes.toString()
                defaultDurationMin > 0 -> defaultDurationMin.toString()
                else -> ""
            }
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Service") },
            text = {
                Text("Removing \"${config.displayName}\" will delete this service from your shop's service list. Clients will no longer see it.\n\nAre you sure you want to proceed?")
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Yes, Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (config.isCustom) {
                        OutlinedTextField(
                            value = config.customName,
                            onValueChange = { if (it.length <= 30) onNameChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            placeholder = { Text("Service name") },
                            shape = MaterialTheme.shapes.small
                        )
                    } else {
                        Text(
                            text = config.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (config.isCustom) "Custom service" else "Default: ₱${defaultPrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(
                    onClick = { if (canDelete) showDeleteConfirm = true },
                    enabled = canDelete
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (canDelete) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
            }

            if (!canDelete && deleteReason != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = deleteReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Your Price: ₱",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            val parts = filtered.split(".")
                            val limited = if (parts.size == 2 && parts[1].length > 2) {
                                "${parts[0]}.${parts[1].take(2)}"
                            } else filtered
                            priceText = limited
                            val parsed = limited.toDoubleOrNull()
                            if (parsed != null && parsed >= 150 && parsed <= 5000) {
                                onPriceChange(parsed)
                            }
                            // When empty or invalid: don't call onPriceChange,
                            // keep the previous valid customPrice.
                            // The red error state will show below.
                        }
                    },
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    singleLine = true,
                    isError = priceText.isNotEmpty() && (priceText.toDoubleOrNull() == null
                        || priceText.toDoubleOrNull()!! < 150
                        || priceText.toDoubleOrNull()!! > 5000),
                    placeholder = { Text("${defaultPrice}") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.small
                )
            }
            val price = priceText.toDoubleOrNull()
            // Show error when: field is non-empty with invalid value,
            // or custom service with empty/0 price
            val showError = priceText.isNotEmpty() || (config.isCustom && priceText.isEmpty())
            if (showError) {
                when {
                    price == null || price < 150 -> Text(
                        "Minimum price is ₱150.00",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                    price > 5000 -> Text(
                        "Maximum price is ₱5,000.00",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val durationValid = durationText.toIntOrNull()
                ?.let { it in MIN_SERVICE_DURATION_MIN..MAX_SERVICE_DURATION_MIN } == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Duration (mins):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { input ->
                        // Whole numbers only — digits, no decimals or signs
                        val filtered = input.filter { it.isDigit() }.take(3)
                        durationText = filtered
                        val parsed = filtered.toIntOrNull()
                        onDurationInput(
                            if (parsed != null && parsed in MIN_SERVICE_DURATION_MIN..MAX_SERVICE_DURATION_MIN) parsed
                            else null
                        )
                    },
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    singleLine = true,
                    isError = !durationValid,
                    placeholder = { Text(if (defaultDurationMin > 0) "$defaultDurationMin" else "e.g. 45") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.small
                )
            }
            if (!durationValid) {
                Text(
                    "Please enter a valid duration between 20 and 180 minutes",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val descriptionBlank = config.description.isBlank()
            OutlinedTextField(
                value = config.description,
                onValueChange = { if (it.length <= MAX_SERVICE_DESCRIPTION_LEN) onDescriptionChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                placeholder = { Text("e.g. Hand wash + tire shine, exterior only") },
                minLines = 2,
                maxLines = 4,
                isError = descriptionBlank,
                supportingText = {
                    if (descriptionBlank) {
                        Text("Add a short detail so clients know what this service includes")
                    } else {
                        Text("${config.description.length}/$MAX_SERVICE_DESCRIPTION_LEN")
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(8.dp))
            // Unavailable dates — Foodpanda-style "sold out today": the service
            // stays in the catalog but can't be booked on these dates.
            var showUnavailablePicker by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.EventBusy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Unavailable dates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showUnavailablePicker = true }) {
                    Text("Add date")
                }
            }
            if (config.unavailableDates.isEmpty()) {
                Text(
                    text = "Available every day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 22.dp)
                )
            } else {
                config.unavailableDates.sorted().forEach { date ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 22.dp)
                    ) {
                        Text(
                            text = DateUtils.formatDate(date),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onUnavailableDatesChange(config.unavailableDates - date) }) {
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
            if (showUnavailablePicker) {
                UnavailableDatePickerDialog(
                    onAdd = { date ->
                        onUnavailableDatesChange((config.unavailableDates + date).distinct())
                        showUnavailablePicker = false
                    },
                    onDismiss = { showUnavailablePicker = false }
                )
            }
        }
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

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Working Hours", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Applied to every day. Clients can only book start times within this window.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
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
        } else if (earliestBookingStart != null) {
            // Explain why the range is limited when it isn't an error.
            Text(
                "Active bookings limit how much you can shorten your hours. Cancel or finish them to shrink further.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.Drag) available
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
            leadingIcon = {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyLarge,
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
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        Text(
            "The whole shop is closed on these dates — clients can't book them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
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
                if (millis != null) onAdd(BookingUtils.startOfDay(millis))
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
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        Text(
            "One-off hours for a specific date — e.g. closing early today. " +
                "Permanent hours are not changed. Affected clients are notified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
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
                    "Only this date is affected. Existing bookings outside these hours are kept and their clients notified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        modifier = Modifier.weight(1f)
                    )
                    TimeDropdown(
                        label = "Closes",
                        valueMinutes = closeMinutes,
                        options = closeOptions,
                        onSelect = { closeMinutes = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(openMinutes, closeMinutes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
