#!/usr/bin/env bash
set -euo pipefail
# True end-to-end test: download exactly the public signed APKs a phone downloads.
target=ru.spanish.learning
family_updater=ru.spanish.learning.familyupdater
repo=agurkin2021-gif/Language-learning
root="https://raw.githubusercontent.com/$repo/refs/heads"
reports=published-family-smoke
if [[ $# -gt 0 ]]; then reports="$1"; fi
mkdir -p "$reports"
trap 'adb logcat -d -t 5000 > "$reports/logcat.txt" 2>/dev/null || true; adb shell dumpsys package "$target" > "$reports/target.txt" 2>/dev/null || true; adb shell dumpsys package "$family_updater" > "$reports/updater.txt" 2>/dev/null || true' EXIT

test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 34

curl_asset() {
  local branch="$1" name="$2" out="$3"
  curl --fail --location --retry 3 --connect-timeout 12 --max-time 120 --silent --show-error \
    "$root/$branch/$name" -o "$out"
  test -s "$out"
}
curl_asset phone-builds build-info.json "$reports/old.json"
curl_asset family-builds build-info.json "$reports/family.json"
curl_asset family-updater-v2-builds build-info.json "$reports/updater.json"
curl_asset phone-builds SpanishLearning.apk "$reports/old.apk"
curl_asset updater-builds SpanishLearningUpdater.apk "$reports/legacy-updater.apk"
curl_asset family-updater-v2-builds SpanishLearningUpdater.apk "$reports/updater.apk"
curl_asset family-builds SpanishLearning.apk "$reports/family.apk"

python3 - "$reports" <<'PY'
import hashlib,json,pathlib,sys
p=pathlib.Path(sys.argv[1])
old=json.loads((p/"old.json").read_text())
fam=json.loads((p/"family.json").read_text())
upd=json.loads((p/"updater.json").read_text())
assert old['channel']=='phone-builds' and old['version_code']==469
assert fam['channel']=='family-builds' and fam['version_code']>469
assert upd['channel']=='family-updater-v2-builds'
assert upd['application_id']=='ru.spanish.learning.familyupdater'
assert fam['run_number']==upd['run_number']
for meta,file in [(old,'old.apk'),(fam,'family.apk'),(upd,'updater.apk')]:
    assert hashlib.sha256((p/file).read_bytes()).hexdigest()==meta['apk_sha256'],file
assert fam['certificate_sha256']==upd['certificate_sha256']==old['certificate_sha256']
print("LIVE FEED VALID:",old['version_code'],"->",fam['version_code'],"updater",upd['version_code'])
PY

get_version() {
  adb shell dumpsys package "$target" | python3 -c 'import re,sys; m=re.search(r"versionCode=(\d+)",sys.stdin.read()); print(m.group(1) if m else "0")'
}
expected=$(python3 - "$reports/family.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1]))['version_code'])
PY
)

# Install actual published legacy app AND legacy updater, as on an old phone.
adb install -r "$reports/old.apk"
adb install -r "$reports/legacy-updater.apk"
adb install -r "$reports/updater.apk"
test "$(get_version)" = 469
adb shell appops set "$family_updater" REQUEST_INSTALL_PACKAGES allow
echo "EMULATOR installed legacy app and both updaters; starting independent family updater."

adb logcat -c
adb shell am start -W -n "$family_updater/ru.spanish.learning.updater.UpdaterActivity" > "$reports/start.txt"
dump_ui() {
  adb shell uiautomator dump /sdcard/family-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/family-ui.xml 2>/dev/null || true
}
tap_android_confirmation() {
  local xml="$1"
  python3 - "$xml" <<'PY' > "$reports/tap-location.txt"
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    if node.get('text','').strip() not in ('Install','Update','Установить','Обновить'): continue
    pkg=node.get('package','')
    if pkg in ('ru.spanish.learning','ru.spanish.learning.familyupdater'): continue
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
    if m:
        a,b,c,d=map(int,m.groups())
        print((a+c)//2,(b+d)//2)
        sys.exit(0)
sys.exit(1)
PY
  read -r x y < "$reports/tap-location.txt"
  adb shell input tap "$x" "$y"
}

done_flag=0
for i in $(seq 1 85); do
  current=$(get_version)
  if [[ "$current" == "$expected" ]]; then
    done_flag=1
    echo "LIVE UPGRADE SUCCESS: $current"
    break
  fi
  dump_ui > "$reports/current-ui.xml"
  if grep -q 'Обновление не выполнено' "$reports/current-ui.xml"; then
    echo "Updater displayed failure. UI and logcat retained." >&2
    exit 1
  fi
  if tap_android_confirmation "$reports/current-ui.xml"; then
    echo "Approved Android PackageInstaller confirmation."
  fi
  sleep 2
done
if [[ "$done_flag" != 1 ]]; then
  echo "Updater failed to upgrade public v369 to $expected; installed $(get_version)" >&2
  exit 1
fi

adb shell am force-stop "$target" || true
adb shell am start -W -n "$target/.MainActivity" > "$reports/launch-new.txt"
sleep 2
dump_ui > "$reports/main-ui.xml"
python3 - "$reports" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1])
old=json.loads((p/'old.json').read_text())
fam=json.loads((p/'family.json').read_text())
(p/'result.json').write_text(json.dumps({
    'success':True,
    'source':'public raw.githubusercontent.com release assets',
    'old_phone_version':old['version_code'],
    'new_phone_version':fam['version_code'],
    'family_channel':fam['channel'],
    'independent_updater_package':'ru.spanish.learning.familyupdater',
    'legacy_updater_installed_alongside':True,
    'sha256_checked':True,
    'emulator_android_api':34
},ensure_ascii=False,indent=2)+'\n')
print((p/'result.json').read_text())
PY
