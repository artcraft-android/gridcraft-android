package io.github.artcraftandroid.gridcraft.ui.editor

import kotlin.math.floor

/** Fit complete tiles inside the available content width, including gaps between tiles. */
internal fun galleryColumns(width: Float, maximum: Int, minimumTile: Float, gap: Float = 0f): Int {
    require(maximum > 0 && minimumTile > 0f && gap >= 0f)
    if (!width.isFinite() || width <= 0f) return 1
    return floor((width + gap) / (minimumTile + gap)).toInt().coerceIn(1, maximum)
}
