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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs
import kotlin.math.sqrt
import androidx.compose.ui.graphics.drawscope.Stroke
import com.gyrosnake.GameViewModel
import com.gyrosnake.game.ControlScheme
import com.gyrosnake.game.Direction
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
    val uiState       by viewModel.engine.uiState.collectAsState()
    val needsSetup    by viewModel.needsControlSetup.collectAsState()

    val isDiscoActive = uiState.activeEffects.any { it.effect is PowerUpEffect.Disco }
    val isLeafActive  = uiState.activeEffects.any { it.effect is PowerUpEffect.Leaf }

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

    // Leaf animations — slow breathing scale + green pulse overlay.
    val leafTransition = rememberInfiniteTransition(label = "leaf")
    val leafBreathe by leafTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label         = "leafBreathe"
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
                    if (isLeafActive) {
                        val breathe = 1f + sin(leafBreathe * Math.PI.toFloat()) * 0.025f
                        scaleX = breathe
                        scaleY = breathe
                    }
                }
        )

        if (isDiscoActive) DiscoOverlay(discoPhase)
        if (isLeafActive)  LeafOverlay(leafBreathe)

        HudBar(
            score     = uiState.score,
            highScore = uiState.highScore,
            modifier  = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        // Overlay controls: shown only when the OVERLAY scheme is active and the game
        // is running. Phase-specific overlays (pause, menu) sit above this in the Z-order
        // and capture all touches, so buttons are unreachable when not needed.
        if (viewModel.settings.controlScheme == ControlScheme.OVERLAY &&
            uiState.phase == GamePhase.PLAYING) {
            OverlayControls(
                onDirection = { viewModel.onOverlayButton(it) },
                modifier    = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }

        // State pattern: one overlay per GamePhase
        when (uiState.phase) {
            GamePhase.MENU      -> MenuOverlay(
                highScore  = uiState.highScore,
                onStart    = { viewModel.startGame() },
                onSettings = { viewModel.openSettings() }
            )
            GamePhase.PAUSED    -> PauseOverlay(
                onResume   = { viewModel.togglePause() },
                onSettings = { viewModel.openSettings() },
                onMenu     = { viewModel.goToMenu() }
            )
            GamePhase.GAME_OVER -> GameOverOverlay(uiState.score, uiState.highScore) {
                viewModel.startGame()
            }
            GamePhase.SETTINGS  -> SettingsOverlay(
                currentScheme    = viewModel.settings.controlScheme,
                onSchemeSelected = { viewModel.applyControlScheme(it) },
                soundVolume      = viewModel.settings.soundVolume,
                onSoundVolume    = { viewModel.applySoundVolume(it) },
                musicVolume      = viewModel.settings.musicVolume,
                onMusicVolume    = { viewModel.applyMusicVolume(it) },
                onBack           = { viewModel.closeSettings() }
            )
            GamePhase.PLAYING   -> Unit
        }

        if (needsSetup) {
            ControlSetupOverlay(
                initial  = viewModel.settings.controlScheme,
                onConfirm = { viewModel.confirmControlSetup(it) }
            )
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

// --- Leaf screen effect ---

/**
 * Decorator pattern: slow-pulsing green vignette drawn when the Leaf powerup is active.
 * [breathe] (0→1 reversing) drives the alpha so the overlay gently fades in and out,
 * giving a calm "breathing" feel distinct from the frantic Disco effect.
 */
@Composable
private fun LeafOverlay(breathe: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = sin(breathe * Math.PI.toFloat()) * 0.12f
        // Green vignette rings radiating from the edges inward
        val cx = size.width  / 2f
        val cy = size.height / 2f
        for (i in 0 until 6) {
            val r = (size.width * (0.9f - i * 0.12f))
            drawCircle(
                color  = Color(0xFF00CC44).copy(alpha = alpha * (i + 1) / 6f),
                radius = r,
                center = Offset(cx, cy),
                style  = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.08f)
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
private fun MenuOverlay(highScore: Int, onStart: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
    }
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("GYRO", COLOR_GREEN, 52)
            RetroText("SNAKE", COLOR_GREEN, 52)
            Spacer(Modifier.padding(12.dp))
            RetroText("TILT TO STEER", COLOR_GREEN_DIM, 18)
            if (highScore > 0) {
                Spacer(Modifier.padding(8.dp))
                RetroText("BEST: $highScore", COLOR_GREEN_DIM, 18)
            }
            Spacer(Modifier.padding(24.dp))
            BlinkingCta("[ TAP TO START ]", COLOR_RED, onStart)
            Spacer(Modifier.padding(8.dp))
            BlinkingCta("[ SETTINGS ]", COLOR_GREEN_DIM, onSettings)
            Spacer(Modifier.padding(16.dp))
            RetroText("v$versionName", COLOR_GREEN_DIM.copy(alpha = 0.4f), 12)
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onSettings: () -> Unit, onMenu: () -> Unit) {
    FullOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("PAUSED", COLOR_GREEN, 42)
            Spacer(Modifier.padding(16.dp))
            BlinkingCta("[ TAP TO RESUME ]", COLOR_GREEN_DIM, onResume)
            Spacer(Modifier.padding(8.dp))
            BlinkingCta("[ SETTINGS ]", COLOR_GREEN_DIM, onSettings)
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
 * Settings overlay — controls, sound, and music toggles.
 *
 * Composite pattern: each option (SchemeOption, ToggleOption) is a self-contained
 * interactive row. All changes apply immediately and persist via SettingsRepository.
 *
 * Layout: BACK is pinned to the bottom of the screen so it is always reachable
 * regardless of content height. The settings body scrolls independently above it.
 * The system back button is wired via BackHandler so hardware/gesture back works too.
 */
@Composable
private fun SettingsOverlay(
    currentScheme: ControlScheme,
    onSchemeSelected: (ControlScheme) -> Unit,
    soundVolume: Float,
    onSoundVolume: (Float) -> Unit,
    musicVolume: Float,
    onMusicVolume: (Float) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selected by remember { mutableStateOf(currentScheme) }
    var soundVol by remember { mutableStateOf(soundVolume) }
    var musicVol by remember { mutableStateOf(musicVolume) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(COLOR_OVERLAY)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = null,
            onClick           = onBack
        )
    ) {

        // Scrollable content — padded at the bottom so it never slides under the pinned bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RetroText("SETTINGS", COLOR_GREEN, 36)
            Spacer(Modifier.padding(12.dp))

            RetroText("CONTROLS", COLOR_GREEN_DIM, 18)
            Spacer(Modifier.padding(8.dp))
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

            Spacer(Modifier.padding(12.dp))
            RetroText("AUDIO", COLOR_GREEN_DIM, 18)
            Spacer(Modifier.padding(8.dp))
            VolumeOption("SOUND FX", soundVol) { v ->
                soundVol = v
                onSoundVolume(v)
            }
            Spacer(Modifier.padding(4.dp))
            VolumeOption("MUSIC", musicVol) { v ->
                musicVol = v
                onMusicVolume(v)
            }
        }

        // Pinned top-left — always visible
        BlinkingCta(
            text    = "[ BACK ]",
            color   = COLOR_GREEN_DIM,
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 24.dp)
        )
    }
}

/**
 * First-launch control scheme picker — shown once until the user confirms a choice.
 * OVERLAY is pre-selected (first entry in ControlScheme.entries after reordering).
 * Confirming persists the selection and dismisses this overlay permanently.
 */
@Composable
private fun ControlSetupOverlay(initial: ControlScheme, onConfirm: (ControlScheme) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(COLOR_BG)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RetroText("CHOOSE CONTROLS", COLOR_GREEN, 28)
            Spacer(Modifier.padding(20.dp))
            ControlScheme.entries.forEach { scheme ->
                SchemeOption(
                    label      = scheme.label,
                    hint       = scheme.description,
                    isSelected = selected == scheme,
                    onClick    = { selected = scheme }
                )
                Spacer(Modifier.padding(12.dp))
            }
            Spacer(Modifier.padding(16.dp))
            BlinkingCta("[ CONFIRM ]", COLOR_GREEN, onClick = { onConfirm(selected) })
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

// --- Overlay D-pad controls ---

/**
 * Virtual joystick — outer ring + floating thumb.
 *
 * A single pointerInput on the 320 dp Box tracks every pointer event:
 *   - While pressed: compute direction from thumb offset quadrant, move thumb visually.
 *   - On release: snap thumb back to centre, stop sending directions.
 * All events are consumed so nothing reaches the full-screen pause handler (dead zone).
 *
 * Joystick centre sits 100 dp from the right and 102 dp from the bottom of the Box,
 * matching where the old D-pad cross centre was.
 */
@Composable
private fun OverlayControls(onDirection: (Direction) -> Unit, modifier: Modifier = Modifier) {
    val density      = LocalDensity.current
    val outerRadiusPx = with(density) { 80.dp.toPx() }
    val thumbRadiusPx = with(density) { 30.dp.toPx() }
    val centerXPx    = with(density) { 220.dp.toPx() }   // 100 dp from Box right
    val centerYPx    = with(density) { 218.dp.toPx() }   // 102 dp from Box bottom

    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(320.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        change.consume()
                        if (!change.pressed) {
                            thumbOffset = Offset.Zero
                            continue
                        }
                        val dx   = change.position.x - centerXPx
                        val dy   = change.position.y - centerYPx
                        val dist = sqrt(dx * dx + dy * dy)
                        // Clamp thumb to outer ring for the visual, use raw delta for direction
                        thumbOffset = if (dist <= outerRadiusPx) Offset(dx, dy)
                                      else Offset(dx / dist * outerRadiusPx, dy / dist * outerRadiusPx)
                        if (dx == 0f && dy == 0f) continue
                        val dir = if (abs(dx) >= abs(dy)) {
                            if (dx > 0) Direction.RIGHT else Direction.LEFT
                        } else {
                            if (dy > 0) Direction.DOWN else Direction.UP
                        }
                        onDirection(dir)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = centerXPx
            val cy = centerYPx
            val stroke = Stroke(width = 2.dp.toPx())

            // Outer ring
            drawCircle(color = Color.White.copy(alpha = 0.07f), radius = outerRadiusPx, center = Offset(cx, cy))
            drawCircle(color = Color.White.copy(alpha = 0.30f), radius = outerRadiusPx, center = Offset(cx, cy), style = stroke)

            // Thumb
            val tx = cx + thumbOffset.x
            val ty = cy + thumbOffset.y
            drawCircle(color = COLOR_GREEN.copy(alpha = 0.30f), radius = thumbRadiusPx, center = Offset(tx, ty))
            drawCircle(color = COLOR_GREEN.copy(alpha = 0.85f), radius = thumbRadiusPx, center = Offset(tx, ty), style = stroke)
        }
    }
}

/**
 * Composite pattern: label + percentage readout + Material3 Slider grouped as one option row.
 * The slider's own pointer input captures drag events, preventing them from bubbling to the
 * background Box's clickable (which would dismiss the overlay).
 */
@Composable
private fun VolumeOption(label: String, volume: Float, onVolumeChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = {}  // consume tap so it doesn't reach the dismiss clickable
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pct = (volume * 100).toInt()
        RetroText("$label: $pct%", COLOR_GREEN_DIM, 18)
        Slider(
            value         = volume,
            onValueChange = onVolumeChange,
            valueRange    = 0f..1f,
            colors        = SliderDefaults.colors(
                thumbColor         = COLOR_GREEN,
                activeTrackColor   = COLOR_GREEN,
                inactiveTrackColor = COLOR_GREEN_DIM
            )
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
private fun BlinkingCta(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        modifier      = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = null,
            onClick           = onClick
        )
    )
}
