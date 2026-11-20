@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.iptvtv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
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
private val TITLE = Color(0xFFFFD166)

val LocalRowFocused = compositionLocalOf { false }

// Couleur d'un texte dans une ligne : devient sombre quand la barre de sélection blanche est active.
@Composable
fun fcolor(normal: Color, focused: Color = Color(0xFF14213D)): Color =
    if (LocalRowFocused.current) focused else normal

@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onLong: (() -> Unit)? = null,
    pad: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable RowScope.() -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { BooleanArray(1) }
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (f) Color.White else Color.Transparent)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { if (onLong == null) false else okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }.padding(pad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalRowFocused provides f) { content() }
    }
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
    all: List<Channel>, sources: List<Source>, current: Channel, epg: Epg, nowMs: Long, focusId: Int, refocus: Int,
    showNums: Boolean, onPick: (Channel) -> Unit, onEdit: (Channel) -> Unit
) {
    var selSrc by remember { mutableStateOf(current.src) }
    var selCat by remember { mutableStateOf(current.cat) }
    var cols by remember { mutableStateOf(false) }
    var goList by remember { mutableStateOf(0) }
    val catFr = remember { FocusRequester() }
    val srcIds = remember(all) { all.map { it.src }.distinct() }
    val srcCats = all.filter { it.src == selSrc }.map { it.cat }.distinct()
    val list = all.filter { it.src == selSrc && it.cat == selCat }
    var focused by remember { mutableStateOf(current) }
    var upIdx by remember { mutableStateOf(-1) }
    val fr = remember { FocusRequester() }
    val idx = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    val fid = if (list.any { it.id == focusId }) focusId else (list.firstOrNull()?.id ?: -1)
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 3).coerceAtLeast(0))
    val fc = list.firstOrNull { it.id == focused.id } ?: focused
    val fp = epg.now(fc.epgId)
    val ups = epg.upcoming(fc.epgId, 4)
    val shownP: Prog? = if (upIdx in ups.indices) ups[upIdx] else fp
    LaunchedEffect(refocus) {
        delay(150)
        try { fr.requestFocus() } catch (ex: Exception) { }
    }
    LaunchedEffect(goList) {
        if (goList > 0) {
            delay(150)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    var firstSel by remember { mutableStateOf(true) }
    LaunchedEffect(selSrc, selCat) {
        if (firstSel) firstSel = false else ls.scrollToItem(0)
    }
    BackHandler(enabled = upIdx >= 0 || cols) {
        if (upIdx >= 0) upIdx = -1 else {
            cols = false
            selSrc = current.src
            selCat = current.cat
        }
    }
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
            } else if (ev.key == Key.DirectionRight && rc == 1 && ups.isNotEmpty() && !cols) {
                upIdx = 0
                true
            } else if (ev.key == Key.DirectionLeft && !cols && rc == 0) {
                cols = true
                true
            } else {
                false
            }
        }
    ) {
        if (cols) {
            LaunchedEffect(Unit) {
                delay(120)
                try { catFr.requestFocus() } catch (ex: Exception) { }
            }
            Column(Modifier.width(200.dp).fillMaxHeight().background(Color(0xB3000000)).padding(top = 60.dp, start = 8.dp, end = 8.dp)) {
                Text("Sources", color = CYAN, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                LazyColumn {
                    items(srcIds) { sid ->
                        val nm = sources.firstOrNull { it.id == sid }?.name ?: "Source"
                        FocusRow({ selSrc = sid }, Modifier, {
                            selSrc = sid
                            if (!all.any { it.src == sid && it.cat == selCat }) selCat = all.first { it.src == sid }.cat
                        }) {
                            Text(nm, color = fcolor(if (sid == selSrc) Color.White else GRAY), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.width(230.dp).fillMaxHeight().background(Color(0xA6000000)).padding(top = 60.dp, start = 8.dp, end = 8.dp)) {
                Text("Categories", color = CYAN, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                LazyColumn {
                    items(srcCats) { cat ->
                        FocusRow(
                            {
                                cols = false
                                goList++
                            },
                            if (cat == selCat) Modifier.focusRequester(catFr) else Modifier,
                            { selCat = cat }
                        ) {
                            Text(cat, color = fcolor(if (cat == selCat) Color.White else GRAY), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Column(Modifier.width(420.dp).fillMaxHeight().background(Color(0x99000000))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selCat, color = CYAN, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                Text(if (cols) "OK: select" else "◄ categories  ·  Hold OK: options", color = GRAY, fontSize = 11.sp)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val rowH = maxHeight / 10
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { c ->
                        val pr = epg.now(c.epgId)
                        FocusRow(
                            { onPick(c) },
                            (if (c.id == fid) Modifier.focusRequester(fr) else Modifier).height(rowH),
                            { focused = c; upIdx = -1; cols = false },
                            { onEdit(c) },
                            4.dp
                        ) {
                            if (showNums) {
                                Text(
                                    "${all.indexOf(c) + 1}", color = fcolor(GRAY, Color(0xFF45557A)), fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1,
                                    modifier = Modifier.width(36.dp).padding(start = 4.dp)
                                )
                            }
                            Box(Modifier.size(52.dp, 32.dp)) {
                                if (c.icon.isNotBlank()) {
                                    AsyncImage(
                                        model = c.icon, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            val rf = LocalRowFocused.current
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(
                                    c.name, color = fcolor(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, softWrap = false,
                                    overflow = if (rf) TextOverflow.Clip else TextOverflow.Ellipsis,
                                    modifier = if (rf) Modifier.basicMarquee() else Modifier
                                )
                                Text(
                                    pr?.title ?: "", color = fcolor(GRAY, Color(0xFF45557A)), fontSize = 12.sp,
                                    maxLines = 1, softWrap = false,
                                    overflow = if (rf) TextOverflow.Clip else TextOverflow.Ellipsis,
                                    modifier = if (rf) Modifier.basicMarquee() else Modifier
                                )
                                if (pr != null) Bar(fracOf(pr, nowMs), Modifier.padding(top = 2.dp).fillMaxWidth(0.8f))
                            }
                        }
                    }
                }
            }
        }
        if (!cols) Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xB3000000)).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fc.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                Text(hm(nowMs), color = GRAY, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (shownP == null) {
                    Text("No guide information", color = GRAY, fontSize = 16.sp)
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${hm(shownP.start)} - ${hm(shownP.stop)}" + if (upIdx >= 0) "   (up next)" else "",
                                color = GRAY, fontSize = 14.sp
                            )
                            Text(
                                shownP.title, color = TITLE, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                            if (upIdx < 0) Bar(fracOf(shownP, nowMs), Modifier.padding(vertical = 8.dp).fillMaxWidth())
                            if (shownP.sub.isNotBlank() && shownP.sub != shownP.desc) {
                                Text(shownP.sub, color = CYAN, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                shownP.desc.ifBlank { shownP.sub }, color = Color(0xFFE0E4EC), fontSize = 14.sp,
                                maxLines = 14, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            if (shownP.cat.isNotBlank()) {
                                Text(shownP.cat, color = GRAY, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
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
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(12.dp)
                ) {
                    Text("Up next", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Hold Right to browse them", color = GRAY, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ups.forEachIndexed { i, u ->
                            val sel = i == upIdx
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) Color.White else Color.Transparent).padding(4.dp)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1B2230))
                                ) {
                                    if (u.icon.isNotBlank()) {
                                        AsyncImage(
                                            model = u.icon, contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                Text(
                                    hm(u.start), color = if (sel) Color.Black else Color.White,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    u.title, color = if (sel) Color(0xFF222222) else GRAY, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(4 - ups.size) { Spacer(Modifier.weight(1f)) }
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

// ---------------------------------------------------------------------------
// Recherche de chaînes
// ---------------------------------------------------------------------------
@Composable
fun SearchScreen(chans: List<Channel>, epg: Epg, onPlay: (Channel) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    var q by remember { mutableStateOf("") }
    val res = remember(q, chans) {
        val t = q.trim().lowercase()
        if (t.isEmpty()) emptyList() else chans.filter { it.name.lowercase().contains(t) }.take(60)
    }
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Search", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            q, { q = it }, singleLine = true, label = { Text("Channel name") },
            modifier = Modifier.padding(vertical = 10.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            items(res, key = { it.id }) { c ->
                FocusRow({ onPlay(c) }) {
                    Box(Modifier.size(64.dp, 40.dp)) {
                        if (c.icon.isNotBlank()) {
                            AsyncImage(
                                model = c.icon, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.name, color = fcolor(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(epg.now(c.epgId)?.title ?: "", color = fcolor(GRAY, Color(0xFF45557A)), fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Guide : une colonne par chaîne (logo, programme en cours, programmes suivants)
// ---------------------------------------------------------------------------
private val PINK = Color(0xFFE20074)

@Composable
fun GuideBar(frac: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).background(Color(0x44FFFFFF))) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(PINK))
    }
}

@Composable
fun GuideNowCard(c: Channel, p: Prog?, onPlay: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF221E2C))
            .border(if (f) 3.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused }
            .clickable { onPlay() }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF14111B))) {
            if (p != null && p.icon.isNotBlank()) {
                AsyncImage(
                    model = p.icon, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    c.name, color = GRAY, fontSize = 13.sp, maxLines = 2,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                )
            }
        }
        Column(Modifier.padding(8.dp)) {
            if (p != null) {
                Text("${hm(p.start)} - ${hm(p.stop)}", color = GRAY, fontSize = 11.sp)
                Text(
                    p.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                GuideBar(fracOf(p, System.currentTimeMillis()), Modifier.padding(top = 6.dp).fillMaxWidth())
            } else {
                Text("No information", color = GRAY, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun GuideRow(u: Prog, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (f) Color(0xFF3A3348) else Color(0xFF1A1722))
            .border(if (f) 2.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }.padding(8.dp)
    ) {
        Text(hm(u.start), color = PINK, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(u.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GuideColumn(c: Channel, epg: Epg, onPlay: () -> Unit, onDetail: (Prog) -> Unit) {
    val p = epg.now(c.epgId)
    val ups = epg.upcoming(c.epgId, 5)
    Column(Modifier.width(190.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            if (c.icon.isNotBlank()) {
                AsyncImage(
                    model = c.icon, contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit
                )
            } else {
                Text(c.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        GuideNowCard(c, p, onPlay)
        for (u in ups) {
            Spacer(Modifier.height(5.dp))
            GuideRow(u) { onDetail(u) }
        }
    }
}

@Composable
fun GuideScreen(chans: List<Channel>, epg: Epg, onPlay: (Channel) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    var cat by remember { mutableStateOf("") }
    var pickCat by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<Channel, Prog>?>(null) }
    val cats = remember(chans) { chans.map { it.cat }.distinct() }
    val cols = remember(chans, cat, epg.progs) {
        val l = if (cat.isEmpty()) chans else chans.filter { it.cat == cat }
        val withData = l.filter { epg.progs.containsKey(it.epgId) }
        if (withData.isNotEmpty()) withData else l
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1D1330), Color(0xFF0B0A12))))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                FocusRow({ pickCat = true }, Modifier.width(340.dp)) {
                    Text(
                        (if (cat.isEmpty()) "All categories" else cat) + "  (change)",
                        color = fcolor(PINK), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text("${cols.size} channels", color = GRAY, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cols, key = { it.id }) { c ->
                        GuideColumn(c, epg, { onPlay(c) }) { p -> detail = Pair(c, p) }
                    }
                }
            }
        }
    }

    val d = detail
    if (d != null) {
        val dc = d.first
        val dp = d.second
        PanelDialog(
            dp.title, { detail = null },
            actions = {
                PButton("Watch channel", { detail = null; onPlay(dc) }, autoFocus = true)
                PButton("Close", { detail = null })
            }
        ) {
            item { PInfo("${dc.name}  ·  ${hm(dp.start)} - ${hm(dp.stop)}") }
            if (dp.sub.isNotBlank() && dp.sub != dp.desc) item { PInfo(dp.sub) }
            item { PInfo(dp.desc.ifBlank { dp.sub }.ifBlank { "No description." }) }
        }
    }
    if (pickCat) {
        PanelDialog("Category", { pickCat = false }, actions = { PButton("Close", { pickCat = false }) }) {
            item { PItem("All categories", "", { cat = ""; pickCat = false }, autoFocus = true) }
            items(cats) { c2 -> PItem(c2, "", { cat = c2; pickCat = false }) }
        }
    }
}
