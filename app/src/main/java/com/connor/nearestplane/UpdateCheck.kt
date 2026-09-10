package com.connor.nearestplane

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The newest release GitHub knows about. */
data class Release(
    /** Numeric version, tag with its "v" stripped: "1.6". */
    val version: String,
    /** Direct link to the APK, when the release has one attached. */
    val apkUrl: String?,
    /** The release page, as a fallback and for the notes. */
    val pageUrl: String
)

/**
 * A sideloaded app has no update channel — Android never checks anywhere, so
 * without this nothing would ever tell you a new version exists.
 *
 * This works because the repository is public: the releases API needs no
 * authentication, so there's no token to ship inside the APK. If the repo were
 * private again this would have to go, rather than carry a credential that
 * anyone could pull back out of the package.
 */
object UpdateCheck {

    private const val LATEST =
        "https://api.github.com/repos/Blueangelman36/NearestPlane/releases/latest"

    private const val TIMEOUT_MS = 10_000

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val body = (URL(LATEST).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "NearestPlaneWidget/1.0")
            try {
                if (responseCode !in 200..299) return@withContext null
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }

        val o = JSONObject(body)
        val tag = o.optString("tag_name", "").ifBlank { return@withContext null }
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name", "").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url", "")
            ?.ifBlank { null }

        Release(
            version = tag.removePrefix("v").removePrefix("V"),
            apkUrl = apk,
            pageUrl = o.optString("html_url", "").ifBlank {
                "https://github.com/Blueangelman36/NearestPlane/releases"
            }
        )
    }

    /**
     * Compares version numbers componentwise, so 1.10 beats 1.9. A string
     * compare would get that backwards, and would do it silently at exactly
     * the moment the version numbers stop being single digits.
     */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split(".")
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
