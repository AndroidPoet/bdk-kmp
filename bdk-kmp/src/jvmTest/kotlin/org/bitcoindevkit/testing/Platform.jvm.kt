package org.bitcoindevkit.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

internal actual fun regtestHost(): String? = System.getenv("BDK_REGTEST_HOST")?.takeIf { it.isNotBlank() }

internal actual fun temporaryDirectory(): String = Files.createTempDirectory("bdk-kmp-test").toString()

internal actual fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
