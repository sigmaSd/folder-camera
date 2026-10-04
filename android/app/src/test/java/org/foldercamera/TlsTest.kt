package org.foldercamera

import com.sun.net.httpserver.HttpsServer
import com.sun.net.httpserver.HttpsConfigurator
import okhttp3.Request
import org.foldercamera.sync.pinnedClient
import org.foldercamera.sync.endpoint
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.security.*
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.*

class TlsTest {
    @Test fun selfSignedTrustIsScopedToExactReceiverAndWrongFingerprintFails() {
        val cert = javaClass.getResourceAsStream("/tls/certificate.pem")!!.use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        val keyBytes = javaClass.getResourceAsStream("/tls/private-key.pem")!!.bufferedReader().use { it.readText() }.replace(Regex("-----[^-]+-----|\\s"), "")
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBytes)))
        val store = KeyStore.getInstance("PKCS12").apply { load(null); setKeyEntry("server", key, "test".toCharArray(), arrayOf(cert)) }
        val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) }
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(ssl)
            createContext("/v1/health") { exchange -> val data = "verified".toByteArray(); exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) } }
            start()
        }
        try {
            val address = "https://127.0.0.1:${server.address.port}"
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
            val client = pinnedClient(address, fingerprint)
            client.newCall(Request.Builder().url("$address/v1/health").build()).execute().use { assertEquals("verified", it.body!!.string()) }
            assertThrows(SSLException::class.java) { pinnedClient(address, "0".repeat(64)).newCall(Request.Builder().url("$address/v1/health").build()).execute() }
            assertThrows(IllegalArgumentException::class.java) { endpoint("http://127.0.0.1:8443") }
            assertThrows(IllegalArgumentException::class.java) { endpoint("https://public.example/") }
            assertThrows(IllegalArgumentException::class.java) { endpoint("https://user:secret@127.0.0.1/") }
        } finally { server.stop(0) }
    }
}
