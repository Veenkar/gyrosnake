package com.gyrosnake.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.gyrosnake.game.Food
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.SnakeState

// --- Retro colour palette ---
private val BG          = Color(0xFF0D0D0D)
private val GRID_LINE   = Color(0xFF1A2A1A)
private val BORDER      = Color(0xFF003300)
private val BORDER_GLOW = Color(0xFF005500)
private val HEAD_COLOR  = Color(0xFF00FF55)
private val BODY_COLOR  = Color(0xFF00AA33)
private val TAIL_DIM    = Color(0xFF006622)
private val EYE_COLOR   = Color(0xFF000000)
private val FOOD_COLOR  = Color(0xFFFF4400)
private val SCAN_LINE   = Color(0x0A000000)

/**
 * Pure rendering composable — Renderer pattern.
 * Draws one game frame using Compose Canvas (hardware-accelerated 2D).
 * No game logic lives here; it only reads from [snake] and [foods].
 *
 * The retro aesthetic is achieved through:
 *   - Dark background with subtle grid lines
 *   - Glowing green border (CRT phosphor effect via layered rects)
 *   - Pulsing food sprite (animated alpha)
 *   - Horizontal scanline overlay (CRT simulation)
 *   - Snake with gradient shade from head to tail
 */
@Composable
fun GameCanvas(
    snake: SnakeState?,
    foods: List<Food>,
    board: GameBoard,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for food pellet
    val inf = rememberInfiniteTransition(label = "food")
    val foodPulse by inf.animateFloat(
        initialValue = 0.55f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation    = tween(450, easing = LinearEasing),
            repeatMode   = RepeatMode.Reverse
        ),
        label = "foodAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val (cellSize, offsetX, offsetY) = computeLayout(board)

        drawBackground(offsetX, offsetY, board, cellSize)
        drawBorder(offsetX, offsetY, board, cellSize)
        snake?.let { drawSnake(it, offsetX, offsetY, cellSize) }
        drawFoods(foods, offsetX, offsetY, cellSize, foodPulse)
        drawScanlines()
    }
}

// --- Layout helper ---

private data class Layout(val cellSize: Float, val offsetX: Float, val offsetY: Float)

private fun DrawScope.computeLayout(board: GameBoard): Layout {
    val cellW = size.width  / board.columns
    val cellH = size.height / board.rows
    val cell  = minOf(cellW, cellH)
    return Layout(
        cellSize = cell,
        offsetX  = (size.width  - cell * board.columns) / 2f,
        offsetY  = (size.height - cell * board.rows)    / 2f
    )
}

// --- Draw primitives ---

private fun DrawScope.drawBackground(ox: Float, oy: Float, board: GameBoard, cell: Float) {
    drawRect(BG)
    // Subtle grid lines inside the play area
    for (x in 0..board.columns) {
        drawLine(
            GRID_LINE,
            Offset(ox + x * cell, oy),
            Offset(ox + x * cell, oy + board.rows * cell),
            strokeWidth = 1f
        )
    }
    for (y in 0..board.rows) {
        drawLine(
            GRID_LINE,
            Offset(ox, oy + y * cell),
            Offset(ox + board.columns * cell, oy + y * cell),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawBorder(ox: Float, oy: Float, board: GameBoard, cell: Float) {
    val w = board.columns * cell
    val h = board.rows    * cell
    // Outer glow layer
    drawRect(BORDER_GLOW, Offset(ox - 4f, oy - 4f), Size(w + 8f, h + 8f), style = Stroke(4f))
    // Inner border
    drawRect(BORDER, Offset(ox - 2f, oy - 2f), Size(w + 4f, h + 4f), style = Stroke(2f))
}

private fun DrawScope.drawSnake(snake: SnakeState, ox: Float, oy: Float, cell: Float) {
    val pad = cell * 0.1f
    snake.body.forEachIndexed { index, seg ->
        val color = when (index) {
            0    -> HEAD_COLOR
            else -> lerpColor(BODY_COLOR, TAIL_DIM, index.toFloat() / snake.length.toFloat())
        }
        drawRect(
            color     = color,
            topLeft   = Offset(ox + seg.x * cell + pad, oy + seg.y * cell + pad),
            size      = Size(cell - pad * 2, cell - pad * 2)
        )
    }
    // Eyes on head
    val head  = snake.body.first()
    val eyePad = cell * 0.25f
    val eyeR  = cell * 0.12f
    val hx = ox + head.x * cell
    val hy = oy + head.y * cell
    drawCircle(EYE_COLOR, eyeR, Offset(hx + eyePad, hy + eyePad))
    drawCircle(EYE_COLOR, eyeR, Offset(hx + cell - eyePad, hy + eyePad))
}

private fun DrawScope.drawFoods(foods: List<Food>, ox: Float, oy: Float, cell: Float, pulse: Float) {
    val pad = cell * 0.15f
    foods.forEach { food ->
        val fx = ox + food.position.x * cell
        val fy = oy + food.position.y * cell
        // Outer glow
        drawRect(
            color   = FOOD_COLOR.copy(alpha = pulse * 0.4f),
            topLeft = Offset(fx, fy),
            size    = Size(cell, cell)
        )
        // Core pellet
        drawRect(
            color   = FOOD_COLOR.copy(alpha = pulse),
            topLeft = Offset(fx + pad, fy + pad),
            size    = Size(cell - pad * 2, cell - pad * 2)
        )
    }
}

private fun DrawScope.drawScanlines() {
    var y = 0f
    while (y < size.height) {
        drawLine(SCAN_LINE, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        y += 4f
    }
}

// --- Colour interpolation helper ---

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
