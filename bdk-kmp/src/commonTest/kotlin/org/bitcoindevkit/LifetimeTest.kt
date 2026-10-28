package org.bitcoindevkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Every handle is Disposable. Using one after it is released must fail in Kotlin, never in Rust.
class LifetimeTest {
    private fun wallet() = Wallet(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Network.REGTEST, Persister.newInMemory())

    @Test
    fun destroy_thenCall_throwsInsteadOfCrashing() {
        val wallet = wallet()
        wallet.destroy()

        assertFailsWith<IllegalStateException> { wallet.balance() }
    }

    @Test
    fun close_twice_isHarmless() {
        val wallet = wallet()

        wallet.close()
        wallet.close()
    }

    @Test
    fun use_releasesTheHandleAfterTheBlock() {
        val wallet = wallet()

        val total = wallet.use { it.balance().total.toSat() }

        assertEquals(0uL, total)
        assertFailsWith<IllegalStateException> { wallet.balance() }
    }

    @Test
    fun valuesOutliveTheHandleThatMadeThem() {
        val wallet = wallet()
        val address = wallet.revealNextAddress(KeychainKind.EXTERNAL).address
        wallet.destroy()

        assertEquals("bcrt1", address.toString().take(5))
    }
}
