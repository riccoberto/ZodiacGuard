package com.zodiacguard.app.ui.counters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.zodiacguard.app.R
import com.zodiacguard.app.data.ManualCounter
import com.zodiacguard.app.databinding.ItemManualCounterBinding

class ManualCounterAdapter(
    private val onAdjust: (ManualCounter, Int) -> Unit,
    private val onDelete: (ManualCounter) -> Unit
) : RecyclerView.Adapter<ManualCounterAdapter.CounterViewHolder>() {

    private var counters: List<ManualCounter> = emptyList()

    class CounterViewHolder(val binding: ItemManualCounterBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CounterViewHolder {
        val binding = ItemManualCounterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CounterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CounterViewHolder, position: Int) {
        val counter = counters[position]
        val binding = holder.binding
        binding.tvCounterTitle.text = counter.title
        binding.tvCounterValue.text = binding.root.context.getString(
            R.string.manual_counter_current_total_format,
            formatDuration(counter.currentMinutes, counter.dayLengthMinutes)
        )
        binding.tvCounterInitial.text = binding.root.context.getString(
            R.string.manual_counter_initial_value,
            formatDuration(counter.initialMinutes, counter.dayLengthMinutes)
        )
        binding.tvCounterDayLength.text = binding.root.context.getString(
            R.string.manual_counter_day_length,
            formatDuration(counter.dayLengthMinutes, counter.dayLengthMinutes)
        )

        binding.btnAddUnits.setOnClickListener {
            applyAdjustment(counter, binding.etAdjustDays, binding.etAdjustHours, binding.etAdjustMinutes, 1)
        }
        binding.btnRemoveUnits.setOnClickListener {
            applyAdjustment(counter, binding.etAdjustDays, binding.etAdjustHours, binding.etAdjustMinutes, -1)
        }
        binding.btnDeleteCounter.setOnClickListener { onDelete(counter) }
    }

    override fun getItemCount(): Int = counters.size

    fun submitList(newCounters: List<ManualCounter>) {
        counters = newCounters
        notifyDataSetChanged()
    }

    private fun applyAdjustment(
        counter: ManualCounter,
        daysField: EditText,
        hoursField: EditText,
        minutesField: EditText,
        direction: Int
    ) {
        val daysText = daysField.text.toString()
        val hoursText = hoursField.text.toString()
        val minutesText = minutesField.text.toString()
        val days = daysText.toLongOrNull() ?: if (daysText.isEmpty()) 0L else null
        val hours = hoursText.toLongOrNull() ?: if (hoursText.isEmpty()) 0L else null
        val minutes = minutesText.toLongOrNull() ?: if (minutesText.isEmpty()) 0L else null
        if (days == null || hours == null || minutes == null ||
            days < 0 || hours < 0 || minutes !in 0..59
        ) {
            showInvalidAdjustment(daysField)
            return
        }
        val amount = try {
            Math.addExact(
                Math.addExact(
                    Math.multiplyExact(days, counter.dayLengthMinutes.toLong()),
                    Math.multiplyExact(hours, MINUTES_PER_HOUR)
                ),
                minutes
            )
        } catch (_: ArithmeticException) {
            0L
        }
        val updatedTotal = counter.currentMinutes.toLong() + amount * direction
        if (amount <= 0 ||
            amount > Int.MAX_VALUE ||
            updatedTotal !in 0..Int.MAX_VALUE.toLong()
        ) {
            showInvalidAdjustment(daysField)
            return
        }

        onAdjust(counter, (amount * direction).toInt())
        daysField.text.clear()
        hoursField.text.clear()
        minutesField.text.clear()
    }

    private fun formatDuration(totalMinutes: Int, dayLengthMinutes: Int): String {
        val days = totalMinutes / dayLengthMinutes
        val remainder = totalMinutes % dayLengthMinutes
        val hours = remainder / MINUTES_PER_HOUR
        val minutes = remainder % MINUTES_PER_HOUR
        return "$days g  $hours h  $minutes min"
    }

    private fun showInvalidAdjustment(view: EditText) {
        Toast.makeText(
            view.context,
            R.string.manual_counter_invalid_adjustment,
            Toast.LENGTH_SHORT
        ).show()
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
