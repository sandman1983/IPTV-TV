package com.example.iptvtv

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// Réglages du lecteur (page Settings > Player). Stockés dans les préférences "iptv_player".
object PlayerPrefs {
    fun sp(c: Context) = c.getSharedPreferences("iptv_player", Context.MODE_PRIVATE)
    fun str(c: Context, k: String, d: String): String = sp(c).getString(k, d) ?: d
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)

    // Taille du tampon en secondes (5, 10, 20, 30, 60)
    fun bufferSec(c: Context): Int = (str(c, "buf", "20").toIntOrNull() ?: 20).coerceIn(5, 60)

    // Décodeurs "software" en premier si demandé (appliqué au démarrage de l'app).
    @OptIn(UnstableApi::class)
    fun applyCodecs(c: Context, f: DefaultRenderersFactory) {
        val vSoft = str(c, "vdec", "hw") == "sw"
        val aSoft = str(c, "adec", "hw") == "sw"
        if (!vSoft && !aSoft) return
        try {
            val sel = MediaCodecSelector { mime, secure, tunneling ->
                val l = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
                val soft = (mime.startsWith("video/") && vSoft) || (mime.startsWith("audio/") && aSoft)
                if (soft) l.sortedByDescending { it.softwareOnly } else l
            }
            val fld = DefaultRenderersFactory::class.java.getDeclaredField("mediaCodecSelector")
            fld.isAccessible = true
            fld.set(f, sel)
        } catch (t: Throwable) {
            // si cette version du lecteur ne le permet pas, on garde le décodage matériel
        }
    }

    private var origFactory: SSLSocketFactory? = null
    private var origVerifier: HostnameVerifier? = null
    private var origSaved = false
    @Volatile var srcInsecure = false

    // "Allow insecure streams" : ne vérifie plus les certificats SSL (serveurs IPTV mal configurés).
    fun applyInsecure(on: Boolean) {
        try {
            if (!origSaved) {
                origFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
                origVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
                origSaved = true
            }
            if (on) {
                val tm = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, tm, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
                HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            } else {
                origFactory?.let { HttpsURLConnection.setDefaultSSLSocketFactory(it) }
                origVerifier?.let { HttpsURLConnection.setDefaultHostnameVerifier(it) }
            }
        } catch (t: Throwable) { }
    }
}

// Applique tout ce qui peut l'être en direct (au démarrage et à chaque retour des réglages).
@OptIn(UnstableApi::class)
fun applyPlayerPrefs(ctx: Context, exo: ExoPlayer) {
    try {
        exo.setVideoChangeFrameRateStrategy(
            if (PlayerPrefs.bool(ctx, "afr", true)) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        )
    } catch (t: Throwable) { }
    try {
        val tl = PlayerPrefs.str(ctx, "tlang", "")
        val al = PlayerPrefs.str(ctx, "alang", "")
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(if (tl.isEmpty()) null else tl)
            .setPreferredAudioLanguage(if (al.isEmpty()) null else al)
            .build()
    } catch (t: Throwable) { }
    PlayerPrefs.applyInsecure(PlayerPrefs.bool(ctx, "insecure", false) || PlayerPrefs.srcInsecure)
}

// Ligne de réglage qui change de valeur à chaque OK (comme les autres réglages de l'app).
@Composable
fun PrefCycle(
    title: String, key: String, options: List<Pair<String, String>>, def: String,
    note: String = "", autoFocus: Boolean = false
) {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf(PlayerPrefs.str(ctx, key, def)) }
    val label = options.firstOrNull { it.first == v }?.second ?: v
    PItem(
        title, label + note,
        {
            val i = options.indexOfFirst { it.first == v }
            v = options[(i + 1) % options.size].first
            PlayerPrefs.sp(ctx).edit().putString(key, v).apply()
        },
        autoFocus = autoFocus
    )
}

@Composable
fun PrefSwitch(title: String, sub: String, key: String, def: Boolean) {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf(PlayerPrefs.bool(ctx, key, def)) }
    PSwitch(title, sub, v, {
        v = !v
        PlayerPrefs.sp(ctx).edit().putBoolean(key, v).apply()
    })
}
