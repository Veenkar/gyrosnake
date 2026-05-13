package com.gyrosnake.game

/**
 * Sealed class hierarchy — Open/Closed principle + Strategy pattern:
 * new power-up types are added by creating new subclasses here without
 * touching any existing game logic other than the render and apply switches.
 *
 * Each subclass carries only the data that defines that effect type.
 * Runtime state (expiry time) lives in [ActiveEffect], not here.
 */
sealed class PowerUpEffect {

    /** Duration range from which a random active duration is sampled on pickup. */
    abstract val minDurationMs: Long
    abstract val maxDurationMs: Long

    /**
     * Disco powerup: distorts the screen with wavy rainbow colors.
     * Visual effect only — no change to game mechanics.
     */
    object Disco : PowerUpEffect() {
        override val minDurationMs = 20_000L
        override val maxDurationMs = 60_000L
    }

    /**
     * Candy powerup: snake turns pink and moves 1.5× faster.
     * Speed multiplier applied in GameEngine via effectiveTickMs.
     */
    object Candy : PowerUpEffect() {
        override val minDurationMs = 20_000L
        override val maxDurationMs = 60_000L
        const val SPEED_MULTIPLIER = 1.5f
    }
}
