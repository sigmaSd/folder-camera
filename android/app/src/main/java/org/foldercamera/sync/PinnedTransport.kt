package org.foldercamera.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.foldercamera.data.*
import org.foldercamera.storage.digest
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.*

class ProtocolFailure(val code: String, val permanent: Boolean, cause: Throwable? = null) : Exception(code, cause)
data class Pairing(val receiverId: String, val endpoint: String, val fingerprint: String, val secret: String, val expiresAt: Long) {
    companion object {
        fun parse(text: String, now: Long = System.currentTimeMillis()): Pairing {
            val result = try {
                require(text.length <= 4096)
                val j = JSONObject(text); require(j.getInt("version") == 1)
                Pairing(j.getString("receiverId"), j.getString("endpoint"), j.getString("fingerprint"), j.getString("secret"), j.getLong("expiresAt")).also {
                    require(it.receiverId.matches(Regex("[0-9a-f-]{36}")))
                    endpoint(it.endpoint)
                    require(it.fingerprint.matches(Regex("[0-9a-f]{64}")) && it.secret.matches(Regex("[A-Za-z0-9_-]{43}")))
                }
            } catch (e: Exception) { throw ProtocolFailure("pairing_payload", true, e) }
            // PCs drop a code once it expires and never issue one more than 10 minutes ahead, so a far-off expiry points at the phone's clock.
            if (result.expiresAt > now + 10 * 60_000 || result.expiresAt <= now - 2 * 60_000) throw ProtocolFailure("pairing_clock", true)
            if (result.expiresAt <= now) throw ProtocolFailure("pairing_expired", true)
            return result
        }
    }
}
fun endpoint(value: String): HttpUrl {
    require(value.length <= 256)
    val parsed = value.toHttpUrl()
    require(parsed.scheme == "https" && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.encodedPath == "/" && parsed.query == null && parsed.fragment == null)
    require(parsed.host == "localhost" || parsed.host.endsWith(".local") || parsed.host.matches(Regex("[0-9.]+")) || parsed.host.contains(':'))
    return parsed
}
fun isLocalAddress(address: InetAddress): Boolean = address.isLoopbackAddress || address.isSiteLocalAddress || (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)
/** This trust manager belongs to exactly one endpoint, pinned to the PC's DER certificate. */
fun pinnedClient(address: String, fingerprint: String, network: android.net.Network? = null): OkHttpClient =
    pinnedClient(address, fingerprint, network?.socketFactory, network?.let { n -> { host: String -> n.getAllByName(host).toList() } })
internal fun pinnedClient(address: String, fingerprint: String, socketFactory: SocketFactory?, lookup: ((String) -> List<InetAddress>)?): OkHttpClient {
    val url = endpoint(address)
    require(fingerprint.matches(Regex("[0-9a-f]{64}")))
    val trust = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw java.security.cert.CertificateException("client_not_supported") }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isEmpty()) throw java.security.cert.CertificateException("certificate_missing")
            chain[0].checkValidity()
            val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded).joinToString("") { "%02x".format(it) }
            if (actual != fingerprint) throw java.security.cert.CertificateException("certificate_changed")
        }
    }
    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), java.security.SecureRandom()) }
    val builder = OkHttpClient.Builder()
    if (socketFactory != null) builder.socketFactory(socketFactory)
    return builder.sslSocketFactory(ssl.socketFactory, trust)
        // Exact DER trust permits a changed LAN IP without accepting any other certificate/host.
        .hostnameVerifier { host, _ -> host == url.host }
        .dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> { require(hostname == url.host); return (lookup?.invoke(hostname) ?: Dns.SYSTEM.lookup(hostname)).also { require(it.isNotEmpty() && it.all(::isLocalAddress)) } } })
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build()
}
/**
 * Runs [request] on the LAN-bound client, and again on the unbound one only if no TCP connection was made.
 * Android refuses to bind sockets to another network while the app is inside a VPN it cannot bypass, yet the
 * default route may still reach the PC. Both clients carry the same pinning; only the route differs.
 */
internal fun executeWithFallback(bound: OkHttpClient?, unbound: OkHttpClient, request: Request): Response {
    if (bound == null) return unbound.newCall(request).execute()
    var connected = false
    val listener = object : EventListener() {
        override fun secureConnectStart(call: Call) { connected = true }
        override fun connectionAcquired(call: Call, connection: Connection) { connected = true }
    }
    return try { bound.newBuilder().eventListener(listener).build().newCall(request).execute() }
    catch (e: IOException) {
        if (connected) throw e
        Log.w("FolderCamera", "LAN-bound connection failed before connecting; retrying on the default route", e)
        unbound.newCall(request).execute()
    }
}
class PinnedTransport(private val context: Context) {
    private fun execute(address: String, fingerprint: String, request: Request): Response {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val lan = manager.allNetworks.firstOrNull { network ->
            val caps = manager.getNetworkCapabilities(network)
            caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        }
        return executeWithFallback(lan?.let { pinnedClient(address, fingerprint, it) }, pinnedClient(address, fingerprint), request)
    }
    fun pair(p: Pairing): Receiver {
        val request = Request.Builder().url(endpoint(p.endpoint).newBuilder().addPathSegments("v1/pair").build())
            .post(JSONObject().put("version", 1).put("receiverId", p.receiverId).put("secret", p.secret).put("deviceName", "Android camera").toString().toRequestBody("application/json".toMediaType())).build()
        execute(p.endpoint, p.fingerprint, request).use { response ->
            val j = responseJson(response)
            if (j.optString("receiverId") != p.receiverId || j.optInt("version") != 1) throw ProtocolFailure("receiver_mismatch", true)
            val token = j.optString("token"); if (!token.matches(Regex("[A-Za-z0-9_-]{43}"))) throw ProtocolFailure("invalid_response", true)
            return Receiver(p.receiverId, p.endpoint, p.fingerprint, token)
        }
    }
    fun health(r: Receiver) {
        execute(r.endpoint, r.fingerprint, Request.Builder().url(endpoint(r.endpoint).newBuilder().addPathSegments("v1/health").build()).header("Authorization", "Bearer ${r.token}").build()).use {
            val j = responseJson(it); require(j.getString("receiverId") == r.id && j.getInt("version") == 1)
        }
    }
    fun upload(c: Capture, r: Receiver): String {
        val metadata = JSONObject().put("version", 1).put("photoId", c.photoId).put("filename", c.filename).put("relativePath", c.relativePath).put("mimeType", c.mimeType).put("byteSize", c.byteSize).put("sha256", c.sha256)
        val encoded = android.util.Base64.encodeToString(metadata.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        val body = object : RequestBody() {
            override fun contentType() = "image/jpeg".toMediaType()
            override fun contentLength() = c.byteSize
            override fun writeTo(sink: okio.BufferedSink) {
                val stream = context.contentResolver.openInputStream(Uri.parse(c.destinationDocumentUri ?: error("local_missing"))) ?: error("local_missing")
                val hash = MessageDigest.getInstance("SHA-256"); var size = 0L
                stream.use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n); size += n; sink.write(buffer, 0, n) } }
                if (size != c.byteSize || hash.digest().joinToString("") { "%02x".format(it) } != c.sha256) throw ProtocolFailure("local_changed", true)
            }
        }
        val request = Request.Builder().url(endpoint(r.endpoint).newBuilder().addPathSegments("v1/photos").addPathSegment(c.photoId).build())
            .header("Authorization", "Bearer ${r.token}").header("X-FolderCamera-Metadata", encoded).put(body).build()
        execute(r.endpoint, r.fingerprint, request).use { response ->
            val j = responseJson(response)
            if (j.getString("receiverId") != r.id || j.getString("photoId") != c.photoId || j.getString("filename") != c.filename || j.getString("relativePath") != c.relativePath || j.getLong("byteSize") != c.byteSize || j.getString("sha256") != c.sha256 || j.getInt("version") != 1 || j.getLong("receivedAt") <= 0) throw ProtocolFailure("receipt_mismatch", true)
            return j.toString()
        }
    }
    private fun responseJson(response: Response): JSONObject {
        val body = response.body ?: throw ProtocolFailure("invalid_response", true)
        val input = body.byteStream(); val buffer = ByteArray(1024); val output = java.io.ByteArrayOutputStream()
        while (output.size() <= 16_384) { val n = input.read(buffer, 0, minOf(buffer.size, 16_385 - output.size())); if (n < 0) break; output.write(buffer, 0, n) }
        val bytes = output.toByteArray()
        if (bytes.size > 16_384) throw ProtocolFailure("invalid_response", true)
        val j = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrElse { throw ProtocolFailure("invalid_response", true) }
        if (!response.isSuccessful) {
            val code = j.optString("code", "server_failure")
            throw ProtocolFailure(code, response.code in listOf(400, 401, 403, 404, 409, 413, 422))
        }
        return j
    }
}
