package com.lovestory.app.presentation.gallery

import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.domain.model.FileLocation
import com.lovestory.app.domain.model.FileType
import com.lovestory.app.domain.model.AppFile
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lovestory.app.databinding.FragmentGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lovestory.app.R
import com.lovestory.app.di.appContainer
import com.lovestory.app.presentation.main.SharedViewModel
import com.lovestory.app.presentation.gallery.GalleryPageDialogFragment
import com.lovestory.app.presentation.gallery.GalleryViewModel
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper
import com.lovestory.app.presentation.common.applyRoundedCorners

class GalleryFragment : BaseThemeFragment<FragmentGalleryBinding>() {

    companion object {
        private const val TAG = "GalleryFragment"
    }

    private val viewModel: GalleryViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var slideShowHandler: Handler? = null
    private var slideShowRunnable: Runnable? = null

    private fun getSlideShowHandler(): Handler {
        if (slideShowHandler == null) {
            slideShowHandler = Handler(Looper.getMainLooper())
        }
        return slideShowHandler!!
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) handleSelectedFiles(data)
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentGalleryBinding {
        return FragmentGalleryBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        FontColorHelper.applyToRoot(binding.root)
        GlassEffectHelper.refreshRoot(binding.galleryActionPanel)
        GlassEffectHelper.refreshRoot(binding.slideshowControls)
        if (viewModel.isSlideShowActive.value != true) {
            updateLastPhotoPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        // слайд-шоу продолжается, не останавливаем
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        binding.galleryActionPanel.post { GlassEffectHelper.refreshRoot(binding.galleryActionPanel) }
        setupGalleryPage()
        setupObservers()
        viewModel.loadFiles()
        updateLastPhotoPreview()
        setupButtonListeners()

        sharedViewModel.slideShowToggle.observe(viewLifecycleOwner) { active ->
            if (active) startSlideShow() else stopSlideShow()
        }

        parentFragmentManager.setFragmentResultListener("files_changed", viewLifecycleOwner) { _, _ ->
            viewModel.loadFiles()
        }
    }

    override fun onDestroyView() {
        stopSlideShow()
        super.onDestroyView()
    }

    override fun applyTheme(isDarkTheme: Boolean) {
        FontColorHelper.refreshRoot(binding.root)
    }

    override fun onGlassChanged() {
        GlassEffectHelper.refreshRoot(binding.root)
    }

    override fun onFontColorChanged() {
        FontColorHelper.applyToRoot(binding.root)
    }

    private fun setupObservers() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            // обновляем slideShowFiles если слайдшоу активно
            if (viewModel.isSlideShowActive.value == true) {
                val currentIdx = viewModel.currentSlideIndex.value ?: 0
                slideShowFiles = files.toList()
                if (slideShowFiles.isEmpty()) {
                    stopSlideShow()
                    return@observe
                }
                if (currentIdx >= slideShowFiles.size) {
                    viewModel.setCurrentSlideIndex(0)
                }
                // обновляем счётчик
                val idx = viewModel.currentSlideIndex.value ?: 0
                binding.lastPhotoInfo.text = getString(R.string.slideshow_counter, idx + 1, slideShowFiles.size)
            } else {
                updateLastPhotoPreview()
                binding.lastPhotoInfo.text = ""
            }
        }
    }

    private fun setupGalleryPage() {
        binding.galleryTitle.text = "\uD83D\uDDBC\uFE0F"
        binding.galleryDescription.text = getString(R.string.gallery_description)
    }

    private fun updateLastPhotoPreview() {
        val files = viewModel.files.value
        val lastFile = files?.firstOrNull()

        if (lastFile != null && lastFile.fileType == FileType.PHOTO) {
            binding.lastPhotoPreview.background = null
            loadImageWithoutAnimation(binding.lastPhotoPreview, lastFile.internalPath)
            binding.lastPhotoInfo.text = ""
            setupPreviewClickListeners()
        } else if (lastFile != null && lastFile.fileType == FileType.VIDEO) {
            loadVideoPreviewWithoutAnimation(binding.lastPhotoPreview, lastFile.internalPath)
            binding.lastPhotoPreview.background = null
            binding.lastPhotoInfo.text = ""
            setupPreviewClickListeners()
        } else {
            binding.lastPhotoPreview.setImageDrawable(null)
            binding.lastPhotoInfo.text = ""
            binding.lastPhotoPreview.background = null
            setupPreviewClickListeners()
        }
    }

    private fun loadImageWithoutAnimation(imageView: ImageView, filePath: String) {
        val options = RequestOptions().centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
        Glide.with(imageView.context).asBitmap().load(filePath).apply(options).into(imageView)
    }

    private fun loadVideoPreviewWithoutAnimation(imageView: ImageView, filePath: String) {
        val options = RequestOptions().centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
        Glide.with(imageView.context).asBitmap().load(filePath).apply(options).into(imageView)
    }

    private fun setupPreviewClickListeners() {
        binding.lastPhotoPreview.setOnClickListener {
            if (viewModel.getFileCount() > 0) showGalleryDialog()
        }
        binding.lastPhotoInfo.setOnClickListener {
            if (viewModel.getFileCount() > 0) showGalleryDialog()
        }
    }

    private fun setupButtonListeners() {
        binding.uploadPhotoButton.setOnClickListener { openFilePicker() }
        binding.openGalleryButton.setOnClickListener {
            if (viewModel.getFileCount() > 0) showGalleryDialog()
        }
        binding.deletePhotosButton.setOnClickListener {
            if (viewModel.getFileCount() > 0) deleteAllFiles()
        }

        binding.btnSlidePrevious.setOnClickListener { previousSlide() }
        binding.btnSlideNext.setOnClickListener { nextSlide() }
        binding.btnSlidePause.setOnClickListener {
            if (isSlideShowPaused()) {
                resumeSlideShow()
                binding.btnSlidePause.text = "\u23F8\uFE0F"
            } else {
                pauseSlideShow()
                binding.btnSlidePause.text = "\u25B6\uFE0F"
            }
        }
        binding.btnSlideInterval.setOnClickListener { showIntervalDialog() }
    }

    private fun showIntervalDialog() {
        val intervals = arrayOf(getString(R.string.interval_3_sec), getString(R.string.interval_5_sec), getString(R.string.interval_7_sec), getString(R.string.interval_10_sec), getString(R.string.interval_15_sec))
        val intervalValues = longArrayOf(3000, 5000, 7000, 10000, 15000)
        val currentIndex = intervalValues.indexOf(viewModel.slideShowInterval.value ?: 7000L).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.slideshow_interval_title))
            .setSingleChoiceItems(intervals, currentIndex) { dialog, which ->
                viewModel.setSlideShowInterval(intervalValues[which])
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    private fun showSlideshowControls(show: Boolean) {
        binding.slideshowControls.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showGalleryDialog() {
        val dialog = GalleryPageDialogFragment.newInstance()
        dialog.show(parentFragmentManager, "gallery_page_dialog")
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.pick_photos_title)))
        } catch (_: android.content.ActivityNotFoundException) {
            Log.e(TAG, "No file picker app found")
        }
    }

    private fun handleSelectedFiles(data: Intent) {
        try {
            val clipData = data.clipData
            lifecycleScope.launch {
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        saveFileToGallery(uri)
                    }
                } else {
                    val uri = data.data
                    if (uri != null) saveFileToGallery(uri)
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "Error handling selected files")
        }
    }

    private suspend fun saveFileToGallery(uri: Uri): Boolean {
        return try {
            val location = FileLocation(LocationType.GALLERY_PAGE)
            val savedFile = requireContext().appContainer.filesRepository.saveFileFromUri(uri, location)
            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    viewModel.addFile(savedFile)
                }
                true
            } else false
        } catch (_: Exception) { false }
    }

    private fun deleteAllFiles() {
        val files = viewModel.files.value ?: return
        if (files.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext(), R.style.LovestoryDialogTheme)
            .setTitle(getString(R.string.delete_all_title))
            .setMessage(getString(R.string.delete_all_message))
            .setPositiveButton(getString(R.string.delete_all)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val filesToDelete = files.toList()
                        withContext(Dispatchers.IO) {
                            filesToDelete.forEach {
                                requireContext().appContainer.deleteFileUseCase(it.internalPath)
                            }
                        }
                        viewModel.clearFiles()
                        if (viewModel.isSlideShowActive.value == true) stopSlideShow()
                    } catch (_: Exception) {
                        Log.e(TAG, "Error deleting all files")
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .applyRoundedCorners()
    }

    // слайд-шоу

    private var slideShowFiles: List<AppFile> = emptyList()

    fun startSlideShow() {
        val files = viewModel.files.value
        if (files.isNullOrEmpty() || viewModel.isSlideShowActive.value == true) return
        slideShowFiles = files.toList()
        viewModel.savedSlideIndex = 0
        startSlideShowFrom(0)
        showSlideshowControls(true)
        binding.btnSlidePause.text = "\u23F8\uFE0F"
        Toast.makeText(requireContext(), getString(R.string.slideshow_started), Toast.LENGTH_SHORT).show()
    }

    private fun startSlideShowFrom(index: Int) {
        if (slideShowFiles.isEmpty()) return
        viewModel.setSlideShowActive(true)
        viewModel.setCurrentSlideIndex(index.coerceAtMost(slideShowFiles.size - 1))
        showSlideInPreview(viewModel.currentSlideIndex.value ?: 0)
        scheduleNextSlide()
    }

    private fun scheduleNextSlide() {
        val handler = getSlideShowHandler()
        slideShowRunnable = object : Runnable {
            override fun run() {
                if (viewModel.isSlideShowActive.value != true || !isAdded || isDetached || view == null) return
                if (slideShowFiles.isEmpty()) { stopSlideShow(); return }
                val currentIdx = viewModel.currentSlideIndex.value ?: 0
                val nextIndex = (currentIdx + 1) % slideShowFiles.size
                viewModel.setCurrentSlideIndex(nextIndex)
                animateSlide(nextIndex)
                handler.postDelayed(this, viewModel.slideShowInterval.value ?: 7000L)
            }
        }
        handler.postDelayed(slideShowRunnable!!, viewModel.slideShowInterval.value ?: 7000L)
    }

    fun pauseSlideShow() {
        if (viewModel.isSlideShowActive.value != true) return
        slideShowRunnable?.let { slideShowHandler?.removeCallbacks(it) }
        slideShowRunnable = null
    }

    fun resumeSlideShow() {
        if (viewModel.isSlideShowActive.value != true) return
        scheduleNextSlide()
    }

    fun isSlideShowPaused(): Boolean = slideShowRunnable == null && viewModel.isSlideShowActive.value == true

    fun nextSlide() {
        if (viewModel.isSlideShowActive.value != true) return
        if (slideShowFiles.isEmpty()) return
        slideShowRunnable?.let { slideShowHandler?.removeCallbacks(it) }
        val currentIdx = viewModel.currentSlideIndex.value ?: 0
        viewModel.setCurrentSlideIndex((currentIdx + 1) % slideShowFiles.size)
        animateSlide(viewModel.currentSlideIndex.value ?: 0)
        if (!isSlideShowPaused()) scheduleNextSlide()
    }

    fun previousSlide() {
        if (viewModel.isSlideShowActive.value != true) return
        if (slideShowFiles.isEmpty()) return
        slideShowRunnable?.let { slideShowHandler?.removeCallbacks(it) }
        val currentIdx = viewModel.currentSlideIndex.value ?: 0
        val newIndex = if (currentIdx - 1 < 0) slideShowFiles.size - 1 else currentIdx - 1
        viewModel.setCurrentSlideIndex(newIndex)
        animateSlide(newIndex)
        if (!isSlideShowPaused()) scheduleNextSlide()
    }

    private fun animateSlide(index: Int) {
        if (!isAdded || isDetached || view == null) return
        if (index >= slideShowFiles.size) { stopSlideShow(); return }
        val file = slideShowFiles[index]

        binding.slideOutView.setImageBitmap((binding.lastPhotoPreview.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap)
        binding.slideOutView.scaleType = binding.lastPhotoPreview.scaleType
        binding.slideOutView.visibility = View.VISIBLE

        val targetWidth = binding.lastPhotoPreview.width
        binding.lastPhotoPreview.translationX = targetWidth.toFloat()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    viewModel.loadAndDecodeImageOptimized(file.internalPath)
                }
                if (bitmap != null) {
                    if (!isAdded || isDetached || view == null) return@launch
                    binding.lastPhotoPreview.setImageBitmap(bitmap)
                    binding.lastPhotoPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                    binding.lastPhotoInfo.text = getString(R.string.slideshow_counter, index + 1, slideShowFiles.size)
                    binding.lastPhotoPreview.animate().translationX(0f).setDuration(1500).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).start()
                    binding.slideOutView.animate().translationX(-targetWidth.toFloat()).setDuration(1500).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).withEndAction {
                        binding.slideOutView.visibility = View.GONE
                        binding.slideOutView.translationX = 0f
                    }.start()
                }
            } catch (_: Exception) {
                if (viewModel.isSlideShowActive.value == true) {
                    stopSlideShow()
                }
            }
        }
    }

    private fun showSlideInPreview(index: Int) {
        if (!isAdded || isDetached || view == null) return
        try {
            val files = slideShowFiles
            if (files.isEmpty() || index >= files.size) { if (viewModel.isSlideShowActive.value == true) stopSlideShow(); return }
            val file = files[index]
            binding.lastPhotoInfo.text = getString(R.string.slideshow_counter, index + 1, files.size)
            loadImageWithoutAnimation(binding.lastPhotoPreview, file.internalPath)
            setupPreviewClickListeners()
        } catch (_: Exception) { if (viewModel.isSlideShowActive.value == true) stopSlideShow() }
    }

    fun stopSlideShow(showToast: Boolean = true) {
        if (viewModel.isSlideShowActive.value != true) return
        viewModel.setSlideShowActive(false)
        slideShowFiles = emptyList()
        slideShowRunnable?.let { slideShowHandler?.removeCallbacks(it) }
        slideShowHandler = null; slideShowRunnable = null
        showSlideshowControls(false)
        viewModel.savedSlideIndex = 0
        viewModel.setCurrentSlideIndex(0)
        viewModel.loadFiles()
        if (showToast) context?.let {
            Toast.makeText(it, getString(R.string.slideshow_stopped), Toast.LENGTH_SHORT).show()
        }
    }
}
