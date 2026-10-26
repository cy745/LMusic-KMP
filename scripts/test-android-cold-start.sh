#!/usr/bin/env bash
set -euo pipefail
# Explicit emulator-only target: never force-stop or replace the user's physical-phone preview.
serial="${1:-emulator-5554}"
[[ "$serial" == emulator-* ]] || { echo 'Cold-start fixture requires an emulator.' >&2; exit 1; }
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
package=com.lalilu.lmusic.kmp
runner="$package.test/androidx.test.runner.AndroidJUnitRunner"
phase() {
  printf '\nCold-start phase: %s\n' "$1"
  "$adb" -s "$serial" shell am force-stop "$package"
  local result
  result=$("$adb" -s "$serial" shell am instrument -w -e class com.lalilu.lmusic.prototype.ColdStartDeviceTest -e coldStartPhase "$1" "$runner")
  printf '%s\n' "$result"
  [[ "$result" == *'OK (1 test)'* ]]
}
cleanup() {
  phase cleanup || { echo 'Fixture cleanup failed; rerun cleanup before using this emulator.' >&2; return 1; }
  "$adb" -s "$serial" shell am force-stop "$package"
}
trap cleanup EXIT
phase prepare
phase removeLibraryRows
phase verify
phase removeCachedBytes
phase verifyMissingBytes
