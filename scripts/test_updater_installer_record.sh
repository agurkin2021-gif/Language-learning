#!/usr/bin/env bash
set -euo pipefail

base_apk=${1:?base app APK required}
first_apk=${2:?first update APK required}
second_apk=${3:?second update APK required}
updater_apk=${4:?updater APK required}
reports=${5:-updater-installer-test}

target=ru.spanish.learning
updater=ru.spanish.learning.updater
mkdir -p "$reports"

collect() {
  dump_ui > "$reports/ui-final.xml" 2>/dev/null || true
  adb shell dumpsys window windows > "$reports/window.txt" 2>&1 || true
  adb logcat -d -t 4000 > "$reports/logcat.txt" 2>&1 || true
  adb shell appops get "$updater" REQUEST_INSTALL_PACKAGES > "$reports/appops.txt" 2>&1 || true
  adb shell dumpsys package "$target" > "$reports/target-package.txt" 2>&1 || true
  adb shell dumpsys package "$updater" > "$reports/updater-package.txt" 2>&1 || true
}
trap collect EXIT

installed_version() {
  adb shell dumpsys package "$target" | python3 -c 'import re,sys; m=re.search(r"versionCode=(\d+)",sys.stdin.read()); print(m.group(1) if m else "0")'
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/window.xml 2>/dev/null || true
}

tap_main_update() {
  for _ in $(seq 1 30); do
    dump_ui > "$reports/main-ui.xml"
    if python3 - "$reports/main-ui.xml" <<'PY' > "$reports/main-tap.txt"
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") != "Обновление":
        continue
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2 = map(int,m.groups())
    print((x1+x2)//2, (y1+y2)//2)
    raise SystemExit(0)
raise SystemExit(1)
PY
    then
      read -r x y < "$reports/main-tap.txt"
      adb shell input tap "$x" "$y"
      return 0
    fi

    # Android 14's package installer can briefly remain above the freshly
    # updated app after the first approved handoff. That dialog belongs to the
    # already-completed first install, not to the second update under test.
    if grep -q 'package="com.google.android.packageinstaller"' "$reports/main-ui.xml"; then
      cp "$reports/main-ui.xml" "$reports/stale-first-confirmation.xml"
      adb shell input keyevent KEYCODE_BACK
    fi
    sleep 1
  done
  echo "Main update button did not become available." >&2
  return 1
}

tap_install_confirmation() {
  dump_ui > "$reports/confirmation.xml"
  python3 - "$reports/confirmation.xml" <<'PY' > "$reports/tap.txt"
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
wanted = {"Update","Install","Обновить","Установить"}
for node in root.iter("node"):
    if node.attrib.get("text") not in wanted:
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

confirmation_visible() {
  local xml
  xml=$(dump_ui)
  grep -Eqi 'text="(Update|Install|Обновить|Установить)"' <<<"$xml"
}

copy_candidate() {
  local source=$1
  adb push "$source" /data/local/tmp/spanish-candidate.apk >/dev/null
  adb shell run-as "$updater" mkdir -p files
  adb shell run-as "$updater" cp /data/local/tmp/spanish-candidate.apk files/candidate.apk
  adb shell rm -f /data/local/tmp/spanish-candidate.apk
}

start_updater() {
  local appdir
  appdir=$(adb shell run-as "$updater" pwd | tr -d '\r')
  adb shell am start -W -n "$updater/.UpdaterActivity" --es test_apk_path "$appdir/files/candidate.apk" >/dev/null
}

wait_for_version() {
  local expected=$1
  local allow_confirmation=$2
  for _ in $(seq 1 90); do
    if [[ $(installed_version) -eq $expected ]]; then
      return 0
    fi
    xml=$(dump_ui)
    if grep -q 'text="Обновление не выполнено."' <<<"$xml"; then
      printf '%s\n' "$xml" > "$reports/updater-failure.xml"
      echo "Updater reported a failure before PackageInstaller completed." >&2
      return 1
    fi
    focus=$(adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -n 2 || true)
    if grep -Eqi 'settings/.Settings|settings.*ManageExternalSources' <<<"$focus"; then
      printf '%s\n' "$focus" > "$reports/unexpected-settings.txt"
      echo "Updater still lacks install-source permission." >&2
      return 1
    fi
    if confirmation_visible; then
      if [[ $allow_confirmation == yes ]]; then
        tap_install_confirmation
        allow_confirmation=no
      else
        echo "Unexpected second Android install confirmation." >&2
        dump_ui > "$reports/unexpected-second-confirmation.xml"
        return 1
      fi
    fi
    sleep 1
  done
  echo "Timed out waiting for version $expected." >&2
  return 1
}

installer_line() {
  adb shell dumpsys package "$target" | grep -E -m1 'installerPackageName=|Installer package name:' || true
}

adb install -r "$base_apk" >/dev/null
adb install -r "$updater_apk" >/dev/null
adb shell appops set "$updater" REQUEST_INSTALL_PACKAGES allow

base_version=$(installed_version)
first_version=$("$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$first_apk" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -n1)
second_version=$("$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$second_apk" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -n1)
if [[ -z $first_version || -z $second_version || $base_version -ge $first_version || $first_version -ge $second_version ]]; then
  echo "Fixture versions are invalid." >&2
  exit 1
fi

copy_candidate "$first_apk"
start_updater
wait_for_version "$first_version" yes
sleep 2
first_installer=$(installer_line)
printf '%s\n' "$first_installer" > "$reports/installer-after-first.txt"
if ! grep -q "$updater" <<<"$first_installer"; then
  echo "Updater did not become installer of record after the approved transition." >&2
  exit 1
fi

adb shell am force-stop "$updater"
copy_candidate "$second_apk"
updater_appdir=$(adb shell run-as "$updater" pwd | tr -d '\r')
adb logcat -c
adb shell am start -W -n "$target/.MainActivity" --es updater_test_path "$updater_appdir/files/candidate.apk" >/dev/null
sleep 1
tap_main_update
sleep 1
adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' > "$reports/focus-after-main-button.txt" || true
wait_for_version "$second_version" no
sleep 2
second_installer=$(installer_line)
printf '%s\n' "$second_installer" > "$reports/installer-after-second.txt"
if ! grep -q "$updater" <<<"$second_installer"; then
  echo "Updater lost installer-of-record status." >&2
  exit 1
fi

python3 - "$reports/result.json" "$base_version" "$first_version" "$second_version" <<'PY'
import json, sys
path, base, first, second = sys.argv[1], *(map(int, sys.argv[2:]))
with open(path, "w", encoding="utf-8") as out:
    json.dump({
        "success": True,
        "base_version": base,
        "approved_transition_version": first,
        "silent_followup_version": second,
        "installer_of_record": "ru.spanish.learning.updater",
        "second_confirmation_required": False,
    }, out, ensure_ascii=False, indent=2)
    out.write("\n")
PY
cat "$reports/result.json"
