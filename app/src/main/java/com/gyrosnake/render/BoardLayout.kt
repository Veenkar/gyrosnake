package com.gyrosnake.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.Point

/**
 * Value Object mapping between grid cells and canvas pixels.
 *
 * Extracted so the renderer and the touch layers share one definition of where
 * the board sits: an input layer that computed its own geometry would drift out
 * of alignment with what the player sees the moment either side changed.
 */
data class BoardLayout(
    val cellSize: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    /** Pixel centre of [point]'s cell. */
    fun centerOf(point: Point): Offset = Offset(
        offsetX + (point.x + 0.5f) * cellSize,
        offsetY + (point.y + 0.5f) * cellSize
    )
}

/**
 * Fits the board into [size], keeping cells square and centring any slack.
 * Mirrors the letterboxing the renderer applies.
 */
fun computeBoardLayout(size: Size, board: GameBoard): BoardLayout {
    val cell = minOf(size.width / board.columns, size.height / board.rows)
    return BoardLayout(
        cellSize = cell,
        offsetX  = (size.width  - cell * board.columns) / 2f,
        offsetY  = (size.height - cell * board.rows)    / 2f
    )
}
