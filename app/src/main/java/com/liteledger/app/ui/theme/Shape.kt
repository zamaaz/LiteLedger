package com.liteledger.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// --- LIST GROUPING ---
val TopItemShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
val MiddleItemShape = RoundedCornerShape(4.dp)
val BottomItemShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
val SingleItemShape = RoundedCornerShape(28.dp)

// --- SPLIT BUTTONS (HORIZONTAL BAR) ---
val SplitLeftShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp)
val SplitRightShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp)

// --- SPLIT BUTTONS (VERTICAL STACK BOTTOM) ---
// Used when buttons are at the bottom of a card/sheet
val BottomLeftSplitShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 28.dp, topEnd = 0.dp, bottomEnd = 0.dp)
val BottomRightSplitShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 28.dp)