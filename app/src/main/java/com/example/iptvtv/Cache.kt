package com.example.iptvtv

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Disk cache for channels and the guide, so the app opens instantly with the last data.
object GuideCache {
    private const val VER = 2

    private fun f(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun saveChannels(ctx: Context, key: String, list: List<Channel>) {
        try {
            DataOutputStream(GZIPOutputStream(f(ctx, "ch_cache.gz").outputStream().buffered())).use { o ->
                o.writeInt(VER); o.writeUTF(key); o.writeLong(System.currentTimeMillis())
                o.writeInt(list.size)
                for (c in list) {
                    o.writeInt(c.id); o.writeUTF(c.name); o.writeUTF(c.icon); o.writeUTF(c.epgId)
                    o.writeUTF(c.cat); o.writeBoolean(c.hidden); o.writeUTF(c.url); o.writeUTF(c.src)
                }
            }
        } catch (ex: Exception) {
            f(ctx, "ch_cache.gz").delete()
        }
    }

    // Returns channels and the time they were saved, or null.
    fun loadChannels(ctx: Context, key: String): Pair<List<Channel>, Long>? {
        val file = f(ctx, "ch_cache.gz")
        if (!file.exists()) return null
        return try {
            DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { i ->
                if (i.readInt() != VER || i.readUTF() != key) return null
                val at = i.readLong()
                val n = i.readInt()
                val out = ArrayList<Channel>(n)
                repeat(n) {
                    out.add(
                        Channel(
                            i.readInt(), i.readUTF(), i.readUTF(), i.readUTF(), i.readUTF(),
                            i.readBoolean(), i.readUTF(), i.readUTF()
                        )
                    )
                }
                Pair(out, at)
            }
        } catch (ex: Exception) {
            null
        }
    }

    fun saveEpg(ctx: Context, key: String, e: Epg) {
        try {
            DataOutputStream(GZIPOutputStream(f(ctx, "epg_cache.gz").outputStream().buffered())).use { o ->
                o.writeInt(VER); o.writeUTF(key); o.writeLong(System.currentTimeMillis())
                o.writeInt(e.chans.size)
                for (c in e.chans) { o.writeUTF(c.id); o.writeUTF(c.name); o.writeUTF(c.icon) }
                o.writeInt(e.progs.size)
                for ((id, l) in e.progs) {
                    o.writeUTF(id); o.writeInt(l.size)
                    for (p in l) {
                        o.writeUTF(p.title); o.writeUTF(p.sub); o.writeUTF(p.cat); o.writeUTF(p.icon)
                        o.writeLong(p.start); o.writeLong(p.stop); o.writeUTF(p.desc)
                    }
                }
            }
        } catch (ex: Exception) {
            f(ctx, "epg_cache.gz").delete()
        }
    }

    fun loadEpg(ctx: Context, key: String): Pair<Epg, Long>? {
        val file = f(ctx, "epg_cache.gz")
        if (!file.exists()) return null
        return try {
            DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { i ->
                if (i.readInt() != VER || i.readUTF() != key) return null
                val at = i.readLong()
                val chans = ArrayList<GuideCh>()
                repeat(i.readInt()) { chans.add(GuideCh(i.readUTF(), i.readUTF(), i.readUTF())) }
                val m = HashMap<String, List<Prog>>()
                repeat(i.readInt()) {
                    val id = i.readUTF()
                    val l = ArrayList<Prog>()
                    repeat(i.readInt()) {
                        l.add(Prog(i.readUTF(), i.readUTF(), i.readUTF(), i.readUTF(), i.readLong(), i.readLong(), i.readUTF()))
                    }
                    m[id] = l
                }
                Pair(Epg(m, chans), at)
            }
        } catch (ex: Exception) {
            null
        }
    }
}

// Saves the last crash so the app can show it at the next start.
object CrashLog {
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                File(app.filesDir, "last_crash.txt").writeText(sw.toString().take(6000))
            } catch (ex: Exception) { }
            old?.uncaughtException(t, e)
        }
    }

    fun take(ctx: Context): String? {
        val f = File(ctx.filesDir, "last_crash.txt")
        if (!f.exists()) return null
        val s = try { f.readText() } catch (ex: Exception) { "" }
        f.delete()
        return s.ifBlank { null }
    }
}
