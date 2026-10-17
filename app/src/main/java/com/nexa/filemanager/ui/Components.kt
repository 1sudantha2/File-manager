package com.nexa.filemanager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.filemanager.data.FileItem
import com.nexa.filemanager.data.FileRules
import com.nexa.filemanager.data.StorageInfo

@Composable
fun GlassCard(modifier: Modifier = Modifier, accent: Color = Violet, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(Brush.linearGradient(listOf(accent.copy(alpha = .10f), MaterialTheme.colorScheme.surface)))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .then(Modifier.background(Color.Transparent)).padding(20.dp), content = content)
}

@Composable
fun SectionLabel(title: String, detail: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
            modifier = if (action != null) Modifier.clickable(onClick = action).padding(8.dp) else Modifier)
    }
}

@Composable
fun StorageCard(info: StorageInfo, onClick: () -> Unit) {
    val fraction = if (info.total == 0L) 0f else (info.used.toFloat() / info.total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(fraction, tween(1200, easing = FastOutSlowInEasing), label = "storage")
    val track = MaterialTheme.colorScheme.outline.copy(alpha = .5f)
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(Violet, Cyan, Pink, Violet)), -90f, progress * 360, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("USED", fontSize = 9.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(info.label.uppercase(), letterSpacing = 1.4.sp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("${FileRules.size(info.free)} free", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("of ${FileRules.size(info.total)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Cyan, CircleShape)); Spacer(Modifier.width(6.dp))
                    Text("Explore storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

fun categoryIcon(category: String): ImageVector = when (category) {
    "Images" -> Icons.Rounded.Image
    "Videos" -> Icons.Rounded.PlayCircle
    "Audio" -> Icons.Rounded.Headphones
    "Documents" -> Icons.Rounded.Description
    "APKs" -> Icons.Rounded.Android
    "Archives" -> Icons.Rounded.FolderZip
    "Downloads" -> Icons.Rounded.Download
    else -> Icons.Rounded.Folder
}
fun categoryColor(category: String): Color = when (category) {
    "Images" -> Violet
    "Videos" -> Pink
    "Audio" -> Cyan
    "Documents" -> Amber
    "APKs" -> Mint
    else -> Violet
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector = Icons.Rounded.FolderOpen, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileRow(item: FileItem, selected: Boolean, favorite: Boolean, onClick: () -> Unit, onSelect: () -> Unit, onMenu: () -> Unit, readOnly: Boolean = false) {
    val color = if (item.directory) Amber else categoryColor(FileRules.category(item.file))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent)
        .then(Modifier.combinedFileClick(onClick, onSelect)).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(color.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(if (selected) Icons.Rounded.CheckCircle else if (item.directory) Icons.Rounded.Folder else categoryIcon(FileRules.category(item.file)), null, tint = color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(if (item.directory) "Folder${if (readOnly) " · read-only browser" else ""}" else FileRules.size(item.bytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (favorite) Icon(Icons.Rounded.Star, "Bookmarked", Modifier.size(15.dp), tint = Amber)
        if (!item.readable) Icon(Icons.Rounded.Lock, "Restricted", Modifier.size(16.dp), tint = Pink)
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "File actions", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedFileClick(click: () -> Unit, longClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = click, onLongClick = longClick))
