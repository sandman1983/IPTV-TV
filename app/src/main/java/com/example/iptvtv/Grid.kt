@file:OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.layout.layout
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
private val M_GRAY: Color get() = Th.sub
private val M_TILE: Color get() = if (Th.black) Color.Black else Color(0xFF2B2F36)

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
    pv: androidx.media3.exoplayer.ExoPlayer? = null,
    pvReady: Boolean = false,
    onFocusState: (Boolean) -> Unit = {},
    tileBg: Color? = null,
    thumb: @Composable BoxScope.() -> Unit
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val sc by animateFloatAsState(if (f) 1.06f else 1f, label = "miniItem")
    Column(
        Modifier.width(156.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; onFocusState(it.isFocused); if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { if (onLong == null) false else okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        // The selection frame is drawn around the picture, so it never hides the progress bar.
        Box(
            Modifier.size(156.dp, 90.dp)
                .border(3.dp, if (f) Th.sel else Color.Transparent, RoundedCornerShape(12.dp)).padding(3.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(tileBg ?: M_TILE)) {
                thumb()
                if (pv != null && f) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { cx -> android.view.TextureView(cx).also { tv -> pv.setVideoTextureView(tv) } },
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (pvReady) 1f else 0f }
                    )
                }
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
            title, color = Th.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 3.dp, end = 8.dp).then(if (f) Modifier.basicMarquee() else Modifier)
        )
        val sx = sub.indexOf(" - ")
        Text(
            if (sx in 1..4 && sub.substring(0, sx).all { it.isDigit() })
                androidx.compose.ui.text.buildAnnotatedString {
                    append(sub)
                    addStyle(androidx.compose.ui.text.SpanStyle(color = Th.sel, fontWeight = FontWeight.Bold), 0, sx)
                }
            else androidx.compose.ui.text.AnnotatedString(sub),
            color = M_GRAY, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp)
        )
    }
}

private fun Modifier.downChevron(col: Color): Modifier = this.drawBehind {
    val st = 2.5.dp.toPx()
    drawLine(col, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width / 2f, size.height), st, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(col, androidx.compose.ui.geometry.Offset(size.width / 2f, size.height), androidx.compose.ui.geometry.Offset(size.width, 0f), st, androidx.compose.ui.graphics.StrokeCap.Round)
}

private fun Modifier.upChevron(col: Color): Modifier = this.drawBehind {
    val st = 2.5.dp.toPx()
    drawLine(col, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width / 2f, 0f), st, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(col, androidx.compose.ui.geometry.Offset(size.width / 2f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), st, androidx.compose.ui.graphics.StrokeCap.Round)
}

// Icônes dessinées : 0 pause, 1 lecture, 2 audio (haut-parleur), 3 sous-titres (CC)
@Composable
private fun CtrlIcon(kind: Int, col: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        val O = { x: Float, y: Float -> androidx.compose.ui.geometry.Offset(x, y) }
        val S = { x: Float, y: Float -> androidx.compose.ui.geometry.Size(x, y) }
        when (kind) {
            0 -> {
                drawRect(col, O(w * 0.18f, h * 0.1f), S(w * 0.24f, h * 0.8f))
                drawRect(col, O(w * 0.58f, h * 0.1f), S(w * 0.24f, h * 0.8f))
            }
            1 -> {
                val p = androidx.compose.ui.graphics.Path()
                p.moveTo(w * 0.22f, h * 0.08f); p.lineTo(w * 0.92f, h * 0.5f); p.lineTo(w * 0.22f, h * 0.92f); p.close()
                drawPath(p, col)
            }
            2 -> {
                val p = androidx.compose.ui.graphics.Path()
                p.moveTo(0f, h * 0.38f); p.lineTo(w * 0.22f, h * 0.38f); p.lineTo(w * 0.48f, h * 0.14f)
                p.lineTo(w * 0.48f, h * 0.86f); p.lineTo(w * 0.22f, h * 0.62f); p.lineTo(0f, h * 0.62f); p.close()
                drawPath(p, col)
                val st = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(col, -45f, 90f, false, O(w * 0.34f, h * 0.28f), S(w * 0.4f, h * 0.44f), style = st)
                drawArc(col, -50f, 100f, false, O(w * 0.26f, h * 0.1f), S(w * 0.68f, h * 0.8f), style = st)
            }
            else -> {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawRoundRect(col, O(sw / 2, h * 0.14f), S(w - sw, h * 0.72f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = st)
                drawArc(col, 40f, 280f, false, O(w * 0.2f, h * 0.34f), S(w * 0.26f, h * 0.32f), style = st)
                drawArc(col, 40f, 280f, false, O(w * 0.56f, h * 0.34f), S(w * 0.26f, h * 0.32f), style = st)
            }
        }
    }
}

// Bouton rond avec icône ; la sélection est pilotée par le clavier (état "sel"), pas par le focus Compose.
@Composable
private fun CtrlCircle(kind: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(Th.ovCtl)
            .then(if (selected) Modifier.background(if (Th.light) Color(0x14000000) else Color(0x33FFFFFF)) else Modifier)
            .then(if (selected) Modifier.border(2.dp, Th.sel, CircleShape) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CtrlIcon(kind, if (selected) Th.sel else Th.text)
    }
}

@Composable
fun MiniPlayer(
    channels: List<Channel>, c: Channel, epg: Epg, nowMs: Long, hist: List<Int>,
    isPlaying: () -> Boolean, onToggle: () -> Unit,
    onPick: (Channel) -> Unit, onGuide: () -> Unit, onKey: () -> Unit,
    onClearHist: (Int?) -> Unit = {},
    onTracks: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    DarkZone()
    val prev = rememberCardPreview(channels, c.id)
    var clearFor by remember { mutableStateOf<Channel?>(null) }
    val clr = remember { FocusRequester() }
    val p = epg.now(c.epgId)
    // Always the last 10 channels played (the current one first).
    // L'ordre est figé tant que le mini lecteur reste ouvert : choisir une chaîne ne la fait pas sauter en tête de rangée.
    // À la prochaine ouverture, la chaîne en cours repasse en premier.
    val recent = remember(channels) {
        // La chaîne en cours n'apparaît plus dans la rangée : elle est déjà affichée dans le bloc d'info.
        val l = ArrayList<Channel>()
        for (id in hist) {
            val ch = channels.firstOrNull { it.id == id }
            if (ch != null && ch.id != c.id && l.none { it.id == ch.id }) l.add(ch)
        }
        l.take(10)
    }
    var removed by remember { mutableStateOf(setOf<Int>()) }
    var removedAll by remember { mutableStateOf(false) }
    val shownRecent = if (removedAll) emptyList() else recent.filter { it.id !in removed && it.id != c.id }
    val gr = remember { FocusRequester() }
    val itemReqs = remember { HashMap<Int, FocusRequester>() }
    var pendId by remember { mutableStateOf<Int?>(null) }
    // ctrl = true : bloc d'info + commandes seulement ; ctrl = false : la rangée de chaînes est dépliée (touche Bas).
    var ctrl by remember { mutableStateOf(true) }
    val rootFr = remember { FocusRequester() }
    var sel by remember { mutableStateOf(0) }
    val st = rememberLazyListState()
    val sc = rememberCoroutineScope()
    var lastIdx by remember { mutableStateOf(0) }
    fun focusItem(idx: Int) {
        lastIdx = idx
        sc.launch { try { st.animateScrollToItem((idx - 1).coerceAtLeast(0)) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
    }
    var playing by remember { mutableStateOf(isPlaying()) }
    LaunchedEffect(ctrl) {
        if (!ctrl && clearFor == null) {
            // Rangée dépliée : la sélection va sur le carré "Program guide"
            // On revient sur la dernière carte sélectionnée (la rangée garde son défilement, la carte doit être affichée avant le focus)
            val target = lastIdx.coerceIn(0, shownRecent.size)
            try { st.scrollToItem((target - 1).coerceAtLeast(0)) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex }
            delay(160)
            val rq = if (target == 0) gr else shownRecent.getOrNull(target - 1)?.let { itemReqs[it.id] }
            try { (rq ?: gr).requestFocus() } catch (ex: Exception) { try { gr.requestFocus() } catch (e2: Exception) { } }
        } else if (ctrl) {
            delay(60)
            try { rootFr.requestFocus() } catch (ex: Exception) { }
        }
    }
    // Une carte choisie quitte la rangée (elle est maintenant dans le bloc d'info) : la sélection passe à la carte voisine.
    var lastC by remember { mutableStateOf(c.id) }
    LaunchedEffect(c.id) {
        if (c.id != lastC) {
            lastC = c.id
            if (!ctrl && clearFor == null) {
                if (shownRecent.isEmpty()) {
                    lastIdx = 0
                    delay(120)
                    try { gr.requestFocus() } catch (ex: Exception) { }
                } else {
                    val t = lastIdx.coerceIn(1, shownRecent.size)
                    lastIdx = t
                    try { st.scrollToItem((t - 1).coerceAtLeast(0)) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex }
                    delay(120)
                    val rq = shownRecent.getOrNull(t - 1)?.let { itemReqs[it.id] }
                    try { (rq ?: gr).requestFocus() } catch (ex: Exception) { try { gr.requestFocus() } catch (e2: Exception) { } }
                }
            }
        }
    }
    LaunchedEffect(clearFor) {
        if (clearFor != null) {
            delay(100)
            try { clr.requestFocus() } catch (ex: Exception) { }
        } else if (clearFor == null) {
            val t = pendId
            if (t != null) {
                pendId = null
                delay(90)
                val r = itemReqs[t]
                try { if (r != null) r.requestFocus() else gr.requestFocus() } catch (ex: Exception) { try { gr.requestFocus() } catch (e2: Exception) { } }
            } else {
                try { gr.requestFocus() } catch (ex: Exception) { }
            }
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
            } else if (it.key == Key.Back || it.key == Key.Escape) {
                // Un seul Retour ferme tout le lecteur (commandes affichées ou rangée de chaînes)
                if (it.type == KeyEventType.KeyUp) onClose()
                true
            } else if (ctrl) {
                val down = it.type == KeyEventType.KeyDown
                val k = it.key
                if (k == Key.DirectionLeft) {
                    if (down) sel = (sel - 1).coerceAtLeast(0)
                    true
                } else if (k == Key.DirectionRight) {
                    if (down) sel = (sel + 1).coerceAtMost(2)
                    true
                } else if (k == Key.DirectionDown) {
                    if (down) ctrl = false
                    true
                } else if (k == Key.DirectionUp) {
                    true
                } else if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) {
                    if (it.type == KeyEventType.KeyUp) {
                        if (sel == 0) { onToggle(); playing = isPlaying() } else onTracks()
                    }
                    true
                } else {
                    false
                }
            } else if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                sel = 0
                ctrl = true
                true
            } else {
                false
            }
        }.focusRequester(rootFr).focusable()
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Th.ovMid, Th.ovEnd)))
                .padding(start = 24.dp, end = 28.dp, top = 60.dp, bottom = 0.dp)
        ) {
            // Même bloc d'info que le bandeau de zapping : vignette, numéro, chaîne, titre, heure + barre.
            val numC = channels.indexOfFirst { it.id == c.id } + 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(9.dp)).background(Th.cardBg)) {
                    val pic = p?.icon.orEmpty()
                    if (pic.isNotBlank()) {
                        AsyncImage(model = pic, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else if (c.icon.isNotBlank()) {
                        AsyncImage(
                            model = c.icon, contentDescription = null, colorFilter = invFilter(c.invert),
                            modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit
                        )
                    }
                }
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$numC", color = Th.sel, fontSize = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Column(Modifier.weight(1f).height(88.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.icon.isNotBlank()) {
                            AsyncImage(
                                model = c.icon, contentDescription = null, colorFilter = invFilter(c.invert),
                                modifier = Modifier.height(20.dp).widthIn(max = 48.dp).padding(end = 8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Text(c.name, color = M_GRAY, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (p == null) {
                        Text("No guide information", color = M_GRAY, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text(p.title, color = Th.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Text("Now ${hm(nowMs)}", color = M_GRAY, fontSize = 14.sp)
                            Text("  |  ", color = M_GRAY, fontSize = 15.sp)
                            Text("${hm(p.start)} - ${hm(p.stop)}", color = Th.text, fontSize = 15.sp)
                            val left = ((p.stop - nowMs) / 60000L).coerceAtLeast(0)
                            Text("   +$left min", color = Th.sel, fontSize = 14.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (ctrl) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CtrlCircle(if (playing) 0 else 1, sel == 0) { onToggle(); playing = isPlaying() }
                        Spacer(Modifier.width(10.dp))
                        CtrlCircle(2, sel == 1, onTracks)
                        Spacer(Modifier.width(10.dp))
                        CtrlCircle(3, sel == 2, onTracks)
                        // Flèche vers le bas : la rangée de chaînes se déplie avec la touche Bas
                        Spacer(Modifier.width(18.dp))
                        Box(Modifier.size(24.dp, 12.dp).downChevron(Th.sel))
                    }
                } else {
                    // Petite flèche : "Haut" affiche les commandes
                    Box(Modifier.padding(start = 16.dp).size(24.dp, 12.dp).upChevron(M_GRAY))
                }
            }
            if (p != null) {
                // Barre de progression juste sous le bloc d'info (à la place de la ligne du bas du bandeau)
                Bar(fracOf(p, nowMs), Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 19.dp), 8.dp)
            } else {
                Spacer(Modifier.height(32.dp))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !ctrl,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
            Column {
            Text(
                "CHANNELS", color = Th.sel, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            LazyRow(
                state = st,
                // Décalé de 14 dp vers la gauche (compensé par le contentPadding) : le 1er carré s'aligne sous l'heure
                // de début, et l'effet "grow" garde de la place.
                modifier = Modifier.layout { m, cs ->
                    val sh = 14.dp.roundToPx()
                    val pl = m.measure(cs.copy(maxWidth = cs.maxWidth + sh))
                    layout(cs.maxWidth, pl.height) { pl.place(-sh, 0) }
                },
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
                                    Box(Modifier.size(7.dp, 7.dp).background(Th.sel))
                                    Box(Modifier.size(38.dp, 7.dp).background(Th.sel))
                                }
                            }
                        }
                    }
                }
                itemsIndexed(shownRecent, key = { _, ch -> ch.id }) { ri, ch ->
                    val pr = epg.now(ch.epgId)
                    val num = channels.indexOfFirst { it.id == ch.id } + 1
                    MiniItem(
                        { onPick(ch) }, itemReqs.getOrPut(ch.id) { FocusRequester() },
                        pr?.title ?: ch.name, "$num - ${ch.name.uppercase()}",
                        if (pr != null) fracOf(pr, nowMs) else null,
                        if (pr?.icon.isNullOrBlank()) null else ch.icon,
                        { focusItem(ri + 1) },
                        { clearFor = ch },
                        if (prev.pvId == ch.id) prev.player else null, prev.ready, { prev.focus(ch.id, it) }
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
        val cf = clearFor
        if (cf != null) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(24.dp).width(280.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Th.guideBg)
                    .border(1.dp, Th.divider, RoundedCornerShape(14.dp)).padding(14.dp)
            ) {
                Text("CLEAR RECENT", color = Th.sel, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(cf.name, color = M_GRAY, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                FocusRow({
                    val ix = shownRecent.indexOfFirst { it.id == cf.id }
                    pendId = (shownRecent.getOrNull(ix + 1) ?: shownRecent.getOrNull(ix - 1))?.takeIf { it.id != cf.id }?.id
                    onClearHist(cf.id); removed = removed + cf.id; clearFor = null
                }, Modifier.focusRequester(clr), tile = true, tileCorner = 9.dp, tileVPad = 3.dp, soft = true, pad = 12.dp) {
                    Text("This channel", color = fcolor(Color.White), fontSize = 15.sp)
                }
                FocusRow({ onClearHist(null); removedAll = true; clearFor = null }, tile = true, tileCorner = 9.dp, tileVPad = 3.dp, soft = true, pad = 12.dp) {
                    Text("Remove all", color = fcolor(Color.White), fontSize = 15.sp)
                }
                FocusRow({ clearFor = null }, tile = true, tileCorner = 9.dp, tileVPad = 3.dp, soft = true, pad = 12.dp) {
                    Text("Cancel", color = fcolor(Color.White), fontSize = 15.sp)
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
private val ACCENT: Color get() = Th.sel
private val G_GOLD: Color get() = Th.sel
private val P_DARK_TXT: Color get() = Th.sel

@Composable
private fun GChip(label: String, selected: Boolean, onLong: (() -> Unit)? = null, req: FocusRequester? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val gk by androidx.compose.animation.core.animateFloatAsState(if (f) 1f else 0f, androidx.compose.animation.core.tween(120), label = "chipGrow")
    // La zone focalisable inclut une marge transparente : le défilement garde donc la place pour l'effet "grow".
    Box(
        Modifier.zIndex(if (f) 1f else 0f)
            .onFocusChanged { f = it.isFocused }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onPreviewKeyEvent { if (onLong == null) false else okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 5.dp)
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    if (size.width > 0f && size.height > 0f) {
                        val g = 3.dp.toPx() * gk
                        scaleX = (size.width + 2 * g) / size.width
                        scaleY = (size.height + 2 * g) / size.height
                    }
                }
                .clip(RoundedCornerShape(9.dp))
                .background(if (f) Color(0x33E0B85A) else Color.Transparent)
                .then(if (f) Modifier.border(2.dp, Th.sel, RoundedCornerShape(9.dp)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Onglet texte comme l'en-tête : la catégorie choisie est en doré, soulignée
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label, color = if (f) P_DARK_TXT else if (selected) G_GOLD else Th.sub, fontSize = 14.sp, maxLines = 1,
                    fontWeight = if (selected || f) FontWeight.Bold else FontWeight.Normal
                )
                Box(Modifier.padding(top = 3.dp).width(22.dp).height(2.dp).background(if (selected && !f) G_GOLD else Color.Transparent))
            }
        }
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
    // Défilement du titre seulement si on reste sur la tuile (évite l'animation pendant un appui long Haut/Bas).
    var marq by remember { mutableStateOf(false) }
    LaunchedEffect(f) {
        marq = false
        if (f) { delay(600); marq = true }
    }
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight().padding(horizontal = 3.dp, vertical = 4.dp)
            .zIndex(if (f) 1f else 0f)
            .graphicsLayer {
                if (f && size.width > 0f && size.height > 0f) {
                    val grow = 10.dp.toPx()
                    scaleX = (size.width + grow) / size.width
                    scaleY = (size.height + grow) / size.height
                }
            }
            .clip(RoundedCornerShape(9.dp))
            .background(Th.cell)
            .then(if (live) Modifier.background(G_GOLD.copy(alpha = 0.13f)) else Modifier)
            .then(if (f) Modifier.background(Color(0x22FFFFFF)) else Modifier)
            .then(if (f) Modifier.border(2.dp, Th.sel, RoundedCornerShape(9.dp)) else Modifier)
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onSel() }
            .onPreviewKeyEvent { okLong(it, fired, onPlay, onLong) }
            .clickable { onPlay() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mark == "●") {
                    // Enregistrement programmé : point rouge qui clignote.
                    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "recdot")
                    val a by tr.animateFloat(
                        1f, 0.15f,
                        androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(650),
                            androidx.compose.animation.core.RepeatMode.Reverse
                        ), label = "recdotA"
                    )
                    Box(Modifier.padding(end = 6.dp).size(9.dp).graphicsLayer { alpha = a }
                        .clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE5242B)))
                }
                Text(
                    (if (mark.isNotEmpty() && mark != "●") "$mark " else "") + (p?.title ?: "No information"),
                    color = if (past && !f) M_GRAY else Th.text,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    overflow = if (marq && w >= 160.dp) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = (if (marq && w >= 160.dp) Modifier.basicMarquee() else Modifier).weight(1f, fill = false)
                )
            }
            if (p != null && w >= 110.dp) {
                Text(
                    hm(p.start) + " - " + hm(p.stop),
                    color = if (live) G_GOLD else M_GRAY,
                    fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun DetailPane(selProvider: () -> Pair<Channel, Prog?>?, nowMs: Long) {
    // Léger délai : pendant un appui long, le panneau (image + texte) ne se redessine pas à chaque ligne.
    val live = selProvider()
    var shown by remember { mutableStateOf(live) }
    LaunchedEffect(live) {
        if (shown != live) { delay(150); shown = live }
    }
    val sp = shown
        Column(
            Modifier.width(260.dp).fillMaxHeight().padding(start = 10.dp, top = 5.dp, end = 8.dp, bottom = 5.dp)
                .clip(RoundedCornerShape(14.dp)).background(Th.cell).padding(16.dp)
        ) {
            if (sp != null) {
                val sch = sp.first
                val pr = sp.second
                if (pr != null && pr.icon.isNotBlank()) {
                    AsyncImage(
                        model = pr.icon, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(sch.name, color = M_GRAY, fontSize = 13.sp, maxLines = 1)
                Text(
                    pr?.title ?: "No information", color = Th.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                if (pr != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("${dayHm(pr.start)} - ${hm(pr.stop)}", color = G_GOLD, fontSize = 13.sp)
                        if (pr.start <= nowMs && nowMs < pr.stop) {
                            Bar(fracOf(pr, nowMs), Modifier.padding(start = 10.dp).weight(1f), 8.dp)
                        }
                    }
                    if (pr.sub.isNotBlank() && pr.sub != pr.desc) {
                        Text(pr.sub, color = Th.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    AutoScrollText(pr.desc, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), 13.sp, 18.sp, 4.5f)
                }
            }
        }
}


@Composable
fun EpgGrid(
    chans: List<Channel>, epg: Epg, current: Channel?, showNums: Boolean,
    recs: List<Rec>, remOn: Boolean, dvrSet: Boolean,
    onPlay: (Channel) -> Unit, onDvr: (Channel, Prog, String) -> Unit, onEdit: (Channel) -> Unit, onEditCat: (String) -> Unit, editing: Boolean = false, onBack: () -> Unit
) {
    var dvrFor by remember { mutableStateOf<Pair<Channel, Prog>?>(null) }
    var toast by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
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
    val winStart = remember { (System.currentTimeMillis() / slot) * slot }
    val winEnd = winStart + TOTAL_MIN * 60000L
    // La catégorie n'est mémorisée que si on lance une chaîne depuis le guide ; sinon on revient sur la chaîne en cours.
    var cat by remember {
        mutableStateOf(GuideState.cat.takeIf { c -> c.isEmpty() || chans.any { it.cat == c && it.id == current?.id } } ?: "")
    }
    val play: (Channel) -> Unit = { ch -> GuideState.cat = cat; onPlay(ch) }
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
    // Changement de catégorie : la liste repart toujours de la première chaîne
    var catFirst by remember { mutableStateOf(true) }
    LaunchedEffect(cat) {
        if (catFirst) catFirst = false else try { ls.scrollToItem(0) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
    }
    // [0] = centre (px) of the selected tile, [1] = centre kept while moving Up/Down (-1 = none)
    val mids = remember { floatArrayOf(-1f, -1f, 0f) }
    val chipState = androidx.compose.foundation.lazy.rememberLazyListState()
    val chipReq = remember { FocusRequester() }
    var gridFocus by remember { mutableStateOf(false) }
    fun focusChips() {
        val ci = if (cat.isEmpty()) 0 else (cats.indexOf(cat) + 1).coerceAtLeast(0)
        scope.launch {
            try { chipState.scrollToItem(ci) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            delay(60)
            try { chipReq.requestFocus() } catch (e: Exception) { }
        }
    }
    // Retour : d'abord vers les catégories (si on est dans la grille), puis ferme le guide
    BackHandler { if (gridFocus) focusChips() else onBack() }
    LaunchedEffect(Unit) {
        snapshotFlow { sel?.first?.id }.collect { id ->
            if (id != null) {
                val idx = rowsNow.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    val first = ls.firstVisibleItemIndex
                    // La barre reste vers le milieu (3e ligne) et c'est la liste qui défile ; la barre ne descend
                    // jusqu'en bas que pour les dernières chaînes.
                    val target = (idx - 3).coerceIn(0, (rowsNow.size - 7).coerceAtLeast(0))
                    if (target != first || ls.firstVisibleItemScrollOffset != 0) {
                        try { ls.scrollToItem(target) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex }
                    }
                }
            }
        }
    }
    var wrapId by remember { mutableStateOf<Int?>(null) }
    val req = remember { FocusRequester() }
    // Après l'éditeur de chaîne : si la chaîne a été masquée, la sélection passe à sa voisine (suivante, sinon précédente).
    val prevRows = remember { arrayOf(rows) }
    if (rows.any { it.id == sel?.first?.id }) prevRows[0] = rows
    var wasEditing by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) {
            wasEditing = true
        } else if (wasEditing) {
            wasEditing = false
            val id = sel?.first?.id
            val now = rowsNow
            if (id != null && now.isNotEmpty()) {
                val tgt: Channel? = if (now.any { it.id == id }) now.first { it.id == id } else {
                    val old = prevRows[0]
                    val pi = old.indexOfFirst { it.id == id }
                    val ids = now.map { it.id }.toHashSet()
                    (if (pi >= 0) (old.drop(pi + 1).firstOrNull { it.id in ids } ?: old.take(pi).lastOrNull { it.id in ids }) else null)
                        ?.let { n -> now.firstOrNull { it.id == n.id } } ?: now.first()
                }
                if (tgt != null) {
                    sel = Pair(tgt, epg.now(tgt.epgId))
                    val ix = now.indexOfFirst { it.id == tgt.id }
                    try { ls.scrollToItem((ix - 3).coerceIn(0, (now.size - 7).coerceAtLeast(0))) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex }
                    delay(150)
                    wrapId = tgt.id
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(350)
        try { req.requestFocus() } catch (ex: Exception) { }
    }
    val nowX = ((nowMs - winStart) / 60000f * DPM).dp

    // Fond translucide comme avant : le direct reste visible derrière le guide
    Box(Modifier.fillMaxSize().background(Th.guideBg)) {
    Column(Modifier.fillMaxSize()) {
        // En-tête identique aux autres écrans : logo, onglet, catégories, heure
        Row(
            Modifier.fillMaxWidth().background(Th.header).padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EliteLogo(Modifier.padding(end = 28.dp).size(44.dp))
            TopTab("Guide", true, icon = 1) { }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(Th.divider))
            Spacer(Modifier.width(6.dp))
            LazyRow(
                state = chipState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 30.dp)
            ) {
                item { GChip("All", cat.isEmpty(), null, if (cat.isEmpty()) chipReq else null) { cat = "" } }
                items(cats) { c2 -> GChip(c2, cat == c2, { onEditCat(c2) }, if (cat == c2) chipReq else null) { cat = c2 } }
            }
            Spacer(Modifier.width(16.dp))
            HeaderClock()
        }
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            Spacer(Modifier.width(LW))
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { vw = it.width - with(density) { 260.dp.toPx().toInt() } }.horizontalScroll(hs, enabled = false)) {
                Box(Modifier.padding(start = 8.dp).width((TOTAL_MIN * DPM).dp).fillMaxHeight()) {
                    Text(
                        "ON NOW", color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = nowX + 4.dp)
                    )
                    val first = ((winStart + slot - 1) / slot) * slot
                    val ymd = SimpleDateFormat("yyyyMMdd", Locale.US)
                    val todayKey = ymd.format(Date(winStart))
                    var skipK = -1
                    for (k in 0 until TOTAL_MIN / 30) {
                        val t = first + k * slot
                        if (k == skipK) continue
                        if (t < winEnd && Math.abs(t - nowMs) >= 22 * 60000L) {
                            // Premier créneau d'un nouveau jour : on affiche aussi la date.
                            val newDay = ymd.format(Date(t)) != todayKey && ymd.format(Date(t)) != ymd.format(Date(t - slot))
                            if (newDay) skipK = k + 1
                            Text(
                                if (newDay) SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format(Date(t)).uppercase()
                                else if (ymd.format(Date(t)) != todayKey && Date(t).minutes == 0)
                                    SimpleDateFormat("EEE d, HH:mm", Locale.ENGLISH).format(Date(t)).uppercase()
                                else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t)),
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
                    Modifier.weight(1f).fillMaxWidth().onFocusChanged { gridFocus = it.hasFocus }.onPreviewKeyEvent { ev ->
                        // Boucle : Bas sur la dernière chaîne -> première. (Haut sur la première mène aux catégories.)
                        val id = sel?.first?.id
                        if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionDown || ev.key == Key.DirectionUp) && mids[0] >= 0f) {
                            // Sur le programme en cours : Haut/Bas restent sur "maintenant" ; ailleurs : tuile voisine
                            mids[1] = if (mids[2] > 0f) -1f else mids[0].coerceIn(hs.value.toFloat(), (hs.value + vw).toFloat())
                        }
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp && id != null && rows.isNotEmpty() && id == rows.first().id) {
                            focusChips()
                            return@onPreviewKeyEvent true
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
                            // Seules les tuiles proches de la zone visible sont créées (une ligne = 36 h de programmes) :
                            // l'ouverture d'une catégorie et le défilement rapide deviennent beaucoup plus légers.
                            val vwE = if (vw > 0) vw else 1800
                            val half = (vwE / 2).coerceAtLeast(1)
                            val chunk by remember(half) { derivedStateOf { hs.value / half } }
                            val loPx = (chunk - 2) * half
                            val hiPx = (chunk + 6) * half
                            Row(Modifier.fillMaxWidth().height(rowH)) {
                                Box(
                                    Modifier.width(LW).fillMaxHeight().padding(horizontal = 3.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        // Boîte du logo : sombre et neutre, au ton du thème
                                        .background(
                                            if (Th.light) Brush.verticalGradient(listOf(Th.logoA, Th.logoB))
                                            else Brush.verticalGradient(listOf(Color(0xFF25282E), Color(0xFF16181C)))
                                        )
                                        // Chaîne sélectionnée : léger voile doré (lu au dessin, sans recomposer la ligne)
                                        .drawBehind { if (sel?.first?.id == ch.id) drawRect(G_GOLD.copy(alpha = 0.13f)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (ch.icon.isNotBlank()) {
                                        AsyncImage(
                                            model = ch.icon, contentDescription = null, colorFilter = invFilter(ch.invert),
                                            modifier = Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit,
                                            alignment = Alignment.Center
                                        )
                                    } else {
                                        Text(
                                            ch.name, color = Th.text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
                                                        if (qi != tgt && (x1 < hs.value - vwE || x0 > hs.value + 2 * vwE)) continue
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
                                    Box(Modifier.padding(start = 8.dp).width((TOTAL_MIN * DPM).dp).fillMaxHeight()) {
                                        if (progs.isEmpty()) {
                                            GBlock(
                                                null, 0.dp, (TOTAL_MIN * DPM).dp, nowMs, rr, "",
                                                { sel = Pair(ch, null); mids[0] = -1f; mids[1] = -1f; mids[2] = 0f }, { play(ch) }, { onEdit(ch) }
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
                                            if (!useReq && (ePx < loPx || xPx > hiPx)) continue
                                            val mk = recs.firstOrNull {
                                                it.chId == ch.id && it.start == p.start &&
                                                    (it.state == "scheduled" || it.state == "recording")
                                            }?.let { if (it.kind == "rec") "●" else "⏰" } ?: ""
                                            GBlock(
                                                p, x, w, nowMs, if (useReq) rr else preqs[pi], mk,
                                                {
                                                    sel = Pair(ch, p)
                                                    mids[0] = (xPx + ePx) / 2f
                                                    mids[2] = if (live) 1f else 0f
                                                    mids[1] = -1f
                                                    val vs = hs.value
                                                    if (xPx < vs || xPx > vs + (vw * 0.7f).toInt() || ePx < vs + 80) {
                                                        scope.launch { hs.animateScrollTo((xPx - vw / 5).coerceAtLeast(0)) }
                                                    }
                                                },
                                                {
                                                    if (p.start > nowMs) {
                                                        dvrFor = Pair(ch, p)
                                                    } else play(ch)
                                                },
                                                { onEdit(ch) }
                                            )
                                        }
                                        if (nowX > 0.dp) {
                                            Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight().background(G_GOLD))
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
    }
    val tt = toast
    if (tt != null) {
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopCenter,
            offset = androidx.compose.ui.unit.IntOffset(0, with(density) { 120.dp.roundToPx() })
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xE6262626)).padding(horizontal = 28.dp, vertical = 16.dp)
            ) {
                Text(tt.first, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(tt.second, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
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
                item { PItem("Watch now", "", { dvrFor = null; play(dch) }, autoFocus = true) }
            }
            item {
                PItem(
                    if (hasRec) "Cancel recording" else "Record",
                    if (hasRec) "" else if (live) "Starts now" else "Recorded automatically at start time",
                    { onDvr(dch, dp, if (hasRec) "unrec" else "rec"); toast = Pair(if (hasRec) "Removed schedule" else "Scheduled recording", dp.title); dvrFor = null },
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
            if (!live) item { PItem("Watch channel", "", { dvrFor = null; play(dch) }) }
        }
    }
}
