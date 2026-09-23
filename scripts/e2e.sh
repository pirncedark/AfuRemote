#!/usr/bin/env bash
# One Android emulator acts as both TV and phone client.
set -u
APK="$1"; PKG=com.afudm.afuremote; OUT=e2e-out
MP4="https://www.w3schools.com/html/mov_bbb.mp4"; STATE="e2e-out/pair-state.json"; AUTH=0
mkdir -p "$OUT"
fail() { echo "HATA: $*"; adb exec-out screencap -p > "$OUT/hata.png"; adb logcat -d > "$OUT/logcat-son.txt"; grep -A25 "FATAL EXCEPTION" "$OUT/tam.log" | head -60; exit 1; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/$1.xml" 2>/dev/null; }
wait_text() { local lim=$1 text=$2 name=$3 t=0; while [ "$t" -lt "$lim" ]; do dump "$name"; grep -q "$text" "$OUT/$name.xml" && return 0; sleep 2; t=$((t+2)); done; return 1; }
wait_log() { local lim=$1 pattern=$2 t=0; while [ "$t" -lt "$lim" ]; do adb logcat -d | grep -q "$pattern" && return 0; sleep 2; t=$((t+2)); done; return 1; }
api() { local method=$1 path=$2 body=${3:-} maxt=${4:-10}; local args=(-s -o "$OUT/resp.json" -w "%{http_code}" --max-time "$maxt" -X "$method" -H "Content-Type: application/json"); if [ "$AUTH" = 1 ]; then mapfile -t h < <(python3 scripts/afu_pair.py sign "$STATE" "$method" "$path" "$body"); args+=(-H "X-Afu-Device: ${h[0]}" -H "X-Afu-Time: ${h[1]}" -H "X-Afu-Nonce: ${h[2]}" -H "X-Afu-Sig: ${h[3]}"); fi; [ -n "$body" ] && args+=(--data-binary "$body"); curl "${args[@]}" "http://127.0.0.1:9870$path"; }
tap_text() { local xy; xy=$(python3 scripts/ui.py tap-text "$OUT/$1.xml" "$2") || return 1; adb shell input tap $xy; }

adb install -r "$APK" || fail "APK install failed"
adb shell settings put secure enabled_accessibility_services "$PKG/$PKG.tv.RemoteAccessibilityService"
adb shell settings put secure accessibility_enabled 1
adb logcat -c; adb logcat -v time > "$OUT/tam.log" &
adb shell am start -n "$PKG/.MainActivity" --es mode tv >/dev/null
# The debug build (versionCode 1) sees the published release; close the automatic update prompt if it appears.
dismiss_update() { local name=$1; dump "$name"; if grep -q "Yeni AfuRemote sürümü" "$OUT/$name.xml"; then tap_text "$name" "Sonra" || fail "update prompt could not be closed"; sleep 1; fi; }
for _ in $(seq 1 10); do dump tv_start; if grep -q "Yeni AfuRemote sürümü" "$OUT/tv_start.xml"; then dismiss_update tv_start; break; fi; sleep 2; done
wait_text 60 "AfuRemote TV" tv_home || fail "TV home screen did not appear"
adb forward tcp:9870 tcp:9870 >/dev/null
code=""; for _ in $(seq 1 15); do code=$(api GET /v1/info); [ "$code" = 200 ] && break; sleep 2; done
[ "$code" = 200 ] && grep -q '"protocol":"v1"' "$OUT/resp.json" || fail "/v1/info returned $code"
echo "OK 1: TV server"
code=$(api POST /v1/key '{"key":"vol_up"}'); [ "$code" = 401 ] || fail "unauthorized request should return 401 ($code)"
echo "OK 2: unauthorized request rejected"
PAIR_BODY=$(python3 scripts/afu_pair.py start e2e-device e2e "$STATE")
code=$(api POST /v1/pair/start "$PAIR_BODY"); [ "$code" = 200 ] || fail "pair/start returned $code"
cp "$OUT/resp.json" "$OUT/pair.json"
TVPUB=$(python3 -c "import json;print(json.load(open('$OUT/pair.json'))['tvPub'])")
PAIRID=$(python3 -c "import json;print(json.load(open('$OUT/pair.json'))['pairId'])")
PAIRCODE=$(python3 scripts/afu_pair.py finish "$STATE" "$TVPUB")
wait_text 30 "$PAIRCODE" pair_prompt || fail "TV pairing code $PAIRCODE did not appear"
( code=$(api POST /v1/pair/confirm "{\"pairId\":\"$PAIRID\"}" 70); echo "$code" > "$OUT/pair_code.txt" ) & PAIR_PID=$!
wait_text 30 "İzin ver" pair_prompt || fail "pair approval button did not appear"
tap_text pair_prompt "İzin ver" || fail "approval button not found"
wait "$PAIR_PID"; [ "$(cat "$OUT/pair_code.txt")" = 200 ] || fail "pair confirm did not return 200"
AUTH=1; echo "OK 3: secure pairing and code verified"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb logcat -c
code=$(api POST /v1/open "{\"url\":\"$MP4\",\"title\":\"e2e\"}"); [ "$code" = 200 ] || fail "/v1/open returned $code: $(cat "$OUT/resp.json")"
wait_log 60 "AfuRemotePlayer: state=PLAYING" || fail "video did not play on TV"
echo "OK 4: background video open"
adb shell cmd media_session volume --stream 3 --set 5 >/dev/null 2>&1
code=$(api POST /v1/key '{"key":"vol_up"}'); [ "$code" = 200 ] || fail "vol_up returned $code"; sleep 1
vol=$(adb shell cmd media_session volume --stream 3 --get | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+'); [ "${vol:-0}" -gt 5 ] || fail "volume did not increase ($vol)"
echo "OK 5: volume increased ($vol)"
code=$(api POST /v1/key '{"key":"play_pause"}'); [ "$code" = 200 ] || fail "play_pause returned $code"
wait_log 15 "AfuRemotePlayer: state=PAUSED" || fail "video did not pause"; echo "OK 6: pause"
code=$(api POST /v1/key '{"key":"back"}'); [ "$code" = 200 ] || fail "back returned $code"; sleep 3
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q PlayerActivity && fail "back did not close player"
echo "OK 7: back button"
# 8) phone mode discovers the TV on the same emulator
adb shell am start -n "$PKG/.MainActivity" --es mode phone >/dev/null
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
wait_text 40 "$MODEL" phone_home || fail "TV was not discovered in phone mode (model: $MODEL)"
echo "OK 8: TV discovered"

# 9) share a link into AfuRemote, approve pairing on TV, and play on TV
adb logcat -c
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'Film: $MP4'" -n "$PKG/.phone.ShareActivity" >/dev/null
sleep 2
dump share_launch
# Some emulator images display Android's target picker even for a component-qualified SEND intent.
if grep -q 'resource-id="android:id/resolver_list"' "$OUT/share_launch.xml"; then
  tap_text share_launch "AfuRemote" || fail "AfuRemote share target not found"
  dump share_choice
  grep -q 'text="Just once"' "$OUT/share_choice.xml" && tap_text share_choice "Just once"
fi
wait_text 40 "İzin ver" share_pair || fail "pair prompt did not appear during share"
tap_text share_pair "İzin ver" || fail "pair approval button was not found during share"
wait_log 60 "AfuRemotePlayer: state=PLAYING" || fail "shared link did not play on TV"
echo "OK 9: shared link opened on TV"

# 10) Download and open the in-app update installer.
adb shell am start -n "$PKG/.MainActivity" >/dev/null
# Phone mode keeps the update controls on the settings page (gear, content-desc "Ayarlar").
wait_text 40 "Ayarlar" settings_button || fail "settings button did not appear"
dismiss_update settings_button
tap_text settings_button "Ayarlar" || fail "settings button was not found"
wait_text 40 "Güncellemeleri denetle" update_check || fail "update check button did not appear"
tap_text update_check "Güncellemeleri denetle" || fail "update check button was not found"
if ! wait_text 30 "Yeni AfuRemote sürümü: 0.1.0" update_dialog; then
  dump update_rate_limit
  if grep -Eqi 'GitHub 403|rate limit' "$OUT/update_rate_limit.xml"; then
    echo "SKIP 10: GitHub rate limit"
    exit 0
  fi
  fail "new version 0.1.0 was not found"
fi
tap_text update_dialog "İndir ve kur" || fail "download button was not found"
installer_open=0
for _ in $(seq 1 90); do
  dump update_progress
  if grep -q 'text="Kur"' "$OUT/update_progress.xml"; then
    tap_text update_progress "Kur" || fail "Kur button was not selectable"
  fi
  activity=$(adb shell dumpsys activity activities)
  if printf '%s' "$activity" | grep -Eqi 'com\.google\.android\.packageinstaller|com\.android\.packageinstaller|UNKNOWN_APP_SOURCES|ManageAppExternalSources'; then
    installer_open=1
    break
  fi
  if grep -Eqi 'GitHub 403|rate limit' "$OUT/update_progress.xml"; then
    echo "SKIP 10: GitHub rate limit"
    exit 0
  fi
  sleep 2
done
[ "$installer_open" = 1 ] || fail "installer or unknown sources settings did not open within 180 seconds"
echo "OK 10: in-app update reaches installer"
adb shell input keyevent BACK
grep -q "FATAL EXCEPTION" "$OUT/tam.log" && fail "crash detected"
adb exec-out screencap -p > "$OUT/son.png"; echo "BASARILI: all steps passed"
