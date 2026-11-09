package dev.wonderdesk.shared

data class ArchiveSummary(val files: Int, val directories: Int, val bytes: Long)

fun summarizeArchive(entries: List<ZipItem>): ArchiveSummary = ArchiveSummary(
    files = entries.count { !it.directory },
    directories = entries.count { it.directory },
    bytes = entries.filterNot { it.directory }.sumOf { it.bytes }
)

fun filterArchive(entries: List<ZipItem>, query: String): List<ZipItem> =
    if (query.isBlank()) entries else entries.filter { it.path.contains(query.trim(), ignoreCase = true) }
