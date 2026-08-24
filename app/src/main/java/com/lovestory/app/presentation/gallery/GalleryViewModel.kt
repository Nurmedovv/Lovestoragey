package com.lovestory.app.presentation.gallery

import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.di.appContainer
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GalleryViewModel"
    }

    private val _files = MutableLiveData<List<AppFile>>(emptyList())
    val files: LiveData<List<AppFile>> = _files

    private var filesVersion = 0

    private val _isSlideShowActive = MutableLiveData(false)
    val isSlideShowActive: LiveData<Boolean> = _isSlideShowActive

    private val _currentSlideIndex = MutableLiveData(0)
    val currentSlideIndex: LiveData<Int> = _currentSlideIndex

    private val _slideShowInterval = MutableLiveData(7000L)
    val slideShowInterval: LiveData<Long> = _slideShowInterval

    var savedSlideIndex: Int = 0

    fun loadFiles() {
        val myVersion = ++filesVersion
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = getApplication<Application>().appContainer.filesRepository.getFilesForGallery()
                    .sortedByDescending { it.uploadDate }
                withContext(Dispatchers.Main) {
                    if (myVersion == filesVersion) {
                        _files.value = result
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load gallery files", e)
            }
        }
    }

    fun setSlideShowActive(active: Boolean) {
        _isSlideShowActive.value = active
    }

    fun setCurrentSlideIndex(index: Int) {
        _currentSlideIndex.value = index
    }

    fun setSlideShowInterval(intervalMs: Long) {
        _slideShowInterval.value = intervalMs.coerceIn(3000, 15000)
    }

    fun addFile(file: AppFile) {
        val current = _files.value?.toMutableList() ?: mutableListOf()
        current.add(0, file)
        _files.value = current
    }

    fun clearFiles() {
        _files.value = emptyList()
    }

    fun getFileCount(): Int = _files.value?.size ?: 0

    fun loadAndDecodeImageOptimized(filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) return null
            val fileSize = file.length()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            val sampleSize = calculateSmartSampleSize(options.outWidth, options.outHeight, fileSize)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                if (fileSize > 3 * 1024 * 1024) inPreferredConfig = Bitmap.Config.RGB_565
            }
            var bitmap = BitmapFactory.decodeFile(filePath, decodeOptions)
            bitmap = correctImageOrientation(filePath, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image", e)
            null
        }
    }

    private fun calculateSmartSampleSize(width: Int, height: Int, fileSize: Long): Int {
        var sampleSize = 1
        val targetWidth: Int
        val targetHeight: Int
        when {
            fileSize > 10 * 1024 * 1024 -> {
                targetWidth = 800; targetHeight = 800; sampleSize = 4
            }
            fileSize > 5 * 1024 * 1024 -> {
                targetWidth = 1200; targetHeight = 1200; sampleSize = 3
            }
            fileSize > 2 * 1024 * 1024 -> {
                targetWidth = 1600; targetHeight = 1600; sampleSize = 2
            }
            else -> {
                targetWidth = 2000; targetHeight = 2000
            }
        }
        while (width / sampleSize > targetWidth && height / sampleSize > targetHeight) {
            sampleSize *= 2
        }
        return minOf(sampleSize, 8)
    }

    private fun correctImageOrientation(filePath: String, originalBitmap: Bitmap?): Bitmap? {
        if (originalBitmap == null) return null
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
                else -> originalBitmap
            }
        } catch (e: Exception) {
            originalBitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
