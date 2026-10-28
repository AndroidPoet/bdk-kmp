package org.bitcoindevkit.testing

import kotlinx.coroutines.CoroutineScope

/** Host running the regtest stack, or null when this test run has none and network tests must skip. */
internal expect fun regtestHost(): String?

/** A fresh writable directory for files a test creates. */
internal expect fun temporaryDirectory(): String

/** Runs [block] to completion on the calling thread. */
internal expect fun <T> blocking(block: suspend CoroutineScope.() -> T): T
