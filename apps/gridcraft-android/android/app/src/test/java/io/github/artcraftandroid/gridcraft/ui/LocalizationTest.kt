package io.github.artcraftandroid.gridcraft.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {
    private fun strings(folder: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (file in File("src/main/res/$folder").listFiles().orEmpty().filter { it.extension == "xml" }) {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val key = node.attributes.getNamedItem("name").nodeValue
                assertTrue("Duplicate resource $key", key !in values)
                values[key] = node.textContent
            }
        }
        return values
    }

    @Test
    fun arabic_resources_cover_the_interface_and_keep_format_arguments() {
        val english = strings("values")
        val arabic = strings("values-ar")
        assertTrue("No resources found", english.size > 600)
        assertEquals(english.keys, arabic.keys)
        val arguments = Regex("%[0-9]+\\$[ds]")
        for ((key, value) in english) {
            val translated = arabic.getValue(key)
            assertTrue("Empty translation: $key", translated.isNotBlank())
            assertEquals("Format arguments: $key", arguments.findAll(value).map { it.value }.sorted().toList(), arguments.findAll(translated).map { it.value }.sorted().toList())
            if (key != "app_name") assertTrue("Missing Arabic: $key", translated.any { it in '\u0600'..'\u06ff' })
        }
    }
}
