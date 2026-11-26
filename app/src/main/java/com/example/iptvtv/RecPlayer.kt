@file:OptIn(UnstableApi::class)

package com.example.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.media3.common.Tracks
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
    // La chaîne en direct s'arrête pendant la lecture de l'enregistrement et ne reprend pas à la sortie.
    LaunchedEffect(Unit) { try { main.stop() } catch (e: Exception) { } }
    var confirm by remember { mutableStateOf(false) }
    val contReq = remember { FocusRequester() }
    val audioReq = remember { FocusRequester() }
    var tracksOpen by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    val okFired = remember { booleanArrayOf(true) }

    // Bouton Home de la télécommande : l'enregistrement se met en pause quand l'app passe en arrière-plan
    val lifeOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeOwner, player) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) { try { player.pause() } catch (e: Exception) { } } }
        lifeOwner.lifecycle.addObserver(obs)
        onDispose { lifeOwner.lifecycle.removeObserver(obs) }
    }

    val list = remember(recs) {
        recs.filter { it.kind == "rec" && it.state == "done" && it.path.isNotBlank() }.sortedByDescending { it.start }
    }
    val idx = list.indexOfFirst { it.id == rec.id }
    val next = if (idx >= 0) list.getOrNull(idx + 1) else null
    val prev = if (idx > 0) list.getOrNull(idx - 1) else null

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
    var trimOpen by remember { mutableStateOf(false) }
    // Rognage non destructif : la lecture reste entre le début et la fin choisis, le fichier n'est pas modifié
    val trimNow by rememberUpdatedState(Pair(rec.trimIn, rec.trimOut))
    val vs = trimNow.first.coerceAtLeast(0L)
    val ve = if (trimNow.second > 0L && dur > 0L) minOf(trimNow.second, dur) else dur
    val vdur = (ve - vs).coerceAtLeast(0L)
    val vpos = (pos - vs).coerceIn(0L, vdur)

    fun poke() { show = true; touch++ }
    fun seekBy(d: Long) {
        val hi = if (dur > 0) (ve - 500L).coerceAtLeast(vs) else Long.MAX_VALUE
        player.seekTo((player.currentPosition + d).coerceIn(vs, hi))
        poke()
    }
    fun toggle() {
        if (ended[0]) { ended[0] = false; player.seekTo(vs); player.play() }
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
            override fun onTracksChanged(t: Tracks) { tracks = t }
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
        val tvs0 = trimNow.first.coerceAtLeast(0L)
        if (saved != null && saved.first > 5000L && saved.second > 0 && saved.first < saved.second * 0.95) {
            player.seekTo(saved.first + tvs0)
        } else if (tvs0 > 0L) {
            player.seekTo(tvs0)
        }
        player.playWhenReady = true
        var n = 0
        while (true) {
            pos = player.currentPosition
            val d = player.duration
            dur = if (d != C.TIME_UNSET && d > 0) d else (rec.stop - rec.start).coerceAtLeast(0L)
            playing = player.isPlaying
            val tvs = trimNow.first.coerceAtLeast(0L)
            val tve = if (trimNow.second > 0L && dur > 0L) minOf(trimNow.second, dur) else dur
            // Fin de la plage choisie : comme une fin normale
            if (trimNow.second > 0L && tve > 0L && pos >= tve - 300L && !ended[0]) {
                ended[0] = true
                player.pause()
                Watch.set(ctx, rec.id, tve - tvs, tve - tvs)
                if (next != null) onPick(next.id) else show = true
            }
            if (tvs > 0L && pos < tvs - 1000L && !ended[0]) player.seekTo(tvs)
            n++
            if (n % 10 == 0 && (pos - tvs) > 5000L && (tve - tvs) > 0L && !ended[0]) Watch.set(ctx, rec.id, pos - tvs, tve - tvs)
            delay(400)
        }
    }
    DisposableEffect(rec.id) {
        onDispose {
            try {
                val p = player.currentPosition
                val d = player.duration
                val tvs = trimNow.first.coerceAtLeast(0L)
                val tve = if (trimNow.second > 0L && d > 0) minOf(trimNow.second, d) else d
                if (p - tvs > 5000L && d > 0 && !ended[0]) Watch.set(ctx, rec.id, p - tvs, (tve - tvs).coerceAtLeast(1L))
                player.release()
            } catch (e: Exception) { }
        }
    }
    // Les commandes se cachent d'elles-mêmes pendant la lecture
    LaunchedEffect(show, touch, playing, confirm, tracksOpen, trimOpen) {
        if (show && playing && !confirm && !tracksOpen && !trimOpen) {
            delay(4500)
            show = false
            try { root.requestFocus() } catch (e: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        delay(150)
        try { root.requestFocus() } catch (e: Exception) { }
    }
    // La lecture se met en pause pendant le choix des points de rognage
    val resumeTrim = remember { booleanArrayOf(false) }
    LaunchedEffect(trimOpen) {
        if (trimOpen) { resumeTrim[0] = player.isPlaying; player.pause() }
        else if (resumeTrim[0]) { resumeTrim[0] = false; if (!ended[0]) player.play() }
    }
    LaunchedEffect(confirm, tracksOpen, trimOpen) {
        delay(80)
        try { if (confirm) contReq.requestFocus() else if (!tracksOpen && !trimOpen) root.requestFocus() } catch (e: Exception) { }
    }
    // Retour : demande confirmation avant d'arrêter la lecture
    val backAt = remember { longArrayOf(0L) }
    BackHandler {
        // Ignore un second appui reçu quasi en même temps (doublon de la télécommande)
        val now = System.currentTimeMillis()
        if (now - backAt[0] > 300L) { backAt[0] = now; confirm = !confirm }
    }
    fun cycleSpeed() {
        val opts = listOf(1f, 1.25f, 1.5f, 2f)
        speed = opts[(opts.indexOf(speed).coerceAtLeast(0) + 1) % opts.size]
        player.setPlaybackSpeed(speed)
        poke()
    }

    val df = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.ENGLISH) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { ev ->
                // Retour : géré ici (avant le système) pour que la fenêtre se ferme au premier appui,
                // sans que le focus soit simplement retiré du bouton
                if (ev.key == Key.Back) {
                    if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) {
                        val now = System.currentTimeMillis()
                        if (now - backAt[0] > 300L) { backAt[0] = now; confirm = !confirm }
                    }
                    return@onPreviewKeyEvent true
                }
                // OK maintenu = vitesse (la touche ● de la télécommande est le bouton Home du système, on ne peut pas l'utiliser)
                if (!confirm && rootFocused && !tracksOpen && !trimOpen) {
                    if (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter) {
                        return@onPreviewKeyEvent okLong(ev, okFired, { toggle() }, { cycleSpeed() })
                    }
                }
                if (confirm || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val rep = ev.nativeKeyEvent.repeatCount
                val step = if (rep > 10) 60_000L else if (rep > 3) 30_000L else 10_000L
                when (ev.key) {
                    // Touches média de la télécommande : actives quel que soit le bouton sélectionné
                    Key.MediaRewind -> { seekBy(-step); true }
                    Key.MediaFastForward -> { seekBy(step); true }
                    Key.MediaPlayPause -> { if (rep == 0) toggle(); true }
                    Key.MediaPlay -> { if (!player.isPlaying) toggle(); true }
                    Key.MediaPause -> { if (player.isPlaying) toggle(); true }
                    Key.MediaStop -> { confirm = true; true }
                    Key.MediaNext, Key.ChannelUp -> { if (rep == 0) next?.let { onPick(it.id) }; true }
                    Key.MediaPrevious, Key.ChannelDown -> { if (rep == 0) prev?.let { onPick(it.id) }; true }
                    else -> {
                        if (!rootFocused) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionLeft -> { seekBy(-step); true }
                            Key.DirectionRight -> { seekBy(step); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (rep == 0) toggle(); true }
                            Key.DirectionUp -> {
                                if (show) { try { audioReq.requestFocus() } catch (e: Exception) { } }
                                poke(); true
                            }
                            Key.DirectionDown -> {
                                if (show) { try { btn.requestFocus() } catch (e: Exception) { } } else poke()
                                true
                            }
                            else -> false
                        }
                    }
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
                    rec.chName + " · " + df.format(Date(rec.start)) + (if (rec.trimIn > 0L || rec.trimOut > 0L) " · Trimmed" else ""), color = Th.sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Bas : barre de progression et boutons
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Th.ovMid, 1f to Th.ovEnd))
                    .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 8.dp)
            ) {
                // Haut de la barre : audio et sous-titres (accessibles avec la flèche haut)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown) {
                                when (ev.key) {
                                    Key.DirectionUp -> { poke(); true }
                                    Key.DirectionDown -> { try { root.requestFocus() } catch (e: Exception) { }; poke(); true }
                                    else -> { touch++; false }
                                }
                            } else false
                        },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RpBtn(7, "", audioReq) { tracksOpen = true; poke() }
                    RpBtn(8, "CC") { tracksOpen = true; poke() }
                    RpBtn(9, "") { trimOpen = true; poke() }
                }
                val frac = if (vdur > 0) (vpos.toFloat() / vdur).coerceIn(0f, 1f) else 0f
                BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Th.barTrack))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(RP_GOLD))
                    Box(
                        Modifier.align(Alignment.CenterStart).offset(x = (maxWidth * frac) - 7.dp).size(14.dp)
                            .clip(CircleShape).background(if (rootFocused) Color.White else RP_GOLD)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(clockTxt(vpos), color = Th.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("-" + clockTxt((vdur - vpos).coerceAtLeast(0L)) + "  /  " + clockTxt(vdur), color = Th.sub, fontSize = 13.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                try { root.requestFocus() } catch (e: Exception) { }
                                poke(); true
                            } else {
                                if (ev.type == KeyEventType.KeyDown) touch++
                                false
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton lecture exactement au centre : vitesse à gauche, suivant à droite
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        RpBtn(6, (if (speed == speed.toInt().toFloat()) "${speed.toInt()}" else "$speed") + "x") { cycleSpeed() }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RpBtn(0, "60") { seekBy(-60_000L) }
                        RpBtn(0, "10") { seekBy(-10_000L) }
                        RpBtn(if (playing) 3 else 2, "", btn, big = true) { toggle() }
                        RpBtn(1, "10") { seekBy(10_000L) }
                        RpBtn(1, "60") { seekBy(60_000L) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (next != null) RpBtn(4, "") { onPick(next.id) }
                    }
                }
            }
        }
        if (tracksOpen) TracksDialog(tracks, player) { tracksOpen = false }
        if (trimOpen) {
            val cur = player.currentPosition
            val trimmed = rec.trimIn > 0L || rec.trimOut > 0L
            PanelDialog("Trim", { trimOpen = false }, actions = { PButton("Close", { trimOpen = false }) }) {
                item { PInfo("Now " + clockTxt(cur) + "   ·   Start " + clockTxt(vs) + "   ·   End " + clockTxt(ve) + "   (the file itself is not changed)") }
                item {
                    PItem("Set start here", clockTxt(cur), {
                        val out = if (rec.trimOut in 1L..(cur + 1000L)) 0L else rec.trimOut
                        Dvr.update(ctx, rec.id) { it.copy(trimIn = cur, trimOut = out) }
                        trimOpen = false
                    }, autoFocus = true)
                }
                item {
                    PItem("Set end here", clockTxt(cur), {
                        if (cur > vs + 1000L) {
                            Dvr.update(ctx, rec.id) { it.copy(trimOut = cur) }
                            // On recule un peu pour voir la fin de la plage plutôt que de sauter à la suite
                            player.seekTo((cur - 5000L).coerceAtLeast(vs))
                        }
                        trimOpen = false
                    })
                }
                if (trimmed) item {
                    PItem("Clear trim", "Play the whole recording", {
                        Dvr.update(ctx, rec.id) { it.copy(trimIn = 0L, trimOut = 0L) }
                        trimOpen = false
                    })
                }
            }
        }
        if (confirm) {
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Th.cardBg).padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Stop playing?", color = Th.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(rec.title, color = Th.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp).widthIn(max = 260.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RpPill("Continue watching", contReq) { confirm = false }
                        RpPill("Stop", null) { onBack() }
                    }
                }
            }
        }
    }
}

@Composable
private fun RpPill(label: String, req: FocusRequester?, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .clip(RoundedCornerShape(18.dp))
            .background(if (f) RP_GOLD else Color(0x22FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (f) Color(0xFF1A1A1A) else Th.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Boutons ronds avec de vraies icônes dessinées, toutes centrées
// kind : 0 = saut arrière, 1 = saut avant, 2 = lecture, 3 = pause, 4 = suivant, 5 = fermer, 6 = vitesse
@Composable
private fun RpBtn(kind: Int, label: String, req: FocusRequester? = null, big: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val sz = if (big) 44.dp else 34.dp
    val sc by animateFloatAsState(if (f) 1.12f else 1f, label = "rpb")
    val ink = if (f) Color(0xFF1A1A1A) else Color.White
    Box(
        Modifier.padding(horizontal = 5.dp).size(sz).scale(sc)
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .clip(CircleShape)
            .background(if (f) RP_GOLD else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            0, 1 -> {
                Canvas(Modifier.size(sz * 0.8f)) { rpSeek(kind == 1, ink) }
                Text(label, color = ink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            6 -> Text(label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            8 -> Box(Modifier.size(24.dp, 17.dp).border(1.5.dp, ink, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                Text(label, color = ink, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            else -> Canvas(Modifier.size(sz * (if (kind == 7 || kind == 9) 0.5f else 0.42f))) { rpGlyph(kind, ink) }
        }
    }
}

private fun DrawScope.rpSeek(fwd: Boolean, col: Color) {
    val s = size.minDimension
    val c = Offset(s / 2f, s / 2f)
    val r = s * 0.40f
    val start = if (fwd) -60f else -120f
    val sweep = if (fwd) 300f else -300f
    drawArc(col, start, sweep, false, topLeft = Offset(c.x - r, c.y - r), size = Size(2f * r, 2f * r), style = Stroke(s * 0.08f))
    val th = Math.toRadians((start + sweep).toDouble())
    val p = Offset(c.x + r * cos(th).toFloat(), c.y + r * sin(th).toFloat())
    val d = if (fwd) Offset((-sin(th)).toFloat(), cos(th).toFloat()) else Offset(sin(th).toFloat(), (-cos(th)).toFloat())
    val n = Offset(-d.y, d.x)
    val h = s * 0.17f
    val tip = Offset(p.x + d.x * h * 1.1f, p.y + d.y * h * 1.1f)
    val b1 = Offset(p.x + n.x * h, p.y + n.y * h)
    val b2 = Offset(p.x - n.x * h, p.y - n.y * h)
    val path = Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }
    drawPath(path, col)
}

private fun DrawScope.rpGlyph(kind: Int, col: Color) {
    val s = size.minDimension
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val pa = Path().apply { moveTo(x1 * s, y1 * s); lineTo(x2 * s, y2 * s); lineTo(x3 * s, y3 * s); close() }
        drawPath(pa, col)
    }
    when (kind) {
        2 -> tri(0.22f, 0.04f, 0.22f, 0.96f, 0.96f, 0.5f)
        3 -> {
            drawRect(col, Offset(0.12f * s, 0.06f * s), Size(0.28f * s, 0.88f * s))
            drawRect(col, Offset(0.60f * s, 0.06f * s), Size(0.28f * s, 0.88f * s))
        }
        4 -> {
            tri(0.04f, 0.06f, 0.04f, 0.94f, 0.72f, 0.5f)
            drawRect(col, Offset(0.78f * s, 0.06f * s), Size(0.17f * s, 0.88f * s))
        }
        9 -> {
            // Ciseaux : deux anneaux et deux lames croisées
            drawCircle(col, 0.14f * s, Offset(0.18f * s, 0.2f * s), style = Stroke(s * 0.08f))
            drawCircle(col, 0.14f * s, Offset(0.18f * s, 0.8f * s), style = Stroke(s * 0.08f))
            drawLine(col, Offset(0.3f * s, 0.28f * s), Offset(0.96f * s, 0.74f * s), s * 0.08f, StrokeCap.Round)
            drawLine(col, Offset(0.3f * s, 0.72f * s), Offset(0.96f * s, 0.26f * s), s * 0.08f, StrokeCap.Round)
        }
        7 -> {
            // Haut-parleur : boîtier + cône + deux ondes
            drawRect(col, Offset(0.04f * s, 0.36f * s), Size(0.22f * s, 0.28f * s))
            val cone = Path().apply { moveTo(0.26f * s, 0.36f * s); lineTo(0.56f * s, 0.12f * s); lineTo(0.56f * s, 0.88f * s); lineTo(0.26f * s, 0.64f * s); close() }
            drawPath(cone, col)
            drawArc(col, -40f, 80f, false, topLeft = Offset(0.40f * s, 0.30f * s), size = Size(0.40f * s, 0.40f * s), style = Stroke(s * 0.07f))
            drawArc(col, -50f, 100f, false, topLeft = Offset(0.38f * s, 0.14f * s), size = Size(0.62f * s, 0.72f * s), style = Stroke(s * 0.07f))
        }
        5 -> {
            drawLine(col, Offset(0.1f * s, 0.1f * s), Offset(0.9f * s, 0.9f * s), s * 0.14f, StrokeCap.Round)
            drawLine(col, Offset(0.9f * s, 0.1f * s), Offset(0.1f * s, 0.9f * s), s * 0.14f, StrokeCap.Round)
        }
    }
}
