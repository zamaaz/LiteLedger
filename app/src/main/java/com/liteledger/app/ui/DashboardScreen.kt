package com.liteledger.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.liteledger.app.data.PersonWithBalance
import com.liteledger.app.ui.theme.*
import com.liteledger.app.utils.rememberAppHaptic
import com.liteledger.app.utils.Formatters
import com.liteledger.app.utils.PrivacyText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    searchQuery: String,
    hapticsEnabled: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    onRenamePerson: (Long, String) -> Unit,
    onPersonClick: (Long) -> Unit,
    onDeletePerson: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onValidateName: suspend (String) -> Boolean,
    isPrivacyMode: Boolean,
    initialAction: String?,
    onActionConsumed: () -> Unit,
) {
    var showInputSheet by remember { mutableStateOf(false) }
    var selectedPersonId by remember { mutableStateOf<Long?>(null) }
    var selectedPersonName by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // --- SPRING EFFECT STATE ---
    var swipingCardIndex by remember { mutableStateOf<Int?>(null) }
    var swipeProgress by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()
    val haptic = rememberAppHaptic(enabled = hapticsEnabled)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // --- COLLAPSING LOGIC ---
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()

    // 1. Calculate dynamic limits
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 152.dp + statusBarHeight

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    // 2. State wrappers for NestedScrollConnection
    val maxPxState = rememberUpdatedState(maxTopBarHeightPx)
    val minPxState = rememberUpdatedState(minTopBarHeightPx)

    // 3. Offset State
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    // 4. Calculate actual height
    val topBarHeightPx = (maxTopBarHeightPx + topBarOffsetHeightPx).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)

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
                if (isSearchActive) return Offset.Zero
                val delta = available.y
                val isScrollingDown = delta < 0
                val scrollRange = minPxState.value - maxPxState.value

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val newOffset = (topBarOffsetHeightPx + delta).coerceIn(scrollRange, 0f)
                val consumed = newOffset - topBarOffsetHeightPx
                topBarOffsetHeightPx = newOffset
                return if (consumed != 0f) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress && !isSearchActive) {
            val scrollRange = minTopBarHeightPx - maxTopBarHeightPx
            val midpoint = scrollRange / 2f
            val targetOffset = if (topBarOffsetHeightPx > midpoint) 0f else scrollRange
            if (topBarOffsetHeightPx != targetOffset) {
                Animatable(topBarOffsetHeightPx).animateTo(targetOffset, animationSpec = spring(stiffness = Spring.StiffnessMedium)) {
                    topBarOffsetHeightPx = value
                }
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(initialAction) {
        when (initialAction) {
            "add_person" -> {
                showInputSheet = true
                onActionConsumed()
            }
            "search" -> {
                isSearchActive = true
                onActionConsumed()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .nestedScroll(nestedScrollConnection)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = with(density) { topBarHeightPx.toDp() + 16.dp },
                start = 16.dp, end = 16.dp, bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                AnimatedVisibility(visible = !isSearchActive && state.people.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
                    Column { ReportCard(state.totalReceive, state.totalPay, isPrivacyMode) }
                }
            }

            item {
                if (!isSearchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = {},
                        onSearch = {},
                        active = false,
                        onActiveChange = { isSearchActive = true },
                        placeholder = { Text("Search people...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) Icon(Icons.Outlined.Close, null) },
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            dividerColor = MaterialTheme.colorScheme.outlineVariant,
                            inputFieldColors = TextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { }
                    if (state.people.isNotEmpty()) Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (!isSearchActive) {
                if (state.people.isNotEmpty()) {
                    itemsIndexed(
                        items = state.people,
                        key = { _, item -> item.person.id }
                    ) { index, person ->
                        val shape = when {
                            state.people.size == 1 -> SingleItemShape
                            index == 0 -> TopItemShape
                            index == state.people.size - 1 -> BottomItemShape
                            else -> MiddleItemShape
                        }

                        // --- SPRING EFFECT FOR NEIGHBORS (HORIZONTAL) ---
                        val springOffsetX by animateFloatAsState(
                            targetValue = if (swipingCardIndex != null && index != swipingCardIndex) {
                                val distance = abs(index - swipingCardIndex!!)
                                val maxSpringPx = with(density) { 12.dp.toPx() }
                                val factor = (1f / (distance.toFloat() + 1f)) * abs(swipeProgress).coerceIn(0f, 1f)
                                // Follow the swipe direction (positive = right, negative = left)
                                val direction = if (swipeProgress >= 0) 1f else -1f
                                maxSpringPx * factor * direction
                            } else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "SpringOffsetX"
                        )

                        SwipeablePersonCard(
                            item = person,
                            index = index,
                            shape = shape,
                            springOffsetX = springOffsetX,
                            onSwipeStart = { idx -> swipingCardIndex = idx },
                            onSwipeProgress = { progress -> swipeProgress = progress },
                            onSwipeEnd = { swipingCardIndex = null; swipeProgress = 0f },
                            onRename = {
                                haptic.success()
                                selectedPersonId = person.person.id
                                selectedPersonName = person.person.name
                                isRenaming = true
                                showInputSheet = true
                            },
                            onDelete = {
                                haptic.warning()
                                selectedPersonId = person.person.id
                                showDeleteConfirmation = true
                            },
                            onClick = { haptic.click(); onPersonClick(person.person.id) },
                            onLongPress = {
                                haptic.heavy()
                                selectedPersonId = person.person.id
                                selectedPersonName = person.person.name
                            }
                        )
                    }
                } else {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize()) {
                            EmptyState()
                        }
                    }
                }
            }
        }

        // ... (Search Active Logic Omitted for Brevity - It remains similar but can be swiped too if desired) ...
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier.zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = {},
                    active = true,
                    onActiveChange = {
                        if (!it) {
                            isSearchActive = false
                            onSearchQueryChange("")
                            topBarOffsetHeightPx = 0f
                        }
                    },
                    placeholder = { Text("Search people...") },
                    leadingIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            onSearchQueryChange("")
                            topBarOffsetHeightPx = 0f
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Outlined.Close, "Clear") }
                        }
                    },
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant,
                        inputFieldColors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                ) {
                    if (searchQuery.isEmpty()) {
                        SearchInitialState()
                    } else {
                        if (state.people.isEmpty()) {
                            SearchNotFoundState()
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(
                                    items = state.people,
                                    key = { _, item -> item.person.id }
                                ) { index, person ->
                                    val shape = when {
                                        state.people.size == 1 -> SingleItemShape
                                        index == 0 -> TopItemShape
                                        index == state.people.size - 1 -> BottomItemShape
                                        else -> MiddleItemShape
                                    }

                                    PersonRow(
                                        item = person,
                                        onClick = {
                                            haptic.click()
                                            onPersonClick(it)
                                            scope.launch {
                                                delay(300)
                                                isSearchActive = false
                                                onSearchQueryChange("")
                                            }
                                        },
                                        onLongPress = { haptic.heavy(); selectedPersonId = it; selectedPersonName = person.person.name },
                                        shape = shape
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isSearchActive) {
            CustomDashboardTopBar(
                height = with(density) { topBarHeightPx.toDp() },
                fraction = collapseFraction,
                statusBarHeight = statusBarHeight,
                onSettingsClick = onSettingsClick
            )

            FloatingActionButton(
                onClick = { haptic.click(); isRenaming = false; selectedPersonName = ""; showInputSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) { Icon(Icons.Outlined.Add, "Add") }
        }
    }

    // ... (Keep PersonInputSheet, DeleteConfirmationSheet logic) ...
    if (showInputSheet) {
        PersonInputSheet(
            initialName = if (isRenaming) selectedPersonName else "",
            isRename = isRenaming,
            onDismiss = {
                showInputSheet = false
                selectedPersonId = null
            },
            onValidate = onValidateName,
            onConfirm = { name ->
                if (isRenaming && selectedPersonId != null) {
                    onRenamePerson(selectedPersonId!!, name)
                    selectedPersonId = null
                } else {
                    onAddPerson(name)
                }
                showInputSheet = false
            }
        )
    }

    if (selectedPersonId != null && !showDeleteConfirmation && !showInputSheet) {
        // Fallback for long press if user prefers that, or just remove if Swipe is primary.
        // Keeping it for accessibility/completeness.
        ModalBottomSheet(
            onDismissRequest = { selectedPersonId = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Text("Manage Person", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { isRenaming = true; showInputSheet = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = SplitLeftShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Rename") }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = SplitRightShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete") }
                }
            }
        }
    }

    if (showDeleteConfirmation && selectedPersonId != null) {
        DeleteConfirmationSheet(
            onDismiss = { showDeleteConfirmation = false; selectedPersonId = null },
            onConfirm = { onDeletePerson(selectedPersonId!!); showDeleteConfirmation = false; selectedPersonId = null }
        )
    }
}

// --- CUSTOM SWIPEABLE PERSON CARD WITH SPRING EFFECT ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeablePersonCard(
    item: PersonWithBalance,
    index: Int,
    shape: Shape,
    springOffsetX: Float,
    onSwipeStart: (Int) -> Unit,
    onSwipeProgress: (Float) -> Unit,
    onSwipeEnd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Threshold for triggering action (in pixels)
    val actionThresholdPx = with(density) { 120.dp.toPx() }

    // Resistance factor - creates spring-like pull back effect (0 = no resistance, 1 = full resistance)
    val resistanceFactor = 0.55f

    // Animatable for smooth swipe and snap-back
    val offsetX = remember { Animatable(0f) }

    // Derived swipe direction and progress
    val currentOffset = offsetX.value
    val swipeDirection = when {
        currentOffset > 0 -> SwipeDirection.StartToEnd // Rename
        currentOffset < 0 -> SwipeDirection.EndToStart // Delete
        else -> SwipeDirection.None
    }
    val progress = (abs(currentOffset) / actionThresholdPx).coerceIn(0f, 1f)

    // Shape morphing - transitions to SingleItemShape when swiping
    val morphedShape = if (progress > 0.05f) SingleItemShape else shape

    // Background colors
    val renameColor = MaterialTheme.colorScheme.secondaryContainer
    val deleteColor = MaterialTheme.colorScheme.errorContainer
    val backgroundColor by animateColorAsState(
        targetValue = when {
            currentOffset > 0 -> renameColor
            currentOffset < 0 -> deleteColor
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SwipeBgColor"
    )

    // Icon properties - using outlined icons
    val icon = when (swipeDirection) {
        SwipeDirection.StartToEnd -> Icons.Outlined.Edit
        SwipeDirection.EndToStart -> Icons.Outlined.Delete
        SwipeDirection.None -> Icons.Outlined.Edit
    }
    val iconTint = when (swipeDirection) {
        SwipeDirection.StartToEnd -> MaterialTheme.colorScheme.onSecondaryContainer
        SwipeDirection.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
        SwipeDirection.None -> Color.Transparent
    }
    val iconAlignment = when (swipeDirection) {
        SwipeDirection.StartToEnd -> Alignment.CenterStart
        SwipeDirection.EndToStart -> Alignment.CenterEnd
        SwipeDirection.None -> Alignment.Center
    }

    // Icon scale based on progress (grows as user swipes further)
    val iconScale by animateFloatAsState(
        targetValue = if (progress > 0f) 0.9f + (0.3f * progress) else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "IconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Horizontal spring offset for neighbor cards
                translationX = springOffsetX
            }
    ) {
        // Background layer (shows immediately on swipe)
        // Always use SingleItemShape to match the morphed foreground and prevent corner glitches
        if (currentOffset != 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(SingleItemShape)
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = iconAlignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = progress.coerceIn(0.4f, 1f)),
                    modifier = Modifier.size(24.dp * iconScale)
                )
            }
        }

        // Foreground card layer
        Box(
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onSwipeStart(index)
                        },
                        onDragEnd = {
                            val finalOffset = offsetX.value
                            val triggered = abs(finalOffset) >= actionThresholdPx

                            scope.launch {
                                // Snap back with tight spring animation
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }

                            // Trigger action ONLY on release if threshold was met
                            if (triggered) {
                                if (finalOffset > 0) {
                                    onRename()
                                } else {
                                    onDelete()
                                }
                            }

                            onSwipeEnd()
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            onSwipeEnd()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                // Apply resistance - the further you drag, the harder it gets
                                val currentPos = offsetX.value
                                val resistedDrag = dragAmount * (1f - (abs(currentPos) / (actionThresholdPx * 2f)) * resistanceFactor)
                                val newValue = currentPos + resistedDrag
                                offsetX.snapTo(newValue)
                                onSwipeProgress(newValue / actionThresholdPx)
                            }
                        }
                    )
                }
        ) {
            PersonRow(
                item = item,
                onClick = { onClick() },
                onLongPress = { onLongPress() },
                shape = morphedShape
            )
        }
    }
}

enum class SwipeDirection {
    None, StartToEnd, EndToStart
}

// ... (Keep existing helpers: CustomDashboardTopBar, ReportCard, etc.) ...
@Composable
fun CustomDashboardTopBar(height: Dp, fraction: Float, statusBarHeight: Dp, onSettingsClick: () -> Unit) {
    val titleSize = lerpTextUnit(32.sp, 22.sp, fraction)
    val titleStartPadding = androidx.compose.ui.unit.lerp(16.dp, 16.dp, fraction)
    val titleBottomPadding = androidx.compose.ui.unit.lerp(24.dp, 16.dp, fraction)

    Surface(
        modifier = Modifier.height(height).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (fraction > 0.9f) 3.dp else 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            FilledIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = statusBarHeight + 8.dp, end = 8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }

            Text(
                text = "LiteLedger",
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = titleStartPadding, bottom = titleBottomPadding)
            )
        }
    }
}

@Composable
fun ReportCard(totalReceive: Long, totalPay: Long, isPrivacyMode: Boolean) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeGreen = if (isDark) Color(0xFF81C784) else MoneyGreen
    val activeRed = if (isDark) Color(0xFFE57373) else MoneyRed

    Card(modifier = Modifier.fillMaxWidth(), shape = SingleItemShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("RECEIVE", color = activeGreen, style = MaterialTheme.typography.labelMedium)
                PrivacyText(
                    text = Formatters.formatCurrency(totalReceive),
                    isPrivacyMode = isPrivacyMode,
                    color = activeGreen,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("PAY", color = activeRed, style = MaterialTheme.typography.labelMedium)
                PrivacyText(
                    text = Formatters.formatCurrency(totalPay),
                    isPrivacyMode = isPrivacyMode,
                    color = activeRed,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

fun lerpTextUnit(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit = (start.value + (stop.value - start.value) * fraction).sp

// ... (Rest of existing composables: PersonInputSheet, DeleteConfirmationSheet, PersonRow, EmptyState...) ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonInputSheet(initialName: String, isRename: Boolean, onDismiss: () -> Unit, onValidate: suspend (String) -> Boolean, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun updateName(newName: String) { name = newName; if (errorText != null) errorText = null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
            Text(if (isRename) "Rename Person" else "New Person", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = name, onValueChange = { updateName(it) }, label = { Text("Name") }, isError = errorText != null,
                supportingText = { if (errorText != null) Text(errorText!!, color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.fillMaxWidth(), shape = SingleItemShape, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp), shape = SplitLeftShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Cancel") }
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) { errorText = "Name cannot be empty" }
                    else if (trimmed.equals(initialName, ignoreCase = true)) { onConfirm(trimmed) }
                    else { scope.launch { if (onValidate(trimmed)) errorText = "Person already exists" else onConfirm(trimmed) } }
                }, modifier = Modifier.weight(1f).height(56.dp), shape = SplitRightShape) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmationSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Delete Person?", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            Text("This will delete all history permanently.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp), shape = SplitLeftShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Cancel") }
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = onConfirm, modifier = Modifier.weight(1f).height(56.dp), shape = SplitRightShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonRow(item: PersonWithBalance, onClick: (Long) -> Unit, onLongPress: (Long) -> Unit, shape: Shape) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeGreen = if (isDark) Color(0xFF81C784) else MoneyGreen
    val activeRed = if (isDark) Color(0xFFE57373) else MoneyRed
    val balanceColor = if (item.balance >= 0) activeGreen else activeRed

    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = { onClick(item.person.id) }, onLongClick = { onLongPress(item.person.id) })) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text(item.person.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(16.dp))
            Text(item.person.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(Formatters.formatCurrency(item.balance), color = balanceColor, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 120.dp)) {
            Icon(Icons.Outlined.Person, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text("No debts yet", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchInitialState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 48.dp)) {
            Text("No recent searches", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchNotFoundState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 48.dp)) {
            Icon(Icons.Outlined.SearchOff, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Not found", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}