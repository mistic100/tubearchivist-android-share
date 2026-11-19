package fr.strangeplanet.tubearchivist.share

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

data class InstanceConfig(
    val baseUrl: String,
    val apiToken: String,
    var autoDownload: Boolean,
)

class TubeArchivistApi {
    fun validateConfig(config: InstanceConfig) {
        normalizeBaseUrl(config.baseUrl)
        if (config.apiToken.isBlank()) {
            throw ApiException("Enter the admin API token.")
        }
    }

    fun testConnection(config: InstanceConfig) {
        request(config, method = "GET", url = "download/")
    }

    fun enqueue(config: InstanceConfig, link: YouTubeLink) {
        val jsonUrl = link.url.jsonString()
        val body = """{"data":[{"youtube_id":$jsonUrl,"status":"pending"}]}"""
        val url = "download/?autostart=${config.autoDownload.toString()}";
        request(config, method = "POST", url = url, body = body)
    }

    private fun request(config: InstanceConfig, method: String, url: String, body: String? = null) {
        validateConfig(config)
        val baseUrl = normalizeBaseUrl(config.baseUrl)
        val connection = try {
            URL("$baseUrl/api/$url").openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw ApiException("Could not connect to the TubeArchivist instance: ${error.message ?: "invalid server URL"}")
        }

        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Token ${config.apiToken}")
            connection.setRequestProperty("Accept", "application/json")

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw ApiException(errorMessage(connection, statusCode))
            }
        } catch (error: IOException) {
            throw ApiException("Could not reach TubeArchivist: ${error.message ?: "network error"}")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(connection: HttpURLConnection, statusCode: Int): String {
        val detail = try {
            connection.errorStream?.use { it.readLimitedText(4096) }
                ?.replace(Regex("\\s+"), " ")
                ?.take(300)
        } catch (_: IOException) {
            null
        }
        return if (detail.isNullOrBlank()) {
            "TubeArchivist returned HTTP $statusCode. Check the server URL and admin API token."
        } else {
            "TubeArchivist returned HTTP $statusCode: $detail"
        }
    }

    private fun InputStream.readLimitedText(maxBytes: Int): String {
        val buffer = ByteArray(1024)
        val output = java.io.ByteArrayOutputStream()
        while (output.size() < maxBytes) {
            val remaining = maxBytes - output.size()
            val count = read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            throw ApiException("Enter a valid HTTP or HTTPS instance URL.")
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null ||
            uri.rawQuery != null ||
            uri.rawFragment != null
        ) {
            throw ApiException("Enter a valid HTTP or HTTPS instance URL without credentials, query, or fragment.")
        }
        return trimmed
    }

    private fun String.jsonString(): String = buildString {
        append('"')
        this@jsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

class ApiException(message: String) : Exception(message)
