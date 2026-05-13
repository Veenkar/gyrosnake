package com.gyrosnake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gyrosnake.audio.SoundManager
import com.gyrosnake.data.SettingsRepository
import com.gyrosnake.game.ControlScheme
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.GameEngine
import com.gyrosnake.input.GyroscopeAdapter
import com.gyrosnake.input.GyroscopeFlickAdapter
import com.gyrosnake.input.TiltInputAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * ViewModel — survives configuration changes and acts as the central mediator.
 *
 * OOP techniques applied:
 *   - Mediator pattern: coordinates input adapter, game engine, and UI.
 *   - Strategy pattern: [TiltInputAdapter] is swappable at runtime; the active
 *     strategy is held in [_inputAdapter] and switched via [applyControlScheme].
 *   - Factory Method pattern: [createAdapter] maps a [ControlScheme] to the
 *     concrete adapter class, isolating construction from usage.
 *   - Observer pattern: [_inputAdapter] is a StateFlow; [flatMapLatest] re-subscribes
 *     to the new adapter's direction stream whenever the strategy changes.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    val board    = GameBoard(columns = 20, rows = 12)
    val engine   = GameEngine(
        board = board,
        onEat = { SoundManager.playEat() },
        onDie = { SoundManager.playDie() }
    )
    val settings = SettingsRepository.getInstance(app)

    // Strategy pattern: holds the currently active input adapter.
    // MutableStateFlow allows flatMapLatest to transparently re-subscribe when swapped.
    private val _inputAdapter = MutableStateFlow<TiltInputAdapter>(
        createAdapter(settings.controlScheme)
    )
    val inputAdapter: TiltInputAdapter get() = _inputAdapter.value

    // Tracks whether the adapter is currently registered (between onResume / onPause)
    // so that applyControlScheme can hand off registration to the new adapter atomically.
    @Volatile private var adapterActive = false

    init {
        // Observer pattern: flatMapLatest cancels the previous direction collection
        // automatically when _inputAdapter emits a new adapter, so direction events
        // always come from exactly the current strategy with no leakage from old ones.
        viewModelScope.launch {
            _inputAdapter
                .flatMapLatest { it.direction }
                .filterNotNull()
                .collect { dir -> engine.onDirectionRequest(dir) }
        }
    }

    // --- Lifecycle hooks called by GameScreen's DisposableEffect ---

    fun onAdapterResumed(displayRotation: Int) {
        adapterActive = true
        inputAdapter.displayRotation = displayRotation
        inputAdapter.register()
    }

    fun onAdapterPaused() {
        adapterActive = false
        inputAdapter.unregister()
        pauseIfPlaying()
    }

    // --- Settings ---

    /**
     * Strategy swap: persists the chosen scheme, then atomically unregisters the
     * old adapter and registers the new one if the sensor is currently active.
     * Factory Method pattern: [createAdapter] handles the concrete instantiation.
     */
    fun applyControlScheme(scheme: ControlScheme) {
        settings.controlScheme = scheme
        val prev = _inputAdapter.value
        val rotation = prev.displayRotation
        if (adapterActive) prev.unregister()
        val next = createAdapter(scheme)
        next.displayRotation = rotation
        _inputAdapter.value = next
        if (adapterActive) next.register()
    }

    // Factory Method pattern: maps ControlScheme enum to concrete TiltInputAdapter.
    private fun createAdapter(scheme: ControlScheme): TiltInputAdapter = when (scheme) {
        ControlScheme.GRAVITY -> GyroscopeAdapter(getApplication())
        ControlScheme.FLICK   -> GyroscopeFlickAdapter(getApplication())
    }

    // --- Game actions ---

    fun startGame()      { SoundManager.playStart(); engine.startGame(viewModelScope) }
    fun togglePause()    = engine.togglePause()
    fun pauseIfPlaying() = engine.pauseIfPlaying()
    fun goToMenu()       = engine.goToMenu()
    fun openSettings()   = engine.openSettings()
    fun closeSettings()  = engine.closeSettings()
}
