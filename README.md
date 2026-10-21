# Game Control (Redmi parental guard)

- `core/` — Rust engine: policy, PIN hashing, allow/block decision (JNI). Tests: `cargo test -p guard_core`.
- `android/` — Kotlin shell: PIN gate, parent settings, AccessibilityService, device-admin receiver.

## Build
```sh
rustup target add aarch64-linux-android armv7-linux-androideabi && cargo install cargo-ndk
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK_HOME=$(ls -d $ANDROID_HOME/ndk/* | tail -1)
./build.sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```
(Open `android/` in Android Studio once if you have no `gradle`; it creates the wrapper.)

## First run on the phone
1. Open Game Control, set the parent PIN.
2. Tap the three setup buttons: accessibility service, uninstall protection, autostart.
3. Redmi: Battery saver -> No restrictions for Game Control; lock it in Recents.
4. Install YouTube Kids (Play Store is blocked for the child, so do it while unlocked or before enabling).

## Parent mode
Entering the PIN unlocks everything for 10 minutes (store, settings, uninstall). "Lock now" ends it.
