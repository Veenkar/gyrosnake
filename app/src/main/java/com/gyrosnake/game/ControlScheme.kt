package com.gyrosnake.game

/**
 * Value Object / Enumeration pattern: represents the set of available control schemes.
 * Adding a new scheme only requires a new entry here and a mapping in GameViewModel.
 */
enum class ControlScheme(val label: String, val description: String) {
    OVERLAY ("OVERLAY",        "Virtual joystick on screen"),
    FLICK   ("FLICK (GYRO)",   "Flick wrist to turn"),
    GRAVITY ("TILT (GRAVITY)", "Hold flat, tilt to steer\nPlace phone on desk, screen facing ceiling"),
}
