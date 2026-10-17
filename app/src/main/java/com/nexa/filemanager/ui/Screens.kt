package com.nexa.filemanager.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.filemanager.FileViewModel
import com.nexa.filemanager.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: FileViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR DIGITAL SPACE", fontSize = 10.sp, letterSpacing = 2.4.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Less clutter.\nMore clarity.", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp)
                }
                Surface(shape = RoundedCornerShape(50), color = Mint.copy(alpha = .12f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.OfflineBolt, null, Modifier.size(13.dp), tint = Mint)
                        Text("ON DEVICE", Modifier.padding(start = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Mint)
                    }
                }
            }
        }
        items(vm.storage, key = { it.root.path }) { storage -> StorageCard(storage) { vm.browse(storage.root) } }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable { vm.browse(vm.repository.home) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text("Find something in your folders", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
            }
        }
        item { SectionLabel("Your collections", if (vm.scanning) "Indexing…" else if (vm.scanned) "Refresh index ↗" else "Scan storage ↗", vm::startScan) }
        items(listOf(listOf("Images", "Videos", "Audio"), listOf("Documents", "APKs", "Archives"))) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { category ->
                    val color = categoryColor(category)
                    GlassCard(Modifier.weight(1f).heightIn(min = 120.dp), accent = color, onClick = { vm.navigate(category) }) {
                        Icon(categoryIcon(category), null, Modifier.size(27.dp), tint = color)
                        Spacer(Modifier.height(16.dp))
                        Text(category, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        val count = if (vm.scanned) (vm.categoryCounts[category] ?: 0).toString() + " files" else "Tap to explore"
                        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item {
            SectionLabel("A little more organized")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f), accent = Cyan, onClick = { vm.navigate("Downloads") }) {
                    Icon(Icons.Rounded.Download, null, tint = Cyan)
                    Spacer(Modifier.height(12.dp)); Text("Downloads", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("All in one place", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GlassCard(Modifier.weight(1f), accent = Pink, onClick = { vm.navigate("Vault") }) {
                    Icon(Icons.Rounded.Shield, null, tint = Pink)
                    Spacer(Modifier.height(12.dp)); Text("Private vault", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Just for your eyes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = Mint, onClick = { vm.navigate("Junk") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(30.dp), tint = Mint)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Make space for what matters", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Review residual files. Reclaim your space.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = Mint)
                }
            }
        }
        item { Text("PRIVATE BY DESIGN  ·  NO NETWORK PERMISSION", Modifier.fillMaxWidth().padding(vertical = 14.dp), fontSize = 8.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ToolsScreen(vm: FileViewModel) {
    val tools = listOf(
        Triple("Junk", "Review temporary, log and residual files", Icons.Rounded.CleaningServices),
        Triple("Duplicates", "Size → quick hash → full SHA-256 verification", Icons.Rounded.FileCopy),
        Triple("Large files", "Find files larger than 25 MB", Icons.Rounded.DataUsage),
        Triple("Trash", "Restore deleted files or empty the bin", Icons.Rounded.DeleteOutline),
        Triple("Apps", "Installed apps, base APK backup and uninstall", Icons.Rounded.Apps),
        Triple("Root", "Read-only system browser · no root escalation", Icons.Rounded.Terminal),
        Triple("Cloud", "Import documents with Android's secure picker", Icons.Rounded.CloudQueue)
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("A smarter space.", "YOUR TOOLKIT") }
        items(tools, key = { it.first }) { (name, description, icon) ->
            GlassCard(Modifier.fillMaxWidth(), accent = if (name == "Trash") Pink else Violet, onClick = {
                if (name == "Root") vm.browse(File("/"), true) else vm.navigate(name)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(26.dp), tint = if (name == "Trash") Pink else MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(if (name == "Trash") "Recycle bin" else if (name == "Root") "System explorer" else name, fontWeight = FontWeight.SemiBold)
                        Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        }
        item { Notice("Scans run on your device. Android 11+ restricts other apps' private data even with all-files access. System explorer never writes to system partitions.") }
    }
}

@Composable
fun PageTitle(title: String, eyebrow: String) {
    Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Text(eyebrow, letterSpacing = 2.sp, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        Text(title, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun FileListHeader(vm: FileViewModel, onNewFolder: () -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }, enabled = !vm.busy) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(if (vm.page in listOf("Browse", "Root")) vm.directory.name.ifEmpty { "System" } else vm.page,
                Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (vm.page !in listOf("Browse", "Root", "Favorites")) IconButton(onClick = vm::startScan, enabled = !vm.scanning && !vm.busy) { Icon(Icons.Rounded.Refresh, "Refresh file index") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Sort files") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("Name", "Size", "Recent").forEach { label -> DropdownMenuItem(text = { Text(label) }, onClick = { vm.sort = label; sortMenu = false }) }
                }
            }
        }
        if (vm.page in listOf("Browse", "Root")) {
            val parts = vm.directory.absolutePath.split('/').filter { it.isNotEmpty() }
            LazyRow(verticalAlignment = Alignment.CenterVertically, contentPadding = PaddingValues(vertical = 6.dp)) {
                item { Text("/", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { vm.browse(if (vm.page == "Root") File("/") else vm.repository.home, vm.page == "Root") }.padding(8.dp), fontSize = 12.sp) }
                items(parts.size) { i ->
                    val path = File("/" + parts.take(i + 1).joinToString("/"))
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(parts[i], Modifier.clickable { vm.browse(path, vm.page == "Root") }.padding(8.dp), fontSize = 12.sp, color = if (i == parts.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedTextField(value = vm.search, onValueChange = { vm.search = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            placeholder = { Text("Search ${if (vm.page in listOf("Browse", "Root")) "this folder" else vm.page.lowercase()}", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) }, trailingIcon = {
                if (vm.search.isNotEmpty()) IconButton(onClick = { vm.search = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
            }, singleLine = true, shape = RoundedCornerShape(18.dp))
        if (vm.page == "Browse") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNewFolder, enabled = !vm.busy) { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)); Text("New folder", fontSize = 11.sp) }
            TextButton(onClick = vm::refresh, enabled = !vm.busy) { Text("Refresh", fontSize = 11.sp) }
        }
    }
}

@Composable
fun SelectionBar(vm: FileViewModel, onZip: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::clearSelection, enabled = !vm.busy) { Icon(Icons.Rounded.Close, "Clear selection", Modifier.size(18.dp)) }
            Text("${vm.selected.size} selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            TextButton(onClick = vm::selectAll, enabled = !vm.busy) { Text("Select all", fontSize = 11.sp) }
        }
        LazyRow {
            item { ActionChip("Copy", Icons.Rounded.ContentCopy, !vm.busy) { vm.copy(false) } }
            item { ActionChip("Move", Icons.Rounded.DriveFileMove, !vm.busy) { vm.copy(true) } }
            if (vm.page == "Browse") item { ActionChip("ZIP", Icons.Rounded.FolderZip, !vm.busy, onZip) }
            item { ActionChip("Vault", Icons.Rounded.Lock, !vm.busy, vm::protectSelected) }
            item { ActionChip("Recycle", Icons.Rounded.DeleteOutline, !vm.busy, onDelete) }
        }
    }
}
@Composable
private fun ActionChip(label: String, icon: ImageVector, enabled: Boolean, action: () -> Unit) {
    AssistChip(onClick = action, enabled = enabled, label = { Text(label, fontSize = 11.sp) }, leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) }, modifier = Modifier.padding(horizontal = 3.dp))
}

@Composable
fun RootShortcuts(vm: FileViewModel, onInfo: (String) -> Unit) {
    val roots = listOf("/", "/system", "/vendor", "/data", "/proc")
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(roots) { root ->
            val info by produceState("Checking…", root) {
                value = withContext(Dispatchers.IO) {
                    val file = File(root)
                    "${if (file.canRead()) "Readable" else "Restricted"} · ${file.list()?.size?.toString() ?: "?"} items"
                }
            }
            Column {
                AssistChip(onClick = { vm.browse(File(root), true) }, label = { Text(root) }, leadingIcon = { Icon(Icons.Rounded.Terminal, null, Modifier.size(14.dp)) })
                Text(info, Modifier.clickable { onInfo("$root\n$info\n\nCounts reflect entries visible to this app's Linux user. No su/root access is requested.") }.padding(bottom = 8.dp), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VaultScreen(vm: FileViewModel, biometric: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Only you. Always.", "PRIVATE VAULT") }
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = Pink) {
                Icon(Icons.Rounded.EnhancedEncryption, null, Modifier.size(40.dp), tint = Pink)
                Spacer(Modifier.height(14.dp))
                Text(if (vm.vaultUnlocked) "Your private space is open" else if (vm.vaultConfigured) "Welcome back" else "Create your private space", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("AES-256-GCM encryption · Android Keystore\nLocks when the app goes into the background.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                if (!vm.vaultUnlocked) {
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text(if (vm.vaultConfigured) "Vault PIN" else "Create PIN (6–12 digits)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                    if (!vm.vaultConfigured) OutlinedTextField(confirmation, { confirmation = it.filter(Char::isDigit).take(12) }, label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Button(onClick = {
                        if (vm.vaultConfigured) vm.unlock(pin) else vm.setupPin(pin, confirmation)
                        pin = ""; confirmation = ""
                    }, enabled = !vm.busy && pin.length >= 6 && (vm.vaultConfigured || confirmation.length >= 6), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (vm.vaultConfigured) "Unlock vault" else "Create vault") }
                    if (vm.vaultConfigured) OutlinedButton(onClick = biometric, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Use biometrics") }
                } else TextButton(onClick = vm::lockVault, enabled = !vm.busy) { Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp)); Text(" Lock vault") }
            }
        }
        if (vm.vaultUnlocked) {
            item { Notice("To add files, select them in Browse and tap Vault. Restoring returns a file to its original folder (or internal storage if unavailable). Existing files are never overwritten.") }
            if (vm.vaultItems.isEmpty()) item { EmptyState("A safe place, ready for you", "Select files in Browse → Vault to encrypt and move them here.", Icons.Rounded.Shield) }
            items(vm.vaultItems, key = { it.id }) { item ->
                ListItem(headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(FileRules.size(item.size)) }, leadingContent = { Icon(Icons.Rounded.Lock, null, tint = Pink) }, trailingContent = { TextButton(onClick = { vm.restoreVault(item) }, enabled = !vm.busy) { Text("Restore") } }, modifier = Modifier.clip(RoundedCornerShape(18.dp)))
            }
        } else item { Notice("Remember your PIN. There is no reset backdoor. Uninstalling or clearing this app's data permanently destroys the vault and its encryption key. Keep separate backups of important files.") }
    }
}

@Composable
fun TrashScreen(vm: FileViewModel, empty: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageTitle("A second chance.", "RECYCLE BIN") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.trash.size} items", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = empty, enabled = vm.trash.isNotEmpty() && !vm.busy) { Text("Empty trash", color = Pink) }
            }
        }
        item { Notice("Trash lives in app-private storage. Uninstalling the app or clearing its data removes trash permanently. Android-protected files cannot be recycled.") }
        if (vm.trash.isEmpty()) item { EmptyState("All clear", "Files you recycle will appear here until restored or permanently removed.", Icons.Rounded.DeleteOutline) }
        items(vm.trash, key = { it.id }) { item ->
            GlassCard(Modifier.fillMaxWidth(), accent = Pink) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                Text(item.original, fontSize = 10.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormat.getDateTimeInstance().format(Date(item.deletedAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.restore(item) }, enabled = !vm.busy) { Icon(Icons.Rounded.Restore, null, Modifier.size(16.dp)); Text(" Restore") }
            }
        }
    }
}

@Composable
fun DuplicatesScreen(vm: FileViewModel, onOpen: (FileItem) -> Unit, recycle: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageTitle("One is enough.", "DUPLICATE FINDER") }
        item { Notice("Candidates are grouped by byte size and a 64 KB partial hash, then the entire file is verified with SHA-256. The first copy in each group is kept. Review every path before recycling copies.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::startScan, enabled = !vm.busy && !vm.scanning) { Text("Rescan") }
                Button(onClick = recycle, enabled = vm.duplicateGroups.isNotEmpty() && !vm.busy && vm.scanned) { Text("Recycle extra copies") }
            }
        }
        if (vm.duplicateGroups.isEmpty()) item { EmptyState(if (vm.scanning || vm.busy) "Comparing your files" else "No duplicates found", "Scan shared storage to compare accessible files.", Icons.Rounded.FileCopy) }
        vm.duplicateGroups.forEachIndexed { i, group ->
            item(key = "group-$i") { SectionLabel("Group ${i + 1}", "${group.size} × ${FileRules.size(group.first().bytes)}") }
            items(group, key = { it.path }) { file ->
                Column {
                    FileRow(file, false, false, { onOpen(file) }, {}, { onOpen(file) })
                    Text(file.path + if (file == group.first()) " · KEEP" else " · EXTRA COPY", Modifier.padding(horizontal = 12.dp), fontSize = 9.sp, color = if (file == group.first()) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AppsScreen(vm: FileViewModel, uninstall: (InstalledApp) -> Unit) {
    var showSystem by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<InstalledApp?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Apps, in perspective.", "APP MANAGER") }
        item { OutlinedTextField(search, { search = it }, label = { Text("Find an app") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Include system apps", Modifier.weight(1f)); Switch(showSystem, { showSystem = it }) } }
        item { Notice("Sizes include base and split APKs, not private app data. Backups contain the base APK only; split apps may not reinstall from a base APK alone. System apps cannot normally be uninstalled.") }
        items(vm.apps.filter { (showSystem || !it.system) && (it.label.contains(search, true) || it.packageName.contains(search, true)) }, key = { it.packageName }) { app ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Android, null, Modifier.size(32.dp), tint = Mint)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${FileRules.size(app.bytes)}${if (app.splitCount > 0) " · ${app.splitCount} splits" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row {
                    TextButton(onClick = { vm.backup(app) }, enabled = !vm.busy) { Icon(Icons.Rounded.SaveAlt, null, Modifier.size(16.dp)); Text(" Backup APK", fontSize = 12.sp) }
                    TextButton(onClick = { pending = app }, enabled = !app.system && !vm.busy) { Text("Uninstall", color = Pink, fontSize = 12.sp) }
                }
            }
        }
    }
    pending?.let { app -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Uninstall ${app.label}?") }, text = { Text("Android will ask for confirmation. The app's private data may be removed.") },
        confirmButton = { TextButton(onClick = { uninstall(app); pending = null }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
}

@Composable
fun CloudScreen(onPick: () -> Unit, onImports: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Your files, connected.", "CLOUD HUB") }
        item { Notice("Uses Android's Storage Access Framework, not a direct cloud login. Install and sign into your provider's app, then choose it from the system picker's navigation menu. Only providers exposing a DocumentsProvider will appear. Selected files are imported as local copies; editing does not sync changes back.") }
        items(listOf("Google Drive", "Microsoft OneDrive", "Dropbox")) { provider ->
            GlassCard(Modifier.fillMaxWidth(), accent = Cyan, onClick = onPick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudQueue, null, tint = Cyan)
                    Text(provider, Modifier.weight(1f).padding(horizontal = 14.dp), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                }
                Text("Open Android document picker", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { OutlinedButton(onClick = onImports, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DownloadDone, null); Text(" Imported documents") } }
    }
}
