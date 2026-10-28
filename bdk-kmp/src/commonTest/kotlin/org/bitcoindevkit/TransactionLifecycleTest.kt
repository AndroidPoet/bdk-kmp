package org.bitcoindevkit

import org.bitcoindevkit.testing.Backend
import org.bitcoindevkit.testing.FreshWallet
import org.bitcoindevkit.testing.Regtest
import org.bitcoindevkit.testing.awaitUntil
import org.bitcoindevkit.testing.temporaryDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The path that moves money: receive, build, sign, broadcast, confirm, watch the balance follow,
// then reopen the wallet from SQLite and find the same state. Needs the regtest stack; skips without it.
class TransactionLifecycleTest {
    @Test
    fun spend_overEsplora_confirmsAndBalanceFollows() = spendRoundTrip { Backend.Esplora(it) }

    @Test
    fun spend_overElectrum_confirmsAndBalanceFollows() = spendRoundTrip { Backend.Electrum(it) }

    private fun spendRoundTrip(backend: (Regtest) -> Backend) {
        val regtest = Regtest.connectOrSkip() ?: return
        val client = backend(regtest)
        val dbPath = temporaryDirectory() + "/wallet.sqlite3"
        val persister = Persister.newSqlite(dbPath)
        val fresh = FreshWallet(persister)
        val wallet = fresh.wallet

        val funded = 100_000uL
        val fundingTxid = regtest.send(wallet.revealNextAddress(KeychainKind.EXTERNAL).address.toString(), 0.001)
        regtest.mine(1)
        awaitUntil("${client.name} to index funding tx $fundingTxid") {
            client.fullScan(wallet)
            wallet.balance().confirmed.toSat() == funded
        }

        val recipient = Address(regtest.newAddress(), Network.REGTEST)
        val sent = 40_000uL
        val psbt = TxBuilder()
            .addRecipient(recipient.scriptPubkey(), Amount.fromSat(sent))
            .feeRate(FeeRate.fromSatPerVb(2uL))
            .finish(wallet)
        assertTrue(wallet.sign(psbt), "the wallet holds the keys, so signing must finalize the PSBT")
        val transaction = psbt.extractTx()
        val txid = transaction.computeTxid()
        val fee = wallet.calculateFee(transaction).toSat()
        assertTrue(fee > 0uL, "fee must be positive, was $fee")
        val paid = transaction.output().single { it.scriptPubkey.toBytes().contentEquals(recipient.scriptPubkey().toBytes()) }
        assertEquals(sent, paid.value.toSat(), "the recipient output carries exactly the requested amount")
        assertEquals(2, transaction.output().size, "one recipient output and one change output")

        client.broadcast(transaction)
        regtest.mine(1)
        awaitUntil("${client.name} to confirm $txid") {
            client.sync(wallet)
            wallet.getTx(txid)?.chainPosition is ChainPosition.Confirmed
        }

        assertIs<ChainPosition.Confirmed>(wallet.getTx(txid)?.chainPosition)
        val remaining = funded - sent - fee
        assertEquals(remaining, wallet.balance().total.toSat())

        assertTrue(wallet.persist(persister), "a confirmed spend leaves changes to persist")
        val reopened = fresh.load(Persister.newSqlite(dbPath))
        assertEquals(remaining, reopened.balance().total.toSat(), "balance survives a reload from SQLite")
        assertIs<ChainPosition.Confirmed>(assertNotNull(reopened.getTx(txid)).chainPosition)
        assertContentEquals(
            wallet.listUnspent().map { it.outpoint.txid.toString() + ":" + it.outpoint.vout },
            reopened.listUnspent().map { it.outpoint.txid.toString() + ":" + it.outpoint.vout },
        )
    }
}
