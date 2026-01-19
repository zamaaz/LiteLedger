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
import androidx.core.content.res.ResourcesCompat
import com.liteledger.app.R
import com.liteledger.app.data.Transaction
import com.liteledger.app.data.TransactionType
import com.liteledger.app.ui.SettlementStatus
import com.liteledger.app.ui.SmartStatementData
import com.liteledger.app.ui.SmartStatementMode
import com.liteledger.app.ui.TransactionUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ReceiptGenerator {

    // Colors
    private const val COL_GREEN = 0xFF1B5E20.toInt()
    private const val COL_RED = 0xFFB71C1C.toInt()

    // A4 Dimensions at 72 DPI (595 x 842 points)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PADDING = 40f

    // Font cache - loaded once per context
    @Volatile
    private var cachedTypeface: Typeface? = null

    private fun getAppFont(context: Context): Typeface {
        return cachedTypeface ?: synchronized(this) {
            cachedTypeface ?: ResourcesCompat.getFont(context, R.font.app_font)?.also {
                cachedTypeface = it
            } ?: Typeface.DEFAULT
        }
    }

    // Paint factories using app font
    private fun createTitlePaint(typeface: Typeface) = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        this.typeface = Typeface.create(typeface, Typeface.BOLD)
        isAntiAlias = true
    }

    private fun createTextPaint(typeface: Typeface) = Paint().apply {
        color = Color.DKGRAY
        textSize = 12f
        this.typeface = typeface
        isAntiAlias = true
    }

    private fun createAmountPaint(typeface: Typeface) = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        this.typeface = Typeface.create(typeface, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    private val linePaint by lazy {
        Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
    }

    private fun createFooterPaint(typeface: Typeface) = Paint().apply {
        color = Color.LTGRAY
        textSize = 10f
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private fun createNotePaint(typeface: Typeface) = Paint().apply {
        color = Color.GRAY
        textSize = 10f
        this.typeface = typeface
        isAntiAlias = true
    }

    /**
     * Share the receipt PDF via share sheet.
     * Does NOT save to Downloads - only shares temporarily.
     */
    suspend fun shareReceipt(context: Context, personName: String, transactions: List<Transaction>, totalBalance: Long) {
        val uri = withContext(Dispatchers.IO) {
            val cacheFile = generatePdf(context, personName, transactions, totalBalance)
            if (cacheFile != null) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            } else null
        }

        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Statement: $personName")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Statement"))
        }
    }

    /**
     * Generate PDF and return the cache file for explicit saving.
     * Used with ActivityResultLauncher for ACTION_CREATE_DOCUMENT.
     */
    suspend fun generatePdfFile(context: Context, personName: String, transactions: List<Transaction>, totalBalance: Long): File? {
        return withContext(Dispatchers.IO) {
            generatePdf(context, personName, transactions, totalBalance)
        }
    }

    /**
     * Write PDF content to a destination URI (from ACTION_CREATE_DOCUMENT result).
     */
    suspend fun writePdfToUri(context: Context, sourceFile: File, destinationUri: android.net.Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // ========== SMART STATEMENT METHODS ==========

    /**
     * Share smart statement PDF via share sheet.
     * Uses filtered transactions based on settlement status.
     */
    suspend fun shareSmartStatement(context: Context, personName: String, data: SmartStatementData) {
        val uri = withContext(Dispatchers.IO) {
            val cacheFile = generateSmartPdf(context, personName, data)
            if (cacheFile != null) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            } else null
        }

        if (uri != null) {
            val subject = if (data.mode == SmartStatementMode.SETTLED) {
                "Statement: $personName (All Settled)"
            } else {
                "Statement: $personName"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Statement"))
        }
    }

    /**
     * Generate smart statement PDF file for explicit saving.
     */
    suspend fun generateSmartPdfFile(context: Context, personName: String, data: SmartStatementData): File? {
        return withContext(Dispatchers.IO) {
            generateSmartPdf(context, personName, data)
        }
    }

    @WorkerThread
    private fun generateSmartPdf(context: Context, name: String, data: SmartStatementData): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val appFont = getAppFont(context)
        drawSmartContent(canvas, name, data, appFont)

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

    private fun drawSmartContent(canvas: Canvas, name: String, data: SmartStatementData, typeface: Typeface) {
        val titlePaint = createTitlePaint(typeface)
        val textPaint = createTextPaint(typeface)
        val amountPaint = createAmountPaint(typeface)
        val footerPaint = createFooterPaint(typeface)
        val notePaint = createNotePaint(typeface)

        // Chip paint for settlement status badges
        val chipTextPaint = Paint(textPaint).apply { 
            textSize = 8f
            color = Color.WHITE
        }
        val chipBgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        var y = PADDING + 20f

        // 1. Header
        canvas.drawText("Statement", PADDING, y, textPaint)
        y += 25
        canvas.drawText(name, PADDING, y, titlePaint)
        y += 15

        // 2. Settled badge (only for fully settled accounts)
        if (data.mode == SmartStatementMode.SETTLED) {
            y += 5
            val settledPaint = Paint(textPaint).apply { 
                color = COL_GREEN
                textSize = 12f 
            }
            canvas.drawText("All Due Cleared", PADDING, y, settledPaint)
            y += 10
        }

        canvas.drawLine(PADDING, y, PAGE_WIDTH - PADDING, y, linePaint)
        y += 30

        // 3. Transactions
        data.transactions.forEach { uiModel ->
            if (y > PAGE_HEIGHT - 100) return@forEach

            val txn = uiModel.transaction
            val isGave = txn.type == TransactionType.GAVE
            val prefix = if (isGave) "You Gave" else "You Got"

            // Date
            val dateText = Formatters.formatSheetDate(txn.date)
            canvas.drawText(dateText, PADDING, y, textPaint)

            // Settlement status chip (subtle, only for SETTLED or PARTIAL)
            if (uiModel.settlementStatus == SettlementStatus.SETTLED || 
                uiModel.settlementStatus == SettlementStatus.PARTIAL) {
                val chipText = if (uiModel.settlementStatus == SettlementStatus.SETTLED) "Settled" else "Partial"
                val chipColor = if (uiModel.settlementStatus == SettlementStatus.SETTLED) 
                    Color.parseColor("#9E9E9E") else Color.parseColor("#FF9800")  // Gray or Orange
                
                val dateWidth = textPaint.measureText(dateText)
                val chipX = PADDING + dateWidth + 8f
                val chipTextWidth = chipTextPaint.measureText(chipText)
                val chipPadding = 4f
                val chipHeight = 12f
                
                chipBgPaint.color = chipColor
                canvas.drawRoundRect(
                    chipX, y - chipHeight + 2f,
                    chipX + chipTextWidth + chipPadding * 2, y + 2f,
                    4f, 4f, chipBgPaint
                )
                canvas.drawText(chipText, chipX + chipPadding, y - 1f, chipTextPaint)
            }

            // Amount
            amountPaint.color = if (isGave) COL_RED else COL_GREEN
            canvas.drawText(Formatters.formatCurrency(txn.amount), PAGE_WIDTH - PADDING, y, amountPaint)

            y += 15
            canvas.drawText(prefix, PADDING, y, textPaint)

            // Note
            if (txn.note.isNotEmpty()) {
                val noteX = PADDING + 60f
                val cleanNote = if (txn.note.length > 50) txn.note.take(47) + "..." else txn.note
                canvas.drawText("• $cleanNote", noteX, y, notePaint)
            }

            y += 15
            canvas.drawLine(PADDING, y, PAGE_WIDTH - PADDING, y, linePaint)
            y += 25
        }

        // 4. Total
        y += 10
        canvas.drawText("Net Balance", PADDING, y, textPaint)
        val totalLabel = when {
            data.totalBalance > 0 -> "You Get"
            data.totalBalance < 0 -> "You Owe"
            else -> "Settled"
        }

        canvas.drawText(totalLabel, PAGE_WIDTH - PADDING - 100, y, textPaint)

        y += 25
        val finalPaint = Paint(amountPaint).apply { 
            textSize = 24f
            color = if (data.totalBalance >= 0) COL_GREEN else COL_RED 
        }
        canvas.drawText(Formatters.formatCurrency(data.totalBalance), PAGE_WIDTH - PADDING, y, finalPaint)

        // 5. Footer
        y = PAGE_HEIGHT.toFloat() - PADDING
        canvas.drawText("Generated by LiteLedger", PAGE_WIDTH / 2f, y, footerPaint)
    }

    @WorkerThread
    private fun generatePdf(context: Context, name: String, list: List<Transaction>, balance: Long): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Get app font
        val appFont = getAppFont(context)

        drawContent(canvas, name, list, balance, appFont)

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

    private fun drawContent(canvas: Canvas, name: String, list: List<Transaction>, balance: Long, typeface: Typeface) {
        // Create paints with app font
        val titlePaint = createTitlePaint(typeface)
        val textPaint = createTextPaint(typeface)
        val amountPaint = createAmountPaint(typeface)
        val footerPaint = createFooterPaint(typeface)
        val notePaint = createNotePaint(typeface)

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
        y = PAGE_HEIGHT.toFloat() - PADDING
        canvas.drawText("Generated by LiteLedger", PAGE_WIDTH / 2f, y, footerPaint)
    }
}