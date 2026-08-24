package com.lovestory.app.presentation.notes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lovestory.app.db.NoteEntity
import java.text.SimpleDateFormat
import java.util.*
import com.lovestory.app.R
import com.lovestory.app.presentation.common.GlassEffectHelper
import com.lovestory.app.presentation.common.FontColorHelper

class NotesAdapter(
    initialNotes: List<NoteEntity>,
    private val onNoteAction: (NoteEntity, NoteAction) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    private val notes: MutableList<NoteEntity> = initialNotes.toMutableList()

    enum class NoteAction {
        EDIT, DELETE, PIN
    }

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteContent: TextView = itemView.findViewById(R.id.noteContent)
        val noteDate: TextView = itemView.findViewById(R.id.noteDate)
        val pinIndicator: ImageView? = itemView.findViewById(R.id.pinIndicator)
        val editButton: ImageView = itemView.findViewById(R.id.editButton)
        val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]

        GlassEffectHelper.refreshRoot(holder.itemView)
        FontColorHelper.refreshRoot(holder.itemView)

        holder.noteContent.text = note.content

        val date = Date(note.timestamp)
        holder.noteDate.text = dateFormat.format(date)

        holder.pinIndicator?.setImageResource(
            if (note.isPinned) R.drawable.ic_star_filled
            else R.drawable.ic_star_outline
        )

        holder.editButton.setOnClickListener {
            onNoteAction(note, NoteAction.EDIT)
        }

        holder.deleteButton.setOnClickListener {
            onNoteAction(note, NoteAction.DELETE)
        }

        holder.pinIndicator?.setOnClickListener {
            onNoteAction(note, NoteAction.PIN)
        }

        holder.itemView.setOnClickListener {
            onNoteAction(note, NoteAction.EDIT)
        }
    }

    override fun getItemCount() = notes.size

    fun updateNotes(newNotes: List<NoteEntity>) {
        notes.clear()
        notes.addAll(newNotes)
        notifyDataSetChanged()
    }
}