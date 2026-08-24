package com.lovestory.app.presentation.calendar

import com.lovestory.app.domain.model.AppFile
import android.content.Context
import java.util.Calendar
import com.lovestory.app.R
import com.lovestory.app.presentation.calendar.HolidayHelper

// модель месяца с названиями в двух падежах
data class CalendarMonth(
    val monthName: String,
    val monthNameGenitive: String,
    val dates: List<CalendarDate>
)

// модель дня календаря
data class CalendarDate(
    val dayOfMonth: Int,
    val mediaFiles: List<AppFile> = emptyList(),
    val isSpecial: Boolean = false,
    val year: Int = Calendar.getInstance().get(Calendar.YEAR)
)

// генератор календаря
object CalendarGenerator {

    // генерирует список месяцев для конкретного года с учётом диапазона дат
    fun generateMonthsForYear(context: Context, year: Int, startDate: String, endDate: String): List<CalendarMonth> {
        val months = mutableListOf<CalendarMonth>()

        if (startDate.isBlank() || endDate.isBlank()) return months

        val startParts = startDate.split("-")
        val endParts = endDate.split("-")

        if (startParts.size != 3 || endParts.size != 3) return months

        val startYear = startParts[0].toIntOrNull() ?: return months
        val startMonth = startParts[1].toIntOrNull() ?: return months
        val startDay = startParts[2].toIntOrNull() ?: return months

        val endYear = endParts[0].toIntOrNull() ?: return months
        val endMonth = endParts[1].toIntOrNull() ?: return months
        val endDay = endParts[2].toIntOrNull() ?: return months

        // первый месяц года, попадающий в диапазон
        val firstMonth = if (year == startYear) startMonth else 1
        // последний месяц года, попадающий в диапазон
        val lastMonth = if (year == endYear) endMonth else 12

        for (month in firstMonth..lastMonth) {
            val daysInMonth = getDaysInMonth(year, month)

            // первый и последний день месяца в диапазоне
            val firstDay = if (year == startYear && month == startMonth) startDay else 1
            val lastDay = if (year == endYear && month == endMonth) endDay else daysInMonth

            val dates = (firstDay..lastDay).map { day ->
                CalendarDate(
                    dayOfMonth = day,
                    isSpecial = HolidayHelper.isHoliday(day, month),
                    year = year
                )
            }

            if (dates.isNotEmpty()) {
                months.add(
                    CalendarMonth(
                        monthName = getMonthName(context, month),
                        monthNameGenitive = getMonthNameGenitive(context, month),
                        dates = dates
                    )
                )
            }
        }

        return months
    }

    // количество дней в месяце с учётом високосного года
    private fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 31
        }
    }

    // проверка на високосный год
    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    // название месяца в именительном падеже
    private fun getMonthName(context: Context, month: Int): String {
        return when (month) {
            1 -> context.getString(R.string.month_january)
            2 -> context.getString(R.string.month_february)
            3 -> context.getString(R.string.month_march)
            4 -> context.getString(R.string.month_april)
            5 -> context.getString(R.string.month_may)
            6 -> context.getString(R.string.month_june)
            7 -> context.getString(R.string.month_july)
            8 -> context.getString(R.string.month_august)
            9 -> context.getString(R.string.month_september)
            10 -> context.getString(R.string.month_october)
            11 -> context.getString(R.string.month_november)
            12 -> context.getString(R.string.month_december)
            else -> ""
        }
    }

    // название месяца в родительном падеже
    private fun getMonthNameGenitive(context: Context, month: Int): String {
        return when (month) {
            1 -> context.getString(R.string.month_genitive_january)
            2 -> context.getString(R.string.month_genitive_february)
            3 -> context.getString(R.string.month_genitive_march)
            4 -> context.getString(R.string.month_genitive_april)
            5 -> context.getString(R.string.month_genitive_may)
            6 -> context.getString(R.string.month_genitive_june)
            7 -> context.getString(R.string.month_genitive_july)
            8 -> context.getString(R.string.month_genitive_august)
            9 -> context.getString(R.string.month_genitive_september)
            10 -> context.getString(R.string.month_genitive_october)
            11 -> context.getString(R.string.month_genitive_november)
            12 -> context.getString(R.string.month_genitive_december)
            else -> ""
        }
    }
}