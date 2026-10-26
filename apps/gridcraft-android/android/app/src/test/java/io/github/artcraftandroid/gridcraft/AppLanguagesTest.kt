package io.github.artcraftandroid.gridcraft

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguagesTest {
    @Test
    fun explicit_supported_languages_are_retained() {
        assertEquals("ar", AppLanguages.supportedChoice("ar"))
        assertEquals("en", AppLanguages.supportedChoice("en"))
    }

    @Test
    fun system_default_and_invalid_saved_choices_follow_the_system() {
        for (value in listOf(null, "", "system", "fr", "invalid")) {
            assertEquals("system", AppLanguages.supportedChoice(value))
        }
    }
}
