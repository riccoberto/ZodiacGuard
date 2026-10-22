package com.zodiacguard.app.ui.counters

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zodiacguard.app.R
import com.zodiacguard.app.data.ManualCounter
import com.zodiacguard.app.data.ManualCounterDataStore
import com.zodiacguard.app.databinding.DialogManualCounterBinding
import com.zodiacguard.app.databinding.FragmentManualCountersBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ManualCountersFragment : Fragment() {

    private var _binding: FragmentManualCountersBinding? = null
    private val binding get() = _binding!!
    private lateinit var counterDataStore: ManualCounterDataStore
    private lateinit var counterAdapter: ManualCounterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualCountersBinding.inflate(inflater, container, false)
        counterDataStore = ManualCounterDataStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        counterAdapter = ManualCounterAdapter(
            onAdjust = { counter, delta -> adjustCounter(counter, delta) },
            onDelete = ::confirmDelete
        )
        binding.recyclerViewCounters.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCounters.adapter = counterAdapter
        binding.btnAddCounter.setOnClickListener { showCreateCounterDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            counterDataStore.countersFlow.collectLatest { counters ->
                counterAdapter.submitList(counters)
                binding.tvEmptyCounters.visibility =
                    if (counters.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showCreateCounterDialog() {
        val dialogBinding = DialogManualCounterBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.manual_counter_create_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.update_later, null)
            .setPositiveButton(R.string.manual_counter_add, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.etCounterTitle.text?.toString()?.trim().orEmpty()
                val minutes = parseDurationMinutes(
                    dialogBinding.etInitialDays.text?.toString(),
                    dialogBinding.etInitialHours.text?.toString(),
                    dialogBinding.etInitialMinutes.text?.toString()
                )
                if (title.isBlank()) {
                    dialogBinding.etCounterTitle.error =
                        getString(R.string.manual_counter_title_required)
                    return@setOnClickListener
                }
                if (minutes == null) {
                    Toast.makeText(
                        requireContext(),
                        R.string.manual_counter_invalid_total,
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        counterDataStore.addCounter(title, minutes)
                        dialog.dismiss()
                    } catch (exception: Exception) {
                        Log.e(TAG, "Creazione contatore fallita", exception)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.manual_counter_operation_error, exception.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun adjustCounter(counter: ManualCounter, deltaMinutes: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                counterDataStore.adjustCounter(counter.id, deltaMinutes)
            } catch (exception: Exception) {
                Log.e(TAG, "Aggiornamento contatore fallito", exception)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.manual_counter_operation_error, exception.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmDelete(counter: ManualCounter) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.manual_counter_delete_title)
            .setMessage(getString(R.string.manual_counter_delete_message, counter.title))
            .setNegativeButton(R.string.update_later, null)
            .setPositiveButton(R.string.manual_counter_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        counterDataStore.deleteCounter(counter.id)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Eliminazione contatore fallita", exception)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.manual_counter_operation_error, exception.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun parseDurationMinutes(days: CharSequence?, hours: CharSequence?, minutes: CharSequence?): Int? {
        val dayCount = days?.toString()?.toLongOrNull() ?: 0L
        val hourCount = hours?.toString()?.toLongOrNull() ?: 0L
        val minuteCount = minutes?.toString()?.toLongOrNull() ?: 0L
        return try {
            val total = Math.addExact(
                Math.addExact(
                    Math.multiplyExact(dayCount, MINUTES_PER_DAY),
                    Math.multiplyExact(hourCount, MINUTES_PER_HOUR)
                ),
                minuteCount
            )
            total.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        } catch (_: ArithmeticException) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TAG = "ManualCountersFragment"
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
    }
}
