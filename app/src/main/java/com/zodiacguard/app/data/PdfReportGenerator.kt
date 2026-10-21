package com.zodiacguard.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PdfReportGenerator(private val context: Context) {

    companion object {
        private const val TAG = "PdfReportGenerator"
        private const val PDF_FOLDER_NAME = "ZGuard"
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val PAGE_MARGIN = 40f
        private const val CONTENT_WIDTH = PAGE_WIDTH - PAGE_MARGIN * 2
        private const val CONTENT_BOTTOM = PAGE_HEIGHT - 48f
    }

    private data class PdfPage(val page: PdfDocument.Page, var contentY: Float)

    private val titlePaint = textPaint(20f, Color.rgb(34, 47, 62), bold = true)
    private val headingPaint = textPaint(12f, Color.rgb(34, 47, 62), bold = true)
    private val bodyPaint = textPaint(10f, Color.rgb(54, 65, 76))
    private val smallPaint = textPaint(9f, Color.rgb(85, 95, 105))
    private val valuePaint = textPaint(11f, Color.rgb(34, 47, 62), bold = true)
    private val goldPaint = textPaint(10f, Color.rgb(146, 105, 0), bold = true)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(222, 226, 230)
        strokeWidth = 1f
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(246, 248, 250)
    }

    fun generateMonthlyPdf(
        year: Int,
        month: Int,
        stats: MonthlyStatsHelper.MonthlyStats
    ): Uri? {
        if (stats.dailyStats.isEmpty()) return null

        val document = PdfDocument()
        try {
            var pageNumber = 1
            var page = startPage(document, pageNumber)

            drawSummary(page.page.canvas, page, year, month, stats)
            page.contentY += 20f
            page.contentY = drawSectionHeading(
                page.page.canvas,
                page.contentY,
                "AGENDA GIORNALIERA"
            )

            for ((index, day) in stats.dailyStats.withIndex()) {
                val activityLines = wrapText(
                    "${day.activity} · ${day.location}",
                    CONTENT_WIDTH - 20f,
                    bodyPaint
                )
                val detailsLines = wrapText(formatDayDetails(day), CONTENT_WIDTH - 20f, smallPaint)
                var remainingMarkLines = wrapText(
                    formatMarkings(day.entries),
                    CONTENT_WIDTH - 20f,
                    smallPaint
                )
                var firstPart = true

                while (firstPart || remainingMarkLines.isNotEmpty()) {
                    val firstActivityLines = if (firstPart) activityLines else emptyList()
                    val firstDetailsLines = if (firstPart) detailsLines else emptyList()
                    val fixedLines = firstActivityLines.size + firstDetailsLines.size
                    val availableMarkLines =
                        ((CONTENT_BOTTOM - page.contentY - 25f) / 12f).toInt() - fixedLines

                    if (availableMarkLines < 1 && page.contentY > 100f) {
                        finishPage(document, page, pageNumber)
                        pageNumber++
                        page = startPage(document, pageNumber)
                        page.contentY = drawSectionHeading(
                            page.page.canvas,
                            page.contentY,
                            "AGENDA GIORNALIERA · CONTINUA"
                        )
                        continue
                    }

                    val markLineCount = minOf(
                        remainingMarkLines.size,
                        maxOf(1, availableMarkLines)
                    )
                    val cardMarkLines = remainingMarkLines.take(markLineCount)
                    remainingMarkLines = remainingMarkLines.drop(markLineCount)
                    val cardHeight = 25f + 12f *
                        (fixedLines + cardMarkLines.size)

                    drawDayCard(
                        page.page.canvas,
                        page.contentY,
                        cardHeight,
                        index,
                        day,
                        firstPart,
                        firstActivityLines,
                        firstDetailsLines,
                        cardMarkLines
                    )
                    page.contentY += cardHeight + 7f
                    firstPart = false

                    if (remainingMarkLines.isNotEmpty()) {
                        finishPage(document, page, pageNumber)
                        pageNumber++
                        page = startPage(document, pageNumber)
                        page.contentY = drawSectionHeading(
                            page.page.canvas,
                            page.contentY,
                            "AGENDA GIORNALIERA · CONTINUA"
                        )
                    }
                }
            }

            finishPage(document, page, pageNumber)
            return savePdf(year, month, document)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Errore generazione PDF: ${e.message}", e)
            throw e
        } finally {
            document.close()
        }
    }

    private fun drawSummary(
        canvas: Canvas,
        page: PdfPage,
        year: Int,
        month: Int,
        stats: MonthlyStatsHelper.MonthlyStats
    ) {
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN)
            .format(monthDate(year, month))
            .replaceFirstChar { it.uppercase(Locale.ITALIAN) }

        canvas.drawText("RIEPILOGO MENSILE", PAGE_MARGIN, page.contentY, titlePaint)
        page.contentY += 23f
        canvas.drawText(monthLabel, PAGE_MARGIN, page.contentY, goldPaint)
        page.contentY += 15f

        drawSectionHeading(canvas, page.contentY, "RIEPILOGO ORE")
        page.contentY += 27f

        val columnGap = 22f
        val columnWidth = (CONTENT_WIDTH - columnGap) / 2f
        val rightColumnX = PAGE_MARGIN + columnWidth + columnGap
        val summaryRows = listOf(
            "Totale lavorate" to MonthlyStatsHelper.minutesToText(stats.totalWorkedMinutes),
            "Contratto attese (${stats.workingDaysInMonth} gg)" to
                MonthlyStatsHelper.minutesToText(stats.totalContractMinutes),
            "Ordinarie" to MonthlyStatsHelper.minutesToText(stats.totalOrdinaryMinutes),
            "Straordinari" to MonthlyStatsHelper.minutesToText(stats.totalOvertimeMinutes),
            "Banca ore" to MonthlyStatsHelper.minutesToText(stats.totalBankMinutes),
            "Viaggio" to MonthlyStatsHelper.minutesToText(stats.totalTravelMinutes),
            "Saldo" to signedMinutes(stats.balanceMinutes),
            "Buoni pasto" to stats.mealTickets.toString(),
            "Giornate fuori sede" to stats.outOfTownDays.toString()
        )

        var leftY = page.contentY
        var rightY = page.contentY
        for ((index, row) in summaryRows.withIndex()) {
            val columnX = if (index % 2 == 0) PAGE_MARGIN else rightColumnX
            val rowY = if (index % 2 == 0) leftY else rightY
            canvas.drawText(row.first, columnX, rowY, bodyPaint)
            canvas.drawText(row.second, columnX + columnWidth - valuePaint.measureText(row.second), rowY, valuePaint)
            if (index % 2 == 0) leftY += 21f else rightY += 21f
        }
        page.contentY = maxOf(leftY, rightY) + 5f

        drawSectionHeading(canvas, page.contentY, "ATTIVITÀ E SEDI")
        page.contentY += 27f
        val activities = stats.teatriniCount.entries
            .sortedBy { it.key }
            .joinToString("   ·   ") { "${it.key}: ${it.value}" }
            .ifEmpty { "Nessun teatrino" }
        val teatrinoDays = stats.teatriniDays
            .joinToString("   ·   ") { formatShortDate(it) }
            .ifEmpty { "Nessun teatrino" }
        val locations = stats.locationsCount.entries
            .sortedBy { it.key }
            .joinToString("   ·   ") { "${it.key}: ${it.value} gg" }
            .ifEmpty { "Nessuna presenza" }

        page.contentY = drawWrappedLabel(canvas, "Teatrini", activities, page.contentY)
        page.contentY = drawWrappedLabel(canvas, "Giorni", teatrinoDays, page.contentY)
        page.contentY = drawWrappedLabel(canvas, "Presenze", locations, page.contentY)
    }

    private fun drawWrappedLabel(
        canvas: Canvas,
        label: String,
        value: String,
        y: Float
    ): Float {
        canvas.drawText("$label:", PAGE_MARGIN, y, headingPaint)
        val lines = wrapText(value, CONTENT_WIDTH - 74f, bodyPaint)
        for ((index, line) in lines.withIndex()) {
            canvas.drawText(line, PAGE_MARGIN + 74f, y + index * 13f, bodyPaint)
        }
        return y + maxOf(18f, lines.size * 13f) + 3f
    }

    private fun drawSectionHeading(canvas: Canvas, y: Float, text: String): Float {
        canvas.drawText(text, PAGE_MARGIN, y, headingPaint)
        canvas.drawLine(PAGE_MARGIN, y + 5f, PAGE_WIDTH - PAGE_MARGIN, y + 5f, linePaint)
        return y + 17f
    }

    private fun drawDayCard(
        canvas: Canvas,
        y: Float,
        height: Float,
        index: Int,
        day: MonthlyStatsHelper.DailySummary,
        firstPart: Boolean,
        activityLines: List<String>,
        detailsLines: List<String>,
        markings: List<String>
    ) {
        val rect = RectF(PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, y + height)
        if (index % 2 == 0) canvas.drawRoundRect(rect, 5f, 5f, cardPaint)

        val left = PAGE_MARGIN + 10f
        var lineY = y + 15f
        if (firstPart) {
            val date = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN)
                .format(parseDate(day.date))
                .replaceFirstChar { it.uppercase(Locale.ITALIAN) }
            canvas.drawText(date, left, lineY, headingPaint)
        } else {
            canvas.drawText(
                "${SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(parseDate(day.date))} · Marcature (continua)",
                left,
                lineY,
                headingPaint
            )
        }

        lineY += 14f
        for (line in activityLines) {
            canvas.drawText(line, left, lineY, bodyPaint)
            lineY += 12f
        }

        for (line in detailsLines) {
            canvas.drawText(line, left, lineY, smallPaint)
            lineY += 12f
        }

        for (line in markings) {
            canvas.drawText(line, left, lineY, smallPaint)
            lineY += 12f
        }
    }

    private fun formatDayDetails(day: MonthlyStatsHelper.DailySummary): String =
        buildList {
            add("Totale: ${MonthlyStatsHelper.minutesToText(day.totalMinutes)}")
            add("Viaggio: ${MonthlyStatsHelper.minutesToText(day.travelMinutes)}")
            add("Produzione: ${if (day.isProduction) "sì" else "no"}")
            add("Buoni pasto: ${day.mealTickets}")
        }.joinToString("   ·   ")

    private fun formatMarkings(entries: List<WorkEntry>): String {
        if (entries.isEmpty()) return "Marcature: nessuna"

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return entries.sortedBy { it.timestamp }.joinToString("   ·   ", "Marcature: ") { entry ->
            "${timeFormat.format(Date(entry.timestamp))} ${entry.type}"
        }
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")

        val result = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(Regex("\\s+"))) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                result.add(line.toString())
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) result.add(line.toString())
        return result
    }

    private fun startPage(document: PdfDocument, pageNumber: Int): PdfPage {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = document.startPage(info)
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawText("Z GUARD", PAGE_MARGIN, 30f, headingPaint)
        page.canvas.drawLine(
            PAGE_MARGIN,
            39f,
            PAGE_WIDTH - PAGE_MARGIN,
            39f,
            linePaint
        )
        return PdfPage(page, 63f)
    }

    private fun finishPage(
        document: PdfDocument,
        page: PdfPage,
        pageNumber: Int
    ) {
        page.page.canvas.drawLine(
            PAGE_MARGIN,
            PAGE_HEIGHT - 35f,
            PAGE_WIDTH - PAGE_MARGIN,
            PAGE_HEIGHT - 35f,
            linePaint
        )
        val footer = "Pagina $pageNumber"
        page.page.canvas.drawText(
            footer,
            PAGE_WIDTH - PAGE_MARGIN - smallPaint.measureText(footer),
            PAGE_HEIGHT - 20f,
            smallPaint
        )
        document.finishPage(page.page)
    }

    private fun savePdf(year: Int, month: Int, document: PdfDocument): Uri {
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        val fileName = "Riepilogo_${getMonthName(year, month)}.pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savePdfToDownloads(fileName, output.toByteArray())
        } else {
            savePdfToAppStorage(fileName, output.toByteArray())
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdfToDownloads(fileName: String, pdfBytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$PDF_FOLDER_NAME"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Impossibile creare il PDF in Download")

        try {
            resolver.openOutputStream(uri)?.use { it.write(pdfBytes) }
                ?: throw IOException("Impossibile scrivere il PDF")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        return uri
    }

    private fun savePdfToAppStorage(fileName: String, pdfBytes: ByteArray): Uri {
        val downloadDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("Archivio esterno dell'app non disponibile")
        val pdfDirectory = File(downloadDirectory, PDF_FOLDER_NAME)
        if (!pdfDirectory.exists() && !pdfDirectory.mkdirs()) {
            throw IOException("Impossibile creare la cartella del PDF")
        }

        val pdfFile = File(pdfDirectory, fileName)
        pdfFile.writeBytes(pdfBytes)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    private fun signedMinutes(minutes: Int): String {
        val sign = if (minutes >= 0) "+" else "-"
        val absolute = kotlin.math.abs(minutes)
        return "$sign${MonthlyStatsHelper.minutesToText(absolute)}"
    }

    private fun parseDate(date: String): Date =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date)
            ?: throw IllegalArgumentException("Data non valida: $date")

    private fun formatShortDate(date: String): String =
        SimpleDateFormat("dd/MM", Locale.ITALIAN).format(parseDate(date))

    private fun monthDate(year: Int, month: Int): Date =
        Calendar.getInstance().apply { set(year, month - 1, 1) }.time

    private fun getMonthName(year: Int, month: Int): String =
        SimpleDateFormat("MMMM_yyyy", Locale.ITALIAN)
            .format(monthDate(year, month))
            .replaceFirstChar { it.uppercase(Locale.ITALIAN) }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
        }
}
