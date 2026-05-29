---
name: run-detectmind
description: run, start, build, launch, screenshot, install, test the DETECTMiND Android app on an emulator
---

DETECTMiND is a Kotlin/Compose Android app. It is driven via `adb` — build with Gradle, install to an emulator, launch the activity, capture screenshots with `adb exec-out screencap -p`.

The smoke script at `.claude/skills/run-detectmind/smoke.sh` is the agent path: it builds, installs, launches, and saves a screenshot to `/tmp/detectmind_smoke.png`.

## Prerequisites

- Android SDK with `adb` and `emulator` in PATH (comes with Android Studio on macOS at `~/Library/Android/sdk/`)
- AVD named `Medium_Phone_API_35` (or `Medium_Phone_API_35_2`) — list with `emulator -list-avds`
- Java 17+ (Gradle wrapper handles the rest)

## Build

```bash
./gradlew assembleDebug
# APK lands at: app/build/outputs/apk/debug/app-debug.apk
```

## Run (agent path)

**Step 1 — Start the emulator** (skip if already running):

```bash
emulator -avd Medium_Phone_API_35 -no-snapshot-load &
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do sleep 3; done
echo "Boot complete"
```

**Step 2 — Run the smoke script:**

```bash
bash .claude/skills/run-detectmind/smoke.sh
```

Screenshot lands at `/tmp/detectmind_smoke.png`. Read it with the `Read` tool to verify the UI.

**Step 3 — Navigate / interact manually via adb:**

```bash
# Take a fresh screenshot anytime
adb exec-out screencap -p > /tmp/shot.png

# Tap by screen coordinates (useful for buttons)
adb shell input tap <x> <y>

# Scroll down
adb shell input swipe 540 900 540 300

# Go back
adb shell input keyevent KEYCODE_BACK

# Force-stop the app
adb shell am force-stop com.research.detectmind

# Re-launch
adb shell am start -n com.research.detectmind/.ui.MainActivity
```

## Run (human path)

Open Android Studio → select `Medium_Phone_API_35` AVD → click Run. The app launches in the emulator window.

## Gotchas

- **`INSTALL_FAILED_VERSION_DOWNGRADE`** — the emulator has a higher `versionCode` than the debug build. Fix: use `adb install -r -d` (the `-d` flag allows downgrade). The smoke script already does this.
- **Activity not found as `.MainActivity`** — the launcher activity is `.ui.MainActivity` (full: `com.research.detectmind/.ui.MainActivity`). Using the short form fails.
- **Emulator boots but `sys.boot_completed` never returns `1`** — wait longer; the first cold boot on `Medium_Phone_API_35` takes ~60s. The `until` loop in step 1 handles this.
- **`adb devices` shows `unauthorized`** — open the emulator window and accept the RSA key prompt, or run `adb kill-server && adb start-server`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `emulator: command not found` | Add `~/Library/Android/sdk/emulator` to PATH |
| `adb: command not found` | Add `~/Library/Android/sdk/platform-tools` to PATH |
| Build fails with Hilt/KSP errors | Run `./gradlew clean assembleDebug` |
| Screenshot is blank/black | App may not have finished launching — increase the `sleep 3` in smoke.sh |
