package com.example.gyrosnake.game

/**
 * Value Object representing the fixed grid dimensions and boundary rules.
 * Acts as a domain boundary validator — no game object should reference raw grid sizes.
 */
data class GameBoard(val columns: Int, val rows: Int) {

    /** True when [point] lies within the grid (walls kill on contact). */
    fun isInBounds(point: Point): Boolean =
        point.x in 0 until columns && point.y in 0 until rows

    /**
     * Factory helper — returns all grid points not currently occupied.
     * Used by [EntityFactory] to pick random food spawn locations.
     */
    fun emptyCells(snake: SnakeState, foods: List<Food>): List<Point> {
        val occupied = (snake.body + foods.map { it.position }).toHashSet()
        return buildList {
            for (x in 0 until columns)
                for (y in 0 until rows)
                    if (Point(x, y) !in occupied) add(Point(x, y))
        }
    }
}
