package org.bitcoindevkit.testing

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

// An emulator reaches the machine hosting it at 10.0.2.2. To run only the offline tests, pass
// -Pandroid.testInstrumentationRunnerArguments.regtestHost=none
internal actual fun regtestHost(): String? =
    (InstrumentationRegistry.getArguments().getString("regtestHost") ?: "10.0.2.2").takeIf { it != "none" }

internal actual fun temporaryDirectory(): String {
    val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    return File.createTempFile("bdk-kmp-test", "", cache).apply { delete(); mkdirs() }.absolutePath
}

internal actual fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
