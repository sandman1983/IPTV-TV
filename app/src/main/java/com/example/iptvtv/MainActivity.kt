package com.example.iptvtv

import android.os.Bundle
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.media3.common.C
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

private val MUTED = Color(0xFFB0B8C8)
private val ACCENT = Color(0xFF1EB6E8)

data class Channel(
    val id: Int, val name: String, val icon: String, val epgId: String, val cat: String,
    val hidden: Boolean = false, val url: String = "", val src: String = ""
)

data class Prog(
    val title: String, val sub: String, val cat: String, val icon: String,
    val start: Long, val stop: Long, val desc: String = ""
)

data class GuideCh(val id: String, val name: String, val icon: String)

// Guide : liste de programmes par chaîne ; "en cours" et "à suivre" sont calculés à l'heure actuelle.
class Epg(val progs: Map<String, List<Prog>>, val chans: List<GuideCh> = emptyList()) {
    val icons: Map<String, String> by lazy {
        chans.filter { it.icon.isNotBlank() }.associate { it.id to it.icon }
    }

    fun now(id: String): Prog? {
        val t = System.currentTimeMillis()
        return progs[id]?.firstOrNull { it.start <= t && t < it.stop }
    }

    fun upcoming(id: String, n: Int): List<Prog> {
        val t = System.currentTimeMillis()
        return progs[id]?.filter { it.start > t }?.take(n) ?: emptyList()
    }

    fun touch(): Epg = Epg(progs, chans)
}

// Fusionne deux guides : le premier a la priorité.
fun mergeEpg(a: Epg, b: Epg): Epg {
    val ids = a.chans.map { it.id }.toHashSet()
    return Epg(b.progs + a.progs, a.chans + b.chans.filter { it.id !in ids })
}

private var startChecked = false

fun norm(h: String): String {
    val t = h.trim().trimEnd('/')
    return if (t.startsWith("http")) t else "http://$t"
}

fun get(url: String): String = URL(url).openStream().bufferedReader().use { it.readText() }

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun parseDate(f: SimpleDateFormat, s: String?): Long =
    try { f.parse(s ?: "")?.time ?: 0L } catch (ex: Exception) { 0L }

// Après un appui long qui ouvre un panneau, la touche OK est encore enfoncée : les composants
// du nouveau panneau doivent ignorer ses répétitions et son relâchement.
object HoldGuard {
    @Volatile var block = false
    @Volatile var since = 0L
}

// Renvoie true si l'événement doit être ignoré (suite d'un appui long déjà traité).
// - Les répétitions (touche maintenue) sont toujours ignorées tant que la garde est active.
// - Quand une nouvelle fenêtre s'ouvre, l'ancienne reçoit un faux "relâchement" : on l'ignore pendant 0,8 s.
// - Un vrai nouvel appui (repeatCount 0) n'est accepté qu'après 1,2 s.
fun holdGuard(ev: KeyEvent): Boolean {
    val k = ev.key
    if (k != Key.DirectionCenter && k != Key.Enter && k != Key.NumPadEnter) return false
    if (!HoldGuard.block) return false
    val age = System.currentTimeMillis() - HoldGuard.since
    if (ev.type == KeyEventType.KeyDown) {
        if (ev.nativeKeyEvent.repeatCount == 0 && age > 1200) {
            HoldGuard.block = false
            return false
        }
        return true
    }
    if (ev.type == KeyEventType.KeyUp && age > 800) HoldGuard.block = false
    return true
}

// Appui court sur OK = onShort, appui long (environ 0,5 s) = onLong.
fun okLong(ev: KeyEvent, fired: BooleanArray, onShort: () -> Unit, onLong: () -> Unit): Boolean {
    val k = ev.key
    if (k != Key.DirectionCenter && k != Key.Enter && k != Key.NumPadEnter) return false
    val rc = ev.nativeKeyEvent.repeatCount
    if (ev.type == KeyEventType.KeyDown) {
        if (rc == 0) {
            fired[0] = false
        } else if (rc == 1 && !fired[0]) {
            fired[0] = true
            HoldGuard.block = true
            HoldGuard.since = System.currentTimeMillis()
            onLong()
        }
        return true
    }
    if (ev.type == KeyEventType.KeyUp) {
        if (!fired[0]) onShort()
        fired[0] = false
        return true
    }
    return false
}

// Lit un guide XMLTV (.gz ou non) en flux : chaînes du guide (nom, logo) et, pour chaque chaîne,
// tous les programmes de la fenêtre [maintenant - 3 h ; maintenant + 36 h].
suspend fun loadEpg(url: String): Epg = withContext(Dispatchers.IO) {
    val raw = URL(url).openStream().buffered()
    raw.mark(2)
    val b1 = raw.read()
    val b2 = raw.read()
    raw.reset()
    val input = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
    val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    val now = System.currentTimeMillis()
    val progs = HashMap<String, ArrayList<Prog>>()
    val chans = ArrayList<GuideCh>()
    val p = Xml.newPullParser()
    p.setInput(input, null)
    var inCh = false
    var chId = ""
    var chName = ""
    var chIcon = ""
    var ch = ""
    var st = 0L
    var sp = 0L
    var title = ""
    var sub = ""
    var desc = ""
    var cat = ""
    var icon = ""
    var inProg = false
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
            val n = p.name
            if (n == "channel") {
                inCh = true
                chId = p.getAttributeValue(null, "id") ?: ""
                chName = ""
                chIcon = ""
            } else if (n == "display-name" && inCh) {
                val t = p.nextText()
                if (chName.isEmpty()) chName = t
            } else if (n == "icon" && inCh) {
                chIcon = p.getAttributeValue(null, "src") ?: ""
            } else if (n == "programme") {
                inProg = true
                ch = p.getAttributeValue(null, "channel") ?: ""
                st = parseDate(fmt, p.getAttributeValue(null, "start"))
                sp = parseDate(fmt, p.getAttributeValue(null, "stop"))
                title = ""
                sub = ""
                desc = ""
                cat = ""
                icon = ""
            } else if (n == "title" && inProg) {
                title = p.nextText()
            } else if (n == "sub-title" && inProg) {
                sub = p.nextText()
            } else if (n == "desc" && inProg) {
                desc = p.nextText()
            } else if (n == "category" && inProg) {
                val t = p.nextText()
                if (cat.isEmpty()) cat = t
            } else if (n == "icon" && inProg) {
                icon = p.getAttributeValue(null, "src") ?: ""
            }
        } else if (ev == XmlPullParser.END_TAG) {
            val n = p.name
            if (n == "channel" && inCh) {
                chans.add(GuideCh(chId, chName, chIcon))
                inCh = false
            } else if (n == "programme") {
                inProg = false
                if (sp > now - 3 * 3600000L && st < now + 36 * 3600000L) {
                    progs.getOrPut(ch) { ArrayList() }.add(Prog(title, sub.ifBlank { desc }, cat, icon, st, sp, desc.take(1500)))
                }
            }
        }
        ev = p.next()
    }
    input.close()
    for (l in progs.values) l.sortBy { it.start }
    Epg(progs, chans)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cache des logos : mémoire + disque (200 Mo), en ignorant les en-têtes "no-cache" des serveurs
        try {
            coil.Coil.setImageLoader(
                coil.ImageLoader.Builder(applicationContext)
                    .memoryCache { coil.memory.MemoryCache.Builder(applicationContext).maxSizePercent(0.25).build() }
                    .diskCache {
                        coil.disk.DiskCache.Builder()
                            .directory(applicationContext.filesDir.resolve("img_cache"))
                            .maxSizeBytes(200L * 1024 * 1024)
                            .build()
                    }
                    .respectCacheHeaders(false)
                    .crossfade(false)
                    .build()
            )
        } catch (e: Exception) { }
        Th.load(this)
        setContent { App() }
    }

    // Compose can throw while moving focus during a fast scroll (a known library bug).
    // Ignore that single key press instead of closing the app.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            true
        }
    }
}

fun activityOf(c: Context): Activity? {
    var x = c
    while (x is ContextWrapper) {
        if (x is Activity) return x
        x = x.baseContext
    }
    return null
}

// Boîte de confirmation de sortie, en bas à droite.
@Composable
fun ExitBox(modifier: Modifier, onYes: () -> Unit, onNo: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try { fr.requestFocus() } catch (ex: Exception) { }
    }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Th.popup).padding(16.dp)) {
        Text("Exit the app?", color = Th.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusRow(onYes, Modifier.width(110.dp).focusRequester(fr)) { Text("Yes", color = fcolor(Color.White), fontSize = 16.sp) }
            FocusRow(onNo, Modifier.width(110.dp)) { Text("No", color = fcolor(Color.White), fontSize = 16.sp) }
        }
    }
}

@Composable
fun ReminderBox(r: Rec, modifier: Modifier, onWatch: () -> Unit, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try { fr.requestFocus() } catch (ex: Exception) { }
    }
    Column(modifier.width(360.dp).clip(RoundedCornerShape(12.dp)).background(Th.popup).padding(16.dp)) {
        Text("Reminder", color = Th.time, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(r.title, color = Th.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(r.chName + " · " + hm(r.start), color = MUTED, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusRow(onWatch, Modifier.width(140.dp).focusRequester(fr)) { Text("Watch", color = fcolor(Color.White), fontSize = 16.sp) }
            FocusRow(onClose, Modifier.width(140.dp)) { Text("Dismiss", color = fcolor(Color.White), fontSize = 16.sp) }
        }
    }
}

@Composable
fun Solid(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Th.bgBottom)) { content() }
}

@OptIn(UnstableApi::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var sources by remember { mutableStateOf(loadSources(ctx)) }
    var cfg by remember { mutableStateOf(loadCfg(ctx)) }
    var hist by remember { mutableStateOf(loadHist(ctx)) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var epg by remember { mutableStateOf(Epg(emptyMap())) }
    var status by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var bg by remember { mutableStateOf<Channel?>(null) }
    var full by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("") }
    var settingsPage by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf(0L) }
    var editCh by remember { mutableStateOf<Int?>(null) }
    var editCat by remember { mutableStateOf<String?>(null) }
    var exitAsk by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        CrashLog.install(ctx)
        crash = withContext(Dispatchers.IO) { CrashLog.take(ctx) }
    }
    var weather by remember { mutableStateOf("") }
    var lastBack by remember { mutableStateOf(0L) }
    var resumeTick by remember { mutableStateOf(0) }
    var miniStart by remember { mutableStateOf(true) }
    var fromGuide by remember { mutableStateOf(false) }
    var quietStart by remember { mutableStateOf(false) }
    var playErr by remember { mutableStateOf("") }
    var errShow by remember { mutableStateOf(false) }
    // Le message d'erreur n'apparaît que si le problème dure (les coupures brèves se rétablissent seules),
    // puis il disparaît tout seul au bout de quelques secondes.
    LaunchedEffect(playErr) {
        errShow = false
        if (playErr.isNotEmpty()) {
            delay(6000)
            errShow = true
            delay(10000)
            errShow = false
        }
    }
    var reminder by remember { mutableStateOf<Rec?>(null) }
    val recs = remember(Dvr.tick) { loadRecs(ctx) }
    val cfgNow by rememberUpdatedState(cfg)

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                PlayerPrefs.bufferSec(ctx) * 1000, PlayerPrefs.bufferSec(ctx) * 3000,
                3000.coerceAtMost(PlayerPrefs.bufferSec(ctx) * 1000), 6000.coerceAtMost(PlayerPrefs.bufferSec(ctx) * 1000)
            )
            .build()
        val passthrough = ctx.getSharedPreferences("iptv_audio", android.content.Context.MODE_PRIVATE).getBoolean("pt", false)
        val renderers = if (passthrough) {
            androidx.media3.exoplayer.DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        } else {
            // Mode sûr (par défaut) : son décodé en PCM stéréo, pas de passthrough HDMI
            object : androidx.media3.exoplayer.DefaultRenderersFactory(ctx) {
                override fun buildAudioSink(
                    context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink? =
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
            }.setEnableDecoderFallback(true)
        }
        PlayerPrefs.applyCodecs(ctx, renderers)
        ExoPlayer.Builder(ctx, renderers)
            .setLoadControl(load)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http), extractors)
            )
            .build()
    }
    // Reconnexion automatique (3 essais espacés de 3 s) sauf erreurs 4xx (accès refusé, introuvable).
    val retries = remember { intArrayOf(0) }
    DisposableEffect(exo) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val l = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playErr = friendlyError(error)
                if ((error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
                ) && retries[0] >= 1
                ) {
                    // Deux échecs de suite : la sortie 5.1 est refusée, on passe en stéréo (le 5.1 / passthrough reste essayé en premier)
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setMaxAudioChannelCount(2)
                        .build()
                }
                val code = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                if (code in 400..499 || retries[0] >= 3) return
                retries[0]++
                handler.postDelayed({
                    try {
                        exo.prepare()
                        exo.playWhenReady = true
                    } catch (ex: Exception) { }
                }, 3000)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retries[0] = 0; playErr = "" }
            }
        }
        exo.addListener(l)
        onDispose { handler.removeCallbacksAndMessages(null); exo.removeListener(l) }
    }
    LaunchedEffect(exo) {
        var stuck = 0
        while (true) {
            delay(1000)
            if (exo.playbackState == Player.STATE_BUFFERING && exo.playWhenReady) stuck++ else stuck = 0
            if (stuck >= 10) {
                stuck = 0
                try {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                } catch (ex: Exception) { }
            }
        }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }
    // Réglages du lecteur appliqués au démarrage et à chaque retour des réglages
    LaunchedEffect(screen) { applyPlayerPrefs(ctx, exo) }

    val all = remember(channels, cfg, epg.chans) { applyCfg(channels, cfg, epg.icons) }
    val shown = remember(all) { all.filter { !it.hidden } }
    val cur = bg?.let { b -> all.firstOrNull { it.id == b.id } ?: b }

    fun play(c: Channel) {
        bg = c
        hist = pushHist(hist, c.id)
        saveHist(ctx, hist)
    }

    fun recAct(act: String, id: String) {
                        val r = recs.firstOrNull { it.id == id }
                        if (r != null) {
                            when (act) {
                                "play" -> {
                                    play(Channel(-Math.abs(r.id.hashCode()) - 1, r.title, "", "", "Recordings", false, r.path, ""))
                                    full = true; quietStart = false
                                    miniStart = false
                                    screen = ""
                                }
                                "stop" -> Dvr.stop(r.id)
                                "cancel" -> Dvr.remove(ctx, r.id)
                                "delete" -> Dvr.delete(ctx, r)
                            }
                        }
                    
    }

    LaunchedEffect(Unit) { Dvr.cleanup(ctx) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val c = cfgNow
            for (r in loadRecs(ctx)) {
                if (r.state != "scheduled") continue
                if (r.kind == "rec") {
                    if (now >= r.stop + c.recStop * 60000L) {
                        Dvr.update(ctx, r.id) { it.copy(state = "failed", note = "Missed (app was not running)") }
                    } else if (now >= r.start - c.recStart * 60000L) {
                        Dvr.start(ctx, r, c)
                    }
                } else if (c.remOn) {
                    if (now >= r.stop) {
                        Dvr.update(ctx, r.id) { it.copy(state = "done") }
                    } else if (now >= r.start - 60000L) {
                        Dvr.update(ctx, r.id) { it.copy(state = "done") }
                        reminder = r
                    }
                }
            }
            delay(10000)
        }
    }
    LaunchedEffect(reminder) {
        if (reminder != null) {
            delay(45000)
            reminder = null
        }
    }

    // Retour : action normale, mais un 2e appui en moins de 1,5 s ouvre la confirmation de sortie.
    fun onRootBack(normal: () -> Unit) {
        if (exitAsk) {
            exitAsk = false
            return
        }
        val t = System.currentTimeMillis()
        if (t - lastBack < 1500) {
            exitAsk = true
            lastBack = 0L
        } else {
            lastBack = t
            normal()
        }
    }
    BackHandler(enabled = screen == "" && !(cur != null && full) && sources.isNotEmpty()) {
        onRootBack {
            if (cur != null) { full = true; quietStart = true }
            else Toast.makeText(ctx, "Press Back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(exitAsk) {
        if (exitAsk) {
            delay(8000)
            exitAsk = false
        }
    }

    // Coupe le son quand l'app passe en arrière-plan ; reprend la chaîne au retour.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val stopped = BooleanArray(1)
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exo.pause()
                stopped[0] = true
            } else if (event == Lifecycle.Event.ON_START && stopped[0]) {
                stopped[0] = false
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Au démarrage : la dernière chaîne regardée joue en fond de l'accueil.
    LaunchedEffect(all) {
        if (bg == null) {
            val id = hist.firstOrNull()
            if (id != null) bg = all.firstOrNull { it.id == id }
        }
    }
    LaunchedEffect(bg?.id, resumeTick) {
        val c = bg
        if (c != null) {
            resetTracks(exo)
            retries[0] = 0
            playErr = ""
            try {
                exo.setMediaItem(MediaItem.fromUri(c.url))
                exo.prepare()
                exo.playWhenReady = true
            } catch (e: IllegalStateException) {
                // e.g. a DASH (.mpd) stream: this player build cannot open it. Do not crash.
                exo.stop()
                Toast.makeText(ctx, "This channel's stream type is not supported (" + c.name + ")", Toast.LENGTH_LONG).show()
            }
        } else {
            exo.pause()
        }
    }
    LaunchedEffect(full, cfg.bgSound) { exo.volume = if (!full && !cfg.bgSound) 0f else 1f }

    // Météo (Open-Meteo) pour la ville choisie, actualisée toutes les 30 minutes.
    LaunchedEffect(cfg.city) {
        if (cfg.city.isBlank()) {
            weather = ""
        } else {
            while (true) {
                weather = fetchWeather(cfg.city.trim())
                delay(30 * 60 * 1000L)
            }
        }
    }

    // Rafraîchit l'affichage "en cours / à suivre" toutes les 30 s.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            epg = epg.touch()
        }
    }

    // Chargement des sources et des guides, puis rechargement des guides toutes les 30 minutes.
    LaunchedEffect(sources, cfg.epgs, reload) {
        val sources = sources.filter { it.enabled }
        val cacheKey = sources.joinToString("|") { "${it.id}${it.type}${it.host}${it.user}${it.pass}${it.url}${it.epg}${it.output}${it.useAuth}" } +
            "#" + cfg.epgs.joinToString("|")
        val force = reload > 0
        // 0 = synchronisation périodique désactivée
        val maxAge = if (cfg.syncHours > 0) cfg.syncHours * 3600_000L else Long.MAX_VALUE / 4
        PlayerPrefs.srcInsecure = sources.any { it.insecure }
        PlayerPrefs.applyInsecure(PlayerPrefs.bool(ctx, "insecure", false) || PlayerPrefs.srcInsecure)
        val firstRun = !startChecked
        startChecked = true
        var haveCache = false
        var fresh = false
        if (!force) {
            val cc = withContext(Dispatchers.IO) { GuideCache.loadChannels(ctx, cacheKey) }
            val ce = withContext(Dispatchers.IO) { GuideCache.loadEpg(ctx, cacheKey) }
            if (cc != null && ce != null) {
                channels = cc.first
                epg = ce.first
                lastSync = ce.second
                haveCache = true
                fresh = System.currentTimeMillis() - ce.second < maxAge && !(cfg.updateOnStart && firstRun)
                status = ""
                report = listOf("Loaded from cache")
            }
        }
        suspend fun fetchGuides(urls: List<String>, into: MutableList<String>, show: Boolean): Epg {
            var g = Epg(emptyMap())
            var n = 0
            for (u in urls) {
                n++
                if (show) status = "Loading guide $n/${urls.size}…"
                try {
                    val e = loadEpg(u)
                    g = mergeEpg(g, e)
                    into.add("OK - Guide $n : ${e.chans.size} channels, ${e.progs.size} with programmes")
                } catch (ex: Exception) {
                    into.add("ERROR - Guide $n : ${ex.message}")
                }
            }
            return g
        }

        var guideUrls: List<String> = emptyList()
        if (!(haveCache && fresh)) {
            // Cache missing or old: refresh (silently when cached data is already shown).
            if (!haveCache) status = "Loading channels…"
            val chs = ArrayList<Channel>()
            val urls = ArrayList<String>(cfg.epgs)
            val rep = ArrayList<String>()
            for (s in sources) {
                try {
                    val r = loadSource(ctx, s)
                    chs.addAll(r.first)
                    urls.add(s.epg.ifBlank { r.second })
                    rep.add("OK - ${s.name} : ${r.first.size} channels")
                } catch (ex: Exception) {
                    rep.add("ERROR - ${s.name} : ${ex.message}")
                }
            }
            val seen = HashSet<Int>()
            val newCh = chs.map { c ->
                var id = c.id
                while (!seen.add(id)) id++
                if (id == c.id) c else c.copy(id = id)
            }
            guideUrls = urls.filter { it.isNotBlank() }.distinct()
            val okAll = rep.none { it.startsWith("ERROR") }
            if (newCh.isNotEmpty() || !haveCache) channels = newCh
            val g = fetchGuides(guideUrls, rep, !haveCache)
            if (g.progs.isNotEmpty() || g.chans.isNotEmpty() || !haveCache) epg = g
            lastSync = System.currentTimeMillis()
            report = rep.toList()
            status = if (rep.any { it.startsWith("ERROR") }) "A source failed: see Settings > Sources" else ""
            if (okAll && newCh.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    GuideCache.saveChannels(ctx, cacheKey, newCh)
                    if (g.progs.isNotEmpty() || g.chans.isNotEmpty()) GuideCache.saveEpg(ctx, cacheKey, g)
                }
            }
            delay(maxAge)
        } else {
            // Rebuild the guide URL list (needed for later refreshes) without downloading channels again.
            guideUrls = (cfg.epgs + sources.map { it.epg }).filter { it.isNotBlank() }.distinct()
            delay(if (cfg.syncHours > 0) (maxAge - (System.currentTimeMillis() - lastSync)).coerceAtLeast(60_000L) else maxAge)
        }
        while (true) {
            if (guideUrls.isEmpty()) {
                val u = ArrayList<String>(cfg.epgs)
                for (s in sources) {
                    if (s.epg.isNotBlank()) u.add(s.epg)
                    else try { u.add(loadSource(ctx, s).second) } catch (ex: Exception) { }
                }
                guideUrls = u.filter { it.isNotBlank() }.distinct()
            }
            val g2 = fetchGuides(guideUrls, ArrayList(), false)
            if (g2.progs.isNotEmpty() || g2.chans.isNotEmpty()) {
                epg = g2
                lastSync = System.currentTimeMillis()
                withContext(Dispatchers.IO) { GuideCache.saveEpg(ctx, cacheKey, g2) }
            }
            delay(maxAge)
        }
    }

    MaterialTheme(colorScheme = if (Th.light) androidx.compose.material3.lightColorScheme() else darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Lecteur vidéo toujours présent en fond : aperçu à l'accueil, plein écran en lecture.
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exo
                        useController = false
                        // Le message d'erreur est affiché par notre propre boîte (en bas à gauche).
                        setErrorMessageProvider { _ -> android.util.Pair(0, "") }
                        findViewById<android.widget.TextView>(androidx.media3.ui.R.id.exo_error_message)?.apply {
                            setBackgroundColor(0)
                            setPadding(0, 0, 0, 0)
                            textSize = 0f
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            when {
                sources.isEmpty() || screen == "settings" -> CompositionLocalProvider(LocalThemed provides true) { SettingsHub(
                    if (sources.isEmpty()) "src_type" else settingsPage,
                    sources, cfg, all, epg.chans, report, lastSync, recs,
                    { a, i -> recAct(a, i) },
                    { l -> saveSources(ctx, l); sources = l },
                    { cfg = it; saveCfg(ctx, it) },
                    { reload++ },
                    sources.isNotEmpty(),
                    { screen = "" }
                ) }
                screen == "recs" -> Solid { CompositionLocalProvider(LocalThemed provides true) { RecordingsScreen(recs, { a, i -> recAct(a, i) }) { screen = "" } } }
                screen == "multi" -> MultiviewScreen(exo, shown, epg, cur) { screen = "" }
                screen == "search" -> Solid {
                    SearchScreen(shown, epg, { play(it); full = true; quietStart = false; miniStart = true; fromGuide = false; screen = "" }) { screen = "" }
                }
                screen == "guide" -> EpgGrid(
                    shown, epg, cur, cfg.showNums, recs, cfg.remOn,
                    { play(it); full = true; quietStart = false; miniStart = false; fromGuide = true; screen = "" },
                    { ch, p, act ->
                        when (act) {
                            "rec" -> Dvr.schedule(ctx, ch, p, "rec")
                            "rem" -> Dvr.schedule(ctx, ch, p, "rem")
                            "unrec" -> Dvr.remove(ctx, "${ch.id}_${p.start}_rec")
                            "unrem" -> Dvr.remove(ctx, "${ch.id}_${p.start}_rem")
                        }
                    },
                    { editCh = it.id },
                    { editCat = it }
                ) {
                    if (fromGuide) { fromGuide = false; full = false } else miniStart = true
                    screen = ""
                }
                cur != null && full -> PlayerScreen(
                    recs, { ch, p, act ->
                        when (act) {
                            "rec" -> Dvr.schedule(ctx, ch, p, "rec")
                            "rem" -> Dvr.schedule(ctx, ch, p, "rem")
                            "unrec" -> Dvr.remove(ctx, "${ch.id}_${p.start}_rec")
                            "unrem" -> Dvr.remove(ctx, "${ch.id}_${p.start}_rem")
                        }
                    },
                    sources,
                    cur, exo, shown, epg, all, cfg, exitAsk || reminder != null, weather, miniStart, quietStart, hist, { cfg = it; saveCfg(ctx, it) }, { play(it) }, { screen = "guide" },
                    { hist = it; saveHist(ctx, it) }
                ) {
                    if (fromGuide) screen = "guide" else onRootBack { full = false }
                }
                else -> CompositionLocalProvider(LocalThemed provides true) { Home(
                    shown, sources, epg, status, hist, cur,
                    { play(it); full = true; quietStart = false; miniStart = true; fromGuide = false },
                    { screen = "search" }, { settingsPage = ""; screen = "settings" }, { settingsPage = "sources"; screen = "settings" },
                    { editCh = it.id },
                    { screen = "guide" },
                    { screen = "recs" }, { screen = "multi" },
                    cfg.merge,
                    { play(it) }
                ) }
            }
            val ec = editCh
            if (ec != null) {
                ChannelEditor(ec, all, cfg, epg.chans, { cfg = it; saveCfg(ctx, it) }) { editCh = null }
            }
            val ecat = editCat
            if (ecat != null) {
                CategoryEditor(ecat, all, cfg, { cfg = it; saveCfg(ctx, it) }) { editCat = null }
            }
            val cr = crash
            if (cr != null) {
                val fr2 = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(150)
                    try { fr2.requestFocus() } catch (ex: Exception) { }
                }
                Column(
                    Modifier.align(Alignment.Center).width(760.dp).clip(RoundedCornerShape(12.dp))
                        .background(Th.popup).padding(18.dp)
                ) {
                    Text("The app closed unexpectedly last time", color = Color(0xFFFFB4A9), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Please take a photo of this and send it to Claude:", color = MUTED, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Text(cr.take(1400), color = Th.text, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 28, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    FocusRow({ crash = null }, Modifier.width(160.dp).focusRequester(fr2)) {
                        Text("Close", color = fcolor(Color.White), fontSize = 16.sp)
                    }
                }
            }
            if (playErr.isNotEmpty() && errShow && screen != "settings" && !exitAsk) {
                val parts = playErr.split("\n")
                Column(
                    Modifier.align(Alignment.TopEnd).padding(24.dp).width(440.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xF0151A24)).padding(16.dp)
                ) {
                    Text("Stream problem", color = Color(0xFFFFB4A9), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(parts.first(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    if (parts.size > 1) {
                        Text(parts.drop(1).joinToString(" "), color = Color(0xFFB0B8C8), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (exitAsk) {
                ExitBox(
                    Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    { exo.pause(); activityOf(ctx)?.finishAndRemoveTask() },
                    { exitAsk = false }
                )
            }
            val rm = reminder
            if (rm != null && !exitAsk) {
                ReminderBox(
                    rm, Modifier.align(Alignment.TopEnd).padding(24.dp),
                    {
                        val ch = all.firstOrNull { it.id == rm.chId }
                        reminder = null
                        if (ch != null) {
                            play(ch)
                            full = true; quietStart = false
                            miniStart = false
                            screen = ""
                        }
                    },
                    { reminder = null }
                )
            }
        }
    }
}

class HomeRow(val header: Boolean, val title: String, val chans: List<Channel>)

// Une section par source (si plusieurs), puis une rangée par catégorie.
fun buildRows(ch: List<Channel>, sources: List<Source>, merge: Boolean = false): List<HomeRow> {
    val out = ArrayList<HomeRow>()
    if (merge) {
        for ((cat, l) in ch.groupBy { it.cat }) out.add(HomeRow(false, cat, l))
        return out
    }
    val multi = sources.size > 1
    val bySrc = ch.groupBy { it.src }
    val order = sources.map { it.id } + bySrc.keys.filter { k -> sources.none { it.id == k } }
    for (sid in order) {
        val list = bySrc[sid] ?: continue
        for ((cat, l) in list.groupBy { it.cat }) out.add(HomeRow(false, cat, l))
    }
    return out
}

// Onglet de l'en-tête : le carré blanc (sans coins arrondis) grandit quand il est sélectionné.
@Composable
fun TopTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.12f else 1f, label = "tab")
    Box(
        Modifier.padding(horizontal = 4.dp).zIndex(if (f) 1f else 0f)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .background(if (f) Th.focusBg else Color.Transparent)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (f) Th.focusText else if (selected) Th.text else Th.sub, fontSize = 15.sp,
            fontWeight = if (selected || f) FontWeight.Bold else FontWeight.Normal, maxLines = 1
        )
    }
}

// Boutons ronds de l'en-tête : 0 = recherche, 1 = réglages.
@Composable
fun HeaderIcon(kind: Int, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.15f else 1f, label = "hicon")
    val col = if (f) Th.focusText else Th.text
    Box(
        Modifier.size(38.dp).zIndex(if (f) 1f else 0f)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (f) Th.focusBg else Th.iconBg)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val st = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (kind == 0) {
                drawCircle(col, w * 0.34f, androidx.compose.ui.geometry.Offset(w * 0.42f, w * 0.42f), style = st)
                drawLine(col, androidx.compose.ui.geometry.Offset(w * 0.68f, w * 0.68f), androidx.compose.ui.geometry.Offset(w * 0.94f, w * 0.94f), 1.8.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            } else {
                val c0 = androidx.compose.ui.geometry.Offset(w / 2f, w / 2f)
                drawCircle(col, w * 0.2f, c0, style = st)
                for (k in 0 until 8) {
                    val ang = Math.PI * k / 4.0
                    val ca = Math.cos(ang).toFloat()
                    val sa = Math.sin(ang).toFloat()
                    drawLine(
                        col,
                        androidx.compose.ui.geometry.Offset(c0.x + ca * w * 0.32f, c0.y + sa * w * 0.32f),
                        androidx.compose.ui.geometry.Offset(c0.x + ca * w * 0.46f, c0.y + sa * w * 0.46f),
                        2.6.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

// Heure et date en haut à droite (son propre état, pour ne pas redessiner tout l'accueil chaque seconde).
@Composable
fun HeaderClock() {
    var t by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { t = System.currentTimeMillis(); delay(1000) } }
    Column(horizontalAlignment = Alignment.End) {
        Text(hm(t), color = Th.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(Date(t)), color = Th.sub, fontSize = 12.sp)
    }
}

@Composable
fun Home(
    ch: List<Channel>, sources: List<Source>, epg: Epg, status: String, hist: List<Int>, cur: Channel?,
    onPlay: (Channel) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit, onSources: () -> Unit,
    onEdit: (Channel) -> Unit, onGuide: () -> Unit, onRecs: () -> Unit, onMulti: () -> Unit, merge: Boolean = false,
    onSwitch: (Channel) -> Unit = {}
) {
    val cp = cur?.let { epg.now(it.epgId) }
    // Aperçu dans les cartes : lecteur muet. Après 3 s sur une carte l'aperçu démarre ;
    // après 10 s d'aperçu, la chaîne passe en arrière-plan (avec le son).
    val pctx = LocalContext.current
    var focusId by remember { mutableStateOf<Int?>(null) }
    var pvId by remember { mutableStateOf<Int?>(null) }
    var pvReady by remember { mutableStateOf(false) }
    val pvPlayer = remember {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val p = ExoPlayer.Builder(pctx)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultDataSource.Factory(pctx, http)
                )
            ).build()
        p.volume = 0f
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { pvReady = true }
        })
        p
    }
    DisposableEffect(Unit) { onDispose { try { pvPlayer.release() } catch (e: Exception) { } } }
    LaunchedEffect(focusId) {
        pvId = null
        pvReady = false
        try { pvPlayer.stop(); pvPlayer.clearMediaItems() } catch (e: Exception) { }
        val id = focusId
        val c = if (id != null) ch.firstOrNull { it.id == id } else null
        val on = pctx.getSharedPreferences("iptv_player", 0).getBoolean("cardPreview", true)
        if (c != null && on && c.id != cur?.id) {
            delay(3000)
            try {
                pvPlayer.setMediaItem(MediaItem.fromUri(c.url))
                pvPlayer.prepare()
                pvPlayer.playWhenReady = true
                pvId = c.id
            } catch (e: Exception) { return@LaunchedEffect }
            delay(10000)
            onSwitch(c)
            pvId = null
            try { pvPlayer.stop(); pvPlayer.clearMediaItems() } catch (e: Exception) { }
        }
    }
    val recent = remember(hist, ch) { hist.mapNotNull { id -> ch.firstOrNull { it.id == id } } }
    val rows = remember(ch, sources, merge) { buildRows(ch, sources, merge) }
    val listState = rememberLazyListState()
    val homeScope = rememberCoroutineScope()
    val baseIdx = (if (status.isNotBlank()) 1 else 0) + (if (recent.isNotEmpty()) 1 else 0)
    var lastRow by remember { mutableStateOf(-1) }
    fun rowFocus(idx: Int) {
        val aligned = listState.firstVisibleItemIndex == idx && listState.firstVisibleItemScrollOffset == 0
        if (idx != lastRow || !aligned) {
            lastRow = idx
            homeScope.launch {
                try {
                    delay(70)
                    listState.animateScrollToItem(idx.coerceAtLeast(0))
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Th.gradTop, 0.4f to Th.gradMid,
                    0.62f to Th.bgMid, 1f to Th.bgBottom
                )
            )
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Th.header).padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "IPTV", color = Th.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 28.dp)
                )
                TopTab("Home", true) { }
                TopTab("Guide", false) { onGuide() }
                TopTab("Recordings", false) { onRecs() }
                TopTab("Multiview", false) { onMulti() }
                Spacer(Modifier.weight(1f))
                HeaderIcon(0) { onSearch() }
                Spacer(Modifier.width(10.dp))
                HeaderIcon(1) { onSettings() }
                Spacer(Modifier.width(20.dp))
                HeaderClock()
            }
            Box(Modifier.weight(0.42f).fillMaxWidth()) {
                Column(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 8.dp)) {
                    if (cur != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (cur.icon.isNotBlank()) {
                                AsyncImage(
                                    model = cur.icon, contentDescription = null,
                                    modifier = Modifier.padding(end = 10.dp).size(56.dp, 30.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(cur.name, color = Th.sub, fontSize = 16.sp)
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFE5484D)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            cp?.title ?: "", color = Th.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (cp != null) {
                            Row(Modifier.width(380.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(hm(cp.start), color = Th.sub, fontSize = 14.sp)
                                Bar(fracOf(cp, System.currentTimeMillis()), Modifier.weight(1f).padding(horizontal = 10.dp), 5.dp)
                                Text("+${((cp.stop - System.currentTimeMillis()) / 60000).coerceAtLeast(0)} min", color = Th.sub, fontSize = 14.sp, maxLines = 1, softWrap = false)
                            }
                            val dsc = cp.desc.ifBlank { cp.sub }
                            if (dsc.isNotBlank()) {
                                Text(
                                    dsc, color = Th.desc, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(380.dp).padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(0.58f).fillMaxWidth()) {
            val rowH = maxHeight / 1.6f
            LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 4.dp, bottom = maxHeight)
            ) {
                if (status.isNotBlank()) {
                    item { Text(status, color = Color(0xFFFFB4A9), fontSize = 13.sp) }
                }
                if (recent.isNotEmpty()) {
                    item {
                        Column(Modifier.heightIn(min = rowH)) {
                            Text(
                                "CONTINUE WATCHING", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 6.dp)
                            )
                            CardRow(recent, epg, onPlay, onEdit, { rowFocus(baseIdx - 1) }, pvPlayer, pvId, pvReady) { c, fo -> if (fo) focusId = c.id else if (focusId == c.id) focusId = null }
                        }
                    }
                }
                itemsIndexed(rows) { ri, r ->
                    if (r.header) {
                        Text(
                            r.title.uppercase(), color = Th.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 22.dp, bottom = 2.dp)
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(Th.accent))
                    } else {
                        Column(Modifier.heightIn(min = rowH)) {
                            Text(
                                r.title.uppercase(), color = Th.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp)
                            )
                            CardRow(r.chans, epg, onPlay, onEdit, { rowFocus(baseIdx + ri - (if (ri > 0 && rows[ri - 1].header) 1 else 0)) }, pvPlayer, pvId, pvReady) { c, fo -> if (fo) focusId = c.id else if (focusId == c.id) focusId = null }
                        }
                    }
                }
            }
            }
        }
    }
}

// Carte de chaîne : image du programme, logo en bas à gauche, barre de progression, titre et suite.
// Le cadre de sélection est dessiné autour de l'image (il ne cache pas la barre de progression).
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CardRow(
    list: List<Channel>, epg: Epg, onPlay: (Channel) -> Unit, onEdit: (Channel) -> Unit, onRowFocus: () -> Unit,
    pv: ExoPlayer? = null, pvId: Int? = null, pvReady: Boolean = false, onCardFocus: (Channel, Boolean) -> Unit = { _, _ -> }
) {
    val st = rememberLazyListState()
    val sc = rememberCoroutineScope()
    val firstReq = remember { FocusRequester() }
    var firstReady by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    LazyRow(
        state = st,
        modifier = Modifier
            .focusProperties { enter = { if (firstReady) firstReq else FocusRequester.Default } }
            .focusGroup()
            .onFocusChanged {
                // Leaving the row: put it back at its first card, so coming back always starts there.
                if (hadFocus && !it.hasFocus) sc.launch { try { st.scrollToItem(0) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
                hadFocus = it.hasFocus
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 70.dp, top = 14.dp, bottom = 30.dp)
    ) {
        itemsIndexed(list, key = { i, c -> "${c.id}_$i" }) { i, c ->
            if (i == 0) {
                DisposableEffect(Unit) {
                    firstReady = true
                    onDispose { firstReady = false }
                }
            }
            ChannelCard(c, epg, { onPlay(c) }, { onEdit(c) }, if (i == 0) firstReq else null, pv = if (pvId == c.id) pv else null, pvReady = pvReady, onFocusState = { onCardFocus(c, it) }) {
                onRowFocus()
                sc.launch { try { st.animateScrollToItem((i - 1).coerceAtLeast(0)) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e } }
            }
        }
    }
}

@Composable
fun ChannelCard(
    c: Channel, epg: Epg, onClick: () -> Unit, onLong: () -> Unit, req: FocusRequester? = null,
    pv: ExoPlayer? = null, pvReady: Boolean = false, onFocusState: (Boolean) -> Unit = {}, onFocus: () -> Unit = {}
) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(true) }
    val scale by animateFloatAsState(if (f) 1.06f else 1f, label = "cardScale")
    val p = epg.now(c.epgId)
    val outer = RoundedCornerShape(12.dp)
    val inner = RoundedCornerShape(9.dp)
    Column(
        Modifier.width(200.dp)
            .zIndex(if (f) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (req != null) Modifier.focusRequester(req) else Modifier)
            .onFocusChanged { f = it.isFocused; onFocusState(it.isFocused); if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(3.dp, if (f) Th.text else Color.Transparent, outer).padding(3.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(inner).background(Th.cardBg)) {
                if (p != null && p.icon.isNotBlank()) {
                    AsyncImage(
                        model = p.icon, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        c.name, color = Th.sub, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp)
                    )
                }
                if (pv != null && f) {
                    AndroidView(
                        factory = { cx -> android.view.TextureView(cx).also { tv -> pv.setVideoTextureView(tv) } },
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (pvReady) 1f else 0f }
                    )
                }
                if (c.icon.isNotBlank()) {
                    AsyncImage(
                        model = c.icon, contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 12.dp)
                            .size(48.dp, 26.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (p != null) {
                    Bar(
                        fracOf(p, System.currentTimeMillis()),
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        6.dp
                    )
                }
            }
        }
        Text(
            p?.title ?: c.name, color = Th.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 3.dp)
        )
        Text(
            if (p != null) "${hm(p.start)} - ${hm(p.stop)}" else "",
            color = Th.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 3.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    recs: List<Rec>, onDvr: (Channel, Prog, String) -> Unit,
    sources: List<Source>, c: Channel, exo: ExoPlayer, channels: List<Channel>, epg: Epg, all: List<Channel>, cfg: Cfg, exitAsk: Boolean, weather: String, startMini: Boolean, quiet: Boolean, hist: List<Int>,
    onCfg: (Cfg) -> Unit, onZap: (Channel) -> Unit, onGuide: () -> Unit, onHist: (List<Int>) -> Unit, onBack: () -> Unit
) {
    val num = channels.indexOf(c) + 1
    val p = epg.now(c.epgId)
    val ups = epg.upcoming(c.epgId, 4)
    var evIdx by remember { mutableStateOf(-1) }
    var dir by remember { mutableStateOf(1) }
    var show by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var fromPanel by remember { mutableStateOf(false) }
    var mini by remember { mutableStateOf(startMini && !quiet) }
    var miniTick by remember { mutableStateOf(0) }
    val firstC = remember { booleanArrayOf(true) }
    val okFired = remember { booleanArrayOf(true) }
    var tracksOpen by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var editId by remember { mutableStateOf<Int?>(null) }
    var editCatP by remember { mutableStateOf<String?>(null) }
    var focusId by remember { mutableStateOf(c.id) }
    var refocus by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var res by remember { mutableStateOf("") }
    var codec by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }

    LaunchedEffect(c.id) {
        evIdx = -1
        if (firstC[0]) {
            firstC[0] = false
            show = !startMini && !quiet
        } else {
            mini = false
            show = true
            tick++
        }
    }
    // Opening the mini player or the channel list hides the infobar for good (it must not come back when they close).
    LaunchedEffect(mini, panel) { if (mini || panel) { show = false; evIdx = -1 } }
    LaunchedEffect(mini, miniTick) {
        if (mini) {
            delay(12000)
            mini = false
        }
    }
    LaunchedEffect(panel, tracksOpen, editId, editCatP, exitAsk, mini) {
        delay(100)
        if (!panel && !tracksOpen && editId == null && editCatP == null && !exitAsk && !mini) { try { fr.requestFocus() } catch (ex: Exception) { } }
    }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(tick) { delay(if (evIdx >= 0) 12000L else 7000L); show = false; evIdx = -1 }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width <= 0) return
                val f = exo.videoFormat
                val mime = f?.sampleMimeType ?: ""
                codec = when {
                    mime.contains("hevc") -> "HEVC"
                    mime.contains("avc") -> "H264"
                    mime.contains("mpeg2") -> "MPEG2"
                    else -> ""
                }
                val fps = if (f != null && f.frameRate > 0f) "  ${f.frameRate.toInt()} fps" else ""
                res = "${videoSize.width} x ${videoSize.height}$fps"
                quality = if (videoSize.height >= 1080) "FHD" else if (videoSize.height >= 720) "HD" else "SD"
            }
        }
        exo.addListener(l)
        l.onTracksChanged(exo.currentTracks)
        l.onVideoSizeChanged(exo.videoSize)
        onDispose { exo.removeListener(l) }
    }
    BackHandler {
        if (panel) panel = false
        else if (mini) mini = false
        else if (evIdx >= 0) evIdx = -1
        else if (fromPanel) { fromPanel = false; focusId = c.id; panel = true }
        else onBack()
    }

    Box(
        Modifier.fillMaxSize().focusRequester(fr).focusable().onPreviewKeyEvent {
            val isOk = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
            if (panel || tracksOpen || editId != null || editCatP != null || mini) {
                false
            } else if (isOk) {
                okLong(it, okFired, { mini = true; miniTick++ }, { panel = true; focusId = c.id })
            } else if (it.type != KeyEventType.KeyDown) {
                false
            } else if (it.key == Key.Back || it.key == Key.Escape) {
                false
            } else if (it.key == Key.DirectionRight) {
                if (evIdx < ups.size - 1) {
                    dir = 1
                    evIdx++
                }
                show = true
                tick++
                true
            } else if (it.key == Key.DirectionLeft && evIdx >= 0) {
                dir = -1
                evIdx--
                show = true
                tick++
                true
            } else if (it.key == Key.DirectionLeft) {
                true
            } else if (it.key == Key.Menu) {
                tracksOpen = true
                true
            } else {
                show = true
                tick++
                val i = channels.indexOf(c)
                val n = channels.size
                if (n > 0 && (it.key == Key.DirectionUp || it.key == Key.ChannelUp)) {
                    onZap(channels[(i + 1) % n])
                    true
                } else if (n > 0 && (it.key == Key.DirectionDown || it.key == Key.ChannelDown)) {
                    onZap(channels[(i - 1 + n) % n])
                    true
                } else {
                    false
                }
            }
        }
    ) {
        SlideIn(show && !panel && !mini, false, Modifier.align(Alignment.BottomCenter)) {
            BottomInfo(
                c, num, p, ups, evIdx, dir, nowMs, res, weather, codec, quality,
                tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO },
                tracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
            )
        }
        if (mini && !panel) {
            MiniPlayer(
                channels, c, epg, nowMs, hist,
                { exo.playWhenReady }, { exo.playWhenReady = !exo.playWhenReady },
                { onZap(it); mini = false }, { onGuide() }, { miniTick++ },
                { id -> onHist(if (id == null) emptyList() else hist.filter { it != id }) },
                onTracks = { tracksOpen = true; miniTick++ },
                onClose = { mini = false }
            )
        }
        if (panel) {
            CompositionLocalProvider(LocalThemed provides true) { ChannelPanel(
                if (cfg.merge) channels.map { it.copy(src = "") } else channels, sources,
                if (cfg.merge) c.copy(src = "") else c, epg, nowMs, focusId, refocus,
                cfg.showNums, { onZap(it); panel = false; fromPanel = true }, { editId = it.id }, { editCatP = it },
                recs = recs, remOn = cfg.remOn, onDvr = onDvr,
                onClose = { panel = false }
            ) }
        }
        if (tracksOpen) TracksDialog(tracks, exo) { tracksOpen = false }
        val ecp = editCatP
        if (ecp != null) CategoryEditor(ecp, all, cfg, onCfg) { editCatP = null; refocus++ }
        val ed = editId
        if (ed != null) {
            ChannelEditor(ed, all, cfg, epg.chans, onCfg) {
                editId = null
                focusId = ed
                refocus++
            }
        }
    }
}

private val LIME = Color(0xFFB5D334)
private val SOFT = Color(0xFFEFF5B0)

fun wmoText(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    in 95..99 -> "Thunderstorm"
    else -> ""
}

// Ville -> coordonnées (Open-Meteo) -> température actuelle. Retourne "" en cas d'échec.
suspend fun fetchWeather(city: String): String = withContext(Dispatchers.IO) {
    try {
        val q = java.net.URLEncoder.encode(city, "UTF-8")
        val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en"))
        val r = g.optJSONArray("results") ?: return@withContext ""
        val o = r.getJSONObject(0)
        val lat = o.getDouble("latitude")
        val lon = o.getDouble("longitude")
        val label = o.optString("name") + (if (o.has("country")) ", " + o.optString("country") else "")
        val w = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"))
        val cur = w.getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m")).toInt()
        val txt = wmoText(cur.getInt("weather_code"))
        label + "  ·  " + t + "°C" + (if (txt.isNotEmpty()) "  ·  " + txt else "")
    } catch (ex: Exception) {
        ""
    }
}

// Apparition / disparition animée : glissement + fondu.
@Composable
fun SlideIn(visible: Boolean, fromTop: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(visible) { p.animateTo(if (visible) 1f else 0f, tween(320)) }
    if (visible || p.value > 0.01f) {
        Box(
            modifier.graphicsLayer {
                translationY = (1f - p.value) * size.height * 0.5f * (if (fromTop) -1f else 1f)
                alpha = p.value
            }
        ) { content() }
    }
}

@Composable
fun Badge(t: String) {
    Text(
        t, color = Th.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.padding(start = 8.dp).border(1.dp, Th.sub, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// Barre du haut : numéro, nom de la chaîne, météo, badges.
@Composable
fun TopInfo(c: Channel, num: Int, weather: String, codec: String, quality: String, audioN: Int, subN: Int) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Th.ovTop, Color.Transparent)))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$num", color = Th.lime, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(20.dp))
        Text(c.name, color = Th.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            weather, color = Color(0xFFE0E4EC), fontSize = 16.sp, maxLines = 1,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1.3f)
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (subN > 0) Badge("SUB")
            if (audioN > 1) Badge("AUDIO $audioN")
            if (codec.isNotBlank()) Badge(codec)
            if (quality.isNotBlank()) Badge(quality)
        }
    }
}

// Petit chevron dessiné (haut / bas) pour le numéro de chaîne.
private fun Modifier.chevron(up: Boolean, col: Color): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    val st = 2.5.dp.toPx()
    val y1 = if (up) h else 0f
    val y2 = if (up) 0f else h
    drawLine(col, androidx.compose.ui.geometry.Offset(0f, y1), androidx.compose.ui.geometry.Offset(w / 2f, y2), st, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(col, androidx.compose.ui.geometry.Offset(w / 2f, y2), androidx.compose.ui.geometry.Offset(w, y1), st, androidx.compose.ui.graphics.StrokeCap.Round)
}

// Bandeau du bas (style "zapping" TV) : vignette à gauche, numéro + flèches, nom de chaîne,
// titre, heure + progression + badges. Droite = événements suivants, gauche = audio / sous-titres.
@Composable
fun BottomInfo(
    c: Channel, num: Int, p: Prog?, ups: List<Prog>, evIdx: Int, dir: Int, nowMs: Long, res: String,
    weather: String, codec: String, quality: String, audioN: Int, subN: Int
) {
    val slide = remember { Animatable(0f) }
    val first = remember { BooleanArray(1) { true } }
    LaunchedEffect(evIdx, c.id) {
        if (first[0]) {
            first[0] = false
        } else {
            slide.snapTo(1f)
            slide.animateTo(0f, tween(380))
        }
    }
    val ev: Prog? = if (evIdx in ups.indices) ups[evIdx] else null
    val sp: Prog? = ev ?: p
    Column(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color.Transparent, Th.ovMid, Th.ovEnd))
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 28.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vignette collée au bord gauche + numéro + texte (glissent quand on change d'événement)
            Row(
                Modifier.weight(1f).graphicsLayer {
                    translationX = slide.value * dir * 90.dp.toPx()
                    alpha = 1f - slide.value * 0.9f
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.padding(start = 24.dp).width(150.dp).height(84.dp).clip(RoundedCornerShape(4.dp)).background(Th.cardBg)) {
                    if (sp != null && sp.icon.isNotBlank()) {
                        AsyncImage(
                            model = sp.icon, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else if (c.icon.isNotBlank()) {
                        AsyncImage(
                            model = c.icon, contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit
                        )
                    }
                }
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(22.dp, 11.dp).chevron(true, Th.sub))
                    Text("$num", color = Th.lime, fontSize = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Box(Modifier.size(22.dp, 11.dp).chevron(false, Th.sub))
                }
                Column(Modifier.weight(1f).height(88.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.icon.isNotBlank()) {
                            AsyncImage(
                                model = c.icon, contentDescription = null,
                                modifier = Modifier.height(20.dp).widthIn(max = 48.dp).padding(end = 8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Text(c.name, color = Th.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (sp == null) {
                        Text("No guide information", color = Th.sub, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text(
                            sp.title, color = Th.text, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            if (ev == null) {
                                Text("Now ${hm(nowMs)}", color = Th.time, fontSize = 14.sp)
                                Text("  |  ", color = Th.sub, fontSize = 15.sp)
                                Text("${hm(sp.start)} - ${hm(sp.stop)}", color = Th.time, fontSize = 15.sp)
                                Bar(fracOf(sp, nowMs), Modifier.padding(horizontal = 14.dp).width(150.dp), 8.dp)
                                Text("+${((sp.stop - nowMs) / 60000).coerceAtLeast(0)} min", color = Th.time, fontSize = 14.sp, maxLines = 1, softWrap = false)
                            } else {
                                Text("${hm(sp.start)} - ${hm(sp.stop)}", color = Th.time, fontSize = 15.sp)
                                Text("   in ${((sp.start - nowMs) / 60000).coerceAtLeast(0)} min", color = Th.sub, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            if (subN > 0) Badge("SUB")
                            if (audioN > 1) Badge("AUDIO $audioN")
                            if (codec.isNotBlank()) Badge(codec)
                            if (quality.isNotBlank()) Badge(quality)
                        }
                    }
                }
            }
            if (ups.isNotEmpty()) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("\u203A", color = Th.sub, fontSize = 30.sp)
                    Text("  Up next", color = Th.sub, fontSize = 16.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.cat, color = Th.sub, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                "\u2191\u2193  zap     OK  list",
                color = Th.sub, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
    }
}


// Message d'erreur lisible pour l'écran du lecteur.
@OptIn(UnstableApi::class)
fun friendlyError(e: androidx.media3.common.PlaybackException): String {
    val http = e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
    if (http != null) {
        return when (val c = http.responseCode) {
            401 -> "Login refused (401)\nWrong username or password, or account expired"
            403 -> "Access denied (403)\nAccount expired, too many connections, or channel blocked"
            404 -> "Channel not found (404)\nThe stream no longer exists on the server"
            407, 429 -> "Server busy ($c)\nToo many requests: try again in a moment"
            in 500..599 -> "Server error ($c)\nThe provider's server has a problem"
            else -> "Server answered with error $c"
        }
    }
    return when (e.errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "No response from the server\nThe stream is offline or too slow"
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "Cannot connect\nCheck your internet connection or the provider"
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "Invalid stream\nThe channel sends data the player cannot read"
        androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
            "Audio problem\nThe TV could not start the sound of this channel (retrying)"
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "Video format not supported by this TV"
        else -> "Playback error\n" + e.errorCodeName
    }
}
