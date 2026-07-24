package com.gyrosnake.game

import androidx.annotation.StringRes
import com.gyrosnake.R

/**
 * Which diagram a tutorial step draws. The renderer (GameScreen) maps each
 * constant to a Canvas drawing — keeping the drawing choice as data rather than
 * a lambda keeps this file free of any UI dependency.
 */
enum class TutorialVisual {
    SNAKE,      // snake body + food, the basic goal
    JOYSTICK,   // virtual joystick thumb sweeping
    FLICK,      // phone flicking left/right
    TILT,       // phone tilting, gravity arrow
    POWERUPS    // the three powerup sprites
}

/**
 * Value Object: one page of the tutorial. Immutable, holds only string resource
 * ids so the walkthrough follows the app's locale like every other screen.
 */
data class TutorialStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val visual: TutorialVisual
)

/**
 * Singleton registry of tutorial pages (Object/Registry pattern).
 *
 * Open/Closed: adding a page means appending a [TutorialStep] here plus a
 * branch in the renderer for a new [TutorialVisual] — neither the overlay
 * layout, the paging logic, nor GameEngine changes.
 */
object TutorialContent {
    val steps: List<TutorialStep> = listOf(
        TutorialStep(R.string.tut_goal_title,     R.string.tut_goal_body,     TutorialVisual.SNAKE),
        TutorialStep(R.string.tut_overlay_title,  R.string.tut_overlay_body,  TutorialVisual.JOYSTICK),
        TutorialStep(R.string.tut_flick_title,    R.string.tut_flick_body,    TutorialVisual.FLICK),
        TutorialStep(R.string.tut_gravity_title,  R.string.tut_gravity_body,  TutorialVisual.TILT),
        TutorialStep(R.string.tut_powerups_title, R.string.tut_powerups_body, TutorialVisual.POWERUPS),
        TutorialStep(R.string.tut_pause_title,    R.string.tut_pause_body,    TutorialVisual.SNAKE),
    )
}
