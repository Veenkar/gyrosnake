package com.example.gyrosnake.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gyrosnake.GameViewModel
import com.example.gyrosnake.game.GamePhase

private val COLOR_GREEN     = Color(0xFF00FF55)
private val COLOR_GREEN_DIM = Color(0xFF007722)
private val COLOR_RED       = Color(0xFFFF4400)
private val COLOR_BG        = Color(0xFF0D0D0D)
private val COLOR_OVERLAY   = Color(0xCC000000)

/**
 * Top-level composable for the game screen.
 *
 * OOP techniques applied:
 *   - Facade pattern: single entry point that composes sub-components (canvas,
 *     HUD, overlays) without the caller needing to know the internal structure.
 *   - Observer pattern (Compose): [collectAsState] turns StateFlow into Compose
 *     state; any emission automatically triggers recomposition of affected nodes.
 *   - Lifecycle Observer (DisposableEffect): registers/unregisters the sensor
 *     adapter bound to the host Activity's lifecycle to avoid sensor leaks.
 */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.engine.uiState.collectAsState()

    // Lifecycle Observer pattern: sensor must match Activity visibility
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = viewModel.gyroscopeAdapter.register()
            override fun onPause(owner: LifecycleOwner)  = viewModel.gyroscopeAdapter.unregister()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(COLOR_BG)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                // Tap anywhere during play to pause (excluding overlay taps handled below)
                when (uiState.phase) {
                    GamePhase.PLAYING -> viewModel.togglePause()
                    GamePhase.PAUSED  -> viewModel.togglePause()
                    else              -> Unit
                }
            }
    ) {
        GameCanvas(
            snake    = uiState.snake,
            foods    = uiState.foods,
            board    = viewModel.board,
            modifier = Modifier.fillMaxSize()
        )

        HudBar(
            score     = uiState.score,
            highScore = uiState.highScore,
            modifier  = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        when (uiState.phase) {
            GamePhase.MENU      -> MenuOverlay     { viewModel.startGame() }
            GamePhase.PAUSED    -> PauseOverlay    { viewModel.togglePause() }
            GamePhase.GAME_OVER -> GameOverOverlay(uiState.score, uiState.highScore) { viewModel.startGame() }
            GamePhase.PLAYING   -> Unit
        }
    }
}

// --- HUD ---

@Composable
private fun HudBar(score: Int, highScore: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        RetroText("SCORE: $score", COLOR_GREEN, 18)
        Spacer(Modifier.weight(1f))
        RetroText("BEST: $highScore", COLOR_GREEN_DIM, 18)
    }
}

// --- Overlays (State pattern: one overlay per GamePhase) ---

@Composable
private fun MenuOverlay(onStart: () -> Unit) {
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("GYRO", COLOR_GREEN, 52)
            RetroText("SNAKE", COLOR_GREEN, 52)
            Spacer(Modifier.padding(12.dp))
            RetroText("TILT TO STEER", COLOR_GREEN_DIM, 18)
            Spacer(Modifier.padding(24.dp))
            BlinkingCta("[ TAP TO START ]", COLOR_RED, onStart)
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit) {
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("PAUSED", COLOR_GREEN, 42)
            Spacer(Modifier.padding(16.dp))
            BlinkingCta("[ TAP TO RESUME ]", COLOR_GREEN_DIM, onResume)
        }
    }
}

@Composable
private fun GameOverOverlay(score: Int, highScore: Int, onRestart: () -> Unit) {
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("GAME OVER", COLOR_RED, 42)
            Spacer(Modifier.padding(8.dp))
            RetroText("SCORE: $score", COLOR_GREEN, 24)
            if (score > 0 && score >= highScore) {
                RetroText("NEW BEST!", COLOR_RED, 20)
            } else {
                RetroText("BEST: $highScore", COLOR_GREEN_DIM, 20)
            }
            Spacer(Modifier.padding(24.dp))
            BlinkingCta("[ TAP TO RETRY ]", COLOR_GREEN, onRestart)
        }
    }
}

// --- Reusable primitives ---

@Composable
private fun FullOverlay(content: @Composable () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(COLOR_OVERLAY),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RetroText(text: String, color: Color, fontSize: Int) {
    Text(
        text          = text,
        color         = color,
        fontSize      = fontSize.sp,
        fontFamily    = FontFamily.Monospace,
        textAlign     = TextAlign.Center,
        letterSpacing = 4.sp
    )
}

/**
 * Call-to-action text with slow blink animation.
 * Clickable with its own interaction source so it does NOT bubble the tap
 * up to the parent Box (which would trigger pause/resume).
 */
@Composable
private fun BlinkingCta(text: String, color: Color, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "blink")
    val alpha by inf.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )
    Text(
        text          = text,
        color         = color.copy(alpha = alpha),
        fontSize      = 22.sp,
        fontFamily    = FontFamily.Monospace,
        textAlign     = TextAlign.Center,
        letterSpacing = 3.sp,
        modifier      = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = null,
            onClick           = onClick
        )
    )
}
