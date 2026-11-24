package com.example.iptvtv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val LocalThemed = androidx.compose.runtime.compositionLocalOf { true }

// Thèmes : 0 = sombre (par défaut), 1 = clair, 2 = noir (fond noir pur).
// Tous les écrans lisent ces couleurs ; quand le thème change, ils se redessinent tout seuls.
object Th {
    var mode by mutableStateOf(0)

    val light: Boolean get() = mode == 1
    val black: Boolean get() = mode == 2

    fun load(ctx: Context) {
        mode = when (ctx.getSharedPreferences("iptv_player", 0).getString("theme", "dark")) {
            "black" -> 2
            else -> 0
        }
    }

    fun set(ctx: Context, m: Int) {
        mode = m
        ctx.getSharedPreferences("iptv_player", 0).edit()
            .putString("theme", when (m) { 1 -> "light"; 2 -> "black"; else -> "dark" }).apply()
    }

    private fun pick(dark: Long, light: Long, black: Long): Color =
        Color(when (mode) { 1 -> light; 2 -> black; else -> dark })

    val text: Color get() = pick(0xFFFFFFFF, 0xFF14213D, 0xFFFFFFFF)
    val sub: Color get() = pick(0xFFB0B8C8, 0xFF55627A, 0xFFA9ADB6)
    val accent: Color get() = pick(0xFF1EB6E8, 0xFF0B6FA8, 0xFF2CC4F0)
    val accentSoft: Color get() = pick(0xFF9CC2F5, 0xFF2A6FB0, 0xFF8FCBF7)
    val focusBg: Color get() = pick(0xFFFFFFFF, 0xFF14213D, 0xFFFFFFFF)
    val focusText: Color get() = pick(0xFF14213D, 0xFFFFFFFF, 0xFF000000)
    val focusSub: Color get() = pick(0xFF45557A, 0xFFC9D3E6, 0xFF444444)
    val tile: Color get() = pick(0xE62A313E, 0xFFDDE3EE, 0xFF161616)
    val tileCur: Color get() = pick(0xFF1F5FA6, 0xFFBBD6F5, 0xFF16406B)
    val panel: Color get() = pick(0xF5171717, 0xFFF1F4F9, 0xFF000000)
    val panel2: Color get() = pick(0xF5191919, 0xFFE3E8F1, 0xFF0A0A0A)
    val panel3: Color get() = pick(0xF5151515, 0xFFE9EDF4, 0xFF050505)
    val scrim: Color get() = pick(0x80000000, 0x66FFFFFF, 0xB3000000)
    val header: Color get() = pick(0xB3000000, 0xCCFFFFFF, 0xFF000000)
    val headerBox: Color get() = pick(0xCC0C0C0C, 0xFFD3DBE8, 0xFF141414)
    val bgBottom: Color get() = pick(0xFF0E1117, 0xFFF1F4F9, 0xFF000000)
    val bgMid: Color get() = pick(0xEB0E1117, 0xEBF1F4F9, 0xF2000000)
    val gradTop: Color get() = pick(0x99000000, 0x66FFFFFF, 0x99000000)
    val gradMid: Color get() = pick(0x55000000, 0x33FFFFFF, 0x66000000)
    val barTrack: Color get() = pick(0x33FFFFFF, 0x33000000, 0x33FFFFFF)
    val barFill: Color get() = Color(0xFFE0B85A)
    val sel: Color get() = Color(0xFFE0B85A)
    val cardBg: Color get() = pick(0xFF1B2230, 0xFFDDE3EE, 0xFF121212)
    val iconBg: Color get() = pick(0xFF1F2733, 0xFFDDE3EE, 0xFF1A1A1A)
    val desc: Color get() = pick(0xFFDDE2EA, 0xFF2B3750, 0xFFDDDDDD)
    val time: Color get() = pick(0xFF8FCBF7, 0xFF0B6FA8, 0xFF8FCBF7)
    val gold: Color get() = pick(0xFFFFE9A8, 0xFF8A6100, 0xFFFFE9A8)
    val goldSoft: Color get() = pick(0xFFEFE6C8, 0xFF9A7A20, 0xFFEFE6C8)
    val popup: Color get() = pick(0xF2151A24, 0xFFF1F4F9, 0xFF0A0A0A)
    val cell: Color get() = pick(0xFF262626, 0xFFE3E8F1, 0xFF141414)
    val cellLive: Color get() = pick(0xFF2F3B48, 0xFFC3D6EA, 0xFF1B2A3A)
    val chip: Color get() = pick(0xFF232A36, 0xFFDDE3EE, 0xFF161616)
    val guideBg: Color get() = pick(0xE0141414, 0xF2F1F4F9, 0xE6000000)
    val logoA: Color get() = pick(0xFF4B5568, 0xFFD3DBE8, 0xFF1C1C1C)
    val logoB: Color get() = pick(0xFF333B49, 0xFFBFC9DA, 0xFF101010)
    val divider: Color get() = pick(0xFF2A3140, 0xFFC3CCDA, 0xFF2A2A2A)
    val ovMid: Color get() = pick(0xE6000000, 0xE6F1F4F9, 0xE6000000)
    val ovEnd: Color get() = pick(0xF5000000, 0xF5F1F4F9, 0xFF000000)
    val ovTop: Color get() = pick(0xEE000000, 0xEEF1F4F9, 0xEE000000)
    val ovCtl: Color get() = pick(0x66FFFFFF, 0x2214213D, 0x66FFFFFF)
    val mvCell: Color get() = pick(0xFF11151C, 0xFFE3E8F1, 0xFF0A0A0A)
    val lime: Color get() = pick(0xFFB5D334, 0xFF5E7A00, 0xFFB5D334)
    val nowLine: Color get() = pick(0xFF8FCBF7, 0xFF0B6FA8, 0xFF8FCBF7)

    // Adapte une couleur de texte "sombre par défaut" au thème clair (utilisé par fcolor).
    fun mapNormal(c: Color, lt: Boolean = light): Color {
        if (!lt) return c
        return when (c) {
            Color.White, Color(0xDDFFFFFF) -> text
            Color(0xFFB0B8C8) -> sub
            Color(0xFF8FCBF7) -> time
            Color(0xFF1EB6E8) -> accent
            else -> c
        }
    }

    fun mapFocused(c: Color, lt: Boolean = light): Color {
        if (!lt) return c
        return when (c) {
            Color(0xFF14213D) -> Color.White
            Color(0xFF45557A) -> Color(0xFFC9D3E6)
            else -> c
        }
    }
}
