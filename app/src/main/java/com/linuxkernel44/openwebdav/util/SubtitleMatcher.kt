package com.linuxkernel44.openwebdav.util

import com.linuxkernel44.openwebdav.data.DavEntry

object SubtitleMatcher {
    /**
     * Finds the subtitle file that belongs to [video] among the files of the same folder.
     * "Movie.mkv" matches "Movie.srt" and "Movie.en.srt" but not "Movie 2.srt".
     * An exact match wins, then one tagged with [preferredLanguage], then the shortest name.
     */
    fun find(video: DavEntry, siblings: List<DavEntry>, preferredLanguage: String): DavEntry? {
        val base = video.name.substringBeforeLast('.')
        val candidates = siblings.filter {
            !it.isDirectory &&
                FileTypes.kindOf(it.name) == FileKind.SUBTITLE &&
                it.name.length > base.length &&
                it.name.startsWith(base, ignoreCase = true) &&
                it.name[base.length] == '.'
        }
        return candidates.minWithOrNull(
            compareBy<DavEntry> { rank(it.name, base, preferredLanguage) }.thenBy { it.name.length },
        )
    }

    private fun rank(name: String, base: String, language: String): Int {
        val rest = name.substring(base.length + 1).lowercase()
        return when {
            !rest.contains('.') -> 0
            rest.split('.').contains(language) -> 1
            else -> 2
        }
    }
}
