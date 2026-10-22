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
            formatDuration(counter.currentMinutes)
        )
        binding.tvCounterInitial.text = binding.root.context.getString(
            R.string.manual_counter_initial_value,
            formatDuration(counter.initialMinutes)
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
        val days = daysText.toLongOrNull() ?: 0L
        val hours = hoursText.toLongOrNull() ?: 0L
        val minutes = minutesText.toLongOrNull() ?: 0L
        val amount = try {
            Math.addExact(
                Math.addExact(
                    Math.multiplyExact(days, MINUTES_PER_DAY),
                    Math.multiplyExact(hours, MINUTES_PER_HOUR)
                ),
                minutes
            )
        } catch (_: ArithmeticException) {
            0L
        }
        val updatedTotal = counter.currentMinutes.toLong() + amount * direction
        if ((daysText.isNotEmpty() && days == 0L) ||
            (hoursText.isNotEmpty() && hours == 0L) ||
            (minutesText.isNotEmpty() && minutes == 0L) ||
            amount <= 0 ||
            amount > Int.MAX_VALUE ||
            updatedTotal !in 0..Int.MAX_VALUE.toLong()
        ) {
            Toast.makeText(
                daysField.context,
                R.string.manual_counter_invalid_adjustment,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        onAdjust(counter, (amount * direction).toInt())
        daysField.text.clear()
        hoursField.text.clear()
        minutesField.text.clear()
    }

    private fun formatDuration(totalMinutes: Int): String {
        val days = totalMinutes / MINUTES_PER_DAY
        val hours = totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return "$days g  $hours h  $minutes min"
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
