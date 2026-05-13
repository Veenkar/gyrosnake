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
class GyroscopeAdapter(context: Context) : TiltInputAdapter, SensorEventListener {

    companion object {
        /** m/s² — phone must tilt past this to register a direction (dead zone). */
        private const val DEAD_ZONE = 2.5f

        /**
         * rawZ threshold for face-down detection.
         * Face-up  → rawZ ≈ −9.8 (screen faces sky, Z points away from earth).
         * Face-down → rawZ ≈ +9.8 (screen faces ground, Z points toward earth).
         * A threshold of +2 m/s² corresponds to ~12° past vertical — safely
         * distinguishes "lying in bed holding phone above head" from normal play.
         */
        private const val FACE_DOWN_THRESHOLD = 2.0f
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile override var displayRotation: Int = Surface.ROTATION_90

    private val _direction = MutableStateFlow<Direction?>(null)
    override val direction: StateFlow<Direction?> = _direction.asStateFlow()

    override fun register() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun unregister() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Adapter method: remaps physical sensor axes → screen-space axes → Direction.
     * Dominant screen axis wins so diagonal holds produce one clean cardinal direction.
     *
     * Face-down detection (Observer/Strategy hook):
     * rawZ > FACE_DOWN_THRESHOLD means the screen faces the floor (user lying on back,
     * phone held above head). In this orientation the user's perceived left/right and
     * up/down are BOTH mirrored relative to face-up, so we XOR the direction with the
     * face-down flag — effectively cancelling the face-up inversion.
     *
     *   face-up  + (screenRight > 0)  →  LEFT   (inverted mapping, calibrated on device)
     *   face-down + (screenRight > 0) →  RIGHT  (XOR flips it back for mirrored view)
     */
    override fun onSensorChanged(event: SensorEvent) {
        val rawX = event.values[0]   // physical right  (+)
        val rawY = event.values[1]   // physical up/top (+)
        val rawZ = event.values[2]   // out of screen   (+); negative when face-up

        // Rotate sensor vector into screen space based on display rotation
        val screenRight: Float
        val screenUp: Float
        if (displayRotation == Surface.ROTATION_270) {
            screenRight =  rawY
            screenUp    = -rawX
        } else {                     // ROTATION_90 (default / locked orientation)
            screenRight = -rawY
            screenUp    =  rawX
        }

        if (abs(screenRight) < DEAD_ZONE && abs(screenUp) < DEAD_ZONE) return

        // When face-down the player's view is mirrored → XOR flips both axes
        val faceDown = rawZ > FACE_DOWN_THRESHOLD

        _direction.value = if (abs(screenRight) >= abs(screenUp)) {
            if ((screenRight > 0) xor faceDown) Direction.RIGHT else Direction.LEFT
        } else {
            if ((screenUp > 0) xor faceDown) Direction.UP else Direction.DOWN
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
