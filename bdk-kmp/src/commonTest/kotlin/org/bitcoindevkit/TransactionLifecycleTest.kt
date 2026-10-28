package org.bitcoindevkit

import org.bitcoindevkit.testing.Backend
import org.bitcoindevkit.testing.Regtest
import org.bitcoindevkit.testing.awaitUntil
import org.bitcoindevkit.testing.freshWallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The path that moves money: receive, build, sign, broadcast, confirm, and watch the balance follow.
// Needs the regtest stack; skips without it.
class TransactionLifecycleTest {
    @Test
    fun spend_overEsplora_confirmsAndBalanceFollows() = spendRoundTrip { Backend.Esplora(it) }

    @Test
    fun spend_overElectrum_confirmsAndBalanceFollows() = spendRoundTrip { Backend.Electrum(it) }

    private fun spendRoundTrip(backend: (Regtest) -> Backend) {
        val regtest = Regtest.connectOrSkip() ?: return
        val client = backend(regtest)
        val wallet = freshWallet()

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

        client.broadcast(transaction)
        regtest.mine(1)
        awaitUntil("${client.name} to confirm $txid") {
            client.sync(wallet)
            wallet.getTx(txid)?.chainPosition is ChainPosition.Confirmed
        }

        assertIs<ChainPosition.Confirmed>(wallet.getTx(txid)?.chainPosition)
        assertEquals(funded - sent - fee, wallet.balance().total.toSat())
    }
}
