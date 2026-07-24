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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.gyrosnake.game.Direction
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.SnakeState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val POINT_GREEN = Color(0xFF00FF55)

// A press shorter than this that barely moves is treated as a tap (pause)
// rather than a steer, so the whole board stays tappable.
private const val TAP_MAX_MS       = 180L
private const val TAP_SLOP_PX      = 24f
// Below this distance the finger is effectively on the head; steering from
// such a short vector would jitter between axes, so it is ignored.
private const val MIN_STEER_PX     = 36f
// Steering is re-sent on this cadence, comfortably under the engine's tick.
private const val STEER_INTERVAL_MS = 50L

/**
 * Point-and-go control layer: the snake turns toward wherever the finger rests.
 *
 * Covers the whole board, so it also has to preserve tap-to-pause. It resolves
 * the ambiguity by duration and travel: a short, stationary press is forwarded
 * to [onTap]; anything longer or moving is a steer and is consumed.
 *
 * Steering picks the dominant axis of (finger - head), which is what makes the
 * scheme feel direct: the snake commits to the larger component first and
 * naturally switches axis as it closes in. Reversals are rejected downstream by
 * SnakeState, so no special-casing is needed here.
 */
@Composable
fun PointAndGoLayer(
    snake: SnakeState?,
    board: GameBoard,
    onDirection: (Direction) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Null while nothing is pressed — drives both steering and the visuals.
    var finger by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Read through so the steering loop below always sees the live snake
    // rather than the one captured when the effect started.
    val currentSnake by rememberUpdatedState(snake)

    // Steering has to be re-sent on a timer, not on touch events: a finger held
    // still produces no events, and GameEngine consumes pendingDirection once
    // per tick. Event-driven steering therefore turned exactly once and then let
    // the snake run straight.
    LaunchedEffect(board) {
        while (true) {
            delay(STEER_INTERVAL_MS)
            val target = finger ?: continue
            val head   = currentSnake?.head ?: continue
            if (canvasSize == IntSize.Zero) continue
            val layout = computeBoardLayout(canvasSize.toSize(), board)
            steerToward(layout.centerOf(head), target)?.let(onDirection)
        }
    }

    val transition = rememberInfiniteTransition(label = "pointAndGo")
    // Drives the arrows travelling head -> finger; one full cycle per period.
    val flow by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label         = "flow"
    )
    val pulse by transition.animateFloat(
        initialValue  = 0.75f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label         = "pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(board) {
                awaitPointerEventScope {
                    while (true) {
                        // Wait for a press, remembering where and when it started.
                        var change = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (!change.pressed) {
                            finger = null
                            continue
                        }
                        val startPos  = change.position
                        val startTime = System.currentTimeMillis()
                        var travelled = 0f

                        // Track the finger until release. Steering itself is done
                        // by the timer loop above, which keeps going even while
                        // the finger is perfectly still and emits no events.
                        while (change.pressed) {
                            finger = change.position
                            travelled = maxOf(travelled, (change.position - startPos).getDistance())
                            change.consume()
                            change = awaitPointerEvent().changes.firstOrNull() ?: break
                        }

                        finger = null
                        val heldMs = System.currentTimeMillis() - startTime
                        if (heldMs <= TAP_MAX_MS && travelled <= TAP_SLOP_PX) onTap()
                        change.consume()
                    }
                }
            }
    ) {
        val target = finger ?: return@Canvas
        val head = snake?.head ?: return@Canvas
        drawSteerStream(
            from  = computeBoardLayout(size, board).centerOf(head),
            to    = target,
            flow  = flow
        )
        drawFingerHighlight(target, pulse)
    }
}

/**
 * Dominant-axis resolution of the vector from [head] to [finger].
 * Returns null when the finger is too close to the head to give a stable answer.
 */
private fun steerToward(head: Offset, finger: Offset): Direction? {
    val dx = finger.x - head.x
    val dy = finger.y - head.y
    if (hypot(dx, dy) < MIN_STEER_PX) return null
    return if (abs(dx) >= abs(dy)) {
        if (dx > 0) Direction.RIGHT else Direction.LEFT
    } else {
        if (dy > 0) Direction.DOWN else Direction.UP
    }
}

/**
 * Faint chevrons drifting from the snake's head toward the finger, showing the
 * steering vector without competing with the game for attention. Chevrons fade
 * in at the head and out at the finger so the stream has no hard ends.
 */
private fun DrawScope.drawSteerStream(from: Offset, to: Offset, flow: Float) {
    val dx   = to.x - from.x
    val dy   = to.y - from.y
    val dist = hypot(dx, dy)
    if (dist < MIN_STEER_PX) return

    val angle   = atan2(dy, dx)
    val spacing = 46f
    val count   = (dist / spacing).toInt().coerceIn(1, 14)
    val size    = 9f

    for (i in 0 until count) {
        // Each chevron sits at a fixed fraction of the way along, shifted by the
        // animation so the whole set slides toward the finger and recycles.
        val t = ((i + flow) / count).let { if (it > 1f) it - 1f else it }
        val x = from.x + dx * t
        val y = from.y + dy * t

        // Fade at both ends of the run.
        val alpha = 0.30f * when {
            t < 0.15f -> t / 0.15f
            t > 0.85f -> (1f - t) / 0.15f
            else      -> 1f
        }

        val nose = Offset(x + cos(angle) * size, y + sin(angle) * size)
        val left = Offset(
            x + cos(angle + 2.4f) * size,
            y + sin(angle + 2.4f) * size
        )
        val right = Offset(
            x + cos(angle - 2.4f) * size,
            y + sin(angle - 2.4f) * size
        )
        drawPath(
            path  = Path().apply {
                moveTo(left.x, left.y)
                lineTo(nose.x, nose.y)
                lineTo(right.x, right.y)
            },
            color = POINT_GREEN.copy(alpha = alpha),
            style = Stroke(width = 2.5f)
        )
    }
}

/** Soft round glow under the finger, marking the point the snake is heading for. */
private fun DrawScope.drawFingerHighlight(center: Offset, pulse: Float) {
    val r = 34f * pulse
    drawCircle(POINT_GREEN.copy(alpha = 0.10f), r * 1.6f, center)
    drawCircle(POINT_GREEN.copy(alpha = 0.18f), r,        center)
    drawCircle(POINT_GREEN.copy(alpha = 0.55f), r,        center, style = Stroke(width = 2f))
}
