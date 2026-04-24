package com.example.gyrosnake.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.example.gyrosnake.game.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Adapter pattern: wraps the Android Sensor API and translates raw gravity-vector
 * readings into a clean [Direction] stream.
 *
 * WHY the axis remapping is necessary
 * ------------------------------------
 * The gravity sensor always reports values in the device's *physical* (portrait) frame:
 *   rawX (+) = toward the physical right edge of the phone
 *   rawY (+) = toward the physical top edge of the phone  (portrait top)
 *   rawZ (+) = out of the screen
 *
 * This coordinate system does NOT rotate with the screen.  In landscape the game grid
 * is drawn with the screen rotated 90°, so we must rotate the sensor vector to match:
 *
 *   ROTATION_90  (most phones' natural landscape — CCW from portrait):
 *     screen RIGHT = physical BOTTOM = -rawY
 *     screen UP    = physical RIGHT  = +rawX
 *
 *   ROTATION_270 (reverse landscape — CW from portrait):
 *     screen RIGHT = physical TOP    = +rawY
 *     screen UP    = physical LEFT   = -rawX
 *
 * [displayRotation] must be set from the UI thread before [register] is called.
 * It is @Volatile so the sensor-callback thread sees the latest value immediately.
 */
class GyroscopeAdapter(context: Context) : SensorEventListener {

    companion object {
        /** m/s² — phone must tilt past this to register a direction (dead zone). */
        private const val DEAD_ZONE = 2.5f
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Set this to [Surface.ROTATION_90] or [Surface.ROTATION_270] before registering. */
    @Volatile var displayRotation: Int = Surface.ROTATION_90

    private val _direction = MutableStateFlow<Direction?>(null)
    val direction: StateFlow<Direction?> = _direction.asStateFlow()

    fun register() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Adapter method: remaps physical sensor axes → screen-space axes → Direction.
     * Dominant screen axis wins so diagonal holds produce one clean cardinal direction.
     */
    override fun onSensorChanged(event: SensorEvent) {
        val rawX = event.values[0]   // physical right  (+)
        val rawY = event.values[1]   // physical up/top (+)

        // Rotate sensor vector into screen space
        val screenRight: Float
        val screenUp: Float
        if (displayRotation == Surface.ROTATION_270) {
            screenRight =  rawY   //  physical top    → screen right
            screenUp    = -rawX   //  physical left   → screen up
        } else {
            // ROTATION_90 (default, locked orientation)
            screenRight = -rawY   //  physical bottom → screen right
            screenUp    =  rawX   //  physical right  → screen up
        }

        if (abs(screenRight) < DEAD_ZONE && abs(screenUp) < DEAD_ZONE) return

        // Directions are negated to match the player's intuitive expectation:
        // tilting the right side down moves the snake right (gravity pulls right → snake goes right).
        _direction.value = if (abs(screenRight) >= abs(screenUp)) {
            if (screenRight > 0) Direction.LEFT else Direction.RIGHT
        } else {
            if (screenUp > 0) Direction.DOWN else Direction.UP
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
