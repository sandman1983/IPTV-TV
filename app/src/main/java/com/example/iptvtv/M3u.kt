package com.example.iptvtv

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONArray

private val ATTR = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

private fun m3uAttrs(line: String): Map<String, String> =
    ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

// Lit une liste M3U / M3U8 depuis un lien (http, https) ou un fichier local (content://, file://).
fun loadM3u(ctx: Context, src: String, sid: String): Pair<List<Channel>, String> {
    val stream = if (src.startsWith("content://") || src.startsWith("file://")) {
        ctx.contentResolver.openInputStream(Uri.parse(src)) ?: throw Exception("Fichier illisible")
    } else {
        URL(src).openStream()
    }
    val out = ArrayList<Channel>()
    var epgUrl = ""
    var hls = false
    var pending = false
    var pendingName = ""
    var pendingAttrs: Map<String, String> = emptyMap()
    var group = ""
    stream.bufferedReader().useLines { seq ->
        for (raw in seq) {
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U")) {
                val a = m3uAttrs(line)
                epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: "").split(",").first().trim()
            } else if (line.startsWith("#EXTINF")) {
                pendingAttrs = m3uAttrs(line)
                val q = line.lastIndexOf('"')
                val comma = line.indexOf(',', if (q >= 0) q else 0)
                pendingName = if (comma >= 0) line.substring(comma + 1).trim() else ""
                pending = true
            } else if (line.startsWith("#EXTGRP:")) {
                group = line.substring(8).trim()
            } else if (line.startsWith("#EXT-X-STREAM-INF") || line.startsWith("#EXT-X-TARGETDURATION")) {
                hls = true
            } else if (line.startsWith("#")) {
                // autres balises ignorées
            } else if (pending) {
                val a = pendingAttrs
                val name = pendingName.ifBlank { a["tvg-name"] ?: line }
                val cat = (a["group-title"] ?: "").ifBlank { group }.ifBlank { "No category" }
                val epgId = (a["tvg-id"] ?: "").ifBlank { a["tvg-name"] ?: "" }
                out.add(Channel("$sid|$line".hashCode(), name, a["tvg-logo"] ?: "", epgId, cat, false, line, sid))
                pending = false
            }
        }
    }
    // Un .m3u8 qui est un flux HLS unique (et non une liste de chaînes) : une seule chaîne.
    if (hls) {
        if (!src.startsWith("http")) throw Exception("Local HLS stream not supported: use a link (URL)")
        val n = src.substringAfterLast('/').substringBefore('?').ifBlank { "Flux direct" }
        return Pair(listOf(Channel("$sid|$src".hashCode(), n, "", "", "Flux direct", false, src, sid)), "")
    }
    if (out.isEmpty()) throw Exception("No channel found in this list")
    return Pair(out, epgUrl)
}

// Xtream Codes : chaînes en direct + URL du guide par défaut.
fun loadXtream(s: Source): Pair<List<Channel>, String> {
    val api = "${s.host}/player_api.php?username=${s.user}&password=${s.pass}"
    val ca = JSONArray(get("$api&action=get_live_categories"))
    val cats = HashMap<String, String>()
    for (i in 0 until ca.length()) {
        val o = ca.getJSONObject(i)
        cats[o.str("category_id")] = o.str("category_name")
    }
    val st = JSONArray(get("$api&action=get_live_streams"))
    val list = (0 until st.length()).map {
        val o = st.getJSONObject(it)
        val n = o.getInt("stream_id")
        Channel(
            "${s.id}:$n".hashCode(), o.str("name"), o.str("stream_icon"), o.str("epg_channel_id"),
            cats[o.str("category_id")] ?: "Autres", false, "${s.host}/live/${s.user}/${s.pass}/$n.m3u8", s.id
        )
    }
    return Pair(list, "${s.host}/xmltv.php?username=${s.user}&password=${s.pass}")
}

suspend fun loadSource(ctx: Context, s: Source): Pair<List<Channel>, String> = withContext(Dispatchers.IO) {
    val r = if (s.type == "m3u") loadM3u(ctx, s.url, s.id) else loadXtream(s)
    Pair(r.first.map { it.copy(src = s.name) }, r.second)
}


// Informations de compte Xtream (statut, expiration, connexions).
suspend fun fetchAccount(s: Source): String = withContext(Dispatchers.IO) {
    if (s.type != "xtream") return@withContext "Account info is only available for Xtream sources."
    try {
        val j = org.json.JSONObject(get("${s.host}/player_api.php?username=${s.user}&password=${s.pass}"))
        val u = j.optJSONObject("user_info") ?: return@withContext "No answer from the server: wrong host, username or password."
        if (u.optInt("auth", 1) == 0) return@withContext "Login refused: wrong username or password."
        val st = u.optString("status")
        val exp = u.optString("exp_date")
        val expTxt = if (exp.isBlank() || exp == "null") "Unlimited"
        else java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(exp.toLong() * 1000))
        val left = if (exp.isBlank() || exp == "null") "" else {
            val d = (exp.toLong() * 1000 - System.currentTimeMillis()) / 86400000L
            if (d < 0) " (expired)" else " ($d days left)"
        }
        "Status: $st\nExpires: $expTxt$left\nConnections: ${u.optString("active_cons")} / ${u.optString("max_connections")}" +
            (if (u.optString("is_trial") == "1") "\nTrial account" else "")
    } catch (ex: Exception) {
        "Cannot reach the server: " + (ex.message ?: ex.javaClass.simpleName)
    }
}
