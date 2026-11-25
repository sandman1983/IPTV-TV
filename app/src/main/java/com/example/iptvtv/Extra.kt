@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val X_MUTED = Color(0xFFB0B8C8)

// ---------------------------------------------------------------------------
// Suivi de lecture des enregistrements (reprise + étiquette NEW)
// ---------------------------------------------------------------------------
object Watch {
    private fun sp(c: android.content.Context) = c.getSharedPreferences("watch", 0)
    // Retourne (position, durée, horodatage) ou null si jamais lu
    fun get(c: android.content.Context, id: String): Triple<Long, Long, Long>? {
        val s = sp(c).getString(id, null) ?: return null
        val p = s.split("|")
        if (p.size < 3) return null
        return Triple(p[0].toLongOrNull() ?: 0L, p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L)
    }
    fun set(c: android.content.Context, id: String, pos: Long, dur: Long) {
        sp(c).edit().putString(id, "$pos|$dur|${System.currentTimeMillis()}").apply()
    }
    fun clear(c: android.content.Context, id: String) { sp(c).edit().remove(id).apply() }
}

private val X_GOLD = Color(0xFFE0B85A)
private val X_RED = Color(0xFFE5484D)

private fun durTxt(ms: Long): String {
    val m = (ms / 60000L).coerceAtLeast(0L)
    return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)}"
}

private fun bytesTxt(b: Long): String =
    if (b >= 1_000_000_000L) "%.1f GB".format(b / 1e9) else "${b / 1_000_000L} MB"

// Logo de chaîne dans un petit badge blanc (coin haut gauche)
@Composable
private fun LogoBadge(ch: Channel?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF4F4F4))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (ch != null && ch.icon.isNotBlank()) {
            AsyncImage(model = ch.icon, contentDescription = null, modifier = Modifier.size(34.dp, 16.dp), contentScale = ContentScale.Fit)
        } else {
            Text(name.take(5).uppercase(), color = Color(0xFF14213D), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun Thumb(ch: Channel?, thumb: String, name: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Th.cardBg), contentAlignment = Alignment.Center) {
        if (thumb.isNotBlank()) {
            AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name, color = Th.sub, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
        }
    }
}

// Grande carte (Continue watching / Recorded) : cadre doré extérieur, jamais sur la barre de progression.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecCard(
    w: androidx.compose.ui.unit.Dp, ch: Channel?, r: Rec, thumb: String,
    topRight: String, topRightGold: Boolean, bottomRight: String, frac: Float?,
    line1: String, line2: androidx.compose.ui.text.AnnotatedString,
    onClick: () -> Unit, onLong: () -> Unit, req: FocusRequester? = null
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (f) 1.04f else 1f, label = "recScale")
    Column(
        Modifier.width(w)
            .zIndex(if (f) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(3.dp, if (f) Th.sel else Color.Transparent, RoundedCornerShape(12.dp)).padding(3.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))) {
                Thumb(ch, thumb, r.title, Modifier.fillMaxSize())
                LogoBadge(ch, r.chName, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (topRight.isNotBlank()) {
                    Text(
                        topRight, color = if (topRightGold) Color(0xFF1A1A1A) else Color.White,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (topRightGold) X_GOLD else Color(0xB3000000)).padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (bottomRight.isNotBlank()) {
                    Text(
                        bottomRight, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xB3000000)).padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (f) {
                    Box(
                        Modifier.align(Alignment.Center).size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", color = Color(0xFF14213D), fontSize = 16.sp)
                    }
                }
                if (frac != null) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Th.barTrack)) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(X_GOLD))
                    }
                }
            }
        }
        Text(
            line1, color = Th.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            overflow = if (f) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 3.dp, end = 6.dp).then(if (f) Modifier.basicMarquee() else Modifier)
        )
        Text(line2, color = Th.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp, end = 6.dp))
    }
}

// Tuile compacte (Scheduled)
@Composable
private fun SchedTile(
    w: androidx.compose.ui.unit.Dp, ch: Channel?, r: Rec, thumb: String, whenTxt: String,
    onClick: () -> Unit, req: FocusRequester? = null
) {
    var f by remember { mutableStateOf(false) }
    val rec = r.kind == "rec"
    val live = r.state == "recording"
    Row(
        Modifier.width(w).height(72.dp).clip(RoundedCornerShape(10.dp))
            .background(if (f) Th.focusBg else Th.tile)
            .then(if (f) Modifier.border(2.dp, Th.sel, RoundedCornerShape(10.dp)) else Modifier)
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp, 56.dp).clip(RoundedCornerShape(8.dp))) {
            Thumb(ch, thumb, "", Modifier.fillMaxSize())
            LogoBadge(ch, r.chName, Modifier.align(Alignment.TopStart).padding(3.dp).scale(0.7f))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(whenTxt, color = if (f) Color(0xFF8A6100) else X_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (rec) Color(0x33E5484D) else Color(0x33808080)).padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rec) Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(X_RED))
                    Text(
                        (if (rec) (if (live) " Recording" else " Record") else "🔔 Reminder"),
                        color = if (f) Th.focusText else (if (rec) Color(0xFFFFB4A9) else Th.text), fontSize = 10.sp, maxLines = 1
                    )
                }
            }
            Text(r.title, color = if (f) Th.focusText else Th.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.chName, color = if (f) Th.focusSub else Th.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SecHead(title: String, sub: String) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = Th.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Th.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
    }
}

@Composable
private fun TabPill(label: String, sel: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Text(
        label, color = if (sel) Th.focusText else Th.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (sel) Th.focusBg else Color.Transparent)
            .then(if (f) Modifier.border(2.dp, Th.sel, RoundedCornerShape(50)) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

// ---------------------------------------------------------------------------
// Enregistrements : onglets, reprise de lecture, programmés, enregistrés
// ---------------------------------------------------------------------------
@Composable
fun RecordingsScreen(recs: List<Rec>, channels: List<Channel>, epg: Epg, onAct: (String, String) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf<Rec?>(null) }
    var ren by remember { mutableStateOf<Rec?>(null) }
    var renText by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { clock = System.currentTimeMillis(); delay(10000) } }
    val chById = remember(channels) { channels.associateBy { it.id } }
    fun thumbOf(r: Rec): String {
        val ep = chById[r.chId]?.epgId.orEmpty()
        return epg.progs[ep]?.firstOrNull { it.start == r.start }?.icon.orEmpty()
    }

    val done = remember(recs) { recs.filter { it.kind == "rec" && (it.state == "done" || it.state == "failed") }.sortedByDescending { it.start } }
    val sched = remember(recs) { recs.filter { it.state == "scheduled" || it.state == "recording" }.sortedBy { it.start } }
    val watch = remember(recs, menu, ren) { done.associate { it.id to Watch.get(ctx, it.id) } }
    val cont = remember(done, watch) {
        done.filter { r ->
            val w = watch[r.id]
            r.state == "done" && w != null && w.second > 0 && w.first > 5000L && w.first < w.second * 0.95
        }.sortedByDescending { watch[it.id]!!.third }
    }
    val used = remember(recs) {
        var s = 0L
        for (r in done) try {
            val u = android.net.Uri.parse(r.path)
            if (u.scheme == "file") s += java.io.File(u.path ?: "").length()
        } catch (e: Exception) { }
        s
    }
    val free = remember(recs) {
        try { android.os.StatFs((ctx.getExternalFilesDir(null) ?: ctx.filesDir).path).availableBytes } catch (e: Exception) { 0L }
    }
    val total = used + free

    val dfDay = remember { SimpleDateFormat("EEE d", Locale.ENGLISH) }
    val dfRec = remember { SimpleDateFormat("EEE d MMM", Locale.ENGLISH) }
    val dfKey = remember { SimpleDateFormat("yyyyMMdd", Locale.US) }
    fun whenTxt(t: Long): String {
        val k = dfKey.format(Date(t))
        val day = when (k) {
            dfKey.format(Date(clock)) -> "Today"
            dfKey.format(Date(clock + 86400000L)) -> "Tomorrow"
            else -> dfDay.format(Date(t))
        }
        return "$day · ${hm(t)}"
    }
    val gap = 14.dp

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        // Barre du haut : titre, onglets, stockage, heure
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recordings", color = Th.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Continue watching", "Scheduled", "Recorded").forEachIndexed { i, l ->
                    TabPill(l, tab == i) { tab = i }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.width(190.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Storage", color = Th.sub, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${bytesTxt(used)} of ${bytesTxt(total)}", color = Th.sub, fontSize = 11.sp)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(Th.barTrack)) {
                    Box(Modifier.fillMaxWidth(if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(X_GOLD))
                }
            }
            Spacer(Modifier.width(20.dp))
            Text(hm(clock), color = Th.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        val showCont = (tab == 0 || tab == 1) && cont.isNotEmpty()
        val showSched = (tab == 0 || tab == 2) && sched.isNotEmpty()
        val showDone = (tab == 0 || tab == 3) && done.isNotEmpty()
        if (!showCont && !showSched && !showDone) {
            Text(
                if (recs.none { it.kind == "rec" }) "No recordings yet. In the guide, select a future programme to record it."
                else "Nothing here yet.",
                color = Th.sub, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val w4 = (maxWidth - gap * 3) / 4
            val w5 = (maxWidth - gap * 4) / 5
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                if (showCont) item(key = "cont") {
                    Column {
                        SecHead("Continue watching", "Resume where you stopped")
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                            items(cont, key = { it.id }) { r ->
                                val wt = watch[r.id]!!
                                val fr = wt.first.toFloat() / wt.second
                                val pct = (fr * 100).toInt()
                                val left = (wt.second - wt.first).coerceAtLeast(0L)
                                CardWrap(w4) {
                                    RecCard(
                                        w4 - 12.dp, chById[r.chId], r, thumbOf(r), "$pct%", false, "", fr,
                                        r.title,
                                        androidx.compose.ui.text.buildAnnotatedString {
                                            append(r.chName + " · ")
                                            withStyle(androidx.compose.ui.text.SpanStyle(color = X_GOLD)) { append("$pct% watched") }
                                            append(" · ${durTxt(left)} left")
                                        },
                                        { onAct("play", r.id) }, { menu = r }
                                    )
                                }
                            }
                        }
                    }
                }
                if (showSched) item(key = "sched") {
                    Column {
                        SecHead("Scheduled", "${sched.size} upcoming")
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                            items(sched, key = { it.id }) { r ->
                                SchedTile(w4 - 12.dp, chById[r.chId], r, thumbOf(r), if (r.state == "recording") "Now · until ${hm(r.stop)}" else whenTxt(r.start), { menu = r })
                            }
                        }
                    }
                }
                if (showDone) item(key = "done") {
                    Column {
                        SecHead("Recorded", "Newest first · Hold OK to delete or rename")
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                            items(done, key = { it.id }) { r ->
                                val isNew = watch[r.id] == null && r.state == "done"
                                CardWrap(w5) {
                                    RecCard(
                                        w5 - 12.dp, chById[r.chId], r, thumbOf(r),
                                        if (r.state == "failed") "FAILED" else if (isNew) "NEW" else "", isNew,
                                        durTxt(r.stop - r.start), null,
                                        r.title,
                                        androidx.compose.ui.text.AnnotatedString("${r.chName} · ${dfRec.format(Date(r.start))}"),
                                        { if (r.state == "done" && r.path.isNotBlank()) onAct("play", r.id) else menu = r }, { menu = r }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val r = menu
    if (r != null) {
        val cur = recs.firstOrNull { it.id == r.id }
        if (cur == null) {
            menu = null
        } else {
            PanelDialog(cur.title, { menu = null }, actions = { PButton("Close", { menu = null }) }) {
                if (cur.state == "done" && cur.path.isNotBlank()) {
                    item { PItem("Play", "", { menu = null; onAct("play", cur.id) }, autoFocus = true) }
                    if (Watch.get(ctx, cur.id) != null) item { PItem("Play from start", "", { Watch.clear(ctx, cur.id); menu = null; onAct("play", cur.id) }) }
                }
                if (cur.state == "recording") {
                    item { PItem("Stop recording", "", { menu = null; onAct("stop", cur.id) }, autoFocus = true) }
                }
                if (cur.state == "scheduled") {
                    item { PItem(if (cur.kind == "rec") "Cancel recording" else "Remove reminder", "", { menu = null; onAct("cancel", cur.id) }, autoFocus = true) }
                }
                if (cur.kind == "rec" && (cur.state == "done" || cur.state == "failed")) {
                    item { PItem("Rename", "", { renText = cur.title; ren = cur; menu = null }) }
                    item {
                        PItem(
                            "Delete", if (cur.path.isNotBlank()) "Also deletes the file" else "",
                            { Watch.clear(ctx, cur.id); menu = null; onAct("delete", cur.id) }, autoFocus = cur.state == "failed"
                        )
                    }
                }
                if (cur.note.isNotBlank()) item { PInfo(cur.note) }
            }
        }
    }
    val rn = ren
    if (rn != null) {
        PanelDialog(
            "Rename recording", { ren = null },
            actions = {
                PButton("Save", {
                    val t = renText.trim()
                    if (t.isNotBlank()) Dvr.update(ctx, rn.id) { it.copy(title = t) }
                    ren = null
                })
                PButton("Cancel", { ren = null })
            }
        ) {
            item { PInput("Title", renText, { renText = it }, autoFocus = true) }
        }
    }
}

// Espace autour de la carte pour que le zoom et le cadre doré ne soient pas coupés par la liste
@Composable
private fun CardWrap(w: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(w), contentAlignment = Alignment.TopCenter) { content() }
}

// ---------------------------------------------------------------------------
// Multiview : 4 vignettes (2 x 2), le son suit la vignette sélectionnée
// ---------------------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun MultiviewScreen(main: ExoPlayer, channels: List<Channel>, epg: Epg, start: Channel?, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val slots = remember { mutableStateListOf<Channel?>(start, null, null, null) }
    var sel by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf(-1) }
    val reqs = remember { List(4) { FocusRequester() } }
    val players = remember {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        List(4) {
            ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
                .build()
        }
    }
    // Le lecteur principal se met en pause pendant le multiview, et reprend à la sortie.
    DisposableEffect(Unit) {
        val was = main.playWhenReady
        main.playWhenReady = false
        onDispose {
            players.forEach { try { it.release() } catch (ex: Exception) { } }
            main.playWhenReady = was
        }
    }
    for (i in 0 until 4) {
        val id = slots[i]?.id
        LaunchedEffect(id) {
            val ch = slots[i]
            val p = players[i]
            try {
                if (ch == null) {
                    p.stop()
                    p.clearMediaItems()
                } else {
                    p.setMediaItem(MediaItem.fromUri(ch.url))
                    p.prepare()
                    p.playWhenReady = true
                }
            } catch (ex: Exception) { }
        }
    }
    LaunchedEffect(sel) {
        players.forEachIndexed { i, p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, i != sel)
                .build()
            p.volume = if (i == sel) 1f else 0f
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        try { reqs[0].requestFocus() } catch (ex: Exception) { }
    }
    LaunchedEffect(picking) {
        if (picking == -1) {
            delay(120)
            try { reqs[sel].requestFocus() } catch (ex: Exception) { }
        }
    }
    Box(Modifier.fillMaxSize().background(Th.bgBottom)) {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (rIdx in 0 until 2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (cIdx in 0 until 2) {
                        val i = rIdx * 2 + cIdx
                        MvCell(
                            slots[i], players[i], sel == i, reqs[i], epg, Modifier.weight(1f).fillMaxHeight(),
                            { picking = i }, { sel = i }
                        )
                    }
                }
            }
        }
    }
    val pi = picking
    if (pi in 0..3) {
        PanelDialog("Choose a channel", { picking = -1 }, actions = { PButton("Cancel", { picking = -1 }) }) {
            if (slots[pi] != null) {
                item { PItem("Remove this view", "", { slots[pi] = null; picking = -1 }, autoFocus = true) }
            }
            itemsIndexed(channels, key = { i, c -> "${c.id}_$i" }) { idx, c ->
                PItem(
                    "${idx + 1}   ${c.name}", epg.now(c.epgId)?.title ?: c.cat,
                    { slots[pi] = c; sel = pi; picking = -1 },
                    autoFocus = idx == 0 && slots[pi] == null, icon = c.icon
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MvCell(
    ch: Channel?, pl: ExoPlayer, selected: Boolean, req: FocusRequester, epg: Epg,
    modifier: Modifier, onClick: () -> Unit, onFocus: () -> Unit
) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier
            .focusRequester(req)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .border(if (f) 3.dp else 1.dp, if (f) Th.sel else Th.divider)
            .background(Th.mvCell)
            .clickable { onClick() }
    ) {
        if (ch != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        isFocusable = false
                        this.player = pl
                        setErrorMessageProvider { _ -> android.util.Pair(0, "") }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).background(Th.ovMid)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ch.name, color = Th.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text("AUDIO", color = Th.time, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                "+  Add a channel", color = Th.sub, fontSize = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}


// Aperçu muet dans les cartes (accueil et mini-lecteur) : démarre dès qu'on se pose sur une carte
// et dure tant qu'on reste dessus. OK ouvre la chaîne ; la chaîne d'arrière-plan ne change jamais toute seule.
// Vrai pendant une mise à jour des chaînes / du guide (affiche un cercle de chargement dans les réglages).
object SyncState {
    var busy by mutableStateOf(false)
}

class CardPreview(val player: ExoPlayer) {
    var focusId by mutableStateOf<Int?>(null)
    var pvId by mutableStateOf<Int?>(null)
    var ready by mutableStateOf(false)
    fun focus(id: Int, on: Boolean) {
        if (on) focusId = id else if (focusId == id) focusId = null
    }
}

@Composable
fun rememberCardPreview(ch: List<Channel>, curId: Int?): CardPreview {
    val ctx = LocalContext.current
    val cp = remember {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val p = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultDataSource.Factory(ctx, http)
                )
            ).build()
        p.volume = 0f
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        val c = CardPreview(p)
        p.addListener(object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() { c.ready = true }
        })
        c
    }
    DisposableEffect(Unit) { onDispose { try { cp.player.release() } catch (e: Exception) { } } }
    // Relancé aussi quand la chaîne en cours change : une carte qui vient d'être lancée repasse sur sa vignette.
    LaunchedEffect(cp.focusId, curId) {
        cp.pvId = null
        cp.ready = false
        try { cp.player.stop(); cp.player.clearMediaItems() } catch (e: Exception) { }
        val id = cp.focusId
        val c = if (id != null) ch.firstOrNull { it.id == id } else null
        val on = ctx.getSharedPreferences("iptv_player", 0).getBoolean("cardPreview", true)
        if (c != null && on && c.id != curId) {
            delay(250) // petit délai anti-rebond quand on défile vite
            try {
                cp.player.setMediaItem(MediaItem.fromUri(c.url))
                cp.player.prepare()
                cp.player.playWhenReady = true
                cp.pvId = c.id
            } catch (e: Exception) { }
        }
    }
    return cp
}
