package com.lovestory.app.presentation.calendar

import com.lovestory.app.R

// определяет международные праздники для выделения особых дат в календаре
object HolidayHelper {

    // возвращает карту праздников: месяц -> день -> ресурс строки
    private fun getHolidayResIds(): Map<Int, Map<Int, Int>> = mapOf(
        1 to mapOf(1 to R.string.holiday_new_year),
        2 to mapOf(14 to R.string.holiday_valentines_day, 23 to R.string.holiday_defender_day),
        3 to mapOf(8 to R.string.holiday_womens_day),
        5 to mapOf(1 to R.string.holiday_spring_labor, 9 to R.string.holiday_victory_day),
        6 to mapOf(12 to R.string.holiday_russia_day),
        11 to mapOf(4 to R.string.holiday_unity_day)
    )

    // проверяет, является ли указанная дата праздником
    fun isHoliday(day: Int, month: Int): Boolean {
        return getHolidayResIds()[month]?.containsKey(day) == true
    }
}