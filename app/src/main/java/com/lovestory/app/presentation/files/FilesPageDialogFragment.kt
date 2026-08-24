package com.lovestory.app.presentation.files

import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.domain.model.LocationType
import com.lovestory.app.R
import com.lovestory.app.di.appContainer
import com.lovestory.app.presentation.common.BaseFilePageDialogFragment

class FilesPageDialogFragment : BaseFilePageDialogFragment() {

    override val tag_ = "FilesPageDialog"
    override val title get() = getString(R.string.dialog_all_files)
    override val statusPrefix get() = getString(R.string.dialog_files_prefix)
    override val fileFilter = "*/*"
    override val filePickerTitle get() = getString(R.string.pick_files_title)
    override val locationType = LocationType.FILES_PAGE
    override val layoutRes = R.layout.dialog_page_files
    override val recyclerViewId = R.id.filesRecyclerView
    override val statusViewId = R.id.filesStatus

    override suspend fun loadFilesFromStorage(): List<AppFile> {
        return requireContext().appContainer.filesRepository.getFilesForFilesPage()
    }

    companion object {
        fun newInstance() = FilesPageDialogFragment()
    }
}
