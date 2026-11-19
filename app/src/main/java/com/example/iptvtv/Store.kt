package com.example.iptvtv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Une source de chaînes : type "xtream" ou "m3u" (lien ou fichier local)
data class Source(
    val id: String, val name: String, val type: String,
    val host: String = "", val user: String = "", val pass: String = "",
    val url: String = "", val epg: String = ""
)

// Personnalisation d'une chaîne (vide = valeur d'origine)
data class Ov(
    val name: String = "", val logo: String = "", val epg: String = "",
    val hidden: Boolean = false, val cat: String = ""
)

data class Cfg(
    val ov: Map<Int, Ov> = emptyMap(),
    val order: List<Int> = emptyList(),
    val cats: List<String> = emptyList(),
    val epgs: List<String> = emptyList(),
    val logoPrio: String = "playlist",
    val bgSound: Boolean = true
)

fun loadSources(c: Context): List<Source> {
    val s = c.getSharedPreferences("src", 0).getString("j", null)
    if (s != null) {
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val x = a.getJSONObject(it)
                Source(
                    x.optString("i"), x.optString("n"), x.optString("t"), x.optString("h"),
                    x.optString("u"), x.optString("p"), x.optString("l"), x.optString("e")
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }
    // Reprise de l'ancienne version (une seule source)
    val old = c.getSharedPreferences("p", 0)
    val h = old.getString("h", null) ?: return emptyList()
    val m = old.getString("m", "") ?: ""
    val e = old.getString("e", "") ?: ""
    val src = if (m.isNotBlank()) {
        Source("s1", "Liste M3U", "m3u", url = m, epg = e)
    } else {
        Source("s1", "Xtream", "xtream", h, old.getString("u", "") ?: "", old.getString("w", "") ?: "", epg = e)
    }
    val list = listOf(src)
    saveSources(c, list)
    return list
}

fun saveSources(c: Context, l: List<Source>) {
    val a = JSONArray()
    for (s in l) {
        a.put(
            JSONObject().put("i", s.id).put("n", s.name).put("t", s.type).put("h", s.host)
                .put("u", s.user).put("p", s.pass).put("l", s.url).put("e", s.epg)
        )
    }
    c.getSharedPreferences("src", 0).edit().putString("j", a.toString()).apply()
}

fun loadCfg(c: Context): Cfg {
    val s = c.getSharedPreferences("cfg", 0).getString("j", null) ?: return Cfg()
    return try {
        val j = JSONObject(s)
        val o = j.getJSONObject("ov")
        val m = HashMap<Int, Ov>()
        for (key in o.keys()) {
            val x = o.getJSONObject(key)
            m[key.toInt()] = Ov(x.optString("n"), x.optString("l"), x.optString("e"), x.optBoolean("h"), x.optString("c"))
        }
        val od = j.getJSONArray("order")
        val ord = (0 until od.length()).map { od.getInt(it) }
        val ca = j.getJSONArray("cats")
        val cs = (0 until ca.length()).map { ca.getString(it) }
        val ea = j.optJSONArray("epgs")
        val es = if (ea == null) emptyList() else (0 until ea.length()).map { ea.getString(it) }
        Cfg(m, ord, cs, es, j.optString("lp", "playlist").ifBlank { "playlist" }, j.optBoolean("bs", true))
    } catch (ex: Exception) {
        Cfg()
    }
}

fun saveCfg(c: Context, g: Cfg) {
    val j = JSONObject()
    val o = JSONObject()
    for ((id, v) in g.ov) {
        o.put(id.toString(), JSONObject().put("n", v.name).put("l", v.logo).put("e", v.epg).put("h", v.hidden).put("c", v.cat))
    }
    j.put("ov", o)
    j.put("order", JSONArray(g.order))
    j.put("cats", JSONArray(g.cats))
    j.put("epgs", JSONArray(g.epgs))
    j.put("lp", g.logoPrio)
    j.put("bs", g.bgSound)
    c.getSharedPreferences("cfg", 0).edit().putString("j", j.toString()).apply()
}

// Applique noms, ID EPG, catégories, masquage, ordre, et résout le logo :
// logo manuel > (selon la priorité choisie) logo de la liste ou logo du guide XMLTV.
fun applyCfg(src: List<Channel>, g: Cfg, icons: Map<String, String>): List<Channel> {
    val pos = HashMap<Int, Int>()
    g.order.forEachIndexed { i, id -> pos[id] = i }
    return src.mapIndexed { i, c ->
        val o = g.ov[c.id]
        val eid = (o?.epg ?: "").ifBlank { c.epgId }
        val gi = icons[eid] ?: ""
        val auto = if (g.logoPrio == "xmltv") gi.ifBlank { c.icon } else c.icon.ifBlank { gi }
        val c2 = c.copy(
            name = (o?.name ?: "").ifBlank { c.name },
            icon = (o?.logo ?: "").ifBlank { auto },
            epgId = eid,
            cat = (o?.cat ?: "").ifBlank { c.cat },
            hidden = o?.hidden ?: false
        )
        Pair(pos[c.id] ?: (g.order.size + i), c2)
    }.sortedBy { it.first }.map { it.second }
}

// Échange la chaîne avec sa voisine de la même catégorie (dir = -1 monter, +1 descendre).
fun moveCh(g: Cfg, all: List<Channel>, id: Int, dir: Int): Cfg {
    val i = all.indexOfFirst { it.id == id }
    if (i < 0) return g
    val cat = all[i].cat
    var j = i + dir
    while (j in all.indices && all[j].cat != cat) j += dir
    if (j !in all.indices) return g
    val ids = all.map { it.id }.toMutableList()
    val t = ids[i]
    ids[i] = ids[j]
    ids[j] = t
    return g.copy(order = ids)
}

private val TAGS = Regex("\\b(hd|fhd|uhd|4k|sd|hevc|h265|raw|vip|backup)\\b")
private val NONALNUM = Regex("[^\\p{L}\\p{N}]")

fun mkey(s: String): String = NONALNUM.replace(TAGS.replace(s.lowercase(), " "), "")

// Associe automatiquement les chaînes sans ID EPG valide au guide, par comparaison des noms.
fun autoMatch(all: List<Channel>, g: Cfg, guide: List<GuideCh>): Pair<Cfg, Int> {
    val ids = guide.map { it.id }.toHashSet()
    val idx = HashMap<String, String>()
    for (x in guide) {
        for (k in listOf(mkey(x.name), mkey(x.id))) {
            if (k.isNotEmpty() && !idx.containsKey(k)) idx[k] = x.id
        }
    }
    var n = 0
    var ov = g.ov
    for (c in all) {
        if (c.epgId.isNotBlank() && c.epgId in ids) continue
        val tail = c.name.substringAfterLast('|').substringAfterLast(':')
        val key = listOf(mkey(c.name), mkey(tail)).firstOrNull { it.isNotEmpty() && idx.containsKey(it) }
        val hit = if (key == null) null else idx[key]
        if (hit != null) {
            ov = ov + (c.id to (ov[c.id] ?: Ov()).copy(epg = hit))
            n++
        }
    }
    return Pair(g.copy(ov = ov), n)
}

// Historique : les 10 dernières chaînes regardées (la plus récente en premier).
fun loadHist(c: Context): List<Int> {
    val s = c.getSharedPreferences("hist", 0).getString("j", null) ?: return emptyList()
    return try {
        val a = JSONArray(s)
        (0 until a.length()).map { a.getInt(it) }
    } catch (ex: Exception) {
        emptyList()
    }
}

fun saveHist(c: Context, l: List<Int>) {
    c.getSharedPreferences("hist", 0).edit().putString("j", JSONArray(l).toString()).apply()
}

fun pushHist(h: List<Int>, id: Int): List<Int> = (listOf(id) + h.filter { it != id }).take(10)
