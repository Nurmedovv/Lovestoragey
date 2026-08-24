package com.lovestory.app.presentation.calendar

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.lovestory.app.domain.repository.AppPrefs
import java.util.Calendar

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = AppPrefs.PREFS_NAME
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentYear = MutableLiveData(Calendar.getInstance().get(Calendar.YEAR))
    val currentYear: LiveData<Int> = _currentYear

    private val _minYear = MutableLiveData(Calendar.getInstance().get(Calendar.YEAR))
    val minYear: LiveData<Int> = _minYear

    private val _maxYear = MutableLiveData(Calendar.getInstance().get(Calendar.YEAR))
    val maxYear: LiveData<Int> = _maxYear

    private val _scrollPosition = MutableLiveData(0)
    val scrollPosition: LiveData<Int> = _scrollPosition

    private val _scrollOffset = MutableLiveData(0)
    val scrollOffset: LiveData<Int> = _scrollOffset

    private val _startDate = MutableLiveData("")
    val startDate: LiveData<String> = _startDate

    private val _endDate = MutableLiveData("")
    val endDate: LiveData<String> = _endDate

    private val _refreshNeeded = MutableLiveData<Boolean>(false)
    val refreshNeeded: LiveData<Boolean> = _refreshNeeded

    fun triggerRefresh() {
        _refreshNeeded.value = true
    }

    fun clearRefresh() {
        _refreshNeeded.value = false
    }

    fun loadDateRange() {
        val startYear = prefs.getInt(AppPrefs.KEY_START_YEAR, Calendar.getInstance().get(Calendar.YEAR))
        val startMonth = prefs.getInt(AppPrefs.KEY_START_MONTH, 0)
        val startDay = prefs.getInt(AppPrefs.KEY_START_DAY, 1)

        val endYear = prefs.getInt(AppPrefs.KEY_END_YEAR, Calendar.getInstance().get(Calendar.YEAR))
        val endMonth = prefs.getInt(AppPrefs.KEY_END_MONTH, 11)
        val endDay = prefs.getInt(AppPrefs.KEY_END_DAY, 31)

        _minYear.value = startYear
        _maxYear.value = endYear
        _currentYear.value = startYear

        _startDate.value = String.format(java.util.Locale.getDefault(),"%d-%02d-%02d", startYear, startMonth + 1, startDay)
        _endDate.value = String.format(java.util.Locale.getDefault(),"%d-%02d-%02d", endYear, endMonth + 1, endDay)
    }

    fun nextYear() {
        val current = _currentYear.value ?: return
        val max = _maxYear.value ?: return
        if (current < max) {
            _currentYear.value = current + 1
        }
    }

    fun previousYear() {
        val current = _currentYear.value ?: return
        val min = _minYear.value ?: return
        if (current > min) {
            _currentYear.value = current - 1
        }
    }

    fun saveScrollPosition(position: Int, offset: Int) {
        val year = _currentYear.value ?: return
        prefs.edit()
            .putInt("calendar_scroll_position_$year", position)
            .putInt("calendar_scroll_offset_$year", offset)
            .apply()
        _scrollPosition.value = position
        _scrollOffset.value = offset
    }

    fun restoreScrollPosition(): Pair<Int, Int> {
        val year = _currentYear.value ?: return 0 to 0
        val position = prefs.getInt("calendar_scroll_position_$year", 0)
        val offset = prefs.getInt("calendar_scroll_offset_$year", 0)
        _scrollPosition.value = position
        _scrollOffset.value = offset
        return position to offset
    }

    fun canGoNextYear(): Boolean {
        return (_currentYear.value ?: 0) < (_maxYear.value ?: 0)
    }

    fun canGoPreviousYear(): Boolean {
        return (_currentYear.value ?: 0) > (_minYear.value ?: 0)
    }
}
