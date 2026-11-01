package io.nekohasekai.sagernet.database.backup

import android.util.Xml
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** WebDAV storage for this fork's existing JSON backup format. */
class WebDavBackup private constructor(
    private val directory: HttpUrl,
    private val username: String,
    private val password: String,
) {
    data class Entry(val name: String, val modified: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun request(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", Credentials.basic(username, password, Charsets.UTF_8))

    fun check() = ensureDirectory()

    fun upload(name: String, content: ByteArray) {
        ensureDirectory()
        val url = fileUrl(name)
        val request = request(url).put(content.toRequestBody(BINARY)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
        }
    }

    fun list(): List<Entry> {
        ensureDirectory()
        val request = request(directory).method("PROPFIND", "".toRequestBody(XML))
            .header("Depth", "1").build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404) return emptyList()
            if (response.code !in listOf(200, 207)) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty WebDAV response")
            val bytes = body.byteStream().use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > MAX_LIST_BYTES) throw IOException("WebDAV listing is too large")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            parseListing(bytes)
        }
    }

    fun download(name: String): ByteArray {
        val request = request(fileUrl(name)).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty WebDAV backup")
            if (body.contentLength() > MAX_BACKUP_BYTES) throw IOException("WebDAV backup is too large")
            body.byteStream().use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > MAX_BACKUP_BYTES) throw IOException("WebDAV backup is too large")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        }
    }

    private fun ensureDirectory() {
        val request = request(directory).method("MKCOL", null).build()
        client.newCall(request).execute().use { response ->
            if (response.code in listOf(200, 201, 204)) return
            if (response.code != 405) throw httpError(response.code)
        }
        val verify = request(directory).method("PROPFIND", "".toRequestBody(XML))
            .header("Depth", "0").build()
        client.newCall(verify).execute().use { response ->
            if (response.code !in listOf(200, 207)) throw httpError(response.code)
        }
    }

    private fun fileUrl(name: String): HttpUrl {
        if (!SAFE_NAME.matches(name)) throw IOException("Invalid backup filename")
        return directory.newBuilder().addPathSegment(name).build()
    }

    private fun parseListing(bytes: ByteArray): List<Entry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(bytes.inputStream(), "UTF-8")
        val entries = mutableListOf<Entry>()
        var event = parser.eventType
        var href: String? = null
        var modified: String? = null
        var inResponse = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "response" -> { inResponse = true; href = null; modified = null }
                    "href" -> if (inResponse) href = parser.nextText()
                    "getlastmodified" -> if (inResponse) modified = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "response") {
                    val raw = href?.substringBefore('?')?.trimEnd('/')
                    val name = raw?.substringAfterLast('/')?.let { android.net.Uri.decode(it) }
                    if (name != null && SAFE_NAME.matches(name)) entries.add(Entry(name, modified.orEmpty()))
                    inResponse = false
                }
            }
            event = parser.next()
        }
        return entries.distinctBy { it.name }.sortedByDescending { it.modified }
    }

    private fun httpError(code: Int): IOException = when (code) {
        401, 403 -> IOException("WebDAV authentication or access denied (HTTP $code)")
        404 -> IOException("WebDAV directory or backup not found (HTTP 404)")
        in 300..399 -> IOException("WebDAV redirected the request (HTTP $code); check the server URL")
        else -> IOException("WebDAV request failed (HTTP $code)")
    }

    companion object {
        private const val MAX_LIST_BYTES = 2 * 1024 * 1024
        private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
        private val SAFE_NAME = Regex("nekobox_backup_[0-9]{8}_[0-9]{6}\\.(?:json|enc)")
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val BINARY = "application/octet-stream".toMediaType()

        fun create(serverUrl: String, username: String, password: String): WebDavBackup {
            val parsed = serverUrl.trim().toHttpUrlOrNull()
                ?: throw IOException("Enter a valid HTTPS WebDAV URL")
            if (parsed.isHttps.not() || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                parsed.query != null || parsed.fragment != null) {
                throw IOException("Use an HTTPS URL without embedded credentials, query or fragment")
            }
            val builder = parsed.newBuilder()
            if (!parsed.encodedPath.endsWith("/")) builder.addPathSegment("")
            val normalized = builder.build()
            val folder = normalized.newBuilder().addPathSegment("NekoBox").addPathSegment("").build()
            return WebDavBackup(folder, username, password)
        }

    }
}
