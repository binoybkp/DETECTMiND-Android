#!/usr/bin/env bash
# Smoke script for DETECTMiND Android app.
# Requires: adb in PATH, emulator already booted and listed in `adb devices`.
# Run from the repo root: bash .claude/skills/run-detectmind/smoke.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.research.detectmind"
ACTIVITY=".ui.MainActivity"
SCREENSHOT="/tmp/detectmind_smoke.png"

echo "=== Build ==="
cd "$REPO_ROOT"
./gradlew assembleDebug

echo ""
echo "=== Install ==="
# -d allows version downgrade (debug builds may have lower versionCode than what's on the emulator)
adb install -r -d "$APK"

echo ""
echo "=== Launch ==="
adb shell am start -n "$PACKAGE/$PACKAGE$ACTIVITY"
sleep 7

echo ""
echo "=== Screenshot ==="
adb exec-out screencap -p > "$SCREENSHOT"
echo "Screenshot saved to $SCREENSHOT"

echo ""
echo "=== Done ==="
