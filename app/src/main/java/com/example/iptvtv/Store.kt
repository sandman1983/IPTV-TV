package com.example.iptvtv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Une source de chaînes : type "xtream" ou "m3u" (lien ou fichier local)
data class Source(
    val id: String, val name: String, val type: String,
    val host: String = "", val user: String = "", val pass: String = "",
    val url: String = "", val epg: String = "",
    val enabled: Boolean = true, val insecure: Boolean = false, val userAgent: String = "",
    val output: String = "m3u8", val useAuth: Boolean = false
)

private fun srcToJson(s: Source): JSONObject =
    JSONObject().put("i", s.id).put("n", s.name).put("t", s.type).put("h", s.host)
        .put("u", s.user).put("p", s.pass).put("l", s.url).put("e", s.epg)
        .put("en", s.enabled).put("in", s.insecure).put("ua", s.userAgent)
        .put("o", s.output).put("au", s.useAuth)

private fun srcFromJson(x: JSONObject): Source = Source(
    x.optString("i"), x.optString("n"), x.optString("t"), x.optString("h"),
    x.optString("u"), x.optString("p"), x.optString("l"), x.optString("e"),
    x.optBoolean("en", true), x.optBoolean("in", false), x.optString("ua", ""),
    x.optString("o", "m3u8").ifBlank { "m3u8" }, x.optBoolean("au", false)
)

// Personnalisation d'une chaîne (vide = valeur d'origine)
data class Ov(
    val name: String = "", val logo: String = "", val epg: String = "",
    val hidden: Boolean = false, val cat: String = "", val inv: Boolean = false
)

data class Cfg(
    val ov: Map<Int, Ov> = emptyMap(),
    val order: List<Int> = emptyList(),
    val cats: List<String> = emptyList(),
    val epgs: List<String> = emptyList(),
    val logoPrio: String = "playlist",
    val bgSound: Boolean = true,
    val city: String = "",
    val syncHours: Int = 24,
    val showNums: Boolean = true,
    val dvrUri: String = "",
    val recStart: Int = 0,
    val recStop: Int = 0,
    val remOn: Boolean = true,
    val merge: Boolean = false,
    val updateOnStart: Boolean = false,
    val epgMine: Boolean = false
)

fun loadSources(c: Context): List<Source> {
    val s = c.getSharedPreferences("src", 0).getString("j", null)
    if (s != null) {
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                srcFromJson(a.getJSONObject(it))
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
        a.put(srcToJson(s))
    }
    c.getSharedPreferences("src", 0).edit().putString("j", a.toString()).apply()
}

fun cfgToJson(g: Cfg): JSONObject {
    val j = JSONObject()
    val o = JSONObject()
    for ((id, v) in g.ov) {
        o.put(id.toString(), JSONObject().put("n", v.name).put("l", v.logo).put("e", v.epg).put("h", v.hidden).put("c", v.cat).put("iv", v.inv))
    }
    j.put("ov", o)
    j.put("order", JSONArray(g.order))
    j.put("cats", JSONArray(g.cats))
    j.put("epgs", JSONArray(g.epgs))
    j.put("lp", g.logoPrio)
    j.put("bs", g.bgSound)
    j.put("ct", g.city)
    j.put("sh", g.syncHours)
    j.put("sn", g.showNums)
    j.put("du", g.dvrUri)
    j.put("rb", g.recStart)
    j.put("ra", g.recStop)
    j.put("rm", g.remOn)
    j.put("mg", g.merge)
    j.put("us", g.updateOnStart)
    j.put("em", g.epgMine)
    return j
}

fun cfgFromJson(j: JSONObject): Cfg {
    val o = j.getJSONObject("ov")
    val m = HashMap<Int, Ov>()
    for (key in o.keys()) {
        val x = o.getJSONObject(key)
        m[key.toInt()] = Ov(x.optString("n"), x.optString("l"), x.optString("e"), x.optBoolean("h"), x.optString("c"), x.optBoolean("iv"))
    }
    val od = j.getJSONArray("order")
    val ord = (0 until od.length()).map { od.getInt(it) }
    val ca = j.getJSONArray("cats")
    val cs = (0 until ca.length()).map { ca.getString(it) }
    val ea = j.optJSONArray("epgs")
    val es = if (ea == null) emptyList() else (0 until ea.length()).map { ea.getString(it) }
    return Cfg(
        m, ord, cs, es, j.optString("lp", "playlist").ifBlank { "playlist" },
        j.optBoolean("bs", true), j.optString("ct", ""), j.optInt("sh", 24), j.optBoolean("sn", true),
        j.optString("du", ""), j.optInt("rb", 0), j.optInt("ra", 0), j.optBoolean("rm", true), j.optBoolean("mg", false),
        j.optBoolean("us", false), j.optBoolean("em", false)
    )
}

fun loadCfg(c: Context): Cfg {
    val s = c.getSharedPreferences("cfg", 0).getString("j", null) ?: return Cfg()
    return try {
        cfgFromJson(JSONObject(s))
    } catch (ex: Exception) {
        Cfg()
    }
}

fun saveCfg(c: Context, g: Cfg) {
    c.getSharedPreferences("cfg", 0).edit().putString("j", cfgToJson(g).toString()).apply()
}

// Export / restauration de la configuration (sources + réglages) au format JSON.
fun exportConfig(sources: List<Source>, cfg: Cfg): String {
    val a = JSONArray()
    for (s in sources) {
        a.put(srcToJson(s))
    }
    return JSONObject().put("app", "iptv-tv").put("sources", a).put("cfg", cfgToJson(cfg)).toString(2)
}

fun importConfig(text: String): Pair<List<Source>, Cfg>? {
    return try {
        val j = JSONObject(text)
        if (j.optString("app") != "iptv-tv") return null
        val a = j.getJSONArray("sources")
        val list = (0 until a.length()).map {
            srcFromJson(a.getJSONObject(it))
        }
        Pair(list, cfgFromJson(j.getJSONObject("cfg")))
    } catch (ex: Exception) {
        null
    }
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
            hidden = o?.hidden ?: false,
            invert = o?.inv ?: false
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


// Déplace des catégories entières (blocs de chaînes). Seules les catégories visibles comptent :
// les catégories masquées gardent leur place.
private fun rebuildOrder(g: Cfg, all: List<Channel>, names: List<String>, newVis: List<String>): Cfg {
    val vis = newVis.iterator()
    val final = names.map { n -> if (all.any { it.cat == n && !it.hidden }) vis.next() else n }
    return g.copy(order = final.flatMap { n -> all.filter { it.cat == n }.map { it.id } })
}

fun moveCats(g: Cfg, all: List<Channel>, cats: Set<String>, dir: Int): Cfg {
    val names = all.map { it.cat }.distinct()
    val v = names.filter { n -> all.any { it.cat == n && !it.hidden } }.toMutableList()
    val seq = if (dir < 0) v.indices else v.indices.reversed()
    for (i in seq) {
        if (v[i] !in cats) continue
        val j = i + dir
        if (j !in v.indices || v[j] in cats) continue
        val t = v[i]; v[i] = v[j]; v[j] = t
    }
    return rebuildOrder(g, all, names, v)
}

fun moveCatsEdge(g: Cfg, all: List<Channel>, cats: Set<String>, top: Boolean): Cfg {
    val names = all.map { it.cat }.distinct()
    val v = names.filter { n -> all.any { it.cat == n && !it.hidden } }
    val moved = v.filter { it in cats }
    val rest = v.filter { it !in cats }
    return rebuildOrder(g, all, names, if (top) moved + rest else rest + moved)
}
