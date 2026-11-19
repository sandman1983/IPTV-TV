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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
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

fun norm(h: String): String {
    val t = h.trim().trimEnd('/')
    return if (t.startsWith("http")) t else "http://$t"
}

fun get(url: String): String = URL(url).openStream().bufferedReader().use { it.readText() }

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun parseDate(f: SimpleDateFormat, s: String?): Long =
    try { f.parse(s ?: "")?.time ?: 0L } catch (ex: Exception) { 0L }

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
// les 8 prochains programmes (le programme en cours inclus).
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
                if (sp > now) {
                    val l = progs.getOrPut(ch) { ArrayList() }
                    l.add(Prog(title, sub.ifBlank { desc }, cat, icon, st, sp, desc.take(700)))
                    if (l.size > 8) {
                        l.sortBy { it.start }
                        l.removeAt(l.size - 1)
                    }
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
        setContent { App() }
    }
}

@Composable
fun Solid(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1117))) { content() }
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
    var editSrc by remember { mutableStateOf<Source?>(null) }
    var editCh by remember { mutableStateOf<Int?>(null) }

    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            )
            .build()
    }
    DisposableEffect(exo) { onDispose { exo.release() } }

    val all = remember(channels, cfg, epg.chans) { applyCfg(channels, cfg, epg.icons) }
    val shown = remember(all) { all.filter { !it.hidden } }
    val cur = bg?.let { b -> all.firstOrNull { it.id == b.id } ?: b }

    fun play(c: Channel) {
        bg = c
        hist = pushHist(hist, c.id)
        saveHist(ctx, hist)
    }

    // Au démarrage : la dernière chaîne regardée joue en fond de l'accueil.
    LaunchedEffect(all) {
        if (bg == null) {
            val id = hist.firstOrNull()
            if (id != null) bg = all.firstOrNull { it.id == id }
        }
    }
    LaunchedEffect(bg?.id, screen) {
        val c = bg
        if (c != null && screen == "") {
            resetTracks(exo)
            exo.setMediaItem(MediaItem.fromUri(c.url))
            exo.prepare()
            exo.playWhenReady = true
        } else {
            exo.pause()
        }
    }
    LaunchedEffect(full, cfg.bgSound) { exo.volume = if (!full && !cfg.bgSound) 0f else 1f }

    // Rafraîchit l'affichage "en cours / à suivre" toutes les 30 s.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            epg = epg.touch()
        }
    }

    // Chargement des sources et des guides, puis rechargement des guides toutes les 30 minutes.
    LaunchedEffect(sources, cfg.epgs, reload) {
        status = "Chargement des chaînes…"
        val chs = ArrayList<Channel>()
        val urls = ArrayList<String>(cfg.epgs)
        val rep = ArrayList<String>()
        for (s in sources) {
            try {
                val r = loadSource(ctx, s)
                chs.addAll(r.first)
                urls.add(s.epg.ifBlank { r.second })
                rep.add("OK - ${s.name} : ${r.first.size} chaînes")
            } catch (ex: Exception) {
                rep.add("ERREUR - ${s.name} : ${ex.message}")
            }
        }
        val seen = HashSet<Int>()
        channels = chs.map { c ->
            var id = c.id
            while (!seen.add(id)) id++
            if (id == c.id) c else c.copy(id = id)
        }
        val guideUrls = urls.filter { it.isNotBlank() }.distinct()

        suspend fun fetchGuides(into: MutableList<String>, show: Boolean): Epg {
            var g = Epg(emptyMap())
            var n = 0
            for (u in guideUrls) {
                n++
                if (show) status = "Chargement du guide $n/${guideUrls.size}…"
                try {
                    val e = loadEpg(u)
                    g = mergeEpg(g, e)
                    into.add("OK - Guide $n : ${e.chans.size} chaînes, ${e.progs.size} avec programmes")
                } catch (ex: Exception) {
                    into.add("ERREUR - Guide $n : ${ex.message}")
                }
            }
            return g
        }

        epg = Epg(emptyMap())
        epg = fetchGuides(rep, true)
        report = rep.toList()
        status = if (rep.any { it.startsWith("ERREUR") }) "Une source a échoué : voir Sources et guides" else ""
        while (true) {
            delay(30 * 60 * 1000L)
            val g2 = fetchGuides(ArrayList(), false)
            if (g2.progs.isNotEmpty() || g2.chans.isNotEmpty()) epg = g2
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Lecteur vidéo toujours présent en fond : aperçu à l'accueil, plein écran en lecture.
            AndroidView(
                factory = { PlayerView(it).apply { player = exo; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
            when {
                sources.isEmpty() || screen == "form" -> Solid {
                    SourceForm(
                        editSrc, sources.isNotEmpty(),
                        { s ->
                            val first = sources.isEmpty()
                            val l = if (editSrc != null) sources.map { if (it.id == s.id) s else it } else sources + s
                            saveSources(ctx, l)
                            sources = l
                            editSrc = null
                            screen = if (first) "" else "sources"
                        },
                        {
                            editSrc = null
                            screen = "sources"
                        }
                    )
                }
                screen == "sources" -> Solid {
                    SourcesScreen(
                        sources, cfg, all, epg.chans, report,
                        { editSrc = null; screen = "form" },
                        { editSrc = it; screen = "form" },
                        { d ->
                            val l = sources.filter { it.id != d.id }
                            saveSources(ctx, l)
                            sources = l
                        },
                        { cfg = it; saveCfg(ctx, it) },
                        { reload++ },
                        { screen = "" }
                    )
                }
                screen == "settings" -> Solid {
                    SettingsScreen(all, cfg, epg.chans, { cfg = it; saveCfg(ctx, it) }) { screen = "" }
                }
                screen == "search" -> Solid {
                    SearchScreen(shown, epg, { play(it); full = true; screen = "" }) { screen = "" }
                }
                screen == "guide" -> GuideScreen(shown, epg, { play(it); full = true; screen = "" }) { screen = "" }
                cur != null && full -> PlayerScreen(
                    cur, exo, shown, epg, all, cfg, { cfg = it; saveCfg(ctx, it) }, { play(it) }
                ) { full = false }
                else -> Home(
                    shown, sources, epg, status, hist, cur,
                    { play(it); full = true },
                    { screen = "search" }, { screen = "settings" }, { screen = "sources" },
                    { editCh = it.id },
                    { screen = "guide" }
                )
            }
            val ec = editCh
            if (ec != null) {
                ChannelEditor(ec, all, cfg, epg.chans, { cfg = it; saveCfg(ctx, it) }) { editCh = null }
            }
        }
    }
}

class HomeRow(val header: Boolean, val title: String, val chans: List<Channel>)

// Une section par source (si plusieurs), puis une rangée par catégorie.
fun buildRows(ch: List<Channel>, sources: List<Source>): List<HomeRow> {
    val out = ArrayList<HomeRow>()
    val multi = sources.size > 1
    val bySrc = ch.groupBy { it.src }
    val order = sources.map { it.id } + bySrc.keys.filter { k -> sources.none { it.id == k } }
    for (sid in order) {
        val list = bySrc[sid] ?: continue
        if (multi) out.add(HomeRow(true, sources.firstOrNull { it.id == sid }?.name ?: "Autre", emptyList()))
        for ((cat, l) in list.groupBy { it.cat }) out.add(HomeRow(false, cat, l))
    }
    return out
}

@Composable
fun TopTab(label: String, selected: Boolean, w: Dp, onClick: () -> Unit) {
    Column(Modifier.width(w)) {
        FocusRow(onClick) {
            Text(
                label, color = if (selected) Color.White else MUTED, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) ACCENT else Color.Transparent))
    }
}

@Composable
fun Home(
    ch: List<Channel>, sources: List<Source>, epg: Epg, status: String, hist: List<Int>, cur: Channel?,
    onPlay: (Channel) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit, onSources: () -> Unit,
    onEdit: (Channel) -> Unit, onGuide: () -> Unit
) {
    val cp = cur?.let { epg.now(it.epgId) }
    val recent = remember(hist, ch) { hist.mapNotNull { id -> ch.firstOrNull { it.id == id } } }
    val rows = remember(ch, sources) { buildRows(ch, sources) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x99000000), 0.4f to Color(0x55000000),
                    0.62f to Color(0xEB0E1117), 1f to Color(0xFF0E1117)
                )
            )
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xB3000000)).padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "IPTV", color = ACCENT, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 28.dp)
                )
                TopTab("Ma TV", true, 120.dp) { }
                TopTab("Guide", false, 110.dp) { onGuide() }
                TopTab("Recherche", false, 150.dp) { onSearch() }
                TopTab("Réglages", false, 140.dp) { onSettings() }
                TopTab("Sources et guides", false, 220.dp) { onSources() }
            }
            Box(Modifier.weight(0.38f).fillMaxWidth()) {
                Column(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 8.dp)) {
                    if (cur != null) {
                        Text(cur.name, color = MUTED, fontSize = 16.sp)
                        Text(
                            cp?.title ?: "", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (cp != null) Text("${hm(cp.start)} - ${hm(cp.stop)}", color = MUTED, fontSize = 14.sp)
                    } else {
                        Text("Choisis une chaîne pour démarrer", color = MUTED, fontSize = 20.sp)
                    }
                }
            }
            LazyColumn(
                Modifier.weight(0.62f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp)
            ) {
                if (status.isNotBlank()) {
                    item { Text(status, color = Color(0xFFFFB4A9), fontSize = 13.sp) }
                }
                if (recent.isNotEmpty()) {
                    item {
                        Text(
                            "CONTINUER À REGARDER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(recent, key = { it.id }) { c ->
                                ChannelCard(c, epg, { onPlay(c) }, { onEdit(c) })
                            }
                        }
                    }
                }
                items(rows) { r ->
                    if (r.header) {
                        Text(
                            r.title.uppercase(), color = ACCENT, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 22.dp, bottom = 2.dp)
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(ACCENT))
                    } else {
                        Text(
                            r.title.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(r.chans, key = { it.id }) { c ->
                                ChannelCard(c, epg, { onPlay(c) }, { onEdit(c) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// Carte de chaîne : image du programme, logo en bas à gauche, barre de progression, titre et suite.
@Composable
fun ChannelCard(c: Channel, epg: Epg, onClick: () -> Unit, onLong: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { BooleanArray(1) }
    val p = epg.now(c.epgId)
    val nx = epg.upcoming(c.epgId, 1).firstOrNull()
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.width(220.dp)
            .onFocusChanged { f = it.isFocused }
            .onPreviewKeyEvent { okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color(0xFF1B2230))
                .border(if (f) 3.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
        ) {
            if (p != null && p.icon.isNotBlank()) {
                AsyncImage(
                    model = p.icon, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    c.name, color = MUTED, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                )
            }
            if (c.icon.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color(0x99000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    AsyncImage(
                        model = c.icon, contentDescription = null,
                        modifier = Modifier.size(48.dp, 26.dp), contentScale = ContentScale.Fit
                    )
                }
            }
            if (p != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                    Box(
                        Modifier.fillMaxWidth(fracOf(p, System.currentTimeMillis())).fillMaxHeight()
                            .background(Color(0xFF1E88E5))
                    )
                }
            }
        }
        Text(
            p?.title ?: c.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            if (nx != null) "${hm(nx.start)} · ${nx.title}" else if (p != null) c.name else "",
            color = MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    c: Channel, exo: ExoPlayer, channels: List<Channel>, epg: Epg, all: List<Channel>, cfg: Cfg,
    onCfg: (Cfg) -> Unit, onZap: (Channel) -> Unit, onBack: () -> Unit
) {
    val num = channels.indexOf(c) + 1
    val p = epg.now(c.epgId)
    val nx = epg.upcoming(c.epgId, 1).firstOrNull()
    var show by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var editId by remember { mutableStateOf<Int?>(null) }
    var focusId by remember { mutableStateOf(c.id) }
    var refocus by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var res by remember { mutableStateOf("") }
    var codec by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }

    LaunchedEffect(c.id) {
        show = true
        tick++
    }
    LaunchedEffect(panel, tracksOpen, editId) {
        delay(100)
        if (!panel && !tracksOpen && editId == null) { try { fr.requestFocus() } catch (ex: Exception) { } }
    }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(tick) { delay(7000); show = false }
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
    BackHandler { if (panel) panel = false else onBack() }

    Box(
        Modifier.fillMaxSize().focusRequester(fr).focusable().onPreviewKeyEvent {
            if (panel || tracksOpen || editId != null || it.type != KeyEventType.KeyDown) {
                false
            } else if (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter) {
                panel = true
                focusId = c.id
                true
            } else if (it.key == Key.DirectionLeft || it.key == Key.Menu) {
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
        if (show && !panel) InfoBar(c, num, p, nx, nowMs, res, codec, quality, Modifier.align(Alignment.BottomCenter))
        if (panel) {
            ChannelPanel(
                channels.filter { it.cat == c.cat }, c, epg, nowMs, focusId, refocus,
                { onZap(it); panel = false }, { editId = it.id }
            )
        }
        if (tracksOpen) TracksDialog(tracks, exo) { tracksOpen = false }
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

@Composable
fun InfoBar(
    c: Channel, num: Int, p: Prog?, nx: Prog?, nowMs: Long,
    res: String, codec: String, quality: String, modifier: Modifier
) {
    Column(modifier.fillMaxWidth()) {
        if (p != null && p.icon.isNotBlank()) {
            AsyncImage(
                model = p.icon, contentDescription = null,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).width(160.dp).height(90.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xCC12151C)).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$num", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.width(24.dp))
            Text(c.name, color = Color.White, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(codec, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text(quality, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            Text(hm(nowMs), color = Color.White, fontSize = 22.sp)
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xF00B0D12)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgPanel(p, nowMs, true, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 16.dp).width(160.dp).height(80.dp), contentAlignment = Alignment.Center) {
                if (c.icon.isNotBlank()) {
                    AsyncImage(
                        model = c.icon, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                    )
                } else {
                    Text(c.name, color = Color.White, fontSize = 18.sp, maxLines = 2)
                }
            }
            ProgPanel(nx, nowMs, false, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF080A0E)).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(c.cat, color = ACCENT, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(res, color = Color(0xFF2EE05A), fontSize = 14.sp)
            Text(
                SimpleDateFormat("EEEE dd.MM.yyyy", Locale.getDefault()).format(Date(nowMs)),
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ProgPanel(pr: Prog?, nowMs: Long, current: Boolean, modifier: Modifier) {
    Column(modifier) {
        if (pr == null) {
            Text("—", color = MUTED, fontSize = 20.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${hm(pr.start)} - ${hm(pr.stop)}", color = Color.White, fontSize = 18.sp)
                if (current) {
                    val total = (pr.stop - pr.start).coerceAtLeast(1L)
                    val frac = ((nowMs - pr.start).toFloat() / total).coerceIn(0f, 1f)
                    Box(Modifier.padding(horizontal = 12.dp).weight(1f).height(3.dp).background(Color(0xFF444B58))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.Red))
                    }
                    Text("+${((pr.stop - nowMs) / 60000).coerceAtLeast(0)} min", color = Color.White, fontSize = 16.sp)
                } else {
                    Spacer(Modifier.weight(1f))
                    Text("${(pr.stop - pr.start) / 60000} min", color = Color.White, fontSize = 16.sp)
                }
            }
            Text(
                pr.title, color = ACCENT, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (pr.sub.isNotBlank()) {
                Text(pr.sub, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (pr.cat.isNotBlank()) {
                Text(pr.cat, color = MUTED, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
