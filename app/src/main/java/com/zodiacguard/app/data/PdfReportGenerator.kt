package com.zodiacguard.app.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PdfReportGenerator(private val context: Context) {

    private val workDataStore = WorkDataStore(context)
    private val contractDataStore = ContractDataStore(context)

    companion object {
        private const val TAG = "PdfReportGenerator"
        private const val PDF_FOLDER_NAME = "ZGuard"
    }

    suspend fun generateMonthlyPdf(year: Int, month: Int): File? {
        return try {
            val entries = workDataStore.entriesFlow.first()
            val productionDays = workDataStore.productionDaysFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()

            // Filtra marcature del mese
            val monthEntries = entries.filter { entry ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = entry.timestamp
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
            }.sortedBy { it.timestamp }

            if (monthEntries.isEmpty()) return null

            // Calcola totali
            val totalHours = calculateTotalHours(monthEntries)
            val expectedHours = workDays * weeklyHours
            val ordinaryHours = minOf(totalHours, expectedHours)
            val overtimeHours = maxOf(0f, totalHours - expectedHours)
            val bankHours = totalHours - expectedHours
            val travelMinutes = calculateTravelMinutes(monthEntries)
            val mealBadges = calculateMealBadges(monthEntries)

            // Conta attività
            val teatrinoMontaggio = monthEntries.count { it.activity == "Teatrino Montaggio" }
            val teatrinoSmontaggio = monthEntries.count { it.activity == "Teatrino Smontaggio" }
            val teatrinoIntero = monthEntries.count { it.activity == "Teatrino Intero" }
            val servizio = monthEntries.count { it.activity == "Servizio" }

            // Presenza per sede
            val presenceByLocation = monthEntries.groupBy { it.location }
                .mapValues { it.value.size }

            // Raggruppa per giorno
            val groupedByDay = monthEntries.groupBy { it.date }

            // Crea PDF
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 in points
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            drawPdf(canvas, year, month, totalHours, expectedHours, ordinaryHours,
                overtimeHours, bankHours, travelMinutes, mealBadges,
                teatrinoMontaggio, teatrinoSmontaggio, teatrinoIntero, servizio,
                presenceByLocation, groupedByDay, productionDays)

            pdfDocument.finishPage(page)

            // Salva file
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfDir = File(downloadDir, PDF_FOLDER_NAME)
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val monthName = SimpleDateFormat("MMMM_yyyy", Locale.ITALIAN).format(
                Calendar.getInstance().apply { set(year, month, 1) }.time
            )
            val pdfFile = File(pdfDir, "Riepilogo_$monthName.pdf")
            pdfDocument.writeTo(pdfFile.outputStream())
            pdfDocument.close()

            pdfFile
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Errore generazione PDF: ${e.message}", e)
            null
        }
    }

    private fun drawPdf(
        canvas: Canvas,
        year: Int, month: Int,
        totalHours: Float, expectedHours: Float, ordinaryHours: Float,
        overtimeHours: Float, bankHours: Float, travelMinutes: Int, mealBadges: Int,
        teatrinoMontaggio: Int, teatrinoSmontaggio: Int, teatrinoIntero: Int, servizio: Int,
        presenceByLocation: Map<String, Int>,
        groupedByDay: Map<String, List<WorkEntry>>,
        productionDays: List<String>
    ) {
        val margin = 30f
        val pageWidth = 595f
        val pageHeight = 842f

        // Colori
        val navyColor = Color.parseColor("#1A2B4A")
        val goldColor = Color.parseColor("#C9A227")
        val lightGray = Color.parseColor("#F5F5F5")
        val textColor = Color.parseColor("#1A1A1A")

        // Paints
        val titlePaint = Paint().apply {
            color = navyColor
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = textColor
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = textColor
            textSize = 8f
            isAntiAlias = true
        }
        val smallPaint = Paint().apply {
            color = Color.GRAY
            textSize = 6f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
            isAntiAlias = true
        }
        val goldLinePaint = Paint().apply {
            color = goldColor
            strokeWidth = 2f
            isAntiAlias = true
        }

        var y = margin + 10f

        // Titolo
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(
            Calendar.getInstance().apply { set(year, month, 1) }.time
        ).replaceFirstChar { it.uppercase() }
        canvas.drawText("RIEPILOGO MESE: $monthName", margin, y, titlePaint)
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, goldLinePaint)
        y += 15f

        // Box riepilogo
        val boxHeight = 95f
        val boxPaint = Paint().apply { color = lightGray; isAntiAlias = true }
        canvas.drawRect(margin, y, pageWidth - margin, y + boxHeight, boxPaint)

        val col1X = margin + 10f
        val col2X = pageWidth / 2 + 10f
        var lineY = y + 12f
        val lineSpacing = 11f

        // Colonna sinistra
        canvas.drawText("Ore Lavorate: ${formatHours(totalHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Ore Attese: ${formatHours(expectedHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Ordinarie: ${formatHours(ordinaryHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Straordinarie: ${formatHours(overtimeHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Banca Ore: ${formatHours(bankHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Viaggio: ${travelMinutes}m", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Buoni Pasto: $mealBadges", col1X, lineY, bodyPaint)

        // Colonna destra
        lineY = y + 12f
        canvas.drawText("Teatrino Montaggio: $teatrinoMontaggio gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Teatrino Smontaggio: $teatrinoSmontaggio gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Teatrino Intero: $teatrinoIntero gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Servizio: $servizio gg", col2X, lineY, bodyPaint); lineY += lineSpacing + 5f
        canvas.drawText("Presenza per sede:", col2X, lineY, headerPaint); lineY += lineSpacing
        presenceByLocation.forEach { (loc, count) ->
            canvas.drawText("  $loc: $count gg", col2X, lineY, bodyPaint)
            lineY += lineSpacing
        }

        y += boxHeight + 10f

        // Tabella giorni
        val colWidths = floatArrayOf(35f, 110f, 90f, 80f, 70f)
        val headers = arrayOf("Giorno", "Marcate", "Icone", "Luogo", "Attività")
        val tableTop = y

        // Header tabella
        val headerBg = Paint().apply { color = navyColor; isAntiAlias = true }
        canvas.drawRect(margin, y, pageWidth - margin, y + 14f, headerBg)
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        var x = margin + 5f
        headers.forEachIndexed { i, h ->
            canvas.drawText(h, x, y + 10f, headerTextPaint)
            x += colWidths[i]
        }
        y += 14f

        // Righe giorni
        val sortedDays = groupedByDay.keys.sorted()
        var rowBg = false
        sortedDays.forEach { date ->
            val dayEntries = groupedByDay[date] ?: return@forEach
            val dayNum = date.split("-").last().toIntOrNull() ?: 0
            val isProduction = productionDays.contains(date)

            // Sfondo alternato
            if (rowBg) {
                val bgPaint = Paint().apply { color = lightGray; isAntiAlias = true }
                canvas.drawRect(margin, y, pageWidth - margin, y + 13f, bgPaint)
            }
            rowBg = !rowBg

            // Giorno
            canvas.drawText("$dayNum", margin + 5f, y + 10f, bodyPaint)

            // Marcate (tutti gli orari)
            val times = dayEntries.map { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
            val timeRange = if (times.size >= 2) "${times.first()}-${times.last()}" else times.joinToString(", ")
            canvas.drawText(timeRange, margin + colWidths[0] + 5f, y + 10f, bodyPaint)

            // Icone (emoji + testo)
            val icons = mutableListOf<String>()
            dayEntries.forEach { entry ->
                when (entry.activity) {
                    "Teatrino Montaggio", "Teatrino Smontaggio", "Teatrino Intero" -> icons.add("🎪")
                    "Servizio" -> icons.add("🔧")
                }
            }
            if (isProduction) icons.add("⭐")
            if (dayEntries.any { it.type == "ENTRATA" || it.type == "USCITA" }) icons.add("🍽️")
            canvas.drawText(icons.joinToString(" "), margin + colWidths[0] + colWidths[1] + 5f, y + 10f, bodyPaint)

            // Luogo
            val location = dayEntries.first().location
            canvas.drawText(location, margin + colWidths[0] + colWidths[1] + colWidths[2] + 5f, y + 10f, bodyPaint)

            // Attività
            val activity = dayEntries.first().activity
            canvas.drawText(activity, margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + 5f, y + 10f, bodyPaint)

            y += 13f
        }

        // Linea finale
        y += 5f
        canvas.drawLine(margin, y, pageWidth - margin, y, goldLinePaint)
        y += 10f

        // Nota finale
        canvas.drawText(
            "Nota: le marcature riportate sono indicative e possono variare di alcuni minuti rispetto ai rilevamenti del marcatempo aziendale.",
            margin, y, smallPaint
        )
    }

    private fun formatHours(hours: Float): String = "${hours.toInt()}h"

    private suspend fun calculateTotalHours(entries: List<WorkEntry>): Float {
        val grouped = entries.groupBy { it.date }
        var total = 0f
        grouped.forEach { (_, dayEntries) ->
            val sorted = dayEntries.sortedBy { it.timestamp }
            for (i in 0 until sorted.size - 1 step 2) {
                val diff = (sorted[i + 1].timestamp - sorted[i].timestamp) / 3600000f
                total += diff
            }
        }
        return total
    }

    private suspend fun calculateTravelMinutes(entries: List<WorkEntry>): Int {
        return entries.count { it.location == "Vignola" || it.location == "Bologna" || it.location == "Castelfranco" } * 30
    }

    private suspend fun calculateMealBadges(entries: List<WorkEntry>): Int {
        val grouped = entries.groupBy { it.date }
        var badges = 0
        grouped.forEach { (_, dayEntries) ->
            if (dayEntries.size >= 2) {
                val first = dayEntries.minBy { it.timestamp }
                val last = dayEntries.maxBy { it.timestamp }
                val hours = (last.timestamp - first.timestamp) / 3600000f
                if (hours > 6) badges++
            }
        }
        return badges
    }
}
