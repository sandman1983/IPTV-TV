package com.example.iptvtv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale

private val GRAY = Color(0xFFB0B8C8)
private val CYAN = Color(0xFF1EB6E8)
private val BLUE = Color(0xFF8AB4F8)

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

@Composable
fun Section(t: String) {
    Text(t, color = CYAN, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

// ---------------------------------------------------------------------------
// Panneau latéral ouvert avec OK pendant la lecture
// ---------------------------------------------------------------------------
@Composable
fun ChannelPanel(
    list: List<Channel>, current: Channel, epg: Epg, nowMs: Long,
    onPick: (Channel) -> Unit
) {
    var focused by remember { mutableStateOf(current) }
    var upIdx by remember { mutableStateOf(-1) }
    val fr = remember { FocusRequester() }
    val idx = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 3).coerceAtLeast(0))
    val fp = epg.now[focused.epgId]
    val ups = epg.next[focused.epgId] ?: emptyList()
    val shownP: Prog? = if (upIdx in ups.indices) ups[upIdx] else fp
    LaunchedEffect(Unit) {
        delay(150)
        try { fr.requestFocus() } catch (ex: Exception) { }
    }
    BackHandler(enabled = upIdx >= 0) { upIdx = -1 }
    Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
            val rc = ev.nativeKeyEvent.repeatCount
            if (ev.type != KeyEventType.KeyDown) {
                false
            } else if (upIdx >= 0) {
                if (rc > 0) {
                    true
                } else {
                    when (ev.key) {
                        Key.DirectionRight -> {
                            if (upIdx < ups.size - 1) upIdx++
                            true
                        }
                        Key.DirectionLeft -> {
                            upIdx--
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            upIdx = -1
                            false
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
                        else -> false
                    }
                }
            } else if (ev.key == Key.DirectionRight && rc == 1 && ups.isNotEmpty()) {
                upIdx = 0
                true
            } else {
                false
            }
        }
    ) {
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
                        { focused = c; upIdx = -1 }
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
        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xB3000000)).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    focused.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                Text(hm(nowMs), color = GRAY, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (shownP == null) {
                    Text("Pas d'informations de guide", color = GRAY, fontSize = 18.sp)
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${hm(shownP.start)} - ${hm(shownP.stop)}" + if (upIdx >= 0) "   (à suivre)" else "",
                                color = GRAY, fontSize = 16.sp
                            )
                            Text(
                                shownP.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                            if (upIdx < 0) Bar(fracOf(shownP, nowMs), Modifier.padding(vertical = 8.dp).fillMaxWidth())
                            if (shownP.sub.isNotBlank() && shownP.sub != shownP.desc) {
                                Text(shownP.sub, color = CYAN, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                shownP.desc.ifBlank { shownP.sub }, color = Color(0xFFE0E4EC), fontSize = 16.sp,
                                maxLines = 14, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            if (shownP.cat.isNotBlank()) {
                                Text(shownP.cat, color = GRAY, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        if (shownP.icon.isNotBlank()) {
                            AsyncImage(
                                model = shownP.icon, contentDescription = null,
                                modifier = Modifier.padding(start = 16.dp).width(180.dp).height(101.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            if (ups.isNotEmpty()) {
                Text("Prochaines émissions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Maintiens la touche droite pour les parcourir", color = GRAY, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ups.take(5).forEachIndexed { i, u ->
                        val sel = i == upIdx
                        Column(
                            Modifier.width(90.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Color.White else Color.Transparent).padding(4.dp)
                        ) {
                            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1B2230))) {
                                if (u.icon.isNotBlank()) {
                                    AsyncImage(
                                        model = u.icon, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Text(
                                hm(u.start), color = if (sel) Color.Black else Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                u.title, color = if (sel) Color(0xFF222222) else GRAY, fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Audio et sous-titres
// ---------------------------------------------------------------------------
fun trackLabel(f: Format, n: Int): String {
    val lang = f.language
    val ln = if (lang != null && lang != "und") Locale(lang).displayLanguage else ""
    val parts = listOf(f.label ?: "", ln).filter { it.isNotBlank() }.distinct()
    val base = if (parts.isEmpty()) "Piste $n" else parts.joinToString(" · ")
    return if (f.channelCount > 0) "$base (${f.channelCount} can.)" else base
}

@OptIn(UnstableApi::class)
fun resetTracks(exo: ExoPlayer) {
    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
        .clearOverrides()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .build()
}

@OptIn(UnstableApi::class)
@Composable
fun TracksDialog(tracks: Tracks, exo: ExoPlayer, onClose: () -> Unit) {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOn = text.any { it.isSelected }
    fun pick(g: Tracks.Group) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(g.type, false)
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
        onClose()
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Fermer") } },
        title = { Text("Audio et sous-titres") },
        text = {
            LazyColumn(Modifier.height(340.dp)) {
                item { Text("Audio", color = CYAN, fontWeight = FontWeight.Bold) }
                if (audio.isEmpty()) {
                    item { Text("Une seule piste audio", color = GRAY, fontSize = 14.sp) }
                }
                itemsIndexed(audio) { i, g ->
                    MenuBtn((if (g.isSelected) "● " else "") + trackLabel(g.getTrackFormat(0), i + 1)) { pick(g) }
                }
                item {
                    Text(
                        "Sous-titres", color = CYAN, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item {
                    MenuBtn((if (!textOn) "● " else "") + "Désactivés") {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onClose()
                    }
                }
                itemsIndexed(text) { i, g ->
                    MenuBtn((if (g.isSelected) "● " else "") + trackLabel(g.getTrackFormat(0), i + 1)) { pick(g) }
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Boîtes de dialogue
// ---------------------------------------------------------------------------
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

// Moteur de recherche dans les chaînes du guide XMLTV pour choisir un ID EPG.
@Composable
fun EpgPickDialog(
    guide: List<GuideCh>, initial: String,
    onPick: (String) -> Unit, onManual: () -> Unit, onCancel: () -> Unit
) {
    var q by remember { mutableStateOf(initial) }
    val res = remember(q, guide) {
        val t = q.trim().lowercase()
        if (t.isEmpty()) guide.take(40)
        else guide.filter { it.name.lowercase().contains(t) || it.id.lowercase().contains(t) }.take(40)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Chercher dans le guide") },
        confirmButton = { TextButton(onClick = onManual) { Text("Saisir l'ID") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Nom ou ID") })
                if (guide.isEmpty()) {
                    Text("Aucun guide chargé : ajoute un guide XMLTV dans Sources et guides.", color = GRAY, fontSize = 14.sp)
                }
                LazyColumn(Modifier.height(260.dp)) {
                    items(res) { g ->
                        MenuBtn(if (g.name.isBlank()) g.id else "${g.name}  [${g.id}]") { onPick(g.id) }
                    }
                }
            }
        }
    )
}

// Options d'une chaîne : renommer, logo, ID EPG, catégorie, ordre, masquer.
@Composable
fun ChannelEditor(
    id: Int, all: List<Channel>, cfg: Cfg, guide: List<GuideCh>,
    onCfg: (Cfg) -> Unit, onClose: () -> Unit
) {
    val cats = remember(all, cfg) { (all.map { it.cat } + cfg.cats).distinct() }
    var mode by remember { mutableStateOf("menu") }
    val e = all.firstOrNull { it.id == id } ?: return

    fun upd(f: (Ov) -> Ov) {
        onCfg(cfg.copy(ov = cfg.ov + (id to f(cfg.ov[id] ?: Ov()))))
    }

    if (mode == "menu") {
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = { TextButton(onClick = onClose) { Text("Fermer") } },
            title = { Text(e.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MenuBtn("Renommer") { mode = "rename" }
                    MenuBtn("Changer le logo (URL)") { mode = "logo" }
                    MenuBtn("Changer l'ID EPG (recherche)") { mode = "epg" }
                    MenuBtn("Changer de catégorie") { mode = "cat" }
                    MenuBtn("Monter") { onCfg(moveCh(cfg, all, e.id, -1)) }
                    MenuBtn("Descendre") { onCfg(moveCh(cfg, all, e.id, 1)) }
                    MenuBtn(if (e.hidden) "Afficher" else "Masquer") { upd { o -> o.copy(hidden = !e.hidden) } }
                    MenuBtn("Réinitialiser cette chaîne") { onCfg(cfg.copy(ov = cfg.ov - e.id)) }
                }
            }
        )
    }
    if (mode == "rename") {
        InputDialog("Nom de la chaîne", e.name, { v -> upd { o -> o.copy(name = v) }; mode = "menu" }, { mode = "menu" })
    }
    if (mode == "logo") {
        InputDialog("URL du logo / picon", e.icon, { v -> upd { o -> o.copy(logo = v) }; mode = "menu" }, { mode = "menu" })
    }
    if (mode == "epg") {
        EpgPickDialog(
            guide, e.name,
            { v -> upd { o -> o.copy(epg = v) }; mode = "menu" },
            { mode = "epgtxt" },
            { mode = "menu" }
        )
    }
    if (mode == "epgtxt") {
        InputDialog("ID EPG (identifiant XMLTV)", e.epgId, { v -> upd { o -> o.copy(epg = v) }; mode = "menu" }, { mode = "menu" })
    }
    if (mode == "cat") {
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
                    val g = cfg.copy(cats = (cfg.cats + v).distinct())
                    onCfg(g.copy(ov = g.ov + (id to (g.ov[id] ?: Ov()).copy(cat = v))))
                }
                mode = "menu"
            },
            { mode = "menu" }
        )
    }
}

// ---------------------------------------------------------------------------
// Réglages des chaînes (par catégorie)
// ---------------------------------------------------------------------------
@Composable
fun SettingsScreen(all: List<Channel>, cfg: Cfg, guide: List<GuideCh>, onCfg: (Cfg) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val cats = remember(all, cfg) { (all.map { it.cat } + cfg.cats).distinct() }
    var sel by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<Int?>(null) }
    var newCat by remember { mutableStateOf(false) }
    LaunchedEffect(cats) { if (sel !in cats) sel = cats.firstOrNull() ?: "" }

    Row(Modifier.fillMaxSize().padding(24.dp)) {
        LazyColumn(Modifier.width(320.dp).fillMaxHeight()) {
            item {
                Text(
                    "Réglages", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item {
                FocusRow({ newCat = true }) { Text("+ Nouvelle catégorie", color = BLUE, fontSize = 16.sp) }
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
                FocusRow({ editId = c.id }) {
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
    val ed = editId
    if (ed != null) ChannelEditor(ed, all, cfg, guide, onCfg) { editId = null }
    if (newCat) {
        InputDialog(
            "Nom de la nouvelle catégorie", "",
            { v ->
                if (v.isNotBlank()) {
                    onCfg(cfg.copy(cats = (cfg.cats + v).distinct()))
                    sel = v
                }
                newCat = false
            },
            { newCat = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Sources et guides
// ---------------------------------------------------------------------------
@Composable
fun SourceForm(initial: Source?, canCancel: Boolean, onSave: (Source) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(initial?.type ?: "xtream") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var h by remember { mutableStateOf(initial?.host ?: "") }
    var u by remember { mutableStateOf(initial?.user ?: "") }
    var w by remember { mutableStateOf(initial?.pass ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var epg by remember { mutableStateOf(initial?.epg ?: "") }
    var msg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ex: Exception) { }
            url = uri.toString()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (initial == null) "Ajouter une source" else "Modifier la source",
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
        OutlinedTextField(name, { name = it }, label = { Text("Nom (optionnel)") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { type = "xtream" }) { Text(if (type == "xtream") "● Xtream Codes" else "Xtream Codes") }
            Button(onClick = { type = "m3u" }) { Text(if (type == "m3u") "● Liste M3U / M3U8" else "Liste M3U / M3U8") }
        }
        if (type == "m3u") {
            OutlinedTextField(url, { url = it }, label = { Text("Lien (URL) ou fichier M3U / M3U8") }, singleLine = true)
            Button(onClick = {
                try {
                    picker.launch(arrayOf("*/*"))
                } catch (ex: Exception) {
                    msg = "Pas d'explorateur de fichiers sur cette TV : utilise un lien (URL)."
                }
            }) { Text("Choisir un fichier local") }
        } else {
            OutlinedTextField(h, { h = it }, label = { Text("Serveur (http://host:port)") }, singleLine = true)
            OutlinedTextField(u, { u = it }, label = { Text("Identifiant") }, singleLine = true)
            OutlinedTextField(w, { w = it }, label = { Text("Mot de passe") }, singleLine = true)
        }
        OutlinedTextField(
            epg, { epg = it }, label = { Text("Guide XMLTV de cette source (optionnel, .xml ou .gz)") },
            singleLine = true
        )
        if (msg.isNotBlank()) Text(msg, color = Color(0xFFFFB4A9), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val id = initial?.id ?: "s${System.currentTimeMillis()}"
                if (type == "m3u") {
                    val t = url.trim()
                    if (t.isEmpty()) {
                        msg = "Renseigne un lien ou choisis un fichier."
                    } else {
                        val src = if (t.startsWith("http") || t.startsWith("content://") || t.startsWith("file://")) t else "http://$t"
                        onSave(Source(id, name.trim().ifBlank { "Liste M3U" }, "m3u", url = src, epg = epg.trim()))
                    }
                } else {
                    if (h.isBlank()) {
                        msg = "Renseigne l'adresse du serveur."
                    } else {
                        val host = norm(h)
                        onSave(Source(id, name.trim().ifBlank { host }, "xtream", host, u.trim(), w.trim(), epg = epg.trim()))
                    }
                }
            }) { Text("Enregistrer") }
            if (canCancel) Button(onClick = onCancel) { Text("Annuler") }
        }
    }
}

@Composable
fun SourcesScreen(
    sources: List<Source>, cfg: Cfg, all: List<Channel>, guide: List<GuideCh>,
    onAdd: () -> Unit, onEdit: (Source) -> Unit, onDelete: (Source) -> Unit,
    onCfg: (Cfg) -> Unit, onBack: () -> Unit
) {
    BackHandler { onBack() }
    var menu by remember { mutableStateOf<Source?>(null) }
    var addEpg by remember { mutableStateOf(false) }
    var delEpg by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(32.dp)) {
        item {
            Text("Sources et guides", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        item { Section("Sources de chaînes") }
        items(sources, key = { it.id }) { s ->
            FocusRow({ menu = s }) {
                Column {
                    Text(s.name, color = Color.White, fontSize = 18.sp, maxLines = 1)
                    Text(
                        (if (s.type == "m3u") "M3U : " + s.url else "Xtream : " + s.host),
                        color = GRAY, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item { FocusRow({ onAdd() }) { Text("+ Ajouter une source", color = BLUE, fontSize = 16.sp) } }
        item { Section("Guides XMLTV (EPG)") }
        item {
            Text(
                "Le guide du haut a la priorité. Chaque source peut aussi avoir son guide.",
                color = GRAY, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(cfg.epgs) { u ->
            FocusRow({ delEpg = u }) { Text(u, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        item { FocusRow({ addEpg = true }) { Text("+ Ajouter un guide XMLTV", color = BLUE, fontSize = 16.sp) } }
        item { Section("Logos et ID EPG") }
        item {
            FocusRow({ onCfg(cfg.copy(logoPrio = if (cfg.logoPrio == "xmltv") "playlist" else "xmltv")) }) {
                Text(
                    "Priorité des logos : " +
                        if (cfg.logoPrio == "xmltv") "1) guide XMLTV  2) liste" else "1) liste  2) guide XMLTV",
                    color = Color.White, fontSize = 16.sp
                )
            }
        }
        item {
            FocusRow({
                if (guide.isEmpty()) {
                    info = "Aucun guide chargé pour le moment."
                } else {
                    val r = autoMatch(all, cfg, guide)
                    onCfg(r.first)
                    info = "${r.second} chaîne(s) associée(s) au guide."
                }
            }) { Text("Associer automatiquement les ID EPG", color = Color.White, fontSize = 16.sp) }
        }
        if (info.isNotBlank()) {
            item { Text(info, color = CYAN, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }

    val m = menu
    if (m != null) {
        AlertDialog(
            onDismissRequest = { menu = null },
            confirmButton = { TextButton(onClick = { menu = null }) { Text("Fermer") } },
            title = { Text(m.name) },
            text = {
                Column {
                    MenuBtn("Modifier") { menu = null; onEdit(m) }
                    MenuBtn("Supprimer") { menu = null; onDelete(m) }
                }
            }
        )
    }
    if (addEpg) {
        InputDialog(
            "URL du guide XMLTV (.xml ou .gz)", "",
            { v ->
                if (v.isNotBlank()) onCfg(cfg.copy(epgs = (cfg.epgs + norm(v)).distinct()))
                addEpg = false
            },
            { addEpg = false }
        )
    }
    val de = delEpg
    if (de != null) {
        AlertDialog(
            onDismissRequest = { delEpg = null },
            title = { Text("Supprimer ce guide ?") },
            text = { Text(de, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { onCfg(cfg.copy(epgs = cfg.epgs.filter { it != de })); delEpg = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { delEpg = null }) { Text("Annuler") } }
        )
    }
}
