package com.gyrosnake.game

/**
 * Immutable Value Object representing the full snake state at one point in time.
 *
 * Using an immutable data class (rather than a mutable entity) means every tick
 * produces a brand-new snapshot.  This integrates cleanly with Kotlin StateFlow:
 * a structural change always produces a new reference, so collectors always react.
 *
 * OOP techniques applied:
 *   - Value Object: equality is structural, no mutable fields
 *   - Immutable Entity: state transitions return new instances (functional style)
 */
data class SnakeState(
    val body: List<Point>,      // head is body[0], tail is body[last]
    val direction: Direction
) {
    val head: Point get() = body.first()
    val length: Int get() = body.size

    /**
     * Returns a new [SnakeState] after advancing one grid step.
     * If [grow] is true the tail segment is retained (snake lengthens).
     */
    fun move(grow: Boolean): SnakeState {
        val newHead = head + direction.delta
        val newBody = buildList {
            add(newHead)
            addAll(if (grow) body else body.dropLast(1))
        }
        return copy(body = newBody)
    }

    /**
     * Returns a new [SnakeState] with [newDir] applied.
     * Silently ignores attempts to reverse — classic snake rule.
     */
    fun withDirection(newDir: Direction): SnakeState {
        if (newDir.isOpposite(direction)) return this
        return copy(direction = newDir)
    }

    /** True when any body segment after the head occupies [point] (self-collision check). */
    fun collidesWithBody(point: Point): Boolean = body.drop(1).contains(point)
}
