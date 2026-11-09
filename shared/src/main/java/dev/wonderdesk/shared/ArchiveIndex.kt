package dev.wonderdesk.shared

data class ArchiveSummary(val files: Int, val directories: Int, val bytes: Long)

fun summarizeArchive(entries: List<ZipItem>): ArchiveSummary = ArchiveSummary(
    files = entries.count { !it.directory },
    directories = entries.count { it.directory },
    bytes = entries.filterNot { it.directory }.sumOf { it.bytes }
)

enum class ArchiveOrder(val label: String) { PATH("Path A–Z"), SIZE("Largest first") }

fun sortArchiveEntries(entries: List<ZipItem>, order: ArchiveOrder): List<ZipItem> = when (order) {
    ArchiveOrder.PATH -> entries.sortedWith(compareBy({ !it.directory }, { it.path.lowercase() }, { it.path }))
    ArchiveOrder.SIZE -> entries.sortedWith(compareBy({ !it.directory }, { if (it.directory) 0L else -it.bytes }, { it.path.lowercase() }, { it.path }))
}

fun filterArchive(entries: List<ZipItem>, query: String): List<ZipItem> =
    if (query.isBlank()) entries else entries.filter { it.path.contains(query.trim(), ignoreCase = true) }
