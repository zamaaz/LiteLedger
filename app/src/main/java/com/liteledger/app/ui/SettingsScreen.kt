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
import androidx.compose.foundation.lazy.items
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
    onExportClick: () -> Unit,
    archivedCount: Int = 0,
    archivedPeople: List<com.liteledger.app.data.PersonWithBalance> = emptyList(),
    onUnarchive: (Long) -> Unit = {},
    onDeletePerson: (Long) -> Unit = {},
    onPersonClick: (Long, String) -> Unit = { _, _ -> }
) {
    val state by viewModel.state.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showTagsSheet by remember { mutableStateOf(false) }
    var showArchivedSheet by remember { mutableStateOf(false) }
    val allTags by viewModel.allTags.collectAsState()

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
        val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = with(density) { topBarHeightPx.toDp() + 16.dp },
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp + navigationBarPadding
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
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSwitchItem(Icons.Outlined.Schedule, "Show Last Activity", "Display last activity date on cards", state.showLastActivity) { viewModel.setShowLastActivity(it) }
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
                        // Tags management - only show if tags exist
                        if (state.tagCount > 0) {
                            SettingsNavigationItem(
                                icon = Icons.Outlined.Label,
                                title = "Tags",
                                subtitle = "${state.tagCount} tag${if (state.tagCount > 1) "s" else ""}",
                                onClick = { showTagsSheet = true }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

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

                        // --- ARCHIVED PEOPLE ---
                        if (archivedCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsNavigationItem(
                                icon = Icons.Outlined.Archive,
                                title = "Archived People",
                                subtitle = "$archivedCount hidden",
                                onClick = { showArchivedSheet = true }
                            )
                        }
                    }
                }
            }
            item {
                SettingsGroup(title = "About") {
                    SettingsContainer {
                        SettingsNavigationItem(Icons.Outlined.Info, "Version", "1.1.0", showChevron = false) {}
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

    if (showTagsSheet) {
        TagsManagementSheet(
            tags = allTags,
            onDismiss = { showTagsSheet = false },
            onRenameTag = { tag, newName -> viewModel.renameTag(tag, newName) },
            onDeleteTag = { tag -> viewModel.deleteTag(tag) }
        )
    }

    if (showArchivedSheet) {
        ArchivedPeopleSheet(
            archivedPeople = archivedPeople,
            onDismiss = { showArchivedSheet = false },
            onUnarchive = onUnarchive,
            onDelete = onDeletePerson,
            onPersonClick = { id, name -> showArchivedSheet = false; onPersonClick(id, name) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedPeopleSheet(
    archivedPeople: List<com.liteledger.app.data.PersonWithBalance>,
    onDismiss: () -> Unit,
    onUnarchive: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onPersonClick: (Long, String) -> Unit
) {
    var personToDelete by remember { mutableStateOf<com.liteledger.app.data.PersonWithBalance?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
            Text("Archived People", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${archivedPeople.size} hidden from main list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (archivedPeople.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No archived people", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(archivedPeople, key = { it.person.id }) { person ->
                        Surface(
                            onClick = { onPersonClick(person.person.id, person.person.name) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.person.name, style = MaterialTheme.typography.bodyLarge)
                                    if (person.person.isTemporary) {
                                        Text("One-time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row {
                                    IconButton(onClick = { onUnarchive(person.person.id) }) {
                                        Icon(Icons.Outlined.Unarchive, "Unarchive", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { personToDelete = person }) {
                                        Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (personToDelete != null) {
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Person?") },
            text = { Text("This will permanently delete \"${personToDelete!!.person.name}\" and all their transactions.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(personToDelete!!.person.id); personToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { personToDelete = null }) { Text("Cancel") } }
        )
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
                style = MaterialTheme.typography.headlineMedium,
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
                fontWeight = FontWeight.Normal
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
                        style = MaterialTheme.typography.titleMedium,
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
                            style = MaterialTheme.typography.bodyLarge,
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
                Text(title, style = MaterialTheme.typography.titleMedium)
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
                Text(title, style = MaterialTheme.typography.titleMedium)
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
                style = MaterialTheme.typography.headlineSmall,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsManagementSheet(
    tags: List<com.liteledger.app.data.Tag>,
    onDismiss: () -> Unit,
    onRenameTag: (com.liteledger.app.data.Tag, String) -> Unit,
    onDeleteTag: (com.liteledger.app.data.Tag) -> Unit
) {
    var tagToRename by remember { mutableStateOf<com.liteledger.app.data.Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<com.liteledger.app.data.Tag?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
            Text(
                "Manage Tags",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "${tags.size} tag${if (tags.size > 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (tags.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tags yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(tags, key = { it.id }) { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                                Row {
                                    IconButton(onClick = { tagToRename = tag; renameText = tag.name }) {
                                        Icon(Icons.Outlined.Edit, "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { tagToDelete = tag }) {
                                        Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (tagToRename != null) {
        AlertDialog(
            onDismissRequest = { tagToRename = null },
            title = { Text("Rename Tag") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameTag(tagToRename!!, renameText.trim())
                        }
                        tagToRename = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { tagToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (tagToDelete != null) {
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Tag?") },
            text = {
                Text("This will remove \"${tagToDelete!!.name}\" from all entries. The entries themselves won't be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tagToDelete!!)
                        tagToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text("Cancel") }
            }
        )
    }
}