package de.aze.iptv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Erscheinungsbild „Orbit“: Logo, Schriftzug, dünne Linien-Symbole und Startbildschirm.
 * Alles gezeichnet (keine Bilddateien) – passt sich dem Farbschema an.
 */

/** Logo: Kreis mit schräger Umlaufbahn, kleinem Satelliten und Abspielzeichen. */
@Composable
fun AzeLogo(size: Dp, ring: Color = Palette.Text, orbit: Color = Palette.Highlight, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 120f
        val c = Offset(60 * u, 60 * u)
        drawCircle(ring, radius = 40 * u, center = c, style = Stroke(width = 3f * u))
        rotate(-24f, pivot = c) {
            drawOval(orbit, topLeft = Offset(4 * u, 40 * u), size = Size(112 * u, 40 * u), style = Stroke(width = 2.6f * u))
        }
        drawCircle(orbit, radius = 4.5f * u, center = Offset(110 * u, 38 * u))
        val play = Path().apply {
            moveTo(52 * u, 44 * u); lineTo(78 * u, 60 * u); lineTo(52 * u, 76 * u); close()
        }
        drawPath(play, ring)
    }
}

/** Schriftzug „AZE“: dünn, weit gesperrt. */
@Composable
fun AzeWordmark(fontSize: TextUnit = 22.sp, color: Color = Palette.Text) {
    Text("AZE", color = color, fontSize = fontSize, fontWeight = FontWeight.Light, letterSpacing = (fontSize.value * 0.3f).sp)
}

/** Dünne Linien-Symbole (24er-Raster, Strichstärke 1,6). */
enum class LineIcon { SEARCH, TV, MOVIE, SERIES, FOOTBALL, PLAYLIST, SETTINGS, EXIT, EYE_OFF }

@Composable
fun LineIconView(kind: LineIcon, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawLineIcon(kind, color) }
}

private fun DrawScope.drawLineIcon(kind: LineIcon, color: Color) {
    val s = size.width / 24f
    val stroke = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, o(x1, y1), o(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
    fun poly(vararg pts: Float, closed: Boolean = false) {
        val p = Path()
        p.moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
        if (closed) p.close()
        drawPath(p, color, style = stroke)
    }
    fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        val p = Path().apply { addRoundRect(RoundRect(Rect(l * s, t * s, r * s, b * s), CornerRadius(rad * s))) }
        drawPath(p, color, style = stroke)
    }
    when (kind) {
        LineIcon.SEARCH -> {
            drawCircle(color, radius = 6.5f * s, center = o(11f, 11f), style = stroke)
            line(16f, 16f, 20.5f, 20.5f)
        }
        LineIcon.TV -> {
            rrect(3f, 5f, 21f, 17f, 2f)
            line(9f, 20.5f, 15f, 20.5f)
        }
        LineIcon.MOVIE -> {
            rrect(3.5f, 8f, 20.5f, 20f, 1.5f)
            poly(3.5f, 8f, 6f, 3.5f, 20.5f, 3.5f, 18f, 8f)
            line(9f, 3.5f, 7f, 8f)
            line(14f, 3.5f, 12f, 8f)
        }
        LineIcon.SERIES -> {
            rrect(6f, 7f, 20f, 20f, 1.5f)
            poly(4f, 16f, 4f, 5f, 16f, 5f)
            poly(11f, 11f, 15.5f, 13.5f, 11f, 16f, closed = true)
        }
        LineIcon.FOOTBALL -> {
            drawCircle(color, radius = 8.5f * s, center = o(12f, 12f), style = stroke)
            poly(12f, 7.5f, 16f, 10.4f, 14.5f, 15f, 9.5f, 15f, 8f, 10.4f, closed = true)
            line(12f, 3.5f, 12f, 7.5f); line(16f, 10.4f, 20f, 9f); line(14.5f, 15f, 17f, 18.5f)
            line(9.5f, 15f, 7f, 18.5f); line(8f, 10.4f, 4f, 9f)
        }
        LineIcon.PLAYLIST -> {
            line(4f, 6f, 15f, 6f); line(4f, 11f, 15f, 11f); line(4f, 16f, 11f, 16f)
            line(17f, 14f, 17f, 20f); line(14f, 17f, 20f, 17f)
        }
        LineIcon.SETTINGS -> {
            drawCircle(color, radius = 3f * s, center = o(12f, 12f), style = stroke)
            line(12f, 2.8f, 12f, 5.2f); line(12f, 18.8f, 12f, 21.2f); line(2.8f, 12f, 5.2f, 12f); line(18.8f, 12f, 21.2f, 12f)
            line(5.5f, 5.5f, 7.2f, 7.2f); line(16.8f, 16.8f, 18.5f, 18.5f); line(5.5f, 18.5f, 7.2f, 16.8f); line(16.8f, 7.2f, 18.5f, 5.5f)
        }
        LineIcon.EXIT -> {
            poly(13f, 4f, 5f, 4f, 5f, 20f, 13f, 20f)
            line(10f, 12f, 20.5f, 12f)
            poly(17.5f, 9f, 20.5f, 12f, 17.5f, 15f)
        }
        LineIcon.EYE_OFF -> {
            poly(2.5f, 12f, 6f, 7.5f, 12f, 6f, 18f, 7.5f, 21.5f, 12f, 18f, 16.5f, 12f, 18f, 6f, 16.5f, closed = true)
            drawCircle(color, radius = 2.8f * s, center = o(12f, 12f), style = stroke)
            line(4f, 4f, 20f, 20f)
        }
    }
}

/**
 * Startbildschirm, solange die Senderliste geladen wird: Logo, Schriftzug, dünner laufender Balken.
 */
@Composable
fun SplashScreen(status: String, version: String) {
    val t = rememberInfiniteTransition(label = "laden")
    val x by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "balken",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ground)
            .background(
                Brush.radialGradient(
                    listOf(lerp(Palette.Ground, Palette.Panel, 0.9f), Palette.Ground),
                    radius = 900f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AzeLogo(110.dp)
            AzeWordmark(34.sp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 26.dp),
            ) {
                Box(Modifier.width(200.dp).height(3.dp).background(Palette.Line, RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier
                            .offset(x = (140 * x).dp)
                            .width(60.dp)
                            .height(3.dp)
                            .background(Palette.Highlight, RoundedCornerShape(2.dp)),
                    )
                }
                Text(status, color = Palette.TextMuted, fontSize = 13.sp)
            }
        }
        Text(
            "Version $version", color = Palette.TextMuted.copy(alpha = 0.7f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        )
    }
}

/** Kleine Kopfzeile in Listen („GRUPPEN“): klein, gesperrt, gedämpft. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = Palette.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp, modifier = modifier,
    )
}
