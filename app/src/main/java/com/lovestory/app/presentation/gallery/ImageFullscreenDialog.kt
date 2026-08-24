package com.lovestory.app.presentation.gallery

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lovestory.app.R

// диалог для полноэкранного просмотра изображений
class ImageFullscreenDialog : DialogFragment() {

    private lateinit var imageView: ImageView
    private var imagePath: String = ""

    companion object {
        private const val ARG_IMAGE_PATH = "image_path"

        // создаёт экземпляр диалога с путём к изображению
        fun newInstance(imagePath: String): ImageFullscreenDialog {
            val args = Bundle().apply {
                putString(ARG_IMAGE_PATH, imagePath)
            }
            return ImageFullscreenDialog().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePath = arguments?.getString(ARG_IMAGE_PATH) ?: ""
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        @Suppress("DEPRECATION")
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_image_fullscreen, container, false)
        imageView = view.findViewById(R.id.fullscreenImageView)

        loadFullscreenImage()

        // закрытие по клику на изображение
        imageView.setOnClickListener {
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        // полноэкранный режим
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // загружает и отображает изображение
    private fun loadFullscreenImage() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadAndCorrectOrientation(imagePath)
                }
                if (!isAdded) return@launch
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                } else {
                    dismiss()
                }
            } catch (e: Exception) {
                if (isAdded) dismiss()
            }
        }
    }

    // загружает изображение с коррекцией ориентации
    private fun loadAndCorrectOrientation(filePath: String): Bitmap? {
        return try {
            val orientation = getExifOrientationFast(filePath)
            val bitmap = loadOptimizedBitmap(filePath) ?: return null
            applyOrientationCorrection(bitmap, orientation)
        } catch (e: Exception) {
            BitmapFactory.decodeFile(filePath)
        }
    }

    // быстро получает ориентацию из EXIF
    private fun getExifOrientationFast(filePath: String): Int {
        return try {
            val exif = ExifInterface(filePath)
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    // загружает оптимизированный Bitmap под размер экрана
    private fun loadOptimizedBitmap(filePath: String): Bitmap? {
        return try {
            val screenSize = getScreenSize()
            val targetWidth = screenSize.first * 2
            val targetHeight = screenSize.second * 2

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            val sampleSize = calculateOptimalSampleSize(
                options.outWidth,
                options.outHeight,
                targetWidth,
                targetHeight
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                inMutable = true
            }

            BitmapFactory.decodeFile(filePath, decodeOptions)
        } catch (e: Exception) {
            null
        }
    }

    // возвращает размер экрана
    private fun getScreenSize(): Pair<Int, Int> {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = requireActivity().windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } else {
                @Suppress("DEPRECATION")
                val displayMetrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
            }
        } catch (e: Exception) {
            Pair(1080, 1920)
        }
    }

    // рассчитывает оптимальный коэффициент масштабирования
    private fun calculateOptimalSampleSize(
        imageWidth: Int,
        imageHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        val maxImageSize = 4000
        var inSampleSize = 1

        if (imageWidth > maxImageSize || imageHeight > maxImageSize) {
            inSampleSize = 4
            while (imageWidth / inSampleSize > targetWidth * 2 ||
                imageHeight / inSampleSize > targetHeight * 2) {
                inSampleSize *= 2
            }
        } else {
            while (imageWidth / inSampleSize > targetWidth ||
                imageHeight / inSampleSize > targetHeight) {
                inSampleSize *= 2
            }
        }

        return minOf(inSampleSize, 8)
    }

    // применяет коррекцию ориентации
    private fun applyOrientationCorrection(bitmap: Bitmap, orientation: Int): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 ->
                rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 ->
                rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 ->
                rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    // поворачивает Bitmap
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply {
            postRotate(degrees)
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}