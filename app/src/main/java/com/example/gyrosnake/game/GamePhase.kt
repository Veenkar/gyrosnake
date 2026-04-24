package com.example.gyrosnake.game

/**
 * State pattern: discrete lifecycle phases of the game.
 * GameEngine transitions between these; the UI renders differently per phase.
 */
enum class GamePhase {
    MENU,       // title screen — waiting for player to start
    PLAYING,    // active game loop
    PAUSED,     // game loop suspended, overlay shown
    GAME_OVER   // collision detected, results displayed
}
