package com.gyrosnake.game

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the wall-wrap food bug: tick() used to resolve the food
 * lookup against an unwrapped head position, so the bite taken on the step that
 * crosses a wall was silently skipped.
 *
 * The engine is pure Kotlin with no Android imports, so these run on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineWrapTest {

    // 10x5 board: EntityFactory.createSnake puts the head at (5, 2) facing RIGHT,
    // with the body trailing to (1, 2). Column 9 is the last one before the wall.
    private val board = GameBoard(columns = 10, rows = 5)

    /**
     * Deterministic replacement for EntityFactory.spawnFood: hands out [positions]
     * in order, then parks any further food far off the snake's path so it cannot
     * interfere with the assertion.
     */
    private class ScriptedSpawner(private vararg val positions: Point) {
        private var index = 0
        val asLambda: (GameBoard, SnakeState, List<Food>, Int) -> Food? =
            { _, _, _, _ ->
                val p = positions.getOrNull(index) ?: Point(5, 0)
                index++
                Food(p, effect = null)
            }
    }

    @Test
    fun `food directly past the wall is eaten on the wrapping step`() = runTest {
        // Food sits at column 0 — the cell the head lands on after wrapping.
        val engine = GameEngine(
            board     = board,
            spawnFood = ScriptedSpawner(Point(0, 2)).asLambda
        )

        engine.startGame(backgroundScope)

        // Head walks 5 -> 9 (four ticks); the fifth wraps it onto (0, 2) and eats.
        // advanceTimeBy runs tasks scheduled strictly before the new time, so the
        // +1 is needed to include the 5th tick at t=1750. Stopping one millisecond
        // later also keeps the 6th out: eating drops the interval to 347ms, which
        // would otherwise schedule it at 2097 and carry the head off the food cell.
        advanceTimeBy(350L * 5 + 1)

        assertEquals(
            "wrapping onto the food must score, not pass through it",
            10,
            engine.uiState.value.score
        )
        assertEquals(Point(0, 2), engine.uiState.value.snake?.head)
        assertTrue(
            "eaten food must be replaced, not left on the board",
            engine.uiState.value.foods.none { it.position == Point(0, 2) }
        )
    }

    @Test
    fun `growth still applies when the bite happens on a wrap`() = runTest {
        val engine = GameEngine(
            board     = board,
            spawnFood = ScriptedSpawner(Point(0, 2)).asLambda
        )

        engine.startGame(backgroundScope)
        val startLength = engine.uiState.value.snake?.length ?: error("no snake after startGame")

        advanceTimeBy(350L * 5 + 1)

        assertEquals(startLength + 1, engine.uiState.value.snake?.length)
    }

    @Test
    fun `food reached without crossing a wall still scores`() = runTest {
        // Guards the fix from over-correcting: the ordinary in-bounds case.
        val engine = GameEngine(
            board     = board,
            spawnFood = ScriptedSpawner(Point(7, 2)).asLambda
        )

        engine.startGame(backgroundScope)
        advanceTimeBy(350L * 3)

        assertEquals(10, engine.uiState.value.score)
    }
}
