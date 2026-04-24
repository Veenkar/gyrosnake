package com.example.gyrosnake.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.gyrosnake.game.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adapter pattern: wraps the Android Sensor API and translates raw gravity-vector
 * readings into a clean [Direction] stream that the rest of the game can consume
 * without knowing anything about SensorManager or SensorEvent.
 *
 * Phone orientation assumed: landscape, face-up (player looks down at the screen).
 * Coordinate mapping (device axes when lying flat in landscape):
 *   +X → physical right of phone  → Direction.RIGHT
 *   -X → physical left of phone   → Direction.LEFT
 *   +Y → physical top of phone    → Direction.UP   (screen top edge tilts down)
 *   -Y → physical bottom of phone → Direction.DOWN
 *
 * Note: exact X/Y sign may vary by device and landscape rotation. If the game
 * controls feel inverted, negate [INVERT_X] or [INVERT_Y] flags below.
 */
class GyroscopeAdapter(context: Context) : SensorEventListener {

    companion object {
        /** Tilt threshold in m/s² — below this the stick is treated as centred. */
        private const val DEAD_ZONE = 2.5f

        /** Flip if your device's X axis feels backwards in landscape. */
        private const val INVERT_X = false

        /** Flip if your device's Y axis feels backwards in landscape. */
        private const val INVERT_Y = false
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    // TYPE_GRAVITY is a virtual sensor (fusion of accel + gyro).
    // Fall back to raw accelerometer on older or limited hardware.
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _direction = MutableStateFlow<Direction?>(null)

    /** Emits the dominant tilt direction; null when within the dead zone. */
    val direction: StateFlow<Direction?> = _direction.asStateFlow()

    /** Register — must be called when the Activity/Composable becomes visible. */
    fun register() {
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /** Unregister — must be called when the Activity/Composable goes to background. */
    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Adapter method: translates a raw [SensorEvent] into a [Direction].
     * Picks the dominant axis (whichever has the larger absolute tilt) so
     * diagonal holds still produce a single clean cardinal direction.
     */
    override fun onSensorChanged(event: SensorEvent) {
        val gx = if (INVERT_X) -event.values[0] else event.values[0]
        val gy = if (INVERT_Y) -event.values[1] else event.values[1]

        val absX = Math.abs(gx)
        val absY = Math.abs(gy)

        if (absX < DEAD_ZONE && absY < DEAD_ZONE) return  // ignore centred/flat

        _direction.value = if (absX >= absY) {
            if (gx > 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (gy > 0) Direction.UP else Direction.DOWN
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
