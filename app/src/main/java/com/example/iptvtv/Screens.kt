package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val GRAY = Color(0xFFB0B8C8)
private val CYAN = Color(0xFF1EB6E8)

@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    var f by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (f) Color(0xFF2A3446) else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun Bar(frac: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).background(Color(0xFF444B58))) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color(0xFF1E88E5)))
    }
}

fun fracOf(p: Prog, nowMs: Long): Float =
    ((nowMs - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1L)).coerceIn(0f, 1f)

// Panneau latéral ouvert avec OK pendant la lecture : chaînes de la catégorie en cours.
@Composable
fun ChannelPanel(
    list: List<Channel>, current: Channel, epg: Epg, nowMs: Long,
    onPick: (Channel) -> Unit
) {
    var focused by remember { mutableStateOf(current) }
    val fr = remember { FocusRequester() }
    val idx = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 3).coerceAtLeast(0))
    LaunchedEffect(Unit) {
        delay(150)
        try { fr.requestFocus() } catch (ex: Exception) { }
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(420.dp).fillMaxHeight().background(Color(0xE6101319))) {
            Text(
                current.cat, color = CYAN, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.padding(16.dp)
            )
            LazyColumn(state = ls, modifier = Modifier.weight(1f)) {
                items(list, key = { it.id }) { c ->
                    val pr = epg.now[c.epgId]
                    FocusRow(
                        { onPick(c) },
                        if (c.id == current.id) Modifier.focusRequester(fr) else Modifier,
                        { focused = c }
                    ) {
                        Box(Modifier.size(64.dp, 40.dp)) {
                            if (c.icon.isNotBlank()) {
                                AsyncImage(
                                    model = c.icon, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(pr?.title ?: "", color = GRAY, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (pr != null) Bar(fracOf(pr, nowMs), Modifier.padding(top = 4.dp).width(200.dp))
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xB3000000)).padding(32.dp)) {
            val fp = epg.now[focused.epgId]
            val ups = epg.next[focused.epgId] ?: emptyList()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    focused.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                Text(hm(nowMs), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            if (fp == null) {
                Text("Pas d'informations de guide", color = GRAY, fontSize = 18.sp)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${hm(fp.start)} - ${hm(fp.stop)}", color = GRAY, fontSize = 20.sp)
                        Text(
                            fp.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Bar(fracOf(fp, nowMs), Modifier.padding(vertical = 8.dp).fillMaxWidth())
                        if (fp.sub.isNotBlank()) {
                            Text(fp.sub, color = GRAY, fontSize = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        if (fp.cat.isNotBlank()) Text(fp.cat, color = CYAN, fontSize = 16.sp, maxLines = 1)
                    }
                    if (fp.icon.isNotBlank()) {
                        AsyncImage(
                            model = fp.icon, contentDescription = null,
                            modifier = Modifier.padding(start = 24.dp).width(280.dp).height(158.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (ups.isNotEmpty()) {
                Text("Prochaines émissions", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (u in ups.take(4)) {
                        Column(Modifier.width(110.dp)) {
                            Box(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1B2230))) {
                                if (u.icon.isNotBlank()) {
                                    AsyncImage(
                                        model = u.icon, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Text(hm(u.start), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(u.title, color = GRAY, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuBtn(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, fontSize = 17.sp) }
}

@Composable
fun InputDialog(title: String, initial: String, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(t.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}

@Composable
fun SettingsScreen(all: List<Channel>, cfg: Cfg, onCfg: (Cfg) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val cats = remember(all, cfg) { (all.map { it.cat } + cfg.cats).distinct() }
    var sel by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<Int?>(null) }
    var mode by remember { mutableStateOf("") }
    LaunchedEffect(cats) { if (sel !in cats) sel = cats.firstOrNull() ?: "" }
    val e = all.firstOrNull { it.id == editId }

    fun upd(f: (Ov) -> Ov) {
        val id = editId ?: return
        onCfg(cfg.copy(ov = cfg.ov + (id to f(cfg.ov[id] ?: Ov()))))
    }

    Row(Modifier.fillMaxSize().padding(24.dp)) {
        LazyColumn(Modifier.width(320.dp).fillMaxHeight()) {
            item {
                Text(
                    "Réglages", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item {
                FocusRow({ editId = null; mode = "newcat" }) {
                    Text("+ Nouvelle catégorie", color = Color(0xFF8AB4F8), fontSize = 16.sp)
                }
            }
            items(cats) { cat ->
                FocusRow({ sel = cat }, Modifier, { sel = cat }) {
                    Text(
                        cat, color = if (cat == sel) CYAN else Color.White, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp)) {
            item {
                Text(
                    "OK sur une chaîne : renommer, déplacer, masquer, logo, ID EPG, catégorie",
                    color = GRAY, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            items(all.filter { it.cat == sel }, key = { it.id }) { c ->
                FocusRow({ editId = c.id; mode = "menu" }) {
                    Box(Modifier.size(56.dp, 36.dp)) {
                        if (c.icon.isNotBlank()) {
                            AsyncImage(
                                model = c.icon, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            c.name + if (c.hidden) "  (masquée)" else "",
                            color = if (c.hidden) GRAY else Color.White, fontSize = 18.sp, maxLines = 1
                        )
                        Text("EPG : ${c.epgId.ifBlank { "—" }}", color = GRAY, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (mode == "menu" && e != null) {
        AlertDialog(
            onDismissRequest = { mode = "" },
            confirmButton = { TextButton(onClick = { mode = "" }) { Text("Fermer") } },
            title = { Text(e.name) },
            text = {
                Column {
                    MenuBtn("Renommer") { mode = "rename" }
                    MenuBtn("Changer le logo (URL)") { mode = "logo" }
                    MenuBtn("Changer l'ID EPG") { mode = "epg" }
                    MenuBtn("Changer de catégorie") { mode = "cat" }
                    MenuBtn("Monter") { onCfg(moveCh(cfg, all, e.id, -1)) }
                    MenuBtn("Descendre") { onCfg(moveCh(cfg, all, e.id, 1)) }
                    MenuBtn(if (e.hidden) "Afficher" else "Masquer") { upd { o -> o.copy(hidden = !e.hidden) } }
                    MenuBtn("Réinitialiser cette chaîne") { onCfg(cfg.copy(ov = cfg.ov - e.id)) }
                }
            }
        )
    }
    if (mode == "rename" && e != null) {
        InputDialog("Nom de la chaîne", e.name, { v -> upd { o -> o.copy(name = v) }; mode = "menu" }, { mode = "menu" })
    }
    if (mode == "logo" && e != null) {
        InputDialog("URL du logo / picon", e.icon, { v -> upd { o -> o.copy(logo = v) }; mode = "menu" }, { mode = "menu" })
    }
    if (mode == "epg" && e != null) {
        InputDialog("ID EPG (identifiant XMLTV)", e.epgId, { v -> upd { o -> o.copy(epg = v) }; mode = "menu" }, { mode = "menu" })
    }
    if (mode == "cat" && e != null) {
        AlertDialog(
            onDismissRequest = { mode = "menu" },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mode = "menu" }) { Text("Annuler") } },
            title = { Text("Catégorie") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    item { MenuBtn("+ Nouvelle catégorie") { mode = "newcat" } }
                    items(cats) { cat -> MenuBtn(cat) { upd { o -> o.copy(cat = cat) }; mode = "menu" } }
                }
            }
        )
    }
    if (mode == "newcat") {
        InputDialog(
            "Nom de la nouvelle catégorie", "",
            { v ->
                if (v.isNotBlank()) {
                    val id = editId
                    var g = cfg.copy(cats = (cfg.cats + v).distinct())
                    if (id != null) g = g.copy(ov = g.ov + (id to (g.ov[id] ?: Ov()).copy(cat = v)))
                    onCfg(g)
                    sel = v
                }
                mode = if (editId != null) "menu" else ""
            },
            { mode = if (editId != null) "menu" else "" }
        )
    }
}
