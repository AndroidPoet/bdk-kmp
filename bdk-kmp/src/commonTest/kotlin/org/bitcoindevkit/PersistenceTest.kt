package org.bitcoindevkit

import org.bitcoindevkit.testing.temporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// SQLite persistence through the bundled store, on every target's file system.
class PersistenceTest {
    @Test
    fun sqlite_persistsRevealedAddressesAcrossReload() {
        val path = temporaryDirectory() + "/wallet.sqlite3"
        val persister = Persister.newSqlite(path)
        val wallet = Wallet(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Network.REGTEST, persister)
        repeat(3) { wallet.revealNextAddress(KeychainKind.EXTERNAL) }
        val expectedNext = wallet.peekAddress(KeychainKind.EXTERNAL, 3u).address.toString()
        assertTrue(wallet.persist(persister), "three reveals leave something to persist")

        val reloaded = Wallet.load(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Persister.newSqlite(path))
        val next = reloaded.revealNextAddress(KeychainKind.EXTERNAL)

        assertEquals(3u, next.index)
        assertEquals(expectedNext, next.address.toString())
    }

    @Test
    fun load_withOtherDescriptors_fails() {
        val path = temporaryDirectory() + "/wallet.sqlite3"
        val persister = Persister.newSqlite(path)
        Wallet(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Network.REGTEST, persister).persist(persister)

        assertFailsWith<LoadWithPersistException> {
            Wallet.load(BIP86_DESCRIPTOR, BIP86_CHANGE_DESCRIPTOR, Persister.newSqlite(path))
        }
    }

    @Test
    fun load_fromEmptyDatabase_fails() {
        val path = temporaryDirectory() + "/empty.sqlite3"

        assertFailsWith<LoadWithPersistException> {
            Wallet.load(BIP84_DESCRIPTOR, BIP84_CHANGE_DESCRIPTOR, Persister.newSqlite(path))
        }
    }
}
