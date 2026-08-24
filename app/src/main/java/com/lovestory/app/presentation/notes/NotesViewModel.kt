package com.lovestory.app.presentation.notes

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lovestory.app.db.AppDatabase
import com.lovestory.app.db.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NotesViewModel"
    }

    private val noteDao = AppDatabase.getDatabase(application).noteDao()

    private val _notes = MutableLiveData<List<NoteEntity>>(emptyList())
    val notes: LiveData<List<NoteEntity>> = _notes

    private val _currentEditingNote = MutableLiveData<NoteEntity?>()
    val currentEditingNote: LiveData<NoteEntity?> = _currentEditingNote

    fun loadNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = noteDao.getAll()
                withContext(Dispatchers.Main) {
                    _notes.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load notes", e)
            }
        }
    }

    fun addNote(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val note = NoteEntity(
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
                noteDao.insert(note)
                loadNotes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add note", e)
            }
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteDao.update(note)
                loadNotes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update note", e)
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteDao.delete(note)
                loadNotes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete note", e)
            }
        }
    }

    fun setCurrentEditingNote(note: NoteEntity?) {
        _currentEditingNote.value = note
    }

    fun searchNotes(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = if (query.isBlank()) {
                    noteDao.getAll()
                } else {
                    noteDao.search(query)
                }
                withContext(Dispatchers.Main) {
                    _notes.value = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search notes", e)
            }
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedNote = note.copy(isPinned = !note.isPinned)
                noteDao.update(updatedNote)
                loadNotes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle pin", e)
            }
        }
    }
}
