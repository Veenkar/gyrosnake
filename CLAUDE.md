# GyroSnake — Claude Development Guide

## Project

Android gyroscope snake game built with Jetpack Compose.
- Package: `com.gyrosnake`
- Min SDK: 31 (Android 12)
- Play Store: published by SelerLabs

## Build & Flash

All bat scripts read their local config (`JAVA_HOME`, keystore path, key alias,
app id) from a gitignored `.env` via `load_env.bat`. Copy `.env.example` to `.env`
on a new machine.

Build debug APK and flash it to the connected device:
```
cmd.exe /c "flash.bat"
```

Build a signed release APK and flash it:
```
cmd.exe /c "flash_release.bat"
```
Prompts for keystore passwords unless `STORE_PASS`/`KEY_PASS` are set in `.env`.
The device must not already hold the debug build — signatures differ, so
`adb uninstall com.gyrosnake` first if the install fails.

Both flash scripts take `--no-build` to install the last build without rebuilding.

Run any other gradle task: `cmd.exe /c "build_release.bat <task>"`.

Build signed release AAB for the Play Store: run `sign_release.bat`. An .aab
cannot be installed with adb — use `flash_release.bat` to test on hardware.

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
- **Before handing control back to the user, run `git status`** and commit anything
  outstanding. Commit as soon as a change is written — do not hold it back to test
  or verify first, since an interrupted verification leaves the work uncommitted.
- **Never push** — commit only, never `git push`, unless user explicitly asks for it in that message
- **Bump version before Play Store releases** — both versionCode and versionName
- **Build and flash to device** after each change to verify on hardware
- **No PNG files with RGB mode** — AAPT2 crashes on release builds; always use RGBA
