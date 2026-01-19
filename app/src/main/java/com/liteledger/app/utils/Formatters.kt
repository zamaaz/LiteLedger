package com.liteledger.app.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.concurrent.TimeUnit

object Formatters {
    private val uiDateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
    private val sheetDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val absoluteDateFormat = SimpleDateFormat("dd MMM", Locale.US)

    private val currencyFormat by lazy { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    fun formatCurrency(amount: Long): String {
        return currencyFormat.format(amount / 100.0)
    }

    fun formatUiDate(dateMillis: Long): String {
        return uiDateFormat.format(Date(dateMillis))
    }

    fun formatSheetDate(dateMillis: Long): String {
        return sheetDateFormat.format(Date(dateMillis))
    }

    private val timeFormat by lazy { SimpleDateFormat("hh:mm a", Locale.US) }

    fun formatTime(dateMillis: Long): String {
        return timeFormat.format(Date(dateMillis))
    }

    fun formatRelativeTime(dateMillis: Long): String {
        val now = java.time.LocalDate.now()
        val then = java.time.Instant.ofEpochMilli(dateMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()

        val days = java.time.temporal.ChronoUnit.DAYS.between(then, now)

        return when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days in 2..7 -> "$days days ago"
            else -> absoluteDateFormat.format(Date(dateMillis))
        }
    }

    fun formatDueDate(dateMillis: Long): String {
        val now = java.time.LocalDate.now()
        val dueDate = java.time.Instant.ofEpochMilli(dateMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()

        val days = java.time.temporal.ChronoUnit.DAYS.between(now, dueDate)

        return when {
            days < 0 -> "Overdue ${absoluteDateFormat.format(Date(dateMillis))}"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            else -> "Due ${absoluteDateFormat.format(Date(dateMillis))}"
        }
    }
}
