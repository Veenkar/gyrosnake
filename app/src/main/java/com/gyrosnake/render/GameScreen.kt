package com.gyrosnake.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gyrosnake.game.PowerUpEffect
import kotlin.math.sin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import com.gyrosnake.GameViewModel
import com.gyrosnake.game.ControlScheme
import com.gyrosnake.game.GamePhase

private val COLOR_GREEN     = Color(0xFF00FF55)
private val COLOR_GREEN_DIM = Color(0xFF007722)
private val COLOR_RED       = Color(0xFFFF4400)
private val COLOR_BG        = Color(0xFF0D0D0D)
private val COLOR_OVERLAY   = Color(0xCC000000)

/**
 * Top-level composable — Facade pattern: single entry point composing all sub-components.
 *
 * Lifecycle management is delegated to the ViewModel via [onAdapterResumed] /
 * [onAdapterPaused] so GameScreen has no direct knowledge of sensor registration.
 */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.engine.uiState.collectAsState()

    val isDiscoActive = uiState.activeEffects.any { it.effect is PowerUpEffect.Disco }

    // Disco animations — only meaningful when isDiscoActive, but kept running so
    // there is no startup lag when the effect first triggers.
    val discoTransition = rememberInfiniteTransition(label = "disco")
    val discoPhase by discoTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label         = "discoPhase"
    )
    val discoWobble by discoTransition.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label         = "discoWobble"
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_90
                viewModel.onAdapterResumed(rotation)
            }
            override fun onPause(owner: LifecycleOwner) = viewModel.onAdapterPaused()
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
                when (uiState.phase) {
                    GamePhase.PLAYING -> viewModel.togglePause()
                    GamePhase.PAUSED  -> viewModel.togglePause()
                    else              -> Unit
                }
            }
    ) {
        // Decorator pattern: graphicsLayer wraps GameCanvas with a wobble transform
        // only when the Disco effect is active, leaving normal rendering untouched.
        GameCanvas(
            snake         = uiState.snake,
            foods         = uiState.foods,
            board         = viewModel.board,
            activeEffects = uiState.activeEffects,
            modifier      = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isDiscoActive) rotationZ = sin(discoWobble * Math.PI.toFloat()) * 2f
                }
        )

        if (isDiscoActive) DiscoOverlay(discoPhase)

        HudBar(
            score     = uiState.score,
            highScore = uiState.highScore,
            modifier  = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        // State pattern: one overlay per GamePhase
        when (uiState.phase) {
            GamePhase.MENU      -> MenuOverlay(
                onStart    = { viewModel.startGame() },
                onSettings = { viewModel.openSettings() }
            )
            GamePhase.PAUSED    -> PauseOverlay(
                onResume = { viewModel.togglePause() },
                onMenu   = { viewModel.goToMenu() }
            )
            GamePhase.GAME_OVER -> GameOverOverlay(uiState.score, uiState.highScore) {
                viewModel.startGame()
            }
            GamePhase.SETTINGS  -> SettingsOverlay(
                currentScheme    = viewModel.settings.controlScheme,
                onSchemeSelected = { viewModel.applyControlScheme(it) },
                onBack           = { viewModel.closeSettings() }
            )
            GamePhase.PLAYING   -> Unit
        }
    }
}

// --- Disco screen effect ---

/**
 * Decorator pattern: drawn on top of the game canvas when the Disco power-up is active.
 * Renders horizontal rainbow bands whose positions are offset by a sine wave so they
 * appear to undulate. The [phase] value (0→1 looping) drives both hue rotation and wave motion.
 *
 * Kept as a standalone composable so it can be reused or replaced independently
 * of the game canvas or overlay logic.
 */
@Composable
private fun DiscoOverlay(phase: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val bandCount = 18
        val bandH     = size.height / bandCount
        for (i in 0 until bandCount) {
            val hue      = ((i.toFloat() / bandCount + phase) % 1f) * 360f
            val waveOffX = sin((i * 0.8f + phase * 6.28f)) * size.width * 0.06f
            drawRect(
                color   = Color.hsv(hue, 1f, 1f, 0.13f),
                topLeft = Offset(waveOffX, i * bandH),
                size    = Size(size.width, bandH)
            )
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
private fun MenuOverlay(onStart: () -> Unit, onSettings: () -> Unit) {
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("GYRO", COLOR_GREEN, 52)
            RetroText("SNAKE", COLOR_GREEN, 52)
            Spacer(Modifier.padding(12.dp))
            RetroText("TILT TO STEER", COLOR_GREEN_DIM, 18)
            Spacer(Modifier.padding(24.dp))
            BlinkingCta("[ TAP TO START ]", COLOR_RED, onStart)
            Spacer(Modifier.padding(8.dp))
            BlinkingCta("[ SETTINGS ]", COLOR_GREEN_DIM, onSettings)
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onMenu: () -> Unit) {
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("PAUSED", COLOR_GREEN, 42)
            Spacer(Modifier.padding(16.dp))
            BlinkingCta("[ TAP TO RESUME ]", COLOR_GREEN_DIM, onResume)
            Spacer(Modifier.padding(8.dp))
            BlinkingCta("[ MENU ]", COLOR_RED, onMenu)
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

/**
 * Settings overlay — allows selecting a control scheme at runtime.
 *
 * Composite pattern: each scheme option is a self-contained selectable row.
 * Selection is applied immediately (no confirm step) so the player can
 * try each scheme and the choice persists across sessions via SettingsRepository.
 */
@Composable
private fun SettingsOverlay(
    currentScheme: ControlScheme,
    onSchemeSelected: (ControlScheme) -> Unit,
    onBack: () -> Unit
) {
    var selected by remember { mutableStateOf(currentScheme) }

    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("SETTINGS", COLOR_GREEN, 36)
            Spacer(Modifier.padding(16.dp))
            RetroText("CONTROLS", COLOR_GREEN_DIM, 18)
            Spacer(Modifier.padding(12.dp))

            ControlScheme.entries.forEach { scheme ->
                val isSelected = selected == scheme
                SchemeOption(
                    label      = scheme.label,
                    hint       = scheme.description,
                    isSelected = isSelected,
                    onClick    = {
                        selected = scheme
                        onSchemeSelected(scheme)
                    }
                )
                Spacer(Modifier.padding(4.dp))
            }

            Spacer(Modifier.padding(24.dp))
            BlinkingCta("[ BACK ]", COLOR_RED, onBack)
        }
    }
}

@Composable
private fun SchemeOption(
    label: String,
    hint: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) COLOR_GREEN else COLOR_GREEN_DIM
    val prefix = if (isSelected) "> " else "  "
    val suffix = if (isSelected) " <" else "  "
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = null,
            onClick           = onClick
        )
    ) {
        Text(
            text          = "$prefix$label$suffix",
            color         = color,
            fontSize      = 20.sp,
            fontFamily    = FontFamily.Monospace,
            textAlign     = TextAlign.Center,
            letterSpacing = 2.sp
        )
        Text(
            text          = hint,
            color         = color.copy(alpha = 0.6f),
            fontSize      = 13.sp,
            fontFamily    = FontFamily.Monospace,
            textAlign     = TextAlign.Center,
            letterSpacing = 1.sp
        )
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
