package com.linuxkernel44.openwebdav

import com.linuxkernel44.openwebdav.data.DavEntry
import com.linuxkernel44.openwebdav.data.SortField
import com.linuxkernel44.openwebdav.util.NaturalOrder
import com.linuxkernel44.openwebdav.util.SubtitleMatcher
import com.linuxkernel44.openwebdav.util.normalizeServerUrl
import com.linuxkernel44.openwebdav.util.parentUrl
import com.linuxkernel44.openwebdav.util.sortEntries
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilTest {
    private fun entry(name: String, dir: Boolean = false, size: Long? = null, modified: Long? = null) = DavEntry(
        name = name,
        url = "https://dav.example.com/x/${name}${if (dir) "/" else ""}".toHttpUrl(),
        isDirectory = dir,
        size = size,
        modified = modified,
        contentType = null,
    )

    @Test
    fun naturalOrderHandlesNumbersAndCase() {
        val sorted = listOf("E10.mkv", "e2.mkv", "E1.mkv", "a.mkv").sortedWith(NaturalOrder)
        assertEquals(listOf("a.mkv", "E1.mkv", "e2.mkv", "E10.mkv"), sorted)
    }

    @Test
    fun foldersComeFirstWhateverTheOrder() {
        val items = listOf(entry("b.mkv"), entry("Folder", dir = true), entry("a.mkv"))
        assertEquals(listOf("Folder", "a.mkv", "b.mkv"), sortEntries(items, SortField.NAME, true).map { it.name })
        assertEquals(listOf("Folder", "b.mkv", "a.mkv"), sortEntries(items, SortField.NAME, false).map { it.name })
    }

    @Test
    fun sortsBySizeAndDate() {
        val items = listOf(entry("a", size = 5, modified = 30), entry("b", size = 1, modified = 20), entry("c", size = 3, modified = 10))
        assertEquals(listOf("b", "c", "a"), sortEntries(items, SortField.SIZE, true).map { it.name })
        assertEquals(listOf("c", "b", "a"), sortEntries(items, SortField.DATE, true).map { it.name })
    }

    @Test
    fun subtitleMatchingIsStrict() {
        val video = entry("Movie.2020.mkv")
        val siblings = listOf(
            entry("Movie.2020.en.srt"),
            entry("Movie.2020.fr.srt"),
            entry("Movie.2020 Extended.srt"),
            entry("Other.srt"),
            entry("Movie.2020.mkv"),
        )
        assertEquals("Movie.2020.fr.srt", SubtitleMatcher.find(video, siblings, "fr")?.name)
        assertEquals("Movie.2020.en.srt", SubtitleMatcher.find(video, siblings, "en")?.name)
        assertEquals("Movie.2020.srt", SubtitleMatcher.find(video, siblings + entry("Movie.2020.srt"), "fr")?.name)
        assertNull(SubtitleMatcher.find(entry("Lonely.mkv"), siblings, "en"))
    }

    @Test
    fun normalizesServerUrls() {
        assertEquals("https://dav.example.com/", normalizeServerUrl("  dav.example.com ")?.toString())
        assertEquals("https://dav.example.com/dav/", normalizeServerUrl("https://u:p@dav.example.com/dav?x=1#y")?.toString())
        assertNull(normalizeServerUrl("http://dav.example.com"))
        assertEquals("http://10.0.2.2:8080/", normalizeServerUrl("http://10.0.2.2:8080", allowCleartext = true)?.toString())
        assertNull(normalizeServerUrl("   "))
    }

    @Test
    fun parentUrlStopsAtRoot() {
        assertEquals("https://h/a/", "https://h/a/b/".toHttpUrl().parentUrl().toString())
        assertEquals("https://h/", "https://h/a/".toHttpUrl().parentUrl().toString())
        assertEquals("https://h/", "https://h/".toHttpUrl().parentUrl().toString())
        assertEquals("https://h/a%20b/", "https://h/a%20b/c/".toHttpUrl().parentUrl().toString())
    }
}
