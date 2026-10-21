package com.linuxkernel44.openwebdav.util

import com.linuxkernel44.openwebdav.data.DavEntry
import com.linuxkernel44.openwebdav.data.SortField

/** Compares names the way people expect: case-insensitive, and "E2" before "E10". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length.compareTo(nb.length)
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }
}

/** Folders always come first; [field] and [ascending] apply within each group. */
fun sortEntries(entries: List<DavEntry>, field: SortField, ascending: Boolean): List<DavEntry> {
    val byName = Comparator<DavEntry> { x, y -> NaturalOrder.compare(x.name, y.name) }
    val primary: Comparator<DavEntry> = when (field) {
        SortField.NAME -> byName
        SortField.DATE -> compareBy<DavEntry> { it.modified ?: 0L }.then(byName)
        SortField.SIZE -> compareBy<DavEntry> { it.size ?: 0L }.then(byName)
    }
    val ordered = if (ascending) primary else primary.reversed()
    return entries.sortedWith(compareByDescending<DavEntry> { it.isDirectory }.then(ordered))
}
