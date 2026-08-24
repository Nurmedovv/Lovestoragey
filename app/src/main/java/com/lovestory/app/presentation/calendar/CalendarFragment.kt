package com.lovestory.app.presentation.calendar

import com.lovestory.app.di.appContainer
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.domain.repository.CoupleRepository
import com.lovestory.app.domain.repository.CoupleSessionStore
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.lovestory.app.databinding.FragmentCalendarBinding
import androidx.recyclerview.widget.LinearLayoutManager
import com.lovestory.app.R
import com.lovestory.app.presentation.calendar.CalendarAdapter
import com.lovestory.app.presentation.calendar.CalendarDate
import com.lovestory.app.presentation.calendar.CalendarGenerator
import com.lovestory.app.presentation.calendar.CalendarViewModel
import com.lovestory.app.presentation.calendar.DateMediaDialogFragment
import com.lovestory.app.presentation.couple.MessageDialogFragment
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.SecretMessages

class CalendarFragment : BaseThemeFragment<FragmentCalendarBinding>() {

    private val viewModel: CalendarViewModel by activityViewModels()
    private lateinit var calendarAdapter: CalendarAdapter
    private var currentMessage: String = ""
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var coupleRepository: CoupleRepository
    private lateinit var coupleSessionStore: CoupleSessionStore

    private var lastMessageTimestamp: Long = 0L
    private var selectedStartDate: String = ""
    private var selectedEndDate: String = ""

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentCalendarBinding {
        return FragmentCalendarBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        FontColorHelper.applyToRoot(binding.root)
        coupleRepository.refreshFcmToken()
        viewModel.loadDateRange()
        selectedStartDate = viewModel.startDate.value.orEmpty()
        selectedEndDate = viewModel.endDate.value.orEmpty()
        val year = viewModel.currentYear.value
        if (selectedStartDate.isNotEmpty() && selectedEndDate.isNotEmpty() && year != null) {
            updateCalendarForYear(year)
        }
        updateNavigationButtonsState()
        restoreScrollPosition()
        setupPartnerMessage()
        startMessageListener()
        if (!coupleSessionStore.isPaired()) {
            binding.calendarTitle.text = getString(R.string.calendar_love_text)
            lastMessageTimestamp = 0L
        }
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
        coupleRepository.stopListening()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val container = requireContext().appContainer
        coupleRepository = container.coupleRepository
        coupleSessionStore = container.coupleSessionStore
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        setupObservers()
        setupCalendar()
        setupYearNavigation()
        setupSecretMessage()
        restorePartnerMessage()
    }

    override fun applyTheme(isDarkTheme: Boolean) {

        val textColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.text_primary_dark)
        } else {
            ContextCompat.getColor(requireContext(), R.color.white)
        }
        binding.calendarTitle.setTextColor(textColor)
        binding.secretMessageText.setTextColor(textColor)
        FontColorHelper.refreshRoot(binding.root)
    }

    override fun onFontColorChanged() {
        FontColorHelper.refreshRoot(binding.root)
        if (::calendarAdapter.isInitialized) calendarAdapter.notifyDataSetChanged()
    }

    override fun onGlassChanged() {
        GlassEffectHelper.refreshRoot(binding.root)
        val rv = binding.calendarRecyclerView
        for (i in 0 until rv.childCount) {
            GlassEffectHelper.refreshRoot(rv.getChildAt(i))
        }
    }

    private fun setupObservers() {
        viewModel.startDate.observe(viewLifecycleOwner) { selectedStartDate = it }
        viewModel.endDate.observe(viewLifecycleOwner) { selectedEndDate = it }
        viewModel.currentYear.observe(viewLifecycleOwner) { year ->
            year?.let {
                updateCalendarForYear(it)
                updateNavigationButtonsState()
            }
        }
        viewModel.refreshNeeded.observe(viewLifecycleOwner) { needed ->
            if (needed == true) {
                viewModel.loadDateRange()
                selectedStartDate = viewModel.startDate.value.orEmpty()
                selectedEndDate = viewModel.endDate.value.orEmpty()
                if (selectedStartDate.isNotEmpty() && selectedEndDate.isNotEmpty()) {
                    updateCalendarForYear(viewModel.currentYear.value ?: return@observe)
                }
                updateNavigationButtonsState()
                viewModel.clearRefresh()
            }
        }
    }

    private fun setupCalendar() {
        viewModel.loadDateRange()
        selectedStartDate = viewModel.startDate.value.orEmpty()
        selectedEndDate = viewModel.endDate.value.orEmpty()
        binding.calendarRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        if (selectedStartDate.isNotEmpty() && selectedEndDate.isNotEmpty()) {
            updateCalendarForYear(viewModel.currentYear.value ?: return)
        }
        restoreScrollPosition()
    }

    private fun setupSecretMessage() {
        binding.secretMessageText.setOnClickListener {
            showRandomSecretMessage()
        }
    }

    // настройка блока сообщений партнёра
    // если пара привязана — показывает последнее сообщение от партнёра
    // если нет — показывает статичный текст "Я тебя люблю"
    // тап по блоку — отправка нового сообщения
    private fun setupPartnerMessage() {
        if (coupleSessionStore.isPaired()) {
            binding.calendarTitle.setOnClickListener {
                val dialog = MessageDialogFragment()
                dialog.show(childFragmentManager, MessageDialogFragment.TAG)
            }
        } else {
            binding.calendarTitle.setOnClickListener(null)
        }
    }

    // слушатель сообщений от партнёра в реальном времени через Firestore
    // показывает последнее сообщение ОТ ПАРТНЁРА (не от текущего пользователя)
    private fun startMessageListener() {
        if (!coupleSessionStore.isPaired()) return
        coupleRepository.startListening { text, senderUid, timestamp ->
            if (!isAdded) return@startListening
            val myUid = coupleSessionStore.getMyUid()
            if (text.isNotEmpty() && senderUid != myUid && timestamp > lastMessageTimestamp) {
                lastMessageTimestamp = timestamp
                binding.calendarTitle.text = text
                coupleSessionStore.saveLastPartnerMessage(text)
                coupleSessionStore.saveLastPartnerMessageTimestamp(timestamp)
            }
        }
    }

    private fun restorePartnerMessage() {
        if (!coupleSessionStore.isPaired()) return
        val savedPartnerMessage = coupleSessionStore.getLastPartnerMessage()
        if (!savedPartnerMessage.isNullOrEmpty()) {
            binding.calendarTitle.text = savedPartnerMessage
        }
        lastMessageTimestamp = coupleSessionStore.getLastPartnerMessageTimestamp()
    }

    private fun showRandomSecretMessage() {
        var newMessage: String
        do {
            newMessage = SecretMessages.getRandomMessage(requireContext())
        } while (newMessage == currentMessage && SecretMessages.getMessages(requireContext()).size > 1)

        currentMessage = newMessage
        binding.secretMessageText.text = getString(R.string.message_format, newMessage)

        binding.secretMessageText.alpha = 0f
        binding.secretMessageText.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        val isVibrationEnabled = sharedPreferences.getBoolean(KEY_VIBRATION, true)
        if (isVibrationEnabled) {
            vibratePhone()
        }
    }

    private fun vibratePhone() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val vibrationEffect = android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "Error vibrating")
        }
    }

    private fun showMediaDialog(date: CalendarDate, monthName: String) {
        val dialog = DateMediaDialogFragment.newInstance(date.dayOfMonth, monthName)
        dialog.show(childFragmentManager, "date_media_dialog")
    }

    companion object {
        private const val TAG = "CalendarFragment"
        private const val PREFS_NAME = AppPrefs.PREFS_NAME
        private const val KEY_VIBRATION = AppPrefs.KEY_VIBRATION_ENABLED
    }

    private fun updateCalendarForYear(year: Int) {
        val calendarMonths = CalendarGenerator.generateMonthsForYear(
            context = requireContext(),
            year = year,
            startDate = selectedStartDate,
            endDate = selectedEndDate
        )

        calendarAdapter = CalendarAdapter(calendarMonths) { selectedDate, monthNameGenitive ->
            showMediaDialog(selectedDate, monthNameGenitive)
        }

        binding.calendarRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = calendarAdapter
        }

        binding.root.findViewById<TextView>(R.id.tvCurrentYear)?.text = year.toString()
    }

    private fun saveScrollPosition() {
        val layoutManager = binding.calendarRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val offset = if (firstVisible >= 0) {
            val view = layoutManager.findViewByPosition(firstVisible)
            view?.top ?: 0
        } else 0
        viewModel.saveScrollPosition(firstVisible, offset)
    }

    private fun restoreScrollPosition() {
        val (pos, offset) = viewModel.restoreScrollPosition()
        if (pos > 0) {
            binding.calendarRecyclerView.post {
                (binding.calendarRecyclerView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(pos, -offset)
            }
        }
    }

    private fun updateNavigationButtonsState() {
        val btnPrev = binding.root.findViewById<TextView>(R.id.btnPrevYear)
        val btnNext = binding.root.findViewById<TextView>(R.id.btnNextYear)

        btnPrev?.isEnabled = viewModel.canGoPreviousYear()
        btnNext?.isEnabled = viewModel.canGoNextYear()

        btnPrev?.alpha = if (viewModel.canGoPreviousYear()) 1.0f else 0.5f
        btnNext?.alpha = if (viewModel.canGoNextYear()) 1.0f else 0.5f
    }

    private fun setupYearNavigation() {
        val btnPrev = binding.root.findViewById<TextView>(R.id.btnPrevYear)
        val btnNext = binding.root.findViewById<TextView>(R.id.btnNextYear)

        btnPrev?.setOnClickListener {
            if (viewModel.canGoPreviousYear()) {
                saveScrollPosition()
                viewModel.previousYear()
            }
        }

        btnNext?.setOnClickListener {
            if (viewModel.canGoNextYear()) {
                saveScrollPosition()
                viewModel.nextYear()
            }
        }
    }
}
