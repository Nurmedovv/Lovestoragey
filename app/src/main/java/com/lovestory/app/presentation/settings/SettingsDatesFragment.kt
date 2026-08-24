package com.lovestory.app.presentation.settings

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import java.util.Calendar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentSettingsDatesBinding
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.R
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.applyRoundedCorners

// Категория настроек «Календарь и даты»: диапазон дат отношений.
// Логика перенесена из прежнего монолитного SettingsFragment без изменений поведения.
class SettingsDatesFragment : BaseThemeFragment<FragmentSettingsDatesBinding>() {

    private lateinit var sharedPreferences: SharedPreferences

    private var startYear = Calendar.getInstance().get(Calendar.YEAR)
    private var startMonth = 0
    private var startDay = 1

    private var endYear = Calendar.getInstance().get(Calendar.YEAR)
    private var endMonth = 11
    private var endDay = 31

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsDatesBinding {
        return FragmentSettingsDatesBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        loadSavedDateRange()
        updateStartDateDisplay()
        updateEndDateDisplay()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        sharedPreferences = requireContext().getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        setupDatePickers()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    private fun loadSavedDateRange() {
        startYear = sharedPreferences.getInt(AppPrefs.KEY_START_YEAR, Calendar.getInstance().get(Calendar.YEAR))
        startMonth = sharedPreferences.getInt(AppPrefs.KEY_START_MONTH, 0)
        startDay = sharedPreferences.getInt(AppPrefs.KEY_START_DAY, 1)

        endYear = sharedPreferences.getInt(AppPrefs.KEY_END_YEAR, Calendar.getInstance().get(Calendar.YEAR))
        endMonth = sharedPreferences.getInt(AppPrefs.KEY_END_MONTH, 11)
        endDay = sharedPreferences.getInt(AppPrefs.KEY_END_DAY, 31)

        updateStartDateDisplay()
        updateEndDateDisplay()
    }

    private fun saveDateRange() {
        if (!isDateRangeValid()) {
            MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
                .setTitle(getString(R.string.error_title))
                .setMessage(getString(R.string.error_date_range_invalid))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
                .applyRoundedCorners()
            return
        }

        performSaveDateRange()
    }

    private fun performSaveDateRange() {
        sharedPreferences.edit().apply {
            putInt(AppPrefs.KEY_START_YEAR, startYear)
            putInt(AppPrefs.KEY_START_MONTH, startMonth)
            putInt(AppPrefs.KEY_START_DAY, startDay)
            putInt(AppPrefs.KEY_END_YEAR, endYear)
            putInt(AppPrefs.KEY_END_MONTH, endMonth)
            putInt(AppPrefs.KEY_END_DAY, endDay)
            apply()
        }
    }

    private fun updateStartDateDisplay() {
        val dateText = String.format(java.util.Locale.getDefault(),"%02d.%02d.%d", startDay, startMonth + 1, startYear)
        binding.root.findViewById<TextView>(R.id.tvStartDate)?.text = dateText
    }

    private fun updateEndDateDisplay() {
        val dateText = String.format(java.util.Locale.getDefault(),"%02d.%02d.%d", endDay, endMonth + 1, endYear)
        binding.root.findViewById<TextView>(R.id.tvEndDate)?.text = dateText
    }

    private fun setupDatePickers() {
        val llStartDate = binding.root.findViewById<View>(R.id.llStartDate)
        val llEndDate = binding.root.findViewById<View>(R.id.llEndDate)

        llStartDate?.setOnClickListener {
            showDatePicker(isStartDate = true)
        }

        llEndDate?.setOnClickListener {
            showDatePicker(isStartDate = false)
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val year = if (isStartDate) startYear else endYear
        val month = if (isStartDate) startMonth else endMonth
        val day = if (isStartDate) startDay else endDay

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            R.style.LovestoryDatePickerTheme,
            { _, selectedYear, selectedMonth, selectedDay ->
                if (isStartDate) {
                    startYear = selectedYear
                    startMonth = selectedMonth
                    startDay = selectedDay
                    updateStartDateDisplay()
                } else {
                    endYear = selectedYear
                    endMonth = selectedMonth
                    endDay = selectedDay
                    updateEndDateDisplay()
                }
                saveDateRange()
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun isDateRangeValid(): Boolean {
        if (startYear > endYear) return false
        if (startYear == endYear && startMonth > endMonth) return false
        if (startYear == endYear && startMonth == endMonth && startDay > endDay) return false
        return true
    }
}
