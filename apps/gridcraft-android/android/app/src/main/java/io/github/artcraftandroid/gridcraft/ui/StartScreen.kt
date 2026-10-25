package io.github.artcraftandroid.gridcraft.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.engine.RecentFile
import io.github.artcraftandroid.gridcraft.ui.editor.bottomEdge
import io.github.artcraftandroid.gridcraft.ui.editor.topEdge
import java.util.Calendar

/**
 * The landing page, as the phone app lays it out: the account circle (Settings here), the app's
 * name and Search; Recent and starred workbooks; and Home, New and Open along the bottom.
 */
@Composable
fun StartScreen(vm: AppViewModel, onOpen: () -> Unit) {
    var tab by vm::startTab
    // back on Home next time
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.startTab = "Home" } }
    var list by remember { mutableStateOf("Recent") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(GridColors.SettingsBack).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(GridColors.Avatar).clickable { vm.openSettings() },
                    contentAlignment = Alignment.Center,
                ) { Icon(LucideIcons.UserRound, contentDescription = "Settings", tint = GridColors.OnAvatar, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(14.dp))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = GridColors.OnBar, fontSize = 18.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(GridColors.Accent),
                        modifier = Modifier.weight(1f),
                    )
                    StartIcon(LucideIcons.X) {
                        searching = false
                        query = ""
                    }
                } else {
                    Text("GridCraft", color = GridColors.Accent, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    StartIcon(LucideIcons.Search) {
                        tab = "Home"
                        searching = true
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "Home" -> Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().height(48.dp).bottomEdge(GridColors.BarEdge)) {
                            ListTab(LucideIcons.Clock, "Recent", list == "Recent", Modifier.width(122.dp)) { list = "Recent" }
                            ListTab(LucideIcons.Star, null, list == "Starred", Modifier.width(204.dp)) { list = "Starred" }
                        }
                        val shown = vm.recent.filter { (list == "Recent" || it.starred) && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
                        RecentList(vm, shown, starred = list == "Starred", onOpen)
                    }
                    "New" -> NewList(vm)
                    else -> OpenList(vm, onOpen)
                }
            }
            Row(Modifier.fillMaxWidth().height(72.dp).topEdge(GridColors.BarEdge)) {
                BottomTab("Home", LucideIcons.House, tab == "Home", Modifier.weight(1f)) { tab = "Home" }
                BottomTab("New", LucideIcons.CirclePlus, tab == "New", Modifier.weight(1f)) {
                    searching = false
                    tab = "New"
                }
                BottomTab("Open", LucideIcons.FolderOpen, tab == "Open", Modifier.weight(1f)) {
                    searching = false
                    tab = "Open"
                }
            }
        }
    }
}

@Composable
private fun StartIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = GridColors.OnBar)
    }
}

/** A tab over the list: its icon, its name beside it when it has one, a line under the current one. */
@Composable
private fun ListTab(icon: ImageVector, label: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) GridColors.Accent else GridColors.OnBar
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                Text(label, color = tint, fontSize = 15.sp)
            }
        }
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(GridColors.Accent))
    }
}

/** A tab of the bottom bar: its icon (in a pill when current) over its name. */
@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) GridColors.Accent else GridColors.OnBar
    Column(modifier.fillMaxSize().clickable(onClick = onClick).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) GridColors.NavPill else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** Today, Yesterday, This week, Earlier. */
private fun groupOf(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val days = ((now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis - then.timeInMillis) / DateUtils.DAY_IN_MILLIS).toInt()
    return when {
        then.timeInMillis >= now.timeInMillis -> "Today"
        days < 1 -> "Yesterday"
        days < 7 -> "This week"
        else -> "Earlier"
    }
}

@Composable
private fun RecentList(vm: AppViewModel, files: List<RecentFile>, starred: Boolean, onOpen: () -> Unit) {
    if (files.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(if (starred) "Workbooks you star show here." else "Workbooks you open show here.", color = GridColors.OnSurface, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Open a workbook",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(GridColors.Accent).clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        return
    }
    val groups = files.groupBy { groupOf(it.opened) }
    LazyColumn(Modifier.fillMaxSize()) {
        for (g in listOf("Today", "Yesterday", "This week", "Earlier")) {
            val list = groups[g] ?: continue
            item(key = "h-$g") {
                Text(g, color = GridColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(list, key = { it.uri }) { r -> RecentRow(vm, r) }
        }
    }
}

@Composable
private fun RecentRow(vm: AppViewModel, r: RecentFile) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(64.dp).clickable { vm.openRecent(r) }.bottomEdge(GridColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(LucideIcons.Table, contentDescription = null, tint = GridColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name.substringBeforeLast('.', r.name), color = GridColors.OnPill, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.starred) {
                    Spacer(Modifier.width(6.dp))
                    Icon(LucideIcons.Star, contentDescription = "Starred", tint = GridColors.Accent, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                r.place,
                color = GridColors.OnSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            StartIcon(LucideIcons.EllipsisVertical) { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (r.starred) "Remove Star" else "Add Star") }, onClick = {
                    menu = false
                    vm.starRecent(r.uri, !r.starred)
                })
                DropdownMenuItem(text = { Text("Remove from list") }, onClick = {
                    menu = false
                    vm.removeRecent(r.uri)
                })
            }
        }
    }
}

@Composable
private fun OpenList(vm: AppViewModel, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OpenRow(LucideIcons.FolderOpen, "Browse", "Workbooks on this phone and in your file apps", onClick = onOpen)
    }
}

@Composable
private fun OpenRow(icon: ImageVector, title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).clickable(enabled = enabled, onClick = onClick).bottomEdge(GridColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(icon, contentDescription = null, tint = GridColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = GridColors.OnPill, fontSize = 16.sp)
            Text(detail, color = GridColors.OnSurface, fontSize = 13.sp)
        }
    }
}

/** New: a blank workbook or one of GridCraft's own samples. */
@Composable
private fun NewList(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        for ((label, sample) in listOf("Blank workbook" to "", "Household Budget" to "budget", "Quarterly Sales" to "sales", "Gradebook" to "grades")) {
            OpenRow(if (sample.isEmpty()) LucideIcons.FilePlus2 else LucideIcons.Table, label, if (sample.isEmpty()) "An empty sheet" else "A GridCraft sample", enabled = vm.engine != null) {
                vm.newWorkbook(sample)
            }
        }
    }
}
