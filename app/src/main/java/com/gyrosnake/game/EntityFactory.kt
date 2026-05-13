package com.gyrosnake.game

import kotlin.random.Random

/**
 * Factory pattern: centralises creation of game entities.
 * Callers never build [SnakeState] or [Food] manually — they call the factory,
 * which encapsulates spawn logic (starting position, initial length, random cell selection).
 */
object EntityFactory {

    private const val INITIAL_LENGTH = 5

    /**
     * Factory method — creates a fresh snake centred on the board,
     * initially moving RIGHT with [INITIAL_LENGTH] body segments.
     */
    fun createSnake(board: GameBoard): SnakeState {
        val startX = board.columns / 2
        val startY = board.rows / 2
        val body = (0 until INITIAL_LENGTH).map { i -> Point(startX - i, startY) }
        return SnakeState(body = body, direction = Direction.RIGHT)
    }

    /**
     * Factory method — spawns a [Food] at a random empty cell.
     * Every [POWERUP_INTERVAL]th food eaten spawns a power-up instead of a normal apple.
     * Returns null when the board is completely full (extremely rare edge case).
     *
     * [foodEatenCount] is the number of foods eaten so far this game session.
     * The power-up type is chosen here; extending to multiple types only requires
     * expanding the `when` branch below.
     */
    fun spawnFood(
        board: GameBoard,
        snake: SnakeState,
        existing: List<Food>,
        foodEatenCount: Int = 0
    ): Food? {
        val empty = board.emptyCells(snake, existing)
        if (empty.isEmpty()) return null
        val position = empty[Random.nextInt(empty.size)]

        // Template Method pattern: powerup selection is isolated here so adding
        // new powerup types requires no changes outside this factory.
        // Each spawn has a 1-in-POWERUP_ODDS chance of being a powerup, giving
        // a random distribution rather than a fixed interval.
        val effect: PowerUpEffect? = if (foodEatenCount > 0 && Random.nextInt(POWERUP_ODDS) == 0) {
            when (Random.nextInt(3)) {
                0    -> PowerUpEffect.Disco
                1    -> PowerUpEffect.Candy
                else -> PowerUpEffect.Leaf
            }
        } else null

        return Food(position, effect)
    }

    private const val POWERUP_ODDS = 5
}
