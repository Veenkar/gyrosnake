package com.gyrosnake.input

import com.gyrosnake.game.Direction
import kotlinx.coroutines.flow.StateFlow

interface TiltInputAdapter {
    var displayRotation: Int
    val direction: StateFlow<Direction?>
    fun register()
    fun unregister()
}
