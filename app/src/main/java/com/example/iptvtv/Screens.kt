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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import java.util.Locale

private val GRAY = Color(0xFFB0B8C8)
private val CYAN = Color(0xFF1EB6E8)
private val BLUE = Color(0xFF8AB4F8)
private val TITLE = Color(0xFFFFD166)
private val GOLD = Color(0xFFE0B85A)

val LocalRowFocused = compositionLocalOf { false }

// Couleur d'un texte dans une ligne : devient sombre quand la barre de sélection blanche est active.
@Composable
fun fcolor(normal: Color, focused: Color = Color(0xFF14213D)): Color =
    (Th.light && LocalThemed.current).let { lt ->
        if (LocalRowFocused.current) Th.mapFocused(focused, lt) else Th.mapNormal(normal, lt)
    }

@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onLong: (() -> Unit)? = null,
    pad: androidx.compose.ui.unit.Dp = 10.dp,
    corner: androidx.compose.ui.unit.Dp = 8.dp,
    tile: Boolean = false,
    tileColor: Color = Color.Unspecified,
    tileCorner: androidx.compose.ui.unit.Dp = 3.dp,
    tileBorder: Color = GOLD,
    tileVPad: androidx.compose.ui.unit.Dp = 4.dp,
    content: @Composable RowScope.() -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val lt = Th.light && LocalThemed.current
    val fbg = Th.focusBg
    val tcol = if (tileColor != Color.Unspecified) tileColor else Th.tile
    val fired = remember { booleanArrayOf(true) }
    val gk by animateFloatAsState(if (tile && f) 1f else 0f, label = "tileGrow")
    val base = if (!tile) {
        modifier.fillMaxWidth().clip(RoundedCornerShape(corner))
            .background(if (f) fbg else Color.Transparent)
            .then(if (f) Modifier.border(2.dp, GOLD, RoundedCornerShape(corner)) else Modifier)
    } else {
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = tileVPad)
            .zIndex(if (f) 1f else 0f)
            .graphicsLayer {
                if (size.width > 0f && size.height > 0f) {
                    val gx = 6.dp.toPx() * gk
                    val gy = 3.dp.toPx() * gk
                    scaleX = (size.width + 2 * gx) / size.width
                    scaleY = (size.height + 2 * gy) / size.height
                }
            }
            .then(if (f) Modifier.shadow(12.dp, RoundedCornerShape(tileCorner)) else Modifier)
            .clip(RoundedCornerShape(tileCorner))
            .background(if (f) fbg else tcol)
            .then(if (f && tileBorder != Color.Unspecified) Modifier.border(2.dp, tileBorder, RoundedCornerShape(tileCorner)) else Modifier)
    }
    Row(
        modifier = base
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { if (holdGuard(it)) true else if (onLong == null) false else okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }.padding(pad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalRowFocused provides f) { content() }
    }
}

@Composable
fun Bar(frac: Float, modifier: Modifier = Modifier, h: androidx.compose.ui.unit.Dp = 8.dp) {
    val shape = RoundedCornerShape(2.dp)
    val foc = LocalRowFocused.current
    Box(
        modifier.height(h.coerceAtMost(5.dp)).clip(shape)
            .background(if (foc || Th.light) Color(0x33000000) else Color(0x33FFFFFF))
    ) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Th.sel))
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
// Texte long : défile automatiquement de haut en bas, puis revient au début.
// Fine barre de progression dorée (liste des chaînes).
@Composable
fun GoldBar(frac: Float, modifier: Modifier = Modifier, h: androidx.compose.ui.unit.Dp = 4.dp, onDark: Boolean = false) {
    val foc = LocalRowFocused.current && !onDark
    Box(modifier.height(h).clip(RoundedCornerShape(2.dp)).background(if (foc) Color(0x33000000) else Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(GOLD))
    }
}

fun leftText(p: Prog, nowMs: Long): String {
    val m = ((p.stop - nowMs) / 60000).toInt().coerceAtLeast(0)
    return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
}

@Composable
fun ColTitle(t: String) {
    Text(
        t.uppercase(), color = Th.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)
    )
}

@Composable
fun AutoScrollText(
    text: String, modifier: Modifier = Modifier,
    fontSz: androidx.compose.ui.unit.TextUnit = 14.sp, line: androidx.compose.ui.unit.TextUnit = 20.sp,
    speed: Float = 9f
) {
    val st = rememberScrollState()
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(text) {
        st.scrollTo(0)
        delay(4000)
        while (true) {
            val max = st.maxValue
            if (max > 0) {
                // vitesse lente et constante (dp par seconde)
                val ms = (max / (speed * dens) * 1000f).toInt().coerceAtLeast(3000)
                try {
                    st.animateScrollTo(max, tween(durationMillis = ms, easing = LinearEasing))
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                delay(4000)
                st.scrollTo(0)
            }
            delay(4000)
        }
    }
    // On n'affiche que des lignes entières : plus de ligne coupée au bord de la zone.
    BoxWithConstraints(modifier) {
        val lineDp = with(androidx.compose.ui.platform.LocalDensity.current) { line.toDp() }
        val n = (maxHeight.value / lineDp.value).toInt()
        val h = if (n >= 1) lineDp * n else maxHeight
        Box(
            Modifier.fillMaxWidth().height(h).clipToBounds()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Petit fondu pendant le défilement
                    val f = (8.dp.toPx() / this.size.height).coerceIn(0.01f, 0.3f)
                    val top = if (st.value > 0) f else 0f
                    drawRect(
                        Brush.verticalGradient(
                            0f to (if (top > 0f) Color.Transparent else Color.Black),
                            (if (top > 0f) top else 0.001f) to Color.Black,
                            (1f - f) to Color.Black,
                            1f to (if (st.value < st.maxValue) Color.Transparent else Color.Black)
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
        ) {
            Text(
                text, color = Color(0xFFD5DAE3), fontSize = fontSz, lineHeight = line,
                modifier = Modifier.fillMaxWidth().verticalScroll(st)
            )
        }
    }
}

@Composable
fun PanelHeader(t: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(3.dp)).background(Th.headerBox).padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(t, color = Th.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChannelPanel(
    all: List<Channel>, sources: List<Source>, current: Channel, epg: Epg, nowMs: Long, focusId: Int, refocus: Int,
    showNums: Boolean, onPick: (Channel) -> Unit, onEdit: (Channel) -> Unit,
    onEditCat: (String) -> Unit = {},
    recs: List<Rec> = emptyList(), remOn: Boolean = false,
    onDvr: (Channel, Prog, String) -> Unit = { _, _, _ -> },
    onClose: () -> Unit = {}
) {
    var dvrFor by remember { mutableStateOf<Pair<Channel, Prog>?>(null) }
    var selSrc by remember { mutableStateOf(current.src) }
    var selCat by remember { mutableStateOf(current.cat) }
    var cols by remember { mutableStateOf(false) }
    var goList by remember { mutableStateOf(0) }
    val catFr = remember { FocusRequester() }
    val srcIds = remember(all) { all.map { it.src }.distinct() }
    val srcCats = all.filter { it.src == selSrc }.map { it.cat }.distinct()
    if (selCat !in srcCats && srcCats.isNotEmpty()) selCat = srcCats.first()
    val list = all.filter { it.src == selSrc && it.cat == selCat }
    var focused by remember { mutableStateOf(current) }
    var wrapTo by remember { mutableStateOf<Int?>(null) }
    val wrapReq = remember { FocusRequester() }
    val wrapScope = rememberCoroutineScope()
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
    LaunchedEffect(wrapTo) {
        if (wrapTo != null) {
            delay(80)
            try { wrapReq.requestFocus() } catch (ex: Exception) { }
            wrapTo = null
        }
    }
    LaunchedEffect(goList) {
        if (goList > 0) {
            delay(150)
            try { fr.requestFocus() } catch (ex: Exception) { }
        }
    }
    // La barre de sélection reste vers le milieu (5e ligne) et c'est la liste qui défile ;
    // elle ne descend jusqu'en bas que pour les dernières chaînes.
    val listNow by rememberUpdatedState(list)
    LaunchedEffect(Unit) {
        snapshotFlow { focused.id }.collect { id ->
            val i = listNow.indexOfFirst { it.id == id }
            if (i >= 0) {
                val target = (i - 3).coerceIn(0, (listNow.size - 8).coerceAtLeast(0))
                if (target != ls.firstVisibleItemIndex || ls.firstVisibleItemScrollOffset != 0) {
                    try { ls.scrollToItem(target) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                }
            }
        }
    }
    var firstSel by remember { mutableStateOf(true) }
    LaunchedEffect(selSrc, selCat) {
        if (firstSel) firstSel = false else ls.scrollToItem(0)
    }
    Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
            val rc = ev.nativeKeyEvent.repeatCount
            if (holdGuard(ev)) {
                true
            } else if (ev.key == Key.Back || ev.key == Key.Escape) {
                if (ev.type == KeyEventType.KeyUp) onClose()
                true
            } else if (ev.type == KeyEventType.KeyDown && !cols && upIdx < 0 && list.size > 1 &&
                ((ev.key == Key.DirectionUp && focused.id == list.first().id) ||
                    (ev.key == Key.DirectionDown && focused.id == list.last().id))
            ) {
                // Boucle : première chaîne -> dernière, et inversement.
                val toLast = ev.key == Key.DirectionUp
                wrapScope.launch {
                    try { ls.scrollToItem(if (toLast) (list.size - 8).coerceAtLeast(0) else 0) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
                    wrapTo = if (toLast) list.last().id else list.first().id
                }
                true
            } else if (upIdx >= 0 && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                if (ev.type == KeyEventType.KeyUp && upIdx in ups.indices) dvrFor = Pair(fc, ups[upIdx])
                true
            } else if (ev.type != KeyEventType.KeyDown) {
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
            } else if (ev.key == Key.DirectionRight && rc == 0 && ups.isNotEmpty() && !cols) {
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
            if (srcIds.size > 1) Column(Modifier.width(200.dp).fillMaxHeight().background(Th.panel3)) {
                ColTitle("Sources")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val rowH2 = maxHeight / 8
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(srcIds) { sid ->
                            val nm = sources.firstOrNull { it.id == sid || it.name == sid }?.name ?: "Source"
                            FocusRow({ selSrc = sid }, Modifier.height(rowH2), {
                                selSrc = sid
                                if (!all.any { it.src == sid && it.cat == selCat }) selCat = all.first { it.src == sid }.cat
                            }, tile = true, tileCorner = 10.dp, tileVPad = 3.dp) {
                                Text(nm, color = fcolor(if (sid == selSrc) Color.White else GRAY), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(260.dp).fillMaxHeight().background(Th.panel2)) {
                ColTitle("Categories")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val rowH2 = maxHeight / 8
                    val catState = rememberLazyListState(initialFirstVisibleItemIndex = (srcCats.indexOf(selCat) - 3).coerceAtLeast(0))
                    LazyColumn(state = catState, modifier = Modifier.fillMaxSize()) {
                        items(srcCats) { cat ->
                            FocusRow(
                                {
                                    cols = false
                                    goList++
                                },
                                (if (cat == selCat) Modifier.focusRequester(catFr) else Modifier).height(rowH2),
                                { selCat = cat },
                                { onEditCat(cat) },
                                tile = true, tileCorner = 10.dp, tileVPad = 3.dp
                            ) {
                                Text(cat, color = fcolor(if (cat == selCat) Color.White else GRAY), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.width(450.dp).fillMaxHeight().background(Th.panel)) {
            Text(
                selCat.uppercase(), color = Th.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val rowH = maxHeight / 8
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { c ->
                        val pr = epg.now(c.epgId)
                        FocusRow(
                            { onPick(c) },
                            (if (c.id == wrapTo) Modifier.focusRequester(wrapReq) else if (c.id == fid) Modifier.focusRequester(fr) else Modifier).height(rowH),
                            { focused = c; upIdx = -1; cols = false },
                            { onEdit(c) },
                            0.dp, 0.dp, tile = true,
                            tileColor = Th.tile, tileCorner = 10.dp, tileBorder = GOLD, tileVPad = 3.dp
                        ) {
                            val rf = LocalRowFocused.current
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showNums) {
                                    Text(
                                        "${all.indexOf(c) + 1}", color = fcolor(GRAY, Color(0xFF45557A)), fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1,
                                        modifier = Modifier.width(40.dp)
                                    )
                                }
                                Box(Modifier.size(56.dp, 34.dp)) {
                                    if (c.icon.isNotBlank()) {
                                        AsyncImage(
                                            model = c.icon, contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Row(Modifier.height(19.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            c.name, color = fcolor(Color.White), fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                                            maxLines = 1, softWrap = false,
                                            overflow = if (rf) TextOverflow.Clip else TextOverflow.Ellipsis,
                                            modifier = (if (rf) Modifier.basicMarquee() else Modifier).weight(1f, fill = false)
                                        )
                                        if (c.id == current.id) {
                                            Spacer(Modifier.width(8.dp))
                                            Row(
                                                Modifier.height(15.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33E0B85A))
                                                    .padding(horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(Modifier.height(9.dp), verticalAlignment = Alignment.Bottom) {
                                                    Box(Modifier.width(2.dp).height(5.dp).background(if (rf) Color(0xFF8A6100) else GOLD))
                                                    Spacer(Modifier.width(1.dp))
                                                    Box(Modifier.width(2.dp).height(9.dp).background(if (rf) Color(0xFF8A6100) else GOLD))
                                                    Spacer(Modifier.width(1.dp))
                                                    Box(Modifier.width(2.dp).height(7.dp).background(if (rf) Color(0xFF8A6100) else GOLD))
                                                }
                                                Text(
                                                    " PLAYING", color = if (rf) Color(0xFF8A6100) else GOLD, fontSize = 10.sp, lineHeight = 12.sp,
                                                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        pr?.title ?: "", color = fcolor(GRAY, Color(0xFF45557A)), fontSize = 13.sp, lineHeight = 16.sp,
                                        maxLines = 1, softWrap = false,
                                        overflow = if (rf) TextOverflow.Clip else TextOverflow.Ellipsis,
                                        modifier = if (rf) Modifier.basicMarquee() else Modifier
                                    )
                                    if (pr != null) {
                                        Row(Modifier.fillMaxWidth().height(13.dp).padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                                            GoldBar(fracOf(pr, nowMs), Modifier.weight(1f), 4.dp)
                                            Text(
                                                leftText(pr, nowMs), color = fcolor(GRAY, Color(0xFF45557A)), fontSize = 11.sp, lineHeight = 12.sp,
                                                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!cols) Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xE6000000))) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 110.dp)) {
                if (fc.icon.isNotBlank()) {
                    AsyncImage(
                        model = fc.icon, contentDescription = null,
                        modifier = Modifier.size(72.dp, 48.dp), contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                    Spacer(Modifier.width(14.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (shownP == null) {
                    Text("No guide information", color = GRAY, fontSize = 16.sp)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(if (upIdx < 0) Color(0xFFE5484D) else Color(0x33E0B85A))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (upIdx < 0) {
                                        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        if (upIdx < 0) "ON NOW" else "UP NEXT", color = if (upIdx < 0) Color.White else GOLD,
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1
                                    )
                                }
                                Text(
                                    shownP.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    Text(
                                        "${hm(shownP.start)} - ${hm(shownP.stop)}",
                                        color = Color(0xFFDDE2EA), fontSize = 14.sp, maxLines = 1, softWrap = false
                                    )
                                    if (upIdx < 0) {
                                        GoldBar(fracOf(shownP, nowMs), Modifier.padding(start = 12.dp).weight(1f), 4.dp, true)
                                        Text(
                                            leftText(shownP, nowMs) + " left", color = GRAY, fontSize = 13.sp,
                                            maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp)
                                        )
                                    }
                                }
                                if (shownP.sub.isNotBlank() && shownP.sub != shownP.desc) {
                                    Text(shownP.sub, color = Color(0xFFFFE9A8), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (shownP.cat.isNotBlank()) {
                                    Text(shownP.cat, color = GRAY, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
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
                        AutoScrollText(
                            shownP.desc.ifBlank { shownP.sub },
                            Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)
                        )
                    }
                }
            }
            if (ups.isNotEmpty()) {
                val browsing = upIdx >= 0
                val boxScale by animateFloatAsState(if (browsing) 1.03f else 1f, label = "upBox")
                Column(
                    Modifier.fillMaxWidth().graphicsLayer { scaleX = boxScale; scaleY = boxScale }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (browsing) Color(0xF2101010) else Color(0xCC0C0C0C))
                        .then(if (browsing) Modifier.border(1.5.dp, Color(0x99FFFFFF), RoundedCornerShape(10.dp)) else Modifier)
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Up next", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "OK ON A SHOW · REMIND OR RECORD", color = GRAY, fontSize = 10.sp, letterSpacing = 1.sp,
                            maxLines = 1, softWrap = false
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ups.forEachIndexed { i, u ->
                            val sel = i == upIdx
                            val chipScale by animateFloatAsState(if (sel) 1.07f else 1f, label = "chip")
                            Column(
                                Modifier.weight(1f).graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) Color.White else Color.Transparent)
                                    .then(if (sel) Modifier.border(2.dp, GOLD, RoundedCornerShape(6.dp)) else Modifier).padding(4.dp)
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
                                    hm(u.start), color = if (sel) Color.Black else GOLD,
                                    fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1
                                )
                                Text(
                                    u.title, color = if (sel) Color(0xFF222222) else GRAY, fontSize = 13.sp, lineHeight = 17.sp,
                                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(4 - ups.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
            Text(hm(nowMs), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp))
        }
        val dv = dvrFor
        if (dv != null) {
            val dch = dv.first
            val dp = dv.second
            val recId = "${dch.id}_${dp.start}_rec"
            val remId = "${dch.id}_${dp.start}_rem"
            val hasRec = recs.any { it.id == recId && (it.state == "scheduled" || it.state == "recording") }
            val hasRem = recs.any { it.id == remId && it.state == "scheduled" }
            val closeDvr: () -> Unit = { dvrFor = null; goList += 1 }
            PanelDialog(dp.title, closeDvr, actions = { PButton("Close", closeDvr) }) {
                item { PInfo(dch.name + "  ·  " + hm(dp.start) + " - " + hm(dp.stop)) }
                item {
                    PItem(
                        if (hasRec) "Cancel recording" else "Record",
                        if (hasRec) "" else "Recorded automatically at start time",
                        { onDvr(dch, dp, if (hasRec) "unrec" else "rec"); closeDvr() },
                        autoFocus = true
                    )
                }
                if (remOn) {
                    item {
                        PItem(
                            if (hasRem) "Remove reminder" else "Set reminder", "A notice appears when it starts",
                            { onDvr(dch, dp, if (hasRem) "unrem" else "rem"); closeDvr() }
                        )
                    }
                }
                item { PItem("Watch channel", "", { dvrFor = null; onPick(dch) }) }
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
    val base = if (parts.isEmpty()) "Track $n" else parts.joinToString(" · ")
    return if (f.channelCount > 0) "$base (${f.channelCount} ch)" else base
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
        Text("Search", color = Th.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
