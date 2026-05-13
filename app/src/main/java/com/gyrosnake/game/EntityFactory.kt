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
     * Returns null when the board is completely full (extremely rare edge case).
     */
    fun spawnFood(board: GameBoard, snake: SnakeState, existing: List<Food>): Food? {
        val empty = board.emptyCells(snake, existing)
        if (empty.isEmpty()) return null
        return Food(empty[Random.nextInt(empty.size)])
    }
}
