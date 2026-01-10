package com.liteledger.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.liteledger.app.data.PersonWithBalance
import com.liteledger.app.data.Transaction
import com.liteledger.app.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

object CsvExporter {
    suspend fun exportData(context: Context, people: List<PersonWithBalance>, transactions: List<Transaction>) {
        // 1. Generate File on Background Thread
        val file = withContext(Dispatchers.IO) {
            val exportFile = File(context.cacheDir, "LiteLedger_Export_${System.currentTimeMillis()}.csv")

            FileWriter(exportFile).use { writer ->
                // Summary
                writer.append("Name,Current Balance,Mobile\n")
                people.forEach { p ->
                    writer.append("${escape(p.person.name)},${p.balance / 100.0},${p.person.mobile ?: ""}\n")
                }

                writer.append("\n\n")

                // Transactions
                writer.append("Transaction ID,Person ID,Type,Amount,Date,Note\n")
                transactions.forEach { t ->
                    val typeStr = if (t.type == TransactionType.GAVE) "GAVE" else "GOT"
                    val dateStr = Formatters.formatSheetDate(t.date) + " " + Formatters.formatTime(t.date)
                    writer.append("${t.id},${t.personId},${typeStr},${t.amount / 100.0},${escape(dateStr)},${escape(t.note)}\n")
                }
            }
            exportFile // Return the file to the main scope
        }

        // 2. Share Intent on Main Thread (Crash Fixed)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Data"))
    }

    private fun escape(data: String): String {
        var escaped = data.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\n")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }
}