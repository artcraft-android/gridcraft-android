package io.github.artcraftandroid.gridcraft.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLayoutTest {
    @Test
    fun colors_and_shapes_reduce_columns_on_narrow_windows() {
        assertEquals(6, galleryColumns(284f, 8, 44f))
        assertEquals(8, galleryColumns(375f, 8, 44f))
        assertEquals(7, galleryColumns(288f, 10, 39f, 2f))
        assertEquals(10, galleryColumns(700f, 10, 39f, 2f))
    }

    @Test
    fun complete_tiles_and_gaps_fit_the_available_width() {
        for (width in listOf(80f, 180f, 288f, 375f, 600f, 900f)) {
            for ((maximum, tile, gap) in listOf(Triple(10, 39f, 2f), Triple(8, 44f, 0f), Triple(7, 44f, 4f))) {
                val columns = galleryColumns(width, maximum, tile, gap)
                assertTrue(columns in 1..maximum)
                assertTrue(columns * tile + (columns - 1) * gap <= width)
            }
        }
        assertEquals(1, galleryColumns(0f, 8, 44f))
        assertEquals(1, galleryColumns(20f, 8, 44f))
    }
}
