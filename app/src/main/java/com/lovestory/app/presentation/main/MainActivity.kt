package com.lovestory.app.presentation.main

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import java.io.File
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.view.animation.OvershootInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.lovestory.app.databinding.ActivityMainBinding
import com.lovestory.app.di.appContainer
import com.lovestory.app.domain.repository.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.lovestory.app.R
import com.lovestory.app.presentation.main.ViewPagerAdapter
import com.lovestory.app.presentation.main.SharedViewModel
import com.lovestory.app.presentation.lock.LockScreenActivity
import com.lovestory.app.presentation.calendar.CalendarViewModel
import com.lovestory.app.presentation.notifications.LovestoryMessagingService
import com.lovestory.app.presentation.notifications.ExactTimeNotifier
import com.lovestory.app.presentation.common.DialogGlassHelper
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.ThemeChangeListener
import com.lovestory.app.presentation.common.GlassChangeListener
import com.lovestory.app.presentation.common.FontColorChangeListener
import com.lovestory.app.db.AppDatabase
import com.lovestory.app.presentation.common.isSystemDarkTheme

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var sharedPreferences: SharedPreferences
    private val themeListeners = mutableListOf<ThemeChangeListener>()
    private val calendarViewModel: CalendarViewModel by viewModels()
    val sharedViewModel: SharedViewModel by viewModels()

    private var galleryClickCount = 0
    private var lastGalleryClickTime = 0L
    private var navIndicator: View? = null
    private var navButtons: List<TextView> = emptyList()
    private var navBarLayout: View? = null
    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private lateinit var backgroundImage: ImageView

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.permissions_not_granted), Toast.LENGTH_SHORT).show()
        }
    }

    private val backgroundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it) ?: return@let
                val destFile = File(filesDir, "custom_background")
                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                sharedPreferences.edit().putString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, Uri.fromFile(destFile).toString()).apply()
                loadCustomBackground()
            } catch (_: Exception) {}
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

        // сохраняем оригинальный системный locale при первом запуске
        if (!prefs.contains("system_locale")) {
            val def = java.util.Locale.getDefault()
            prefs.edit().putString("system_locale", "${def.language}_${def.country}").apply()
        }

        val lang = prefs.getString(AppPrefs.KEY_APP_LANGUAGE, "system") ?: "system"
        val locale = when (lang) {
            "ru" -> java.util.Locale("ru")
            "en" -> java.util.Locale("en")
            "es" -> java.util.Locale("es")
            "it" -> java.util.Locale("it")
            "fr" -> java.util.Locale("fr")
            "de" -> java.util.Locale("de")
            "pt" -> java.util.Locale("pt")
            "pl" -> java.util.Locale("pl")
            "tr" -> java.util.Locale("tr")
            "ar" -> java.util.Locale("ar")
            else -> {
                // восстанавливаем оригинальный системный locale
                val saved = prefs.getString("system_locale", null)
                if (saved != null) {
                    val parts = saved.split("_")
                    java.util.Locale(parts[0], if (parts.size > 1) parts[1] else "")
                } else {
                    java.util.Locale.getDefault()
                }
            }
        }
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val newContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(newContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("DEPRECATION")
        window.setBackgroundDrawableResource(R.drawable.background_gradient)

        com.lovestory.app.db.AppDatabase.getDatabase(this)

        ExactTimeNotifier.setup(this)
        appContainer.filesRepository.initializeStorage()
        LovestoryMessagingService.createNotificationChannel(this)
        saveFcmToken()

        backgroundImage = findViewById(R.id.backgroundImage)

        FontColorHelper.applyToRoot(binding.root)

        setupFullScreenMode()
        setupTouchListener()
        setupViewPager()
        setupCustomNavigation()

        navBarLayout?.let { GlassEffectHelper.refreshRoot(it) }

        loadCustomBackground()
        applySystemTheme()
        setupKeyboardListener()

        val isFirstLaunch = sharedPreferences.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            requestInitialPermissions()
            showDateRangeDialog()
        }

        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        FontColorHelper.refreshRoot(binding.root)
        navBarLayout?.let { GlassEffectHelper.refreshRoot(it) }
        checkAutoLock()
    }

    override fun onStop() {
        super.onStop()
        appContainer.lockRepository.setLastForegroundTime(System.currentTimeMillis())
    }

    private fun checkAutoLock() {
        if (!appContainer.lockRepository.isAuthEnabled()) return
        val timeoutMs = appContainer.lockRepository.getLockTimeout()
        if (timeoutMs == Long.MAX_VALUE) return

        val lastForeground = appContainer.lockRepository.getLastForegroundTime()
        if (lastForeground == 0L) return

        val elapsed = System.currentTimeMillis() - lastForeground
        if (elapsed >= timeoutMs) {
            val intent = android.content.Intent(this, LockScreenActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupFullScreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun setupTouchListener() {
        binding.root.setOnClickListener {
            showSystemBarsTemporarily()
        }
    }

    private fun showSystemBarsTemporarily() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        lifecycleScope.launch {
            delay(2000)
            setupFullScreenMode()
        }
    }

    fun registerThemeListener(listener: ThemeChangeListener) {
        if (!themeListeners.contains(listener)) {
            themeListeners.add(listener)
        }
    }

    fun unregisterThemeListener(listener: ThemeChangeListener) {
        themeListeners.remove(listener)
    }

    fun getCurrentSystemTheme(): Boolean {
        return isSystemDarkTheme()
    }

    fun applySystemTheme() {
        val isDarkTheme = isSystemDarkTheme()
        setAppTheme(isDarkTheme)
    }

    fun setAppTheme(isDarkTheme: Boolean) {
        val hasCustomBg = sharedPreferences.getString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, null) != null
        if (isDarkTheme) {
            binding.root.setBackgroundResource(R.drawable.background_dark)
            if (!hasCustomBg) { @Suppress("DEPRECATION") window.setBackgroundDrawableResource(R.drawable.background_dark) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                        (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
            }
        } else {
            binding.root.setBackgroundResource(R.drawable.background_gradient)
            if (!hasCustomBg) { @Suppress("DEPRECATION") window.setBackgroundDrawableResource(R.drawable.background_gradient) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        notifyAllThemeListeners(isDarkTheme)
        updateNavigationColors(viewPager.currentItem)
    }

    private fun notifyAllThemeListeners(isDarkTheme: Boolean) {
        themeListeners.forEach { listener ->
            listener.onThemeChanged(isDarkTheme)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemTheme()
    }

    private fun setupViewPager() {
        viewPager = binding.viewPager
        viewPager.adapter = ViewPagerAdapter(this)
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = 4

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigationColors(position)
            }
        })
    }

    private fun setupCustomNavigation() {
        val navGallery = findViewById<TextView>(R.id.navGallery)
        val navFiles = findViewById<TextView>(R.id.navFiles)
        val navCalendar = findViewById<TextView>(R.id.navCalendar)
        val navNotes = findViewById<TextView>(R.id.navNotes)
        val navSettings = findViewById<TextView>(R.id.navSettings)
        navIndicator = findViewById(R.id.navIndicator)
        navBarLayout = (findViewById<View>(R.id.customBottomNav) as? android.view.ViewGroup)?.getChildAt(0)
        navButtons = listOf(navGallery, navFiles, navCalendar, navNotes, navSettings)

        navGallery?.setOnClickListener {
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastGalleryClickTime > 1500) {
                galleryClickCount = 1
            } else {
                galleryClickCount++
            }

            lastGalleryClickTime = currentTime

            if (galleryClickCount >= 3) {
                toggleGallerySlideShow()
                galleryClickCount = 0
                return@setOnClickListener
            }

            viewPager.currentItem = 0
            updateNavigationColors(0)
        }

        navFiles?.setOnClickListener {
            viewPager.currentItem = 1
            updateNavigationColors(1)
        }

        navCalendar?.setOnClickListener {
            viewPager.currentItem = 2
            updateNavigationColors(2)
        }

        navNotes?.setOnClickListener {
            viewPager.currentItem = 3
            updateNavigationColors(3)
        }

        navSettings?.setOnClickListener {
            viewPager.currentItem = 4
            updateNavigationColors(4)
        }

        viewPager.setCurrentItem(2, false)
        updateNavigationColors(2)
    }

    private fun toggleGallerySlideShow() {
        viewPager.currentItem = 0
        updateNavigationColors(0)
        sharedViewModel.triggerSlideShowToggle()
    }

    private fun setupKeyboardListener() {
        val navBar = findViewById<View>(R.id.customBottomNav)
        val rootView = binding.root

        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) {
                navBar.visibility = View.GONE
            } else {
                navBar.visibility = View.VISIBLE
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
    }

    fun loadCustomBackground() {
        val uriStr = sharedPreferences.getString(AppPrefs.KEY_CUSTOM_BACKGROUND_URI, null)
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                backgroundImage.setImageURI(uri)
                backgroundImage.visibility = View.VISIBLE
                applyBackgroundToWindow(uri)
            } catch (_: Exception) {
                backgroundImage.visibility = View.GONE
                @Suppress("DEPRECATION")
                window.setBackgroundDrawableResource(R.drawable.background_gradient)
            }
        } else {
            backgroundImage.visibility = View.GONE
            @Suppress("DEPRECATION")
            window.setBackgroundDrawableResource(R.drawable.background_gradient)
        }
    }

    private fun applyBackgroundToWindow(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = when (uri.scheme) {
                    "file" -> {
                        val path = uri.path ?: return@launch
                        java.io.FileInputStream(File(path))
                    }
                    else -> contentResolver.openInputStream(uri)
                } ?: return@launch
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                inputStream.use { bis ->
                    android.graphics.BitmapFactory.decodeStream(bis, null, options)
                }

                val metrics = resources.displayMetrics
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                options.inSampleSize = calculateInSampleSize(options, screenW, screenH)
                options.inJustDecodeBounds = false

                val inputStream2 = when (uri.scheme) {
                    "file" -> {
                        val path = uri.path ?: return@launch
                        java.io.FileInputStream(File(path))
                    }
                    else -> contentResolver.openInputStream(uri)
                } ?: return@launch
                val bitmap = inputStream2.use { bis ->
                    android.graphics.BitmapFactory.decodeStream(bis, null, options)
                }
                if (bitmap == null) return@launch

                val scale = maxOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
                val scaledW = (bitmap.width * scale).toInt()
                val scaledH = (bitmap.height * scale).toInt()
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                val x = (scaledW - screenW) / 2
                val y = (scaledH - screenH) / 2
                val cropped = android.graphics.Bitmap.createBitmap(scaled, x, y, screenW, screenH)
                if (scaled !== bitmap) scaled.recycle()
                val drawable = android.graphics.drawable.BitmapDrawable(resources, cropped)
                withContext(Dispatchers.Main) {
                    @Suppress("DEPRECATION")
                    window.setBackgroundDrawable(drawable)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    @Suppress("DEPRECATION")
                    window.setBackgroundDrawableResource(R.drawable.background_gradient)
                }
            }
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (w, h) = options.outWidth to options.outHeight
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun clearCustomBackground() {
        sharedPreferences.edit().remove(AppPrefs.KEY_CUSTOM_BACKGROUND_URI).apply()
        backgroundImage.setImageURI(null)
        backgroundImage.visibility = View.GONE
        @Suppress("DEPRECATION")
        window.setBackgroundDrawableResource(R.drawable.background_gradient)
    }

    fun pickBackgroundImage() {
        backgroundPickerLauncher.launch("image/*")
    }

    override fun onDestroy() {
        super.onDestroy()
        keyboardListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
    }

    private fun updateNavigationColors(selectedPosition: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.accent)
        val inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive)

        navButtons.forEachIndexed { index, button ->
            button?.setTextColor(if (index == selectedPosition) activeColor else inactiveColor)
        }

        navIndicator?.let { indicator ->
            indicator.post {
                val container = indicator.parent as? android.widget.FrameLayout ?: return@post
                val containerWidth = container.width - container.paddingStart - container.paddingEnd
                val buttonWidth = containerWidth / navButtons.size
                val targetX = buttonWidth * selectedPosition

                indicator.animate().cancel()
                indicator.animate()
                    .translationX(targetX.toFloat())
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun showDateRangeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_date_range_picker, null)
        val isDarkTheme = isSystemDarkTheme()

        val rootLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.dialogRootLayout)
        DialogGlassHelper.applyDialogBackground(rootLayout, isDarkTheme)

        val innerLayout = rootLayout.getChildAt(0) as? android.view.View
        innerLayout?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }

        val navBar = rootLayout.getChildAt(2) as? android.view.View
        navBar?.let { DialogGlassHelper.applyDialogContentBorder(it, isDarkTheme) }

        val textColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        val tvStartDate = dialogView.findViewById<TextView>(R.id.dialogTvStartDate)
        val tvEndDate = dialogView.findViewById<TextView>(R.id.dialogTvEndDate)
        val btnPositive = dialogView.findViewById<TextView>(R.id.dialogPositiveButton)
        val btnNegative = dialogView.findViewById<TextView>(R.id.dialogNegativeButton)

        tvStartDate.setTextColor(textColor)
        tvEndDate.setTextColor(textColor)
        btnPositive.setTextColor(textColor)
        btnNegative.setTextColor(textColor)

        val labelFrom = dialogView.findViewById<TextView>(R.id.dialogTvStartDate)?.parent?.let {
            (it as android.view.ViewGroup).getChildAt(0) as? TextView
        }
        val labelTo = dialogView.findViewById<TextView>(R.id.dialogTvEndDate)?.parent?.let {
            (it as android.view.ViewGroup).getChildAt(0) as? TextView
        }
        labelFrom?.setTextColor(textColor)
        labelTo?.setTextColor(textColor)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        var startYear = currentYear
        var startMonth = 0
        var startDay = 1

        var endYear = currentYear
        var endMonth = 11
        var endDay = 31

        tvStartDate.text = String.format(java.util.Locale.getDefault(),"01.01.%d", currentYear)
        tvEndDate.text = String.format(java.util.Locale.getDefault(),"31.12.%d", currentYear)

        tvStartDate.setOnClickListener {
            showDatePickerDialog { year, month, day ->
                startYear = year
                startMonth = month
                startDay = day
                tvStartDate.text = String.format(java.util.Locale.getDefault(),"%02d.%02d.%d", day, month + 1, year)
            }
        }

        tvEndDate.setOnClickListener {
            showDatePickerDialog { year, month, day ->
                endYear = year
                endMonth = month
                endDay = day
                tvEndDate.text = String.format(java.util.Locale.getDefault(),"%02d.%02d.%d", day, month + 1, year)
            }
        }

        val dialog = Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(false)

        btnPositive.setOnClickListener {
            saveDateRange(startYear, startMonth, startDay, endYear, endMonth, endDay)
            val prefs = getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_first_launch", false).apply()

            dialog.dismiss()

            calendarViewModel.triggerRefresh()
        }

        btnNegative.setOnClickListener {
            dialog.dismiss()
        }

        @Suppress("DEPRECATION")
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.dimAmount = 0.7f

        dialog.show()
    }

    private fun showDatePickerDialog(onDateSelected: (year: Int, month: Int, day: Int) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            R.style.LovestoryDatePickerTheme,
            { _, year, month, dayOfMonth ->
                onDateSelected(year, month, dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun saveDateRange(
        startYear: Int, startMonth: Int, startDay: Int,
        endYear: Int, endMonth: Int, endDay: Int
    ) {
        val prefs = getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(AppPrefs.KEY_START_YEAR, startYear)
            putInt(AppPrefs.KEY_START_MONTH, startMonth)
            putInt(AppPrefs.KEY_START_DAY, startDay)
            putInt(AppPrefs.KEY_END_YEAR, endYear)
            putInt(AppPrefs.KEY_END_MONTH, endMonth)
            putInt(AppPrefs.KEY_END_DAY, endDay)
            apply()
        }
    }

    private fun saveFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                sharedPreferences.edit().putString(AppPrefs.KEY_FCM_TOKEN, token).apply()
                appContainer.coupleRepository.refreshFcmToken()
            }
        }
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("open_calendar", false) == true) {
            viewPager.setCurrentItem(2, false)
            updateNavigationColors(2)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private val glassListeners = mutableListOf<GlassChangeListener>()

    fun registerGlassListener(listener: GlassChangeListener) {
        if (!glassListeners.contains(listener)) glassListeners.add(listener)
    }

    fun unregisterGlassListener(listener: GlassChangeListener) {
        glassListeners.remove(listener)
    }

    fun notifyGlassChanged() {
        navBarLayout?.let { GlassEffectHelper.refreshRoot(it) }
        glassListeners.forEach { it.onGlassChanged() }
    }

    private val fontColorListeners = mutableListOf<FontColorChangeListener>()

    fun registerFontColorListener(listener: FontColorChangeListener) {
        if (!fontColorListeners.contains(listener)) fontColorListeners.add(listener)
    }

    fun unregisterFontColorListener(listener: FontColorChangeListener) {
        fontColorListeners.remove(listener)
    }

    fun notifyFontColorChanged() {
        FontColorHelper.refreshRoot(binding.root)
        fontColorListeners.forEach { it.onFontColorChanged() }
    }
}