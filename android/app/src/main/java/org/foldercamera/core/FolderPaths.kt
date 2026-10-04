package org.foldercamera.core

/** A complete catalog, with ancestors included and no recency cap. Accepted spelling stays exact. */
object FolderPaths {
    fun all(paths: Collection<String>): List<String> {
        val result = linkedSetOf<String>()
        for (path in paths) {
            if (PortablePath.validate(path) != null) continue
            val segments = path.split('/')
            for (count in 1..segments.size) result.add(segments.take(count).joinToString("/"))
        }
        return result.sortedWith(compareBy<String> { it.lowercase(java.util.Locale.ROOT) }.thenBy { it })
    }
    fun visible(paths: List<String>, parent: String, search: String): List<String> {
        if (search.isNotBlank()) return paths.filter { it.contains(search, ignoreCase = true) }
        return paths.filter { it.substringBeforeLast('/', "") == parent }
    }
}
