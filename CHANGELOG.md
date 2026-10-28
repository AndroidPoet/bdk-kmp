# Changelog

## Unreleased

- First Kotlin Multiplatform build of `bdk-ffi` v3.1.1: JVM, Android, iosArm64,
  iosSimulatorArm64, macosArm64 from one `org.bitcoindevkit` API.
- Offline wallet, descriptor, key-derivation, mnemonic and error tests ported from
  `bdk-android` into `commonTest` so they run on every target.
- Public API dumps committed under `bdk-kmp/api/` (binary-compatibility-validator) so the
  generated surface is browsable without a build and checked in CI.
- Network tests in `commonTest`: receive, build, sign, broadcast and confirm over Esplora and
  Electrum, compact-block-filter sync, and sync inspector callbacks, against the regtest
  container upstream uses. CI runs them on Linux and an Android emulator.
- Offline tests for SQLite persistence across reload, concurrent wallet access, and handle
  lifetimes (use after `destroy()` throws, `close()` is idempotent).
- Android device tests share `commonTest`, so the whole suite runs on the emulator.
- Release workflow: release-mode build of every target, JVM jar with macOS arm64, Linux x86_64
  and Windows x86_64 libraries, API check, jar-contents check, then Maven Central.
- GitHub Actions pinned to commits.
- Verified on CI: full suite on Linux JVM and an Android emulator against regtest; offline
  suite on macOS, iOS simulator and macOS-hosted JVM.
