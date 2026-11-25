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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
import kotlinx.coroutines.launch
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

// Logo de chaîne, sans fond, en bas à gauche de la vignette
@Composable
private fun LogoBadge(ch: Channel?, name: String, modifier: Modifier = Modifier, logo: String = "") {
    val lg = if (ch != null && ch.icon.isNotBlank()) ch.icon else logo
    if (lg.isNotBlank()) {
        AsyncImage(
            model = lg, contentDescription = null, colorFilter = invFilter(ch?.invert == true),
            modifier = modifier.size(50.dp, 26.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart
        )
    } else {
        Text(
            name.take(8).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(1f, 1f), 4f)),
            modifier = modifier
        )
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
    topRight: String, tagKind: Int, bottomRight: String, frac: Float?,
    line1: String, line2: androidx.compose.ui.text.AnnotatedString,
    onClick: () -> Unit, onLong: () -> Unit, req: FocusRequester? = null,
    showPlay: Boolean = true, onFocus: (Boolean) -> Unit = {}
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (f) 1.04f else 1f, label = "recScale")
    Column(
        Modifier.width(w)
            .zIndex(if (f) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; onFocus(it.isFocused) }
            .onPreviewKeyEvent { okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(3.dp, if (f) Th.sel else Color.Transparent, RoundedCornerShape(12.dp)).padding(3.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))) {
                Thumb(ch, thumb, r.title, Modifier.fillMaxSize())
                LogoBadge(ch, r.chName, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 12.dp), r.logo)
                if (topRight.isNotBlank()) {
                    Text(
                        topRight, color = if (tagKind == 1) Color(0xFF1A1A1A) else Color.White,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (tagKind == 1) X_GOLD else if (tagKind == 2) Color(0xDDB3262B) else Color(0xB3000000)).padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (bottomRight.isNotBlank()) {
                    Text(
                        bottomRight, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xB3000000)).padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (f && showPlay) {
                    Box(
                        Modifier.align(Alignment.Center).size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White)
                            .drawBehind {
                                // Triangle "play" centré dans le cercle (léger décalage vers la droite pour l'équilibre optique)
                                val tw = size.width * 0.34f
                                val th = size.height * 0.42f
                                val x0 = (size.width - tw) / 2f + tw * 0.08f
                                val y0 = (size.height - th) / 2f
                                val p = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(x0, y0); lineTo(x0 + tw, size.height / 2f); lineTo(x0, y0 + th); close()
                                }
                                drawPath(p, Color(0xFF14213D))
                            }
                    )
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

@Composable
private fun SecHead(title: String, sub: String) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = Th.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Th.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
    }
}

// ---------------------------------------------------------------------------
// Enregistrements : onglets, reprise de lecture, programmés, enregistrés
// ---------------------------------------------------------------------------
@Composable
fun RecordingsScreen(recs: List<Rec>, channels: List<Channel>, epg: Epg, dvrUri: String, onAct: (String, String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    // Retour : depuis Scheduled / Recorded on revient d'abord sur All
    BackHandler { if (tab != 0) tab = 0 else onBack() }
    var menu by remember { mutableStateOf<Rec?>(null) }
    var ren by remember { mutableStateOf<Rec?>(null) }
    var renText by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { clock = System.currentTimeMillis(); delay(10000) } }
    val chById = remember(channels) { channels.associateBy { it.id } }
    fun thumbOf(r: Rec): String {
        if (r.thumb.isNotBlank()) return r.thumb
        val ep = chById[r.chId]?.epgId.orEmpty()
        val l = epg.progs[ep] ?: return ""
        return (l.firstOrNull { it.start == r.start } ?: l.firstOrNull { it.start <= r.start && r.start < it.stop })?.icon.orEmpty()
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
    // Stockage : le disque où sont enregistrés les fichiers (clé USB si choisie), pas la mémoire interne
    val vol = remember(recs, dvrUri) {
        fun stat(path: String): Pair<Long, Long>? = try {
            val st = android.os.StatFs(path); Pair(st.totalBytes, st.availableBytes)
        } catch (e: Exception) { null }
        var res: Pair<Long, Long>? = null
        if (dvrUri.isNotBlank()) {
            try {
                val id = android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(dvrUri))
                val v = id.substringBefore(':')
                res = if (v == "primary") stat(android.os.Environment.getExternalStorageDirectory().path) else stat("/storage/$v")
                if (res == null) {
                    val sm = ctx.getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                    for (sv in sm.storageVolumes) {
                        if (sv.uuid == v && android.os.Build.VERSION.SDK_INT >= 30) { sv.directory?.path?.let { res = stat(it) } }
                    }
                }
            } catch (e: Exception) { }
        }
        res ?: stat((ctx.getExternalFilesDir(null) ?: ctx.filesDir).path) ?: Pair(0L, 0L)
    }
    val total = vol.first
    val used = (vol.first - vol.second).coerceAtLeast(0L)

    val scope = rememberCoroutineScope()
    val lsCont = androidx.compose.foundation.lazy.rememberLazyListState()
    val lsSched = androidx.compose.foundation.lazy.rememberLazyListState()
    val lsDone = androidx.compose.foundation.lazy.rememberLazyListState()
    // Chaque rangée montre un nombre entier de cartes : le défilement se cale sur la carte sélectionnée
    fun snap(ls: androidx.compose.foundation.lazy.LazyListState, idx: Int, n: Int) {
        scope.launch {
            try {
                val first = ls.firstVisibleItemIndex
                if (idx < first) ls.scrollToItem(idx) else if (idx > first + n - 1) ls.scrollToItem(idx - n + 1)
                else if (ls.firstVisibleItemScrollOffset != 0) ls.scrollToItem(first)
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
        }
    }
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
    val gap = 4.dp
    val showCont = (tab == 0 || tab == 1) && cont.isNotEmpty()
    val showSched = (tab == 0 || tab == 2) && sched.isNotEmpty()
    val showDone = (tab == 0 || tab == 3) && done.isNotEmpty()
    val idxSched = if (showCont) 1 else 0
    val idxDone = idxSched + (if (showSched) 1 else 0)
    val lc = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(tab) { try { lc.scrollToItem(0) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
    // Si l'élément (titre de section ou rangée) n'est pas entièrement visible, on le ramène en haut :
    // le titre ne reste jamais caché et la rangée suivante apparaît à moitié en bas.
    fun reveal(idx: Int) {
        scope.launch {
            try {
                // On attend la fin du défilement automatique du focus avant de vérifier
                delay(60)
                while (lc.isScrollInProgress) delay(30)
                val li = lc.layoutInfo
                val info = li.visibleItemsInfo.firstOrNull { it.index == idx }
                val ok = info != null && info.offset >= 0 && info.offset + info.size <= li.viewportEndOffset - li.afterContentPadding
                if (!ok) lc.animateScrollToItem(idx)
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
        }
    }

    @Composable
    fun SchedCard(w4: androidx.compose.ui.unit.Dp, r: Rec, ix: Int, n: Int, ls: androidx.compose.foundation.lazy.LazyListState, li: Int) {
        val rec = r.kind == "rec"
        val live = r.state == "recording"
        CardWrap(w4) {
            RecCard(
                w4 - 10.dp, chById[r.chId], r, thumbOf(r),
                if (rec) (if (live) "● Recording" else "● Record") else "⏰ Reminder", if (rec) 2 else 0,
                "${hm(r.start)} - ${hm(r.stop)}", null,
                r.title,
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = X_GOLD)) { append(if (live) "Now" else whenTxt(r.start)) }
                    append(" · " + r.chName)
                },
                { menu = r }, { menu = r },
                showPlay = false, onFocus = { if (it) { if (ix >= 0) snap(ls, ix, n); reveal(li) } }
            )
        }
    }

    @Composable
    fun DoneCard(w4: androidx.compose.ui.unit.Dp, r: Rec, ix: Int, n: Int, ls: androidx.compose.foundation.lazy.LazyListState, li: Int) {
        val isNew = watch[r.id] == null && r.state == "done"
        CardWrap(w4) {
            RecCard(
                w4 - 10.dp, chById[r.chId], r, thumbOf(r),
                if (r.state == "failed") "FAILED" else if (isNew) "NEW" else "", if (isNew) 1 else 0,
                durTxt(r.stop - r.start), null,
                r.title,
                androidx.compose.ui.text.AnnotatedString("${r.chName} · ${dfRec.format(Date(r.start))}"),
                { if (r.state == "done" && r.path.isNotBlank()) onAct("play", r.id) else menu = r }, { menu = r },
                onFocus = { if (it) { if (ix >= 0) snap(ls, ix, n); reveal(li) } }
            )
        }
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Th.header, 0.25f to Th.bgMid, 1f to Th.bgMid))) {
    Column(Modifier.fillMaxSize()) {
        // En-tête identique à l'accueil : logo, onglets, stockage, heure
        Row(
            Modifier.fillMaxWidth().background(Th.header).padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EliteLogo(Modifier.padding(end = 28.dp).size(44.dp))
            TopTab("Recordings", true, icon = 2) { }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(Th.divider))
            Spacer(Modifier.width(10.dp))
            listOf("All" to 0, "Scheduled" to 2, "Recorded" to 3).forEach { (l, v) ->
                TopTab(l, tab == v, hPad = 10.dp) { tab = v }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.width(172.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Storage", color = Th.sub, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${bytesTxt(used)} of ${bytesTxt(total)}", color = Th.sub, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
                Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(3.dp).background(Th.barTrack)) {
                    Box(Modifier.fillMaxWidth(if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(X_GOLD))
                }
            }
            Spacer(Modifier.width(16.dp))
            HeaderClock()
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp).padding(top = 14.dp)) {

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
            LazyColumn(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Fondu en bas seulement s'il reste du contenu en dessous
                        if (lc.canScrollForward) drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(0.8f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    },
                state = lc, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)
            ) {
                if (showCont) item(key = "cont") {
                    Column {
                        SecHead("Continue watching", "Resume where you stopped")
                        androidx.compose.foundation.lazy.LazyRow(state = lsCont, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(vertical = 6.dp)) {
                            itemsIndexed(cont, key = { _, it -> it.id }) { ix, r ->
                                val wt = watch[r.id]!!
                                val fr = wt.first.toFloat() / wt.second
                                val pct = (fr * 100).toInt()
                                val left = (wt.second - wt.first).coerceAtLeast(0L)
                                CardWrap(w4) {
                                    RecCard(
                                        w4 - 10.dp, chById[r.chId], r, thumbOf(r), "$pct%", 0, "", fr,
                                        r.title,
                                        androidx.compose.ui.text.buildAnnotatedString {
                                            append(r.chName + " · ")
                                            withStyle(androidx.compose.ui.text.SpanStyle(color = X_GOLD)) { append("$pct% watched") }
                                            append(" · ${durTxt(left)} left")
                                        },
                                        { onAct("play", r.id) }, { menu = r },
                                        onFocus = { if (it) { snap(lsCont, ix, 4); reveal(0) } }
                                    )
                                }
                            }
                        }
                    }
                }
                if (showSched) {
                    if (tab == 0) item(key = "sched") {
                        Column {
                            SecHead("Scheduled", "${sched.size} upcoming")
                            androidx.compose.foundation.lazy.LazyRow(state = lsSched, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(vertical = 6.dp)) {
                                itemsIndexed(sched, key = { _, it -> it.id }) { ix, r -> SchedCard(w4, r, ix, 4, lsSched, idxSched) }
                            }
                        }
                    } else {
                        item(key = "sched_h") { SecHead("Scheduled", "${sched.size} upcoming") }
                        itemsIndexed(sched.chunked(4), key = { _, it -> "s_" + it.first().id }) { k, rowItems ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (r in rowItems) SchedCard(w4, r, -1, 4, lsSched, 1 + k)
                            }
                        }
                    }
                }
                if (showDone) {
                    if (tab == 0) item(key = "done") {
                        Column {
                            SecHead("Recorded", "Newest first · Hold OK to delete or rename")
                            androidx.compose.foundation.lazy.LazyRow(state = lsDone, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(vertical = 6.dp)) {
                                itemsIndexed(done, key = { _, it -> it.id }) { ix, r -> DoneCard(w4, r, ix, 4, lsDone, idxDone) }
                            }
                        }
                    } else {
                        item(key = "done_h") { SecHead("Recorded", "Newest first · Hold OK to delete or rename") }
                        itemsIndexed(done.chunked(4), key = { _, it -> "d_" + it.first().id }) { k, rowItems ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (r in rowItems) DoneCard(w4, r, -1, 4, lsDone, 1 + k)
                            }
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
