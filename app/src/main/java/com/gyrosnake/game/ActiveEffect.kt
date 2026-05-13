package com.gyrosnake.game

/**
 * Value Object / Transfer Object pattern:
 * pairs a [PowerUpEffect] with its wall-clock expiry time so the engine
 * can filter expired effects on every tick without external coordination.
 */
data class ActiveEffect(
    val effect: PowerUpEffect,
    val expiresAtMs: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMs
}
