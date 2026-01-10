package com.liteledger.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liteledger.app.data.Transaction
import com.liteledger.app.data.TransactionType
import com.liteledger.app.ui.theme.*
import com.liteledger.app.utils.rememberAppHaptic
import kotlinx.coroutines.launch
import com.liteledger.app.utils.Formatters
import com.liteledger.app.ui.theme.AppFontFamily
import com.liteledger.app.utils.ReceiptGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    personName: String,
    state: DetailState,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onAddTransaction: (Long, TransactionType, String, Long) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onEditTransaction: (Transaction) -> Unit
) {
    var showInputSheet by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(TransactionType.GAVE) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = rememberAppHaptic(enabled = hapticsEnabled)

    // --- DYNAMIC COLORS ---
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeGreen = if (isDark) Color(0xFF81C784) else MoneyGreen
    val activeRed = if (isDark) Color(0xFFE57373) else MoneyRed

    val statusText = if (state.totalBalance > 0) "You get" else if (state.totalBalance < 0) "You owe" else "Settled"
    val statusColor = if (state.totalBalance >= 0) activeGreen else activeRed

    // --- COLLAPSING TOOLBAR LOGIC ---
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 1. Dynamic Limits
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 152.dp + statusBarHeight

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    // 2. Updated State wrappers
    val maxPxState = rememberUpdatedState(maxTopBarHeightPx)
    val minPxState = rememberUpdatedState(minTopBarHeightPx)

    // 3. Offset State (0f to Negative)
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    // 4. Calculate actual height derived from offset
    val topBarHeightPx = (maxTopBarHeightPx + topBarOffsetHeightPx).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)

    // 5. Fraction (Reactive to orientation changes via keys)
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            val scrollRange = maxTopBarHeightPx - minTopBarHeightPx
            if (scrollRange == 0f) 0f else {
                val currentHeight = (maxTopBarHeightPx + topBarOffsetHeightPx).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                1f - ((currentHeight - minTopBarHeightPx) / scrollRange).coerceIn(0f, 1f)
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                val maxPx = maxPxState.value
                val minPx = minPxState.value
                val scrollRange = minPx - maxPx

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val newOffset = (topBarOffsetHeightPx + delta).coerceIn(scrollRange, 0f)
                val consumed = newOffset - topBarOffsetHeightPx

                // Synchronous update
                topBarOffsetHeightPx = newOffset

                return if (consumed != 0f) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    // 6. Snap Animation
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val scrollRange = minTopBarHeightPx - maxTopBarHeightPx
            val midpoint = scrollRange / 2f
            val targetOffset = if (topBarOffsetHeightPx > midpoint) 0f else scrollRange

            if (topBarOffsetHeightPx != targetOffset) {
                Animatable(topBarOffsetHeightPx).animateTo(
                    targetOffset,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) {
                    topBarOffsetHeightPx = value
                }
            }
        }
    }

    // --- MAIN UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. SCROLLABLE CONTENT
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = with(density) { topBarHeightPx.toDp() }, bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No transactions yet", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text("Add an entry to start tracking", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = with(density) { topBarHeightPx.toDp() + 16.dp },
                        bottom = 120.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    //verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.items,
                        // Unique Keys for smart updates
                        key = { item ->
                            when (item) {
                                is DetailListItem.Header -> item.title
                                is DetailListItem.Item -> item.uiModel.transaction.id
                            }
                        },
                        // Content Types for recycling optimization
                        contentType = { item ->
                            when (item) {
                                is DetailListItem.Header -> 0
                                is DetailListItem.Item -> 1
                            }
                        }
                    ) { item ->
                        when (item) {
                            is DetailListItem.Header -> {
                                MonthHeader(title = item.title)
                            }
                            is DetailListItem.Item -> {
                                Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                    TransactionBubble(
                                        uiModel = item.uiModel,
                                        onLongPress = { txn ->
                                            haptic.heavy()
                                            selectedTransaction = txn
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Blur/Fade Effect
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLowest)))
                )
            }
        }

        // 2. CUSTOM COLLAPSING TOP BAR (Sits on top)
        CustomDetailTopBar(
            height = with(density) { topBarHeightPx.toDp() },
            fraction = collapseFraction,
            title = personName,
            statusText = statusText,
            statusColor = statusColor,
            totalBalance = state.totalBalance,
            statusBarHeight = statusBarHeight,
            onBack = onBack,
            onShare = {
                scope.launch {
                    ReceiptGenerator.shareReceipt(context, personName, state.rawTransactions, state.totalBalance)
                }
            }
        )

        // 3. BOTTOM ACTIONS (Always visible)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
                Row(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                    Button(
                        onClick = { haptic.click(); selectedType = TransactionType.GOT; transactionToEdit = null; showInputSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MoneyGreen, contentColor = Color.White),
                        shape = SplitLeftShape,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Got", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)) }

                    Spacer(Modifier.width(4.dp))

                    Button(
                        onClick = { haptic.click(); selectedType = TransactionType.GAVE; transactionToEdit = null; showInputSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MoneyRed, contentColor = Color.White),
                        shape = SplitRightShape,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Gave", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)) }
                }
            }
        }
    }

    // --- SHEETS LOGIC (Keep existing implementations) ---
    if (showInputSheet) {
        TransactionInputSheet(
            type = selectedType,
            existingTransaction = transactionToEdit,
            onDismiss = {
                showInputSheet = false
                selectedTransaction = null
                transactionToEdit = null
            },
            onConfirm = { amount, note, type, date ->
                haptic.success()
                if (transactionToEdit != null) {
                    onEditTransaction(transactionToEdit!!.copy(amount = amount, note = note, type = type, date = date))
                } else {
                    onAddTransaction(amount, type, note, date)
                }
                showInputSheet = false
                transactionToEdit = null
                selectedTransaction = null
            }
        )
    }

    if (selectedTransaction != null && !showDeleteConfirmation) {
        ModalBottomSheet(
            onDismissRequest = { selectedTransaction = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Text("Manage Entry", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { transactionToEdit = selectedTransaction; selectedType = selectedTransaction!!.type; selectedTransaction = null; showInputSheet = true }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitLeftShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { showDeleteConfirmation = true }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitRightShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete") }
                }
            }
        }
    }

    if (showDeleteConfirmation && selectedTransaction != null) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteConfirmation = false; selectedTransaction = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Delete Transaction?", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { showDeleteConfirmation = false; selectedTransaction = null }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitLeftShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onDeleteTransaction(selectedTransaction!!); showDeleteConfirmation = false; selectedTransaction = null }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitRightShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                }
            }
        }
    }
}

// --- CUSTOM TOP BAR COMPONENT ---
@Composable
fun CustomDetailTopBar(
    height: Dp,
    fraction: Float,
    title: String,
    statusText: String,
    statusColor: Color,
    totalBalance: Long,
    statusBarHeight: Dp,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    // Interpolate Values
    val titleSize = lerpTextUnit(28.sp, 22.sp, fraction)
    val titleStartPadding = androidx.compose.ui.unit.lerp(16.dp, 64.dp, fraction)
    val titleBottomPadding = androidx.compose.ui.unit.lerp(24.dp, 16.dp, fraction)

    val subTitleAlpha = (1f - fraction * 2.5f).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.height(height).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (fraction > 0.9f) 3.dp else 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = statusBarHeight + 8.dp, start = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = onShare,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBarHeight + 8.dp, end = 8.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = "Share")
            }

            // 2. TITLE & SUBTITLE
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = titleStartPadding, bottom = titleBottomPadding)
            ) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = titleSize
                )

                if (subTitleAlpha > 0) {
                    Row(
                        modifier = Modifier.graphicsLayer { alpha = subTitleAlpha },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        if (totalBalance != 0L) {
                            Text(
                                text = Formatters.formatCurrency(totalBalance),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// ... (Keep TransactionInputSheet and TransactionBubble unchanged below)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputSheet(
    type: TransactionType,
    existingTransaction: Transaction?,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, TransactionType, Long) -> Unit
) {
    // 1. State Init
    val initialAmount = remember { existingTransaction?.amount?.div(100)?.toString() ?: "" }
    var amountText by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(existingTransaction?.note ?: "") }
    val currentType by rememberUpdatedState(existingTransaction?.type ?: type)

    // Master Timestamp State (Date + Time)
    var selectedTimestamp by remember { mutableLongStateOf(existingTransaction?.date ?: System.currentTimeMillis()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    // 2. Dynamic Colors
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeGreen = if (isDark) Color(0xFF81C784) else MoneyGreen
    val activeRed = if (isDark) Color(0xFFE57373) else MoneyRed

    // 3. Time Picker State
    val zdt = java.time.Instant.ofEpochMilli(selectedTimestamp).atZone(java.time.ZoneId.systemDefault())
    val timeState = rememberTimePickerState(
        initialHour = zdt.hour,
        initialMinute = zdt.minute,
        is24Hour = false
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
            // Header
            Text(
                text = if (existingTransaction != null) "Edit Entry" else if (currentType == TransactionType.GAVE) "I Gave" else "I Got",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (currentType == TransactionType.GAVE) activeRed else activeGreen
            )

            Spacer(Modifier.height(24.dp))

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    if (it.all { c -> c.isDigit() }) {
                        amountText = it
                        if (it.isNotEmpty()) isError = false
                    }
                },
                label = { Text("Amount") },
                isError = isError,
                modifier = Modifier.fillMaxWidth(),
                shape = TopItemShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                shape = MiddleItemShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )

            Spacer(Modifier.height(8.dp))

            // --- DATE & TIME ROW ---
            Row(modifier = Modifier.fillMaxWidth()) {
                // DATE BUTTON
                Surface(
                    onClick = { showDatePicker = true },
                    shape = BottomLeftSplitShape.copy(bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Formatters.formatSheetDate(selectedTimestamp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.width(4.dp))

                // TIME BUTTON
                Surface(
                    onClick = { showTimePicker = true },
                    shape = BottomRightSplitShape.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(4.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Formatters.formatTime(selectedTimestamp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = SplitLeftShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Cancel") }

                Spacer(Modifier.width(4.dp))

                Button(
                    onClick = {
                        val amount = amountText.toLongOrNull()?.times(100) ?: 0L
                        if (amount > 0) {
                            onConfirm(amount, note, currentType, selectedTimestamp)
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = SplitRightShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentType == TransactionType.GAVE) MoneyRed else MoneyGreen,
                        contentColor = Color.White
                    )
                ) { Text("Confirm") }
            }
        }
    }

    // --- 4. DIALOGS ---
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = selectedTimestamp,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { newDateMillis ->
                        val oldZone = java.time.Instant.ofEpochMilli(selectedTimestamp).atZone(java.time.ZoneId.systemDefault())
                        val newZone = java.time.Instant.ofEpochMilli(newDateMillis).atZone(java.time.ZoneId.of("UTC"))

                        var newTimestamp = oldZone
                            .withYear(newZone.year)
                            .withMonth(newZone.monthValue)
                            .withDayOfMonth(newZone.dayOfMonth)
                            .toInstant().toEpochMilli()

                        if (newTimestamp > System.currentTimeMillis()) {
                            newTimestamp = System.currentTimeMillis()
                        }

                        selectedTimestamp = newTimestamp
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val currentZonedTime = java.time.Instant.ofEpochMilli(selectedTimestamp)
            .atZone(java.time.ZoneId.systemDefault())

        val tentativeTime = currentZonedTime
            .withHour(timeState.hour)
            .withMinute(timeState.minute)
            .toInstant()
            .toEpochMilli()

        val isFuture = tentativeTime > System.currentTimeMillis()

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTimestamp = tentativeTime
                        showTimePicker = false
                    },
                    enabled = !isFuture, // Disable if future
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isFuture) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.primary
                    )
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            title = { Text("Select Time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                    if (isFuture) {
                        Text(
                            "Cannot select future time",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionBubble(
    uiModel: TransactionUiModel,
    onLongPress: (Transaction) -> Unit
) {
    val txn = uiModel.transaction
    val isGave = txn.type == TransactionType.GAVE
    val align = if (isGave) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (isGave) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isGave) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val balanceColor = contentColor.copy(alpha = 0.7f)

    val shape = if (isGave) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Surface(
            color = containerColor,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(shape)
                .combinedClickable(onClick = {}, onLongClick = { onLongPress(uiModel.transaction) })
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    Formatters.formatCurrency(txn.amount),
                    color = contentColor,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFontFamily
                    )
                )

                if (txn.note.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        txn.note,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = AppFontFamily
                        ),
                        color = contentColor
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bal: ${Formatters.formatCurrency(uiModel.runningBalance)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AppFontFamily
                        ),
                        color = balanceColor
                    )

                    Text(
                        text = Formatters.formatUiDate(txn.date),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppFontFamily
                        ),
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun MonthHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    }
}