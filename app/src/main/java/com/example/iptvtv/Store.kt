package com.example.iptvtv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Personnalisation d'une chaîne (vide = valeur d'origine)
data class Ov(
    val name: String = "", val logo: String = "", val epg: String = "",
    val hidden: Boolean = false, val cat: String = ""
)

data class Cfg(
    val ov: Map<Int, Ov> = emptyMap(),
    val order: List<Int> = emptyList(),
    val cats: List<String> = emptyList()
)

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
        Cfg(m, ord, cs)
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
    c.getSharedPreferences("cfg", 0).edit().putString("j", j.toString()).apply()
}

// Applique noms, logos, ID EPG, catégories, masquage et ordre personnalisés.
fun applyCfg(src: List<Channel>, g: Cfg): List<Channel> {
    val pos = HashMap<Int, Int>()
    g.order.forEachIndexed { i, id -> pos[id] = i }
    return src.mapIndexed { i, c ->
        val o = g.ov[c.id]
        val c2 = if (o == null) c else c.copy(
            name = o.name.ifBlank { c.name },
            icon = o.logo.ifBlank { c.icon },
            epgId = o.epg.ifBlank { c.epgId },
            cat = o.cat.ifBlank { c.cat },
            hidden = o.hidden
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
