@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.gestures.animateScrollBy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Style "panneau latéral" : fond bleu dégradé, barre de sélection blanche
// ---------------------------------------------------------------------------
private val P_TOP = Color(0xFF14224A)
private val P_BOTTOM = Color(0xFF2D6199)
private val P_DARK: Color get() = Th.text
private val P_BAR = Color(0xFFFFFFFF)
private val P_SUB: Color get() = Th.sub
private val P_SUB_DARK: Color get() = Th.sub

// Position de la liste du panneau : permet de garder l'élément sélectionné entièrement visible (hors fondu).
class ActScope(val req: FocusRequester) { var used = false }
val LocalActScope = androidx.compose.runtime.compositionLocalOf<ActScope?> { null }
class PanelList(val state: androidx.compose.foundation.lazy.LazyListState) { var top = 0f; var bottom = 0f }
val LocalPanelList = androidx.compose.runtime.compositionLocalOf<PanelList?> { null }

@Composable
fun PanelShell(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    scrollTo: Int = 0,
    content: LazyListScope.() -> Unit
) {
    if (backEnabled) BackHandler { onBack() }
    val lstate = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (scrollTo - 2).coerceAtLeast(0)
    )
    val pl = remember { PanelList(lstate) }
    val act = remember { ActScope(FocusRequester()) }
    CompositionLocalProvider(LocalPanelList provides pl, LocalActScope provides act) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.46f).fillMaxHeight().background(Th.scrim))
        Box(Modifier.width(1.dp).fillMaxHeight().background(Th.divider))
        Row(
            Modifier.weight(0.54f).fillMaxHeight().background(Th.guideBg)
        ) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 16.dp, top = 28.dp)) {
                if (title.isNotEmpty()) {
                    Text(
                        title, color = Th.sel, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth()
                        .onGloballyPositioned { val r = it.boundsInWindow(); pl.top = r.top; pl.bottom = r.bottom }
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Fondu doux en haut / en bas quand la liste défile : plus d'éléments coupés net
                            val h = this.size.height
                            val f = (22.dp.toPx() / h).coerceIn(0.01f, 0.2f)
                            val topF = if (lstate.canScrollBackward) f else 0f
                            val botF = if (lstate.canScrollForward) f else 0f
                            drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to (if (topF > 0f) Color.Transparent else Color.Black),
                                    (if (topF > 0f) topF else 0.001f) to Color.Black,
                                    (1f - (if (botF > 0f) botF else 0.001f)) to Color.Black,
                                    1f to (if (botF > 0f) Color.Transparent else Color.Black)
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                            )
                        },
                    state = lstate,
                    contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp)
                ) { content() }
            }
            if (actions != null) {
                Column(
                    Modifier.width(130.dp).fillMaxHeight().background(if (Th.light) Color(0x1A000000) else Color(0xB3000000))
                        .focusProperties { enter = { act.req } }
                        .focusGroup()
                        .padding(start = 8.dp, end = 8.dp, top = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) { actions() }
            }
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
    DarkZone()
    PanelDialogHost(onBack) { PanelShell(title, onBack, false, actions, 0, content) }
}

// Mémoire du dernier choix par page des réglages : au retour, le focus revient sur l'entrée choisie.
class PMem(val page: () -> String) { val last = HashMap<String, String>() }
val LocalPMem = androidx.compose.runtime.compositionLocalOf<PMem?> { null }

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
    val pl = LocalPanelList.current
    val scope = rememberCoroutineScope()
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val co = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val mem = LocalPMem.current
    val saved = mem?.last?.get(mem.page())
    val autoFocus = if (mem != null && saved != null) saved == title else autoFocus
    val onClick = if (mem != null) ({ mem.last[mem.page()] = title; onClick() }) else onClick
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(120)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(9.dp))
            .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
            .background(Th.cell)
            .then(if (f) Modifier.background(if (Th.light) Color(0x14000000) else Color(0x22FFFFFF)) else Modifier)
            .then(if (f) Modifier.border(2.dp, Th.sel, RoundedCornerShape(9.dp)) else Modifier)
            .onGloballyPositioned { co[0] = it }
            .onFocusChanged {
                f = it.isFocused
                if (it.isFocused && pl != null) scope.launch {
                    // Garde l'élément sélectionné entièrement visible, loin du bord et du fondu.
                    delay(90)
                    val c = co[0]
                    if (c != null && c.isAttached) {
                        val r = c.boundsInWindow()
                        val m = with(dens) { 30.dp.toPx() }
                        val over = r.bottom - (pl.bottom - m)
                        val under = (pl.top + m) - r.top
                        try {
                            if (over > 1f) pl.state.animateScrollBy(over)
                            else if (under > 1f && pl.state.canScrollBackward) pl.state.animateScrollBy(-under)
                        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                    }
                }
            }
            .onPreviewKeyEvent { if (holdGuard(it)) true else if (onLong != null) okLong(it, fired, onClick, onLong) else false }
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
                title, color = if (f) P_DARK else Th.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
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
                    .background(if (on) Th.sel else Color(0xFF8A94A8))
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
            val c = if (selected) Th.sel else Th.text
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
    // Le premier bouton du panneau (ex. Save) reçoit le focus quand on appuie sur Droite depuis la liste.
    val actSc = LocalActScope.current
    val first = remember { if (actSc != null && !actSc.used) { actSc.used = true; true } else false }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(120)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
            .then(if (first && actSc != null) Modifier.focusRequester(actSc.req) else Modifier)
            .background(if (f) (if (Th.light) Color(0x14000000) else Color(0x22FFFFFF)) else Color.Transparent)
            .then(if (f) Modifier.border(2.dp, Th.sel, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { holdGuard(it) }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (f) P_DARK else Th.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun PHeader(t: String) {
    Text(
        t.uppercase(), color = Th.sel, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun PInfo(text: String, error: Boolean = false) {
    Text(
        text, color = if (error) Color(0xFFFFB4A9) else Th.text, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
fun PInput(label: String, value: String, onChange: (String) -> Unit, autoFocus: Boolean = false) {
    val fr = remember { FocusRequester() }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(150)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    var foc by remember { mutableStateOf(false) }
    val pl = LocalPanelList.current
    val scope = rememberCoroutineScope()
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val co = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).onGloballyPositioned { co[0] = it }) {
        Text(label, color = if (foc) Th.text else P_SUB, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Th.text, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Th.text),
            modifier = Modifier.fillMaxWidth()
                .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
                .onFocusChanged {
                    foc = it.isFocused
                    if (!it.isFocused) kb?.hide()
                    else if (pl != null) scope.launch {
                        // Garde le champ entièrement visible (haut et bas du panneau)
                        delay(90)
                        val c = co[0]
                        if (c != null && c.isAttached) {
                            val r = c.boundsInWindow()
                            val m = with(dens) { 30.dp.toPx() }
                            val over = r.bottom - (pl.bottom - m)
                            val under = (pl.top + m) - r.top
                            try {
                                if (over > 1f) pl.state.animateScrollBy(over)
                                else if (under > 1f && pl.state.canScrollBackward) pl.state.animateScrollBy(-under)
                            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                        }
                    }
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionDown || ev.key == Key.DirectionUp)) {
                        kb?.hide()
                        fm.moveFocus(if (ev.key == Key.DirectionDown) androidx.compose.ui.focus.FocusDirection.Down else androidx.compose.ui.focus.FocusDirection.Up)
                        true
                    } else false
                },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Th.cell)
                        .border(if (foc) 2.dp else 1.dp, if (foc) Th.sel else Th.divider, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) { inner() }
            }
        )
    }
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
    // Seulement les catégories visibles (au moins une chaîne affichée) + les catégories créées + la catégorie actuelle
    val cats = (all.filter { !it.hidden }.map { it.cat } + cfg.cats + e.cat).distinct()

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
                item { PSwitch("Invert logotype colors", "", e.invert, { upd { it.copy(inv = !e.invert) } }) }
                item { PItem("Reset this channel", "", { onCfg(cfg.copy(ov = cfg.ov - e.id)); page = "menu" }) }
            }
            "move" -> {
                val list = all.filter { it.cat == e.cat && (!it.hidden || it.id == e.id) }
                PanelShell(
                    "Move (${e.cat})", { back() }, false,
                    scrollTo = 1 + list.indexOfFirst { it.id == e.id }.coerceAtLeast(0),
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
                    scrollTo = list.indexOfFirst { it.id == e.id }.coerceAtLeast(0),
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
            "cat" -> PanelShell("Add to category", { back() }, false, actions = { PButton("Back", { page = "menu" }) }, scrollTo = 1 + cats.indexOf(e.cat).coerceAtLeast(0)) {
                item { PItem("New category", "", { open("newcat", "") }) }
                items(cats) { cat ->
                    PItem(cat, if (cat == e.cat) "Current category" else "", { upd { o -> o.copy(cat = cat) }; page = "menu" }, autoFocus = cat == e.cat)
                }
            }
            else -> page = "menu"
        }
    }
}

// ---------------------------------------------------------------------------
// Options d'une catégorie : Move / Edit category / Show-Hide
// ---------------------------------------------------------------------------
@Composable
fun CategoryEditor(
    cat: String, all: List<Channel>, cfg: Cfg,
    onCfg: (Cfg) -> Unit, onClose: () -> Unit
) {
    var page by remember { mutableStateOf("menu") }
    var text by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf(cat) }
    val picked = remember { mutableStateListOf<String>() }
    val cats = all.map { it.cat }.distinct()
    val visCats = all.filter { !it.hidden }.map { it.cat }.distinct()

    fun back() { if (page == "menu") onClose() else page = "menu" }
    fun setHidden(names: Collection<String>, hide: Boolean) {
        var ov = cfg.ov
        for (c in all) if (c.cat in names) ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(hidden = hide))
        onCfg(cfg.copy(ov = ov))
    }
    fun rename(from: String, to: String) {
        var ov = cfg.ov
        for (c in all) if (c.cat == from) ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(cat = to))
        onCfg(cfg.copy(ov = ov, cats = cfg.cats.map { if (it == from) to else it }.distinct()))
    }
    fun restore(name: String) {
        var ov = cfg.ov
        for (c in all) if (c.cat == name && ov[c.id]?.cat?.isNotBlank() == true) ov = ov + (c.id to ov[c.id]!!.copy(cat = ""))
        onCfg(cfg.copy(ov = ov))
    }

    PanelDialogHost({ back() }) {
        when (page) {
            "menu" -> PanelShell(cur, { back() }, false, actions = { PButton("Close", { onClose() }) }) {
                item { PItem("Move", "Several categories at once", { picked.clear(); picked.add(cur); page = "move" }, autoFocus = true) }
                item { PItem("New category", "Create an empty category", { text = ""; page = "newcat" }) }
                item { PItem("Edit category", "Rename, restore", { page = "edit" }) }
                item { PItem("Show / Hide", "Choose from a list", { page = "vis" }) }
                item { PItem("Delete category", "Remove it from the lists", { page = "del" }) }
            }
            "del" -> PanelShell("Delete this category?", { page = "menu" }, false, actions = { PButton("Cancel", { page = "menu" }) }) {
                item { PInfo("The category and its channels disappear from your lists. You can bring it back later with Show / Hide.") }
                item {
                    PItem("Yes, delete", "", {
                        var ov = cfg.ov
                        for (c in all) if (c.cat == cur) ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(hidden = true))
                        onCfg(cfg.copy(ov = ov, cats = cfg.cats.filter { it != cur }))
                        onClose()
                    }, autoFocus = true)
                }
            }
            "edit" -> PanelShell("Edit category", { back() }, false, actions = { PButton("Back", { page = "menu" }) }) {
                item { PItem("Rename", cur, { text = cur; page = "rename" }, autoFocus = true) }
                item { PItem("Restore original name", "Undo renaming", { restore(cur); page = "menu"; onClose() }) }
                item { PItem("Show all channels", "Make every channel of this category visible", { setHidden(listOf(cur), false) }) }
            }
            "rename" -> PanelShell(
                "Rename category", { page = "edit" }, false,
                actions = {
                    PButton("Save", {
                        val v = text.trim()
                        if (v.isNotBlank() && v != cur) { rename(cur, v); onClose() } else page = "edit"
                    })
                    PButton("Cancel", { page = "edit" })
                }
            ) {
                item { PInput("Category name", text, { text = it }, autoFocus = true) }
            }
            "newcat" -> PanelShell(
                "New category", { page = "menu" }, false,
                actions = {
                    PButton("Save", {
                        val v = text.trim()
                        if (v.isNotBlank()) onCfg(cfg.copy(cats = (cfg.cats + v).distinct()))
                        onClose()
                    })
                    PButton("Cancel", { page = "menu" })
                }
            ) {
                item { PInput("Category name", text, { text = it }, autoFocus = true) }
            }
            "move" -> PanelShell(
                "Move categories", { back() }, false,
                scrollTo = 1 + visCats.indexOf(cur).coerceAtLeast(0),
                actions = {
                    PButton("Move up", { onCfg(moveCats(cfg, all, picked.toSet(), -1)) })
                    PButton("Move down", { onCfg(moveCats(cfg, all, picked.toSet(), 1)) })
                    PButton("To top", { onCfg(moveCatsEdge(cfg, all, picked.toSet(), true)) })
                    PButton("To bottom", { onCfg(moveCatsEdge(cfg, all, picked.toSet(), false)) })
                    PButton("Done", { onClose() })
                }
            ) {
                item { PInfo("OK = tick categories, then press Right to use the buttons.") }
                items(visCats, key = { it }) { c ->
                    PSwitch(
                        c, "", c in picked,
                        { if (c in picked) picked.remove(c) else picked.add(c) },
                        autoFocus = c == cur
                    )
                }
            }
            "vis" -> {
                val allHidden = all.all { it.hidden }
                PanelShell("Category visibility", { back() }, false, actions = { PButton("Done", { onClose() }) }, scrollTo = 1 + cats.indexOf(cur).coerceAtLeast(0)) {
                    item { PSwitch("Hide all", "", allHidden, { setHidden(cats, !allHidden) }) }
                    items(cats, key = { it }) { c ->
                        val shown = all.any { it.cat == c && !it.hidden }
                        PSwitch(c, "", shown, { setHidden(listOf(c), shown) }, autoFocus = c == cur)
                    }
                }
            }
            else -> page = "menu"
        }
    }
}

// ---------------------------------------------------------------------------
// Réglages : Sources, Guides, Chaînes, Lecteur, Affichage, À propos
// ---------------------------------------------------------------------------
private val SYNC_OPTIONS = listOf(0, 3, 6, 12, 24, 72, 168)

private fun hoursLabel(h: Int): String = when (h) {
    0 -> "Disabled"
    24 -> "1 day"
    72 -> "3 days"
    168 -> "7 days"
    else -> "$h hours"
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
    var fOut by remember { mutableStateOf("m3u8") }
    var fInsecure by remember { mutableStateOf(false) }
    var fUa by remember { mutableStateOf("") }
    var fAuth by remember { mutableStateOf(false) }
    var selCat by remember { mutableStateOf("") }
    var editCatName by remember { mutableStateOf<String?>(null) }
    var accTxt by remember { mutableStateOf("") }
    var selEpg by remember { mutableStateOf("") }
    var epgIn by remember { mutableStateOf("") }
    var epgName by remember { mutableStateOf("") }
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
            "sync", "setup" -> page = "sources"
            "theme" -> page = "display"
            "src_menu" -> page = "setup"
            "src_cats", "src_acc" -> page = "src_menu"
            "src_type" -> if (canExit) page = "setup"
            "src_form" -> page = if (editingId != null) "src_menu" else "src_type"
            "guide_menu", "guide_add" -> page = "guides"
            "guide_ren" -> page = "guide_menu"
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
        fOut = s?.output ?: "m3u8"
        fInsecure = s?.insecure ?: false
        fUa = s?.userAgent ?: ""
        fAuth = s?.useAuth ?: false
        msg = ""
        page = "src_form"
    }
    fun saveForm() {
        val id = editingId ?: "s${System.currentTimeMillis()}"
        val old = sources.firstOrNull { it.id == id }
        val s: Source? = if (type == "m3u") {
            val t = fUrl.trim()
            if (t.isEmpty()) {
                msg = "Enter a link or choose a file."
                null
            } else {
                val src = if (t.startsWith("http") || t.startsWith("content://") || t.startsWith("file://")) t else "http://$t"
                Source(
                    id, fName.trim().ifBlank { "Playlist M3U" }, "m3u",
                    user = if (fAuth) fUser.trim() else "", pass = if (fAuth) fPass.trim() else "",
                    url = src, epg = fEpg.trim(), enabled = old?.enabled ?: true,
                    insecure = fInsecure, userAgent = fUa.trim(), useAuth = fAuth
                )
            }
        } else {
            if (fHost.isBlank()) {
                msg = "Enter the server address."
                null
            } else {
                val host = norm(fHost)
                Source(
                    id, fName.trim().ifBlank { host }, "xtream", host, fUser.trim(), fPass.trim(),
                    epg = fEpg.trim(), enabled = old?.enabled ?: true, insecure = fInsecure,
                    userAgent = fUa.trim(), output = fOut
                )
            }
        }
        if (s != null) {
            val first = sources.isEmpty()
            onSources(if (editingId != null) sources.map { if (it.id == s.id) s else it } else sources + s)
            msg = ""
            page = if (first) "" else "setup"
        }
    }

    val cats = (all.map { it.cat } + cfg.cats).distinct()
    val pmem = remember { PMem { page } }

    CompositionLocalProvider(LocalPMem provides pmem) {
    when (page) {
        "" -> PanelShell("Settings", { back() }) {
            item { PItem("Sources", "${sources.size} source(s) configured", { page = "sources" }, autoFocus = true) }
            item { PItem("Guides (EPG)", "${cfg.epgs.size} guide(s) added", { page = "guides" }) }
            item { PItem("Channels", "Rename, move, hide, categories", { page = "channels" }) }
            item { PItem("Player", "Preview, audio and subtitles", { page = "player" }) }
            item { PItem("DVR", "Recordings, reminders, storage", { page = "dvr" }) }
            item { PItem("Display", "Theme, channel numbers", { page = "display" }) }
            item { PItem("About", "", { page = "about" }) }
        }
        "sources" -> PanelShell("Sources", { back() }, actions = { PButton("Done", { back() }) }) {
            item {
                PItem(
                    "Time between synchronization", hoursLabel(cfg.syncHours),
                    { page = "sync" }, autoFocus = true
                )
            }
            item {
                PSwitch(
                    "Update on app start", "Refresh channels and guide every time the app opens",
                    cfg.updateOnStart, { onCfg(cfg.copy(updateOnStart = !cfg.updateOnStart)) }
                )
            }
            item { PItem("Setup", "${sources.size} source(s) · add, enable, edit", { page = "setup" }) }
            item {
                PItem(
                    "Update",
                    if (SyncState.busy) "Updating…"
                    else "Last run: " + (if (lastSync > 0) SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH).format(Date(lastSync)) else "—"),
                    { if (!SyncState.busy) onReload() },
                    trailing = {
                        if (SyncState.busy) {
                            androidx.compose.material3.CircularProgressIndicator(
                                Modifier.size(24.dp), color = Color(0xFFE0B85A), strokeWidth = 3.dp
                            )
                        }
                    }
                )
            }
            if (msg.isNotBlank()) item { PInfo(msg) }
        }
        "sync" -> PanelShell("Time between synchronization", { back() }, actions = { PButton("Back", { back() }) }) {
            items(SYNC_OPTIONS, key = { it }) { h ->
                PRadio(
                    hoursLabel(h), "", cfg.syncHours == h,
                    { onCfg(cfg.copy(syncHours = h)); page = "sources" },
                    autoFocus = h == cfg.syncHours
                )
            }
        }
        "setup" -> PanelShell("Setup", { back() }, actions = { PButton("Done", { back() }) }) {
            item {
                PItem(
                    "Add new source", "Playlist (M3U / M3U8) or Xtream Codes",
                    { editingId = null; type = "xtream"; msg = ""; page = "src_type" }, autoFocus = true
                )
            }
            items(sources, key = { it.id }) { s ->
                PItem(
                    s.name, (if (s.type == "m3u") "M3U · " + s.url else "Xtream · " + s.host) + "  (hold OK: options)",
                    { onSources(sources.map { if (it.id == s.id) it.copy(enabled = !it.enabled) else it }) },
                    onLong = { selSrc = s; page = "src_menu" },
                    leading = { f ->
                        val c = if (f) P_DARK else Th.text
                        Box(
                            Modifier.size(20.dp).border(2.dp, c, RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (s.enabled) Text("✓", color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
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
                    item { PItem("Account status", "Expiry, connections, login check", { accTxt = "Checking..."; page = "src_acc" }) }
                    item { PItem("Choose categories", "Pick the ones you want to keep", { page = "src_cats" }) }
                    item { PItem("Delete this source", "", { onSources(sources.filter { it.id != s.id }); page = "setup" }) }
                }
            }
        }
        "src_acc" -> {
            val s = selSrc
            if (s != null) {
                LaunchedEffect(s.id) { accTxt = fetchAccount(s) }
                PanelShell(s.name + " : account", { back() }, actions = { PButton("Back", { back() }) }) {
                    item { PInfo(accTxt) }
                }
            }
        }
        "src_cats" -> {
            val s = selSrc
            if (s != null) {
                val mine = all.filter { it.src == s.id || it.src == s.name }
                val scats = mine.map { it.cat }.distinct()
                fun setHid(names: Collection<String>, hide: Boolean) {
                    var ov = cfg.ov
                    for (c in mine) if (c.cat in names) ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(hidden = hide))
                    onCfg(cfg.copy(ov = ov))
                }
                val keptCount = scats.count { c -> mine.any { it.cat == c && !it.hidden } }
                PanelShell(
                    s.name + " : categories", { back() },
                    scrollTo = 0,
                    actions = {
                        PButton("Select all", { setHid(scats, false) })
                        PButton("Unselect all", { setHid(scats, true) })
                        PButton("Done", { back() })
                    }
                ) {
                    item { PInfo("$keptCount of ${scats.size} categories selected. OK = select / unselect.") }
                    items(scats, key = { it }) { c ->
                        val n = mine.count { it.cat == c }
                        val on = mine.any { it.cat == c && !it.hidden }
                        PSwitch(c, "$n channel(s)", on, { setHid(listOf(c), on) }, autoFocus = c == scats.first())
                    }
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
            item { PRadio("Playlist (m3u)", "M3U / M3U8 link or local file", type == "m3u", { type = "m3u" }, autoFocus = true) }
            item { PRadio("Xtream Codes", "Server, username and password", type == "xtream", { type = "xtream" }) }
        }
        "src_form" -> PanelShell(
            if (type == "m3u") "Playlist (m3u)" else "Xtream Codes", { back() },
            actions = {
                PButton(if (editingId != null) "Save" else "Next", { saveForm() })
                PButton("Back", { back() })
            }
        ) {
            if (type == "m3u") {
                item { PInput("Playlist address (URL)", fUrl, { fUrl = it }, autoFocus = true) }
                item {
                    PItem(
                        "Select local playlist", "Pick an M3U / M3U8 file on this device",
                        {
                            try { picker.launch(arrayOf("*/*")) } catch (ex: Exception) { msg = "No file browser on this TV: use a link (URL)." }
                        }
                    )
                }
                item { PSwitch("Use authentication", "Username and password for the playlist link", fAuth, { fAuth = !fAuth }) }
                if (fAuth) {
                    item { PInput("Username", fUser, { fUser = it }) }
                    item { PInput("Password", fPass, { fPass = it }) }
                }
            } else {
                item { PInput("Server (http://host:port)", fHost, { fHost = it }, autoFocus = true) }
                item { PInput("Username", fUser, { fUser = it }) }
                item { PInput("Password", fPass, { fPass = it }) }
            }
            item { PSwitch("Allow insecure connection", "Accept invalid or self-signed certificates", fInsecure, { fInsecure = !fInsecure }) }
            if (type != "m3u") {
                item {
                    PItem(
                        "Output", if (fOut == "ts") "MPEGTS" else "HLS (m3u8)",
                        { fOut = if (fOut == "ts") "m3u8" else "ts" }
                    )
                }
            }
            item { PInput("User Agent (optional)", fUa, { fUa = it }) }
            item { PInput("Name (optional)", fName, { fName = it }) }
            item { PInput("XMLTV guide for this source (optional)", fEpg, { fEpg = it }) }
            if (msg.isNotBlank()) item { PInfo(msg, true) }
        }
        "guides" -> PanelShell("Guides (EPG)", { back() }, actions = { PButton("Done", { back() }) }) {
            item { PItem("Add XMLTV guide", "URL of an .xml or .gz file", { epgIn = ""; epgName = ""; page = "guide_add" }, autoFocus = true) }
            items(cfg.epgs) { u ->
                val nm = cfg.epgNames[u].orEmpty()
                PItem(nm.ifBlank { u }, if (nm.isBlank()) "Top guide has priority · OK: manage" else u, { selEpg = u; epgName = nm; page = "guide_menu" })
            }
            item {
                PSwitch(
                    "Keep only my playlist channels", "Import programmes only for channels in your playlists (faster, lighter). Applies at the next guide update",
                    cfg.epgMine, { onCfg(cfg.copy(epgMine = !cfg.epgMine)) }
                )
            }
            item {
                PItem(
                    "Logo priority",
                    if (cfg.logoPrio == "xmltv") "1) XMLTV guide   2) playlist" else "1) playlist   2) XMLTV guide",
                    { onCfg(cfg.copy(logoPrio = if (cfg.logoPrio == "xmltv") "playlist" else "xmltv")) }
                )
            }
            if (msg.isNotBlank()) item { PInfo(msg) }
        }
        "guide_menu" -> PanelShell("Guide", { back() }, actions = { PButton("Back", { back() }) }) {
            item { PInfo(selEpg) }
            item { PItem("Rename", cfg.epgNames[selEpg].orEmpty().ifBlank { "No name" }, { epgName = cfg.epgNames[selEpg].orEmpty(); page = "guide_ren" }, autoFocus = true) }
            item {
                PItem("Delete this guide", "", { onCfg(cfg.copy(epgs = cfg.epgs.filter { it != selEpg }, epgNames = cfg.epgNames - selEpg)); page = "guides" })
            }
            item {
                PItem("Move to top (priority)", "", { onCfg(cfg.copy(epgs = listOf(selEpg) + cfg.epgs.filter { it != selEpg })); page = "guides" })
            }
        }
        "guide_ren" -> PanelShell(
            "Rename guide", { page = "guide_menu" },
            actions = {
                PButton("Save", {
                    val n = epgName.trim()
                    onCfg(cfg.copy(epgNames = if (n.isBlank()) cfg.epgNames - selEpg else cfg.epgNames + (selEpg to n)))
                    page = "guides"
                })
                PButton("Back", { page = "guide_menu" })
            }
        ) {
            item { PInput("Guide name", epgName, { epgName = it }, autoFocus = true) }
        }
        "guide_add" -> PanelShell(
            "Add XMLTV guide", { back() },
            actions = {
                PButton("Save", {
                    if (epgIn.isNotBlank()) {
                        val u = norm(epgIn)
                        val n = epgName.trim()
                        onCfg(cfg.copy(epgs = (cfg.epgs + u).distinct(), epgNames = if (n.isBlank()) cfg.epgNames else cfg.epgNames + (u to n)))
                    }
                    page = "guides"
                })
                PButton("Back", { back() })
            }
        ) {
            item { PInput("Name (optional)", epgName, { epgName = it }, autoFocus = true) }
            item { PInput("Guide URL (.xml or .gz)", epgIn, { epgIn = it }) }
        }
        "channels" -> PanelShell("Channels", { back() }, actions = { PButton("Done", { back() }) }) {
            item { PSwitch("Merge sources", "Same categories from all sources shown together", cfg.merge, { onCfg(cfg.copy(merge = !cfg.merge)) }, autoFocus = true) }
            item { PItem("New category", "", { catIn = ""; page = "newcat" }) }
            items(cats) { cat -> PItem(cat, "${all.count { it.cat == cat }} channel(s)" + if (all.none { it.cat == cat && !it.hidden }) " · hidden" else "", { selCat = cat; page = "chlist" }, onLong = { editCatName = cat }) }
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
            item { PrefSwitch("Card preview", "Home and mini player: rest on a card to preview it (muted). OK opens the channel", "cardPreview", true) }
            item { PHeader("Video") }
            item { PrefCycle("Video decoder", "vdec", listOf("hw" to "Hardware", "sw" to "Software"), "hw", "  (restart the app to apply)") }
            item { PrefCycle("Playback buffer", "buf", listOf("5" to "5 Seconds", "10" to "10 Seconds", "20" to "20 Seconds", "30" to "30 Seconds", "60" to "60 Seconds"), "20", "  (restart the app to apply)") }
            item { PrefSwitch("Auto frame rate", "Enable to match display refresh rate to frame rate", "afr", true) }
            item { PHeader("Audio") }
            item {
                val ap = remember { ctx.getSharedPreferences("iptv_audio", android.content.Context.MODE_PRIVATE) }
                var pt by remember { mutableStateOf(ap.getBoolean("pt", false)) }
                PSwitch("Audio passthrough", "Send Dolby 5.1 to the receiver. Restart the app to apply. Off = safe stereo sound", pt, {
                    pt = !pt
                    ap.edit().putBoolean("pt", pt).apply()
                })
            }
            item { PrefCycle("Audio decoder", "adec", listOf("hw" to "Hardware", "sw" to "Software"), "hw", "  (restart the app to apply)") }
            item {
                PrefCycle(
                    "Preferred audio language", "alang",
                    listOf("" to "Not set", "en" to "English", "fr" to "French", "es" to "Spanish", "ar" to "Arabic", "de" to "German", "it" to "Italian", "pt" to "Portuguese"),
                    ""
                )
            }
            item { PHeader("Text") }
            item {
                PrefCycle(
                    "Subtitle language", "tlang",
                    listOf("" to "Not set", "en" to "English", "fr" to "French", "es" to "Spanish", "ar" to "Arabic", "de" to "German", "it" to "Italian", "pt" to "Portuguese"),
                    ""
                )
            }
            item { PHeader("Advanced") }
            item { PrefSwitch("Allow insecure streams", "Skip verification of SSL certificates", "insecure", false) }
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
                    "Theme", when (Th.mode) { 2 -> "Black"; else -> "Dark" },
                    { page = "theme" }, autoFocus = true
                )
            }
            item {
                PSwitch(
                    "Channel numbers", "Show numbers in the channel list", cfg.showNums,
                    { onCfg(cfg.copy(showNums = !cfg.showNums)) }
                )
            }
        }
        "theme" -> PanelShell("Theme", { back() }, actions = { PButton("Back", { back() }) }) {
            item { PRadio("Dark", "Dark blue-grey panels, white text", Th.mode == 0, { Th.set(ctx, 0); page = "display" }, autoFocus = Th.mode == 0) }
            item { PRadio("Black", "Pure black background (OLED)", Th.mode == 2, { Th.set(ctx, 2); page = "display" }, autoFocus = Th.mode == 2) }
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
            item { PInfo("Elite TV · version 1.0") }
        }
    }
    }

    val ec = editCh
    if (ec != null) ChannelEditor(ec, all, cfg, guide, onCfg) { editCh = null }
    val ecat = editCatName
    if (ecat != null) CategoryEditor(ecat, all, cfg, onCfg) { editCatName = null }
}
