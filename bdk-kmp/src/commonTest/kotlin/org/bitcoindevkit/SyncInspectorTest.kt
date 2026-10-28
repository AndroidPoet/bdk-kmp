package org.bitcoindevkit

import org.bitcoindevkit.testing.Regtest
import org.bitcoindevkit.testing.freshWallet
import kotlin.test.Test
import kotlin.test.assertEquals

// Kotlin callbacks invoked from the Rust side during a sync. Needs the regtest stack; skips without it.
class SyncInspectorTest {
    @Test
    fun sync_callsInspectorOncePerRevealedScript() {
        val regtest = Regtest.connectOrSkip() ?: return
        val wallet = freshWallet()
        wallet.revealAddressesTo(KeychainKind.EXTERNAL, 16u)
        var inspected = 0
        var lastTotal = 0uL
        val inspector = object : SyncScriptInspector {
            override fun inspect(script: Script, total: ULong) {
                inspected++
                lastTotal = total
            }
        }

        ElectrumClient(regtest.electrumUrl).sync(
            wallet.startSyncWithRevealedSpks().inspectSpks(inspector).build(),
            10uL,
            true,
        )

        // Indices 0..16 are revealed, so 17 scripts, each inspected once.
        assertEquals(17, inspected)
        assertEquals(17uL, lastTotal)
    }

    @Test
    fun fullScan_inspectsStopGapScriptsOnBothKeychains() {
        val regtest = Regtest.connectOrSkip() ?: return
        val wallet = freshWallet()
        var inspected = 0
        val keychains = mutableSetOf<KeychainKind>()
        val inspector = object : FullScanScriptInspector {
            override fun inspect(keychain: KeychainKind, index: UInt, script: Script) {
                inspected++
                keychains += keychain
            }
        }

        ElectrumClient(regtest.electrumUrl).fullScan(
            wallet.startFullScan().inspectSpksForAllKeychains(inspector).build(),
            25uL,
            1uL,
            true,
        )

        // An empty wallet with a stop gap of 25 inspects the first 25 scripts of each keychain.
        assertEquals(50, inspected)
        assertEquals(setOf(KeychainKind.EXTERNAL, KeychainKind.INTERNAL), keychains)
    }
}
