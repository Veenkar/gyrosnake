package com.example.gyrosnake.game

/**
 * Snapshot / Transfer Object pattern.
 * A single immutable data class holding everything the UI needs to render one frame.
 * The engine emits a new snapshot each tick via StateFlow; Compose reacts automatically.
 */
data class GameUiState(
    val snake: SnakeState? = null,
    val foods: List<Food> = emptyList(),
    val score: Int = 0,
    val highScore: Int = 0,
    val phase: GamePhase = GamePhase.MENU,
    val tickCount: Long = 0L   // monotonically increasing — forces recomposition each tick
)
