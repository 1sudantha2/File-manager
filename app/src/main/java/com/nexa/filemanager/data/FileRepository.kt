package com.nexa.filemanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.io.IOException
import java.io.OutputStream
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(private val context: Context) {
    @Suppress("DEPRECATION")
    val home: File = Environment.getExternalStorageDirectory()
    private val trash = File(context.filesDir, "recycle_bin").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val reservedRoots by lazy { listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile) }

    suspend fun list(directory: File, hidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        require(directory.canRead()) { "Android does not allow access to this directory." }
        val children = directory.listFiles() ?: error("Cannot list this directory. Android may restrict it.")
        children.asSequence().filter { hidden || !it.name.startsWith('.') }.map(FileItem::of)
            .sortedWith(compareByDescending<FileItem> { it.directory }.thenBy { it.name.lowercase() }).toList()
    }
    fun storage(): List<StorageInfo> {
        val roots = mutableListOf("Internal storage" to home)
        context.getExternalFilesDirs(null).filterNotNull().drop(1).forEach { file ->
            val path = file.absolutePath.substringBefore("/Android/")
            roots += "SD card" to File(path)
        }
        return roots.distinctBy { it.second.path }.mapNotNull { (label, file) ->
            runCatching { val stat = StatFs(file.path); StorageInfo(label, file, stat.totalBytes, stat.availableBytes) }.getOrNull()
        }
    }
    private fun writable(file: File) {
        val path = file.canonicalFile
        val imports = File(context.filesDir, "cloud_imports").canonicalPath
        require(path.path.startsWith(imports + "/") || path.path.startsWith(home.canonicalPath + "/") || storage().any { path.path.startsWith(it.root.canonicalPath + "/") }) {
            "System partitions are read-only in this app."
        }
        require(path.path.startsWith(imports + "/") || reservedRoots.none { path == it || path.path.startsWith(it.path + "/") }) { "App-private data is protected." }
        // Never mutate an entire storage root or protected Android app-data tree.
        require(!path.path.substringAfter("/Android/", "").let { path.name == "Android" || it == "data" || it.startsWith("data/") || it == "obb" || it.startsWith("obb/") }) {
            "Android app-data directories are protected."
        }
    }
    suspend fun createFolder(parent: File, name: String) = withContext(Dispatchers.IO) {
        val target = FileRules.child(parent, name); writable(target)
        require(!target.exists()) { "A file with that name already exists." }
        check(target.mkdir()) { "Unable to create folder. Check storage access." }
    }
    suspend fun rename(file: File, name: String) = withContext(Dispatchers.IO) {
        writable(file)
        val target = FileRules.child(requireNotNull(file.parentFile), name); writable(target)
        require(!target.exists()) { "A file with that name already exists." }
        Files.move(file.toPath(), target.toPath())
    }
    private suspend fun copyTree(source: File, destination: File) {
        currentCoroutineContext().ensureActive()
        require(source.canonicalPath == source.absolutePath) { "Symbolic links are not copied." }
        val originalSize = source.length()
        val originalModified = source.lastModified()
        if (source.isDirectory) {
            check(destination.mkdir()) { "Cannot create ${destination.name}." }
            val children = source.listFiles() ?: error("Cannot read ${source.path}.")
            children.forEach { copyTree(it, File(destination, it.name)) }
        } else {
            FileInputStream(source).use { input -> FileOutputStream(destination).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                output.fd.sync()
            } }
        }
        if (source.isFile) check(source.length() == originalSize && source.lastModified() == originalModified && destination.length() == originalSize) {
            "Source changed while copying. The original was kept; retry."
        }
        destination.setLastModified(originalModified)
    }
    private suspend fun transfer(source: File, target: File, move: Boolean) {
        require(source.exists()) { "Source no longer exists." }
        require(!target.exists()) { "${target.name} already exists. Rename it first; nothing was overwritten." }
        val sourcePath = source.canonicalPath
        val targetPath = target.canonicalPath
        require(targetPath != sourcePath && !targetPath.startsWith(sourcePath + "/")) { "Cannot paste a folder into itself." }
        if (move) {
            try { Files.move(source.toPath(), target.toPath()); return }
            catch (e: FileAlreadyExistsException) { throw e }
            catch (_: IOException) { /* Cross-volume moves use verified staged copying below. */ }
        }
        val staging = File(target.parentFile, ".transfer-${UUID.randomUUID()}")
        try {
            copyTree(source, staging)
            Files.move(staging.toPath(), target.toPath())
            if (move) check(source.deleteRecursively()) { "Copied safely, but could not remove the original." }
        } finally { staging.deleteRecursively() }
    }
    suspend fun paste(sources: List<File>, parent: File, move: Boolean) = withContext(Dispatchers.IO) {
        sources.forEach { source ->
            val target = FileRules.child(parent, source.name); writable(target)
            if (move) writable(source)
            transfer(source, target, move)
        }
    }
    suspend fun recycle(files: List<File>) = withContext(Dispatchers.IO) {
        files.forEach { source ->
            writable(source)
            val dir = File(trash, UUID.randomUUID().toString()).apply { check(mkdir()) }
            val info = Properties().apply {
                setProperty("name", source.name); setProperty("original", source.absolutePath)
                setProperty("deletedAt", System.currentTimeMillis().toString())
            }
            FileOutputStream(File(dir, "entry.properties")).use { info.store(it, null) }
            try { transfer(source, File(dir, "payload"), true) }
            catch (e: Exception) { if (!File(dir, "payload").exists()) dir.deleteRecursively(); throw e }
        }
    }
    suspend fun trashItems(): List<TrashItem> = withContext(Dispatchers.IO) {
        trash.listFiles().orEmpty().mapNotNull { dir -> runCatching {
            val p = Properties().apply { FileInputStream(File(dir, "entry.properties")).use { load(it) } }
            val payload = File(dir, "payload")
            if (!payload.exists()) null else TrashItem(dir.name, p.getProperty("name"), p.getProperty("original"), p.getProperty("deletedAt").toLong(), payload)
        }.getOrNull() }.sortedByDescending { it.deletedAt }
    }
    suspend fun restore(item: TrashItem) = withContext(Dispatchers.IO) {
        val target = File(item.original); writable(target)
        check(target.parentFile?.let { it.exists() || it.mkdirs() } == true) { "Original folder is not available." }
        transfer(item.payload, target, true)
        item.payload.parentFile?.deleteRecursively()
    }
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        trash.listFiles().orEmpty().forEach { check(it.deleteRecursively()) { "Could not remove ${it.name}." } }
    }
    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileItem>()
        val pending = ArrayDeque<File>()
        storage().forEach { pending.add(it.root) }
        val seen = HashSet<String>()
        var skipped = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = pending.removeLast()
            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: continue
            if (!seen.add(canonical) || canonical != dir.absolutePath) continue
            if (canonical.endsWith("/Android/data") || canonical.endsWith("/Android/obb") || reservedRoots.any { canonical.startsWith(it.path) }) continue
            val children = dir.listFiles()
            if (children == null) { skipped++; continue }
            children.forEach { child ->
                if (child.name.startsWith(".transfer-")) return@forEach
                if (child.isDirectory) pending.add(child)
                else if (child.isFile && child.canRead() && child.canonicalPath == child.absolutePath) result.add(FileItem.of(child))
            }
        }
        ScanResult(result, skipped)
    }
    suspend fun duplicates(files: List<FileItem>): List<List<FileItem>> = withContext(Dispatchers.IO) {
        val output = mutableListOf<List<FileItem>>()
        files.filter { it.bytes > 0 }.groupBy { it.bytes }.values.filter { it.size > 1 }.forEach { sizeGroup ->
            val quick = sizeGroup.groupBy { fingerprint(it.file, false) }
            quick.values.filter { it.size > 1 }.forEach { candidates ->
                candidates.groupBy { fingerprint(it.file, true) }.values.filter { it.size > 1 }.forEach(output::add)
            }
        }
        output.sortedByDescending { it.first().bytes }
    }
    private suspend fun fingerprint(file: File, full: Boolean): String {
        val hash = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                hash.update(buffer, 0, count)
                if (!full) break
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    fun bookmarks(): Set<String> = prefs.getStringSet("paths", emptySet()).orEmpty().toSet()
    fun bookmark(file: File) {
        val next = bookmarks().toMutableSet()
        if (!next.add(file.absolutePath)) next.remove(file.absolutePath)
        prefs.edit().putStringSet("paths", next).apply()
    }
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.getInstalledApplications(0)
        packages.map { app ->
            val apk = File(app.sourceDir)
            InstalledApp(app.loadLabel(pm).toString(), app.packageName, apk,
                apk.length() + app.splitSourceDirs.orEmpty().sumOf { File(it).length() },
                app.flags and ApplicationInfo.FLAG_SYSTEM != 0, app.splitSourceDirs.orEmpty().size)
        }.sortedBy { it.label.lowercase() }
    }
    suspend fun backup(app: InstalledApp) = withContext(Dispatchers.IO) {
        val dir = File(home, "APK_Backups")
        check(dir.exists() || dir.mkdirs()) { "Cannot create APK_Backups. Grant file access." }
        transfer(app.apk, File(dir, "${app.packageName}-${System.currentTimeMillis()}.apk"), false)
    }
    suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        require(file.length() <= 2 * 1024 * 1024) { "Editor is limited to 2 MB to protect memory." }
        file.readText()
    }
    suspend fun saveText(file: File, value: String) = withContext(Dispatchers.IO) {
        writable(file)
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(value.toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    suspend fun compress(files: List<File>, parent: File, name: String) = withContext(Dispatchers.IO) {
        val output = FileRules.child(parent, if (name.endsWith(".zip", true)) name else "$name.zip")
        writable(output); require(!output.exists()) { "Archive already exists." }
        files.forEach { require(output.canonicalPath != it.canonicalPath && !output.canonicalPath.startsWith(it.canonicalPath + "/")) { "Save the ZIP outside the selected folder." } }
        val temp = File(parent, ".transfer-${UUID.randomUUID()}")
        try {
            ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
                suspend fun add(file: File, path: String) {
                    currentCoroutineContext().ensureActive()
                    require(file.canonicalPath == file.absolutePath) { "Symbolic links cannot be archived." }
                    if (file.isDirectory) {
                        zip.putNextEntry(ZipEntry("$path/")); zip.closeEntry()
                        (file.listFiles() ?: error("Cannot read ${file.path}")).forEach { add(it, "$path/${it.name}") }
                    } else {
                        zip.putNextEntry(ZipEntry(path))
                        FileInputStream(file).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; zip.write(buffer, 0, n) }
                        }
                        zip.closeEntry()
                    }
                }
                files.forEach { add(it, it.name) }
            }
            Files.move(temp.toPath(), output.toPath())
        } finally { temp.delete() }
    }
    @Suppress("DEPRECATION")
    suspend fun extract(archive: File) = withContext(Dispatchers.IO) {
        val parent = requireNotNull(archive.parentFile)
        val output = FileRules.child(parent, archive.nameWithoutExtension + "_extracted")
        writable(output); require(!output.exists()) { "Extraction folder already exists." }
        val staging = File(parent, ".transfer-${UUID.randomUUID()}").apply { check(mkdir()) }
        var total = 0L
        var entries = 0
        // Bound decompression to prevent archive bombs. Extraction is transactional.
        val limit = minOf(8L * 1024 * 1024 * 1024, StatFs(parent.path).availableBytes / 2)
        suspend fun writeEntry(name: String, directory: Boolean, reader: (ByteArray) -> Int) {
            currentCoroutineContext().ensureActive()
            require(++entries <= 100_000) { "Archive has too many entries." }
            val target = FileRules.archiveTarget(staging, name)
            if (directory) { check(target.isDirectory || target.mkdirs()); return }
            check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            require(!target.exists()) { "Archive contains duplicate entry names." }
            FileOutputStream(target).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = reader(buffer); if (n < 0) break
                    total += n; require(total <= limit) { "Extraction exceeds safe free-space limit." }
                    out.write(buffer, 0, n)
                }
            }
        }
        try {
            when (archive.extension.lowercase()) {
                "zip" -> ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
                    while (true) { val entry = zip.nextEntry ?: break; writeEntry(entry.name, entry.isDirectory) { zip.read(it) }; zip.closeEntry() }
                }
                "7z" -> SevenZFile.builder().setFile(archive).setMaxMemoryLimitKb(64 * 1024).get().use { seven ->
                    while (true) { val entry = seven.nextEntry ?: break; writeEntry(entry.name, entry.isDirectory) { seven.read(it) } }
                }
                "rar" -> Archive(archive).use { rar ->
                    require(!rar.isPasswordProtected) { "Encrypted RAR files are not supported." }
                    while (true) {
                        val header = rar.nextFileHeader() ?: break
                        currentCoroutineContext().ensureActive()
                        require(++entries <= 100_000) { "Archive has too many entries." }
                        val target = FileRules.archiveTarget(staging, header.fileNameString.replace('\\', '/'))
                        if (header.isDirectory) {
                            check(target.isDirectory || target.mkdirs())
                        } else {
                            require(!target.exists()) { "Archive contains duplicate entry names." }
                            require(header.fullUnpackSize >= 0 && header.fullUnpackSize <= limit - total) { "RAR entry exceeds safe free-space limit." }
                            check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                            val coroutine = currentCoroutineContext()
                            var extracted = 0L
                            FileOutputStream(target).use { destination ->
                                val guarded = object : OutputStream() {
                                    override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                                    override fun write(buffer: ByteArray, offset: Int, length: Int) {
                                        coroutine.ensureActive()
                                        total += length; extracted += length
                                        require(total <= limit) { "Extraction exceeds safe free-space limit." }
                                        destination.write(buffer, offset, length)
                                    }
                                }
                                // Direct extraction propagates CRC/decode errors (Junrar's InputStream swallows them).
                                rar.extractFile(header, guarded)
                            }
                            require(extracted == header.fullUnpackSize) { "Incomplete RAR entry." }
                        }
                    }
                }
                else -> error("Extraction supports ZIP, RAR (v4) and 7z. Password-protected and RAR5 archives need an external app.")
            }
            Files.move(staging.toPath(), output.toPath())
        } finally { staging.deleteRecursively() }
    }
}
