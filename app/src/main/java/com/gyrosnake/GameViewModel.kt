package com.gyrosnake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gyrosnake.audio.SoundManager
import com.gyrosnake.game.GameBoard
import com.gyrosnake.game.GameEngine
import com.gyrosnake.input.GyroscopeAdapter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * ViewModel pattern: survives configuration changes (screen rotation) and acts
 * as the single source of truth between the sensor layer, game engine, and UI.
 *
 * OOP techniques applied:
 *   - Mediator pattern: coordinates GyroscopeAdapter → GameEngine → UI without
 *     any of those components knowing about each other directly.
 *   - Dependency Injection (constructor): Application context injected by the
 *     Android framework; board and engine created here once.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    val board = GameBoard(columns = 20, rows = 12)

    val engine = GameEngine(
        board = board,
        onEat = { SoundManager.playEat() },
        onDie = { SoundManager.playDie() }
    )

    val gyroscopeAdapter = GyroscopeAdapter(app)

    init {
        // Mediator: pipe gyroscope direction events into the engine
        viewModelScope.launch {
            gyroscopeAdapter.direction
                .filterNotNull()
                .collect { dir -> engine.onDirectionRequest(dir) }
        }
    }

    fun startGame() {
        SoundManager.playStart()
        engine.startGame(viewModelScope)
    }

    fun togglePause() = engine.togglePause()
    fun pauseIfPlaying() = engine.pauseIfPlaying()
    fun goToMenu() = engine.goToMenu()
}
