package fr.strangeplanet.tubearchivist.share

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class YouTubeLinkKind {
    VIDEO,
    COLLECTION,
}

data class YouTubeLink(
    val url: String,
    val kind: YouTubeLinkKind,
)

sealed class LinkParseResult {
    data class Valid(val link: YouTubeLink) : LinkParseResult()
    data class Invalid(val reason: String) : LinkParseResult()
}

object YouTubeLinkParser {
    private val urlPattern = Regex(
        """(?i)(?<![\w@.-])(?:(?:[a-z][a-z0-9+.-]*://)?(?:www\.)?(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,})(?:[/?#][^\s<>"']*)?"""
    )

    fun parse(sharedText: String): LinkParseResult {
        val candidates = urlPattern.findAll(sharedText)
            .map { it.value.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}') }
            .filter { it.isNotBlank() }
            .toList()

        if (candidates.isEmpty()) {
            return LinkParseResult.Invalid("No URL was found in the shared text.")
        }
        if (candidates.size != 1) {
            return LinkParseResult.Invalid("Share exactly one URL at a time.")
        }

        val candidate = candidates.single()
        val hasExplicitScheme = "://" in candidate
        val normalized = if (hasExplicitScheme) candidate else "https://$candidate"
        val uri = try {
            URI(normalized)
        } catch (_: URISyntaxException) {
            return LinkParseResult.Invalid("The shared URL is not valid.")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return LinkParseResult.Invalid("Only HTTP or HTTPS YouTube links are supported.")
        }
        if (uri.rawUserInfo != null) {
            return LinkParseResult.Invalid("The shared URL is not valid.")
        }

        val host = uri.host?.lowercase(Locale.ROOT)
            ?: return LinkParseResult.Invalid("The shared URL is not valid.")
        val path = uri.path.orEmpty()
        val query = parseQuery(uri.rawQuery.orEmpty())

        val kind = when {
            host == "youtu.be" -> {
                if (path.trim('/').isBlank()) {
                    return LinkParseResult.Invalid("The YouTube link does not identify a video.")
                }
                if (!query["list"].isNullOrBlank()) {
                    YouTubeLinkKind.COLLECTION
                } else {
                    YouTubeLinkKind.VIDEO
                }
            }
            host == "youtube.com" || host.endsWith(".youtube.com") -> {
                classifyYouTubePath(path, query)
                    ?: return LinkParseResult.Invalid("This YouTube link type is not supported.")
            }
            else -> return LinkParseResult.Invalid("Share a YouTube video, playlist, or channel URL.")
        }

        val url = if (hasExplicitScheme) candidate else "https://$candidate"
        return LinkParseResult.Valid(YouTubeLink(url, kind))
    }

    private fun classifyYouTubePath(path: String, query: Map<String, String>): YouTubeLinkKind? {
        val segments = path.split('/').filter { it.isNotBlank() }
        val hasPlaylist = !query["list"].isNullOrBlank()

        if (hasPlaylist) {
            return YouTubeLinkKind.COLLECTION
        }
        if (segments.firstOrNull() == "watch" && !query["v"].isNullOrBlank()) {
            return YouTubeLinkKind.VIDEO
        }
        if (segments.firstOrNull() in setOf("shorts", "live", "embed", "v") && segments.getOrNull(1) != null) {
            return YouTubeLinkKind.VIDEO
        }
        if (segments.firstOrNull() in setOf("playlist", "channel", "c", "user") && segments.getOrNull(1) != null) {
            return YouTubeLinkKind.COLLECTION
        }
        if (segments.firstOrNull()?.startsWith("@") == true && segments.first().length > 1) {
            return YouTubeLinkKind.COLLECTION
        }

        return null
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { item ->
            val parts = item.split('=', limit = 2)
            val name = parts[0]
            if (name.isBlank()) null else name to parts.getOrElse(1) { "" }
        }.toMap()
    }
}
