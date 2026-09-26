#!/usr/bin/env bash
# Runs the debug APK on the emulator and walks through the app like a person would:
# Welcome → Home → Settings → Focus session → End early → Done, plus the Do Not Disturb
# permission sheets. Saves a screenshot at each step and a screen recording, and fails if the
# app crashes.
#
# Pass 1 runs with animations off (Android's "Remove animations"), which keeps the screen still
# so uiautomator can read where each control is. Pass 2 turns animations back on and repeats
# the walk with those positions while recording, so the pictures show the app as it really moves.
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

# ---------- Pass 2: the real thing, animated and recorded ----------
anim 1
adb shell am force-stop $PKG
adb shell cmd notification allow_dnd $PKG
sleep 2
adb shell screenrecord --size 540x1200 --bit-rate 1500000 --time-limit 170 /sdcard/run.mp4 &
REC=$!
sleep 1
adb shell am start -n $PKG/.MainActivity
sleep 3;  shot 01-welcome-intro
sleep 3;  shot 02-welcome-intro-2
sleep 4;  shot 03-welcome
tap start "$START"; sleep 4; shot 04-home
tap settings "$SETTINGS"; sleep 4; shot 05-settings
tap pink-noise "$PINK"; sleep 2
adb shell input swipe 540 1900 540 900 600; sleep 3; shot 06-settings-atmosphere
adb shell input swipe 540 1900 540 700 600; sleep 3; shot 07-settings-contacts
adb shell input swipe 540 1900 540 700 600; sleep 3; shot 08-settings-safety
adb shell input keyevent 4; sleep 4
tap dial "$DIAL"; sleep 5; shot 09-session
sleep 3; shot 10-session-2
tap timer "$TIMER"; sleep 3; shot 11-end-early
tap end "$END"; sleep 5; shot 12-done
tap done "$DONE"; sleep 3
# A second session with another scene.
tap settings "$SETTINGS"; sleep 3
adb shell input swipe 540 1900 540 900 600; sleep 2
dump settings-scrolled; GALAXY2=$(where settings-scrolled "Spiral Galaxy")
tap galaxy "$GALAXY2"; sleep 2
adb shell input keyevent 4; sleep 3
tap dial "$DIAL"; sleep 5; shot 13-session-galaxy
tap timer "$TIMER"; sleep 2; tap end "$END"; sleep 3; tap done "$DONE"; sleep 2
adb shell cmd notification disallow_dnd $PKG
tap dial "$DIAL"; sleep 3; shot 14-permission
tap not-now "$NOT_NOW"; sleep 3; shot 15-barrier
tap go-back "$GO_BACK"; sleep 3

adb shell pkill -INT screenrecord
wait $REC
sleep 2
adb pull /sdcard/run.mp4 "$OUT/run.mp4"
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
