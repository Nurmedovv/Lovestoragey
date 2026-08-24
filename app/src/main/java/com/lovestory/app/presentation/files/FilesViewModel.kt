package com.lovestory.app.presentation.files

import com.lovestory.app.domain.model.AppFile
import com.lovestory.app.di.appContainer
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FilesViewModel"
    }

    private val _files = MutableLiveData<List<AppFile>>(emptyList())
    val files: LiveData<List<AppFile>> = _files

    fun loadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = getApplication<Application>().appContainer.filesRepository.getFilesForFilesPage()
                withContext(Dispatchers.Main) {
                    _files.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load files", e)
            }
        }
    }

    fun addFile(file: AppFile) {
        val current = _files.value?.toMutableList() ?: mutableListOf()
        current.add(0, file)
        _files.value = current
    }

    fun clearFiles() {
        _files.value = emptyList()
    }
}
