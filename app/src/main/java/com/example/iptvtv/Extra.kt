@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
// Enregistrements : liste de tous les enregistrements (OK = actions)
// ---------------------------------------------------------------------------
@Composable
fun RecordingsScreen(recs: List<Rec>, onAct: (String, String) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val list = remember(recs) { recs.filter { it.kind == "rec" }.sortedByDescending { it.start } }
    var menu by remember { mutableStateOf<Rec?>(null) }
    val df = remember { SimpleDateFormat("EEE dd MMM", Locale.ENGLISH) }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("Recordings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "OK on a recording for its actions   |   Back to return",
            color = X_MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        if (list.isEmpty()) {
            Text(
                "No recordings yet. In the guide, select a future programme to record it.",
                color = X_MUTED, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list, key = { it.id }) { r ->
                FocusRow({ menu = r }, tile = true, tileColor = Color(0xFF262626)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, color = fcolor(Color.White), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${r.chName}   ${df.format(Date(r.start))}   ${hm(r.start)} - ${hm(r.stop)}",
                            color = fcolor(Color(0xFF8FCBF7)), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    val (txt, col) = when (r.state) {
                        "recording" -> Pair("Recording...", Color(0xFFE5484D))
                        "done" -> Pair("Recorded", Color(0xFF5BD68A))
                        "failed" -> Pair("Failed", Color(0xFFFFB4A9))
                        else -> Pair("Scheduled", Color(0xFFFFE9A8))
                    }
                    Text(txt, color = fcolor(col, Color(0xFF14213D)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    val r = menu
    if (r != null) {
        // l'enregistrement peut avoir changé d'état entre-temps
        val cur = recs.firstOrNull { it.id == r.id }
        if (cur == null) {
            menu = null
        } else {
            PanelDialog(cur.title, { menu = null }, actions = { PButton("Close", { menu = null }) }) {
                if (cur.state == "done" && cur.path.isNotBlank()) {
                    item { PItem("Play", "", { menu = null; onAct("play", cur.id) }, autoFocus = true) }
                }
                if (cur.state == "recording") {
                    item { PItem("Stop recording", "", { menu = null; onAct("stop", cur.id) }, autoFocus = true) }
                }
                if (cur.state == "scheduled") {
                    item { PItem("Cancel recording", "", { menu = null; onAct("cancel", cur.id) }, autoFocus = true) }
                }
                if (cur.state == "done" || cur.state == "failed") {
                    item {
                        PItem(
                            "Delete", if (cur.path.isNotBlank()) "Also deletes the file" else "",
                            { menu = null; onAct("delete", cur.id) }, autoFocus = cur.state == "failed"
                        )
                    }
                }
                if (cur.note.isNotBlank()) item { PInfo(cur.note) }
            }
        }
    }
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            .border(if (f) 3.dp else 1.dp, if (f) Color.White else Color(0xFF2A3140))
            .background(Color(0xFF11151C))
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
                Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0xB3000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ch.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text("AUDIO", color = Color(0xFF8FCBF7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(
                "+  Add a channel", color = X_MUTED, fontSize = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
