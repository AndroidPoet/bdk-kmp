# bdk-kmp

Kotlin Multiplatform bindings for the [Bitcoin Dev Kit](https://bitcoindevkit.org): one Gradle
dependency that gives you `bdk_wallet` on JVM, Android, iOS and macOS from `commonMain`.

The Kotlin API is generated from [`bdk-ffi`](https://github.com/bitcoindevkit/bdk-ffi) (vendored
here as a git submodule) by the
[UniFFI Kotlin Multiplatform bindgen](https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings),
so the types and method names are the same ones `bdk-android` and `bdk-jvm` ship. Existing
`org.bitcoindevkit` code compiles unchanged; it just also runs on Kotlin/Native.

| Target | How Rust is reached |
| --- | --- |
| `jvm()` | JNA, dynamic library bundled in the jar |
| `android` | JNA, `jniLibs` per ABI |
| `iosArm64`, `iosSimulatorArm64`, `macosArm64` | cinterop, static library |

## Status

Pre-release. JVM and macOS arm64 pass the wallet tests; iOS and Android are being wired up.

## Upstream delta

`bdk-ffi` v3.1.1 pins UniFFI 0.31.2, and the only maintained Kotlin Multiplatform bindgen needs
0.32.0. `patches/0001-uniffi-0.32-rust-1.93.patch` is the whole difference (two version strings);
the build applies it to the submodule before `cargo` runs. The patch is being proposed upstream.

## Build

```sh
git submodule update --init
./gradlew :bdk-kmp:jvmTest :bdk-kmp:macosArm64Test
```

Needs Rust 1.93, `rustup target add aarch64-apple-ios aarch64-apple-ios-sim` for iOS, and an
Android NDK for the Android target. The bindgen binary is installed by Gradle on first build.

## License

MIT or Apache-2.0, at your option, matching `bdk-ffi`.
