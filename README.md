<p align="center"><b>bdk-kmp</b></p>
<p align="center"><b>The Bitcoin Dev Kit for Kotlin Multiplatform.</b></p>

<p align="center">
  <a href="https://kotlinlang.org"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white"/></a>
  <a href="https://github.com/bitcoindevkit/bdk_wallet"><img alt="BDK" src="https://img.shields.io/badge/BDK-3.1.1-F7931A?logo=bitcoin&logoColor=white"/></a>
  <a href="https://github.com/AndroidPoet/bdk-kmp/actions/workflows/build.yml"><img alt="Build" src="https://github.com/AndroidPoet/bdk-kmp/actions/workflows/build.yml/badge.svg"/></a>
  <a href="LICENSE-MIT"><img alt="License" src="https://img.shields.io/badge/License-MIT%20or%20Apache--2.0-green.svg"/></a>
</p>

<p align="center">
  <img alt="badge-android" src="https://img.shields.io/badge/-android-6EDB8D.svg?style=flat"/>
  <img alt="badge-ios" src="https://img.shields.io/badge/-ios-CDCDCD.svg?style=flat"/>
  <img alt="badge-macos" src="https://img.shields.io/badge/-macos-111111.svg?style=flat"/>
  <img alt="badge-jvm" src="https://img.shields.io/badge/-jvm-DB413D.svg?style=flat"/>
</p>

<p align="center">
Descriptor wallets, PSBTs, Esplora, Electrum and compact-block-filter sync from <code>commonMain</code>.
The same <code>org.bitcoindevkit</code> API that <code>bdk-android</code> and <code>bdk-jvm</code> ship,
now also on iOS and macOS, so a Kotlin Multiplatform wallet keeps all of its Bitcoin logic in shared code.
</p>

<p align="center">
  <a href="#install">Install</a> ·
  <a href="#quickstart">Quickstart</a> ·
  <a href="#sync-backends">Sync backends</a> ·
  <a href="#platform-notes">Platforms</a> ·
  <a href="#coming-from-bdk-android-or-bdk-jvm">Migrating</a> ·
  <a href="#what-it-is-and-what-it-is-not">What it is not</a> ·
  <a href="#how-it-works">How it works</a> ·
  <a href="#status">Status</a> ·
  <a href="#build--test">Build</a>
</p>

---

## Why

[BDK](https://bitcoindevkit.org) is the wallet library behind Bitkey, Proton Wallet and most new
Bitcoin wallets. Until now its Kotlin packages stopped at Android and the JVM, so a Kotlin
Multiplatform app had to write its iOS wallet layer twice, in Swift, and keep the two in step.

`bdk-kmp` removes that split. One Gradle dependency gives you the `bdk_wallet` API as Kotlin on
Android, JVM desktop, iOS and macOS, with the Rust core compiled per platform and linked for you.
Nothing to add to Xcode, no Swift glue, no second implementation. The [status table](#status) says
exactly what is verified where.

## Install

The first release is being prepared. Until it reaches Maven Central, [build from source](#build--test)
and `publishToMavenLocal`.

```kotlin
// build.gradle.kts (shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.androidpoet:bdk-kmp:3.1.1")
        }
    }
}
```

The version number follows the BDK release it wraps, so `3.1.1` is BDK 3.1.1.

## Quickstart

Everything below lives in `commonMain` and runs unchanged on Android, iOS, macOS and the JVM.

### Create a wallet

```kotlin
import org.bitcoindevkit.*

val mnemonic = Mnemonic(WordCount.WORDS12)
val secretKey = DescriptorSecretKey(NetworkKind.TEST, mnemonic, password = null)

val external = Descriptor.newBip86(secretKey, KeychainKind.EXTERNAL, NetworkKind.TEST)
val change = Descriptor.newBip86(secretKey, KeychainKind.INTERNAL, NetworkKind.TEST)

val persister = Persister.newInMemory()          // or Persister.newSqlite(pathToDbFile)
val wallet = Wallet(external, change, Network.SIGNET, persister)

val address = wallet.revealNextAddress(KeychainKind.EXTERNAL).address
println(address)                                 // tb1p…
```

Keys and descriptors take a `NetworkKind` (main or test); the wallet takes the exact `Network`
(`BITCOIN`, `TESTNET`, `TESTNET4`, `SIGNET`, `REGTEST`).

### Load an existing wallet

```kotlin
val persister = Persister.newSqlite(pathToDbFile)
val wallet = Wallet.load(external, change, persister)
```

### Sync and read the balance

```kotlin
val esplora = EsploraClient("https://mutinynet.com/api")

// First time: scan every address the descriptor can produce, up to a stop gap.
val fullScan = wallet.startFullScan().build()
wallet.applyUpdate(esplora.fullScan(fullScan, stopGap = 20uL, parallelRequests = 4uL))

// Afterwards: only re-check the addresses you have revealed.
val sync = wallet.startSyncWithRevealedSpks().build()
wallet.applyUpdate(esplora.sync(sync, parallelRequests = 4uL))

wallet.persist(persister)
println(wallet.balance().total.toSat())
```

### Send

```kotlin
val recipient = Address("tb1q…", Network.SIGNET)

val psbt = TxBuilder()
    .addRecipient(recipient.scriptPubkey(), Amount.fromSat(50_000uL))
    .feeRate(FeeRate.fromSatPerVb(2uL))
    .finish(wallet)

wallet.sign(psbt)
esplora.broadcast(psbt.extractTx())
wallet.persist(persister)
```

### Keep it off the main thread

Every call is synchronous and does real work (network, signing, SQLite). Wrap the sync and send
paths in `withContext(Dispatchers.IO)` on JVM and Android, and `Dispatchers.Default` on Apple
targets, exactly as you would with any blocking client. A few rules that follow from that:

- A blocking call cannot be cancelled from Kotlin. Give long syncs their own coroutine and a
  timeout on the client (`ElectrumClient(url, timeout = ...)`), and prefer `sync` over `fullScan`
  once a wallet is set up.
- One `Wallet` may be shared across threads; the Rust side serialises access. Expect callers to
  queue behind a running sync rather than run alongside it.
- Every handle is `Disposable`. Release it with `use { }` or `close()`; calling a released handle
  throws `IllegalStateException` rather than touching freed memory. Values such as addresses,
  amounts and transactions outlive the handle that produced them.

## Sync backends

BDK ships three chain sources and all of them are available from shared code.

| Backend | Class | When |
| --- | --- | --- |
| Esplora (HTTP) | `EsploraClient(url)` | Simplest. Public servers exist for every network. |
| Electrum (TCP/TLS) | `ElectrumClient("ssl://host:port")` | Your own server, or privacy over a SOCKS proxy. |
| Compact block filters (BIP 157/158) | `CbfBuilder().build(wallet)` | No trusted server. Talks to Bitcoin peers directly. |

Electrum:

```kotlin
val electrum = ElectrumClient("ssl://electrum.blockstream.info:60002")
val update = electrum.sync(request = sync, batchSize = 10u, fetchPrevTxouts = true)
wallet.applyUpdate(update)
```

Compact block filters (a light client inside your app, no server at all):

```kotlin
val (client, node) = CbfBuilder()
    .connections(2u)
    .scanType(ScanType.Sync)
    .dataDir(pathToPeerDataDir)
    .build(wallet)

node.run()
wallet.applyUpdate(client.update())
```

### Over Tor

BDK does not ship Tor; it takes a SOCKS5 proxy and leaves running Tor to the app. All three
backends accept one, so pairing with [tor-kmp](https://github.com/AndroidPoet/tor-kmp), which runs
Tor in-process on the same targets, needs no extra native code:

```kotlin
val esplora = EsploraClient(url, proxy = "socks5://127.0.0.1:9050")
val electrum = ElectrumClient("ssl://host.onion:50002", socks5 = "127.0.0.1:9050")
val cbf = CbfBuilder().socks5Proxy(Socks5Proxy(IpAddress.fromIpv4(127u, 0u, 0u, 1u), 9050u))
```

Hostnames stay hostnames, so resolution happens inside the proxy and DNS does not leak.

## Platform notes

| Target | Linking | Notes |
| --- | --- | --- |
| `jvm()` | dynamic library inside the jar | macOS arm64 today; other desktops on the roadmap |
| `android` | `jniLibs` per ABI | `minSdk 24`, `compileSdk 36`; arm64-v8a, armeabi-v7a, x86_64 |
| `iosArm64()`, `iosSimulatorArm64()` | static library, linked into your framework | nothing to configure in Xcode |
| `macosArm64()` | static library | |

**iOS.** The Rust core is a static library that the Kotlin/Native compiler links into your shared
framework. Your Swift code never sees it; it calls the Kotlin API you already expose.

**Android.** Native libraries are bundled for each ABI through JNA. Add the usual
`android.ndk` split configuration if you want per-ABI APKs; App Bundles handle it automatically.

**JVM.** The jar carries the native library for the host it was built on and loads it on first use.

## Coming from bdk-android or bdk-jvm

The generated API is the same package, the same classes and the same method names. Code that
targets `org.bitcoindevkit:bdk-android` or `bdk-jvm` compiles against `bdk-kmp` without edits; it
simply becomes available to `commonMain` and to your Apple targets as well. Mixing both in one build
is not supported: pick one.

## What it is, and what it is not

**It is** BDK. Descriptor-based wallets, coin selection, PSBT creation and signing, Taproot,
Esplora, Electrum and compact-block-filter sync, SQLite persistence, BIP 39 mnemonics and BIP 32
derivation, on every Kotlin Multiplatform target that matters for a wallet.

**It is not** a wallet. There is no UI, no key backup, no price feed and no Lightning. It does not
run a full node. It does not route traffic through Tor by itself; point `ElectrumClient` at a
SOCKS proxy or pair it with [tor-kmp](https://github.com/AndroidPoet/tor-kmp) for that.

## How it works

BDK's Rust core is compiled once per target by the Gradle build. The Kotlin API is generated from
the same upstream binding definitions that produce `bdk-android`, `bdk-jvm` and `bdk-swift`, using
the UniFFI Kotlin Multiplatform generator, so there is no hand-written wrapper to drift out of date.
On Apple targets the core is a static library pulled in through cinterop; on Android and the JVM it
is a dynamic library loaded at runtime.

### Where is the Kotlin?

There is no hand-written API layer under `src/`; the whole `org.bitcoindevkit` package is generated
into `build/` when the project compiles. To browse the public surface without building anything,
read the committed dumps in [`bdk-kmp/api/`](bdk-kmp/api): `bdk-kmp.klib.api` lists every
declaration in Kotlin syntax for the native targets, and `jvm/bdk-kmp.api` lists the JVM
signatures. CI runs `./gradlew apiCheck`, so the dumps cannot drift from the generated code. After
moving the upstream pin, run `./gradlew apiDump` and commit the result.

Upstream currently pins a binding-generator version one step older than the one with
Multiplatform support. `patches/` holds that two-line version bump and the build applies it to the
vendored upstream checkout. The bump is being proposed upstream so the patch can go away.

## Status

| | |
| --- | --- |
| JVM (macOS arm64 host) | wallet, descriptor, key, mnemonic and error tests pass |
| macOS arm64 | same suite passes on Kotlin/Native |
| iOS simulator | same suite passes on CI (macOS runner) |
| iOS device | not yet verified |
| Android | wired up, verification in progress |
| Maven Central | not yet published |

Roadmap, in order: iOS and Android verification, first Maven Central release, Linux and Windows
JVM libraries in the jar, a Compose Multiplatform sample wallet, then proposing the project to the
`bitcoindevkit` organisation alongside `bdk-jvm` and `bdk-swift`.

## Build & test

```sh
git clone --recurse-submodules https://github.com/AndroidPoet/bdk-kmp.git
cd bdk-kmp
./gradlew :bdk-kmp:jvmTest :bdk-kmp:macosArm64Test
```

The suite has two halves. The offline tests (keys, descriptors, wallets, persistence, threading,
handle lifetimes) run everywhere. The network tests (full scan, sync, build, sign, broadcast and
confirm over Esplora and Electrum, compact-block-filter sync, Rust-to-Kotlin sync callbacks) need
a regtest stack and skip without one. CI runs them on Linux and on an Android emulator against
the same container upstream `bdk-ffi` uses; to run them locally:

```sh
docker run -d -p 18443:18443 -p 18444:18444 -p 3002:3002 -p 60401:60401 \
  ghcr.io/thunderbiscuit/podman-regtest-infinity-pro:0.4.0
BDK_REGTEST_HOST=127.0.0.1 ./gradlew :bdk-kmp:jvmTest
./gradlew :bdk-kmp:connectedAndroidDeviceTest   # emulator reaches the container at 10.0.2.2
```

Requirements: JDK 17, Rust 1.93 with `rustup target add aarch64-apple-ios aarch64-apple-ios-sim`
for iOS, and an Android NDK (28.x) for Android. The binding generator is installed by Gradle on the
first build. Add `:bdk-kmp:iosSimulatorArm64Test` to run the suite on the iOS simulator.

Rust build output goes to `cargo-build/` at the repo root; it is large, and safe to delete.

## Contributing

Issues and pull requests are welcome. Keep the generated API untouched (it is regenerated on every
build) and put platform-specific fixes in the Gradle build or the patch set. If a change alters
the public API, run `./gradlew apiDump` and commit the updated files under `bdk-kmp/api/`. All contributors are
expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

MIT or Apache-2.0, at your option, matching BDK.
