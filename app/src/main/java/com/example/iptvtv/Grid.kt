@file:OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Mini player : s'affiche au-dessus de la vidéo (programme en cours + rangée de chaînes)
// ---------------------------------------------------------------------------
private val M_GRAY = Color(0xFFB8BEC9)
private val M_TILE = Color(0xFF2B2F36)

@Composable
private fun MiniItem(
    onClick: () -> Unit,
    req: FocusRequester?,
    title: String,
    sub: String,
    frac: Float?,
    logo: String?,
    onFocus: () -> Unit,
    onLong: (() -> Unit)? = null,
    thumb: @Composable BoxScope.() -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val sc by animateFloatAsState(if (f) 1.06f else 1f, label = "miniItem")
    Column(
        Modifier.width(170.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { if (onLong == null) false else okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        // The selection frame is drawn around the picture, so it never hides the progress bar.
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(3.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(10.dp)).padding(3.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).background(M_TILE)) {
                thumb()
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo, contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 10.dp).size(40.dp, 22.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (frac != null) {
                    Bar(frac, Modifier.align(Alignment.BottomStart).fillMaxWidth(), 5.dp)
                }
            }
        }
        Text(
            title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = (if (f) Modifier.basicMarquee() else Modifier).padding(top = 6.dp, start = 3.dp)
        )
        Text(sub, color = M_GRAY, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
fun MiniPlayer(
    channels: List<Channel>, c: Channel, epg: Epg, nowMs: Long, hist: List<Int>,
    isPlaying: () -> Boolean, onToggle: () -> Unit,
    onPick: (Channel) -> Unit, onGuide: () -> Unit, onKey: () -> Unit,
    onClearHist: (Int?) -> Unit = {}
) {
    var clearFor by remember { mutableStateOf<Channel?>(null) }
    val clr = remember { FocusRequester() }
    val p = epg.now(c.epgId)
    // Always the last 10 channels played (the current one first).
    val recent = remember(hist, channels, c.id) {
        val l = ArrayList<Channel>()
        l.add(c)
        for (id in hist) {
            val ch = channels.firstOrNull { it.id == id }
            if (ch != null && l.none { it.id == ch.id }) l.add(ch)
        }
        l.take(10)
    }
    val gr = remember { FocusRequester() }
    val btn = remember { FocusRequester() }
    var ctrl by remember { mutableStateOf(false) }
    val st = rememberLazyListState()
    val sc = rememberCoroutineScope()
    fun focusItem(idx: Int) {
        sc.launch { try { st.animateScrollToItem((idx - 1).coerceAtLeast(0)) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
    }
    var playing by remember { mutableStateOf(isPlaying()) }
    LaunchedEffect(Unit) {
        delay(150)
        try { gr.requestFocus() } catch (ex: Exception) { }
    }
    LaunchedEffect(ctrl) {
        if (ctrl) {
            delay(60)
            try { btn.requestFocus() } catch (ex: Exception) { }
        }
    }
    LaunchedEffect(clearFor) {
        if (clearFor != null) {
            delay(100)
            try { clr.requestFocus() } catch (ex: Exception) { }
        } else if (clearFor == null) {
            try { gr.requestFocus() } catch (ex: Exception) { }
        }
    }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            onKey()
            if (clearFor != null) {
                if (it.key == Key.Back || it.key == Key.Escape) {
                    if (it.type == KeyEventType.KeyUp) clearFor = null
                    true
                } else {
                    false
                }
            } else if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp && !ctrl) {
                ctrl = true
                true
            } else {
                false
            }
        }
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000), Color(0xF5000000))))
                .padding(start = 40.dp, end = 40.dp, top = 90.dp, bottom = 22.dp)
        ) {
            Text(c.name, color = M_GRAY, fontSize = 16.sp, maxLines = 1)
            Text(
                p?.title ?: "", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (ctrl) {
                var bf by remember { mutableStateOf(false) }
                Box(
                    Modifier.padding(top = 10.dp).size(64.dp, 40.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (bf) Color.White else Color(0x66FFFFFF))
                        .focusRequester(btn)
                        .onFocusChanged {
                            // Leaving the button (Down to the channels) makes it disappear again.
                            if (bf && !it.isFocused) ctrl = false
                            bf = it.isFocused
                        }
                        .clickable {
                            onToggle()
                            playing = isPlaying()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (playing) "❚❚" else "▶", color = if (bf) P_DARK_TXT else Color.White,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            if (p != null) {
                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(hm(p.start), color = M_GRAY, fontSize = 13.sp)
                    Bar(fracOf(p, nowMs), Modifier.weight(1f).padding(horizontal = 12.dp), 8.dp)
                    val left = ((p.stop - nowMs) / 60000L).coerceAtLeast(0)
                    Text("+$left min", color = M_GRAY, fontSize = 13.sp)
                }
            }
            Text(
                "Channels", color = Color(0xFFFFE9A8), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            LazyRow(
                state = st,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 14.dp, end = 60.dp, top = 12.dp, bottom = 14.dp)
            ) {
                item {
                    MiniItem(onGuide, gr, "Program guide", "", null, null, { focusItem(0) }) {
                        Column(
                            Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            repeat(4) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(7.dp, 7.dp).background(Color(0xFFEFE6C8)))
                                    Box(Modifier.size(38.dp, 7.dp).background(Color(0xFFEFE6C8)))
                                }
                            }
                        }
                    }
                }
                itemsIndexed(recent, key = { _, ch -> ch.id }) { ri, ch ->
                    val pr = epg.now(ch.epgId)
                    val num = channels.indexOfFirst { it.id == ch.id } + 1
                    MiniItem(
                        { onPick(ch) }, null,
                        pr?.title ?: ch.name, "$num - ${ch.name.uppercase()}",
                        if (pr != null) fracOf(pr, nowMs) else null,
                        if (pr?.icon.isNullOrBlank()) null else ch.icon,
                        { focusItem(ri + 1) },
                        { clearFor = ch }
                    ) {
                        val img = pr?.icon?.ifBlank { null } ?: ch.icon
                        if (img.isNotBlank()) {
                            AsyncImage(
                                model = img, contentDescription = null, modifier = Modifier.fillMaxSize(),
                                contentScale = if (pr?.icon.isNullOrBlank()) ContentScale.Fit else ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
        val cf = clearFor
        if (cf != null) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(24.dp).width(380.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xF2151A24)).padding(16.dp)
            ) {
                Text("Clear recent channels", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(cf.name, color = M_GRAY, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                FocusRow({ onClearHist(cf.id); clearFor = null }, Modifier.focusRequester(clr)) {
                    Text("This channel", color = fcolor(Color.White), fontSize = 16.sp)
                }
                FocusRow({ onClearHist(null); clearFor = null }) {
                    Text("Remove all", color = fcolor(Color.White), fontSize = 16.sp)
                }
                FocusRow({ clearFor = null }) {
                    Text("Cancel", color = fcolor(Color.White), fontSize = 16.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Guide graphique (grille horaire)
// ---------------------------------------------------------------------------
// Catégorie choisie dans le guide : conservée quand on revient du lecteur.
object GuideState {
    var cat: String = ""
}

private const val DPM = 4
private const val TOTAL_MIN = 2160
private val LW = 104.dp
private val ACCENT = Color(0xFF2D9BE0)
private val P_DARK_TXT = Color(0xFF14213D)

@Composable
private fun GChip(label: String, selected: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val gk by androidx.compose.animation.core.animateFloatAsState(if (f) 1f else 0f, androidx.compose.animation.core.tween(120), label = "chipGrow")
    Box(
        Modifier.zIndex(if (f) 1f else 0f)
            .graphicsLayer {
                if (size.width > 0f && size.height > 0f) {
                    val g = 4.dp.toPx() * gk
                    scaleX = (size.width + 2 * g) / size.width
                    scaleY = (size.height + 2 * g) / size.height
                }
            }
            .then(if (f) Modifier.shadow(10.dp, RoundedCornerShape(3.dp)) else Modifier)
            .clip(RoundedCornerShape(3.dp))
            .background(if (f) Color.White else if (selected) Color(0xFF1E6FD9) else Color(0xFF232A36))
            .then(if (f) Modifier.border(1.dp, ACCENT, RoundedCornerShape(3.dp)) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { if (onLong == null) false else okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (f) P_DARK_TXT else Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun GBlock(
    p: Prog?, x: Dp, w: Dp, nowMs: Long, req: FocusRequester?, mark: String,
    onSel: () -> Unit, onPlay: () -> Unit, onLong: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val live = p != null && p.start <= nowMs && nowMs < p.stop
    val past = p != null && p.stop <= nowMs
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight().padding(5.dp)
            .zIndex(if (f) 1f else 0f)
            .graphicsLayer {
                if (f && size.width > 0f && size.height > 0f) {
                    val grow = 10.dp.toPx()
                    scaleX = (size.width + grow) / size.width
                    scaleY = (size.height + grow) / size.height
                }
            }
            .then(if (f) Modifier.shadow(12.dp, RoundedCornerShape(3.dp)) else Modifier)
            .clip(RoundedCornerShape(3.dp))
            .background(if (f) Color.White else if (live) Color(0xFF2F3B48) else Color(0xFF262626))
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onSel() }
            .onPreviewKeyEvent { okLong(it, fired, onPlay, onLong) }
            .clickable { onPlay() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                (if (mark.isNotEmpty()) "$mark " else "") + (p?.title ?: "No information"),
                color = if (f) Color.Black else if (past) M_GRAY else Color.White,
                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                overflow = if (f && w >= 160.dp) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (f && w >= 160.dp) Modifier.basicMarquee() else Modifier
            )
            if (p != null && w >= 110.dp) {
                Text(
                    hm(p.start) + " - " + hm(p.stop),
                    color = if (f) Color(0xFF333333) else M_GRAY,
                    fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun DetailPane(selProvider: () -> Pair<Channel, Prog?>?, nowMs: Long) {
    val sp = selProvider()
        Column(
            Modifier.width(260.dp).fillMaxHeight().padding(start = 10.dp, top = 5.dp, end = 8.dp, bottom = 5.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color(0xFF262626)).padding(16.dp)
        ) {
            if (sp != null) {
                val sch = sp.first
                val pr = sp.second
                if (pr != null && pr.icon.isNotBlank()) {
                    AsyncImage(
                        model = pr.icon, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(sch.name, color = M_GRAY, fontSize = 13.sp, maxLines = 1)
                Text(
                    pr?.title ?: "No information", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                if (pr != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("${hm(pr.start)} - ${hm(pr.stop)}", color = Color(0xFF8FCBF7), fontSize = 13.sp)
                        if (pr.start <= nowMs && nowMs < pr.stop) {
                            Bar(fracOf(pr, nowMs), Modifier.padding(start = 10.dp).weight(1f), 8.dp)
                        }
                    }
                    if (pr.sub.isNotBlank() && pr.sub != pr.desc) {
                        Text(pr.sub, color = Color(0xFFFFE9A8), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        pr.desc, color = Color(0xFFD5DAE3), fontSize = 12.sp, lineHeight = 17.sp,
                        maxLines = 10, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
}


@Composable
fun EpgGrid(
    chans: List<Channel>, epg: Epg, current: Channel?, showNums: Boolean,
    recs: List<Rec>, remOn: Boolean,
    onPlay: (Channel) -> Unit, onDvr: (Channel, Prog, String) -> Unit, onEdit: (Channel) -> Unit, onEditCat: (String) -> Unit, onBack: () -> Unit
) {
    BackHandler { onBack() }
    var dvrFor by remember { mutableStateOf<Pair<Channel, Prog>?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            nowMs = System.currentTimeMillis()
        }
    }
    val slot = 30 * 60000L
    val winStart = remember { (System.currentTimeMillis() / 60000L) * 60000L }
    val winEnd = winStart + TOTAL_MIN * 60000L
    var cat by remember { mutableStateOf(GuideState.cat) }
    LaunchedEffect(cat) { GuideState.cat = cat }
    val cats = remember(chans) { chans.map { it.cat }.distinct() }
    if (cat.isNotEmpty() && cat !in cats) cat = ""
    val rows = remember(chans, cat) { if (cat.isEmpty()) chans else chans.filter { it.cat == cat } }
    val numOf = remember(chans) { chans.withIndex().associate { it.value.id to it.index + 1 } }
    val hs = rememberScrollState()
    var vw by remember { mutableStateOf(0) }
    val startIdx = rows.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (startIdx - 3).coerceAtLeast(0))
    var sel by remember {
        mutableStateOf<Pair<Channel, Prog?>?>(if (current != null) Pair(current, epg.now(current.epgId)) else null)
    }
    // Garde toujours 7 chaînes entières : la liste s'aligne sur la chaîne sélectionnée.
    val rowsNow by rememberUpdatedState(rows)
    // [0] = centre (px) of the selected tile, [1] = centre kept while moving Up/Down (-1 = none)
    val mids = remember { floatArrayOf(-1f, -1f) }
    LaunchedEffect(Unit) {
        snapshotFlow { sel?.first?.id }.collect { id ->
            if (id != null) {
                val idx = rowsNow.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val first = ls.firstVisibleItemIndex
                    val target = when {
                        idx < first -> idx
                        idx > first + 6 -> idx - 6
                        else -> first
                    }
                    if (target != first || ls.firstVisibleItemScrollOffset != 0) {
                        try { ls.scrollToItem(target) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex }
                    }
                }
            }
        }
    }
    var wrapId by remember { mutableStateOf<Int?>(null) }
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        try { req.requestFocus() } catch (ex: Exception) { }
    }
    val nowX = ((nowMs - winStart) / 60000f * DPM).dp

    Column(
        Modifier.fillMaxSize().background(Color(0xE0141414))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today - " + SimpleDateFormat("EEE, MMM dd", Locale.ENGLISH).format(Date(nowMs)),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(24.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
            ) {
                item { GChip("All", cat.isEmpty()) { cat = "" } }
                items(cats) { c2 -> GChip(c2, cat == c2, { onEditCat(c2) }) { cat = c2 } }
            }
        }
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            Spacer(Modifier.width(LW))
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { vw = it.width - with(density) { 260.dp.toPx().toInt() } }.horizontalScroll(hs, enabled = false)) {
                Box(Modifier.width((TOTAL_MIN * DPM).dp).fillMaxHeight()) {
                    Text(
                        "ON NOW", color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    )
                    val first = ((winStart + slot - 1) / slot) * slot
                    for (k in 0 until TOTAL_MIN / 30) {
                        val t = first + k * slot
                        if (t < winEnd && t - winStart >= 22 * 60000L) {
                            Text(
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t)),
                                color = M_GRAY, fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.CenterStart)
                                    .offset(x = ((t - winStart) / 60000f * DPM).dp)
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().onPreviewKeyEvent { ev ->
                        // Boucle : Bas sur la dernière chaîne -> première. (Haut sur la première mène aux catégories.)
                        val id = sel?.first?.id
                        if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionDown || ev.key == Key.DirectionUp) && mids[0] >= 0f) {
                            mids[1] = mids[0].coerceIn(hs.value.toFloat(), (hs.value + vw).toFloat())
                        }
                        if (ev.type == KeyEventType.KeyDown && rows.size > 1 && id != null &&
                            ev.key == Key.DirectionDown && id == rows.last().id
                        ) {
                            scope.launch {
                                try { ls.scrollToItem(0) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                                wrapId = rows.first().id
                            }
                            true
                        } else {
                            false
                        }
                    }
                ) {
                    val rowH = maxHeight / 7
                    LazyColumn(state = ls, modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { it.id }) { ch ->
                            val isCur = ch.id == current?.id
                            val rawProgs = epg.progs[ch.epgId]
                            val progs = remember(rawProgs, winStart) { (rawProgs ?: emptyList()).filter { it.stop > winStart && it.start < winEnd } }
                            val rowReq = remember { FocusRequester() }
                            val rr = if (isCur) req else rowReq
                            LaunchedEffect(wrapId) {
                                if (wrapId == ch.id) {
                                    delay(80)
                                    try { rr.requestFocus() } catch (ex: Exception) { }
                                    wrapId = null
                                }
                            }
                            val tgt = progs.indexOfFirst { it.start <= nowMs && nowMs < it.stop }.coerceAtLeast(0)
                            val preqs = remember(progs.size) { List(progs.size) { FocusRequester() } }
                            Row(Modifier.fillMaxWidth().height(rowH)) {
                                Box(
                                    Modifier.width(LW).fillMaxHeight().padding(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Brush.verticalGradient(listOf(Color(0xFF4B5568), Color(0xFF333B49)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (ch.icon.isNotBlank()) {
                                        AsyncImage(
                                            model = ch.icon, contentDescription = null,
                                            modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit,
                                            alignment = Alignment.Center
                                        )
                                    } else {
                                        Text(
                                            ch.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .focusProperties {
                                            enter = {
                                                val c = mids[1]
                                                if (c < 0f || progs.isEmpty()) {
                                                    rr
                                                } else {
                                                    var best = tgt
                                                    var bd = Float.MAX_VALUE
                                                    for ((qi, q) in progs.withIndex()) {
                                                        val qs = maxOf(q.start, winStart)
                                                        val qe = minOf(q.stop, winEnd)
                                                        val x0 = with(density) { ((qs - winStart) / 60000f * DPM).dp.toPx() }
                                                        val x1 = with(density) { ((qe - winStart) / 60000f * DPM).dp.toPx() }.coerceAtLeast(x0 + 1f)
                                                        val d0 = if (c < x0) x0 - c else if (c > x1) c - x1 else 0f
                                                        if (d0 < bd) { bd = d0; best = qi }
                                                    }
                                                    if (best == tgt) rr else preqs[best]
                                                }
                                            }
                                        }
                                        .focusGroup()
                                        .horizontalScroll(hs)
                                ) {
                                    Box(Modifier.width((TOTAL_MIN * DPM).dp).fillMaxHeight()) {
                                        if (progs.isEmpty()) {
                                            GBlock(
                                                null, 0.dp, (TOTAL_MIN * DPM).dp, nowMs, rr, "",
                                                { sel = Pair(ch, null); mids[0] = -1f; mids[1] = -1f }, { onPlay(ch) }, { onEdit(ch) }
                                            )
                                        }
                                        for ((pi, p) in progs.withIndex()) {
                                            val s = maxOf(p.start, winStart)
                                            val e = minOf(p.stop, winEnd)
                                            val x = ((s - winStart) / 60000f * DPM).dp
                                            val w = ((e - s) / 60000f * DPM).dp.coerceAtLeast(24.dp)
                                            val live = p.start <= nowMs && nowMs < p.stop
                                            val useReq = pi == tgt
                                            val xPx = with(density) { x.toPx().toInt() }
                                            val ePx = with(density) { (x + w).toPx().toInt() }
                                            val mk = recs.firstOrNull {
                                                it.chId == ch.id && it.start == p.start &&
                                                    (it.state == "scheduled" || it.state == "recording")
                                            }?.let { if (it.kind == "rec") "●" else "⏰" } ?: ""
                                            GBlock(
                                                p, x, w, nowMs, if (useReq) rr else preqs[pi], mk,
                                                {
                                                    sel = Pair(ch, p)
                                                    mids[0] = (xPx + ePx) / 2f
                                                    mids[1] = -1f
                                                    val vs = hs.value
                                                    if (xPx < vs || xPx > vs + (vw * 0.7f).toInt() || ePx < vs + 80) {
                                                        scope.launch { hs.animateScrollTo((xPx - 40).coerceAtLeast(0)) }
                                                    }
                                                },
                                                { if (p.start > nowMs) dvrFor = Pair(ch, p) else onPlay(ch) },
                                                { onEdit(ch) }
                                            )
                                        }
                                        if (nowX > 0.dp) {
                                            Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight().background(Color(0xFF8FCBF7)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            DetailPane({ sel }, nowMs)
        }
    }
    val d = dvrFor
    if (d != null) {
        val dch = d.first
        val dp = d.second
        val recId = "${dch.id}_${dp.start}_rec"
        val remId = "${dch.id}_${dp.start}_rem"
        val hasRec = recs.any { it.id == recId && (it.state == "scheduled" || it.state == "recording") }
        val hasRem = recs.any { it.id == remId && it.state == "scheduled" }
        val live = dp.start <= nowMs && nowMs < dp.stop
        PanelDialog(dp.title, { dvrFor = null }, actions = { PButton("Close", { dvrFor = null }) }) {
            item { PInfo(dch.name + "  ·  " + hm(dp.start) + " - " + hm(dp.stop)) }
            if (live) {
                item { PItem("Watch now", "", { dvrFor = null; onPlay(dch) }, autoFocus = true) }
            }
            item {
                PItem(
                    if (hasRec) "Cancel recording" else "Record",
                    if (hasRec) "" else if (live) "Starts now" else "Recorded automatically at start time",
                    { onDvr(dch, dp, if (hasRec) "unrec" else "rec"); dvrFor = null },
                    autoFocus = !live
                )
            }
            if (remOn && !live) {
                item {
                    PItem(
                        if (hasRem) "Remove reminder" else "Set reminder", "A notice appears when it starts",
                        { onDvr(dch, dp, if (hasRem) "unrem" else "rem"); dvrFor = null }
                    )
                }
            }
            if (!live) item { PItem("Watch channel", "", { dvrFor = null; onPlay(dch) }) }
        }
    }
}
