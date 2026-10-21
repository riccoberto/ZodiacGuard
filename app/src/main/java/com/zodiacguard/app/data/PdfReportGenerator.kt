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

            // 1. Filtra marcature del mese in modo esplicito
            val monthEntries = mutableListOf<WorkEntry>()
            for (entry in entries) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = entry.timestamp
                if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                    monthEntries.add(entry)
                }
            }
            
            // Ordinamento esplicito senza lambda 'it'
            monthEntries.sortWith(Comparator { a, b -> 
                a.timestamp.compareTo(b.timestamp) 
            })

            if (monthEntries.isEmpty()) return null

            // 2. Calcola totali
            val totalHours = calculateTotalHours(monthEntries)
            val expectedHours = workDays.toFloat() * weeklyHours.toFloat()
            val ordinaryHours = if (totalHours < expectedHours) totalHours else expectedHours
            val overtimeHours = if (totalHours > expectedHours) totalHours - expectedHours else 0f
            val bankHours = totalHours - expectedHours
            val travelMinutes = calculateTravelMinutes(monthEntries)
            val mealBadges = calculateMealBadges(monthEntries)

            // 3. Conta attività
            var teatrinoMontaggio = 0
            var teatrinoSmontaggio = 0
            var teatrinoIntero = 0
            var servizio = 0
            for (entry in monthEntries) {
                when (entry.activity) {
                    "Teatrino Montaggio" -> teatrinoMontaggio++
                    "Teatrino Smontaggio" -> teatrinoSmontaggio++
                    "Teatrino Intero" -> teatrinoIntero++
                    "Servizio" -> servizio++
                }
            }

            // 4. Presenza per sede
            val presenceByLocation = mutableMapOf<String, Int>()
            for (entry in monthEntries) {
                val currentCount = presenceByLocation[entry.location] ?: 0
                presenceByLocation[entry.location] = currentCount + 1
            }

            // 5. Raggruppa per giorno
            val groupedByDay = mutableMapOf<String, MutableList<WorkEntry>>()
            for (entry in monthEntries) {
                val list = groupedByDay[entry.date]
                if (list == null) {
                    val newList = mutableListOf<WorkEntry>()
                    newList.add(entry)
                    groupedByDay[entry.date] = newList
                } else {
                    list.add(entry)
                }
            }

            // 6. Crea PDF A4
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            drawPdf(canvas, year, month, totalHours, expectedHours, ordinaryHours,
                overtimeHours, bankHours, travelMinutes, mealBadges,
                teatrinoMontaggio, teatrinoSmontaggio, teatrinoIntero, servizio,
                presenceByLocation, groupedByDay, productionDays)

            pdfDocument.finishPage(page)

            // 7. Salva file
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val pdfDir = File(downloadDir, PDF_FOLDER_NAME)
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }

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
        val raw = sdf.format(cal.time)
        return capitalizeFirst(raw)
    }

    private fun capitalizeFirst(s: String): String {
        if (s.isEmpty()) return s
        return s.substring(0, 1).uppercase(Locale.ITALIAN) + s.substring(1)
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

        val black = Color.BLACK
        val white = Color.WHITE
        val lightGray = Color.parseColor("#F0F0F0")
        val mediumGray = Color.parseColor("#CCCCCC")
        val darkGray = Color.parseColor("#333333")

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

        // BOX RIEPILOGO
        val boxHeight = 110f
        val boxRect = RectF(margin, y, pageWidth - margin, y + boxHeight)
        val borderPaint = Paint().apply { 
            color = black
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true 
        }
        canvas.drawRect(boxRect, borderPaint)
        val boxBgPaint = Paint().apply { color = white; isAntiAlias = true }
        canvas.drawRect(margin + 1f, y + 1f, pageWidth - margin - 1f, y + boxHeight - 1f, boxBgPaint)

        val col1X = margin + 10f
        val col2X = pageWidth / 2 + 5f
        var lineY = y + 14f
        val lineSpacing = 13f

        canvas.drawText("ORE LAVORATE: ${formatHours(totalHours)}", col1X, lineY, headerPaint); lineY += lineSpacing
        canvas.drawText("Ore attese: ${formatHours(expectedHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Ordinarie: ${formatHours(ordinaryHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Straordinarie: ${formatHours(overtimeHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Banca ore: ${formatHours(bankHours)}", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Viaggio: ${travelMinutes} min", col1X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Buoni pasto: $mealBadges", col1X, lineY, bodyPaint)

        lineY = y + 14f
        canvas.drawText("TEATRINO MONTAGGIO: $teatrinoMontaggio gg", col2X, lineY, headerPaint); lineY += lineSpacing
        canvas.drawText("Teatrino Smontaggio: $teatrinoSmontaggio gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Teatrino Intero: $teatrinoIntero gg", col2X, lineY, bodyPaint); lineY += lineSpacing
        canvas.drawText("Servizio: $servizio gg", col2X, lineY, bodyPaint); lineY += lineSpacing + 3f
        
        canvas.drawText("PRESENZA PER SEDE:", col2X, lineY, headerPaint); lineY += lineSpacing
        
        val locationEntries = presenceByLocation.entries.toList()
        for (i in locationEntries.indices) {
            val locEntry = locationEntries[i]
            canvas.drawText("  - ${locEntry.key}: ${locEntry.value} gg", col2X, lineY, bodyPaint)
            lineY += lineSpacing
        }

        y += boxHeight + 12f

        // TABELLA GIORNI
        val colWidths = floatArrayOf(35f, 115f, 95f, 85f, 110f)
        val headers = arrayOf("Giorno", "Marcate", "Icone", "Luogo", "Attività")
        val tableWidth = colWidths.sum()

        val headerRect = RectF(margin, y, margin + tableWidth, y + 14f)
        val headerBgPaint = Paint().apply { color = black; isAntiAlias = true }
        canvas.drawRect(headerRect, headerBgPaint)
        
        var x = margin + 5f
        for (i in headers.indices) {
            canvas.drawText(headers[i], x, y + 10f, whiteTextPaint)
            x += colWidths[i]
        }
        y += 14f

        val tableBottom = y + (groupedByDay.size * 14f) + 5f
        canvas.drawLine(margin, y, margin, tableBottom, thickLinePaint)
        canvas.drawLine(margin + tableWidth, y, margin + tableWidth, tableBottom, thickLinePaint)

        val sortedKeys = groupedByDay.keys.toList()
        sortedKeys.sort()
        
        var rowBg = false
        for (i in sortedKeys.indices) {
            val date = sortedKeys[i]
            val dayEntries = groupedByDay[date]
            if (dayEntries == null) continue
            
            val dayNum = date.split("-").last().toIntOrNull() ?: 0
            val isProduction = productionDays.contains(date)

            if (rowBg) {
                val bgPaint = Paint().apply { color = lightGray; isAntiAlias = true }
                canvas.drawRect(margin + 1f, y, margin + tableWidth - 1f, y + 14f, bgPaint)
            }
            rowBg = !rowBg

            canvas.drawLine(margin, y, margin + tableWidth, y, linePaint)
            canvas.drawText("$dayNum", margin + 5f, y + 10f, headerPaint)

            // Marcate
            val times = mutableListOf<String>()
            for (j in dayEntries.indices) {
                val entry = dayEntries[j]
                val cal = Calendar.getInstance()
                cal.timeInMillis = entry.timestamp
                times.add(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
            }
            val timeRange = if (times.size >= 2) "${times[0]}-${times[times.size - 1]}" else times.joinToString(", ")
            canvas.drawText(timeRange, margin + colWidths[0] + 5f, y + 10f, bodyPaint)

            // Icone
            val icons = mutableListOf<String>()
            for (j in dayEntries.indices) {
                val entry = dayEntries[j]
                if (entry.activity == "Teatrino Montaggio" || entry.activity == "Teatrino Smontaggio" || entry.activity == "Teatrino Intero") {
                    icons.add("[T]")
                } else if (entry.activity == "Servizio") {
                    icons.add("[S]")
                }
            }
            if (isProduction) icons.add("[P]")
            
            if (dayEntries.size >= 2) {
                var firstTime = dayEntries[0].timestamp
                var lastTime = dayEntries[0].timestamp
                for (j in dayEntries.indices) {
                    val entry = dayEntries[j]
                    if (entry.timestamp < firstTime) firstTime = entry.timestamp
                    if (entry.timestamp > lastTime) lastTime = entry.timestamp
                }
                val hours = (lastTime - firstTime) / 3600000f
                if (hours > 6) icons.add("[BP]")
            }
            
            val travelLocations = listOf("Vignola", "Bologna", "Castelfranco")
            var needsTravel = false
            for (j in dayEntries.indices) {
                if (travelLocations.contains(dayEntries[j].location)) {
                    needsTravel = true
                    break
                }
            }
            if (needsTravel) icons.add("[V]")
            
            canvas.drawText(icons.joinToString(" "), margin + colWidths[0] + colWidths[1] + 5f, y + 10f, bodyPaint)

            // Luogo
            val location = dayEntries[0].location
            canvas.drawText(location, margin + colWidths[0] + colWidths[1] + colWidths[2] + 5f, y + 10f, bodyPaint)

            // Attività
            val activity = dayEntries[0].activity
            canvas.drawText(activity, margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + 5f, y + 10f, bodyPaint)

            y += 14f
        }

        canvas.drawLine(margin, y, margin + tableWidth, y, thickLinePaint)
        y += 10f

        canvas.drawText("Legenda: [T]=Teatrino  [S]=Servizio  [P]=Produzione  [BP]=Buono Pasto  [V]=Viaggio", margin, y, smallPaint)
        y += 10f

        canvas.drawText(
            "Nota: le marcature riportate sono indicative e possono variare di alcuni minuti rispetto ai rilevamenti del marcatempo aziendale.",
            margin, y, smallPaint
        )
    }

    private fun formatHours(hours: Float): String {
        return "${hours.toInt()}h"
    }

    private suspend fun calculateTotalHours(entries: List<WorkEntry>): Float {
        val grouped = mutableMapOf<String, MutableList<WorkEntry>>()
        for (entry in entries) {
            val list = grouped[entry.date]
            if (list == null) {
                val newList = mutableListOf<WorkEntry>()
                newList.add(entry)
                grouped[entry.date] = newList
            } else {
                list.add(entry)
            }
        }
        
        var total = 0f
        val mapEntries = grouped.entries.toList()
        for (i in mapEntries.indices) {
            val mapEntry = mapEntries[i]
            val dayEntries = mapEntry.value
            
            dayEntries.sortWith(Comparator { a, b -> 
                a.timestamp.compareTo(b.timestamp) 
            })
            
            var j = 0
            while (j < dayEntries.size - 1) {
                val diff = (dayEntries[j + 1].timestamp - dayEntries[j].timestamp) / 3600000f
                total += diff
                j += 2
            }
        }
        return total
    }

    private suspend fun calculateTravelMinutes(entries: List<WorkEntry>): Int {
        val travelLocations = listOf("Vignola", "Bologna", "Castelfranco")
        var count = 0
        for (entry in entries) {
            if (travelLocations.contains(entry.location)) {
                count++
            }
        }
        return count * 30
    }

    private suspend fun calculateMealBadges(entries: List<WorkEntry>): Int {
        val grouped = mutableMapOf<String, MutableList<WorkEntry>>()
        for (entry in entries) {
            val list = grouped[entry.date]
            if (list == null) {
                val newList = mutableListOf<WorkEntry>()
                newList.add(entry)
                grouped[entry.date] = newList
            } else {
                list.add(entry)
            }
        }
        
        var badges = 0
        val mapEntries = grouped.entries.toList()
        for (i in mapEntries.indices) {
            val mapEntry = mapEntries[i]
            val dayEntries = mapEntry.value
            
            if (dayEntries.size >= 2) {
                var firstTime = dayEntries[0].timestamp
                var lastTime = dayEntries[0].timestamp
                
                for (j in dayEntries.indices) {
                    val currentEntry = dayEntries[j]
                    if (currentEntry.timestamp < firstTime) {
                        firstTime = currentEntry.timestamp
                    }
                    if (currentEntry.timestamp > lastTime) {
                        lastTime = currentEntry.timestamp
                    }
                }
                
                val hours = (lastTime - firstTime) / 3600000f
                if (hours > 6) {
                    badges++
                }
            }
        }
        return badges
    }
}
