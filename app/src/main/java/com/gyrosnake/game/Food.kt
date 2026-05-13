package com.gyrosnake.game

/**
 * Value Object pattern: immutable food pellet.
 * A new Food instance is created on each spawn rather than mutating an existing one.
 */
data class Food(val position: Point)
