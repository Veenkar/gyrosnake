# GyroSnake — Claude Development Guide

## Project

Android gyroscope snake game built with Jetpack Compose.
- Package: `com.gyrosnake`
- Min SDK: 31 (Android 12)
- Play Store: published by SelerLabs

## Build & Flash

Build debug APK (from repo root on Windows/WSL):
```
cmd.exe /c "build_release.bat assembleDebug"
```

Flash to connected device:
```
adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

Build signed release AAB for Play Store: run `sign_release.bat` (prompts for keystore passwords).
- Keystore: `C:\Users\xeenk\Documents\android_key\selerlabs`
- Key alias: `key_selerlabs`

Both bat files are gitignored. `build_release.bat` sets `JAVA_HOME` to Android Studio's JBR.

## Versioning

Bump both `versionCode` (integer) and `versionName` (string) in `app/build.gradle.kts` before each Play Store release.
Current: versionCode=11, versionName="2.0"

## Architecture

```
com.gyrosnake/
  GameViewModel.kt          — ViewModel: owns engine, input adapter, settings
  game/
    GameEngine.kt           — Game loop, collision, scoring, effect application
    GameUiState.kt          — Immutable snapshot emitted each tick via StateFlow
    GameBoard.kt            — Grid dimensions, cell helpers
    GamePhase.kt            — MENU, PLAYING, PAUSED, GAME_OVER, SETTINGS
    EntityFactory.kt        — Snake and food creation (Factory pattern)
    SnakeState.kt           — Immutable snake body + direction
    Food.kt                 — Position + optional PowerUpEffect
    PowerUpEffect.kt        — Sealed class hierarchy of powerup types
    ActiveEffect.kt         — Runtime effect with expiry timestamp
    Direction.kt            — UP/DOWN/LEFT/RIGHT + delta
    Point.kt                — 2D grid coordinate
    ControlScheme.kt        — GRAVITY / FLICK enum with label + description
  input/
    TiltInputAdapter.kt     — Interface (Strategy pattern) for swappable input
    GyroscopeFlickAdapter.kt — Flick gesture detection via angular velocity
  render/
    GameScreen.kt           — Top-level composable, overlays, visual effects
    GameCanvas.kt           — Pure Canvas renderer, no game logic
  audio/
    SoundManager.kt         — (reserved)
  data/
    SettingsRepository.kt   — SharedPreferences singleton for persistent settings
```

## OOP Requirements

**Always apply and document OOP design patterns.** This is a hard requirement:
- Use appropriate patterns (Factory, Strategy, Observer, Decorator, State, Template Method, etc.)
- Add a short comment near the pattern usage naming which pattern it is and why
- The sealed class `PowerUpEffect` is intentionally Open/Closed — new powerups extend it without touching existing logic
- `TiltInputAdapter` is a Strategy — new input methods implement the interface, no other code changes

## Powerup System

Powerups spawn randomly (1-in-5 chance per food, equally distributed among types).
Each powerup: random duration 20–60s, tracked in `activeEffects: List<ActiveEffect>`.

| Name  | Sprite            | Effect                                      | Screen effect                  |
|-------|-------------------|---------------------------------------------|--------------------------------|
| Disco | 3×3 rainbow dots  | Visual only                                 | Rainbow bands + canvas wobble  |
| Candy | Blue smiley face  | Snake turns pink, 1.5× faster               | None                           |
| Leaf  | Green leaf shape  | Snake slows to 0.6×, 3 foods on board       | Breathing scale + green vignette |

To add a new powerup:
1. Add `object NewPowerup : PowerUpEffect()` in `PowerUpEffect.kt`
2. Add a branch in `EntityFactory.spawnFood` random selection
3. Add mechanic in `GameEngine.tick()` (speed/food/etc.)
4. Add renderer branch in `GameCanvas.drawFoods`
5. Add screen effect in `GameScreen` if needed

## UI Rules

**Every overlay must fit on screen without scrolling.** Scrolling breaks the retro
blinking-CTA feel — the player should never have to discover content below the fold.
Size content to the viewport instead: give the flexible element `Modifier.weight(1f)`
so it absorbs leftover space, keep font sizes and paddings tight, and split content
across pages rather than letting a column grow.

Only genuinely unbounded lists may scroll (currently the language picker, 22 entries).
If a new screen doesn't fit, page it — don't add `verticalScroll`.

Verify on device at the smallest supported size before committing any overlay change.

## Controls

Two schemes (selectable in Settings, persisted via `SettingsRepository`):
- **GRAVITY** — hold phone flat, tilt to steer (uses gravity sensor)
- **FLICK** — flick wrist to turn (gyroscope angular velocity threshold)

Gyroscope flick: threshold 2.0 rad/s, 200ms cooldown, 700ms opposite-direction cooldown.
Axis mapping for ROTATION_90: `screenRight = wx`, `screenUp = wy`.

## Workflow Rules

- **Always commit after every completed feature or fix** — do not batch multiple features into one commit. No need to ask first; just commit.
- **Never push** — commit only, never `git push`, unless user explicitly asks for it in that message
- **Bump version before Play Store releases** — both versionCode and versionName
- **Build and flash to device** after each change to verify on hardware
- **No PNG files with RGB mode** — AAPT2 crashes on release builds; always use RGBA
