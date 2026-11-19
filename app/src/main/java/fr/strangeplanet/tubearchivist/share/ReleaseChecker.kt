package fr.strangeplanet.tubearchivist.share

import android.content.Context
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

data class ReleaseInfo(val version: String, val url: String)

class ReleaseChecker(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun cachedRelease(): ReleaseInfo? {
        val version = preferences.getString(KEY_VERSION, null) ?: return null
        val url = preferences.getString(KEY_URL, null) ?: return null
        return ReleaseInfo(version, url)
    }

    fun checkIfDue(currentTimeMillis: Long = System.currentTimeMillis()): ReleaseInfo? {
        val cached = cachedRelease()
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0)
        if (currentTimeMillis - lastCheck < CHECK_INTERVAL_MILLIS) return cached

        preferences.edit().putLong(KEY_LAST_CHECK, currentTimeMillis).apply()
        return try {
            fetchLatestRelease().also { release ->
                preferences.edit()
                    .putString(KEY_VERSION, release.version)
                    .putString(KEY_URL, release.url)
                    .apply()
            }
        } catch (error: IOException) {
            Log.w(TAG, "Unable to check for app updates", error)
            cached
        } catch (error: JSONException) {
            Log.w(TAG, "Unable to parse the latest GitHub release", error)
            cached
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = URL(RELEASE_API_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "TubeArchivistShare")

            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP ${connection.responseCode}.")
            }

            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(response)
            val tag = json.getString("tag_name")
            val url = json.getString("html_url")
            val uri = try {
                URI(url)
            } catch (error: URISyntaxException) {
                throw IOException("GitHub returned an invalid release URL.", error)
            }

            return ReleaseInfo(tag.removePrefix("v"), url)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ReleaseChecker"
        const val PREFERENCES_NAME = "release_check"
        const val KEY_VERSION = "latest_version"
        const val KEY_URL = "latest_release_url"
        const val KEY_LAST_CHECK = "last_check"
        const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
        const val RELEASE_API_URL =
            "https://api.github.com/repos/mistic100/tubearchivist-android-share/releases/latest"
    }
}

internal fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = parseVersion(latest) ?: return false
    val currentParts = parseVersion(current) ?: return false

    val numericComparison = (0 until maxOf(latestParts.numbers.size, currentParts.numbers.size))
        .asSequence()
        .map { index ->
            (latestParts.numbers.getOrNull(index) ?: 0L)
                .compareTo(currentParts.numbers.getOrNull(index) ?: 0L)
        }
        .firstOrNull { it != 0 }
        ?: 0
    if (numericComparison != 0) return numericComparison > 0

    if (latestParts.prerelease == null) return currentParts.prerelease != null
    if (currentParts.prerelease == null) return false

    for (index in 0 until maxOf(latestParts.prerelease.size, currentParts.prerelease.size)) {
        val latestPart = latestParts.prerelease.getOrNull(index) ?: return false
        val currentPart = currentParts.prerelease.getOrNull(index) ?: return true
        val latestNumber = latestPart.toLongOrNull()
        val currentNumber = currentPart.toLongOrNull()
        val comparison = when {
            latestNumber != null && currentNumber != null -> latestNumber.compareTo(currentNumber)
            latestNumber != null -> -1
            currentNumber != null -> 1
            else -> latestPart.compareTo(currentPart)
        }
        if (comparison != 0) return comparison > 0
    }
    return false
}

private data class ParsedVersion(val numbers: List<Long>, val prerelease: List<String>?)

private fun parseVersion(value: String): ParsedVersion? {
    val match = VERSION_PATTERN.matchEntire(value) ?: return null
    val numbers = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
    val prerelease = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.')
    return ParsedVersion(numbers, prerelease)
}

private val VERSION_PATTERN =
    Regex("""^v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")
