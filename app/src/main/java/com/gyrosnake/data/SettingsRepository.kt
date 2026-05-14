package com.gyrosnake.data

import android.content.Context
import com.gyrosnake.game.ControlScheme

/**
 * Repository pattern: single source of truth for user preferences.
 * Abstracts SharedPreferences behind a typed API so callers never touch raw keys or strings.
 *
 * Singleton pattern (double-checked locking): one instance per process, created lazily
 * and thread-safely via a companion object factory.
 */
class SettingsRepository private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME          = "gyrosnake_settings"
        private const val KEY_CONTROL_SCHEME  = "control_scheme"
        private const val KEY_SOUND_VOLUME    = "sound_volume"
        private const val KEY_MUSIC_VOLUME    = "music_volume"
        private const val KEY_HIGH_SCORE      = "high_score"

        @Volatile private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var controlScheme: ControlScheme
        get() = ControlScheme.valueOf(
            prefs.getString(KEY_CONTROL_SCHEME, ControlScheme.GRAVITY.name)
                ?: ControlScheme.GRAVITY.name
        )
        set(value) = prefs.edit().putString(KEY_CONTROL_SCHEME, value.name).apply()

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SOUND_VOLUME, value).apply()

    var musicVolume: Float
        get() = prefs.getFloat(KEY_MUSIC_VOLUME, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_MUSIC_VOLUME, value).apply()

    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()
}
