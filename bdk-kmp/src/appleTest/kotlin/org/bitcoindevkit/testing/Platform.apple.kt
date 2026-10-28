package org.bitcoindevkit.testing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun regtestHost(): String? = getenv("BDK_REGTEST_HOST")?.toKString()?.takeIf { it.isNotBlank() }

internal actual fun temporaryDirectory(): String {
    val path = NSTemporaryDirectory() + "bdk-kmp-test-" + NSUUID().UUIDString
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    return path
}

internal actual fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
