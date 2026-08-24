package com.lovestory.app.presentation.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.lovestory.app.presentation.gallery.GalleryFragment
import com.lovestory.app.presentation.calendar.CalendarFragment
import com.lovestory.app.presentation.notes.NotesFragment
import com.lovestory.app.presentation.files.FilesFragment
import com.lovestory.app.presentation.settings.SettingsFragment

// адаптер для ViewPager2 с 5 страницами
class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // возвращает количество страниц
    override fun getItemCount(): Int = 5

    // создаёт фрагмент для указанной позиции
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GalleryFragment()
            1 -> FilesFragment()
            2 -> CalendarFragment()
            3 -> NotesFragment()
            4 -> SettingsFragment()
            else -> CalendarFragment()
        }
    }
}