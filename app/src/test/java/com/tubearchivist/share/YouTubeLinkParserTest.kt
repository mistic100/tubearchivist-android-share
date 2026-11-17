package fr.strangeplanet.tubearchivist.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinkParserTest {
    @Test
    fun extractsVideoUrlFromSharedText() {
        val result = YouTubeLinkParser.parse("Check this out: https://www.youtube.com/watch?v=abc123")

        assertTrue(result is LinkParseResult.Valid)
        assertEquals(
            YouTubeLink(
                "https://www.youtube.com/watch?v=abc123",
                YouTubeLinkKind.VIDEO,
            ),
            (result as LinkParseResult.Valid).link,
        )
    }

    @Test
    fun acceptsShortVideoUrlWithTrailingPunctuation() {
        val result = YouTubeLinkParser.parse("https://youtu.be/abc123?feature=shared.")

        assertTrue(result is LinkParseResult.Valid)
        val link = (result as LinkParseResult.Valid).link
        assertEquals(YouTubeLinkKind.VIDEO, link.kind)
        assertEquals("https://youtu.be/abc123?feature=shared", link.url)
    }

    @Test
    fun addsHttpsToBareYouTubeUrls() {
        val result = YouTubeLinkParser.parse("youtube.com/shorts/abc123")

        assertTrue(result is LinkParseResult.Valid)
        val link = (result as LinkParseResult.Valid).link
        assertEquals("https://youtube.com/shorts/abc123", link.url)
        assertEquals(YouTubeLinkKind.VIDEO, link.kind)
    }

    @Test
    fun classifiesPlaylistAndChannelLinksAsCollections() {
        val playlist = YouTubeLinkParser.parse("https://youtube.com/playlist?list=PL123")
        val channel = YouTubeLinkParser.parse("https://youtube.com/@creator/videos")
        val videoWithPlaylist = YouTubeLinkParser.parse("https://youtu.be/abc123?list=PL123")

        assertEquals(YouTubeLinkKind.COLLECTION, (playlist as LinkParseResult.Valid).link.kind)
        assertEquals(YouTubeLinkKind.COLLECTION, (channel as LinkParseResult.Valid).link.kind)
        assertEquals(
            YouTubeLinkKind.COLLECTION,
            (videoWithPlaylist as LinkParseResult.Valid).link.kind,
        )
    }

    @Test
    fun rejectsMissingOrMultipleUrls() {
        assertTrue(YouTubeLinkParser.parse("No link here") is LinkParseResult.Invalid)
        assertTrue(
            YouTubeLinkParser.parse(
                "https://youtube.com/watch?v=one https://example.com"
            ) is LinkParseResult.Invalid
        )
    }

    @Test
    fun rejectsUnsupportedHostsAndYouTubePages() {
        assertTrue(YouTubeLinkParser.parse("https://example.com/video").let { it is LinkParseResult.Invalid })
        assertTrue(YouTubeLinkParser.parse("https://youtube.com/").let { it is LinkParseResult.Invalid })
    }
}
