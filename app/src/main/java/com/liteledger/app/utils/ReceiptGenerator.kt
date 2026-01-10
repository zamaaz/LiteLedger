package com.liteledger.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import com.liteledger.app.data.Transaction
import com.liteledger.app.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ReceiptGenerator {

    private val titlePaint by lazy {
        Paint().apply { color = Color.BLACK; textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    }
    private val textPaint by lazy {
        Paint().apply { color = Color.DKGRAY; textSize = 12f; isAntiAlias = true }
    }
    private val amountPaint by lazy {
        Paint().apply { color = Color.BLACK; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.RIGHT; isAntiAlias = true }
    }
    private val linePaint by lazy {
        Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
    }
    private val footerPaint by lazy {
        Paint(textPaint).apply { textSize = 10f; color = Color.LTGRAY; textAlign = Paint.Align.CENTER }
    }
    private val notePaint by lazy {
        Paint(textPaint).apply { textSize = 10f; color = Color.GRAY }
    }

    // colors
    private const val COL_GREEN = 0xFF1B5E20.toInt()
    private const val COL_RED = 0xFFB71C1C.toInt()

    // A4 Dimensions at 72 DPI (Standard PDF point size)
    // A4 is 595 x 842 points
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PADDING = 40f

    suspend fun shareReceipt(context: Context, personName: String, transactions: List<Transaction>, totalBalance: Long) {
        val uri = withContext(Dispatchers.IO) {
            val file = generatePdf(context, personName, transactions, totalBalance)
            if (file != null) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else null
        }

        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Statement: $personName")
            }
            context.startActivity(Intent.createChooser(intent, "Share Statement"))
        }
    }

    @WorkerThread
    private fun generatePdf(context: Context, name: String, list: List<Transaction>, balance: Long): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawContent(canvas, name, list, balance)

        document.finishPage(page)

        return try {
            val cachePath = File(context.cacheDir, "receipts").apply { mkdirs() }
            cachePath.listFiles()?.forEach { it.delete() }

            val file = File(cachePath, "Statement_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { stream ->
                document.writeTo(stream)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            document.close()
        }
    }

    private fun drawContent(canvas: Canvas, name: String, list: List<Transaction>, balance: Long) {
        var y = PADDING + 20f

        // 1. Header
        canvas.drawText("Statement", PADDING, y, textPaint)
        y += 25
        canvas.drawText(name, PADDING, y, titlePaint)
        y += 15
        canvas.drawLine(PADDING, y, PAGE_WIDTH - PADDING, y, linePaint)
        y += 30

        // 2. Transactions

        list.forEach { txn ->
            if (y > PAGE_HEIGHT - 100) return@forEach

            val isGave = txn.type == TransactionType.GAVE
            val prefix = if (isGave) "You Gave" else "You Got"

            // Date
            canvas.drawText(Formatters.formatSheetDate(txn.date), PADDING, y, textPaint)

            // Amount
            amountPaint.color = if (isGave) COL_RED else COL_GREEN
            canvas.drawText(Formatters.formatCurrency(txn.amount), PAGE_WIDTH - PADDING, y, amountPaint)

            y += 15
            canvas.drawText(prefix, PADDING, y, textPaint)

            // Note
            if (txn.note.isNotEmpty()) {
                val noteX = PADDING + 60f
                // Simple truncate to avoid wrapping math bloat
                val cleanNote = if (txn.note.length > 50) txn.note.take(47) + "..." else txn.note
                canvas.drawText("• $cleanNote", noteX, y, notePaint)
            }

            y += 15
            canvas.drawLine(PADDING, y, PAGE_WIDTH - PADDING, y, linePaint)
            y += 25
        }

        // 3. Total
        y += 10
        canvas.drawText("Net Balance", PADDING, y, textPaint)
        val totalLabel = when {
            balance > 0 -> "You Get"
            balance < 0 -> "You Owe"
            else -> "Settled"
        }

        canvas.drawText(totalLabel, PAGE_WIDTH - PADDING - 100, y, textPaint)

        y += 25
        val finalPaint = Paint(amountPaint).apply { textSize = 24f; color = if (balance >= 0) COL_GREEN else COL_RED }
        canvas.drawText(Formatters.formatCurrency(balance), PAGE_WIDTH - PADDING, y, finalPaint)

        // 4. Footer
        y = PAGE_HEIGHT - PADDING
        canvas.drawText("Generated by LiteLedger", PAGE_WIDTH / 2f, y, footerPaint)
    }
}