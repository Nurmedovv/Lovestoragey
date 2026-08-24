package com.lovestory.app.presentation.common

import com.lovestory.app.domain.model.FileType
import com.lovestory.app.domain.model.AppFile
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.lovestory.app.R
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.isSystemDarkTheme

// адаптер для отображения списка файлов с превью
class FileAdapter(
    initialFiles: List<AppFile>,
    private val onFileClick: (AppFile) -> Unit,
    private val onFileLongClick: (AppFile) -> Unit,
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var files: MutableList<AppFile> = initialFiles.toMutableList()

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        val videoPlayOverlay: ImageView? = itemView.findViewById(R.id.videoPlayOverlay)
        val fileDate: TextView = itemView.findViewById(R.id.fileDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]

        GlassEffectHelper.refreshRoot(holder.itemView)

        // настройки Glide: обрезка по центру, заглушка, кэширование
        val options = RequestOptions()
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)

        when (file.fileType) {
            FileType.VIDEO -> {
                holder.videoPlayOverlay?.visibility = View.VISIBLE
                holder.videoPlayOverlay?.alpha = 1.0f
                Glide.with(holder.itemView.context)
                    .asBitmap()
                    .load(file.internalPath)
                    .apply(options)
                    .into(holder.fileIcon)
            }
            FileType.PHOTO -> {
                holder.videoPlayOverlay?.visibility = View.GONE
                Glide.with(holder.itemView.context)
                    .asBitmap()
                    .load(file.internalPath)
                    .apply(options)
                    .into(holder.fileIcon)
            }
            FileType.AUDIO -> {
                holder.videoPlayOverlay?.visibility = View.GONE
                holder.fileIcon.setImageResource(android.R.drawable.ic_media_ff)
                holder.fileIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.white))
                holder.fileIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            FileType.DOCUMENT -> {
                holder.videoPlayOverlay?.visibility = View.GONE
                holder.fileIcon.setImageResource(android.R.drawable.ic_menu_edit)
                holder.fileIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.white))
                holder.fileIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            else -> {
                holder.videoPlayOverlay?.visibility = View.GONE
                holder.fileIcon.setImageResource(android.R.drawable.ic_menu_help)
                holder.fileIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.white))
                holder.fileIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }

        // дата загрузки файла
        holder.fileDate.text = android.text.format.DateFormat
            .format("dd.MM.yyyy", file.uploadDate)
        val isDark = holder.itemView.context.isSystemDarkTheme()
        holder.fileDate.setTextColor(if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)

        holder.itemView.setOnClickListener {
            onFileClick(file)
        }

        holder.itemView.setOnLongClickListener {
            onFileLongClick(file)
            true
        }
    }

    override fun getItemCount() = files.size

    fun attachFiles(mutableFiles: MutableList<AppFile>) {
        files = mutableFiles
        notifyDataSetChanged()
    }
}