package org.foldercamera

import com.sun.net.httpserver.HttpsServer
import com.sun.net.httpserver.HttpsConfigurator
import okhttp3.Request
import org.foldercamera.sync.pinnedClient
import org.foldercamera.sync.endpoint
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class TlsTest {
    @Test fun selfSignedTrustIsScopedToExactReceiverAndWrongFingerprintFails() {
        val temporary = Files.createTempDirectory("folder-camera-tls-")
        try {
            val file = temporary.resolve("test.p12")
            val password = "ephemeral-test-only"
            val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
            val process = ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-sigalg", "SHA256withRSA", "-validity", "2", "-storetype", "PKCS12", "-keystore", file.toString(), "-storepass", password, "-dname", "CN=Ephemeral test only", "-ext", "SAN=ip:127.0.0.1", "-noprompt").redirectErrorStream(true).start()
            assertTrue("Test certificate generation timed out", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals("Test certificate generation failed", 0, process.exitValue())
            val store = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(file).use { load(it, password.toCharArray()) } }
            val cert = store.getCertificate("server")
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
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
        } finally { Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
