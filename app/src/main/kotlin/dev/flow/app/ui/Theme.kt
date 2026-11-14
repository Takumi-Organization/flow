package dev.flow.app.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flow.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sistema de color de FLOW.
 *
 * Neutros con matiz (negro-violeta profundo / papel lavanda), nunca grises puros. Tres colores de datos en triada
 * dividida y a luminosidad pareja: disparador = coral (la chispa), condición = violeta (la decisión), acción = menta
 * (el resultado). El acento de la interfaz es elegible; los tres de datos no cambian, para que un flujo se lea igual
 * con cualquier acento. Cada flujo toma además un tono propio de [FlowTones].
 */
@Immutable
class Kami(
    val dark: Boolean, val amoled: Boolean,
    val paper: Color, val raised: Color, val high: Color, val ink: Color, val dim: Color, val muted: Color,
    val faint: Color, val line: Color, val secondary: Color, val gold: Color, val ok: Color, val warn: Color, val error: Color,
    val accent: Color, val signal: Color, val onAccent: Color,
    val trig: Color, val cond: Color, val act: Color,
)

val Accents = listOf(
    Color(0xFFFF6B3D) to "Coral", Color(0xFFFF4F8B) to "Rosa", Color(0xFFA27BFF) to "Violeta", Color(0xFFFFB224) to "Ámbar", Color(0xFF2EE6A6) to "Menta",
)

/** Tonos por flujo: seis matices (coral, rosa, violeta, ámbar, menta, azul) con luminosidad percibida parecida. */
val FlowTones = listOf(Color(0xFFFF6B3D), Color(0xFFFF4F8B), Color(0xFFA27BFF), Color(0xFFFFB224), Color(0xFF2EE6A6), Color(0xFF5B8CFF))
fun flowTone(id: String, k: Kami): Color {
    val c = FlowTones[abs(id.hashCode()) % FlowTones.size]
    return if (k.dark) c else lerp(c, k.ink, .22f)
}

private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

fun kami(theme: String, systemDark: Boolean, accent: Color): Kami {
    val amoled = theme == "amoled"
    val dark = when (theme) { "washi" -> false; "auto" -> systemDark; else -> true }
    val onAccent = if (lum(accent) > .5f) Color(0xFF14121A) else Color(0xFFFFFFFF)
    return if (dark) {
        Kami(true, amoled, if (amoled) Color(0xFF000000) else Color(0xFF07070B), if (amoled) Color(0xFF0C0C11) else Color(0xFF111118), if (amoled) Color(0xFF17171F) else Color(0xFF1B1B25),
            Color(0xFFF5F5FA), Color(0xFFC4C4D2), Color(0xFF8D8D9E), Color(0xFF5C5C6D), Color(0xFFFFFFFF).copy(alpha = .10f),
            Color(0xFFC4C4D2), Color(0xFFFFB84D), Color(0xFF38E0A8), Color(0xFFFFB84D), Color(0xFFFF5470), accent, accent, onAccent,
            Color(0xFFFF6B3D), Color(0xFFB58CFF), Color(0xFF38E0A8))
    } else {
        val ink = Color(0xFF16141D)
        Kami(false, false, Color(0xFFF3F1F8), Color(0xFFFFFFFF), Color(0xFFE9E6F1), ink, Color(0xFF4B4958), Color(0xFF76737F), Color(0xFFA9A6B3), ink.copy(alpha = .09f),
            Color(0xFF4B4958), Color(0xFFB26A00), Color(0xFF0E9F72), Color(0xFFB26A00), Color(0xFFD42A4C), accent, lerp(accent, ink, .18f), onAccent,
            Color(0xFFE2501F), Color(0xFF7E4FE0), Color(0xFF0E9F72))
    }
}

val LocalKami = staticCompositionLocalOf { kami("grafito", true, Accents[0].first) }

private val Disp = FontFamily(Font(R.font.inter_display_semibold, FontWeight.SemiBold), Font(R.font.inter_display_bold, FontWeight.Bold), Font(R.font.inter_display_extrabold, FontWeight.ExtraBold))
/** Inter (tamaño óptico de texto): cuerpo e interfaz. */
val Mono = FontFamily(Font(R.font.inter_text_regular, FontWeight.Normal), Font(R.font.inter_text_medium, FontWeight.Medium), Font(R.font.inter_text_semibold, FontWeight.SemiBold), Font(R.font.inter_text_semibold, FontWeight.Bold))

/** Jerarquía tipográfica: contraste fuerte entre cifras/títulos (display, tabulares) y texto de apoyo. */
object T {
    private const val TN = "tnum"
    val mega = TextStyle(fontFamily = Disp, fontWeight = FontWeight.ExtraBold, fontSize = 132.sp, lineHeight = 124.sp, letterSpacing = (-5).sp, fontFeatureSettings = TN)
    val hero = TextStyle(fontFamily = Disp, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 70.sp, letterSpacing = (-2.5).sp, fontFeatureSettings = TN)
    val display = TextStyle(fontFamily = Disp, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-1).sp)
    val h1 = TextStyle(fontFamily = Disp, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-.5).sp)
    val h2 = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-.2).sp)
    val body = TextStyle(fontFamily = Mono, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = (-.1).sp)
    val small = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = .7.sp)
    val num = TextStyle(fontFamily = Disp, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1).sp, fontFeatureSettings = TN)
}

/** Esquina continua (superelipse): la curva entra tangente en el lado recto, como las esquinas de iOS, sin el «codo» de un arco circular. */
class Squircle(private val radius: Dp, private val n: Float = 4.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val c = minOf(radius.value * density.density * 1.35f, w / 2f, h / 2f)
        val e = 2f / n
        fun f(v: Double) = v.toFloat().coerceAtLeast(0f).pow(e)
        val p = Path()
        p.moveTo(c, 0f); p.lineTo(w - c, 0f)
        val steps = 16
        for (i in 0..steps) { val t = i * (Math.PI / 2 / steps); p.lineTo(w - c + c * f(sin(t)), c - c * f(cos(t))) }
        p.lineTo(w, h - c)
        for (i in 0..steps) { val t = i * (Math.PI / 2 / steps); p.lineTo(w - c + c * f(cos(t)), h - c + c * f(sin(t))) }
        p.lineTo(c, h)
        for (i in 0..steps) { val t = i * (Math.PI / 2 / steps); p.lineTo(c - c * f(sin(t)), h - c + c * f(cos(t))) }
        p.lineTo(0f, c)
        for (i in 0..steps) { val t = i * (Math.PI / 2 / steps); p.lineTo(c - c * f(cos(t)), c - c * f(sin(t))) }
        p.close()
        return Outline.Generic(p)
    }
}

val Sq28 = Squircle(24.dp); val Sq20 = Squircle(16.dp); val Sq14 = Squircle(11.dp)

/** Respeta «Quitar animaciones» de Android (escala de animación 0). */
@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
fun FlowTheme(theme: String, accent: Int, content: @Composable () -> Unit) {
    val target = kami(theme, isSystemInDarkTheme(), Accents[accent.coerceIn(0, Accents.lastIndex)].first)
    val k = target
    val dark = k.dark
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = k.accent, onPrimary = k.onAccent, background = k.paper, onBackground = k.ink, surface = k.paper, onSurface = k.ink,
        surfaceVariant = k.raised, onSurfaceVariant = k.dim, outline = k.line, outlineVariant = k.line, error = k.error,
        surfaceContainer = k.raised, surfaceContainerHigh = k.high, surfaceContainerHighest = k.high, secondary = k.secondary,
        secondaryContainer = k.high, onSecondaryContainer = k.ink, surfaceTint = Color.Transparent,
    )
    val typo = Typography().run {
        copy(headlineLarge = T.display, headlineMedium = T.h1, titleLarge = T.h1, titleMedium = T.h2, titleSmall = T.h2,
            bodyLarge = T.body, bodyMedium = T.body, bodySmall = T.small, labelLarge = T.body, labelMedium = T.small, labelSmall = T.label)
    }
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect { (view.context as? android.app.Activity)?.window?.let { w -> androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    CompositionLocalProvider(LocalKami provides k) { MaterialTheme(colorScheme = scheme, typography = typo, content = content) }
}
