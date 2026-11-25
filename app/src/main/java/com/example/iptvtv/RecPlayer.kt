@file:OptIn(UnstableApi::class)

package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RP_GOLD = Color(0xFFE0B85A)

private fun clockTxt(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

// ---------------------------------------------------------------------------
// Lecteur dédié aux enregistrements : barre de progression, saut, vitesse, reprise, suivant
// ---------------------------------------------------------------------------
@Composable
fun RecPlayerScreen(main: ExoPlayer, rec: Rec?, recs: List<Rec>, onPick: (String) -> Unit, onBack: () -> Unit) {
    if (rec == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val ctx = LocalContext.current
    val player = remember(rec.id) {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx)))
            .build()
    }
    // Le lecteur principal (TV en direct) se met en pause pendant la lecture, et reprend à la sortie.
    DisposableEffect(Unit) {
        val was = main.playWhenReady
        main.playWhenReady = false
        onDispose { main.playWhenReady = was }
    }

    val list = remember(recs) {
        recs.filter { it.kind == "rec" && it.state == "done" && it.path.isNotBlank() }.sortedByDescending { it.start }
    }
    val idx = list.indexOfFirst { it.id == rec.id }
    val next = if (idx >= 0) list.getOrNull(idx + 1) else null

    var show by remember { mutableStateOf(true) }
    var touch by remember { mutableStateOf(0) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(1f) }
    var rootFocused by remember { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    val btn = remember { FocusRequester() }
    val ended = remember(rec.id) { booleanArrayOf(false) }

    fun poke() { show = true; touch++ }
    fun seekBy(d: Long) {
        val total = if (dur > 0) dur else Long.MAX_VALUE
        player.seekTo((player.currentPosition + d).coerceIn(0L, (total - 500L).coerceAtLeast(0L)))
        poke()
    }
    fun toggle() {
        if (ended[0]) { ended[0] = false; player.seekTo(0L); player.play() }
        else if (player.isPlaying) player.pause() else player.play()
        poke()
    }

    // Chargement, reprise et suivi de la position
    LaunchedEffect(rec.id) {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                err = "This recording can't be played."
                show = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    ended[0] = true
                    val d = if (player.duration > 0) player.duration else 0L
                    if (d > 0) Watch.set(ctx, rec.id, d, d)
                    if (next != null) onPick(next.id) else show = true
                }
            }
        })
        player.setMediaItem(MediaItem.fromUri(rec.path))
        player.prepare()
        val saved = Watch.get(ctx, rec.id)
        if (saved != null && saved.first > 5000L && saved.second > 0 && saved.first < saved.second * 0.95) {
            player.seekTo(saved.first)
        }
        player.playWhenReady = true
        var n = 0
        while (true) {
            pos = player.currentPosition
            val d = player.duration
            dur = if (d != C.TIME_UNSET && d > 0) d else (rec.stop - rec.start).coerceAtLeast(0L)
            playing = player.isPlaying
            n++
            if (n % 10 == 0 && pos > 5000L && dur > 0L && !ended[0]) Watch.set(ctx, rec.id, pos, dur)
            delay(400)
        }
    }
    DisposableEffect(rec.id) {
        onDispose {
            try {
                val p = player.currentPosition
                val d = player.duration
                if (p > 5000L && d > 0 && !ended[0]) Watch.set(ctx, rec.id, p, d)
                player.release()
            } catch (e: Exception) { }
        }
    }
    // Les commandes se cachent d'elles-mêmes pendant la lecture
    LaunchedEffect(show, touch, playing) {
        if (show && playing) {
            delay(4500)
            show = false
            try { root.requestFocus() } catch (e: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        delay(150)
        try { root.requestFocus() } catch (e: Exception) { }
    }
    BackHandler {
        if (show && playing) { show = false; try { root.requestFocus() } catch (e: Exception) { } }
        else onBack()
    }

    val df = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.ENGLISH) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (!rootFocused || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val rep = ev.nativeKeyEvent.repeatCount
                when (ev.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val step = if (rep > 10) 60_000L else if (rep > 3) 30_000L else 10_000L
                        seekBy(if (ev.key == Key.DirectionLeft) -step else step); true
                    }
                    Key.MediaRewind -> { seekBy(-60_000L); true }
                    Key.MediaFastForward -> { seekBy(60_000L); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> { if (rep == 0) toggle(); true }
                    Key.MediaPlay -> { if (!player.isPlaying) toggle(); true }
                    Key.MediaPause -> { if (player.isPlaying) toggle(); true }
                    Key.DirectionUp -> { poke(); true }
                    Key.DirectionDown -> {
                        if (show) { try { btn.requestFocus() } catch (e: Exception) { } } else poke()
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    isFocusable = false
                    this.player = player
                    setErrorMessageProvider { _ -> android.util.Pair(0, "") }
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering && err.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = RP_GOLD, strokeWidth = 3.dp)
        }
        if (err.isNotEmpty()) {
            Text(err, color = Th.text, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (show) {
            // En-tête : titre et chaîne
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Th.ovTop, 1f to Color.Transparent))
                    .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 40.dp)
            ) {
                Text(rec.title, color = Th.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    rec.chName + " · " + df.format(Date(rec.start)), color = Th.sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Bas : barre de progression et boutons
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Th.ovMid, 1f to Th.ovEnd))
                    .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 28.dp)
            ) {
                val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Th.barTrack))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(RP_GOLD))
                    Box(
                        Modifier.align(Alignment.CenterStart).offset(x = (maxWidth * frac) - 7.dp).size(14.dp)
                            .clip(CircleShape).background(if (rootFocused) Color.White else RP_GOLD)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(clockTxt(pos), color = Th.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("-" + clockTxt((dur - pos).coerceAtLeast(0L)) + "  /  " + clockTxt(dur), color = Th.sub, fontSize = 13.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                try { root.requestFocus() } catch (e: Exception) { }
                                poke(); true
                            } else {
                                if (ev.type == KeyEventType.KeyDown) touch++
                                false
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopTab("-1 min", false, hPad = 12.dp) { seekBy(-60_000L) }
                    TopTab("-10 s", false, hPad = 12.dp) { seekBy(-10_000L) }
                    TopTab(if (playing) "Pause" else "Play", true, btn, hPad = 22.dp) { toggle() }
                    TopTab("+10 s", false, hPad = 12.dp) { seekBy(10_000L) }
                    TopTab("+1 min", false, hPad = 12.dp) { seekBy(60_000L) }
                    Spacer(Modifier.width(18.dp))
                    TopTab(
                        (if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "$speed") + "x", false, hPad = 12.dp
                    ) {
                        val opts = listOf(1f, 1.25f, 1.5f, 2f)
                        speed = opts[(opts.indexOf(speed).coerceAtLeast(0) + 1) % opts.size]
                        player.setPlaybackSpeed(speed)
                        poke()
                    }
                    if (next != null) TopTab("Next", false, hPad = 12.dp) { onPick(next.id) }
                    TopTab("Back", false, hPad = 12.dp) { onBack() }
                }
            }
        }
    }
}
