package com.gyrosnake.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.gyrosnake.game.Direction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Gyroscope-based flick input. Uses TYPE_GYROSCOPE (angular velocity in rad/s).
 *
 * Unlike the gravity/accelerometer approach which maps absolute tilt angle to direction,
 * this detects rotational gestures — a decisive flick triggers one turn regardless of
 * how the phone is held. This makes controls position-independent.
 *
 * Gesture mapping (landscape, any holding angle):
 *   Roll left  (left edge dips)   → LEFT
 *   Roll right (right edge dips)  → RIGHT
 *   Pitch back (top away from user) → UP
 *   Pitch fwd  (top toward user)  → DOWN
 *
 * A cooldown window after each trigger prevents a single flick from firing multiple turns.
 */
class GyroscopeFlickAdapter(context: Context) : TiltInputAdapter, SensorEventListener {

    companion object {
        // Minimum angular velocity to register a flick (rad/s). ~115°/s — decisive but not twitchy.
        private const val THRESHOLD_RAD_S = 2.0f

        // Milliseconds to ignore input after a direction is triggered (one flick = one turn).
        private const val COOLDOWN_MS = 400L
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile override var displayRotation: Int = Surface.ROTATION_90

    private val _direction = MutableStateFlow<Direction?>(null)
    override val direction: StateFlow<Direction?> = _direction.asStateFlow()

    // Timestamp of last triggered direction — enforces cooldown between flicks
    @Volatile private var lastTriggerMs = 0L

    override fun register() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < COOLDOWN_MS) return

        val wx = event.values[0]  // angular velocity around physical X (pitch)
        val wy = event.values[1]  // angular velocity around physical Y (roll)

        // Remap physical axes to screen axes, same convention as GyroscopeAdapter
        val screenRightRate: Float
        val screenUpRate: Float
        if (displayRotation == Surface.ROTATION_270) {
            screenRightRate = -wx
            screenUpRate    = -wy
        } else {                  // ROTATION_90 default
            screenRightRate =  wx
            screenUpRate    =  wy
        }

        val dominantRight = abs(screenRightRate) >= abs(screenUpRate)
        val dominant = if (dominantRight) screenRightRate else screenUpRate

        if (abs(dominant) < THRESHOLD_RAD_S) return

        lastTriggerMs = now
        _direction.value = if (dominantRight) {
            if (screenRightRate > 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (screenUpRate > 0) Direction.UP else Direction.DOWN
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
