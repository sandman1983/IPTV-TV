package com.example.iptvtv

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// kind : "rec" (enregistrement) ou "rem" (rappel) ; state : scheduled / recording / done / failed
data class Rec(
    val id: String, val chId: Int, val chName: String, val url: String, val title: String,
    val start: Long, val stop: Long, val kind: String, val state: String,
    val path: String = "", val note: String = "",
    val thumb: String = "", val logo: String = ""
)

fun loadRecs(c: Context): List<Rec> {
    val s = c.getSharedPreferences("rec", 0).getString("j", null) ?: return emptyList()
    return try {
        val a = JSONArray(s)
        (0 until a.length()).map {
            val x = a.getJSONObject(it)
            Rec(
                x.optString("i"), x.optInt("c"), x.optString("n"), x.optString("u"), x.optString("t"),
                x.optLong("s"), x.optLong("e"), x.optString("k"), x.optString("st"),
                x.optString("p"), x.optString("no"), x.optString("th"), x.optString("lg")
            )
        }
    } catch (ex: Exception) {
        emptyList()
    }
}

fun saveRecs(c: Context, l: List<Rec>) {
    val a = JSONArray()
    for (r in l) {
        a.put(
            JSONObject().put("i", r.id).put("c", r.chId).put("n", r.chName).put("u", r.url).put("t", r.title)
                .put("s", r.start).put("e", r.stop).put("k", r.kind).put("st", r.state)
                .put("p", r.path).put("no", r.note).put("th", r.thumb).put("lg", r.logo)
        )
    }
    c.getSharedPreferences("rec", 0).edit().putString("j", a.toString()).apply()
}

private class Counter(var n: Long = 0)

object Dvr {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    var tick by mutableStateOf(0)

    @Synchronized
    fun update(ctx: Context, id: String, f: (Rec) -> Rec) {
        saveRecs(ctx, loadRecs(ctx).map { if (it.id == id) f(it) else it })
        tick++
    }

    // Au démarrage de l'app : ce qui était en cours dans un ancien processus est marqué interrompu.
    @Synchronized
    fun cleanup(ctx: Context) {
        val now = System.currentTimeMillis()
        val l = loadRecs(ctx).mapNotNull {
            when {
                it.state == "recording" && !jobs.containsKey(it.id) -> it.copy(state = "failed", note = "Interrupted")
                it.kind == "rem" && it.state != "scheduled" && it.stop < now - 86400000L -> null
                else -> it
            }
        }
        saveRecs(ctx, l)
        tick++
    }

    @Synchronized
    fun schedule(ctx: Context, ch: Channel, p: Prog, kind: String) {
        val id = "${ch.id}_${p.start}_$kind"
        val l = loadRecs(ctx).filter { it.id != id }
        saveRecs(
            ctx,
            l + Rec(id, ch.id, ch.name, ch.url, p.title, p.start, p.stop, kind, "scheduled", thumb = p.icon, logo = ch.icon)
        )
        tick++
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        jobs[id]?.cancel()
        saveRecs(ctx, loadRecs(ctx).filter { it.id != id })
        tick++
    }

    fun stop(id: String) {
        jobs[id]?.cancel()
    }

    fun delete(ctx: Context, r: Rec) {
        jobs[r.id]?.cancel()
        if (r.path.isNotBlank()) {
            try {
                val u = Uri.parse(r.path)
                if (u.scheme == "file") File(u.path ?: "").delete()
                else DocumentsContract.deleteDocument(ctx.contentResolver, u)
            } catch (ex: Exception) { }
        }
        remove(ctx, r.id)
    }

    fun start(ctx: Context, r: Rec, cfg: Cfg) {
        if (jobs.containsKey(r.id)) return
        val app = ctx.applicationContext
        val job = scope.launch {
            update(app, r.id) { it.copy(state = "recording") }
            var path = ""
            try {
                val endAt = r.stop + cfg.recStop * 60000L
                val (os, p) = openOut(app, cfg, r)
                path = p
                os.use { out -> recordStream(r.url, out, endAt) }
                update(app, r.id) { it.copy(state = "done", path = path) }
            } catch (ce: CancellationException) {
                update(app, r.id) { it.copy(state = "done", path = path) }
                throw ce
            } catch (ex: Exception) {
                update(app, r.id) { it.copy(state = "failed", path = path, note = ex.message ?: "Error") }
            } finally {
                jobs.remove(r.id)
            }
        }
        jobs[r.id] = job
    }

    private fun safe(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()

    private fun openOut(ctx: Context, cfg: Cfg, r: Rec): Pair<OutputStream, String> {
        val name = safe(r.chName) + " - " + safe(r.title) + " - " +
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(r.start)) + ".ts"
        if (cfg.dvrUri.isNotBlank()) {
            val tree = Uri.parse(cfg.dvrUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/octet-stream", name)
                ?: throw IOException("Cannot create a file in the DVR folder")
            val os = ctx.contentResolver.openOutputStream(doc) ?: throw IOException("Cannot write in the DVR folder")
            return Pair(os, doc.toString())
        }
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Recordings")
        dir.mkdirs()
        val f = File(dir, name)
        return Pair(FileOutputStream(f), Uri.fromFile(f).toString())
    }

    private fun open(url: String): HttpURLConnection {
        var u = url
        for (i in 0 until 6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) IPTV-TV")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Bad redirect")
                u = URL(URL(u), loc).toString()
                c.disconnect()
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("HTTP $code")
            }
            return c
        }
        throw IOException("Too many redirects")
    }

    private fun httpText(url: String): String {
        val c = open(url)
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun downloadTo(url: String, out: OutputStream, cnt: Counter) {
        val c = open(url)
        try {
            c.inputStream.use { ins ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    cnt.n += n
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun recordStream(url: String, out: OutputStream, endAt: Long) {
        val cnt = Counter()
        val m3u8 = Regex("/live/[^/]+/[^/]+/\\d+\\.m3u8$").containsMatchIn(url)
        if (m3u8) {
            // Xtream : le flux .ts direct est plus simple et plus fiable que le HLS.
            try {
                recordRaw(url.removeSuffix(".m3u8") + ".ts", out, endAt, cnt)
                return
            } catch (ex: IOException) {
                if (cnt.n > 0) throw ex
            }
        }
        if (url.contains("m3u8", true)) recordHls(url, out, endAt, cnt) else recordRaw(url, out, endAt, cnt)
    }

    private suspend fun recordRaw(url: String, out: OutputStream, endAt: Long, cnt: Counter) {
        val c = open(url)
        try {
            c.inputStream.use { ins ->
                val buf = ByteArray(65536)
                while (System.currentTimeMillis() < endAt) {
                    currentCoroutineContext().ensureActive()
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    cnt.n += n
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun recordHls(url: String, out: OutputStream, endAt: Long, cnt: Counter) {
        var mediaUrl = url
        var txt = httpText(mediaUrl)
        if (txt.contains("#EXT-X-STREAM-INF")) {
            var best = -1L
            var bestUri = ""
            val lines = txt.lines()
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                    if (j < lines.size && bw >= best) {
                        best = bw
                        bestUri = lines[j].trim()
                    }
                }
            }
            if (bestUri.isEmpty()) throw IOException("Empty HLS playlist")
            mediaUrl = URL(URL(url), bestUri).toString()
        }
        val seen = HashSet<String>()
        val order = ArrayDeque<String>()
        var first = true
        var empties = 0
        while (System.currentTimeMillis() < endAt) {
            currentCoroutineContext().ensureActive()
            txt = httpText(mediaUrl)
            if (txt.contains("#EXT-X-KEY") && !txt.contains("METHOD=NONE")) throw IOException("Encrypted stream (not supported)")
            var target = 6.0
            Regex("#EXT-X-TARGETDURATION:(\\d+)").find(txt)?.let { target = it.groupValues[1].toDouble() }
            val segs = txt.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { URL(URL(mediaUrl), it).toString() }
            if (first) {
                first = false
                for (s in segs.dropLast(3)) seen.add(s)
            }
            var got = 0
            for (su in segs) {
                if (seen.add(su)) {
                    order.addLast(su)
                    if (order.size > 400) seen.remove(order.removeFirst())
                    downloadTo(su, out, cnt)
                    got++
                    if (System.currentTimeMillis() >= endAt) break
                }
            }
            if (txt.contains("#EXT-X-ENDLIST")) break
            if (got == 0) {
                empties++
                if (empties > 20) throw IOException("No new segments")
            } else {
                empties = 0
            }
            delay((target * 400).toLong().coerceAtLeast(1000L))
        }
    }
}
