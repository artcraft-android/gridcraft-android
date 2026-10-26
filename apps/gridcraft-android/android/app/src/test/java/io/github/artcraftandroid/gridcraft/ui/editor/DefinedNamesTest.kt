package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DefinedNamesTest {
    @Test
    fun inserting_a_name_preserves_the_rest_of_the_formula() {
        val result = insertDefinedName(TextFieldValue("=SUM()+1", TextRange(5)), "المبيعات")
        assertEquals("=SUM(المبيعات)+1", result.text)
        assertEquals(TextRange(5 + "المبيعات".length), result.selection)
    }

    @Test
    fun reversed_selection_is_replaced_without_losing_the_suffix() {
        val result = insertDefinedName(TextFieldValue("=Old*2", TextRange(4, 1)), "Sales")
        assertEquals("=Sales*2", result.text)
        assertEquals(TextRange(6), result.selection)
    }
}
