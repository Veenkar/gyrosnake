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
 * Accelerometer-based tilt input. Uses TYPE_ACCELEROMETER directly.
 *
 * The accelerometer reports gravity + linear acceleration, so a low-pass filter
 * is applied to isolate the gravity component and reduce movement noise.
 * The axis remapping and orientation detection logic is identical to GyroscopeAdapter.
 */
class AccelerometerAdapter(context: Context) : TiltInputAdapter, SensorEventListener {

    companion object {
        private const val DEAD_ZONE           = 2.5f
        private const val FACE_DOWN_THRESHOLD = 2.0f
        private const val PORTRAIT_THRESHOLD  = 6.0f
        // Low-pass filter coefficient: higher = smoother but more lag (0..1)
        private const val ALPHA               = 0.15f
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile override var displayRotation: Int = Surface.ROTATION_90

    private val _direction = MutableStateFlow<Direction?>(null)
    override val direction: StateFlow<Direction?> = _direction.asStateFlow()

    // Filtered gravity estimate — updated each sample via low-pass filter
    private var filtX = 0f
    private var filtY = 0f
    private var filtZ = 0f

    override fun register() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Low-pass filter: isolate gravity from linear acceleration
        filtX += ALPHA * (event.values[0] - filtX)
        filtY += ALPHA * (event.values[1] - filtY)
        filtZ += ALPHA * (event.values[2] - filtZ)

        val rawX = filtX
        val rawY = filtY
        val rawZ = filtZ

        val portrait = abs(rawY) > PORTRAIT_THRESHOLD && abs(rawY) >= abs(rawZ)
        val faceDown = !portrait && rawZ > FACE_DOWN_THRESHOLD

        val screenRight: Float
        val screenUp: Float

        if (portrait) {
            screenRight =  rawX
            screenUp    = -rawZ
        } else {
            if (displayRotation == Surface.ROTATION_270) {
                screenRight =  rawY
                screenUp    = -rawX
            } else {
                screenRight = -rawY
                screenUp    =  rawX
            }
        }

        if (abs(screenRight) < DEAD_ZONE && abs(screenUp) < DEAD_ZONE) return

        _direction.value = if (abs(screenRight) >= abs(screenUp)) {
            if ((screenRight > 0) xor faceDown) Direction.RIGHT else Direction.LEFT
        } else {
            if ((screenUp > 0) xor faceDown) Direction.UP else Direction.DOWN
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
