package com.zodiacguard.app.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

            // Crea PDF A4
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
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

            val monthName = getMonthName(year, month)
            val pdfFile = File(pdfDir, "Riepilogo_$monthName.pdf")
            pdfDocument.writeTo(pdfFile.outputStream())
            pdfDocument.close()

            pdfFile
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Errore generazione PDF: ${e.message}", e)
            null
        }
    }

    private fun getMonthName(year: Int, month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val sdf = SimpleDateFormat("MMMM_yyyy", Locale.ITALIAN)
        return sdf.format(cal.time).replaceFirstChar { c -> c.uppercase() }
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

        // Colori B/N-friendly
        val black = Color.BLACK
        val white = Color.WHITE
        val lightGray = Color.parseColor("#F0F0F0")
        val mediumGray = Color.parseColor("#CCCCCC")
        val darkGray = Color.parseColor("#333333")

        // Paints
        val titlePaint = Paint().apply {
            color = black
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = black
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = darkGray
            textSize = 8f
            isAntiAlias = true
        }
        val smallPaint = Paint().apply {
            color = Color.GRAY
            textSize = 6f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val linePaint = Paint().apply {
            color = mediumGray
            strokeWidth = 0.5f
            isAntiAlias = true
        }
        val thickLinePaint = Paint().apply {
            color = black
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        val whiteTextPaint = Paint().apply {
            color = white
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = darkGray
            textSize = 20f
            isAntiAlias = true
        }

        var y = margin + 10f

        // TITOLO
        canvas.drawText("RIEPILOGO MESE", margin, y, titlePaint)
        val monthName = getMonthName(year, month)
        val titleWidth = titlePaint.measureText("RIEPILOGO MESE")
        canvas.drawText(": $monthName", margin + titleWidth + 10f, y, subtitlePaint)
        y += 6f
        canvas.drawLine(margin, y, pageWidth - margin, y, thickLinePaint)
        y += 15f

        // BOX RIEPILOGO (con bordo nero, sfondo bianco)
        val boxHeight = 110f
        val boxRect = RectF(margin, y, pageWidth - margin, y + boxHeight)
        
        // Bordo nero
        val borderPaint = Paint().apply { color = black; strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true }
        canvas.drawRect(boxRect, borderPaint)
        
        // Sfondo bianco
        val boxBgPaint = Paint().apply { color = white; isAntiAlias = true }
        canvas.drawRect(margin + 1f, y + 1f, pageWidth - margin - 1f, y + boxHeight - 1f, boxBgPaint)

        val col1X = margin + 10f
        val col2X = pageWidth / 2 + 5f
        var lineY = y + 14f
        val lineSpacing = 13f

        // Colonna sinistra - Ore
        canvas.drawText("ORE LAVORATE: ${formatHours(totalHours)}", col1X, lineY, headerPaint); lineY += lineSpacing
        canvas.drawText("Ore attese: ${formatHours(expectedHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Ordinarie: ${formatHours(ordinaryHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Straordinarie: ${formatHours(overtimeHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Banca ore: ${formatHours(bankHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Viaggio: ${travelMinutes} min", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Buoni pasto: $mealBadges", col1X, lineY, bodyPaint)

        // Colonna destra - Attività e sedi
        lineY = y + 14f
        canvas.drawText("TEATRINO MONTAGGIO: $teatrinoMontaggio gg", col2X, lineY, headerPaint); lineY += lineSpacing
        canvas.drawText("Teatrino Smontaggio: $teatrinoSmontaggio gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Teatrino Intero: $teatrinoIntero gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Servizio: $servizio gg", col2X, lineY, bodyPaint); lineY += lineSpacing + 3f
        
        canvas.drawText("PRESENZA PER SEDE:", col2X, lineY, headerPaint); lineY += lineSpacing
        presenceByLocation.forEach { (loc, count) ->
            canvas.drawText("  - $loc: $count gg", col2X, lineY, bodyPaint)
            lineY += lineSpacing
        }

        y += boxHeight + 12f

        // TABELLA GIORNI
        val colWidths = floatArrayOf(35f, 115f, 95f, 85f, 110f)
        val headers = arrayOf("Giorno", "Marcate", "Icone", "Luogo", "Attività")
        val tableWidth = colWidths.sum()

        // Header tabella (sfondo nero, testo bianco)
        val headerRect = RectF(margin, y, margin + tableWidth, y + 14f)
        val headerBgPaint = Paint().apply { color = black; isAntiAlias = true }
        canvas.drawRect(headerRect, headerBgPaint)
        
        var x = margin + 5f
        headers.forEachIndexed { i, h ->
            canvas.drawText(h, x, y + 10f, whiteTextPaint)
            x += colWidths[i]
        }
        y += 14f

        // Bordi laterali tabella
        val tableBottom = y + (groupedByDay.size * 14f) + 5f
        canvas.drawLine(margin, y, margin, tableBottom, thickLinePaint)
        canvas.drawLine(margin + tableWidth, y, margin + tableWidth, tableBottom, thickLinePaint)

        // Righe giorni
        val sortedDays = groupedByDay.keys.sorted()
        var rowBg = false
        sortedDays.forEach { date ->
            val dayEntries = groupedByDay[date] ?: return@forEach
            val dayNum = date.split("-").last().toIntOrNull() ?: 0
            val isProduction = productionDays.contains(date)

            // Sfondo alternato grigio chiarissimo (visibile anche in B/N)
            if (rowBg) {
                val bgPaint = Paint().apply { color = lightGray; isAntiAlias = true }
                canvas.drawRect(margin + 1f, y, margin + tableWidth - 1f, y + 14f, bgPaint)
            }
            rowBg = !rowBg

            // Linea separatrice
            canvas.drawLine(margin, y, margin + tableWidth, y, linePaint)

            // Giorno (grassetto)
            canvas.drawText("$dayNum", margin + 5f, y + 10f, headerPaint)

            // Marcate (tutti gli orari)
            val times = dayEntries.map { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
            val timeRange = if (times.size >= 2) "${times.first()}-${times.last()}" else times.joinToString(", ")
            canvas.drawText(timeRange, margin + colWidths[0] + 5f, y + 10f, bodyPaint)

            // Icone (testuali per B/N)
            val icons = mutableListOf<String>()
            dayEntries.forEach { entry ->
                when (entry.activity) {
                    "Teatrino Montaggio", "Teatrino Smontaggio", "Teatrino Intero" -> icons.add("[T]")
                    "Servizio" -> icons.add("[S]")
                }
            }
            if (isProduction) icons.add("[P]")
            if (dayEntries.size >= 2) {
                val first = dayEntries.minBy { it.timestamp }
                val last = dayEntries.maxBy { it.timestamp }
                val hours = (last.timestamp - first.timestamp) / 3600000f
                if (hours > 6) icons.add("[BP]")
            }
            val locationsNeedingTravel = listOf("Vignola", "Bologna", "Castelfranco")
            if (dayEntries.any { it.location in locationsNeedingTravel }) icons.add("[V]")
            canvas.drawText(icons.joinToString(" "), margin + colWidths[0] + colWidths[1] + 5f, y + 10f, bodyPaint)

            // Luogo
            val location = dayEntries.first().location
            canvas.drawText(location, margin + colWidths[0] + colWidths[1] + colWidths[2] + 5f, y + 10f, bodyPaint)

            // Attività
            val activity = dayEntries.first().activity
            canvas.drawText(activity, margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + 5f, y + 10f, bodyPaint)

            y += 14f
        }

        // Linea finale tabella
        canvas.drawLine(margin, y, margin + tableWidth, y, thickLinePaint)
        y += 10f

        // Legenda icone (piccola, in basso)
        canvas.drawText("Legenda: [T]=Teatrino  [S]=Servizio  [P]=Produzione  [BP]=Buono Pasto  [V]=Viaggio", margin, y, smallPaint)
        y += 10f

        // Nota finale
        canvas.drawText(
            "Nota: le marcature riportate sono indicative e possono variare di alcuni minuti rispetto ai rilevamenti del marcatempo aziendale.",
            margin, y, smallPaint
        )
    }

    // Funzione helper per formattare le ore
    private fun formatHours(hours: Float): String {
        return "${hours.toInt()}h"
    }

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
        val travelLocations = listOf("Vignola", "Bologna", "Castelfranco")
        return entries.count { it.location in travelLocations } * 30
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
