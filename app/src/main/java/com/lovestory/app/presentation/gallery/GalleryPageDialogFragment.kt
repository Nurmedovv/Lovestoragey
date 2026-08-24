package com.lovestory.app.presentation.gallery

import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.R
import com.lovestory.app.di.appContainer
import com.lovestory.app.presentation.common.BaseFilePageDialogFragment

class GalleryPageDialogFragment : BaseFilePageDialogFragment() {

    override val tag_ = "GalleryPageDialog"
    override val title get() = getString(R.string.dialog_all_photos)
    override val statusPrefix get() = getString(R.string.dialog_photos_prefix)
    override val fileFilter = "image/*"
    override val filePickerTitle get() = getString(R.string.pick_photos_title)
    override val locationType = LocationType.GALLERY_PAGE
    override val layoutRes = R.layout.dialog_page_gallery
    override val recyclerViewId = R.id.galleryRecyclerView
    override val statusViewId = R.id.galleryStatus

    override suspend fun loadFilesFromStorage(): List<AppFile> {
        return requireContext().appContainer.filesRepository.getFilesForGallery()
    }

    companion object {
        fun newInstance() = GalleryPageDialogFragment()
    }
}
