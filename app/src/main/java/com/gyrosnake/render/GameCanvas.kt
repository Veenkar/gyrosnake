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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.gyrosnake.game.ActiveEffect
import com.gyrosnake.game.Food
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.PowerUpEffect
import com.gyrosnake.game.SnakeState

// --- Retro colour palette ---
private val BG          = Color(0xFF0D0D0D)
private val GRID_LINE   = Color(0xFF1A2A1A)
private val BORDER      = Color(0xFF003300)
private val BORDER_GLOW = Color(0xFF005500)
private val HEAD_COLOR       = Color(0xFF00FF55)
private val BODY_COLOR       = Color(0xFF00AA33)
private val TAIL_DIM         = Color(0xFF006622)
private val HEAD_COLOR_CANDY  = Color(0xFFFF69B4)
private val BODY_COLOR_CANDY  = Color(0xFFCC3377)
private val TAIL_DIM_CANDY    = Color(0xFF882255)
private val EYE_COLOR   = Color(0xFF000000)
private val FOOD_COLOR  = Color(0xFFFF4400)
private val SCAN_LINE   = Color(0x0A000000)

/**
 * Pure rendering composable — Renderer / Visitor pattern.
 * Draws one game frame using Compose Canvas (hardware-accelerated 2D).
 * No game logic lives here; it only reads from [snake] and [foods].
 *
 * Food rendering uses the Visitor-like dispatch on [Food.effect]:
 * null → normal apple, non-null → delegates to the effect-specific renderer.
 * Adding a new power-up only requires a new branch in [drawFoods].
 */
@Composable
fun GameCanvas(
    snake: SnakeState?,
    foods: List<Food>,
    board: GameBoard,
    activeEffects: List<ActiveEffect> = emptyList(),
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "canvas")
    // Normal food pulse
    val foodPulse by inf.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "foodAlpha"
    )

    // Disco powerup color phase — cycles 0→1 continuously for rainbow dot animation
    val discoPhase by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discoPhase"
    )

    val isCandyActive = activeEffects.any { it.effect is PowerUpEffect.Candy }

    Canvas(modifier = modifier.fillMaxSize()) {
        val (cellSize, offsetX, offsetY) = computeLayout(board)
        drawBackground(offsetX, offsetY, board, cellSize)
        drawBorder(offsetX, offsetY, board, cellSize)
        snake?.let { drawSnake(it, offsetX, offsetY, cellSize, isCandyActive) }
        drawFoods(foods, offsetX, offsetY, cellSize, foodPulse, discoPhase)
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
    for (x in 0..board.columns) {
        drawLine(GRID_LINE, Offset(ox + x * cell, oy), Offset(ox + x * cell, oy + board.rows * cell), strokeWidth = 1f)
    }
    for (y in 0..board.rows) {
        drawLine(GRID_LINE, Offset(ox, oy + y * cell), Offset(ox + board.columns * cell, oy + y * cell), strokeWidth = 1f)
    }
}

private fun DrawScope.drawBorder(ox: Float, oy: Float, board: GameBoard, cell: Float) {
    val w = board.columns * cell
    val h = board.rows    * cell
    drawRect(BORDER_GLOW, Offset(ox - 4f, oy - 4f), Size(w + 8f, h + 8f), style = Stroke(4f))
    drawRect(BORDER,      Offset(ox - 2f, oy - 2f), Size(w + 4f, h + 4f), style = Stroke(2f))
}

private fun DrawScope.drawSnake(snake: SnakeState, ox: Float, oy: Float, cell: Float, candyActive: Boolean = false) {
    val pad = cell * 0.1f
    val headCol = if (candyActive) HEAD_COLOR_CANDY else HEAD_COLOR
    val bodyCol = if (candyActive) BODY_COLOR_CANDY else BODY_COLOR
    val tailCol = if (candyActive) TAIL_DIM_CANDY   else TAIL_DIM
    snake.body.forEachIndexed { index, seg ->
        val color = when (index) {
            0    -> headCol
            else -> lerpColor(bodyCol, tailCol, index.toFloat() / snake.length.toFloat())
        }
        drawRect(color = color, topLeft = Offset(ox + seg.x * cell + pad, oy + seg.y * cell + pad), size = Size(cell - pad * 2, cell - pad * 2))
    }
    val head  = snake.body.first()
    val eyePad = cell * 0.25f
    val eyeR  = cell * 0.12f
    val hx = ox + head.x * cell
    val hy = oy + head.y * cell
    drawCircle(EYE_COLOR, eyeR, Offset(hx + eyePad,          hy + eyePad))
    drawCircle(EYE_COLOR, eyeR, Offset(hx + cell - eyePad,   hy + eyePad))
}

/**
 * Dispatcher — routes each food to its type-specific renderer.
 * Open/Closed: new power-up types add a branch here without changing other rendering code.
 */
private fun DrawScope.drawFoods(
    foods: List<Food>,
    ox: Float, oy: Float,
    cell: Float,
    pulse: Float,
    discoPhase: Float
) {
    foods.forEach { food ->
        when (food.effect) {
            null                 -> drawNormalFood(food, ox, oy, cell, pulse)
            is PowerUpEffect.Disco -> drawDiscoPowerup(food, ox, oy, cell, discoPhase)
            is PowerUpEffect.Candy -> drawCandyPowerup(food, ox, oy, cell)
        }
    }
}

private fun DrawScope.drawNormalFood(food: Food, ox: Float, oy: Float, cell: Float, pulse: Float) {
    val pad = cell * 0.15f
    val fx  = ox + food.position.x * cell
    val fy  = oy + food.position.y * cell
    drawRect(color = FOOD_COLOR.copy(alpha = pulse * 0.4f), topLeft = Offset(fx, fy), size = Size(cell, cell))
    drawRect(color = FOOD_COLOR.copy(alpha = pulse),        topLeft = Offset(fx + pad, fy + pad), size = Size(cell - pad * 2, cell - pad * 2))
}

/**
 * Disco powerup sprite: a small square divided into a 3×3 grid of rainbow-colored dots.
 * The [discoPhase] (0→1 looping) cycles the hue offset so colors rotate continuously,
 * giving a pulsing appearance distinct from any normal food.
 */
private fun DrawScope.drawDiscoPowerup(food: Food, ox: Float, oy: Float, cell: Float, discoPhase: Float) {
    val fx   = ox + food.position.x * cell
    val fy   = oy + food.position.y * cell
    val pad  = cell * 0.08f
    val grid = 3
    val dotSize = (cell - pad * 2) / grid

    // Outer white frame
    drawRect(Color.White.copy(alpha = 0.9f), Offset(fx + pad, fy + pad), Size(cell - pad * 2, cell - pad * 2), style = Stroke(1.5f))

    // 3×3 rainbow dot grid — each dot's hue is offset by position + discoPhase (powerup color cycle)
    for (row in 0 until grid) {
        for (col in 0 until grid) {
            val hue = ((col + row * grid).toFloat() / (grid * grid) + discoPhase) % 1f * 360f
            val color = Color.hsv(hue, 1f, 1f)
            val dx = fx + pad + col * dotSize + dotSize * 0.15f
            val dy = fy + pad + row * dotSize + dotSize * 0.15f
            drawRect(color, Offset(dx, dy), Size(dotSize * 0.7f, dotSize * 0.7f))
        }
    }

    // Bright outer glow
    drawRect(Color.White.copy(alpha = 0.25f), Offset(fx, fy), Size(cell, cell))
}

/**
 * Candy powerup sprite: a blue circle with a smiley face.
 */
private fun DrawScope.drawCandyPowerup(food: Food, ox: Float, oy: Float, cell: Float) {
    val cx = ox + food.position.x * cell + cell / 2f
    val cy = oy + food.position.y * cell + cell / 2f
    val radius = cell * 0.42f

    drawCircle(Color(0xFF1A6EFF), radius, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.25f), radius + 2f, Offset(cx, cy), style = Stroke(2f))

    // Eyes
    val eyeR   = radius * 0.13f
    val eyeOffX = radius * 0.35f
    val eyeOffY = radius * 0.25f
    drawCircle(Color.White, eyeR, Offset(cx - eyeOffX, cy - eyeOffY))
    drawCircle(Color.White, eyeR, Offset(cx + eyeOffX, cy - eyeOffY))

    // Smile arc via quadratic path
    val smileW = radius * 0.55f
    val smileY = cy + radius * 0.15f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - smileW, smileY)
        quadraticBezierTo(cx, smileY + radius * 0.38f, cx + smileW, smileY)
    }
    drawPath(path, Color.White, style = Stroke(width = radius * 0.14f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

private fun DrawScope.drawScanlines() {
    var y = 0f
    while (y < size.height) {
        drawLine(SCAN_LINE, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        y += 4f
    }
}

// --- Colour helpers ---

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
