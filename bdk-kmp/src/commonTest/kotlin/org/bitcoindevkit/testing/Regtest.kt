package org.bitcoindevkit.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import org.bitcoindevkit.IpAddress
import org.bitcoindevkit.Peer
import org.bitcoindevkit.Txid
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The regtest stack the network tests run against: bitcoind with a funded `faucet` wallet, an
 * Esplora server and an Electrum server. CI starts ghcr.io/thunderbiscuit/podman-regtest-infinity-pro,
 * the image upstream bdk-ffi tests with. Locally, point BDK_REGTEST_HOST at your own.
 */
internal class Regtest private constructor(val host: String) {
    val esploraUrl = "http://$host:3002"
    val electrumUrl = "tcp://$host:60401"
    val peer: Peer = Peer(ipAddress(host), 18444u, false)

    private val rpcUrl = "http://$host:18443/wallet/faucet"
    private val authorization = "Basic " + Base64.encode("regtest:password".encodeToByteArray())
    private val http = HttpClient(CIO)

    fun newAddress(): String = rpc("getnewaddress")

    /** Sends [btc] from the faucet; the transaction sits in the mempool until [mine]. */
    fun send(address: String, btc: Double): Txid =
        Txid.fromString(rpc("sendtoaddress", "\"address\":\"$address\"", "\"amount\":$btc", "\"fee_rate\":2"))

    fun mine(blocks: Int) {
        rpc("generatetoaddress", "\"nblocks\":$blocks", "\"address\":\"${newAddress()}\"")
    }

    private fun rpc(method: String, vararg namedParams: String): String = blocking {
        val body = """{"jsonrpc":"1.0","id":"bdk-kmp","method":"$method","params":{${namedParams.joinToString(",")}}}"""
        val response = http.post(rpcUrl) {
            header(HttpHeaders.Authorization, authorization)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "$method failed with ${response.status}: $text" }
        resultOf(text, method)
    }

    companion object {
        /** The regtest stack for this run, or null after saying why, so a test can return early. */
        fun connectOrSkip(): Regtest? {
            val host = regtestHost()
            if (host == null) println("skipped: no regtest stack (set BDK_REGTEST_HOST)")
            return host?.let(::Regtest)
        }

        /** Pulls `result` out of a JSON-RPC reply; the tests only ever need a string or a number. */
        internal fun resultOf(reply: String, method: String): String {
            val error = Regex("\"error\"\\s*:\\s*(\\{[^}]*\\})").find(reply)?.groupValues?.get(1)
            check(error == null) { "$method returned an error: $error" }
            val afterKey = reply.substringAfter("\"result\":").trimStart()
            return if (afterKey.startsWith("\"")) afterKey.drop(1).substringBefore("\"")
            else afterKey.substringBefore(",\"error\"").trim()
        }

        private fun ipAddress(host: String): IpAddress {
            val octets = host.split(".").map { it.toUByte() }
            require(octets.size == 4) { "regtest host must be an IPv4 address, got $host" }
            return IpAddress.fromIpv4(octets[0], octets[1], octets[2], octets[3])
        }
    }
}

/** Polls [condition] every 250 ms until it holds, failing after [timeout]. */
internal fun awaitUntil(what: String, timeout: Duration = 30.seconds, condition: () -> Boolean) {
    val started = TimeSource.Monotonic.markNow()
    while (!condition()) {
        check(started.elapsedNow() < timeout) { "timed out after $timeout waiting for $what" }
        blocking { delay(250.milliseconds) }
    }
}
