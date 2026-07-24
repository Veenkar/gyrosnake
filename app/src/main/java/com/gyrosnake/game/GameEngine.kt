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
    initialHighScore: Int = 0,
    private val onEat: () -> Unit = {},
    private val onDie: () -> Unit = {},
    private val onNewHighScore: (Int) -> Unit = {}
) {

    // --- Observer pattern: single StateFlow acting as the event bus ---

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // --- Internal mutable game state (private, never leaks outside engine) ---

    private var snake: SnakeState = EntityFactory.createSnake(board)
    private var foods: List<Food> = emptyList()
    private var score = 0
    private var highScore = initialHighScore
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

    // Tracks which phase opened settings so closeSettings() can return to the right screen.
    private var settingsOrigin: GamePhase = GamePhase.MENU

    /** Opens the settings screen from MENU or PAUSED. */
    fun openSettings() {
        val phase = _uiState.value.phase
        if (phase == GamePhase.MENU || phase == GamePhase.PAUSED) {
            settingsOrigin = phase
            publish(GamePhase.SETTINGS)
        }
    }

    /** Returns from settings to whichever phase opened it (MENU or PAUSED). */
    fun closeSettings() {
        if (_uiState.value.phase == GamePhase.SETTINGS) publish(settingsOrigin)
    }

    // Same origin-tracking trick as settings, kept separate so the two screens
    // can be opened independently without clobbering each other's return phase.
    private var tutorialOrigin: GamePhase = GamePhase.MENU

    /** Opens the tutorial from MENU or PAUSED. The game loop is untouched. */
    fun openTutorial() {
        val phase = _uiState.value.phase
        if (phase == GamePhase.MENU || phase == GamePhase.PAUSED) {
            tutorialOrigin = phase
            publish(GamePhase.TUTORIAL)
        }
    }

    /** Returns from the tutorial to whichever phase opened it (MENU or PAUSED). */
    fun closeTutorial() {
        if (_uiState.value.phase == GamePhase.TUTORIAL) publish(tutorialOrigin)
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

        // Where the head lands this step, already wrapped through the walls.
        // Wrapping BEFORE the food lookup is load-bearing: food sits at the
        // wrapped cell, so testing a raw off-grid coordinate (x = -1 against
        // food at x = columns - 1) silently misses the bite and its power-up.
        val nextHead  = board.wrap(snake.head + snake.direction.delta)
        val eatenFood = foods.firstOrNull { it.position == nextHead }
        val growing   = eatenFood != null

        // Move the snake, then apply the wrap by reusing the point above so
        // the head can never disagree with what the food check was told.
        snake = snake.move(grow = growing)
        if (snake.head != nextHead) {
            snake = snake.copy(body = listOf(nextHead) + snake.body.drop(1))
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
            //
            // Cancellation rule: Candy and Leaf are opposites — eating one while the
            // other is active removes both, returning the snake to the normal state.
            // Disco is orthogonal and always stacks freely with either.
            eatenFood!!.effect?.let { effect ->
                val oppositeActive = when (effect) {
                    is PowerUpEffect.Candy -> activeEffects.any { it.effect is PowerUpEffect.Leaf }
                    is PowerUpEffect.Leaf  -> activeEffects.any { it.effect is PowerUpEffect.Candy }
                    else                   -> false
                }
                if (oppositeActive) {
                    activeEffects = activeEffects.filterNot {
                        it.effect is PowerUpEffect.Candy || it.effect is PowerUpEffect.Leaf
                    }
                } else {
                    val durationMs = (effect.minDurationMs..effect.maxDurationMs).random()
                    activeEffects = activeEffects + ActiveEffect(effect, System.currentTimeMillis() + durationMs)
                }
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
        if (score > highScore) {
            highScore = score
            onNewHighScore(highScore)
        }
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
