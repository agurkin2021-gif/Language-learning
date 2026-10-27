#!/usr/bin/env bash
# Keep test execution and diagnostics in ONE emulator-runner shell invocation.
# reactivecircus runs each line of a multiline script separately and aborts
# after the first failing command, so inline "set +e" is ineffective.
set -uo pipefail
adb logcat -c || true
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -PbuildNumber="$GITHUB_RUN_NUMBER" \
  -PsourceRevision="$BUILD_REVISION" \
  -PbuildRunId="$GITHUB_RUN_ID"
result=$?
mkdir -p build-preview
adb logcat -d -v threadtime > android-test-logcat.txt 2>&1 || true
adb pull /sdcard/Android/data/ru.spanish.learning/files/build-preview/. build-preview/ || true
echo "Android instrumentation exit code: $result"
exit "$result"
