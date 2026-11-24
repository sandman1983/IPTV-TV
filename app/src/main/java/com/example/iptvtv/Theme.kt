package com.example.iptvtv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Thème de l'app : sombre (par défaut) ou clair. Les écrans lisent ces couleurs ;
// quand le thème change, ils se redessinent tout seuls.
// Vrai seulement dans les écrans qui suivent le thème (accueil, réglages, liste des chaînes, enregistrements).
// Les écrans sur la vidéo (lecteur, mini-lecteur, multiview, guide) restent sombres.
val LocalThemed = androidx.compose.runtime.compositionLocalOf { false }

object Th {
    var light by mutableStateOf(false)

    fun load(ctx: Context) {
        light = ctx.getSharedPreferences("iptv_player", 0).getString("theme", "dark") == "light"
    }

    fun set(ctx: Context, v: Boolean) {
        light = v
        ctx.getSharedPreferences("iptv_player", 0).edit().putString("theme", if (v) "light" else "dark").apply()
    }

    val text: Color get() = if (light) Color(0xFF14213D) else Color.White
    val sub: Color get() = if (light) Color(0xFF55627A) else Color(0xFFB0B8C8)
    val accent: Color get() = if (light) Color(0xFF0B6FA8) else Color(0xFF1EB6E8)
    val accentSoft: Color get() = if (light) Color(0xFF2A6FB0) else Color(0xFF9CC2F5)
    val focusBg: Color get() = if (light) Color(0xFF14213D) else Color.White
    val focusText: Color get() = if (light) Color.White else Color(0xFF14213D)
    val focusSub: Color get() = if (light) Color(0xFFC9D3E6) else Color(0xFF45557A)
    val tile: Color get() = if (light) Color(0xFFDDE3EE) else Color(0xE62A313E)
    val tileCur: Color get() = if (light) Color(0xFFBBD6F5) else Color(0xFF1F5FA6)
    val panel: Color get() = if (light) Color(0xFFF1F4F9) else Color(0xF5171717)
    val panel2: Color get() = if (light) Color(0xFFE3E8F1) else Color(0xF5191919)
    val panel3: Color get() = if (light) Color(0xFFE9EDF4) else Color(0xF5151515)
    val scrim: Color get() = if (light) Color(0x66FFFFFF) else Color(0x80000000)
    val header: Color get() = if (light) Color(0xCCFFFFFF) else Color(0xB3000000)
    val headerBox: Color get() = if (light) Color(0xFFD3DBE8) else Color(0xCC0C0C0C)
    val bgBottom: Color get() = if (light) Color(0xFFF1F4F9) else Color(0xFF0E1117)
    val bgMid: Color get() = if (light) Color(0xEBF1F4F9) else Color(0xEB0E1117)
    val gradTop: Color get() = if (light) Color(0x66FFFFFF) else Color(0x99000000)
    val gradMid: Color get() = if (light) Color(0x33FFFFFF) else Color(0x55000000)
    val barTrack: Color get() = if (light) Color(0xFFC9D3E6) else Color(0xFF070C1A)
    val barFill: Color get() = if (light) Color(0xFF2D9BE0) else Color(0xFF8FCBF7)
    val cardBg: Color get() = if (light) Color(0xFFDDE3EE) else Color(0xFF1B2230)
    val iconBg: Color get() = if (light) Color(0xFFDDE3EE) else Color(0xFF1F2733)
    val desc: Color get() = if (light) Color(0xFF2B3750) else Color(0xFFDDE2EA)
    val time: Color get() = if (light) Color(0xFF0B6FA8) else Color(0xFF8FCBF7)

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
