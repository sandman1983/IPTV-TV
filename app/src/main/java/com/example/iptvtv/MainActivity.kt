package com.example.iptvtv

import android.content.Context
import android.os.Bundle
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

data class Channel(val id: Int, val name: String, val icon: String, val epgId: String, val cat: String)
data class Prog(val title: String, val icon: String, val start: Long, val stop: Long)
data class Creds(val host: String, val user: String, val pass: String, val epgUrl: String)

fun load(c: Context): Creds? {
    val p = c.getSharedPreferences("p", 0)
    val h = p.getString("h", null) ?: return null
    return Creds(h, p.getString("u", "") ?: "", p.getString("w", "") ?: "", p.getString("e", "") ?: "")
}

fun save(c: Context, k: Creds) {
    c.getSharedPreferences("p", 0).edit()
        .putString("h", k.host).putString("u", k.user).putString("w", k.pass).putString("e", k.epgUrl).apply()
}

fun norm(h: String): String {
    val t = h.trim().trimEnd('/')
    return if (t.startsWith("http")) t else "http://$t"
}

fun get(url: String): String = URL(url).openStream().bufferedReader().use { it.readText() }

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

suspend fun loadChannels(k: Creds): List<Channel> = withContext(Dispatchers.IO) {
    val api = "${k.host}/player_api.php?username=${k.user}&password=${k.pass}"
    val ca = JSONArray(get("$api&action=get_live_categories"))
    val cats = HashMap<String, String>()
    for (i in 0 until ca.length()) {
        val o = ca.getJSONObject(i)
        cats[o.str("category_id")] = o.str("category_name")
    }
    val s = JSONArray(get("$api&action=get_live_streams"))
    (0 until s.length()).map {
        val o = s.getJSONObject(it)
        Channel(o.getInt("stream_id"), o.str("name"), o.str("stream_icon"), o.str("epg_channel_id"),
            cats[o.str("category_id")] ?: "Autres")
    }
}

fun parseDate(f: SimpleDateFormat, s: String?): Long =
    try { f.parse(s ?: "")?.time ?: 0L } catch (ex: Exception) { 0L }

// Lit le guide XMLTV (.gz ou non) en flux et garde le programme en cours de chaque chaîne.
suspend fun loadEpg(k: Creds): Map<String, Prog> = withContext(Dispatchers.IO) {
    val url = k.epgUrl.ifBlank { "${k.host}/xmltv.php?username=${k.user}&password=${k.pass}" }
    val raw = URL(url).openStream().buffered()
    raw.mark(2)
    val b1 = raw.read()
    val b2 = raw.read()
    raw.reset()
    val input = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
    val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    val now = System.currentTimeMillis()
    val out = HashMap<String, Prog>()
    val p = Xml.newPullParser()
    p.setInput(input, null)
    var ch = ""
    var st = 0L
    var sp = 0L
    var title = ""
    var icon = ""
    var inProg = false
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
            val n = p.name
            if (n == "programme") {
                inProg = true
                ch = p.getAttributeValue(null, "channel") ?: ""
                st = parseDate(fmt, p.getAttributeValue(null, "start"))
                sp = parseDate(fmt, p.getAttributeValue(null, "stop"))
                title = ""
                icon = ""
            } else if (n == "title" && inProg) {
                title = p.nextText()
            } else if (n == "icon" && inProg) {
                icon = p.getAttributeValue(null, "src") ?: ""
            }
        } else if (ev == XmlPullParser.END_TAG && p.name == "programme") {
            inProg = false
            if (st <= now && now < sp) out[ch] = Prog(title, icon, st, sp)
        }
        ev = p.next()
    }
    input.close()
    out
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
    var creds by remember { mutableStateOf(load(ctx)) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var epg by remember { mutableStateOf<Map<String, Prog>>(emptyMap()) }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(creds) {
        val k = creds ?: return@LaunchedEffect
        status = "Chargement des chaînes…"
        try {
            channels = loadChannels(k)
            status = "Chargement du guide…"
        } catch (ex: Exception) {
            status = "Erreur chaînes : ${ex.message}"
            return@LaunchedEffect
        }
        try {
            epg = loadEpg(k)
            status = ""
        } catch (ex: Exception) {
            status = "Erreur guide : ${ex.message}"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E1117))) {
            val k = creds
            val pl = playing
            when {
                k == null -> Login { save(ctx, it); creds = it }
                pl != null -> Player(pl, k, epg[pl.epgId]) { playing = null }
                else -> Home(channels, epg, status, { playing = it }, { creds = null })
            }
        }
    }
}

@Composable
fun Login(onOk: (Creds) -> Unit) {
    val old = load(LocalContext.current)
    var h by remember { mutableStateOf(old?.host ?: "") }
    var u by remember { mutableStateOf(old?.user ?: "") }
    var w by remember { mutableStateOf(old?.pass ?: "") }
    var e by remember { mutableStateOf(old?.epgUrl ?: "") }
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Connexion Xtream", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        OutlinedTextField(h, { h = it }, label = { Text("Serveur (http://host:port)") }, singleLine = true)
        OutlinedTextField(u, { u = it }, label = { Text("Identifiant") }, singleLine = true)
        OutlinedTextField(w, { w = it }, label = { Text("Mot de passe") }, singleLine = true)
        OutlinedTextField(e, { e = it }, label = { Text("URL du guide (optionnel, .xml ou .gz)") }, singleLine = true)
        Button(onClick = { onOk(Creds(norm(h), u.trim(), w.trim(), e.trim())) }) { Text("Se connecter") }
    }
}

@Composable
fun Home(
    ch: List<Channel>, epg: Map<String, Prog>, status: String,
    onPlay: (Channel) -> Unit, onLogout: () -> Unit
) {
    var hero by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(ch) { if (hero == null) hero = ch.firstOrNull() }
    val groups = remember(ch) { ch.groupBy { it.cat }.entries.toList() }
    val hp = hero?.let { epg[it.epgId] }
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
                    color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold
                )
                if (hp != null) Text("${hm(hp.start)} - ${hm(hp.stop)}", color = Color(0xFFB0B8C8), fontSize = 16.sp)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)) {
            item {
                Text(
                    "Changer de compte", color = Color(0xFF8AB4F8), fontSize = 14.sp,
                    modifier = Modifier.clickable { onLogout() }.padding(vertical = 6.dp)
                )
            }
            items(groups) { g ->
                Text(g.key, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(g.value) { c ->
                        ChannelCard(c, epg[c.epgId], { hero = c }, { onPlay(c) })
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(c: Channel, p: Prog?, onFocus: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(200.dp).clip(shape).background(Color(0xFF1B2230))
            .border(if (f) 3.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
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

@Composable
fun Player(c: Channel, k: Creds, p: Prog?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val exo = remember { ExoPlayer.Builder(ctx).build() }
    LaunchedEffect(c) {
        exo.setMediaItem(MediaItem.fromUri("${k.host}/live/${k.user}/${k.pass}/${c.id}.m3u8"))
        exo.prepare()
        exo.playWhenReady = true
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { player = exo; useController = false } },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.align(Alignment.BottomStart).background(Color(0xAA000000)).padding(16.dp)) {
            Text(c.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (p != null) Text("${p.title}  (${hm(p.start)} - ${hm(p.stop)})", color = Color.White, fontSize = 14.sp)
        }
    }
}
