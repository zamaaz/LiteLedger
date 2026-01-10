package com.liteledger.app.utils

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

class AppHaptic(private val view: View, private val enabled: Boolean) {

    fun click() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun success() {
        if (!enabled) return
        // CONFIRM is a satisfying double-bump (Android 11+), falls back to LongPress
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }


    fun warning() {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun heavy() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberAppHaptic(enabled: Boolean): AppHaptic {
    val view = LocalView.current
    return remember(view, enabled) { AppHaptic(view, enabled) }
}