package com.liteledger.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.liteledger.app.data.Tag
import com.liteledger.app.data.Transaction
import com.liteledger.app.data.TransactionType
import com.liteledger.app.ui.theme.*
import com.liteledger.app.utils.rememberAppHaptic
import kotlinx.coroutines.launch
import com.liteledger.app.utils.Formatters
import com.liteledger.app.ui.theme.AppFontFamily
import com.liteledger.app.utils.ReceiptGenerator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    personName: String,
    state: DetailState,
    allTags: List<Tag>,
    recentTags: List<Tag>,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onAddTransaction: (Long, TransactionType, String, Long, Long?, List<Long>) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onEditTransaction: (Transaction, List<Long>) -> Unit,
    onCreateTag: (String) -> Unit,
    // Settlement-related callbacks
    getEligibleSettlementTargets: (TransactionType) -> List<TransactionUiModel>,
    onAddTransactionWithSettlements: (Long, TransactionType, String, Long, Long?, List<Long>, List<Pair<Long, Long>>) -> Unit,
    onUpdateSettlements: (Long, List<Pair<Long, Long>>) -> Unit,
    // Smart statement callback
    getSmartStatementData: () -> SmartStatementData
) {
    var showInputSheet by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(TransactionType.GAVE) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSettlementEditor by remember { mutableStateOf(false) }
    var settlementEditTxn by remember { mutableStateOf<TransactionUiModel?>(null) }

    // Share/Save state
    var showExportSheet by remember { mutableStateOf(false) }
    var pendingPdfFile by remember { mutableStateOf<File?>(null) }

    val context = LocalContext.current
    val haptic = rememberAppHaptic(enabled = hapticsEnabled)
    val scope = rememberCoroutineScope()

    // ActivityResultLauncher for saving PDF to user-selected location
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && pendingPdfFile != null) {
            scope.launch {
                val success = ReceiptGenerator.writePdfToUri(context, pendingPdfFile!!, uri)
                if (success) {
                    Toast.makeText(context, "Saved to Files", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                }
                pendingPdfFile = null
            }
        }
    }

    // --- DYNAMIC COLORS ---
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeGreen = if (isDark) Color(0xFF81C784) else MoneyGreen
    val activeRed = if (isDark) Color(0xFFE57373) else MoneyRed

    val statusText = if (state.totalBalance > 0) "You get" else if (state.totalBalance < 0) "You owe" else "Settled"
    val statusColor = if (state.totalBalance >= 0) activeGreen else activeRed

    // --- COLLAPSING TOOLBAR LOGIC ---
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()

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
                        Text("No transactions yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
            onShare = { showExportSheet = true }
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
                    ) { Text("Got", style = MaterialTheme.typography.titleMedium) }

                    Spacer(Modifier.width(6.dp))

                    Button(
                        onClick = { haptic.click(); selectedType = TransactionType.GAVE; transactionToEdit = null; showInputSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MoneyRed, contentColor = Color.White),
                        shape = SplitRightShape,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Gave", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }

    // --- SHEETS LOGIC (Keep existing implementations) ---
    if (showInputSheet) {
        // Get existing tags for transaction being edited
        val existingTagIds = remember(transactionToEdit) {
            state.items.filterIsInstance<DetailListItem.Item>()
                .find { it.uiModel.transaction.id == transactionToEdit?.id }
                ?.uiModel?.tags?.map { it.id } ?: emptyList()
        }

        // Get eligible settlement targets for new transactions only
        val eligibleTargets = remember(selectedType, transactionToEdit) {
            if (transactionToEdit == null) getEligibleSettlementTargets(selectedType) else emptyList()
        }

        TransactionInputSheet(
            type = selectedType,
            existingTransaction = transactionToEdit,
            allTags = allTags,
            recentTags = recentTags,
            existingTagIds = existingTagIds,
            onDismiss = {
                showInputSheet = false
                selectedTransaction = null
                transactionToEdit = null
            },
            onConfirm = { amount, note, type, date, dueDate, tagIds ->
                haptic.success()
                if (transactionToEdit != null) {
                    onEditTransaction(transactionToEdit!!.copy(amount = amount, note = note, type = type, date = date, dueDate = dueDate), tagIds)
                } else {
                    onAddTransaction(amount, type, note, date, dueDate, tagIds)
                }
                showInputSheet = false
                transactionToEdit = null
                selectedTransaction = null
            },
            onCreateTag = onCreateTag,
            eligibleTargets = eligibleTargets,
            onConfirmWithSettlements = { amount, note, type, date, dueDate, tagIds, settlements ->
                haptic.success()
                onAddTransactionWithSettlements(amount, type, note, date, dueDate, tagIds, settlements)
                showInputSheet = false
                transactionToEdit = null
                selectedTransaction = null
            }
        )
    }

    if (selectedTransaction != null && !showDeleteConfirmation && !showSettlementEditor) {
        // Find the UI model for the selected transaction
        val selectedUiModel = remember(selectedTransaction) {
            state.items.filterIsInstance<DetailListItem.Item>()
                .find { it.uiModel.transaction.id == selectedTransaction?.id }?.uiModel
        }

        // Check if this transaction can link settlements (has eligible opposite-type transactions)
        val canLinkSettlements = remember(selectedTransaction) {
            selectedTransaction?.let { txn ->
                getEligibleSettlementTargets(txn.type).isNotEmpty()
            } ?: false
        }

        ModalBottomSheet(
            onDismissRequest = { selectedTransaction = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Text("Manage Entry", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))

                // Edit & Delete row
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { transactionToEdit = selectedTransaction; selectedType = selectedTransaction!!.type; selectedTransaction = null; showInputSheet = true }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitLeftShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { showDeleteConfirmation = true }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitRightShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete") }
                }

                // Mark Settlement button (only show if eligible)
                if (canLinkSettlements) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            settlementEditTxn = selectedUiModel
                            selectedTransaction = null
                            showSettlementEditor = true
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = SingleItemShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Outlined.Link, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mark Settlement")
                    }
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
                Text("Delete Transaction?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { showDeleteConfirmation = false; selectedTransaction = null }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitLeftShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onDeleteTransaction(selectedTransaction!!); showDeleteConfirmation = false; selectedTransaction = null }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitRightShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                }
            }
        }
    }

    // Settlement Editor Sheet (for marking settlements on existing transactions)
    if (showSettlementEditor && settlementEditTxn != null) {
        val txn = settlementEditTxn!!.transaction
        val eligibleTargets = remember(txn.type) { getEligibleSettlementTargets(txn.type) }
        val existingSettlementsAsMap = remember(settlementEditTxn) {
            // Convert settlesAmount to current settlements if any exist
            emptyList<Pair<Long, Long>>()
        }

        SettlementPickerSheet(
            eligibleTransactions = eligibleTargets,
            selectedSettlements = existingSettlementsAsMap,
            availableAmount = txn.amount,
            onDismiss = {
                showSettlementEditor = false
                settlementEditTxn = null
            },
            onConfirm = { settlements ->
                onUpdateSettlements(txn.id, settlements)
                showSettlementEditor = false
                settlementEditTxn = null
            }
        )
    }

    // Export Sheet (Share vs Save options)
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text("Export Statement", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose how to export the statement for $personName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth()) {
                    // Share button
                    Button(
                        onClick = {
                            showExportSheet = false
                            scope.launch {
                                val smartData = getSmartStatementData()
                                ReceiptGenerator.shareSmartStatement(context, personName, smartData)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = SplitLeftShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Outlined.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }

                    Spacer(Modifier.width(4.dp))

                    // Save to Files button
                    Button(
                        onClick = {
                            showExportSheet = false
                            scope.launch {
                                val smartData = getSmartStatementData()
                                val file = ReceiptGenerator.generateSmartPdfFile(
                                    context, personName, smartData
                                )
                                if (file != null) {
                                    pendingPdfFile = file
                                    saveFileLauncher.launch("Statement_$personName.pdf")
                                } else {
                                    Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = SplitRightShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Outlined.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
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
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = titleSize,
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
                                style = MaterialTheme.typography.titleMedium,
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
    allTags: List<Tag>,
    recentTags: List<Tag>,
    existingTagIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, TransactionType, Long, Long?, List<Long>) -> Unit,
    onCreateTag: (String) -> Unit,
    // Settlement support
    eligibleTargets: List<TransactionUiModel> = emptyList(),
    onConfirmWithSettlements: ((Long, String, TransactionType, Long, Long?, List<Long>, List<Pair<Long, Long>>) -> Unit)? = null,
    existingSettlements: List<Pair<Long, Long>> = emptyList()
) {
    // 1. State Init
    val initialAmount = remember { existingTransaction?.amount?.div(100)?.toString() ?: "" }
    var amountText by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(existingTransaction?.note ?: "") }
    val currentType by rememberUpdatedState(existingTransaction?.type ?: type)

    // Tag State
    var selectedTagIds by remember { mutableStateOf(existingTagIds.toMutableList()) }
    var showTagPicker by remember { mutableStateOf(false) }

    // Settlement State
    var selectedSettlements by remember { mutableStateOf(existingSettlements.toMutableList()) }
    var showSettlementPicker by remember { mutableStateOf(false) }

    // Master Timestamp State (Date + Time)
    var selectedTimestamp by remember { mutableLongStateOf(existingTransaction?.date ?: System.currentTimeMillis()) }

    // Due Date State (optional)
    var dueDate by remember { mutableStateOf(existingTransaction?.dueDate) }
    var showDueDatePicker by remember { mutableStateOf(false) }

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
                style = MaterialTheme.typography.headlineMedium,
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

            // Note (negative offset to eliminate OutlinedTextField internal gap)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth().offset(y = (-2).dp),
                shape = MiddleItemShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )

            // --- SETTLES PREVIOUS SECTION (optional, only for new transactions) ---
            if (existingTransaction == null && eligibleTargets.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val totalSettled = selectedSettlements.sumOf { it.second }
                Surface(
                    onClick = { showSettlementPicker = true },
                    shape = MiddleItemShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        if (selectedSettlements.isEmpty()) {
                            Text("Settles previous", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(
                                "${selectedSettlements.size} txn(s) · ${Formatters.formatCurrency(totalSettled)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (selectedSettlements.isNotEmpty()) {
                            IconButton(onClick = { selectedSettlements = mutableListOf() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // --- DUE DATE & TAGS ROW (split) ---
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                // DUE DATE (left side)
                Surface(
                    onClick = { showDueDatePicker = true },
                    shape = MiddleSplitLeftShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        if (dueDate == null) {
                            Text("Due date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(Formatters.formatSheetDate(dueDate!!), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.Close,
                                "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).clickable { dueDate = null }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // TAGS (right side)
                Surface(
                    onClick = { showTagPicker = true },
                    shape = MiddleSplitRightShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        if (selectedTagIds.isEmpty()) {
                            Text("Tags", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val selectedTags = allTags.filter { it.id in selectedTagIds }
                            Text(
                                selectedTags.take(2).joinToString { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

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

                Spacer(Modifier.width(6.dp))

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

                Spacer(Modifier.width(6.dp))

                Button(
                    onClick = {
                        val amount = amountText.toLongOrNull()?.times(100) ?: 0L
                        if (amount > 0) {
                            if (selectedSettlements.isNotEmpty() && onConfirmWithSettlements != null) {
                                onConfirmWithSettlements(amount, note, currentType, selectedTimestamp, dueDate, selectedTagIds, selectedSettlements)
                            } else {
                                onConfirm(amount, note, currentType, selectedTimestamp, dueDate, selectedTagIds)
                            }
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

    // --- TAG PICKER SHEET ---
    if (showTagPicker) {
        TagPickerSheet(
            allTags = allTags,
            recentTags = recentTags,
            selectedTagIds = selectedTagIds,
            maxTags = 2,
            onDismiss = { showTagPicker = false },
            onTagToggle = { tagId, selected ->
                selectedTagIds = if (selected) {
                    (selectedTagIds + tagId).take(2).toMutableList()
                } else {
                    selectedTagIds.toMutableList().apply { remove(tagId) }
                }
            },
            onCreateTag = onCreateTag
        )
    }

    // --- SETTLEMENT PICKER SHEET ---
    if (showSettlementPicker && eligibleTargets.isNotEmpty()) {
        val currentAmount = amountText.toLongOrNull()?.times(100) ?: 0L
        SettlementPickerSheet(
            eligibleTransactions = eligibleTargets,
            selectedSettlements = selectedSettlements,
            availableAmount = currentAmount,
            onDismiss = { showSettlementPicker = false },
            onConfirm = { settlements ->
                selectedSettlements = settlements.toMutableList()
                // Auto-populate note if empty and settlements selected
                if (note.isEmpty() && settlements.isNotEmpty()) {
                    val firstSettledTxn = eligibleTargets.find { it.transaction.id == settlements.first().first }
                    firstSettledTxn?.transaction?.note?.takeIf { it.isNotEmpty() }?.let { originalNote ->
                        note = "$originalNote settlement"
                    }
                }
                showSettlementPicker = false
            }
        )
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

    // --- DUE DATE PICKER ---
    if (showDueDatePicker) {
        val dueDateState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate ?: (System.currentTimeMillis() + 86400000L) // Default to tomorrow
        )
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDateState.selectedDateMillis?.let { dueDate = it }
                    showDueDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = dueDateState)
        }
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

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bal: ${Formatters.formatCurrency(uiModel.runningBalance)}",
                        style = MaterialTheme.typography.labelMedium.copy(
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

                // Tags and settlement status display
                val hasTags = uiModel.tags.isNotEmpty()
                val hasSettlementStatus = uiModel.settlementStatus != SettlementStatus.OPEN
                val hasDueDate = txn.dueDate != null && uiModel.settlementStatus == SettlementStatus.OPEN

                // Show row if there are tags, settlement status, or due date
                if (hasTags || hasSettlementStatus || hasDueDate) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: tags only
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tags
                            uiModel.tags.take(2).forEach { tag ->
                                BubbleTagChip(
                                    name = tag.name,
                                    contentColor = contentColor
                                )
                            }

                            // Show indicator if total count > 2
                            if (uiModel.tags.size > 2) {
                                Text(
                                    text = "+${uiModel.tags.size - 2}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppFontFamily
                                    ),
                                    color = contentColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Right side: Settlement status chip OR due date
                        when {
                            hasSettlementStatus -> {
                                // Show Settled/Partial chip on right side for TARGET transactions
                                when (uiModel.settlementStatus) {
                                    SettlementStatus.SETTLED -> SettlementStatusChip("Settled", contentColor)
                                    SettlementStatus.PARTIAL -> SettlementStatusChip("Partial", contentColor)
                                    SettlementStatus.OPEN -> {}
                                }
                            }
                            hasDueDate -> {
                                Text(
                                    text = Formatters.formatDueDate(txn.dueDate!!),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppFontFamily
                                    ),
                                    color = contentColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
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
                fontFamily = AppFontFamily
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

// --- TAG COMPONENTS ---

@Composable
fun TagChip(tag: Tag, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                tag.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(14.dp).clip(RoundedCornerShape(50)).clickable { onRemove() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BubbleTagChip(name: String, contentColor: Color = LocalContentColor.current) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = contentColor.copy(alpha = 0.15f)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFontFamily),
            color = contentColor.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    allTags: List<Tag>,
    recentTags: List<Tag>,
    selectedTagIds: List<Long>,
    maxTags: Int = 2,
    onDismiss: () -> Unit,
    onTagToggle: (tagId: Long, selected: Boolean) -> Unit,
    onCreateTag: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTags = remember(searchQuery, allTags) {
        if (searchQuery.isEmpty()) allTags
        else allTags.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val showCreateOption = searchQuery.isNotEmpty() && filteredTags.none { it.name.equals(searchQuery, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()) {
            Text(
                "Select Tags",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Max $maxTags tags per entry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search or create tag") },
                modifier = Modifier.fillMaxWidth(),
                shape = SingleItemShape,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )

            Spacer(Modifier.height(16.dp))

            // Recent tags suggestion
            if (searchQuery.isEmpty() && recentTags.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    recentTags.take(5).forEach { tag ->
                        val isSelected = tag.id in selectedTagIds
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onTagToggle(tag.id, false)
                                } else if (selectedTagIds.size < maxTags) {
                                    onTagToggle(tag.id, true)
                                }
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Tag list
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showCreateOption) {
                    item {
                        Surface(
                            onClick = {
                                onCreateTag(searchQuery)
                                searchQuery = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Text("Create \"$searchQuery\"", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                items(filteredTags, key = { it.id }) { tag ->
                    val isSelected = tag.id in selectedTagIds
                    Surface(
                        onClick = {
                            if (isSelected) {
                                onTagToggle(tag.id, false)
                            } else if (selectedTagIds.size < maxTags) {
                                onTagToggle(tag.id, true)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(tag.name, color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                            if (isSelected) {
                                Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SETTLEMENT COMPONENTS ---

@Composable
fun SettlementStatusChip(text: String, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = contentColor.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = AppFontFamily
            ),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementPickerSheet(
    eligibleTransactions: List<TransactionUiModel>,
    selectedSettlements: List<Pair<Long, Long>>,
    availableAmount: Long,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<Long, Long>>) -> Unit
) {
    // Local state for editing
    var localSelections by remember { mutableStateOf(selectedSettlements.toMap().toMutableMap()) }

    val totalAllocated = localSelections.values.sum()
    val remaining = availableAmount - totalAllocated

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()) {
            Text(
                "Settles Previous",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Select transactions to settle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Allocation summary
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Allocated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatCurrency(totalAllocated), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Formatters.formatCurrency(remaining),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Transaction list
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(eligibleTransactions, key = { it.transaction.id }) { uiModel ->
                    val txn = uiModel.transaction
                    val isSelected = localSelections.containsKey(txn.id)
                    val remainingOnTarget = txn.amount - uiModel.settledAmount
                    val allocatedToThis = localSelections[txn.id] ?: 0L

                    Surface(
                        onClick = {
                            if (isSelected) {
                                localSelections = localSelections.toMutableMap().apply { remove(txn.id) }
                            } else {
                                // Auto-allocate min of remaining target amount and available amount
                                val toAllocate = minOf(remainingOnTarget, remaining)
                                if (toAllocate > 0) {
                                    localSelections = localSelections.toMutableMap().apply { put(txn.id, toAllocate) }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        Formatters.formatCurrency(txn.amount),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (uiModel.settlementStatus == SettlementStatus.PARTIAL) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "(${Formatters.formatCurrency(remainingOnTarget)} left)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    Formatters.formatSheetDate(txn.date) + if (txn.note.isNotEmpty()) " • ${txn.note}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (txn.dueDate != null) {
                                    Text(
                                        Formatters.formatDueDate(txn.dueDate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            if (isSelected) {
                                Text(
                                    Formatters.formatCurrency(allocatedToThis),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = SplitLeftShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Cancel") }

                Spacer(Modifier.width(4.dp))

                Button(
                    onClick = { onConfirm(localSelections.toList()) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = SplitRightShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Confirm") }
            }
        }
    }
}
