package org.bitcoindevkit.testing

import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.ElectrumClient
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.NetworkKind
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.Wallet
import org.bitcoindevkit.WordCount

/** A wallet on a brand-new BIP-86 key, so regtest coins other tests received never show up in it. */
internal fun freshWallet(persister: Persister = Persister.newInMemory()): Wallet {
    val key = DescriptorSecretKey(NetworkKind.TEST, Mnemonic(WordCount.WORDS12), null)
    return Wallet(
        Descriptor.newBip86(key, KeychainKind.EXTERNAL, NetworkKind.TEST),
        Descriptor.newBip86(key, KeychainKind.INTERNAL, NetworkKind.TEST),
        Network.REGTEST,
        persister,
    )
}

/** A sync backend reduced to what the lifecycle tests need, so every test runs against each one. */
internal sealed interface Backend {
    val name: String
    fun fullScan(wallet: Wallet)
    fun sync(wallet: Wallet)
    fun broadcast(transaction: Transaction)

    class Esplora(regtest: Regtest) : Backend {
        private val client = EsploraClient(regtest.esploraUrl)
        override val name = "Esplora"
        override fun fullScan(wallet: Wallet) = wallet.applyUpdate(client.fullScan(wallet.startFullScan().build(), 10uL, 1uL))
        override fun sync(wallet: Wallet) = wallet.applyUpdate(client.sync(wallet.startSyncWithRevealedSpks().build(), 1uL))
        override fun broadcast(transaction: Transaction) = client.broadcast(transaction)
    }

    class Electrum(regtest: Regtest) : Backend {
        private val client = ElectrumClient(regtest.electrumUrl)
        override val name = "Electrum"
        override fun fullScan(wallet: Wallet) = wallet.applyUpdate(client.fullScan(wallet.startFullScan().build(), 10uL, 10uL, true))
        override fun sync(wallet: Wallet) = wallet.applyUpdate(client.sync(wallet.startSyncWithRevealedSpks().build(), 10uL, true))
        override fun broadcast(transaction: Transaction) {
            client.transactionBroadcast(transaction)
        }
    }
}
