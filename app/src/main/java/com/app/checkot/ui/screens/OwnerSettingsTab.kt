package com.app.checkot.ui.screens
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.theme.CheckotCardSurface
import com.app.checkot.ui.theme.CheckotTeal
import com.app.checkot.ui.components.AppVersionFooter
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSettingsTab(
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
    var shopNameInput by remember { mutableStateOf(customization.shopName) }
    var shopAddressInput by remember { mutableStateOf(customization.shopAddress) }
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val maxServices = 15

    val bayCountChanged = bayCountText.toIntOrNull() != customization.bayCount

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
    val shopNameChanged = shopNameInput != customization.shopName
    val shopAddressChanged = shopAddressInput != customization.shopAddress
    val canSave = (editedServices != customization.services || bayCountChanged || hoursChanged ||
        closedDatesChanged || dayOverridesChanged || editedStaff != customization.staffNames ||
        shopNameChanged || shopAddressChanged) &&
        (!hoursChanged || hoursValid) && shopNameInput.trim().isNotEmpty() && shopAddressInput.trim().isNotEmpty()

    LaunchedEffect(customization) {
        editedServices = normalizeConfigs(customization.services)
        bayCountText = customization.bayCount.toString()
        openMinutes = customization.openMinutes
        closeMinutes = customization.closeMinutes
        closedDates = customization.closedDates
        dayOverrides = customization.dayOverrides
        editedStaff = customization.staffNames
        invalidDurationKeys = emptySet()
        shopNameInput = customization.shopName
        shopAddressInput = customization.shopAddress
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
            shopName = shopNameInput.trim(),
            shopAddress = shopAddressInput.trim(),
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

    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    fun performDelete() {
        isDeletingAccount = true
        deleteError = null
        ownerViewModel.deleteOwnerAccount(
            onSuccess = {
                isDeletingAccount = false
                showDeleteConfirm = false
                navController.navigate("login") {
                    popUpTo(0)
                }
            },
            onError = { msg ->
                isDeletingAccount = false
                deleteError = msg
                showDeleteConfirm = true // Force dialog open if error occurred during biometric flow
            }
        )
    }

    fun confirmDelete() {
        deleteError = null
        val act = activity
        if (act != null && BiometricAuth.canAuthenticate(context)) {
            BiometricAuth.prompt(
                activity = act,
                title = "Close Business & Delete Account",
                subtitle = "Confirm identity to permanently delete your shop and account",
                onSuccess = { performDelete() },
                onError = { msg -> deleteError = msg }
            )
        } else {
            showDeleteConfirm = true
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) {
                    showDeleteConfirm = false
                    deleteError = null
                }
            },
            title = { Text("Close Business & Delete Account?") },
            text = {
                Column {
                    Text(
                        "This will permanently remove your shop from the customer app, delist your services, and delete your owner login. Past completed transactions will be archived. This cannot be undone.",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = deleteError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { performDelete() },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Close & Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteError = null
                    },
                    enabled = !isDeletingAccount
                ) { Text("Cancel") }
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
        LazyColumn(
            modifier = Modifier.weight(1f).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp)
        ) {
        item {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // Card: Shop Availability Toggle
            SettingsCard(title = "Shop Availability", icon = Icons.Default.PowerSettingsNew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!customization.isClosed) "Shop is OPEN" else "Shop is TEMPORARILY CLOSED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (!customization.isClosed) Color(0xFF00E6C3) else Color(0xFFFF5252)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!customization.isClosed) "Accepting new client bookings" else "New bookings paused for clients",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = !customization.isClosed,
                        onCheckedChange = { isOpen ->
                            ownerViewModel.saveShopCustomization(customization.copy(isClosed = !isOpen))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E6C3),
                            checkedTrackColor = Color(0xFF004D40),
                            uncheckedThumbColor = Color(0xFFFF5252),
                            uncheckedTrackColor = Color(0xFF4A121A)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Card 0: Shop Profile
            SettingsCard(title = "Shop Profile", icon = Icons.Default.Store) {
                OutlinedTextField(
                    value = shopNameInput,
                    onValueChange = { if (it.length <= 40) shopNameInput = it },
                    label = { Text("Shop Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CheckotCardSurface,
                        unfocusedContainerColor = CheckotCardSurface,
                        focusedBorderColor = CheckotTeal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        errorContainerColor = CheckotCardSurface,
                        disabledContainerColor = CheckotCardSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = shopAddressInput,
                    onValueChange = { if (it.length <= 100) shopAddressInput = it },
                    label = { Text("Shop Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CheckotCardSurface,
                        unfocusedContainerColor = CheckotCardSurface,
                        focusedBorderColor = CheckotTeal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        errorContainerColor = CheckotCardSurface,
                        disabledContainerColor = CheckotCardSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Schedule & Availability — Working Hours, Closed Dates, Overrides.
            SettingsCard(title = "Schedule & Availability", icon = Icons.Default.Schedule) {
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ClosedDatesSection(
                    closedDates = closedDates,
                    onClosedDatesChange = { closedDates = it }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                HoursOverridesSection(
                    dayOverrides = dayOverrides,
                    defaultOpenMinutes = openMinutes,
                    defaultCloseMinutes = closeMinutes,
                    onDayOverridesChange = { dayOverrides = it }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Card 2: Shop Location — compact preview + change button.
            SettingsCard(title = "Shop Location", icon = Icons.Default.Place) {
                val locationSet = customization.latitude != 0.0 || customization.longitude != 0.0
                Text(
                    text = if (locationSet) "Location is set — clients can see your shop on the map."
                           else "No location set yet. Set it so clients can find you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (locationSet) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { navController.navigate("set_shop_location") },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (locationSet) "Change Location" else "Set Location on Map")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Card 3: Staff Management — removable pill chips.
            SettingsCard(title = "Staff Management", icon = Icons.Default.Group) {
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
                Spacer(modifier = Modifier.height(8.dp))
                if (editedStaff.isEmpty()) {
                    Text(
                        text = "No staff yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        editedStaff.forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    IconButton(
                                        onClick = { editedStaff = editedStaff - name },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $name",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Danger Zone Card
            SettingsCard(title = "Danger Zone", icon = Icons.Default.Warning) {
                Text(
                    text = "Once you close your shop and delete your account, this action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { confirmDelete() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
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
                        shopNameInput = customization.shopName
                        shopAddressInput = customization.shopAddress
                        invalidDurationKeys = emptySet()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
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
                        containerColor = CheckotTeal,
                        contentColor = Color(0xFF00332B)
                    )
                ) {
                    if (isSavingServices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF00332B),
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
        item {
            AppVersionFooter()
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.displayName, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${BookingUtils.formatPrice(price)} • $durationLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${config.displayName}", modifier = Modifier.size(18.dp))
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
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
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
    val valid = (price != null && price > 0) && parsedDuration != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${service.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Price (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Duration") },
                    placeholder = { Text("e.g. 30 mins, 1 hour, 1.5 hours") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Description") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
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
                if (parsedDuration == null) {
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
