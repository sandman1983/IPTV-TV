@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.iptvtv

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.net.URLDecoder
import java.net.URLEncoder

// En-têtes HTTP propres à une chaîne (#EXTVLCOPT / #EXTHTTP) : on les garde dans l'adresse sous la forme
//   adresse|User-Agent=...&Referer=...
// puis le lecteur les retire de l'adresse et les ajoute à chaque requête (liste et segments).
class HdrHolder {
    @Volatile var cur: Map<String, String> = emptyMap()
}

object StreamHdr {
    private val HDR = Regex("^[A-Za-z][A-Za-z0-9-]*=")

    fun join(url: String, h: Map<String, String>): String =
        if (h.isEmpty()) url
        else url + "|" + h.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }

    private fun dec(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    fun split(u: String): Pair<String, Map<String, String>> {
        val i = u.indexOf('|')
        if (i <= 0) return Pair(u, emptyMap())
        val rest = u.substring(i + 1)
        if (!HDR.containsMatchIn(rest)) return Pair(u, emptyMap())
        val m = LinkedHashMap<String, String>()
        for (part in rest.split('&')) {
            val e = part.indexOf('=')
            if (e > 0) m[dec(part.substring(0, e))] = dec(part.substring(e + 1))
        }
        return Pair(u.substring(0, i), m)
    }

    // Fabrique de sources de données : agent VLC par défaut, remplacé par les en-têtes de la chaîne en cours.
    fun factory(ctx: Context, holder: HdrHolder): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("User-Agent" to "VLC/3.0.18 LibVLC/3.0.18"))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(ctx, http)) { spec ->
            val h = holder.cur
            if (h.isEmpty()) spec else spec.withAdditionalHeaders(h)
        }
    }
}
