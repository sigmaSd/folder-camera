package org.foldercamera

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import okhttp3.Request
import org.foldercamera.sync.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.*
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class PairingErrorTest {
    private val now = 1_800_000_000_000L
    private fun payload(expiresAt: Long, secret: String = "a".repeat(43)) = JSONObject().put("version", 1).put("receiverId", "0".repeat(8) + "-0000-0000-0000-" + "0".repeat(12))
        .put("endpoint", "https://10.48.142.213:8443").put("fingerprint", "0".repeat(64)).put("secret", secret).put("expiresAt", expiresAt).toString()
    private fun parseCode(text: String) = try { Pairing.parse(text, now); null } catch (e: ProtocolFailure) { e.code }

    @Test fun parseNamesExpiryAndClockProblems() {
        assertNull(parseCode(payload(now + 300_000)))
        assertEquals("pairing_expired", parseCode(payload(now - 1_000)))
        assertEquals("pairing_clock", parseCode(payload(now - 30 * 60_000)))
        assertEquals("pairing_clock", parseCode(payload(now + 60 * 60_000)))
        assertEquals("pairing_payload", parseCode(payload(now + 300_000, secret = "short")))
        assertEquals("pairing_payload", parseCode("not json"))
    }

    @Test fun failuresMapToTheStepThatFailed() {
        assertEquals("pair_invalid", pairingError(ProtocolFailure("pairing_payload", true)))
        assertEquals("pair_expired", pairingError(ProtocolFailure("pairing_expired", true)))
        assertEquals("pair_clock", pairingError(ProtocolFailure("pairing_clock", true)))
        assertEquals("pair_used", pairingError(ProtocolFailure("pairing_invalid", true)))
        assertEquals("pair_rate_limited", pairingError(ProtocolFailure("rate_limited", false)))
        assertEquals("pair_wrong_pc", pairingError(ProtocolFailure("receiver_mismatch", true)))
        assertEquals("lan_permission", pairingError(ProtocolFailure("lan_permission", true)))
        assertEquals("pair_wrong_pc", pairingError(SSLHandshakeException("handshake").apply { initCause(CertificateException("certificate_changed")) }))
        assertEquals("pair_unreachable", pairingError(ConnectException("Failed to connect")))
        assertEquals("pair_unreachable", pairingError(SocketTimeoutException("connect timed out")))
        assertEquals("pair_unreachable", pairingError(SocketException("Binding socket to network 101 failed: EPERM")))
        assertEquals("pair_failed", pairingError(ProtocolFailure("server_failure", false)))
        assertEquals("pair_failed", pairingError(IllegalStateException("database")))
    }

    /** Behaves like a Network's factory while the app is inside a VPN it cannot bypass: binding fails before any packet is sent. */
    private val bindRefused = object : SocketFactory() {
        private fun refuse(): Nothing = throw SocketException("Binding socket to network 101 failed: EPERM")
        override fun createSocket(): Socket = refuse()
        override fun createSocket(host: String, port: Int): Socket = refuse()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = refuse()
        override fun createSocket(host: InetAddress, port: Int): Socket = refuse()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = refuse()
    }

    @Test fun refusedBindFallsBackToDefaultRouteWithTheSamePinning() = withReceiver { address, fingerprint ->
        val request = Request.Builder().url("$address/v1/health").build()
        val bound = pinnedClient(address, fingerprint, bindRefused, null)
        executeWithFallback(bound, pinnedClient(address, fingerprint), request).use { assertEquals("verified", it.body!!.string()) }
        // The fallback route keeps exact certificate pinning.
        assertThrows(SSLException::class.java) { executeWithFallback(bound, pinnedClient(address, "0".repeat(64)), request) }
        // Once a connection was made, its failure is final: the other route is not tried.
        assertThrows(SSLException::class.java) { executeWithFallback(pinnedClient(address, "0".repeat(64), SocketFactory.getDefault(), null), pinnedClient(address, fingerprint), request) }
    }

    private fun withReceiver(block: (address: String, fingerprint: String) -> Unit) {
        val temporary = Files.createTempDirectory("folder-camera-pairing-")
        try {
            val file = temporary.resolve("test.p12")
            val password = "ephemeral-test-only"
            val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
            val process = ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-sigalg", "SHA256withRSA", "-validity", "2", "-storetype", "PKCS12", "-keystore", file.toString(), "-storepass", password, "-dname", "CN=Ephemeral test only", "-ext", "SAN=ip:127.0.0.1", "-noprompt").redirectErrorStream(true).start()
            assertTrue("Test certificate generation timed out", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals("Test certificate generation failed", 0, process.exitValue())
            val store = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(file).use { load(it, password.toCharArray()) } }
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
            val ssl = SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) }
            val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                httpsConfigurator = HttpsConfigurator(ssl)
                createContext("/v1/health") { exchange -> val data = "verified".toByteArray(); exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) } }
                start()
            }
            try {
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(store.getCertificate("server").encoded).joinToString("") { "%02x".format(it) }
                block("https://127.0.0.1:${server.address.port}", fingerprint)
            } finally { server.stop(0) }
        } finally { Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
