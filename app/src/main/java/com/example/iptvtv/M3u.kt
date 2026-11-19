package com.example.iptvtv

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val ATTR = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

private fun m3uAttrs(line: String): Map<String, String> =
    ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

// Lit une liste M3U / M3U8 depuis un lien (http, https) ou un fichier local (content://, file://).
// Retourne les chaînes et l'URL du guide déclarée dans l'en-tête (url-tvg / x-tvg-url), si présente.
fun loadM3u(ctx: Context, src: String): Pair<List<Channel>, String> {
    val stream = if (src.startsWith("content://") || src.startsWith("file://")) {
        ctx.contentResolver.openInputStream(Uri.parse(src)) ?: throw Exception("Fichier illisible")
    } else {
        URL(src).openStream()
    }
    val seen = HashSet<Int>()
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
                var id = line.hashCode()
                while (!seen.add(id)) id++
                val cat = (a["group-title"] ?: "").ifBlank { group }.ifBlank { "Sans catégorie" }
                val epgId = (a["tvg-id"] ?: "").ifBlank { a["tvg-name"] ?: "" }
                out.add(Channel(id, name, a["tvg-logo"] ?: "", epgId, cat, false, line))
                pending = false
            }
        }
    }
    // Un .m3u8 qui est un flux HLS unique (et non une liste de chaînes) : une seule chaîne.
    if (hls) {
        if (!src.startsWith("http")) throw Exception("Flux HLS local non supporté : utilise un lien (URL)")
        val n = src.substringAfterLast('/').substringBefore('?').ifBlank { "Flux direct" }
        return Pair(listOf(Channel(src.hashCode(), n, "", "", "Flux direct", false, src)), "")
    }
    if (out.isEmpty()) throw Exception("Aucune chaîne trouvée dans cette liste")
    return Pair(out, epgUrl)
}

// Xtream Codes ou M3U selon ce qui est renseigné. Retourne les chaînes et l'URL de guide par défaut.
suspend fun loadSource(ctx: Context, k: Creds): Pair<List<Channel>, String> = withContext(Dispatchers.IO) {
    if (k.m3u.isNotBlank()) {
        loadM3u(ctx, k.m3u)
    } else {
        Pair(loadChannels(k), "${k.host}/xmltv.php?username=${k.user}&password=${k.pass}")
    }
}
