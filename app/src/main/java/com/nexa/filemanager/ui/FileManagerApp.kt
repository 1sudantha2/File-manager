package com.nexa.filemanager.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.nexa.filemanager.FileViewModel
import com.nexa.filemanager.MainActivity
import com.nexa.filemanager.R
import com.nexa.filemanager.data.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp(vm: FileViewModel) {
    FileManagerTheme(vm.dark) {
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        var menuFile by remember { mutableStateOf<FileItem?>(null) }
        var input by remember { mutableStateOf<String?>(null) }
        var renameFile by remember { mutableStateOf<File?>(null) }
        var confirm by remember { mutableStateOf<String?>(null) }
        var properties by remember { mutableStateOf<FileItem?>(null) }
        var image by remember { mutableStateOf<File?>(null) }
        var deleteTargets by remember { mutableStateOf<List<File>>(emptyList()) }
        var rootInfo by remember { mutableStateOf<String?>(null) }
        val cloudPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importDocument) }
        val lifecycle = LocalLifecycleOwner.current
        var allAccess by remember { mutableStateOf(hasStorageAccess(context)) }
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            allAccess = hasStorageAccess(context); vm.refresh()
            if (!allAccess) vm.notify("Media access is limited. Grant All files access to browse folders and manage files.")
        }
        val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            allAccess = hasStorageAccess(context); vm.refresh()
        }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { allAccess = hasStorageAccess(context); vm.refresh() } }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }
        WatchDirectory(vm)
        DisposableEffect(vm.page, vm.vaultUnlocked) {
            (context as? MainActivity)?.protectScreen(vm.page == "Vault")
            onDispose { (context as? MainActivity)?.protectScreen(false) }
        }
        LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) } }
        BackHandler(enabled = vm.page != "Home" || vm.selected.isNotEmpty() || vm.editorFile != null) { if (!vm.busy) vm.back() }

        fun open(item: FileItem) {
            if (vm.selected.isNotEmpty()) { vm.select(item.file); return }
            if (item.directory) { vm.browse(item.file, vm.page == "Root"); return }
            when (item.file.extension.lowercase()) {
                in FileRules.image -> image = item.file
                in FileRules.text -> vm.openEditor(item.file)
                "zip", "rar", "7z" -> { menuFile = item }
                else -> openExternal(context, item.file, vm::notify)
            }
        }
        fun requestAllFiles() {
            if (Build.VERSION.SDK_INT >= 30) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                try { settings.launch(intent) }
                catch (_: ActivityNotFoundException) { settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else permissions.launch(legacyPermissions())
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.CenterVertically) {
                        val animation = rememberInfiniteTransition(label = "brand")
                        val tilt by animation.animateFloat(-5f, 5f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "vector tilt")
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(46.dp).rotate(tilt))
                        Column(Modifier.weight(1f).padding(start = 2.dp)) {
                            Text("File manager", fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-.7).sp)
                            Text("A LITTLE SPACE. A LOT OF POSSIBILITIES.", fontSize = 7.sp, letterSpacing = .9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = vm::toggleHidden, enabled = !vm.busy) { Icon(if (vm.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (vm.hidden) "Hide hidden files" else "Show hidden files", Modifier.size(20.dp)) }
                        IconButton(onClick = vm::theme) { Icon(if (vm.dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Switch theme", Modifier.size(20.dp)) }
                    }
                    if (vm.busy || vm.scanning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (vm.busy) vm.status else "Indexing shared storage…", Modifier.weight(1f).padding(vertical = 5.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            if (vm.scanning && !vm.busy) TextButton(onClick = vm::cancelScan) { Text("Stop", fontSize = 11.sp) }
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    if (vm.clipboard.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ContentPaste, null, Modifier.size(20.dp))
                            Text("${vm.clipboard.size} ${if (vm.cutting) "to move" else "to copy"}", Modifier.weight(1f).padding(12.dp), fontSize = 12.sp)
                            TextButton(onClick = vm::paste, enabled = vm.page == "Browse" && !vm.busy) { Text(if (vm.cutting) "Move here" else "Paste") }
                            IconButton(onClick = vm::clearClipboard, enabled = !vm.busy) { Icon(Icons.Rounded.Close, "Clear clipboard") }
                        }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        listOf("Home" to Icons.Rounded.GridView, "Browse" to Icons.Rounded.FolderOpen, "Tools" to Icons.Rounded.Tune, "Favorites" to Icons.Rounded.StarOutline, "Vault" to Icons.Rounded.Shield).forEach { (page, icon) ->
                            NavigationBarItem(selected = vm.page == page, onClick = { vm.navigate(page) }, enabled = !vm.busy,
                                icon = { Icon(icon, page, Modifier.size(22.dp)) }, label = { Text(page, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f)))
                        }
                    }
                }
            },
            floatingActionButton = {
                if (vm.page == "Browse" && vm.selected.isEmpty()) SmallFloatingActionButton(onClick = { input = "New folder" }, containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Rounded.CreateNewFolder, "Create folder") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!allAccess) PermissionBanner(onGrant = ::requestAllFiles, onMedia = {
                    permissions.launch(when {
                        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                        else -> legacyPermissions()
                    })
                })
                AnimatedContent(targetState = vm.page, transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 18 }).togetherWith(fadeOut(tween(120)))
                }, label = "pages", modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        "Home" -> HomeScreen(vm)
                        "Tools" -> ToolsScreen(vm)
                        "Vault" -> VaultScreen(vm) { biometric(context, vm) }
                        "Cloud" -> CloudScreen(onPick = { cloudPicker.launch(arrayOf("*/*")) }, onImports = vm::cloudImports)
                        "Trash" -> TrashScreen(vm) { confirm = "Empty trash" }
                        "Apps" -> AppsScreen(vm) { app ->
                            try { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) }
                            catch (_: ActivityNotFoundException) { vm.notify("System uninstaller is unavailable.") }
                        }
                        "Duplicates" -> DuplicatesScreen(vm, onOpen = ::open) { deleteTargets = vm.duplicateCopies(); confirm = "Recycle files" }
                        else -> {
                            Column {
                                FileListHeader(vm, onNewFolder = { input = "New folder" })
                                if (vm.selected.isNotEmpty()) SelectionBar(vm,
                                    onZip = { input = "Create ZIP" },
                                    onDelete = { deleteTargets = vm.selected.map(::File); confirm = "Recycle files" })
                                if (page == "Root") RootShortcuts(vm) { root -> rootInfo = root }
                                if (vm.error != null) EmptyState("Access restricted", vm.error.orEmpty(), Icons.Rounded.Lock, "Try again", vm::refresh)
                                else {
                                    val files = vm.visibleFiles()
                                    if (files.isEmpty()) EmptyState(
                                        if (vm.scanning) "Scanning your files" else if (!vm.scanned && page !in listOf("Browse", "Root", "Favorites")) "Build your file index" else "Nothing here yet",
                                        if (page == "Favorites") "Long-press a file or folder and bookmark it from its menu." else "Hidden files and Android-protected directories may not be shown.",
                                        action = if (page !in listOf("Browse", "Root", "Favorites") && !vm.scanning) "Scan storage" else null,
                                        onAction = vm::startScan)
                                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp)) {
                                        if (page == "Junk") item {
                                            Notice("Review before cleaning. Logs, backups and empty files may be intentional. Files are moved to the recycle bin.")
                                            OutlinedButton(onClick = { deleteTargets = files.map { it.file }; confirm = "Recycle files" }, enabled = files.isNotEmpty() && !vm.busy && !vm.scanning, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                Icon(Icons.Rounded.CleaningServices, null, Modifier.size(18.dp)); Text(" Recycle ${files.size} candidates")
                                            }
                                        }
                                        items(files, key = { it.path }, contentType = { if (it.directory) "directory" else "file" }) { item ->
                                            FileRow(item, item.path in vm.selected, item.path in vm.bookmarks,
                                                onClick = { open(item) }, onSelect = { if (!vm.busy && page != "Root") vm.select(item.file) }, onMenu = { menuFile = item }, readOnly = page == "Root")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        menuFile?.let { item ->
            ModalBottomSheet(onDismissRequest = { menuFile = null }) {
                Column(Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
                    Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(if (item.directory) "Folder" else FileRules.size(item.bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    fun dismiss(action: () -> Unit) { menuFile = null; action() }
                    MenuAction("Properties", Icons.Rounded.Info) { dismiss { properties = item } }
                    MenuAction(if (item.path in vm.bookmarks) "Remove bookmark" else "Bookmark", Icons.Rounded.StarOutline) { dismiss { vm.bookmark(item.file) } }
                    if (vm.page != "Root" && !vm.busy) {
                        MenuAction("Select", Icons.Rounded.CheckCircle) { dismiss { vm.select(item.file) } }
                        MenuAction("Rename", Icons.Rounded.Edit) { dismiss { renameFile = item.file; input = "Rename" } }
                        if (!item.directory && item.file.extension.lowercase() in setOf("zip", "rar", "7z")) MenuAction("Extract here", Icons.Rounded.Unarchive) { dismiss { vm.extract(item.file) } }
                        if (!item.directory) MenuAction("Open with another app", Icons.Rounded.OpenInNew) { dismiss { openExternal(context, item.file, vm::notify) } }
                        MenuAction("Move to recycle bin", Icons.Rounded.DeleteOutline, Pink) { dismiss { deleteTargets = listOf(item.file); confirm = "Recycle files" } }
                    }
                }
            }
        }
        input?.let { title ->
            NameDialog(title, if (title == "Rename") renameFile?.name.orEmpty() else if (title == "Create ZIP") "Archive.zip" else "", onDismiss = { input = null }) { value ->
                when (title) { "New folder" -> vm.createFolder(value); "Rename" -> renameFile?.let { vm.rename(it, value) }; "Create ZIP" -> vm.compress(value) }
                input = null
            }
        }
        confirm?.let { title ->
            AlertDialog(onDismissRequest = { confirm = null }, title = { Text(title) },
                text = { Text(if (title == "Empty trash") "Permanently delete everything in the recycle bin? This cannot be undone." else "Move ${deleteTargets.size} item(s) to private trash? You can restore them from Tools → Recycle bin. Large files will consume app storage.") },
                confirmButton = { TextButton(onClick = { if (title == "Empty trash") vm.emptyTrash() else vm.delete(deleteTargets); confirm = null }) { Text(if (title == "Empty trash") "Delete forever" else "Recycle") } },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
        }
        properties?.let { item ->
            val message by produceState("Reading metadata…", item.path) {
                value = withContext(Dispatchers.IO) { vm.properties(FileItem.of(item.file)) }
            }
            AlertDialog(onDismissRequest = { properties = null }, title = { Text("Properties") }, text = { SelectionContainerText(message) }, confirmButton = { TextButton(onClick = { properties = null }) { Text("Done") } })
        }
        rootInfo?.let { message -> AlertDialog(onDismissRequest = { rootInfo = null }, title = { Text("Read-only system explorer") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { rootInfo = null }) { Text("OK") } }) }
        image?.let { ImagePreview(it) { image = null } }
        vm.editorFile?.let { TextEditor(it, vm.editorContent, vm.busy, vm::closeEditor, vm::saveEditor) }
    }
}

private fun hasStorageAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
private fun legacyPermissions() = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

fun openExternal(context: Context, file: File, report: (String) -> Unit) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open ${file.name}"))
    } catch (_: ActivityNotFoundException) { report("No installed app can open this file.") }
    catch (e: Exception) { report(e.message ?: "Cannot share this protected path.") }
}

private fun biometric(context: Context, vm: FileViewModel) {
    val activity = context as? MainActivity ?: return
    if (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
        vm.notify("No strong biometric is enrolled. Use your vault PIN."); return
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.biometricUnlock() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) vm.notify(errString.toString()) }
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock your private vault")
        .setSubtitle("Authenticate to access encrypted files").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use PIN").build())
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit, onMedia: () -> Unit) {
    Surface(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Give your files room to move", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Text("Allow all-files access to browse and manage shared storage. Android/data remains protected.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Row { TextButton(onClick = onGrant) { Text("Allow file access", fontSize = 12.sp) }; TextButton(onClick = onMedia) { Text("Media permissions", fontSize = 12.sp) } }
        }
    }
}

@Composable
fun Notice(message: String) {
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(14.dp))
}

@Composable
private fun MenuAction(title: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.onSurface, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = color); Text(title, color = color, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val valid = FileRules.validName(name)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }, isError = name.isNotEmpty() && !valid,
            supportingText = { if (!valid) Text("No path separators, leading spaces or special path names.") })
    }, confirmButton = { TextButton(onClick = { onSave(name) }, enabled = valid) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SelectionContainerText(message: String) {
    androidx.compose.foundation.text.selection.SelectionContainer { Text(message, modifier = Modifier.verticalScroll(rememberScrollState()), fontSize = 13.sp) }
}

@Composable
private fun ImagePreview(file: File, close: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = close) { Icon(Icons.Rounded.Close, "Close preview") } }
                bitmap?.let { Image(it.asImageBitmap(), file.name, Modifier.fillMaxWidth().heightIn(max = 520.dp)) }
                    ?: Text("Preview loading or format unsupported. Use Open with for other formats.", Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun TextEditor(file: File, initial: String, busy: Boolean, close: () -> Unit, save: (String) -> Unit) {
    var value by remember(file.path) { mutableStateOf(initial) }
    var discard by remember { mutableStateOf(false) }
    val dismiss = { if (value != initial) discard = true else close() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.Rounded.Close, "Close editor") }
                    Text(file.name, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { save(value) }, enabled = !busy) { Text("Save") }
                }
                OutlinedTextField(value, { if (it.toByteArray().size <= 2 * 1024 * 1024) value = it }, Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), enabled = !busy)
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") }, text = { Text("Your edits haven't been saved.") },
            confirmButton = { TextButton(onClick = close) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    }
}

@Suppress("DEPRECATION")
@Composable
private fun WatchDirectory(vm: FileViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(vm.page, vm.directory.path, owner) {
        val handler = Handler(Looper.getMainLooper())
        val refresh = Runnable { vm.refresh() }
        val watcher = if (vm.page in listOf("Browse", "Root")) object : FileObserver(vm.directory.path,
            FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) {
            override fun onEvent(event: Int, path: String?) {
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, 250)
            }
        } else null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> watcher?.startWatching()
                Lifecycle.Event.ON_STOP -> watcher?.stopWatching()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) watcher?.startWatching()
        onDispose {
            watcher?.stopWatching(); handler.removeCallbacks(refresh)
            owner.lifecycle.removeObserver(observer)
        }
    }
}
