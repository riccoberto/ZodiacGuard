package com.zodiacguard.app.ui.dashboard

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.zodiacguard.app.R
import java.text.SimpleDateFormat
import java.util.*

class CustomCalendarDialog(
    private val selectedDate: String,
    private val datesWithData: Set<String>,
    private val onDateSelected: (String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_custom_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedCal = Calendar.getInstance()
        selectedCal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate) ?: Date()
        
        val year = selectedCal.get(Calendar.YEAR)
        val month = selectedCal.get(Calendar.MONTH)

        val tvMonthYear = view.findViewById<TextView>(R.id.tvMonthYear)
        val gridCalendar = view.findViewById<GridLayout>(R.id.gridCalendar)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        tvMonthYear.text = sdf.format(cal.time).replaceFirstChar { it.uppercase() }

        // Configura la griglia (7 colonne per i giorni della settimana)
        gridCalendar.columnCount = 7

        // Aggiungi header dei giorni
        val daysOfWeek = arrayOf("L", "M", "M", "G", "V", "S", "D")
        for (day in daysOfWeek) {
            val textView = TextView(requireContext())
            textView.text = day
            textView.setTextColor(Color.parseColor("#FFD700"))
            textView.textSize = 14f
            textView.setPadding(8, 8, 8, 8)
            textView.gravity = android.view.Gravity.CENTER
            gridCalendar.addView(textView)
        }

        // Calcola il primo giorno del mese e i giorni totali
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Domenica, ma vogliamo Lunedì = 0
        val adjustedFirstDay = if (firstDayOfWeek == 0) 6 else firstDayOfWeek - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Aggiungi spazi vuoti per i giorni prima del primo
        for (i in 0 until adjustedFirstDay) {
            val emptyView = TextView(requireContext())
            emptyView.text = ""
            gridCalendar.addView(emptyView)
        }

        // Aggiungi i giorni del mese
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (day in 1..daysInMonth) {
            val dateStr = sdfDate.format(Calendar.getInstance().apply {
                set(year, month, day)
            }.time)

            val textView = TextView(requireContext())
            textView.text = day.toString()
            textView.textSize = 16f
            textView.setPadding(12, 12, 12, 12)
            textView.gravity = android.view.Gravity.CENTER

            // Colore diverso se il giorno ha dati
            if (datesWithData.contains(dateStr)) {
                textView.setBackgroundColor(Color.parseColor("#4A90E2")) // Blu per giorni con dati
                textView.setTextColor(Color.WHITE)
            } else {
                textView.setBackgroundColor(Color.TRANSPARENT)
                textView.setTextColor(Color.WHITE)
            }

            // Evidenzia la data selezionata
            if (dateStr == selectedDate) {
                textView.setBackgroundColor(Color.parseColor("#FFD700")) // Oro per data selezionata
                textView.setTextColor(Color.parseColor("#000033"))
            }

            textView.setOnClickListener {
                onDateSelected(dateStr)
                dismiss()
            }

            gridCalendar.addView(textView)
        }

        btnClose.setOnClickListener {
            dismiss()
        }
    }
}
