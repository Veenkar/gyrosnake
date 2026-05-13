package com.gyrosnake.game

/**
 * Enum (Enumeration / Value Object pattern).
 * Represents the four cardinal movement directions.
 * Behaviour is co-located with the value to keep the domain self-contained.
 */
enum class Direction {
    UP, DOWN, LEFT, RIGHT;

    /** Returns true when [other] is the exact opposite — used to prevent reversing into self. */
    fun isOpposite(other: Direction): Boolean = when (this) {
        UP    -> other == DOWN
        DOWN  -> other == UP
        LEFT  -> other == RIGHT
        RIGHT -> other == LEFT
    }
}
