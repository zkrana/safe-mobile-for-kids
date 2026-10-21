#!/bin/sh
# Test the Rust core, build the .so for arm64 phones, then the APK.
set -e
cd "$(dirname "$0")"
cargo test -p guard_core
cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs build -p guard_core --release
cd android
if [ -x ./gradlew ]; then ./gradlew assembleDebug; else gradle assembleDebug; fi
echo "APK: android/app/build/outputs/apk/debug/app-debug.apk"
