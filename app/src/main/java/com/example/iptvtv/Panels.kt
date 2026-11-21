@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.iptvtv

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Style "panneau latéral" : fond bleu dégradé, barre de sélection blanche
// ---------------------------------------------------------------------------
private val P_TOP = Color(0xFF14224A)
private val P_BOTTOM = Color(0xFF2D6199)
private val P_DARK = Color(0xFF14213D)
private val P_BAR = Color(0xFFFFFFFF)
private val P_SUB = Color(0xFFB0B8C8)
private val P_SUB_DARK = Color(0xFF45557A)

@Composable
fun PanelShell(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    if (backEnabled) BackHandler { onBack() }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.40f).fillMaxHeight().background(Color(0x80000000)))
        Box(Modifier.width(2.dp).fillMaxHeight().background(Color(0xFFD6DEEC)))
        Row(
            Modifier.weight(0.60f).fillMaxHeight().background(Color(0xF5171717))
        ) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 16.dp, top = 28.dp)) {
                if (title.isNotEmpty()) {
                    Text(
                        title, color = Color(0xFF1EB6E8), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) { content() }
            }
            if (actions != null) {
                Column(
                    Modifier.width(150.dp).fillMaxHeight().background(Color(0xF5191919))
                        .padding(start = 8.dp, end = 8.dp, top = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) { actions() }
            }
        }
    }
}

// Fenêtre modale (garde le focus de la télécommande dans le panneau).
@Composable
fun PanelDialogHost(onBack: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        content()
    }
}

@Composable
fun PanelDialog(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    PanelDialogHost(onBack) { PanelShell(title, onBack, false, actions, content) }
}

@Composable
fun PItem(
    title: String,
    sub: String,
    onClick: () -> Unit,
    autoFocus: Boolean = false,
    icon: String = "",
    onLong: (() -> Unit)? = null,
    leading: (@Composable (Boolean) -> Unit)? = null,
    trailing: (@Composable (Boolean) -> Unit)? = null
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val fr = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(120)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp))
            .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
            .background(if (f) Color.White else Color.Transparent)
            .onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { if (onLong != null) okLong(it, fired, onClick, onLong) else false }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading(f)
            Spacer(Modifier.width(14.dp))
        }
        if (icon.isNotBlank()) {
            AsyncImage(
                model = icon, contentDescription = null,
                modifier = Modifier.size(44.dp, 28.dp), contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = if (f) P_DARK else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, softWrap = false, overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (f) Modifier.basicMarquee() else Modifier
            )
            if (sub.isNotBlank()) {
                Text(
                    sub, color = if (f) P_SUB_DARK else P_SUB, fontSize = 12.sp,
                    maxLines = 1, softWrap = false, overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (f) Modifier.basicMarquee() else Modifier
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing(f)
        }
    }
}

@Composable
fun PSwitch(title: String, sub: String, on: Boolean, onToggle: () -> Unit, autoFocus: Boolean = false) {
    PItem(
        title, sub, onToggle, autoFocus = autoFocus,
        trailing = { _ ->
            Box(
                Modifier.size(38.dp, 20.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color(0xFF2F7FC1) else Color(0xFF8A94A8))
            ) {
                Box(
                    Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(2.dp).size(16.dp).clip(CircleShape).background(Color.White)
                )
            }
        }
    )
}

@Composable
fun PRadio(title: String, sub: String, selected: Boolean, onClick: () -> Unit, autoFocus: Boolean = false) {
    PItem(
        title, sub, onClick, autoFocus = autoFocus,
        leading = { f ->
            val c = if (f) P_DARK else Color.White
            Box(Modifier.size(18.dp).border(2.dp, c, CircleShape).padding(4.dp)) {
                if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(c))
            }
        }
    )
}

@Composable
fun PButton(label: String, onClick: () -> Unit, autoFocus: Boolean = false) {
    var f by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(120)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
            .background(if (f) Color.White else Color.Transparent)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (f) P_DARK else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun PHeader(t: String) {
    Text(
        t.uppercase(), color = Color(0xFF9CC2F5), fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun PInfo(text: String, error: Boolean = false) {
    Text(
        text, color = if (error) Color(0xFFFFB4A9) else Color(0xDDFFFFFF), fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
fun PInput(label: String, value: String, onChange: (String) -> Unit, autoFocus: Boolean = false) {
    val fr = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(150)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    OutlinedTextField(
        value, onChange, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
    )
}

// ---------------------------------------------------------------------------
// Pistes audio et sous-titres (lecteur)
// ---------------------------------------------------------------------------
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
    PanelDialog("Audio & subtitles", onClose, actions = { PButton("Close", onClose) }) {
        item { PHeader("Audio") }
        if (audio.isEmpty()) item { PInfo("Only one audio track") }
        itemsIndexed(audio) { i, g ->
            PRadio(trackLabel(g.getTrackFormat(0), i + 1), "", g.isSelected, { pick(g) }, autoFocus = i == 0)
        }
        item { PHeader("Subtitles") }
        item {
            PRadio(
                "Off", "", !textOn,
                {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    onClose()
                },
                autoFocus = audio.isEmpty()
            )
        }
        itemsIndexed(text) { i, g ->
            PRadio(trackLabel(g.getTrackFormat(0), i + 1), "", g.isSelected, { pick(g) })
        }
    }
}

// ---------------------------------------------------------------------------
// Options d'une chaîne : Add to category / Move / Edit channel / Show-Hide
// ---------------------------------------------------------------------------
@Composable
fun ChannelEditor(
    id: Int, all: List<Channel>, cfg: Cfg, guide: List<GuideCh>,
    onCfg: (Cfg) -> Unit, onClose: () -> Unit
) {
    val e = all.firstOrNull { it.id == id } ?: return
    var page by remember { mutableStateOf("menu") }
    var text by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Int>() }
    val cats = (all.map { it.cat } + cfg.cats).distinct()

    fun upd(f: (Ov) -> Ov) {
        onCfg(cfg.copy(ov = cfg.ov + (id to f(cfg.ov[id] ?: Ov()))))
    }
    fun back() {
        if (page == "menu") onClose()
        else if (page in listOf("rename", "logo", "epgtxt", "epg")) page = "edit"
        else page = "menu"
    }
    fun open(p: String, init: String) {
        text = init
        page = p
    }
    // Déplace toutes les chaînes cochées (ou la chaîne courante) d'un cran.
    fun moveSel(dir: Int) {
        val ids = (if (picked.isEmpty()) listOf(e.id) else picked.toList())
        val order = all.map { it.id }.toMutableList()
        val sel = ids.toSet()
        val seq = if (dir < 0) order.indices else order.indices.reversed()
        for (i in seq) {
            if (order[i] !in sel) continue
            val cat = all.first { it.id == order[i] }.cat
            var j = i + dir
            while (j in order.indices && all.first { it.id == order[j] }.cat != cat) j += dir
            if (j !in order.indices || order[j] in sel) continue
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        onCfg(cfg.copy(order = order))
    }
    fun moveEdge(top: Boolean) {
        val sel = (if (picked.isEmpty()) listOf(e.id) else picked.toList()).toSet()
        val cat = e.cat
        val inCat = all.filter { it.cat == cat }.map { it.id }
        val moved = inCat.filter { it in sel }
        val rest = inCat.filter { it !in sel }
        val newCat = if (top) moved + rest else rest + moved
        var k = 0
        val order = all.map { if (it.cat == cat) newCat[k++] else it.id }
        onCfg(cfg.copy(order = order))
    }

    PanelDialogHost({ back() }) {
        when (page) {
            "menu" -> PanelShell(e.name, { back() }, false, actions = { PButton("Close", { onClose() }) }) {
                item { PItem("Add to category", e.cat, { page = "cat" }, autoFocus = true) }
                item { PItem("Move", "Several channels at once", { picked.clear(); picked.add(e.id); page = "move" }) }
                item { PItem("Edit channel", "Name, logo, EPG ID", { page = "edit" }) }
                item { PItem("Show / Hide", "Choose from a list", { page = "vis" }) }
            }
            "edit" -> PanelShell("Edit channel", { back() }, false, actions = { PButton("Back", { page = "menu" }) }) {
                item { PItem("Rename", e.name, { open("rename", e.name) }, autoFocus = true) }
                item { PItem("Change logo (URL)", if (e.icon.isBlank()) "None" else e.icon, { open("logo", e.icon) }, icon = e.icon) }
                item { PItem("Change EPG ID", e.epgId.ifBlank { "None" }, { open("epg", e.name) }) }
                item { PItem("Reset this channel", "", { onCfg(cfg.copy(ov = cfg.ov - e.id)); page = "menu" }) }
            }
            "move" -> {
                val list = all.filter { it.cat == e.cat }
                PanelShell(
                    "Move (${e.cat})", { back() }, false,
                    actions = {
                        PButton("Move up", { moveSel(-1) })
                        PButton("Move down", { moveSel(1) })
                        PButton("To top", { moveEdge(true) })
                        PButton("To bottom", { moveEdge(false) })
                        PButton("Done", { page = "menu" })
                    }
                ) {
                    item { PInfo("OK = tick channels, then use the buttons on the right (Right key).") }
                    items(list, key = { it.id }) { c ->
                        PSwitch(
                            c.name, "", c.id in picked,
                            { if (c.id in picked) picked.remove(c.id) else picked.add(c.id) },
                            autoFocus = c.id == e.id
                        )
                    }
                }
            }
            "vis" -> {
                val list = all.filter { it.cat == e.cat }
                PanelShell(
                    "Show / Hide (${e.cat})", { back() }, false,
                    actions = {
                        PButton("Show all", {
                            var ov = cfg.ov
                            for (c in list) ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(hidden = false))
                            onCfg(cfg.copy(ov = ov))
                        })
                        PButton("Hide all", {
                            var ov = cfg.ov
                            for (c in list) ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(hidden = true))
                            onCfg(cfg.copy(ov = ov))
                        })
                        PButton("Done", { page = "menu" })
                    }
                ) {
                    items(list, key = { it.id }) { c ->
                        PSwitch(
                            c.name, "", !c.hidden,
                            { onCfg(cfg.copy(ov = cfg.ov + (c.id to (cfg.ov[c.id] ?: Ov()).copy(hidden = !c.hidden)))) },
                            autoFocus = c.id == e.id
                        )
                    }
                }
            }
            "rename", "logo", "epgtxt", "newcat" -> {
                val label = when (page) {
                    "rename" -> "Channel name"
                    "logo" -> "Logo / picon URL"
                    "epgtxt" -> "EPG ID (XMLTV identifier)"
                    else -> "New category"
                }
                PanelShell(
                    label, { back() }, false,
                    actions = {
                        PButton("Save", {
                            val v = text.trim()
                            when (page) {
                                "rename" -> upd { o -> o.copy(name = v) }
                                "logo" -> upd { o -> o.copy(logo = v) }
                                "epgtxt" -> upd { o -> o.copy(epg = v) }
                                else -> if (v.isNotBlank()) {
                                    val g = cfg.copy(cats = (cfg.cats + v).distinct())
                                    onCfg(g.copy(ov = g.ov + (id to (g.ov[id] ?: Ov()).copy(cat = v))))
                                }
                            }
                            page = if (page == "newcat") "menu" else "edit"
                        })
                        PButton("Cancel", { back() })
                    }
                ) {
                    item { PInput(label, text, { text = it }, autoFocus = true) }
                }
            }
            "epg" -> {
                val res = remember(text, guide) {
                    val t = text.trim().lowercase()
                    if (t.isEmpty()) guide.take(40)
                    else guide.filter { it.name.lowercase().contains(t) || it.id.lowercase().contains(t) }.take(40)
                }
                PanelShell(
                    "Search the guide", { back() }, false,
                    actions = {
                        PButton("Type ID", { open("epgtxt", e.epgId) })
                        PButton("Back", { page = "edit" })
                    }
                ) {
                    item { PInput("Name or ID", text, { text = it }, autoFocus = true) }
                    if (guide.isEmpty()) {
                        item { PInfo("No guide loaded: add one in Settings > Guides.") }
                    }
                    items(res) { g ->
                        PItem(
                            if (g.name.isBlank()) g.id else g.name, g.id,
                            { upd { o -> o.copy(epg = g.id) }; page = "edit" },
                            icon = g.icon
                        )
                    }
                }
            }
            "cat" -> PanelShell("Add to category", { back() }, false, actions = { PButton("Back", { page = "menu" }) }) {
                item { PItem("New category", "", { open("newcat", "") }, autoFocus = true) }
                items(cats) { cat ->
                    PItem(cat, if (cat == e.cat) "Current category" else "", { upd { o -> o.copy(cat = cat) }; page = "menu" })
                }
            }
            else -> page = "menu"
        }
    }
}

// ---------------------------------------------------------------------------
// Réglages : Sources, Guides, Chaînes, Lecteur, Affichage, À propos
// ---------------------------------------------------------------------------
private val SYNC_OPTIONS = listOf(1, 3, 6, 12, 24, 48)

private fun hoursLabel(h: Int): String = when (h) {
    1 -> "1 heure"
    24 -> "1 jour"
    48 -> "2 jours"
    else -> "$h heures"
}

@Composable
fun SettingsHub(
    startPage: String, sources: List<Source>, cfg: Cfg, all: List<Channel>, guide: List<GuideCh>,
    report: List<String>, lastSync: Long, recs: List<Rec>, onRecAct: (String, String) -> Unit,
    onSources: (List<Source>) -> Unit, onCfg: (Cfg) -> Unit, onReload: () -> Unit,
    canExit: Boolean, onExit: () -> Unit
) {
    val ctx = LocalContext.current
    var page by remember { mutableStateOf(startPage) }
    var msg by remember { mutableStateOf("") }
    var selSrc by remember { mutableStateOf<Source?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf("xtream") }
    var fName by remember { mutableStateOf("") }
    var fHost by remember { mutableStateOf("") }
    var fUser by remember { mutableStateOf("") }
    var fPass by remember { mutableStateOf("") }
    var fUrl by remember { mutableStateOf("") }
    var fEpg by remember { mutableStateOf("") }
    var selCat by remember { mutableStateOf("") }
    var selEpg by remember { mutableStateOf("") }
    var epgIn by remember { mutableStateOf("") }
    var catIn by remember { mutableStateOf("") }
    var cityIn by remember { mutableStateOf("") }
    var editCh by remember { mutableStateOf<Int?>(null) }
    var selRec by remember { mutableStateOf<Rec?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ex: Exception) { }
            fUrl = uri.toString()
        }
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (ex: Exception) { }
            onCfg(cfg.copy(dvrUri = uri.toString()))
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(exportConfig(sources, cfg).toByteArray()) }
                msg = "Configuration exported."
            } catch (ex: Exception) {
                msg = "Export failed : ${ex.message}"
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val t = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val r = importConfig(t)
                if (r == null) {
                    msg = "Invalid file."
                } else {
                    onSources(r.first)
                    onCfg(r.second)
                    msg = "Configuration restored."
                }
            } catch (ex: Exception) {
                msg = "Restore failed : ${ex.message}"
            }
        }
    }

    fun back() {
        msg = ""
        when (page) {
            "" -> onExit()
            "src_menu" -> page = "sources"
            "src_type" -> if (canExit) page = "sources"
            "src_form" -> page = if (editingId != null) "src_menu" else "src_type"
            "guide_menu", "guide_add" -> page = "guides"
            "chlist", "newcat" -> page = "channels"
            "city" -> page = "display"
            "recs" -> page = "dvr"
            "rec_menu" -> page = "recs"
            else -> page = ""
        }
    }
    fun openForm(s: Source?) {
        editingId = s?.id
        if (s != null) type = s.type
        fName = s?.name ?: ""
        fHost = s?.host ?: ""
        fUser = s?.user ?: ""
        fPass = s?.pass ?: ""
        fUrl = s?.url ?: ""
        fEpg = s?.epg ?: ""
        msg = ""
        page = "src_form"
    }
    fun saveForm() {
        val id = editingId ?: "s${System.currentTimeMillis()}"
        val s: Source? = if (type == "m3u") {
            val t = fUrl.trim()
            if (t.isEmpty()) {
                msg = "Renseigne un lien ou choisis un fichier."
                null
            } else {
                val src = if (t.startsWith("http") || t.startsWith("content://") || t.startsWith("file://")) t else "http://$t"
                Source(id, fName.trim().ifBlank { "Playlist M3U" }, "m3u", url = src, epg = fEpg.trim())
            }
        } else {
            if (fHost.isBlank()) {
                msg = "Renseigne l'adresse du serveur."
                null
            } else {
                val host = norm(fHost)
                Source(id, fName.trim().ifBlank { host }, "xtream", host, fUser.trim(), fPass.trim(), epg = fEpg.trim())
            }
        }
        if (s != null) {
            val first = sources.isEmpty()
            onSources(if (editingId != null) sources.map { if (it.id == s.id) s else it } else sources + s)
            msg = ""
            page = if (first) "" else "sources"
        }
    }

    val cats = (all.map { it.cat } + cfg.cats).distinct()

    when (page) {
        "" -> PanelShell("Settings", { back() }) {
            item { PItem("Sources", "${sources.size} source(s) configured", { page = "sources" }, autoFocus = true) }
            item { PItem("Guides (EPG)", "${cfg.epgs.size} guide(s) added", { page = "guides" }) }
            item { PItem("Channels", "Rename, move, hide, categories", { page = "channels" }) }
            item { PItem("Player", "Preview, audio and subtitles", { page = "player" }) }
            item { PItem("DVR", "Recordings, reminders, storage", { page = "dvr" }) }
            item { PItem("Display", "Weather in the info bar", { page = "display" }) }
            item { PItem("About", "", { page = "about" }) }
        }
        "sources" -> PanelShell("Sources", { back() }, actions = { PButton("Done", { back() }) }) {
            item {
                PItem(
                    "Time between synchronizations", hoursLabel(cfg.syncHours) + "  (guide refresh)",
                    { onCfg(cfg.copy(syncHours = SYNC_OPTIONS[(SYNC_OPTIONS.indexOf(cfg.syncHours) + 1) % SYNC_OPTIONS.size])) },
                    autoFocus = true
                )
            }
            item {
                PItem(
                    "Update now",
                    "Last run: " + (if (lastSync > 0) SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH).format(Date(lastSync)) else "—"),
                    { onReload(); msg = "Update started…" }
                )
            }
            item {
                PItem("Add new source", "M3U / M3U8 playlist or Xtream Codes", { editingId = null; type = "xtream"; msg = ""; page = "src_type" })
            }
            items(sources, key = { it.id }) { s ->
                PItem(s.name, if (s.type == "m3u") "M3U · " + s.url else "Xtream · " + s.host, { selSrc = s; page = "src_menu" })
            }
            item {
                PItem(
                    "Export configuration", "File with sources and settings (contains your credentials)",
                    {
                        try { exporter.launch("iptv-config.json") } catch (ex: Exception) { msg = "No file browser on this TV." }
                    }
                )
            }
            item {
                PItem(
                    "Restore configuration", "Reads a previously exported file",
                    {
                        try { importer.launch(arrayOf("*/*")) } catch (ex: Exception) { msg = "No file browser on this TV." }
                    }
                )
            }
            if (msg.isNotBlank()) item { PInfo(msg) }
        }
        "src_menu" -> {
            val s = selSrc
            if (s != null) {
                PanelShell(s.name, { back() }, actions = { PButton("Back", { back() }) }) {
                    item { PItem("Edit", "", { openForm(s) }, autoFocus = true) }
                    item { PItem("Delete this source", "", { onSources(sources.filter { it.id != s.id }); page = "sources" }) }
                }
            }
        }
        "src_type" -> PanelShell(
            "Add new source", { back() },
            actions = {
                PButton("Next", { openForm(null) }, autoFocus = false)
                if (canExit) PButton("Back", { back() })
            }
        ) {
            item { PRadio("Playlist (M3U / M3U8)", "Link or local file", type == "m3u", { type = "m3u" }, autoFocus = true) }
            item { PRadio("Xtream Codes", "Server, username and password", type == "xtream", { type = "xtream" }) }
        }
        "src_form" -> PanelShell(
            if (type == "m3u") "Playlist M3U / M3U8" else "Xtream Codes", { back() },
            actions = {
                PButton("Save", { saveForm() })
                PButton("Back", { back() })
            }
        ) {
            item { PInput("Name (optional)", fName, { fName = it }, autoFocus = true) }
            if (type == "m3u") {
                item { PInput("Link (URL) or M3U / M3U8 file", fUrl, { fUrl = it }) }
                item {
                    PItem(
                        "Choose a local file", "",
                        {
                            try { picker.launch(arrayOf("*/*")) } catch (ex: Exception) { msg = "No file browser on this TV: use a link (URL)." }
                        }
                    )
                }
            } else {
                item { PInput("Server (http://host:port)", fHost, { fHost = it }) }
                item { PInput("Username", fUser, { fUser = it }) }
                item { PInput("Password", fPass, { fPass = it }) }
            }
            item { PInput("XMLTV guide for this source (optional)", fEpg, { fEpg = it }) }
            if (msg.isNotBlank()) item { PInfo(msg, true) }
        }
        "guides" -> PanelShell("Guides (EPG)", { back() }, actions = { PButton("Done", { back() }) }) {
            item { PItem("Add XMLTV guide", "URL of an .xml or .gz file", { epgIn = ""; page = "guide_add" }, autoFocus = true) }
            items(cfg.epgs) { u -> PItem(u, "Top guide has priority · OK: manage", { selEpg = u; page = "guide_menu" }) }
            item {
                PItem(
                    "Logo priority",
                    if (cfg.logoPrio == "xmltv") "1) XMLTV guide   2) playlist" else "1) playlist   2) XMLTV guide",
                    { onCfg(cfg.copy(logoPrio = if (cfg.logoPrio == "xmltv") "playlist" else "xmltv")) }
                )
            }
            item {
                PItem(
                    "Auto-match EPG IDs", "${guide.size} channels in the loaded guide",
                    {
                        if (guide.isEmpty()) {
                            msg = "No guide loaded yet."
                        } else {
                            val r = autoMatch(all, cfg, guide)
                            onCfg(r.first)
                            msg = "${r.second} channel(s) matched to the guide."
                        }
                    }
                )
            }
            if (msg.isNotBlank()) item { PInfo(msg) }
            item { PHeader("Load status") }
            if (report.isEmpty()) item { PInfo("Loading…") }
            items(report) { line -> PInfo(line, line.startsWith("ERROR")) }
        }
        "guide_menu" -> PanelShell("Guide", { back() }, actions = { PButton("Back", { back() }) }) {
            item { PInfo(selEpg) }
            item {
                PItem("Delete this guide", "", { onCfg(cfg.copy(epgs = cfg.epgs.filter { it != selEpg })); page = "guides" }, autoFocus = true)
            }
            item {
                PItem("Move to top (priority)", "", { onCfg(cfg.copy(epgs = listOf(selEpg) + cfg.epgs.filter { it != selEpg })); page = "guides" })
            }
        }
        "guide_add" -> PanelShell(
            "Add XMLTV guide", { back() },
            actions = {
                PButton("Save", {
                    if (epgIn.isNotBlank()) onCfg(cfg.copy(epgs = (cfg.epgs + norm(epgIn)).distinct()))
                    page = "guides"
                })
                PButton("Back", { back() })
            }
        ) {
            item { PInput("Guide URL (.xml or .gz)", epgIn, { epgIn = it }, autoFocus = true) }
        }
        "channels" -> PanelShell("Channels", { back() }, actions = { PButton("Done", { back() }) }) {
            item { PItem("New category", "", { catIn = ""; page = "newcat" }, autoFocus = true) }
            items(cats) { cat -> PItem(cat, "${all.count { it.cat == cat }} channel(s)", { selCat = cat; page = "chlist" }) }
        }
        "newcat" -> PanelShell(
            "New category", { back() },
            actions = {
                PButton("Save", {
                    val v = catIn.trim()
                    if (v.isNotBlank()) {
                        onCfg(cfg.copy(cats = (cfg.cats + v).distinct()))
                        selCat = v
                    }
                    page = "channels"
                })
                PButton("Back", { back() })
            }
        ) {
            item { PInput("Category name", catIn, { catIn = it }, autoFocus = true) }
        }
        "chlist" -> {
            val list = all.filter { it.cat == selCat }
            val firstId = list.firstOrNull()?.id
            PanelShell(selCat, { back() }, actions = { PButton("Back", { back() }) }) {
                if (list.isEmpty()) item { PInfo("No channel in this category.") }
                items(list, key = { it.id }) { c ->
                    PItem(
                        c.name + if (c.hidden) "  (hidden)" else "",
                        "EPG : ${c.epgId.ifBlank { "—" }}",
                        { editCh = c.id },
                        autoFocus = c.id == firstId, icon = c.icon
                    )
                }
            }
        }
        "player" -> PanelShell("Player", { back() }, actions = { PButton("Done", { back() }) }) {
            item {
                PSwitch(
                    "Background preview sound", "On the home screen", cfg.bgSound,
                    { onCfg(cfg.copy(bgSound = !cfg.bgSound)) }, autoFocus = true
                )
            }
            item {
                PInfo("While watching: Up/Down = zap, OK = channel list, Right = up next, Left = audio and subtitles.")
            }
        }
        "dvr" -> PanelShell("DVR", { back() }, actions = { PButton("Done", { back() }) }) {
            item {
                PSwitch(
                    "Enable reminders", "Ability to add reminders", cfg.remOn,
                    { onCfg(cfg.copy(remOn = !cfg.remOn)) }, autoFocus = true
                )
            }
            item {
                PItem(
                    "DVR location",
                    if (cfg.dvrUri.isBlank()) "Internal app storage" else (Uri.parse(cfg.dvrUri).lastPathSegment ?: cfg.dvrUri),
                    {
                        try { treePicker.launch(null) } catch (ex: Exception) { msg = "No folder picker on this TV: internal storage is used." }
                    }
                )
            }
            if (cfg.dvrUri.isNotBlank()) {
                item { PItem("Use internal storage", "Reset the DVR location", { onCfg(cfg.copy(dvrUri = "")) }) }
            }
            item {
                PItem(
                    "Recording start time", if (cfg.recStart == 0) "On time" else "${cfg.recStart} min before",
                    {
                        val o = listOf(0, 1, 2, 5, 10, 15)
                        onCfg(cfg.copy(recStart = o[(o.indexOf(cfg.recStart) + 1) % o.size]))
                    }
                )
            }
            item {
                PItem(
                    "Recording stop time", if (cfg.recStop == 0) "On time" else "${cfg.recStop} min after",
                    {
                        val o = listOf(0, 1, 2, 5, 10, 15, 30)
                        onCfg(cfg.copy(recStop = o[(o.indexOf(cfg.recStop) + 1) % o.size]))
                    }
                )
            }
            item { PItem("Recordings and reminders", "${recs.size} item(s)", { page = "recs" }) }
            item { PInfo("Recording works while the app is running. Keep the TV on and the app open.") }
            if (msg.isNotBlank()) item { PInfo(msg) }
        }
        "recs" -> PanelShell("Recordings and reminders", { back() }, actions = { PButton("Back", { back() }) }) {
            if (recs.isEmpty()) item { PInfo("Nothing scheduled yet. In the guide, select a future programme to record it or set a reminder.") }
            items(recs.sortedByDescending { it.start }, key = { it.id }) { r ->
                val st = when (r.state) {
                    "scheduled" -> "Scheduled"
                    "recording" -> "Recording…"
                    "done" -> if (r.kind == "rec") "Saved" else "Reminder done"
                    else -> "Failed: " + r.note
                }
                PItem(
                    (if (r.kind == "rec") "● " else "⏰ ") + r.title,
                    r.chName + " · " + SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH).format(Date(r.start)) + " · " + st,
                    { selRec = r; page = "rec_menu" }
                )
            }
        }
        "rec_menu" -> {
            val r = recs.firstOrNull { it.id == selRec?.id }
            if (r == null) {
                page = "recs"
            } else {
                PanelShell(r.title, { back() }, actions = { PButton("Back", { back() }) }) {
                    item { PInfo(r.chName + " · " + hm(r.start) + " - " + hm(r.stop)) }
                    if (r.state == "done" && r.kind == "rec" && r.path.isNotBlank()) {
                        item { PItem("Play", "", { onRecAct("play", r.id) }, autoFocus = true) }
                    }
                    if (r.state == "recording") {
                        item { PItem("Stop recording", "", { onRecAct("stop", r.id) }, autoFocus = true) }
                    }
                    if (r.state == "scheduled") {
                        item { PItem(if (r.kind == "rec") "Cancel recording" else "Cancel reminder", "", { onRecAct("cancel", r.id); page = "recs" }, autoFocus = true) }
                    }
                    if (r.state == "done" || r.state == "failed") {
                        item { PItem("Delete", if (r.path.isNotBlank()) "Also deletes the file" else "", { onRecAct("delete", r.id); page = "recs" }, autoFocus = r.state == "failed") }
                    }
                    if (r.state == "failed") item { PInfo(r.note, true) }
                }
            }
        }
        "display" -> PanelShell("Display", { back() }, actions = { PButton("Done", { back() }) }) {
            item {
                PItem(
                    "Weather in the info bar", if (cfg.city.isBlank()) "Off" else cfg.city,
                    { cityIn = cfg.city; page = "city" }, autoFocus = true
                )
            }
            item {
                PSwitch(
                    "Channel numbers", "Show numbers in the channel list", cfg.showNums,
                    { onCfg(cfg.copy(showNums = !cfg.showNums)) }
                )
            }
        }
        "city" -> PanelShell(
            "Weather", { back() },
            actions = {
                PButton("Save", { onCfg(cfg.copy(city = cityIn.trim())); page = "display" })
                PButton("Back", { back() })
            }
        ) {
            item { PInput("City (empty = off)", cityIn, { cityIn = it }, autoFocus = true) }
        }
        else -> PanelShell("About", { back() }, actions = { PButton("Back", { back() }) }) {
            item { PInfo("IPTV TV · version 0.1") }
            item { PInfo("${sources.size} source(s) · ${all.size} channels · ${guide.size} channels in guide") }
            item { PItem("Back", "", { back() }, autoFocus = true) }
        }
    }

    val ec = editCh
    if (ec != null) ChannelEditor(ec, all, cfg, guide, onCfg) { editCh = null }
}
