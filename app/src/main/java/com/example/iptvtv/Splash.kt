package com.example.iptvtv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Écran de démarrage : affiché une seule fois par lancement (le drapeau est remis à zéro quand on quitte l'app).
object SplashState {
    @Volatile var done = false
}

private val SP_BG = Color(0xFF06070B)
private val SP_GOLD = Color(0xFFD4AF6A)

@Composable
fun SplashScreen(durationMs: Int = 15000, onDone: () -> Unit) {
    val fade = remember { Animatable(1f) }
    val prog = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        prog.animateTo(1f, tween(durationMs, easing = LinearEasing))
        fade.animateTo(0f, tween(450))
        onDone()
    }
    val inf = rememberInfiniteTransition(label = "sp")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val glow by inf.animateFloat(0.75f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "glow")

    Box(Modifier.fillMaxSize().alpha(fade.value).background(SP_BG)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(520.dp, 330.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, 118.dp.toPx())
                    // Halo doré
                    drawCircle(
                        Brush.radialGradient(
                            listOf(SP_GOLD.copy(alpha = 0.16f * glow), Color.Transparent),
                            center = c, radius = 230.dp.toPx()
                        ),
                        radius = 230.dp.toPx(), center = c
                    )
                    // Anneau + arc qui tourne
                    val r = 107.dp.toPx()
                    drawCircle(SP_GOLD.copy(alpha = 0.28f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))
                    drawArc(
                        SP_GOLD, startAngle = spin - 90f, sweepAngle = 70f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Losange arrondi
                    val side = 62.dp.toPx()
                    rotate(45f, pivot = c) {
                        drawRoundRect(
                            SP_GOLD, topLeft = Offset(c.x - side / 2, c.y - side / 2), size = Size(side, side),
                            cornerRadius = CornerRadius(9.dp.toPx()), style = Stroke(3.dp.toPx())
                        )
                    }
                    // Triangle "play"
                    val tri = Path().apply {
                        moveTo(c.x - 9.dp.toPx(), c.y - 21.dp.toPx())
                        lineTo(c.x + 21.dp.toPx(), c.y)
                        lineTo(c.x - 9.dp.toPx(), c.y + 21.dp.toPx())
                        close()
                    }
                    drawPath(tri, SP_GOLD)
                }
                Text(
                    buildAnnotated(),
                    fontSize = 64.sp, letterSpacing = 14.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                )
            }
        }
        // Barre de chargement + texte
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(280.dp).height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxSize().let { it }) {
                    Box(
                        Modifier.fillMaxWidth(prog.value.coerceIn(0f, 1f)).height(3.dp).background(SP_GOLD)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Loading channels & guide…", color = Color(0xFF8E8A80), fontSize = 15.sp, letterSpacing = 2.sp)
        }
        Text(
            "v1.0", color = Color(0xFF5F5C55), fontSize = 12.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 70.dp, bottom = 50.dp)
        )
    }
}

@Composable
private fun buildAnnotated(): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFF5F2EA), fontWeight = FontWeight.Light))
        append("ELITE")
        pop()
        pushStyle(androidx.compose.ui.text.SpanStyle(color = SP_GOLD, fontWeight = FontWeight.Bold))
        append(" TV")
        pop()
    }
