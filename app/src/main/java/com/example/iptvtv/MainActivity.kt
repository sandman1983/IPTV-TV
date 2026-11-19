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

data class Channel(
    val id: Int, val name: String, val icon: String, val epgId: String, val cat: String,
    val hidden: Boolean = false, val url: String = ""
)

data class Prog(
    val title: String, val sub: String, val cat: String, val icon: String,
    val start: Long, val stop: Long, val desc: String = ""
)

data class GuideCh(val id: String, val name: String, val icon: String)

class Epg(
    val now: Map<String, Prog>,
    val next: Map<String, List<Prog>>,
    val chans: List<GuideCh> = emptyList()
) {
    val icons: Map<String, String> by lazy {
        chans.filter { it.icon.isNotBlank() }.associate { it.id to it.icon }
    }
}

// Fusionne deux guides : le premier a la priorité.
fun mergeEpg(a: Epg, b: Epg): Epg {
    val ids = a.chans.map { it.id }.toHashSet()
    return Epg(b.now + a.now, b.next + a.next, a.chans + b.chans.filter { it.id !in ids })
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

// Lit un guide XMLTV (.gz ou non) en flux : chaînes du guide (nom, logo) + programme en cours
// et 5 programmes suivants de chaque chaîne.
suspend fun loadEpg(url: String): Epg = withContext(Dispatchers.IO) {
    val raw = URL(url).openStream().buffered()
    raw.mark(2)
    val b1 = raw.read()
    val b2 = raw.read()
    raw.reset()
    val input = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
    val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    val now = System.currentTimeMillis()
    val cur = HashMap<String, Prog>()
    val nxt = HashMap<String, ArrayList<Prog>>()
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
                val pr = Prog(title, sub.ifBlank { desc }, cat, icon, st, sp, desc)
                if (st <= now && now < sp) {
                    cur[ch] = pr
                } else if (st >= now) {
                    val l = nxt.getOrPut(ch) { ArrayList() }
                    l.add(pr)
                    if (l.size > 5) {
                        l.sortBy { it.start }
                        l.removeAt(l.size - 1)
                    }
                }
            }
        }
        ev = p.next()
    }
    input.close()
    for (l in nxt.values) l.sortBy { it.start }
    Epg(cur, nxt, chans)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var sources by remember { mutableStateOf(loadSources(ctx)) }
    var cfg by remember { mutableStateOf(loadCfg(ctx)) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var epg by remember { mutableStateOf(Epg(emptyMap(), emptyMap())) }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var screen by remember { mutableStateOf("") }
    var editSrc by remember { mutableStateOf<Source?>(null) }
    var editCh by remember { mutableStateOf<Int?>(null) }
    val all = remember(channels, cfg, epg) { applyCfg(channels, cfg, epg.icons) }
    val shown = remember(all) { all.filter { !it.hidden } }

    LaunchedEffect(sources, cfg.epgs) {
        status = "Chargement des chaînes…"
        val chs = ArrayList<Channel>()
        val urls = ArrayList<String>(cfg.epgs)
        val errs = ArrayList<String>()
        for (s in sources) {
            try {
                val r = loadSource(ctx, s)
                chs.addAll(r.first)
                urls.add(s.epg.ifBlank { r.second })
            } catch (ex: Exception) {
                errs.add("${s.name} : ${ex.message}")
            }
        }
        val seen = HashSet<Int>()
        channels = chs.map { c ->
            var id = c.id
            while (!seen.add(id)) id++
            if (id == c.id) c else c.copy(id = id)
        }
        var g = Epg(emptyMap(), emptyMap())
        epg = g
        var n = 0
        for (u in urls.filter { it.isNotBlank() }.distinct()) {
            n++
            status = "Chargement du guide $n…"
            try {
                g = mergeEpg(g, loadEpg(u))
                epg = g
            } catch (ex: Exception) {
                errs.add("Guide : ${ex.message}")
            }
        }
        status = if (errs.isEmpty()) "" else errs.joinToString(" ; ")
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E1117))) {
            val pl = playing
            when {
                sources.isEmpty() || screen == "form" -> SourceForm(
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
                screen == "sources" -> SourcesScreen(
                    sources, cfg, all, epg.chans,
                    { editSrc = null; screen = "form" },
                    { editSrc = it; screen = "form" },
                    { d ->
                        val l = sources.filter { it.id != d.id }
                        saveSources(ctx, l)
                        sources = l
                    },
                    { cfg = it; saveCfg(ctx, it) },
                    { screen = "" }
                )
                screen == "settings" -> SettingsScreen(all, cfg, epg.chans, { cfg = it; saveCfg(ctx, it) }) { screen = "" }
                pl != null -> PlayerScreen(pl, shown, epg, { playing = it }) { playing = null }
                else -> Home(
                    shown, epg, status, { playing = it },
                    { screen = "sources" }, { screen = "settings" }, { editCh = it.id }
                )
            }
            val ec = editCh
            if (ec != null) {
                ChannelEditor(ec, all, cfg, epg.chans, { cfg = it; saveCfg(ctx, it) }) { editCh = null }
            }
        }
    }
}

@Composable
fun Home(
    ch: List<Channel>, epg: Epg, status: String,
    onPlay: (Channel) -> Unit, onSources: () -> Unit, onSettings: () -> Unit, onEdit: (Channel) -> Unit
) {
    var hero by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(ch) { hero = ch.firstOrNull { it.id == hero?.id } ?: ch.firstOrNull() }
    val groups = remember(ch) { ch.groupBy { it.cat }.entries.toList() }
    val hp = hero?.let { epg.now[it.epgId] }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            if (hp != null && hp.icon.isNotBlank()) {
                AsyncImage(
                    model = hp.icon, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = 0.45f
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                Text(hero?.name ?: "IPTV", color = Color(0xFFB0B8C8), fontSize = 18.sp)
                Text(
                    hp?.title ?: status.ifBlank { "Sélectionne une chaîne" },
                    color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (hp != null) Text("${hm(hp.start)} - ${hm(hp.stop)}", color = Color(0xFFB0B8C8), fontSize = 16.sp)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusRow({ onSettings() }, Modifier.width(160.dp)) {
                        Text("Réglages", color = Color.White, fontSize = 15.sp)
                    }
                    FocusRow({ onSources() }, Modifier.width(220.dp)) {
                        Text("Sources et guides", color = Color(0xFF8AB4F8), fontSize = 15.sp)
                    }
                }
            }
            if (status.isNotBlank() && hp != null) {
                item { Text(status, color = Color(0xFFFFB4A9), fontSize = 13.sp) }
            }
            items(groups) { g ->
                Text(
                    g.key, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(g.value) { c ->
                        ChannelCard(c, epg.now[c.epgId], { hero = c }, { onPlay(c) }, { onEdit(c) })
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(c: Channel, p: Prog?, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val fired = remember { BooleanArray(1) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(200.dp).clip(shape).background(Color(0xFF1B2230))
            .border(if (f) 3.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { okLong(it, fired, onClick, onLong) }
            .clickable { onClick() }
    ) {
        val img = if (p != null && p.icon.isNotBlank()) p.icon else c.icon
        Box(Modifier.fillMaxWidth().height(112.dp).background(Color(0xFF111620))) {
            if (img.isNotBlank()) {
                AsyncImage(
                    model = img, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = if (p != null && p.icon.isNotBlank()) ContentScale.Crop else ContentScale.Fit
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(c.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(p?.title ?: "—", color = Color(0xFFB0B8C8), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    c: Channel, channels: List<Channel>, epg: Epg,
    onZap: (Channel) -> Unit, onBack: () -> Unit
) {
    val num = channels.indexOf(c) + 1
    val p = epg.now[c.epgId]
    val nx = epg.next[c.epgId]?.firstOrNull()
    val ctx = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            )
            .build()
    }
    var show by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var tick by remember { mutableStateOf(0) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var res by remember { mutableStateOf("") }
    var codec by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }

    LaunchedEffect(c.id) {
        resetTracks(exo)
        exo.setMediaItem(MediaItem.fromUri(c.url))
        exo.prepare()
        exo.playWhenReady = true
        show = true
        tick++
    }
    LaunchedEffect(panel, tracksOpen) {
        delay(100)
        if (!panel && !tracksOpen) { try { fr.requestFocus() } catch (ex: Exception) { } }
    }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(tick) { delay(7000); show = false }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
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
        onDispose { exo.removeListener(l); exo.release() }
    }
    BackHandler { if (panel) panel = false else onBack() }

    Box(
        Modifier.fillMaxSize().focusRequester(fr).focusable().onPreviewKeyEvent {
            if (panel || tracksOpen || it.type != KeyEventType.KeyDown) {
                false
            } else if (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter) {
                panel = true
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
        AndroidView(
            factory = { PlayerView(it).apply { player = exo; useController = false } },
            modifier = Modifier.fillMaxSize()
        )
        if (show && !panel) InfoBar(c, num, p, nx, nowMs, res, codec, quality, Modifier.align(Alignment.BottomCenter))
        if (panel) {
            ChannelPanel(channels.filter { it.cat == c.cat }, c, epg, nowMs) { onZap(it); panel = false }
        }
        if (tracksOpen) TracksDialog(tracks, exo) { tracksOpen = false }
    }
}

@Composable
fun InfoBar(
    c: Channel, num: Int, p: Prog?, nx: Prog?, nowMs: Long,
    res: String, codec: String, quality: String, modifier: Modifier
) {
    val cyan = Color(0xFF1EB6E8)
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
            Text(c.cat, color = cyan, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
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
    val cyan = Color(0xFF1EB6E8)
    val gray = Color(0xFFB0B8C8)
    Column(modifier) {
        if (pr == null) {
            Text("—", color = gray, fontSize = 20.sp)
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
                pr.title, color = cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (pr.sub.isNotBlank()) {
                Text(pr.sub, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (pr.cat.isNotBlank()) {
                Text(pr.cat, color = gray, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
