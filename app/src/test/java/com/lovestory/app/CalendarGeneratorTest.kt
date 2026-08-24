package com.lovestory.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.lovestory.app.presentation.calendar.CalendarMonth
import com.lovestory.app.presentation.calendar.CalendarDate
import com.lovestory.app.presentation.calendar.CalendarGenerator

// Robolectric: генератор читает строки месяцев через context.getString,
// поэтому нужен реальный контекст с ресурсами, а не mock.
// qualifiers="ru" — базовые значения строк русские, локаль по умолчанию en-US
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru")
class CalendarGeneratorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    // ==================== generateMonthsForYear ====================

    @Test
    fun `generateMonthsForYear - returns empty for blank dates`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "", "")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateMonthsForYear - returns empty for invalid format`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-01", "2026-12")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateMonthsForYear - returns empty for non-numeric dates`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "abc-def-ghi", "jkl-mno-pqr")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateMonthsForYear - full year range`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-01-01", "2026-12-31")
        assertEquals(12, result.size)
        assertEquals("Январь", result[0].monthName)
        assertEquals("Декабрь", result[11].monthName)
    }

    @Test
    fun `generateMonthsForYear - partial year range`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-03-15", "2026-06-20")
        assertEquals(4, result.size)
        assertEquals("Март", result[0].monthName)
        assertEquals("Июнь", result[3].monthName)
    }

    @Test
    fun `generateMonthsForYear - single month`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-05-01", "2026-05-31")
        assertEquals(1, result.size)
        assertEquals("Май", result[0].monthName)
    }

    @Test
    fun `generateMonthsForYear - correct days in January`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-01-01", "2026-01-31")
        assertEquals(1, result.size)
        assertEquals(31, result[0].dates.size)
        assertEquals(1, result[0].dates[0].dayOfMonth)
        assertEquals(31, result[0].dates[30].dayOfMonth)
    }

    @Test
    fun `generateMonthsForYear - leap year February`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2024, "2024-02-01", "2024-02-29")
        assertEquals(1, result.size)
        assertEquals(29, result[0].dates.size)
    }

    @Test
    fun `generateMonthsForYear - non-leap year February`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2025, "2025-02-01", "2025-02-28")
        assertEquals(1, result.size)
        assertEquals(28, result[0].dates.size)
    }

    @Test
    fun `generateMonthsForYear - cross-year range shows only relevant months`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2025-12-15", "2026-01-15")
        assertEquals(1, result.size)
        assertEquals("Январь", result[0].monthName)
        assertEquals(15, result[0].dates.size)
    }

    @Test
    fun `generateMonthsForYear - range outside year returns all months`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2025-01-01", "2025-12-31")
        assertEquals(12, result.size)
    }

    @Test
    fun `generateMonthsForYear - partial first month`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-03-15", "2026-03-31")
        assertEquals(1, result.size)
        assertEquals(17, result[0].dates.size)
        assertEquals(15, result[0].dates[0].dayOfMonth)
    }

    @Test
    fun `generateMonthsForYear - partial last month`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-03-01", "2026-03-15")
        assertEquals(1, result.size)
        assertEquals(15, result[0].dates.size)
        assertEquals(15, result[0].dates[14].dayOfMonth)
    }

    // ==================== Month names ====================

    @Test
    fun `month names - all months have correct genitive form`() {
        val result = CalendarGenerator.generateMonthsForYear(context, 2026, "2026-01-01", "2026-12-31")
        val expectedGenitive = listOf(
            "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня",
            "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря"
        )
        result.forEachIndexed { index, month ->
            assertEquals(expectedGenitive[index], month.monthNameGenitive)
        }
    }

    // ==================== CalendarDate ====================

    @Test
    fun `CalendarDate - default values`() {
        val date = CalendarDate(dayOfMonth = 15)
        assertEquals(15, date.dayOfMonth)
        assertTrue(date.mediaFiles.isEmpty())
        assertFalse(date.isSpecial)
    }

    @Test
    fun `CalendarMonth - data class equality`() {
        val month1 = CalendarMonth("Январь", "Января", emptyList())
        val month2 = CalendarMonth("Январь", "Января", emptyList())
        assertEquals(month1, month2)
    }
}
