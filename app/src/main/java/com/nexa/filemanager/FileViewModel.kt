package com.nexa.filemanager

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.filemanager.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FileViewModel(app: Application) : AndroidViewModel(app) {
    val repository = FileRepository(app)
    private val vault = VaultRepository(app)
    private val prefs = app.getSharedPreferences("appearance", 0)
    var dark by mutableStateOf(prefs.getBoolean("dark", true))
        private set
    var hidden by mutableStateOf(prefs.getBoolean("hidden", false))
        private set
    var page by mutableStateOf("Home")
        private set
    var directory by mutableStateOf(repository.home)
        private set
    var entries by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var storage by mutableStateOf<List<StorageInfo>>(emptyList())
        private set
    var scan by mutableStateOf(ScanResult())
        private set
    var scanned by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
    var sort by mutableStateOf("Name")
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var bookmarks by mutableStateOf(repository.bookmarks())
        private set
    var clipboard by mutableStateOf<List<File>>(emptyList())
        private set
    var cutting by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var trash by mutableStateOf<List<TrashItem>>(emptyList())
        private set
    var apps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var duplicateGroups by mutableStateOf<List<List<FileItem>>>(emptyList())
        private set
    var vaultItems by mutableStateOf<List<VaultItem>>(emptyList())
        private set
    var vaultConfigured by mutableStateOf(vault.configured)
        private set
    var vaultUnlocked by mutableStateOf(false)
        private set
    var editorFile by mutableStateOf<File?>(null)
        private set
    var editorContent by mutableStateOf("")
        private set
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var pendingVaultFiles: List<File> = emptyList()
    private var listing: Job? = null
    private var scanningJob: Job? = null
    private var operation: Job? = null
    var visible by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var categoryCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    private data class FilterQuery(val page: String, val entries: List<FileItem>, val scanned: List<FileItem>,
        val bookmarks: Set<String>, val search: String, val sort: String, val hidden: Boolean)
    init {
        refresh()
        viewModelScope.launch {
            snapshotFlow { FilterQuery(page, entries, scan.files, bookmarks, search, sort, hidden) }
                .mapLatest { query -> withContext(Dispatchers.IO) { filterFiles(query) } }
                .collect { visible = it }
        }
    }

    fun notify(message: String) { messages.trySend(message) }
    fun clearError() { error = null }
    fun theme() { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }
    fun toggleHidden() { hidden = !hidden; prefs.edit().putBoolean("hidden", hidden).apply(); refresh() }
    fun navigate(target: String) {
        if (busy) return
        if (page == "Vault" && target != "Vault") lockVault()
        page = target; search = ""; selected = emptySet(); error = null
        when (target) {
            "Browse", "Root" -> refresh()
            "Trash" -> task("Loading trash") { trash = repository.trashItems() }
            "Apps" -> task("Reading installed apps") { apps = repository.installedApps() }
            "Vault" -> if (vaultUnlocked) loadVault()
            "Images", "Videos", "Audio", "Documents", "APKs", "Archives", "Downloads", "Large files", "Junk", "Duplicates" -> {
                if (!scanned) startScan() else if (target == "Duplicates") findDuplicates()
            }
        }
    }
    fun browse(file: File, root: Boolean = false) {
        if (busy) return
        directory = file; page = if (root) "Root" else "Browse"; search = ""; selected = emptySet(); refresh()
    }
    fun back(): Boolean {
        if (editorFile != null) { closeEditor(); return true }
        if (selected.isNotEmpty()) { selected = emptySet(); return true }
        if ((page == "Browse" || page == "Root") && directory.parentFile != null && (page == "Root" || storage.any { directory.path.startsWith(it.root.path + "/") })) {
            browse(requireNotNull(directory.parentFile), page == "Root"); return true
        }
        if (page != "Home") { navigate("Home"); return true }
        return false
    }
    fun refresh() {
        listing?.cancel()
        listing = viewModelScope.launch {
            storage = withContext(Dispatchers.IO) { repository.storage() }
            if (page == "Browse" || page == "Root") {
                entries = emptyList(); error = null
                try { entries = repository.list(directory, hidden) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Unable to list folder." }
            } else if (page == "Apps" && !busy) {
                try { apps = repository.installedApps() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { notify(e.message ?: "Cannot refresh applications.") }
            }
        }
    }
    fun select(file: File) { selected = if (file.path in selected) selected - file.path else selected + file.path }
    fun selectAll() { selected = visibleFiles().map { it.path }.toSet() }
    fun clearSelection() { selected = emptySet() }
    fun visibleFiles(): List<FileItem> = visible
    private fun filterFiles(query: FilterQuery): List<FileItem> {
        val source = when (query.page) {
            "Browse", "Root" -> query.entries
            "Favorites" -> query.bookmarks.mapNotNull { runCatching { FileItem.of(File(it)) }.getOrNull() }
            "Large files" -> query.scanned.filter { it.bytes > 25L * 1024 * 1024 }.sortedByDescending { it.bytes }
            "Junk" -> query.scanned.filter { FileRules.junk(it.name, it.bytes) }
            "Downloads" -> query.scanned.filter { it.path.contains("/Download/") || it.path.contains("/Downloads/") }
            else -> query.scanned.filter { FileRules.category(it.file) == query.page }
        }
        val result = source.filter { (query.hidden || !it.name.startsWith('.')) && it.name.contains(query.search, true) }
        if (query.page == "Large files") return result
        return when (query.sort) {
            "Size" -> result.sortedWith(compareByDescending<FileItem> { it.directory }.thenByDescending { it.bytes })
            "Recent" -> result.sortedWith(compareByDescending<FileItem> { it.directory }.thenByDescending { it.modified })
            else -> result.sortedWith(compareByDescending<FileItem> { it.directory }.thenBy { it.name.lowercase() })
        }
    }
    private fun chosen(): List<File> = selected.map(::File)
    fun copy(move: Boolean) { clipboard = chosen(); cutting = move; selected = emptySet(); notify(if (move) "Choose a folder, then Move here." else "Choose a folder, then Paste.") }
    fun clearClipboard() { clipboard = emptyList() }
    fun paste() = task(if (cutting) "Moving files" else "Copying files") {
        repository.paste(clipboard, directory, cutting); clipboard = emptyList(); changed("Files transferred")
    }
    fun createFolder(name: String) = task("Creating folder") { repository.createFolder(directory, name); refresh() }
    fun rename(file: File, name: String) = task("Renaming") { repository.rename(file, name); selected = emptySet(); changed("Renamed") }
    fun delete(files: List<File> = chosen()) = task("Moving to trash") {
        repository.recycle(files); selected = emptySet(); changed("Moved to trash — restore from Tools → Recycle bin")
    }
    fun bookmark(file: File) { repository.bookmark(file); bookmarks = repository.bookmarks() }
    fun compress(name: String) = task("Creating ZIP") { repository.compress(chosen(), directory, name); selected = emptySet(); changed("ZIP created") }
    fun extract(file: File) = task("Extracting archive") { repository.extract(file); changed("Extracted beside original archive") }
    fun restore(item: TrashItem) = task("Restoring") { repository.restore(item); trash = repository.trashItems(); changed("Restored to original folder") }
    fun emptyTrash() = task("Emptying trash") { repository.emptyTrash(); trash = repository.trashItems(); refresh(); notify("Trash permanently emptied") }
    fun backup(app: InstalledApp) = task("Backing up base APK") { repository.backup(app); notify("Saved base APK to /APK_Backups. Split APKs are not included.") }
    fun startScan() {
        if (scanning || busy) return
        scanningJob = viewModelScope.launch {
            scanning = true
            try {
                scan = repository.scan()
                categoryCounts = withContext(Dispatchers.Default) { scan.files.groupingBy { FileRules.category(it.file) }.eachCount() }
                scanned = true
                if (page == "Duplicates") findDuplicates()
                notify("Indexed ${scan.files.size} files; ${scan.skipped} restricted folders skipped")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notify(e.message ?: "Scan failed") }
            finally { scanning = false }
        }
    }
    fun cancelScan() { scanningJob?.cancel(); scanning = false }
    private fun findDuplicates() = task("Comparing SHA-256 fingerprints") { duplicateGroups = repository.duplicates(scan.files) }
    fun duplicateCopies(): List<File> = duplicateGroups.flatMap { it.drop(1) }.map { it.file }
    fun setupPin(pin: String, confirmation: String) = task("Securing vault") {
        require(pin == confirmation) { "PINs do not match." }
        vault.setPin(pin); vaultConfigured = true; vaultUnlocked = true; importPendingVaultFiles(); vaultItems = vault.items()
    }
    fun unlock(pin: String) = task("Unlocking vault") {
        require(vault.unlock(pin)) { "Incorrect PIN." }
        vaultUnlocked = true; importPendingVaultFiles(); vaultItems = vault.items()
    }
    fun biometricUnlock() {
        if (!vaultConfigured || System.currentTimeMillis() < vault.lockedUntil) {
            notify("PIN lockout active. Please wait before trying again."); return
        }
        task("Unlocking vault") {
            vaultUnlocked = true; importPendingVaultFiles(); vaultItems = vault.items()
        }
    }
    fun lockVault() { vaultUnlocked = false; vaultItems = emptyList(); pendingVaultFiles = emptyList() }
    private fun loadVault() = task("Opening vault") { check(vaultUnlocked); vaultItems = vault.items() }
    fun protectSelected() {
        pendingVaultFiles = chosen()
        if (pendingVaultFiles.isEmpty()) return
        if (!vaultUnlocked) {
            navigate("Vault")
            notify("Unlock or create your vault to encrypt ${pendingVaultFiles.size} selected file(s).")
        } else task("Encrypting files") { importPendingVaultFiles(); vaultItems = vault.items() }
    }
    private suspend fun importPendingVaultFiles() {
        check(vaultUnlocked) { "Unlock your vault first." }
        val pending = pendingVaultFiles
        pendingVaultFiles = emptyList()
        require(pending.all { it.isFile }) { "Vault accepts individual files. ZIP folders before protecting them." }
        pending.forEach { check(vaultUnlocked) { "Vault locked; select remaining files again." }; vault.store(it) }
        if (pending.isNotEmpty()) { selected = emptySet(); changed("Encrypted files moved into private vault") }
    }
    fun restoreVault(item: VaultItem) = task("Decrypting file") {
        check(vaultUnlocked) { "Unlock vault first." }
        val parent = File(item.original).parentFile?.takeIf { it.isDirectory && it.canWrite() } ?: repository.home
        vault.restore(item, parent); vaultItems = vault.items(); changed("Restored to ${parent.path}")
    }
    fun openEditor(file: File) = task("Reading text") { editorContent = repository.readText(file); editorFile = file }
    fun closeEditor() { editorFile = null; editorContent = "" }
    fun saveEditor(content: String) = task("Saving text") { repository.saveText(requireNotNull(editorFile), content); closeEditor(); changed("Saved") }
    fun importDocument(uri: Uri) = task("Importing cloud document") {
        val context = getApplication<Application>()
        val file = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.takeIf(FileRules::validName) ?: "cloud-document"
            val folder = File(context.filesDir, "cloud_imports").apply { mkdirs() }
            val output = File(folder, "${UUID.randomUUID().toString().replace("-", "").take(12)}-$name")
            check(output.createNewFile()) { "Import name collision; please try again." }
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Provider did not return document content.")
                input.use { source -> output.outputStream().use { destination -> source.copyTo(destination, 64 * 1024) } }
            } catch (e: Exception) { output.delete(); throw e }
            output
        }
        notify("Imported ${file.name.substringAfter('-')}. Available in Cloud → Imported documents.")
    }
    fun cloudImports() {
        val folder = File(getApplication<Application>().filesDir, "cloud_imports")
        folder.mkdirs(); browse(folder)
    }
    private fun changed(message: String) { scanned = false; scan = ScanResult(); categoryCounts = emptyMap(); duplicateGroups = emptyList(); refresh(); notify(message) }
    private fun task(label: String, block: suspend () -> Unit) {
        if (busy) { notify("Wait for the current operation to finish."); return }
        operation = viewModelScope.launch {
            busy = true; status = label
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notify(e.message ?: "Operation failed"); refresh() }
            finally { busy = false; status = "" }
        }
    }
    fun properties(item: FileItem): String {
        val file = item.file
        return "${item.name}\n\nPath: ${item.path}\nSize: ${FileRules.size(item.bytes)} (${item.bytes} bytes; folders exclude contents)\nModified: ${DateFormat.getDateTimeInstance().format(Date(item.modified))}\nReadable: ${file.canRead()}\nWritable: ${file.canWrite()}\nHidden: ${file.isHidden}\nExists: ${file.exists()}\nType: ${if (item.directory) "Folder" else file.extension.ifEmpty { "File" }}"
    }
}
