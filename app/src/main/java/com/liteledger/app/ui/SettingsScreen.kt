package com.liteledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liteledger.app.data.AppTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    isPrivacyMode: Boolean,
    onPrivacyToggle: (Boolean) -> Unit,
    onExportClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }

    // --- COLLAPSING LOGIC ---
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

    // 3. Offset State
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    // 4. Calculate actual height
    val topBarHeightPx = (maxTopBarHeightPx + topBarOffsetHeightPx).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)

    // 5. Fraction
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

    // --- DATA ---
    val currentThemeLabel = when(state.theme) {
        AppTheme.LIGHT -> "Light Theme"
        AppTheme.DARK -> "Dark Theme"
        AppTheme.SYSTEM -> "Follow System"
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> viewModel.onBackupSelected(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> viewModel.onRestoreSelected(uri) }

    // --- UI ---
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
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsGroup(title = "Appearance") {
                    SettingsContainer {
                        SettingsSelectorItem(Icons.Outlined.WbSunny, "App Theme", "Switch between light, dark, or follow system appearance.", currentThemeLabel) { showThemeSheet = true }
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSwitchItem(Icons.Outlined.Vibration, "Haptic Feedback", "Vibrate on interactions", state.hapticsEnabled) { viewModel.setHaptics(it) }
                    }
                }
            }
            item {
                SettingsGroup(title = "Security") {
                    SettingsContainer {
                        SettingsSwitchItem(Icons.Outlined.Fingerprint, "Biometric Lock", "Require fingerprint to open app", state.biometricEnabled) { viewModel.setBiometric(it) }

                        // --- PRIVACY TOGGLE ADDED HERE ---
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSwitchItem(
                            icon = Icons.Outlined.VisibilityOff,
                            title = "Privacy Mode",
                            subtitle = "Hide balances by default",
                            checked = isPrivacyMode,
                            onCheckedChange = onPrivacyToggle
                        )
                    }
                }
            }
            item {
                SettingsGroup(title = "Data") {
                    SettingsContainer {
                        SettingsNavigationItem(Icons.Outlined.Backup, "Backup Data", "Save your ledger to a JSON file") {
                            backupLauncher.launch("LiteLedger_Backup_${DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now())}.json")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsNavigationItem(Icons.Outlined.CloudDownload, "Restore Data", "Import ledger from a file") {
                            restoreLauncher.launch(arrayOf("application/json"))
                        }

                        // --- EXPORT DATA ADDED HERE ---
                         Spacer(modifier = Modifier.height(4.dp))
                         SettingsNavigationItem(
                            icon = Icons.Outlined.FileDownload,
                            title = "Export Data",
                            subtitle = "Save as CSV (Excel)",
                            onClick = onExportClick
                        )
                    }
                }
            }
            item {
                SettingsGroup(title = "About") {
                    SettingsContainer {
                        SettingsNavigationItem(Icons.Outlined.Info, "Version", "1.0.0 (Lite)", showChevron = false) {}
                    }
                }
            }
        }

        CustomSettingsTopBar(
            height = with(density) { topBarHeightPx.toDp() },
            fraction = collapseFraction,
            statusBarHeight = statusBarHeight,
            onBack = onBack
        )
    }

    if (showThemeSheet) {
        ThemeSelectionSheet(state.theme, { showThemeSheet = false }, { viewModel.setTheme(it); showThemeSheet = false })
    }
}

// ... (Rest of the file remains unchanged: CustomSettingsTopBar, SettingsGroup, etc.)
@Composable
fun CustomSettingsTopBar(
    height: Dp,
    fraction: Float,
    statusBarHeight: Dp,
    onBack: () -> Unit
) {
    val titleSize = lerpTextUnit(32.sp, 22.sp, fraction)
    val titleStartPadding = androidx.compose.ui.unit.lerp(16.dp, 64.dp, fraction)
    val titleBottomPadding = androidx.compose.ui.unit.lerp(24.dp, 16.dp, fraction)

    Surface(
        modifier = Modifier.height(height).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (fraction > 0.9f) 3.dp else 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(top = statusBarHeight + 8.dp, start = 8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = "Settings",
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = titleStartPadding, bottom = titleBottomPadding)
            )
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.background(
            color = Color.Transparent,
            shape = RoundedCornerShape(24.dp)
        )
            .clip(shape = RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

@Composable
fun SettingsItemSurface(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
fun SettingsSelectorItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selectedValue: String,
    onClick: () -> Unit
) {
    SettingsItemSurface(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(icon)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Spacer(modifier = Modifier.width(56.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedValue,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItemSurface(onClick = { onCheckedChange(!checked) }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    SettingsItemSurface(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun IconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionSheet(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit
) {
    val options = listOf(
        AppTheme.LIGHT to "Light Theme",
        AppTheme.DARK to "Dark Theme",
        AppTheme.SYSTEM to "Follow System"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "App Theme",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            options.forEach { (theme, label) ->
                val isSelected = (theme == currentTheme)
                val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(containerColor)
                        .clickable { onThemeSelected(theme) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if(isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}