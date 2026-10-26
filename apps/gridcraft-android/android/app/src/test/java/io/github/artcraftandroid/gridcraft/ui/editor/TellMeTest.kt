package io.github.artcraftandroid.gridcraft.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TellMeTest {
    private fun item(label: String, words: String = "") = TellMeItem(label, "Home", null, words) { }

    private val items = listOf(
        item("Hide Rows"),
        item("Unhide Rows", "show"),
        item("Fill Color", "background shading colour highlight"),
        item("Font Color", "text colour"),
        item("Freeze Panes", "lock top row first column"),
    )

    private fun names(q: String) = tellMeMatches(q, items).map { it.label }

    @Test
    fun names_starting_with_the_query_come_first() {
        // "Hide Rows" starts with it; "Unhide Rows" only contains it
        assertEquals(listOf("Hide Rows", "Unhide Rows"), names("hide"))
    }

    @Test
    fun other_words_find_a_command() {
        assertEquals(listOf("Freeze Panes"), names("lock"))
        assertEquals(listOf("Fill Color", "Font Color"), names("colour"))
        // every word typed has to match
        assertEquals(listOf("Fill Color"), names("colour shading"))
    }

    @Test
    fun nothing_typed_or_nothing_matching_finds_nothing() {
        assertTrue(names("   ").isEmpty())
        assertTrue(names("paragraph").isEmpty())
    }

    @Test
    fun each_command_has_its_own_name() {
        // recent picks are kept by name
        val labels = TELL_ME_ITEMS.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun arabic_labels_and_english_aliases_find_the_same_commands() {
        val labels = mapOf("Hide Rows" to "إخفاء الصفوف", "Unhide Rows" to "إظهار الصفوف", "Fill Color" to "لون التعبئة")
        assertEquals(listOf("Hide Rows"), tellMeMatches("إخفاء", items, labels).map { it.label })
        assertEquals(listOf("Fill Color"), tellMeMatches("لون التعبئة", items, labels).map { it.label })
        assertEquals(listOf("Hide Rows", "Unhide Rows"), tellMeMatches("hide", items, labels).map { it.label })
    }
}
