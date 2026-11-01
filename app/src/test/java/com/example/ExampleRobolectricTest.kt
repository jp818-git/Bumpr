package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Bumpr", appName)
    }

    @Test
    fun `test lrc parser with multiple timestamps`() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:01.00] First Line
            [00:03.50] Second Line
            [00:06.00] Third Line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals("Test Song", parsed.title)
        assertEquals("Test Artist", parsed.artist)
        assertEquals(3, parsed.lines.size)
        assertTrue(parsed.isSynced)

        // Test binary search lookup
        val active0 = LrcParser.findActiveLineIndex(parsed.lines, 500L)
        assertEquals(0, active0)

        val active1 = LrcParser.findActiveLineIndex(parsed.lines, 2000L)
        assertEquals(0, active1)

        val active2 = LrcParser.findActiveLineIndex(parsed.lines, 4000L)
        assertEquals(1, active2)

        val active3 = LrcParser.findActiveLineIndex(parsed.lines, 7000L)
        assertEquals(2, active3)
    }

    @Test
    fun `test track folder filtering logic`() {
        val tracks = listOf(
            com.example.model.Track("1", "Song A", "Artist A", "Album A", 1000L, "", folderPath = "Music"),
            com.example.model.Track("2", "Song B", "Artist B", "Album B", 1000L, "", folderPath = "Downloads")
        )
        val selectedFolders = setOf("Music")
        val filtered = tracks.filter { selectedFolders.contains(it.folderPath) }
        assertEquals(1, filtered.size)
        assertEquals("Song A", filtered.first().title)

        val nonMatchingSelection = setOf("Podcasts")
        val noneAvailable = tracks.filter { nonMatchingSelection.contains(it.folderPath) }
        assertEquals(0, noneAvailable.size)
    }
}
