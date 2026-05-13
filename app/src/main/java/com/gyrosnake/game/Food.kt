package com.gyrosnake.game

/**
 * Value Object pattern: immutable food pellet.
 * [effect] is null for a normal apple; non-null for a power-up pickup.
 * The type of effect determines rendering (GameCanvas) and what happens on eat (GameEngine).
 */
data class Food(val position: Point, val effect: PowerUpEffect? = null)
