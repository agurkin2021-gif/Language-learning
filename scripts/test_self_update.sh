#!/usr/bin/env bash
set -euo pipefail

# Run only on the disposable API 34 emulator created by the CI workflow.
# The host installs only the base APK. The next APK must be installed by
# AppUpdater inside the application, using the ordinary PackageInstaller path.
base_apk=${1:?base APK path required}
next_apk=${2:?next APK path required}
tests_apk=${3:?instrumentation APK path required}
reports=${4:-self-update-reports}
package=ru.spanish.learning
runner=ru.spanish.learning.test/android.test.InstrumentationTestRunner
test_class=ru.spanish.learning.SelfUpdateTest
mkdir -p "$reports"

if [[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') != 1 ]]; then
  echo 'Refusing self-update integration test on a non-emulator device.' >&2
  exit 1
fi
if [[ $(adb shell getprop ro.build.version.sdk | tr -d '\r') != 34 ]]; then
  echo 'This integration test requires the Android 14 / API 34 emulator.' >&2
  exit 1
fi

installed_version() {
  adb shell dumpsys package "$package" | python3 -c 'import re,sys; m=re.search(r"versionCode=(\d+)",sys.stdin.read()); print(m.group(1) if m else "0")'
}
save_update_state() {
  adb exec-out run-as "$package" cat shared_prefs/app_updates.xml > "$reports/update-state.xml" 2>/dev/null || true
}
collect_failure() {
  save_update_state
  adb logcat -d -t 1500 > "$reports/logcat.txt" 2>&1 || true
  adb shell dumpsys package "$package" > "$reports/package.txt" 2>&1 || true
}
trap collect_failure ERR

adb install -r -t "$base_apk"
adb install -r -t "$tests_apk"
previous=$(installed_version)
expected=$((previous + 1))
[[ $previous -gt 0 ]]

# This represents the user's one-time "allow from this source" choice. Do not
# disable the package verifier or any other installation protection in this test.
adb shell appops set "$package" REQUEST_INSTALL_PACKAGES allow
adb shell run-as "$package" mkdir -p files/updates
adb shell "run-as $package sh -c 'cat > files/updates/self-update-fixture.apk'" < "$next_apk"

set +e
timeout 60s adb shell am instrument -w -r -e selfUpdate true \
  -e class "$test_class#testPrepareAndRequestSelfUpdate" "$runner" \
  > "$reports/install-request.txt" 2>&1 &
request_pid=$!
set -e

actual=$previous
for attempt in $(seq 1 60); do
  actual=$(installed_version)
  if [[ $actual -eq $expected ]]; then break; fi
  sleep 1
done
set +e
wait "$request_pid"
request_exit=$?
set -e
printf '%s\n' "$request_exit" > "$reports/install-request-exit.txt"

if [[ $actual -ne $expected ]]; then
  echo "Self-update failed: expected versionCode $expected, found $actual." >&2
  collect_failure
  exit 1
fi

# The install intentionally kills the old instrumentation. Its exit code is
# informational. Actual higher version AND the production success callback AND
# an independent fresh-process data test are all mandatory.
for attempt in $(seq 1 20); do
  save_update_state
  if python3 - "$reports/update-state.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
    values = {n.attrib.get("name"): n.attrib.get("value", n.text) for n in root}
    success = values.get("state") == "updated" and values.get("last_result") == "0"
except (OSError, ET.ParseError):
    success = False
raise SystemExit(0 if success else 1)
PY
  then break; fi
  sleep 1
done

timeout 60s adb shell am instrument -w -r -e selfUpdate true \
  -e class "$test_class#testUpdatedVersionKeepsDataAndNeedsNoConfirmation" "$runner" \
  > "$reports/after-update.txt" 2>&1
adb exec-out run-as "$package" cat files/self-update-result.json > "$reports/result.json"
python3 - "$reports/result.json" "$previous" "$expected" <<'PY'
import json, sys
with open(sys.argv[1]) as stream:
    result = json.load(stream)
assert result["success"] is True, result
assert result["sdk"] == 34, result
assert result["previous_version"] == int(sys.argv[2]), result
assert result["installed_version"] == int(sys.argv[3]), result
assert result["package_installer_status"] == 0, result
assert result["manual_confirmation_required"] is False, result
assert result["sqlite_cards_archive_story_position_preserved"] is True, result
assert result["shared_preferences_preserved"] is True, result
print(json.dumps(result, ensure_ascii=False, indent=2))
PY
