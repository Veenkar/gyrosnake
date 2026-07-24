package com.gyrosnake.game

import androidx.annotation.StringRes
import com.gyrosnake.R

/**
 * Value Object / Enumeration pattern: represents the set of available control schemes.
 * Adding a new scheme only requires a new entry here and a mapping in GameViewModel.
 * Label/description are string resource ids so they follow the app's locale
 * (system by default, or the user's in-app override).
 */
enum class ControlScheme(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    POINT   (R.string.scheme_point_label,   R.string.scheme_point_desc),
    OVERLAY (R.string.scheme_overlay_label, R.string.scheme_overlay_desc),
    FLICK   (R.string.scheme_flick_label,   R.string.scheme_flick_desc),
    GRAVITY (R.string.scheme_gravity_label, R.string.scheme_gravity_desc),
}
