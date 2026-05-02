package com.liteledger.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liteledger.app.data.PersonWithBalance
import com.liteledger.app.data.Tag
import com.liteledger.app.ui.theme.*
import com.liteledger.app.utils.Formatters

/** Split mode for dividing expenses */
enum class SplitMode(val label: String) {
    EQUAL("Equal"), AMOUNT("Amount"), PERCENTAGE("%")
}

/** Data class for each split transaction to be created */
data class SplitTransactionData(
    val personId: Long, val personName: String, val amount: Long, // in paise
    val note: String, val date: Long, val dueDate: Long?, val tagIds: List<Long>
)

/** Represents a participant in the split - either "Me" or a Person */
private sealed class SplitParticipant {
    /** The user themselves - no transaction created for this */
    data object Me : SplitParticipant()

    /** A real person from the database */
    data class Person(val personWithBalance: PersonWithBalance) : SplitParticipant()

    val id: Long
        get() = when (this) {
            is Me -> -1L // Special ID for "Me"
            is Person -> personWithBalance.person.id
        }

    val displayName: String
        get() = when (this) {
            is Me -> "You"
            is Person -> personWithBalance.person.name
        }

    val isMe: Boolean
        get() = this is Me
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitTransactionSheet(
    allPeople: List<PersonWithBalance>,
    allTags: List<Tag>,
    recentTags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (List<SplitTransactionData>) -> Unit,
    onCreateTag: (String) -> Unit
) {
    // --- STATE ---
    var includingMe by remember { mutableStateOf(false) }
    var selectedPeopleIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var totalAmountText by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(SplitMode.EQUAL) }

    // Custom amounts per participant (id -> amount as string)
    var customAmounts by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    // Percentages per participant (id -> percentage as string)
    var percentages by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // Shared metadata
    var note by remember { mutableStateOf("") }
    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var selectedTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // UI state
    var showParticipantPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    // Track if user has made changes (for dismiss protection)
    val hasChanges = remember(selectedPeopleIds, includingMe, totalAmountText, note) {
        selectedPeopleIds.isNotEmpty() || includingMe || totalAmountText.isNotEmpty() || note.isNotEmpty()
    }

    // --- BUILD PARTICIPANT LIST ---
    val allParticipants: List<SplitParticipant> =
        remember(includingMe, selectedPeopleIds, allPeople) {
            buildList {
                if (includingMe) add(SplitParticipant.Me)
                allPeople.filter { it.person.id in selectedPeopleIds }.forEach {
                    add(SplitParticipant.Person(it))
                }
            }
        }

    val participantCount = allParticipants.size
    val hasParticipants = participantCount > 0

    // --- CALCULATIONS ---
    val totalAmount = (totalAmountText.toLongOrNull() ?: 0L) * 100 // Convert to paise

    // Calculate splits based on mode - includes Me in calculations
    val calculatedSplits: Map<Long, Long> =
        remember(splitMode, totalAmount, allParticipants, customAmounts, percentages) {
            when (splitMode) {
                SplitMode.EQUAL -> {
                    if (participantCount == 0 || totalAmount == 0L) emptyMap()
                    else {
                        val baseAmount = totalAmount / participantCount
                        val remainder = totalAmount % participantCount
                        allParticipants.mapIndexed { index, participant ->
                            val amount = if (index == participantCount - 1) baseAmount + remainder
                            else baseAmount
                            participant.id to amount
                        }.toMap()
                    }
                }

                SplitMode.AMOUNT -> {
                    allParticipants.associate { participant ->
                        val amountStr = customAmounts[participant.id] ?: ""
                        val amount = (amountStr.toLongOrNull() ?: 0L) * 100
                        participant.id to amount
                    }
                }

                SplitMode.PERCENTAGE -> {
                    if (totalAmount == 0L) emptyMap()
                    else {
                        val amounts = allParticipants.map { participant ->
                            val pctStr = percentages[participant.id] ?: ""
                            val pct = pctStr.toFloatOrNull() ?: 0f
                            participant.id to (totalAmount * pct / 100f).toLong()
                        }
                        val calculatedTotal = amounts.sumOf { it.second }
                        val remainder = totalAmount - calculatedTotal
                        amounts.mapIndexed { index, pair ->
                            if (index == amounts.lastIndex) pair.first to (pair.second + remainder)
                            else pair
                        }.toMap()
                    }
                }
            }
        }

    // Validation
    val enteredTotal = calculatedSplits.values.sum()
    val totalPercentage =
        allParticipants.sumOf { (percentages[it.id]?.toFloatOrNull() ?: 0f).toDouble() }.toFloat()

    val hasRealPeople = selectedPeopleIds.isNotEmpty()

    val isValid = remember(
        hasRealPeople, totalAmount, splitMode, enteredTotal, totalPercentage, hasParticipants
    ) {
        hasRealPeople && hasParticipants && totalAmount > 0 && when (splitMode) {
            SplitMode.EQUAL -> true
            SplitMode.AMOUNT -> enteredTotal == totalAmount
            SplitMode.PERCENTAGE -> kotlin.math.abs(totalPercentage - 100f) < 0.01f
        }
    }

    // Dynamic colors
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeRed = if (isDark) Color(0xFFE57373) else MoneyRed

    // Sheet state with dismiss protection
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true, confirmValueChange = { sheetValue ->
            // Prevent accidental dismiss if there are changes
            if (sheetValue == SheetValue.Hidden && hasChanges) {
                false // Block dismiss - user must use Cancel button
            } else {
                true
            }
        })

    ModalBottomSheet(
        onDismissRequest = {
            if (!hasChanges) onDismiss()
            // If hasChanges, dismissal is blocked by confirmValueChange
        }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, sheetState = sheetState
    ) {
        val splitSheetLazyListState = rememberLazyListState()
        LazyColumn(
            state = splitSheetLazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .bottomSheetScrollFix(splitSheetLazyListState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- HEADER ---
            item {
                Text(
                    text = "Split Transaction",
                    style = MaterialTheme.typography.headlineMedium,
                    color = activeRed
                )
            }

            // --- SECTION 1: PARTICIPANTS SELECTION (Button to open picker) ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Participants", style = MaterialTheme.typography.titleMedium
                    )

                    // Button to open participant picker
                    Surface(
                        onClick = { showParticipantPicker = true },
                        shape = SingleItemShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (participantCount == 0) {
                                    Text(
                                        text = "Select participants",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column {
                                        Text(
                                            text = "$participantCount selected",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = allParticipants.take(
                                            3
                                        ).joinToString(
                                            ", "
                                        ) {
                                            it.displayName
                                        } + if (participantCount > 3) " +${participantCount - 3} more"
                                        else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = "Select",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Selected participants as chips (for quick removal)
                    AnimatedVisibility(
                        visible = hasParticipants, enter = expandVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(), exit = shrinkVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeOut()
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (includingMe) {
                                InputChip(
                                    selected = true, onClick = {
                                    includingMe = false
                                    customAmounts = customAmounts - (-1L)
                                    percentages = percentages - (-1L)
                                }, label = { Text("You") }, leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(
                                            18.dp
                                        )
                                    )
                                }, trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(
                                            18.dp
                                        )
                                    )
                                }, colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                )
                            }
                            allPeople.filter {
                                it.person.id in selectedPeopleIds
                            }.forEach { person ->
                                InputChip(selected = true, onClick = {
                                    selectedPeopleIds = selectedPeopleIds - person.person.id
                                    customAmounts = customAmounts - person.person.id
                                    percentages = percentages - person.person.id
                                }, label = {
                                    Text(
                                        person.person.name
                                    )
                                }, trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(
                                            18.dp
                                        )
                                    )
                                })
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: TOTAL AMOUNT ---
            item {
                OutlinedTextField(
                    value = totalAmountText,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() }) {
                            totalAmountText = it
                        }
                    },
                    label = { Text("Total Amount") },
                    prefix = { Text("₹") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = SingleItemShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }

            // --- SECTION 3: SPLIT MODE ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Split Mode", style = MaterialTheme.typography.titleMedium
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SplitMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = splitMode == mode,
                                onClick = { splitMode = mode },
                                shape = when (index) {
                                    0 -> SplitLeftShape
                                    SplitMode.entries.lastIndex -> SplitRightShape
                                    else -> MiddleItemShape
                                }
                            ) { Text(mode.label) }
                        }
                    }
                }
            }

            // --- SPLIT PREVIEW/INPUT with animation ---
            item {
                AnimatedVisibility(
                    visible = hasParticipants && totalAmount > 0, enter = expandVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(), exit = shrinkVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Split Preview", style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(4.dp))

                        // Animated content for mode switching
                        AnimatedContent(
                            targetState = splitMode, transitionSpec = {
                                (fadeIn(
                                    animationSpec = tween(200)
                                ) + slideInVertically {
                                    it / 4
                                }).togetherWith(
                                    fadeOut(
                                        animationSpec = tween(
                                            150
                                        )
                                    ) + slideOutVertically {
                                        -it / 4
                                    })
                            }, label = "SplitModeAnimation"
                        ) { currentMode ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                allParticipants.forEachIndexed { index, participant ->
                                    val shape = when {
                                        participantCount == 1 -> SingleItemShape
                                        index == 0 -> TopItemShape
                                        index == participantCount - 1 -> BottomItemShape
                                        else -> MiddleItemShape
                                    }

                                    val containerColor =
                                        if (participant.isMe) MaterialTheme.colorScheme.tertiaryContainer.copy(
                                            alpha = 0.3f
                                        )
                                        else MaterialTheme.colorScheme.surfaceContainerHigh

                                    Surface(
                                        shape = shape, color = containerColor
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // CHANGED: added min height to keep "Equal" rows consistent with input rows
                                                .heightIn(min = 68.dp)
                                                // CHANGED: reduced vertical padding from 14.dp to 8.dp to reduce bloat
                                                .padding(
                                                    horizontal = 16.dp, vertical = 8.dp
                                                ),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(
                                                    8.dp
                                                ),
                                                modifier = Modifier.weight(
                                                    1f
                                                )
                                            ) {
                                                if (participant.isMe) {
                                                    Icon(
                                                        Icons.Outlined.Person,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(
                                                            20.dp
                                                        ),
                                                        tint = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = participant.displayName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (participant.isMe) MaterialTheme.colorScheme.tertiary
                                                        else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (participant.isMe) {
                                                        Text(
                                                            text = "Your share",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            when (currentMode) {
                                                SplitMode.EQUAL -> {
                                                    Text(
                                                        text = Formatters.formatCurrency(
                                                            calculatedSplits[participant.id] ?: 0L
                                                        ),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = if (participant.isMe) MaterialTheme.colorScheme.tertiary
                                                        else activeRed
                                                    )
                                                }

                                                SplitMode.AMOUNT -> {
                                                    OutlinedTextField(
                                                        value = customAmounts[participant.id] ?: "",
                                                        onValueChange = { value ->
                                                            if (value.all { c -> c.isDigit() }) {
                                                                customAmounts =
                                                                    customAmounts + (participant.id to value)
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .width(100.dp)
                                                            // fixed: m3 really wants 56dp min. anything less clips without custom basictextfield
                                                            .height(56.dp),
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodyMedium,
                                                        // fixed: removed invalid contentPadding param
                                                        prefix = {
                                                            Text(
                                                                "₹",
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        },
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number
                                                        ),
                                                        shape = RoundedCornerShape(
                                                            12.dp
                                                        ),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.5f
                                                            ),
                                                            focusedBorderColor = if (participant.isMe) MaterialTheme.colorScheme.tertiary
                                                            else MaterialTheme.colorScheme.primary
                                                        )
                                                    )
                                                }

                                                SplitMode.PERCENTAGE -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                    ) {
                                                        OutlinedTextField(
                                                            value = percentages[participant.id]
                                                                ?: "",
                                                            onValueChange = { value ->
                                                                if (value.isEmpty() || value.matches(
                                                                        Regex(
                                                                            "^\\d*\\.?\\d*$"
                                                                        )
                                                                    )
                                                                ) {
                                                                    percentages =
                                                                        percentages + (participant.id to value)
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .width(72.dp)
                                                                // fixed: 56dp min
                                                                .height(56.dp),
                                                            singleLine = true,
                                                            textStyle = MaterialTheme.typography.bodyMedium,
                                                            // fixed: removed invalid contentPadding param
                                                            suffix = {
                                                                Text(
                                                                    "%",
                                                                    style = MaterialTheme.typography.bodySmall
                                                                )
                                                            },
                                                            keyboardOptions = KeyboardOptions(
                                                                keyboardType = KeyboardType.Decimal
                                                            ),
                                                            shape = RoundedCornerShape(
                                                                12.dp
                                                            ),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                                    alpha = 0.5f
                                                                ),
                                                                focusedBorderColor = if (participant.isMe) MaterialTheme.colorScheme.tertiary
                                                                else MaterialTheme.colorScheme.primary
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Summary row for Amount and Percentage modes
                        AnimatedVisibility(
                            visible = splitMode != SplitMode.EQUAL,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = 8.dp
                                    ), horizontalArrangement = Arrangement.End
                            ) {
                                val isError = when (splitMode) {
                                    SplitMode.AMOUNT -> enteredTotal != totalAmount
                                    SplitMode.PERCENTAGE -> kotlin.math.abs(
                                        totalPercentage - 100f
                                    ) >= 0.01f

                                    else -> false
                                }
                                Text(
                                    text = when (splitMode) {
                                        SplitMode.AMOUNT -> "Entered: ${
                                            Formatters.formatCurrency(
                                                enteredTotal
                                            )
                                        } / ${Formatters.formatCurrency(totalAmount)}"

                                        SplitMode.PERCENTAGE -> "Total: ${
                                            String.format(
                                                "%.1f", totalPercentage
                                            )
                                        }%"

                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 4: SHARED METADATA ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Details", style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    // Description
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Description") },
                        placeholder = { Text("What was this for?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = TopItemShape,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )

                    // Due Date & Tags row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { showDueDatePicker = true },
                            shape = MiddleSplitLeftShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.Event,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                if (dueDate == null) {
                                    Text(
                                        "Due date",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        Formatters.formatSheetDate(
                                            dueDate!!
                                        ), style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Outlined.Close,
                                        "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(
                                                16.dp
                                            )
                                            .clickable {
                                                dueDate = null
                                            })
                                }
                            }
                        }

                        Spacer(Modifier.width(2.dp))

                        Surface(
                            onClick = { showTagPicker = true },
                            shape = MiddleSplitRightShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.Sell,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                if (selectedTagIds.isEmpty()) {
                                    Text(
                                        "Tags",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val selectedTags = allTags.filter {
                                        it.id in selectedTagIds
                                    }
                                    Text(
                                        selectedTags.take(2).joinToString {
                                            it.name
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Date & Time row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { showDatePicker = true },
                            shape = BottomLeftSplitShape.copy(
                                bottomEnd = androidx.compose.foundation.shape.CornerSize(
                                    4.dp
                                )
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.CalendarToday,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    Formatters.formatSheetDate(
                                        selectedTimestamp
                                    ), style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(Modifier.width(2.dp))

                        Surface(
                            onClick = { showTimePicker = true },
                            shape = BottomRightSplitShape.copy(
                                bottomStart = androidx.compose.foundation.shape.CornerSize(
                                    4.dp
                                )
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    Formatters.formatTime(
                                        selectedTimestamp
                                    ), style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // --- ACTION BUTTONS ---
            item {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = SplitLeftShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Cancel") }

                    Spacer(Modifier.width(4.dp))

                    Button(
                        onClick = {
                            // Generate split transaction data - ONLY
                            // for real people, not "Me"
                            val splitData =
                                allParticipants.filterNot { it.isMe }.map { participant ->
                                    val person =
                                        (participant as SplitParticipant.Person).personWithBalance
                                    SplitTransactionData(
                                        personId = person.person.id,
                                        personName = person.person.name,
                                        amount = calculatedSplits[participant.id] ?: 0L,
                                        note = note,
                                        date = selectedTimestamp,
                                        dueDate = dueDate,
                                        tagIds = selectedTagIds
                                    )
                                }
                            onSave(splitData)
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = SplitRightShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeRed, contentColor = Color.White
                        )
                    ) { Text("Split & Save") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // --- PARTICIPANT PICKER SHEET ---
    if (showParticipantPicker) {
        ParticipantPickerSheet(
            allPeople = allPeople,
            selectedPeopleIds = selectedPeopleIds,
            includingMe = includingMe,
            onMeToggle = { includingMe = it },
            onPersonToggle = { personId, selected ->
                selectedPeopleIds = if (selected) {
                    selectedPeopleIds + personId
                } else {
                    selectedPeopleIds - personId
                }
            },
            onDismiss = { showParticipantPicker = false })
    }

    // --- DATE PICKER DIALOG ---
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = selectedTimestamp,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(
                    utcTimeMillis: Long
                ): Boolean = utcTimeMillis <= System.currentTimeMillis()
            })
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(
                onClick = {
                    dateState.selectedDateMillis?.let { newDateMillis ->
                        val oldZone = java.time.Instant.ofEpochMilli(
                            selectedTimestamp
                        ).atZone(
                            java.time.ZoneId.systemDefault()
                        )
                        val newZone = java.time.Instant.ofEpochMilli(
                            newDateMillis
                        ).atZone(
                            java.time.ZoneId.of(
                                "UTC"
                            )
                        )

                        var newTimestamp = oldZone.withYear(newZone.year).withMonth(
                            newZone.monthValue
                        ).withDayOfMonth(
                            newZone.dayOfMonth
                        ).toInstant().toEpochMilli()

                        if (newTimestamp > System.currentTimeMillis()) {
                            newTimestamp = System.currentTimeMillis()
                        }

                        selectedTimestamp = newTimestamp
                    }
                    showDatePicker = false
                }) { Text("OK") }
        }, dismissButton = {
            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
        }) { DatePicker(state = dateState) }
    }

    // --- TIME PICKER DIALOG ---
    if (showTimePicker) {
        val zdt = java.time.Instant.ofEpochMilli(selectedTimestamp)
            .atZone(java.time.ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = zdt.hour, initialMinute = zdt.minute, is24Hour = false
        )

        val currentZonedTime = java.time.Instant.ofEpochMilli(selectedTimestamp)
            .atZone(java.time.ZoneId.systemDefault())
        val tentativeTime =
            currentZonedTime.withHour(timeState.hour).withMinute(timeState.minute).toInstant()
                .toEpochMilli()
        val isFuture = tentativeTime > System.currentTimeMillis()

        AlertDialog(onDismissRequest = { showTimePicker = false }, confirmButton = {
            TextButton(
                onClick = {
                    selectedTimestamp = tentativeTime
                    showTimePicker = false
                }, enabled = !isFuture
            ) { Text("OK") }
        }, dismissButton = {
            TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
        }, title = { Text("Select Time") }, text = {
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
        })
    }

    // --- DUE DATE PICKER DIALOG ---
    if (showDueDatePicker) {
        val dueDateState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate ?: (System.currentTimeMillis() + 86400000L)
        )
        DatePickerDialog(onDismissRequest = { showDueDatePicker = false }, confirmButton = {
            TextButton(
                onClick = {
                    dueDateState.selectedDateMillis?.let {
                        dueDate = it
                    }
                    showDueDatePicker = false
                }) { Text("Set") }
        }, dismissButton = {
            TextButton(onClick = { showDueDatePicker = false }) {
                Text("Cancel")
            }
        }) { DatePicker(state = dueDateState) }
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
                    (selectedTagIds + tagId).take(2)
                } else {
                    selectedTagIds - tagId
                }
            },
            onCreateTag = onCreateTag
        )
    }
}

// --- PARTICIPANT PICKER SHEET ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantPickerSheet(
    allPeople: List<PersonWithBalance>,
    selectedPeopleIds: Set<Long>,
    includingMe: Boolean,
    onMeToggle: (Boolean) -> Unit,
    onPersonToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Participants", style = MaterialTheme.typography.titleLarge
                )
                val selectedCount = selectedPeopleIds.size + (if (includingMe) 1 else 0)
                if (selectedCount > 0) {
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(
                                horizontal = 12.dp, vertical = 4.dp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider()

            // Scrollable list - with scroll fix to prevent sheet collapse during fast scrolling
            val lazyListState = rememberLazyListState()
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 400.dp)
                    .bottomSheetScrollFix(lazyListState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // "Me" option at top
                item {
                    Surface(
                        onClick = { onMeToggle(!includingMe) },
                        shape = if (allPeople.isEmpty()) SingleItemShape
                        else TopItemShape,
                        color = if (includingMe) MaterialTheme.colorScheme.tertiaryContainer.copy(
                            alpha = 0.5f
                        )
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp, vertical = 16.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(
                                        24.dp
                                    ),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Column {
                                    Text(
                                        text = "Me",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "Include your share (no transaction)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Checkbox(
                                checked = includingMe, onCheckedChange = {
                                    onMeToggle(it)
                                }, colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.tertiary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }

                // People list
                itemsIndexed(allPeople, key = { _, person -> person.person.id }) { index, person ->
                    val isSelected = person.person.id in selectedPeopleIds
                    val shape = when {
                        index == allPeople.lastIndex -> BottomItemShape
                        else -> MiddleItemShape
                    }

                    Surface(
                        onClick = {
                            onPersonToggle(
                                person.person.id, !isSelected
                            )
                        },
                        shape = shape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.3f
                        )
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp, vertical = 8.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = person.person.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isSelected, onCheckedChange = {
                                    onPersonToggle(
                                        person.person.id, it
                                    )
                                })
                        }
                    }
                    if (index < allPeople.lastIndex) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            // Done button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(56.dp),
                shape = SingleItemShape
            ) { Text("Done") }
        }
    }
}
