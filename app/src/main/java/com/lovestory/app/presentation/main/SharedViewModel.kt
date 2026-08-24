package com.lovestory.app.presentation.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

    private val _slideShowToggle = MutableLiveData<Boolean>()
    val slideShowToggle: LiveData<Boolean> = _slideShowToggle

    fun triggerSlideShowToggle() {
        _slideShowToggle.value = _slideShowToggle.value != true
    }

    private val _filesChanged = MutableLiveData<Boolean>()
    val filesChanged: LiveData<Boolean> = _filesChanged

    fun triggerFilesChanged() {
        _filesChanged.value = true
    }
}
