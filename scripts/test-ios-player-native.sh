#!/usr/bin/env bash
set -euo pipefail

# Run AVFoundation tests inside a BOOTED simulator's normal service environment.
# Kotlin/Native's Gradle runner uses `simctl spawn --standalone`; in that environment
# our local WAV preparation fails with AVFoundation -11800 / OSStatus -12746.
# The identical binary succeeds with normal spawn. Do not skip the native assertions.
: "${LMUSIC_IOS_SIMULATOR_ID:?Set LMUSIC_IOS_SIMULATOR_ID to a booted simulator UDID}"
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
./gradlew :lplayer:linkDebugTestIosSimulatorArm64 --console=plain
xcrun simctl spawn "$LMUSIC_IOS_SIMULATOR_ID" \
    "$task_root/lplayer/build/bin/iosSimulatorArm64/debugTest/test.kexe" \
    -- --ktest_logger=TEAMCITY \
    --ktest_gradle_filter=com.lalilu.lplayer.playback.AVPlayerPreparationTest,com.lalilu.lplayer.playback.MediaTimeConversionTest,com.lalilu.lplayer.playback.HistoryQueueRestorerTest
