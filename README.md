# GyroSnake

A retro snake game for Android controlled by tilting or flicking your phone. No buttons needed.

## Gameplay

- Steer the snake by tilting your phone (or flicking your wrist)
- Eat food to grow longer and score points
- Snake speeds up as your score increases
- Wrap around walls — no wall deaths
- Self-collision ends the game

### Power-ups

| Power-up | Effect |
|----------|--------|
| 🌈 Disco  | Rainbow screen effect |
| 😊 Candy  | Snake turns pink, 1.5× faster |
| 🍃 Leaf   | Slows down, 3 foods appear at once |

## Controls

Two control schemes available in Settings:

- **Tilt (Gravity)** — hold phone flat facing the ceiling, tilt to steer
- **Flick (Gyro)** — hold phone in any position, flick your wrist to turn

## Building

Requirements: Android Studio, JDK 17+

```bash
./gradlew assembleDebug
```

For a signed release AAB (Play Store), run `sign_release.bat` on Windows.

## Tech Stack

- Kotlin
- Jetpack Compose (Canvas rendering)
- Android Gravity / Gyroscope sensors
- Coroutines + StateFlow game loop
- No third-party game engine

## Developer

Made by [SelerLabs](https://github.com/Veenkar)
