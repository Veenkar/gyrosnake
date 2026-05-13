package com.gyrosnake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gyrosnake.render.GameScreen

/**
 * Single-Activity architecture.
 * The Activity is deliberately thin — it only bootstraps Compose and sets
 * the immersive fullscreen flags.  All state lives in [GameViewModel].
 *
 * OOP technique: Delegation — the Activity delegates UI construction to Compose
 * and state management to the ViewModel (Separation of Concerns).
 */
class MainActivity : ComponentActivity() {

    // ViewModel survives rotation (the Activity does NOT)
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        setContent {
            GameScreen(viewModel = viewModel)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    /** Hides system bars for maximum game canvas real-estate (WindowInsetsController API). */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
