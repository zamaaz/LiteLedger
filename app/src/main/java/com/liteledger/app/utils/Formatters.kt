package com.liteledger.app.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

object Formatters {
    private val uiDateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
    private val sheetDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

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
}