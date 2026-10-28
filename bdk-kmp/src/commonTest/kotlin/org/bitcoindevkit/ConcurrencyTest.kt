package org.bitcoindevkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.bitcoindevkit.testing.blocking
import kotlin.test.Test
import kotlin.test.assertEquals

// One wallet handle shared by many threads. The Rust side serialises access; these tests pin that
// down on every target rather than trusting it.
class ConcurrencyTest {
    private val workers = 8
    private val revealsPerWorker = 50

    @Test
    fun revealNextAddress_fromManyThreads_neverRepeatsAnIndex() {
        val wallet = Wallet(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Network.REGTEST, Persister.newInMemory())

        val indexes = blocking {
            List(workers) {
                async(Dispatchers.Default) {
                    List(revealsPerWorker) { wallet.revealNextAddress(KeychainKind.EXTERNAL).index }
                }
            }.awaitAll().flatten()
        }

        val expected = (0u until (workers * revealsPerWorker).toUInt()).toSet()
        assertEquals(expected, indexes.toSet())
    }

    @Test
    fun reads_whileAnotherThreadReveals_seeConsistentState() {
        val wallet = Wallet(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Network.REGTEST, Persister.newInMemory())

        blocking {
            val writer = async(Dispatchers.Default) {
                repeat(workers * revealsPerWorker) { wallet.revealNextAddress(KeychainKind.INTERNAL) }
            }
            val readers = List(workers) {
                async(Dispatchers.Default) {
                    repeat(revealsPerWorker) {
                        assertEquals(0uL, wallet.balance().total.toSat())
                        assertEquals(Network.REGTEST, wallet.network())
                    }
                }
            }
            (readers + writer).awaitAll()
        }

        assertEquals((workers * revealsPerWorker).toUInt(), wallet.revealNextAddress(KeychainKind.INTERNAL).index)
    }
}
