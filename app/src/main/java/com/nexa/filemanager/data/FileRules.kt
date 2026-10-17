package com.nexa.filemanager.data

import java.io.File
import java.util.Locale

object FileRules {
    val image = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif")
    val video = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")
    val audio = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus")
    val text = setOf("txt", "json", "xml", "log", "md", "kt", "py", "csv", "html", "css", "js", "yaml", "yml")
    val archives = setOf("zip", "rar", "7z", "tar", "gz")
    fun validName(name: String): Boolean = name.isNotBlank() && name == name.trim() &&
        name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' || it.isISOControl() } && name.toByteArray().size <= 240
    fun child(parent: File, name: String): File {
        require(validName(name)) { "Use a valid single file or folder name (up to 240 bytes)." }
        return File(parent, name)
    }
    fun archiveTarget(root: File, entry: String): File {
        require(!entry.startsWith('/') && !entry.contains('\\') && !(entry.length > 1 && entry[1] == ':')) { "Unsafe archive entry." }
        val canonicalRoot = root.canonicalFile
        val result = File(root, entry).canonicalFile
        require(result.path.startsWith(canonicalRoot.path + File.separator)) { "Archive path escapes destination." }
        return result
    }
    fun category(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
        in image -> "Images"
        in video -> "Videos"
        in audio -> "Audio"
        "apk" -> "APKs"
        in archives -> "Archives"
        else -> "Documents"
    }
    fun junk(file: File): Boolean = junk(file.name, file.length())
    fun junk(filename: String, bytes: Long): Boolean {
        val name = filename.lowercase(Locale.ROOT)
        return bytes == 0L || name.substringAfterLast('.', "") in setOf("tmp", "log", "bak", "old") || name.startsWith(".thumb")
    }
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var i = 0
        while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", value, units[i])
    }
}

data class FileItem(val file: File, val directory: Boolean, val bytes: Long, val modified: Long, val readable: Boolean) {
    val path: String get() = file.absolutePath
    val name: String get() = file.name.ifEmpty { "/" }
    companion object {
        fun of(file: File) = FileItem(file, file.isDirectory, file.length(), file.lastModified(), file.canRead())
    }
}
data class StorageInfo(val label: String, val root: File, val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0)
}
data class ScanResult(val files: List<FileItem> = emptyList(), val skipped: Int = 0)
data class TrashItem(val id: String, val name: String, val original: String, val deletedAt: Long, val payload: File)
data class VaultItem(val id: String, val name: String, val original: String, val size: Long, val payload: File)
data class InstalledApp(val label: String, val packageName: String, val apk: File, val bytes: Long, val system: Boolean, val splitCount: Int)
