package com.lovestory.app.presentation.notes

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lovestory.app.databinding.FragmentNotesBinding
import com.lovestory.app.db.NoteEntity
import com.lovestory.app.R
import com.lovestory.app.presentation.notes.NotesAdapter
import com.lovestory.app.presentation.notes.NotesViewModel
import com.lovestory.app.presentation.common.BaseThemeFragment
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper

class NotesFragment : BaseThemeFragment<FragmentNotesBinding>() {

    private val viewModel: NotesViewModel by viewModels()
    private lateinit var notesAdapter: NotesAdapter

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentNotesBinding {
        return FragmentNotesBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, isDarkTheme: Boolean) {
        GlassEffectHelper.applyToRoot(binding.root)
        FontColorHelper.applyToRoot(binding.root)
        setupNotesPage()
        setupRecyclerView()
        setupObservers()
        setupSearch()
        viewModel.loadNotes()
    }

    override fun onResume() {
        super.onResume()
        FontColorHelper.applyToRoot(binding.root)
    }

    override fun applyTheme(isDarkTheme: Boolean) {

        binding.notesTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        binding.searchInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        binding.searchInput.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint_light))
        binding.noteInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        binding.noteInput.setHintTextColor(ContextCompat.getColor(requireContext(), if (isDarkTheme) R.color.text_hint_light else R.color.text_hint_dark))
        binding.addNoteButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        binding.saveEditButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        binding.cancelEditButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        FontColorHelper.refreshRoot(binding.root)
    }

    override fun onGlassChanged() {
        GlassEffectHelper.refreshRoot(binding.root)
        if (::notesAdapter.isInitialized) notesAdapter.notifyDataSetChanged()
    }

    override fun onFontColorChanged() {
        FontColorHelper.refreshRoot(binding.root)
        if (::notesAdapter.isInitialized) notesAdapter.notifyDataSetChanged()
    }

    private fun setupNotesPage() {
        binding.notesTitle.text = getString(R.string.notes_title)
        binding.noteInput.hint = getString(R.string.note_hint_add)
        binding.noteInput.setOnClickListener { showKeyboard() }
        binding.noteInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                if (viewModel.currentEditingNote.value != null) saveEditedNote() else addNewNote()
                true
            } else false
        }
        binding.addNoteButton.setOnClickListener { addNewNote() }
        binding.saveEditButton.setOnClickListener { saveEditedNote() }
        binding.cancelEditButton.setOnClickListener { cancelEditing() }
        showAddMode()
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(emptyList()) { note, action ->
            when (action) {
                NotesAdapter.NoteAction.EDIT -> editNote(note)
                NotesAdapter.NoteAction.DELETE -> deleteNote(note)
                NotesAdapter.NoteAction.PIN -> togglePin(note)
            }
        }

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notesAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            notesAdapter.updateNotes(notes)
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                viewModel.searchNotes(query)
            }
        })
    }

    private fun addNewNote() {
        val noteText = binding.noteInput.text.toString().trim()

        if (noteText.isNotEmpty()) {
            viewModel.addNote(noteText)
            binding.noteInput.text.clear()
            showToast(getString(R.string.note_saved_toast))
            binding.notesRecyclerView.scrollToPosition(0)
            hideKeyboard()
        } else {
            showToast(getString(R.string.note_empty_warning))
        }
    }

    private fun editNote(note: NoteEntity) {
        viewModel.setCurrentEditingNote(note)

        binding.noteInput.setText(note.content)

        showEditMode()

        binding.noteInput.requestFocus()
        binding.noteInput.setSelection(binding.noteInput.text.length)
        showKeyboard()

        showToast(getString(R.string.note_editing_toast))
    }

    private fun saveEditedNote() {
        val noteText = binding.noteInput.text.toString().trim()
        val editingNote = viewModel.currentEditingNote.value

        if (noteText.isNotEmpty() && editingNote != null) {
            val updatedNote = editingNote.copy(content = noteText)
            viewModel.updateNote(updatedNote)

            binding.noteInput.text.clear()
            cancelEditing()

            showToast(getString(R.string.note_updated_toast))
        } else {
            showToast(getString(R.string.note_empty_error))
        }
    }

    private fun deleteNote(note: NoteEntity) {
        viewModel.deleteNote(note)

        if (viewModel.currentEditingNote.value?.id == note.id) {
            cancelEditing()
        }

        Snackbar.make(binding.root, getString(R.string.note_deleted), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.undo)) {
                viewModel.addNote(note.content)
            }
            .show()
    }

    private fun togglePin(note: NoteEntity) {
        viewModel.togglePin(note)
    }

    private fun cancelEditing() {
        viewModel.setCurrentEditingNote(null)
        binding.noteInput.text.clear()
        showAddMode()
        hideKeyboard()
    }

    private fun showAddMode() {
        binding.addNoteButton.visibility = View.VISIBLE
        binding.saveEditButton.visibility = View.GONE
        binding.cancelEditButton.visibility = View.GONE
        binding.noteInput.hint = getString(R.string.note_hint_add)
    }

    private fun showEditMode() {
        binding.addNoteButton.visibility = View.GONE
        binding.saveEditButton.visibility = View.VISIBLE
        binding.cancelEditButton.visibility = View.VISIBLE
        binding.noteInput.hint = getString(R.string.note_hint_edit)
    }

    private fun showKeyboard() {
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.noteInput.requestFocus()
        inputMethodManager.showSoftInput(binding.noteInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.noteInput.windowToken, 0)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        hideKeyboard()
    }
}
