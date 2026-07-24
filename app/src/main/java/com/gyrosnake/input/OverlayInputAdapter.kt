package com.gyrosnake.input

import com.gyrosnake.game.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Null Object pattern: satisfies the TiltInputAdapter interface with no-op sensor behaviour.
 * Shared by every touch-driven control scheme (OVERLAY's joystick and POINT's
 * point-and-go): the direction flow never emits, because touches are read by the
 * composable that draws the controls and forwarded straight to GameEngine via
 * GameViewModel.onOverlayButton().
 *
 * Keeping these schemes on a no-op adapter means the sensor registration
 * lifecycle stays uniform across schemes at zero cost.
 */
class OverlayInputAdapter : TiltInputAdapter {
    override var displayRotation: Int = 0
    override val direction: StateFlow<Direction?> = MutableStateFlow(null)
    override fun register()   = Unit
    override fun unregister() = Unit
}
