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
        private const val PAGE_MARGIN = 20f
        private const val CONTENT_WIDTH = PAGE_WIDTH - PAGE_MARGIN * 2
        private const val TABLE_TOP = 171f
        private const val ROW_HEIGHT = 19f
        private const val FOOTER_TOP = 798f

        private val COLUMN_WIDTHS = floatArrayOf(45f, 139f, 68f, 56f, 102f, 145f)
        private val COLUMN_HEADERS = arrayOf(
            "GIORNO", "MARCATURE", "ICONE", "SEDE", "ATTIVITA", "TOTALE / STR"
        )
    }

    private val titlePaint = textPaint(15f, Color.rgb(34, 47, 62), bold = true)
    private val monthPaint = textPaint(11f, Color.rgb(146, 105, 0), bold = true)
    private val headingPaint = textPaint(8f, Color.rgb(34, 47, 62), bold = true)
    private val bodyPaint = textPaint(7f, Color.rgb(54, 65, 76))
    private val smallPaint = textPaint(6.3f, Color.rgb(85, 95, 105))
    private val metricPaint = textPaint(7.3f, Color.rgb(34, 47, 62))
    private val tableHeaderPaint = textPaint(6.2f, Color.WHITE, bold = true)
    private val rowDatePaint = textPaint(6.7f, Color.rgb(34, 47, 62), bold = true)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 225, 230)
        strokeWidth = 0.5f
    }
    private val alternateRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(247, 248, 250)
    }
    private val headerBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(44, 62, 80)
    }

    fun generateMonthlyPdf(
        year: Int,
        month: Int,
        stats: MonthlyStatsHelper.MonthlyStats
    ): Uri {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            drawTitle(canvas, year, month)
            drawSummary(canvas, stats)
            drawAgenda(canvas, year, month, stats)
            drawFooter(canvas)
            document.finishPage(page)

            return savePdf(year, month, document)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Errore generazione PDF: ${e.message}", e)
            throw e
        } finally {
            document.close()
        }
    }

    private fun drawTitle(canvas: Canvas, year: Int, month: Int) {
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN)
            .format(monthDate(year, month))
            .replaceFirstChar { it.uppercase(Locale.ITALIAN) }

        canvas.drawText("RIEPILOGO MENSILE", PAGE_MARGIN, 31f, titlePaint)
        canvas.drawText("· $monthLabel", PAGE_MARGIN, 45f, monthPaint)
        canvas.drawLine(PAGE_MARGIN, 51f, PAGE_WIDTH - PAGE_MARGIN, 51f, linePaint)
    }

    private fun drawSummary(canvas: Canvas, stats: MonthlyStatsHelper.MonthlyStats) {
        canvas.drawText("RIEPILOGO ORE", PAGE_MARGIN, 64f, headingPaint)

        val metrics = listOf(
            "Lavorate ${MonthlyStatsHelper.minutesToText(stats.totalWorkedMinutes)}",
            "Contratto attese (${stats.workingDaysInMonth} gg) ${MonthlyStatsHelper.minutesToText(stats.totalContractMinutes)}",
            "Ordinarie ${MonthlyStatsHelper.minutesToText(stats.totalOrdinaryMinutes)}",
            "Straordinari ${MonthlyStatsHelper.minutesToText(stats.totalOvertimeMinutes)}",
            "Banca ore ${MonthlyStatsHelper.minutesToText(stats.totalBankMinutes)}",
            "Viaggio ${MonthlyStatsHelper.minutesToText(stats.totalTravelMinutes)}",
            "Saldo ${signedMinutes(stats.balanceMinutes)}",
            "Buoni pasto ${stats.mealTickets}",
            "Giornate fuori sede ${stats.outOfTownDays}"
        )
        val summaryColumnWidth = CONTENT_WIDTH / 3f
        for ((index, metric) in metrics.withIndex()) {
            val row = index / 3
            val column = index % 3
            val x = PAGE_MARGIN + summaryColumnWidth * column
            val baseline = 77f + row * 12f
            drawFittedText(canvas, metric, x, baseline, summaryColumnWidth - 7f, metricPaint)
        }

        val teatrini = stats.teatriniCount.entries
            .sortedBy { it.key }
            .joinToString(" · ") { "${it.key}: ${it.value}" }
            .ifEmpty { "Nessun teatrino" }
        drawFittedText(canvas, "Teatrini: $teatrini", PAGE_MARGIN, 117f, CONTENT_WIDTH, smallPaint)

        val locations = stats.locationsCount.entries
            .sortedBy { it.key }
            .joinToString(" · ") { "${it.key}: ${it.value} gg" }
            .ifEmpty { "Nessuna presenza" }
        drawFittedText(canvas, "Sedi: $locations", PAGE_MARGIN, 127f, CONTENT_WIDTH, smallPaint)

        val teatrinoDays = stats.teatriniDays
            .joinToString(" · ") { formatShortDate(it) }
            .ifEmpty { "Nessuno" }
        val dayLines = wrapText("Giorni teatrino: $teatrinoDays", CONTENT_WIDTH, smallPaint)
        for ((index, line) in dayLines.withIndex()) {
            canvas.drawText(line, PAGE_MARGIN, 137f + index * 8f, smallPaint)
        }
    }

    private fun drawAgenda(
        canvas: Canvas,
        year: Int,
        month: Int,
        stats: MonthlyStatsHelper.MonthlyStats
    ) {
        val monthDates = MonthlyStatsHelper.getDatesInMonth(year, month)
        val daysByDate = stats.dailyStats.associateBy { it.date }
        val dayLines = wrapText(
            "Giorni teatrino: " +
                stats.teatriniDays.joinToString(" · ") { formatShortDate(it) }
                    .ifEmpty { "Nessuno" },
            CONTENT_WIDTH,
            smallPaint
        )
        val tableTop = maxOf(TABLE_TOP, 141f + dayLines.size * 8f + 5f)
        drawTableHeader(canvas, tableTop)

        for ((index, date) in monthDates.withIndex()) {
            val day = daysByDate[date]
            val rowTop = tableTop + ROW_HEIGHT * (index + 1)
            val baseline = rowTop + 12f

            if (index % 2 == 0) {
                canvas.drawRect(
                    PAGE_MARGIN,
                    rowTop,
                    PAGE_WIDTH - PAGE_MARGIN,
                    rowTop + ROW_HEIGHT,
                    alternateRowPaint
                )
            }

            canvas.drawText(formatWeekday(date), PAGE_MARGIN + 3f, baseline, rowDatePaint)
            if (day != null) {
                drawDay(canvas, baseline, day)
            }

            canvas.drawLine(
                PAGE_MARGIN,
                rowTop + ROW_HEIGHT,
                PAGE_WIDTH - PAGE_MARGIN,
                rowTop + ROW_HEIGHT,
                linePaint
            )
        }
    }

    private fun drawTableHeader(canvas: Canvas, top: Float) {
        canvas.drawRect(
            PAGE_MARGIN,
            top,
            PAGE_WIDTH - PAGE_MARGIN,
            top + ROW_HEIGHT,
            headerBackgroundPaint
        )
        var x = PAGE_MARGIN
        for (index in COLUMN_HEADERS.indices) {
            canvas.drawText(COLUMN_HEADERS[index], x + 3f, top + 12f, tableHeaderPaint)
            x += COLUMN_WIDTHS[index]
        }
    }

    private fun drawDay(canvas: Canvas, baseline: Float, day: MonthlyStatsHelper.DailySummary) {
        val starts = columnStarts()
        val markings = day.entries.sortedBy { it.timestamp }.joinToString(" · ") { entry ->
            val kind = if (entry.type == "ENTRATA") "E" else "U"
            "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestamp))} $kind"
        }
        drawFittedText(canvas, markings, starts[1] + 3f, baseline, COLUMN_WIDTHS[1] - 6f, smallPaint)

        val icons = buildList {
            if (day.isProduction) add("P")
            if (day.hasMealTicket) add("BP")
            if (day.isTeatrino) add("T")
            if (day.location != "Modena") add("KM")
        }.joinToString(" · ")
        drawFittedText(canvas, icons, starts[2] + 3f, baseline, COLUMN_WIDTHS[2] - 6f, smallPaint)
        drawFittedText(
            canvas,
            day.location,
            starts[3] + 3f,
            baseline,
            COLUMN_WIDTHS[3] - 6f,
            smallPaint
        )
        drawFittedText(
            canvas,
            day.activity,
            starts[4] + 3f,
            baseline,
            COLUMN_WIDTHS[4] - 6f,
            smallPaint
        )

        val timeX = starts[5] + 3f
        val timeWidth = COLUMN_WIDTHS[5] - 6f
        drawFittedText(
            canvas,
            MonthlyStatsHelper.minutesToText(day.totalMinutes),
            timeX,
            baseline - if (day.overtimeMinutes > 0) 3f else 0f,
            timeWidth,
            smallPaint
        )
        if (day.overtimeMinutes > 0) {
            drawFittedText(
                canvas,
                "STR ${MonthlyStatsHelper.minutesToText(day.overtimeMinutes)}",
                timeX,
                baseline + 5f,
                timeWidth,
                smallPaint
            )
        }
    }

    private fun drawFooter(canvas: Canvas) {
        canvas.drawLine(PAGE_MARGIN, FOOTER_TOP, PAGE_WIDTH - PAGE_MARGIN, FOOTER_TOP, linePaint)
        val note =
            "Le marcature possono differire di qualche minuto da quelle originali e sono riportate solo come riferimento."
        drawFittedText(canvas, note, PAGE_MARGIN, FOOTER_TOP + 13f, CONTENT_WIDTH, smallPaint)
        drawFittedText(
            canvas,
            "P = produzione · BP = buono pasto · T = teatrino · KM = rimborso chilometrico",
            PAGE_MARGIN,
            FOOTER_TOP + 23f,
            CONTENT_WIDTH,
            smallPaint
        )
    }

    private fun columnStarts(): FloatArray {
        val starts = FloatArray(COLUMN_WIDTHS.size)
        var x = PAGE_MARGIN
        for (index in COLUMN_WIDTHS.indices) {
            starts[index] = x
            x += COLUMN_WIDTHS[index]
        }
        return starts
    }

    private fun formatWeekday(date: String): String {
        val formatted = SimpleDateFormat("EEE dd", Locale.ITALIAN).format(parseDate(date))
        return formatted.replaceFirstChar { it.lowercase(Locale.ITALIAN) }
    }

    private fun formatShortDate(date: String): String =
        SimpleDateFormat("dd/MM", Locale.ITALIAN).format(parseDate(date))

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in text.split(Regex("\\s+"))) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (currentLine.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(candidate)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        if (text.isEmpty()) return
        val measuredWidth = paint.measureText(text)
        if (measuredWidth <= maxWidth || measuredWidth == 0f) {
            canvas.drawText(text, x, baseline, paint)
            return
        }

        val scale = maxWidth / measuredWidth
        canvas.save()
        canvas.scale(scale, 1f, x, baseline)
        canvas.drawText(text, x, baseline, paint)
        canvas.restore()
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
        return "$sign${MonthlyStatsHelper.minutesToText(kotlin.math.abs(minutes))}"
    }

    private fun parseDate(date: String): Date =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date)
            ?: throw IllegalArgumentException("Data non valida: $date")

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
