package com.gyrosnake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gyrosnake.audio.MusicPlayer
import com.gyrosnake.audio.SoundManager
import com.gyrosnake.R
import com.gyrosnake.data.SettingsRepository
import com.gyrosnake.game.ControlScheme
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.GameEngine
import com.gyrosnake.game.Direction
import com.gyrosnake.game.GamePhase
import com.gyrosnake.game.GameUiState
import com.gyrosnake.game.PowerUpEffect
import com.gyrosnake.input.GyroscopeAdapter
import com.gyrosnake.input.GyroscopeFlickAdapter
import com.gyrosnake.input.OverlayInputAdapter
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
    // Facade pattern: single MusicPlayer instance for all background tracks.
    // Track selection is delegated to resolveTrack() — add new soundtracks there.
    private val music        = MusicPlayer(getApplication())
    private val musicOverlay = MusicPlayer(getApplication())  // Disco solo layer
    val settings = SettingsRepository.getInstance(app)
    val engine   = GameEngine(
        board            = board,
        initialHighScore = settings.highScore,
        onEat            = { SoundManager.playEat(settings.soundVolume) },
        onDie            = { SoundManager.playDie(settings.soundVolume) },
        onNewHighScore   = { settings.highScore = it }
    )

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

        // Observer pattern: watches every uiState emission to drive background music.
        // Kept separate from direction collection so the two concerns don't interfere.
        viewModelScope.launch {
            engine.uiState.collect { s -> updateMusicForState(s) }
        }
    }

    // Value object carrying a track resource ID, playback volume, and sync behaviour.
    private data class TrackConfig(val resId: Int, val volume: Float, val startFromBeginning: Boolean = false)

    /**
     * Routing table: maps current game state to a TrackConfig (track + base volume).
     * Returns null for silence. Add new powerup or screen soundtracks here.
     *
     * Open/Closed principle: MusicPlayer and updateMusicForState never change — only
     * this function grows when new tracks are introduced.
     *
     * Sound priority: activeEffects is ordered oldest-first (effects are appended on eat).
     * lastOrNull() gives the most recently eaten non-expired effect, so whichever powerup
     * was eaten last drives the music. PAUSED returns the same config as PLAYING so music
     * resumes seamlessly on unpause. Phases with no music return null → music.stop().
     */
    // Sound priority: the most recently eaten effect (lastOrNull) wins.
    // Disco as base → normalsnake; the overlay adds discosnake_solo on top.
    // Any other effect as last → its own track, Disco overlay suppressed.
    // PLAYING + PAUSED both return the same game track so MusicPlayer can pause/resume
    // at the same position. menu.ogg plays on all true menu screens (not during pause).
    private fun resolveTrack(s: GameUiState): TrackConfig? = when (s.phase) {
        GamePhase.PLAYING, GamePhase.PAUSED -> when (s.activeEffects.lastOrNull()?.effect) {
            is PowerUpEffect.Leaf  -> TrackConfig(R.raw.leafsnake,   VOLUME_FULL,       startFromBeginning = true)
            is PowerUpEffect.Candy -> TrackConfig(R.raw.candysnake,  VOLUME_QUIET,      startFromBeginning = true)
            is PowerUpEffect.Disco -> TrackConfig(R.raw.normalsnake, VOLUME_QUIET_DISCO)
            else                   -> TrackConfig(R.raw.normalsnake, VOLUME_QUIET)
        }
        // menu.ogg loops continuously across menu screens; same resId keeps it uninterrupted.
        GamePhase.MENU, GamePhase.SETTINGS, GamePhase.GAME_OVER ->
            TrackConfig(R.raw.menu, VOLUME_MENU, startFromBeginning = true)
        else -> null
    }

    // Overlay active during PLAYING and PAUSED so it pauses/resumes in sync with the base.
    private fun resolveOverlay(s: GameUiState): TrackConfig? =
        if ((s.phase == GamePhase.PLAYING || s.phase == GamePhase.PAUSED) &&
                s.activeEffects.lastOrNull()?.effect is PowerUpEffect.Disco)
            TrackConfig(R.raw.discosnake_solo, VOLUME_DISCO_SOLO)
        else null

    /** Applies the current music state immediately — called by both the uiState observer
     *  and applyMusicVolume so slider changes take effect without waiting for the next tick. */
    private fun updateMusicForState(s: GameUiState) {
        val mv      = settings.musicVolume
        if (mv <= 0f) { music.stop(); musicOverlay.stop(); return }
        val cfg     = resolveTrack(s)
        val overlay = resolveOverlay(s)
        val playing = s.phase == GamePhase.PLAYING
        when {
            cfg != null && (playing || s.phase == GamePhase.MENU ||
                s.phase == GamePhase.SETTINGS || s.phase == GamePhase.GAME_OVER)
                            -> music.play(cfg.resId, cfg.volume * mv, cfg.startFromBeginning)
            cfg != null     -> music.pause()   // PAUSED: preserve position for seamless resume
            else            -> music.stop()
        }
        when {
            overlay != null && playing ->
                musicOverlay.play(overlay.resId, overlay.volume * mv, startPositionMs = music.currentPosition)
            overlay != null -> musicOverlay.pause()
            else            -> musicOverlay.stop()
        }
    }

    companion object {
        private const val VOLUME_FULL        = 1.0f
        private const val VOLUME_QUIET       = 0.3162f  // -10 dB: 10^(-10/20)
        private const val VOLUME_QUIET_DISCO = 0.1778f   // -15 dB: normalsnake under Disco (10^(-15/20))
        private const val VOLUME_DISCO_SOLO  = 0.5623f  //  -5 dB: discosnake_solo (10^(-5/20))
        private const val VOLUME_MENU        = 0.3162f   // -10 dB: 10^(-10/20)
    }

    override fun onCleared() {
        super.onCleared()
        music.release()
        musicOverlay.release()
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
    fun applySoundVolume(vol: Float) { settings.soundVolume = vol }
    fun applyMusicVolume(vol: Float) { settings.musicVolume = vol; updateMusicForState(engine.uiState.value) }

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
        ControlScheme.OVERLAY -> OverlayInputAdapter()
    }

    // --- Game actions ---

    fun startGame()          { SoundManager.playStart(settings.soundVolume); engine.startGame(viewModelScope) }
    fun onOverlayButton(dir: Direction) = engine.onDirectionRequest(dir)
    fun togglePause()    = engine.togglePause()
    fun pauseIfPlaying() = engine.pauseIfPlaying()
    fun goToMenu()       = engine.goToMenu()
    fun openSettings()   = engine.openSettings()
    fun closeSettings()  = engine.closeSettings()
}
