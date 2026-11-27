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

    // Zones sombres : Accueil, mini lecteur et boîtes popup restent sombres même en thème clair.
    private var zones by mutableStateOf(0)
    fun enterDark() { zones++ }
    fun leaveDark() { if (zones > 0) zones-- }
    private val eff: Int get() = if (mode == 1 && zones > 0) 0 else mode

    val light: Boolean get() = eff == 1
    val black: Boolean get() = mode == 2

    fun load(ctx: Context) {
        mode = when (ctx.getSharedPreferences("iptv_player", 0).getString("theme", "dark")) {
            "black" -> 2
            "slate" -> 3
            "sky" -> 4
            else -> 0
        }
    }

    fun set(ctx: Context, m: Int) {
        mode = m
        ctx.getSharedPreferences("iptv_player", 0).edit()
            .putString("theme", when (m) { 1 -> "light"; 2 -> "black"; 3 -> "slate"; 4 -> "sky"; else -> "dark" }).apply()
    }

    private fun pick(dark: Long, light: Long, black: Long, slate: Long = dark, sky: Long = slate): Color =
        Color(when (eff) { 1 -> light; 2 -> black; 3 -> slate; 4 -> sky; else -> dark })

    val text: Color get() = pick(0xFFFFFFFF, 0xFF14213D, 0xFFFFFFFF, 0xFFE6EAF0, 0xFFFFFFFF)
    val sub: Color get() = pick(0xFFB0B8C8, 0xFF55627A, 0xFFA9ADB6, 0xFF9AA6B8, 0xFF9BA7BD)
    val accent: Color get() = pick(0xFF1EB6E8, 0xFF0B6FA8, 0xFF2CC4F0, 0xFF1EB6E8, 0xFF3DA9FC)
    val accentSoft: Color get() = pick(0xFF9CC2F5, 0xFF2A6FB0, 0xFF8FCBF7)
    val focusBg: Color get() = pick(0xFFFFFFFF, 0xFF14213D, 0xFFFFFFFF)
    val focusText: Color get() = pick(0xFF14213D, 0xFFFFFFFF, 0xFF000000)
    val focusSub: Color get() = pick(0xFF45557A, 0xFFC9D3E6, 0xFF444444)
    val tile: Color get() = pick(0xE62A313E, 0xFFDDE3EE, 0xFF161616, 0xE61B2330, 0xE6101827)
    val tileCur: Color get() = pick(0xFF1F5FA6, 0xFFBBD6F5, 0xFF16406B)
    val panel: Color get() = pick(0xF5171717, 0xFFF1F4F9, 0xF0000000, 0xF5121820, 0xF50A0F1C)
    val panel2: Color get() = pick(0xF5191919, 0xFFE3E8F1, 0xF00A0A0A, 0xF5141B24, 0xF50C1220)
    val panel3: Color get() = pick(0xF5151515, 0xFFE9EDF4, 0xF0050505, 0xF50F151C, 0xF5080C16)
    val scrim: Color get() = pick(0x80000000, 0x66FFFFFF, 0xB3000000)
    val header: Color get() = pick(0xB3000000, 0xCCFFFFFF, 0xFF000000, 0xB30B1016, 0xB3030509)
    val headerBox: Color get() = pick(0xCC0C0C0C, 0xFFD3DBE8, 0xFF141414, 0xCC111822, 0xCC0B111D)
    val bgBottom: Color get() = pick(0xFF0E1117, 0xFFF1F4F9, 0xFF000000, 0xFF0B1118, 0xFF05070D)
    val bgMid: Color get() = pick(0xEB0E1117, 0xEBF1F4F9, 0xF2000000, 0xEB0B1118, 0xEB05070D)
    val gradTop: Color get() = pick(0x99000000, 0x66FFFFFF, 0x99000000)
    val gradMid: Color get() = pick(0x55000000, 0x33FFFFFF, 0x66000000)
    val barTrack: Color get() = pick(0x33FFFFFF, 0x33000000, 0x33FFFFFF)
    val barFill: Color get() = pick(0xFF3DDC97, 0xFFE0B85A, 0xFFE0B85A, 0xFF9B8CFF, 0xFF3DA9FC)
    val selTint: Color get() = pick(0x333DDC97, 0x33E0B85A, 0x33E0B85A, 0x339B8CFF, 0x333DA9FC)
    val selLine: Color get() = pick(0x553DDC97, 0x55E0B85A, 0x55E0B85A, 0x559B8CFF, 0x553DA9FC)
    val sel: Color get() = pick(0xFF3DDC97, 0xFFE0B85A, 0xFFE0B85A, 0xFF9B8CFF, 0xFF3DA9FC)
    val cardBg: Color get() = pick(0xFF1B2230, 0xFFDDE3EE, 0xFF121212, 0xFF17202B, 0xFF0E1522)
    val iconBg: Color get() = pick(0xFF1F2733, 0xFFDDE3EE, 0xFF1A1A1A, 0xFF1B2531, 0xFF131C2C)
    val desc: Color get() = pick(0xFFDDE2EA, 0xFF2B3750, 0xFFDDDDDD, 0xFFD3DAE5, 0xFFDCE6F5)
    val time: Color get() = pick(0xFF8FCBF7, 0xFF0B6FA8, 0xFF8FCBF7, 0xFF8FCBF7, 0xFF7CC4FF)
    val gold: Color get() = pick(0xFFB8F2D6, 0xFF8A6100, 0xFFFFE9A8, 0xFFD3CCFF, 0xFFBFE0FF)
    val goldSoft: Color get() = pick(0xFFA5E6C8, 0xFF9A7A20, 0xFFEFE6C8, 0xFFC0B8F2, 0xFFA9C8E6)
    val popup: Color get() = pick(0xF2151A24, 0xFFF1F4F9, 0xF00A0A0A, 0xF2111822, 0xF20A0F1C)
    val cell: Color get() = pick(0xFF262626, 0xFFE3E8F1, 0xFF141414, 0xFF1B2430, 0xFF131C2C)
    val cellLive: Color get() = pick(0xFF2F3B48, 0xFFC3D6EA, 0xFF1B2A3A, 0xFF263647, 0xFF1B2D4A)
    val chip: Color get() = pick(0xFF232A36, 0xFFDDE3EE, 0xFF161616, 0xFF1B2430, 0xFF131C2C)
    val guideBg: Color get() = pick(0xE0141414, 0xF2F1F4F9, 0xE6000000, 0xE0111821, 0xE0070B14)
    val logoA: Color get() = pick(0xFF4B5568, 0xFFD3DBE8, 0xFF1C1C1C, 0xFF3D4A5E, 0xFF33425C)
    val logoB: Color get() = pick(0xFF333B49, 0xFFBFC9DA, 0xFF101010, 0xFF2A3546, 0xFF222D40)
    val divider: Color get() = pick(0xFF2A3140, 0xFFC3CCDA, 0xFF2A2A2A, 0xFF263040, 0xFF1C2638)
    val ovMid: Color get() = pick(0xE6000000, 0xE6F1F4F9, 0xE6000000)
    val ovEnd: Color get() = pick(0xF5000000, 0xF5F1F4F9, 0xFF000000)
    val ovTop: Color get() = pick(0xEE000000, 0xEEF1F4F9, 0xEE000000)
    val ovCtl: Color get() = pick(0x66FFFFFF, 0x2214213D, 0x66FFFFFF)
    val mvCell: Color get() = pick(0xFF11151C, 0xFFE3E8F1, 0xFF0A0A0A, 0xFF0E141C, 0xFF0A101B)
    val lime: Color get() = pick(0xFFB5D334, 0xFF5E7A00, 0xFFB5D334)
    val nowLine: Color get() = pick(0xFF3DDC97, 0xFF0B6FA8, 0xFF8FCBF7, 0xFF9B8CFF, 0xFF7CC4FF)

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

// À appeler au début d'un écran ou d'une boîte qui doit rester sombre en thème clair.
@androidx.compose.runtime.Composable
fun DarkZone() {
    androidx.compose.runtime.remember { Th.enterDark(); true }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Th.leaveDark() } }
}
