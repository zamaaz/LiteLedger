package com.liteledger.app.utils

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PrivacyText(
    text: String,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    var isRevealed by remember { mutableStateOf(false) }

    // Reset reveal state if privacy mode is toggled off globally
    LaunchedEffect(isPrivacyMode) {
        if (!isPrivacyMode) isRevealed = true
    }

    val displayText = if (!isPrivacyMode || isRevealed) text else "••••••"

    AnimatedContent(
        targetState = displayText,
        label = "PrivacyFade",
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.9f))
        }
    ) { targetText ->
        Text(
            text = targetText,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // No ripple, just magic
            ) {
                if (isPrivacyMode) isRevealed = !isRevealed
            },
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}