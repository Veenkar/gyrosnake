package com.gyrosnake.input

import com.gyrosnake.game.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Null Object pattern: satisfies the TiltInputAdapter interface with no-op sensor behaviour.
 * The direction flow never emits — on-screen button presses bypass the adapter entirely
 * and are forwarded directly to GameEngine via GameViewModel.onOverlayButton().
 * This keeps the sensor registration lifecycle clean with zero overhead.
 */
class OverlayInputAdapter : TiltInputAdapter {
    override var displayRotation: Int = 0
    override val direction: StateFlow<Direction?> = MutableStateFlow(null)
    override fun register()   = Unit
    override fun unregister() = Unit
}
