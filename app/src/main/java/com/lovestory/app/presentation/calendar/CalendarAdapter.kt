package com.lovestory.app.presentation.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lovestory.app.R
import com.lovestory.app.presentation.calendar.CalendarMonth
import com.lovestory.app.presentation.calendar.CalendarDate
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper

// адаптер для отображения месяцев календаря
class CalendarAdapter(
    private val months: List<CalendarMonth>,
    private val onDateClick: (CalendarDate, String) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.MonthViewHolder>() {

    // общий пул для вложенных списков, чтобы переиспользовать ViewHolder
    private val viewPool = RecyclerView.RecycledViewPool()

    class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthTitle: TextView = itemView.findViewById(R.id.monthTitle)
        val datesRecyclerView: RecyclerView = itemView.findViewById(R.id.datesRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_month, parent, false)
        return MonthViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        val month = months[position]

        GlassEffectHelper.refreshRoot(holder.itemView)
        FontColorHelper.refreshRoot(holder.itemView)

        // название месяца в именительном падеже для отображения
        holder.monthTitle.text = month.monthName

        // адаптер для дней месяца, передаём родительный падеж для диалогов
        val dateAdapter = DateAdapter(month.dates) { date ->
            onDateClick(date, month.monthNameGenitive)
        }

        // подключаем общий пул для вложенного списка
        holder.datesRecyclerView.setRecycledViewPool(viewPool)

        holder.datesRecyclerView.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(
                holder.itemView.context,
                7
            )
            adapter = dateAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(14)
        }
    }

    override fun getItemCount() = months.size
}

// адаптер для отображения дней месяца с выделением особых дат
class DateAdapter(
    private val dates: List<CalendarDate>,
    private val onDateClick: (CalendarDate) -> Unit
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayNumber: TextView = itemView.findViewById(R.id.dayNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_date, parent, false)
        return DateViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        val date = dates[position]

        // номер дня
        holder.dayNumber.text = date.dayOfMonth.toString()

        // особое оформление для праздников
        if (date.isSpecial) {
            holder.itemView.setBackgroundResource(R.drawable.special_date_background)
            holder.dayNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        } else {
            holder.dayNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.accent))
            holder.itemView.setBackgroundResource(R.drawable.date_selector)
        }

        // обработка нажатия на дату
        holder.itemView.setOnClickListener {
            onDateClick(date)
        }
    }

    override fun getItemCount() = dates.size
}