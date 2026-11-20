@file:OptIn(ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
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
    thumb: @Composable BoxScope.() -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.06f else 1f, label = "miniItem")
    Column(
        Modifier.width(170.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(M_TILE)
                .then(if (f) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
        ) {
            thumb()
            if (frac != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color(0xFF42A5F5)))
                }
            }
        }
        Text(
            title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = (if (f) Modifier.basicMarquee() else Modifier).padding(top = 6.dp)
        )
        Text(sub, color = M_GRAY, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MiniPlayer(
    channels: List<Channel>, c: Channel, epg: Epg, nowMs: Long,
    onPick: (Channel) -> Unit, onGuide: () -> Unit, onKey: () -> Unit
) {
    val p = epg.now(c.epgId)
    val n = channels.size
    val i0 = channels.indexOfFirst { it.id == c.id }.coerceAtLeast(0)
    val order = remember(channels, c.id) { (0 until n).map { (i0 + it) % n } }
    val gr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try { gr.requestFocus() } catch (ex: Exception) { }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { onKey(); false }) {
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
            if (p != null) {
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(fracOf(p, nowMs)).fillMaxHeight().background(Color.White))
                }
            }
            Text(
                "Channels", color = Color(0xFFFFE9A8), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    MiniItem(onGuide, gr, "Program guide", "", null) {
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
                items(order, key = { channels[it].id }) { idx ->
                    val ch = channels[idx]
                    val pr = epg.now(ch.epgId)
                    MiniItem(
                        { onPick(ch) }, null,
                        pr?.title ?: ch.name, "${idx + 1} - ${ch.name.uppercase()}",
                        if (pr != null) fracOf(pr, nowMs) else null
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
    }
}

// ---------------------------------------------------------------------------
// Guide graphique (grille horaire)
// ---------------------------------------------------------------------------
private const val DPM = 4
private const val TOTAL_MIN = 600
private val CH_W = 150.dp
private val ROW_H = 64.dp
private val G_BG = Color(0xFF0E1218)
private val G_CELL = Color(0xFF171C25)

@Composable
private fun GChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (f) Color.White else if (selected) Color(0xFF1E6FD9) else Color(0xFF232A36))
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (f) Color(0xFF14213D) else Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun GBlock(
    p: Prog?, x: Dp, w: Dp, nowMs: Long, req: FocusRequester?,
    onSel: () -> Unit, onPlay: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val live = p != null && p.start <= nowMs && nowMs < p.stop
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight().padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (f) Color.White else if (live) Color(0xFF283A52) else Color(0xFF1B222C))
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onSel() }
            .clickable { onPlay() }
            .padding(horizontal = 8.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text(
                p?.title ?: "No information", color = if (f) Color(0xFF14213D) else Color.White,
                fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (f) Modifier.basicMarquee() else Modifier
            )
            val sub = p?.sub ?: ""
            if (sub.isNotBlank()) {
                Text(
                    sub, color = if (f) Color(0xFF45557A) else M_GRAY, fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EpgGrid(
    chans: List<Channel>, epg: Epg, current: Channel?, showNums: Boolean,
    onPlay: (Channel) -> Unit, onBack: () -> Unit
) {
    BackHandler { onBack() }
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
    val winStart = remember { (System.currentTimeMillis() / slot) * slot - slot }
    val winEnd = winStart + TOTAL_MIN * 60000L
    var cat by remember { mutableStateOf("") }
    val cats = remember(chans) { chans.map { it.cat }.distinct() }
    val rows = remember(chans, cat) { if (cat.isEmpty()) chans else chans.filter { it.cat == cat } }
    val numOf = remember(chans) { chans.withIndex().associate { it.value.id to it.index + 1 } }
    val hs = rememberScrollState()
    var vw by remember { mutableStateOf(0) }
    val startIdx = rows.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val ls = rememberLazyListState(initialFirstVisibleItemIndex = (startIdx - 2).coerceAtLeast(0))
    var sel by remember { mutableStateOf<Pair<Channel, Prog?>?>(null) }
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        try { req.requestFocus() } catch (ex: Exception) { }
    }
    val nowX = ((nowMs - winStart) / 60000f * DPM).dp

    Column(Modifier.fillMaxSize().background(G_BG)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today - " + SimpleDateFormat("EEE, MMM dd", Locale.ENGLISH).format(Date(nowMs)),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(24.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { GChip("All", cat.isEmpty()) { cat = "" } }
                items(cats) { c2 -> GChip(c2, cat == c2) { cat = c2 } }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(28.dp)) {
                    Spacer(Modifier.width(CH_W))
                    Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { vw = it.width }.horizontalScroll(hs, enabled = false)) {
                        Row {
                            for (i in 0 until TOTAL_MIN / 30) {
                                Box(Modifier.width((30 * DPM).dp).fillMaxHeight()) {
                                    Text(
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(winStart + i * slot)),
                                        color = M_GRAY, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.id }) { ch ->
                        val isCur = ch.id == current?.id
                        val progs = (epg.progs[ch.epgId] ?: emptyList()).filter { it.stop > winStart && it.start < winEnd }
                        var reqUsed = false
                        Row(Modifier.fillMaxWidth().height(ROW_H)) {
                            Row(
                                Modifier.width(CH_W).fillMaxHeight().padding(vertical = 1.dp).background(G_CELL),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showNums) {
                                    Text(
                                        "${numOf[ch.id] ?: ""}", color = M_GRAY, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(40.dp).padding(start = 8.dp), maxLines = 1
                                    )
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (ch.icon.isNotBlank()) {
                                    AsyncImage(
                                        model = ch.icon, contentDescription = null,
                                        modifier = Modifier.size(80.dp, 44.dp), contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(ch.name, color = Color.White, fontSize = 13.sp, maxLines = 2, modifier = Modifier.width(96.dp))
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hs)) {
                                Box(Modifier.width((TOTAL_MIN * DPM).dp).fillMaxHeight()) {
                                    if (progs.isEmpty()) {
                                        GBlock(
                                            null, 0.dp, (TOTAL_MIN * DPM).dp, nowMs, if (isCur) req else null,
                                            { sel = Pair(ch, null) }, { onPlay(ch) }
                                        )
                                    }
                                    for (p in progs) {
                                        val s = maxOf(p.start, winStart)
                                        val e = minOf(p.stop, winEnd)
                                        val x = ((s - winStart) / 60000f * DPM).dp
                                        val w = (((e - s) / 60000f * DPM) - 0f).dp.coerceAtLeast(24.dp)
                                        val live = p.start <= nowMs && nowMs < p.stop
                                        val useReq = isCur && live && !reqUsed
                                        if (useReq) reqUsed = true
                                        val xPx = with(density) { x.toPx().toInt() }
                                        GBlock(
                                            p, x, w, nowMs, if (useReq) req else null,
                                            {
                                                sel = Pair(ch, p)
                                                val vs = hs.value
                                                if (xPx < vs || xPx > vs + (vw * 0.7f).toInt()) {
                                                    scope.launch { hs.animateScrollTo((xPx - 40).coerceAtLeast(0)) }
                                                }
                                            },
                                            { onPlay(ch) }
                                        )
                                    }
                                    if (nowX > 0.dp) {
                                        Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight().background(Color(0xFFE53935)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val sp = sel
            Column(
                Modifier.width(250.dp).fillMaxHeight().background(Color(0xFF12171F)).padding(16.dp)
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
                        Text("${hm(pr.start)} - ${hm(pr.stop)}", color = Color(0xFF8FCBF7), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
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
    }
}
