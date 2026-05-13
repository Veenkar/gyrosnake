package com.gyrosnake.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val INITIAL_TICK_MS  = 350L
private const val MIN_TICK_MS      = 150L
private const val SPEED_STEP_MS    = 3L
private const val POINTS_PER_FOOD  = 10

/**
 * Game Engine — central controller of game logic.
 *
 * OOP techniques applied:
 *   - Observer pattern: exposes [uiState] as a StateFlow; all interested parties
 *     (UI, audio) subscribe rather than being called directly.
 *   - Game Loop pattern: [startGame] launches a coroutine that ticks at a fixed
 *     interval, advancing game state and publishing a new snapshot each tick.
 *   - Callback / Template Method pattern: [onEat] and [onDie] lambdas let callers
 *     inject side-effects (sound) without coupling engine to audio layer.
 *
 * The engine itself is pure game logic; it has no Android imports.
 *
 * @param board     fixed grid dimensions
 * @param onEat     called each time the snake eats food (play sound, etc.)
 * @param onDie     called when a fatal collision occurs
 */
class GameEngine(
    val board: GameBoard,
    private val onEat: () -> Unit = {},
    private val onDie: () -> Unit = {}
) {

    // --- Observer pattern: single StateFlow acting as the event bus ---

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // --- Internal mutable game state (private, never leaks outside engine) ---

    private var snake: SnakeState = EntityFactory.createSnake(board)
    private var foods: List<Food> = emptyList()
    private var score = 0
    private var highScore = 0
    private var tickMs = INITIAL_TICK_MS
    private var tickCount = 0L
    private var foodEatenCount = 0
    private var activeEffects: List<ActiveEffect> = emptyList()

    // Pending direction buffered from the gyroscope — applied at the start of each tick
    @Volatile private var pendingDirection: Direction? = null

    private var gameLoopJob: Job? = null

    // --- Public API ---

    /** Starts (or restarts) a new game. Cancels any running game loop. */
    fun startGame(scope: CoroutineScope) {
        gameLoopJob?.cancel()
        snake = EntityFactory.createSnake(board)
        foods = listOfNotNull(EntityFactory.spawnFood(board, snake, emptyList()))
        score = 0
        tickMs = INITIAL_TICK_MS
        tickCount = 0L
        foodEatenCount = 0
        activeEffects = emptyList()
        pendingDirection = null
        publish(GamePhase.PLAYING)

        // Game Loop pattern: fixed-rate coroutine driving all game updates.
        // Loop always spins at tickMs; ticks are skipped while paused so the
        // coroutine stays alive and resumes instantly on unpause.
        gameLoopJob = scope.launch {
            while (isActive) {
                val effectiveTickMs = when {
                    activeEffects.any { it.effect is PowerUpEffect.Candy } ->
                        (tickMs / PowerUpEffect.Candy.SPEED_MULTIPLIER).toLong()
                    activeEffects.any { it.effect is PowerUpEffect.Leaf } ->
                        (tickMs / PowerUpEffect.Leaf.SPEED_MULTIPLIER).toLong()
                    else -> tickMs
                }
                delay(effectiveTickMs)
                when (_uiState.value.phase) {
                    GamePhase.PLAYING  -> tick()
                    GamePhase.GAME_OVER -> break   // engine killed by endGame()
                    else               -> Unit     // PAUSED — keep loop alive, skip tick
                }
            }
        }
    }

    /** Toggles between PLAYING and PAUSED. No-op in other phases. */
    fun togglePause() {
        when (_uiState.value.phase) {
            GamePhase.PLAYING -> publish(GamePhase.PAUSED)
            GamePhase.PAUSED  -> publish(GamePhase.PLAYING)
            else              -> Unit
        }
    }

    /** Pauses only if currently PLAYING — safe to call from lifecycle onPause. */
    fun pauseIfPlaying() {
        if (_uiState.value.phase == GamePhase.PLAYING) publish(GamePhase.PAUSED)
    }

    /** Cancels the game loop and returns to the main menu. */
    fun goToMenu() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        publish(GamePhase.MENU)
    }

    /** Opens the settings screen — only valid from MENU. */
    fun openSettings() {
        if (_uiState.value.phase == GamePhase.MENU) publish(GamePhase.SETTINGS)
    }

    /** Returns from settings back to the main menu. */
    fun closeSettings() {
        if (_uiState.value.phase == GamePhase.SETTINGS) publish(GamePhase.MENU)
    }

    /**
     * Receives a direction request from the input adapter.
     * Thread-safe volatile write — gyroscope runs on a sensor thread.
     */
    fun onDirectionRequest(dir: Direction) {
        if (_uiState.value.phase == GamePhase.PLAYING) pendingDirection = dir
    }

    // --- Private game-loop tick ---

    private fun tick() {
        // Expire any effects whose wall-clock time has passed
        activeEffects = activeEffects.filterNot { it.isExpired() }

        // Apply buffered direction (ignores reversal — enforced inside SnakeState)
        pendingDirection?.let { snake = snake.withDirection(it) }
        pendingDirection = null

        // Determine if food will be eaten on this step
        val nextHead = snake.head + snake.direction.delta
        val eatenFood = foods.firstOrNull { it.position == nextHead }
        val growing = eatenFood != null

        // Move the snake
        snake = snake.move(grow = growing)

        // Wall wrap-around: teleport head to opposite side instead of dying
        if (!board.isInBounds(snake.head)) {
            val wrapped = Point(
                (snake.head.x + board.columns) % board.columns,
                (snake.head.y + board.rows)    % board.rows
            )
            snake = snake.copy(body = listOf(wrapped) + snake.body.drop(1))
        }

        // Self-collision check (head vs rest of body)
        if (snake.collidesWithBody(snake.head)) {
            endGame()
            return
        }

        // Food eaten: update score, speed, apply power-up effect, spawn replacement
        if (growing) {
            foodEatenCount++
            score += POINTS_PER_FOOD
            tickMs = maxOf(MIN_TICK_MS, tickMs - SPEED_STEP_MS)

            // Observer / Strategy: apply effect if this was a power-up food.
            // Adding new effect types only requires a new branch here.
            eatenFood!!.effect?.let { effect ->
                val durationMs = (effect.minDurationMs..effect.maxDurationMs).random()
                activeEffects = activeEffects + ActiveEffect(effect, System.currentTimeMillis() + durationMs)
            }

            val remaining = foods - eatenFood
            foods = remaining + listOfNotNull(
                EntityFactory.spawnFood(board, snake, remaining, foodEatenCount)
            )
            onEat()
        }

        // Leaf powerup: maintain extra food while active, trim back to 1 when it expires
        val foodTarget = if (activeEffects.any { it.effect is PowerUpEffect.Leaf })
            PowerUpEffect.Leaf.FOOD_TARGET else 1
        while (foods.size < foodTarget) {
            val extra = EntityFactory.spawnFood(board, snake, foods, foodEatenCount) ?: break
            foods = foods + extra
        }
        if (foods.size > foodTarget) foods = foods.take(foodTarget)

        tickCount++
        publish(GamePhase.PLAYING)
    }

    private fun endGame() {
        if (score > highScore) highScore = score
        gameLoopJob?.cancel()
        onDie()
        publish(GamePhase.GAME_OVER)
    }

    /** Emits a fresh [GameUiState] snapshot — triggers Compose recomposition. */
    private fun publish(phase: GamePhase) {
        _uiState.value = GameUiState(
            snake         = snake,
            foods         = foods,
            score         = score,
            highScore     = highScore,
            phase         = phase,
            tickCount     = tickCount,
            activeEffects = activeEffects
        )
    }
}
