#!/usr/bin/env bash
set -euo pipefail

base_apk=${1:?previous phone APK required}
expected_run=${2:?expected GitHub run number required}
reports=${3:-published-update-smoke}
package=ru.spanish.learning
activity=.MainActivity
expected_version=$((100 + expected_run))
mkdir -p "$reports"

if [[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') != 1 ]]; then
  echo 'Refusing published update smoke test on a non-emulator device.' >&2
  exit 1
fi
if [[ $(adb shell getprop ro.build.version.sdk | tr -d '\r') != 34 ]]; then
  echo 'Published update smoke test requires Android 14 / API 34.' >&2
  exit 1
fi

installed_version() {
  adb shell dumpsys package "$package" | python3 -c 'import re,sys; m=re.search(r"versionCode=(\d+)",sys.stdin.read()); print(m.group(1) if m else "0")'
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/window.xml 2>/dev/null || true
}

tap_text() {
  local wanted=$1
  dump_ui > "$reports/ui.xml"
  python3 - "$reports/ui.xml" "$wanted" <<'PY' > "$reports/tap.txt"
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
wanted = sys.argv[2]
for node in root.iter("node"):
    if node.attrib.get("text") != wanted:
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2 = map(int,m.groups())
    print((x1+x2)//2, (y1+y2)//2)
    raise SystemExit(0)
raise SystemExit(1)
PY
  read -r x y < "$reports/tap.txt"
  adb shell input tap "$x" "$y"
}

collect() {
  dump_ui > "$reports/ui-final.xml" || true
  adb shell dumpsys window windows > "$reports/window.txt" 2>&1 || true
  adb shell dumpsys package "$package" > "$reports/package.txt" 2>&1 || true
  adb logcat -d -t 2500 > "$reports/logcat.txt" 2>&1 || true
}
trap collect EXIT

adb install -r "$base_apk"
previous=$(installed_version)
if [[ $previous -le 0 || $previous -ge $expected_version ]]; then
  echo "Previous APK versionCode $previous is not older than expected $expected_version." >&2
  exit 1
fi

# Represents the user's one-time Android permission to let this app update itself.
adb shell appops set "$package" REQUEST_INSTALL_PACKAGES allow
adb shell am force-stop "$package"
adb shell am start -W -n "$package/$activity" > "$reports/start.txt"
sleep 2

tap_text "Обновление"
printf 'clicked\n' > "$reports/button.txt"
sleep 1
xml_after_click=$(dump_ui)
if grep -Eqi 'text="(Закрыть|Проверить|Разрешить обновления|Подтвердить в Android)"' <<<"$xml_after_click"; then
  echo 'Update button opened an app-side confirmation/progress dialog; one-tap contract failed.' >&2
  printf '%s\n' "$xml_after_click" > "$reports/app-dialog-after-click.xml"
  exit 1
fi

actual=$previous
for attempt in $(seq 1 120); do
  actual=$(installed_version)
  if [[ $actual -eq $expected_version ]]; then
    break
  fi

  # The update button must not hand the user off to a browser or file manager.
  focus=$(adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -n 2 || true)
  printf '%s\n' "$focus" > "$reports/focus-latest.txt"
  if grep -Eqi 'documentsui|chrome|browser|downloadsui' <<<"$focus"; then
    echo "Update flow incorrectly opened a browser/file manager: $focus" >&2
    exit 1
  fi

  # On reference Android 14 this flow is required to finish without a second install tap.
  xml=$(dump_ui)
  if grep -Eqi 'text="(Install|Update|Установить|Обновить)"' <<<"$xml" &&
     ! grep -q 'text="Обновление"' <<<"$xml"; then
    echo 'Android requested an extra installation confirmation; one-tap update contract failed.' >&2
    printf '%s\n' "$xml" > "$reports/installer-confirmation.xml"
    exit 1
  fi
  sleep 1
done

if [[ $actual -ne $expected_version ]]; then
  echo "One-tap published update failed: expected versionCode $expected_version, found $actual." >&2
  exit 1
fi

sleep 2

python3 - "$reports/result.json" "$previous" "$actual" <<'PY'
import json, sys
path, previous, actual = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
with open(path, "w", encoding="utf-8") as out:
    json.dump({
        "success": True,
        "android_api": 34,
        "previous_version_code": previous,
        "installed_version_code": actual,
        "update_button_clicked": True,
        "browser_or_file_manager_opened": False,
        "second_install_tap_required": False,
    }, out, ensure_ascii=False, indent=2)
    out.write("\n")
PY
cat "$reports/result.json"
