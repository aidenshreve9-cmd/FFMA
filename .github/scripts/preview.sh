#!/usr/bin/env bash
# Runs the debug APK on the emulator and walks through the app like a person would:
# Welcome → Home → Settings → Focus session → End early → Done, plus the Do Not Disturb
# permission sheets. Saves a screenshot at each step and fails if the app crashes.
#
# Pass 1 runs with animations off (Android's "Remove animations"), which keeps the screen still
# so uiautomator can read where each control is. Pass 2 turns animations back on and repeats
# the core loop with those positions, so the pictures show the app as it really looks.
set -u
PKG=com.focusfriend.app
OUT=preview
mkdir -p "$OUT/ui"
: > "$OUT/steps.txt"

log() { echo "$*"; echo "$*" >> "$OUT/steps.txt"; }
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k "$1"; done; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "screenshot $1"; }
dump() { timeout 40 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb exec-out cat /sdcard/ui.xml > "$OUT/ui/$1.xml"; }
where() { python3 .github/scripts/find_node.py "$OUT/ui/$1.xml" "$2"; }
tap() { # tap NAME "x y"
  if [ -n "$2" ]; then adb shell input tap $2; log "tap $1 at $2"; else log "MISSING $1"; fi
}
alive() { adb shell pidof $PKG > /dev/null; }

adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell cmd notification allow_dnd $PKG
adb logcat -c

# ---------- Pass 1: find the controls (animations off) ----------
anim 0
adb shell am start -W -n $PKG/.MainActivity
sleep 4
dump welcome;  START=$(where welcome "Start")
tap start "$START"; sleep 3
dump home;     SETTINGS=$(where home "Settings"); DIAL=$(where home "Focus,")
tap settings "$SETTINGS"; sleep 3
dump settings; PINK=$(where settings "Pink Noise"); GALAXY=$(where settings "Spiral Galaxy"); QUANTUM=$(where settings "Quantum Nebula")
adb shell input keyevent 4; sleep 3
tap dial "$DIAL"; sleep 4
dump session;  TIMER=$(where session "~left. Tap to end early")
tap timer "$TIMER"; sleep 3
dump confirm;  END=$(where confirm "End session")
tap end "$END"; sleep 3
dump done;     DONE=$(where done "Done")
tap done "$DONE"; sleep 3

# Without Do Not Disturb access, starting asks for it first.
adb shell cmd notification disallow_dnd $PKG
tap dial "$DIAL"; sleep 3
dump permission; NOT_NOW=$(where permission "Not now")
tap not-now "$NOT_NOW"; sleep 3
dump barrier; GO_BACK=$(where barrier "Go back")
tap go-back "$GO_BACK"; sleep 2
alive && log "pass 1: app still running" || log "pass 1: APP NOT RUNNING"

# ---------- Pass 2: the core loop, animated, one screenshot per screen ----------
# No screen recording or sound preview: together they froze the emulator last time.
anim 1
adb shell am force-stop $PKG
adb shell cmd notification allow_dnd $PKG
sleep 2
adb shell am start -n $PKG/.MainActivity
sleep 10; shot 01-welcome
tap start "$START"; sleep 6; shot 02-home
tap dial "$DIAL"; sleep 8; shot 03-session
tap timer "$TIMER"; sleep 4; shot 04-end-early
tap end "$END"; sleep 6; shot 05-done
tap done "$DONE"; sleep 4; shot 06-home-again
alive && log "pass 2: app still running" || log "pass 2: APP NOT RUNNING"

adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d | grep -E "AndroidRuntime|focusfriend|FocusFriend|RuntimeShader|AudioTrack" | tail -300 > "$OUT/logcat.txt"
if [ -s "$OUT/crash.txt" ]; then
  echo "::error title=App crashed::$(head -60 "$OUT/crash.txt" | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  exit 1
fi
if grep -q "MISSING\|NOT RUNNING" "$OUT/steps.txt"; then
  echo "::error title=Walkthrough incomplete::$(grep "MISSING\|NOT RUNNING" "$OUT/steps.txt" | tr '\n' ';')"
  exit 1
fi
echo "Walkthrough finished without crashes."
