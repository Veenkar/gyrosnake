package com.example.gyrosnake.game

/**
 * Value Object pattern: immutable 2-D grid coordinate.
 * Equality is purely structural (data class), no identity semantics.
 */
data class Point(val x: Int, val y: Int) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
}

/** Extension property mapping each [Direction] to its unit grid delta (Strategy pattern hook). */
val Direction.delta: Point
    get() = when (this) {
        Direction.UP    -> Point(0, -1)
        Direction.DOWN  -> Point(0, 1)
        Direction.LEFT  -> Point(-1, 0)
        Direction.RIGHT -> Point(1, 0)
    }
