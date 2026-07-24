package com.gyrosnake.game

/**
 * Value Object representing the fixed grid dimensions and boundary rules.
 * Acts as a domain boundary validator — no game object should reference raw grid sizes.
 */
data class GameBoard(val columns: Int, val rows: Int) {

    /**
     * Maps [point] onto the grid by wrapping it through the walls; in-bounds
     * points come back unchanged.
     *
     * Single source of truth for the wrap rule — callers must resolve a
     * candidate position through this before comparing it against anything
     * on the board, or an off-grid coordinate will fail to match the entity
     * that actually sits at the wrapped cell.
     */
    fun wrap(point: Point): Point = Point(
        (point.x % columns + columns) % columns,
        (point.y % rows    + rows)    % rows
    )

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
