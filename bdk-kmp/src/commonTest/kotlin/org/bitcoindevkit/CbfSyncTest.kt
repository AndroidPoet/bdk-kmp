package org.bitcoindevkit

import kotlinx.coroutines.withTimeout
import org.bitcoindevkit.testing.Regtest
import org.bitcoindevkit.testing.blocking
import org.bitcoindevkit.testing.freshWallet
import org.bitcoindevkit.testing.temporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

// Compact block filters against the regtest node's p2p port. Needs the regtest stack; skips without it.
class CbfSyncTest {
    @Test
    fun compactBlockFilterSync_findsFunds() {
        val regtest = Regtest.connectOrSkip() ?: return
        val wallet = freshWallet()
        regtest.send(wallet.revealNextAddress(KeychainKind.EXTERNAL).address.toString(), 0.001)
        regtest.mine(2)

        val (client, node) = CbfBuilder()
            .peers(listOf(regtest.peer))
            .connections(1u)
            .scanType(ScanType.Sync)
            .dataDir(temporaryDirectory())
            .build(wallet)
        node.run()
        try {
            val update = blocking { withTimeout(90.seconds) { client.update() } }
            wallet.applyUpdate(update)
            assertEquals(100_000uL, wallet.balance().total.toSat())
        } finally {
            client.shutdown()
        }
    }
}
