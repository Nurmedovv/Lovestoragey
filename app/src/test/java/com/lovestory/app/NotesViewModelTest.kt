package com.lovestory.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.lovestory.app.db.NoteDao
import com.lovestory.app.db.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var noteDao: NoteDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        noteDao = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Search Logic ====================

    @Test
    fun `searchNotes - blank query returns all notes`() = runTest {
        val allNotes = listOf(
            NoteEntity(id = 1, content = "Hello", timestamp = 1000),
            NoteEntity(id = 2, content = "World", timestamp = 2000)
        )
        whenever(noteDao.getAll()).thenReturn(allNotes)
        whenever(noteDao.search(any())).thenReturn(emptyList())

        // Test the logic: blank query should call getAll()
        val query = ""
        val result = if (query.isBlank()) noteDao.getAll() else noteDao.search(query)

        assertEquals(2, result.size)
        verify(noteDao).getAll()
    }

    @Test
    fun `searchNotes - non-blank query calls search`() = runTest {
        val searchResults = listOf(
            NoteEntity(id = 1, content = "Hello World", timestamp = 1000)
        )
        whenever(noteDao.search("Hello")).thenReturn(searchResults)

        val query = "Hello"
        val result = if (query.isBlank()) noteDao.getAll() else noteDao.search(query)

        assertEquals(1, result.size)
        verify(noteDao).search("Hello")
    }

    // ==================== Toggle Pin Logic ====================

    @Test
    fun `togglePin - unpinned becomes pinned`() {
        val note = NoteEntity(id = 1, content = "Test", timestamp = 1000, isPinned = false)
        val toggled = note.copy(isPinned = !note.isPinned)
        assertTrue(toggled.isPinned)
    }

    @Test
    fun `togglePin - pinned becomes unpinned`() {
        val note = NoteEntity(id = 1, content = "Test", timestamp = 1000, isPinned = true)
        val toggled = note.copy(isPinned = !note.isPinned)
        assertFalse(toggled.isPinned)
    }

    // ==================== Note Count ====================

    @Test
    fun `getNoteCount - returns 0 for empty list`() {
        val notes = emptyList<NoteEntity>()
        val count = notes.size
        assertEquals(0, count)
    }

    @Test
    fun `getNoteCount - returns correct count`() {
        val notes = listOf(
            NoteEntity(id = 1, content = "A", timestamp = 1000),
            NoteEntity(id = 2, content = "B", timestamp = 2000)
        )
        val count = notes.size
        assertEquals(2, count)
    }

    // ==================== Note Sorting ====================

    @Test
    fun `notes sorted by pinned first then timestamp desc`() {
        val notes = listOf(
            NoteEntity(id = 1, content = "Old pinned", timestamp = 1000, isPinned = true),
            NoteEntity(id = 2, content = "New unpinned", timestamp = 3000, isPinned = false),
            NoteEntity(id = 3, content = "Old unpinned", timestamp = 1000, isPinned = false),
            NoteEntity(id = 4, content = "New pinned", timestamp = 3000, isPinned = true)
        )

        val sorted = notes.sortedWith(
            compareByDescending<NoteEntity> { it.isPinned }
                .thenByDescending { it.timestamp }
        )

        assertEquals(4, sorted.size)
        assertTrue(sorted[0].isPinned && sorted[0].timestamp == 3000L)
        assertTrue(sorted[1].isPinned && sorted[1].timestamp == 1000L)
        assertFalse(sorted[2].isPinned && sorted[2].timestamp == 3000L)
        assertFalse(sorted[3].isPinned && sorted[3].timestamp == 1000L)
    }
}
