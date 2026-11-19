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

/**
 * Acentos de Ryoku Glass. El ORDEN se conserva (la preferencia guarda el índice): 0 Bermellón (antes Coral), 1 Fucsia
 * (Rosa), 2 Egeo (Violeta), 3 Oro (Ámbar), 4 Lima (Menta). El color de la lista es la variante oscura; en claro se usa
 * [accentLight] (más oscura, para que el acento como texto o relleno llegue a 4,5:1 sobre el papel claro).
 */
val Accents = listOf(
    Color(0xFFFF5A4A) to "Bermellón", Color(0xFFFF6FB1) to "Fucsia", Color(0xFF62B6E8) to "Egeo", Color(0xFFF2B33D) to "Oro", Color(0xFFB6E04A) to "Lima",
)
private val AccentLight = listOf(Color(0xFFD12B1F), Color(0xFFC4166F), Color(0xFF0B6B99), Color(0xFF8F5F00), Color(0xFF4F7A00))
private val AccentOnDark = listOf(Color(0xFF1B0805), Color(0xFF21040F), Color(0xFF04141D), Color(0xFF1D1300), Color(0xFF121A00))
private fun accentIndex(c: Color) = Accents.indexOfFirst { it.first == c }.coerceAtLeast(0)

/** Tonos por flujo: seis matices (coral, rosa, violeta, ámbar, menta, azul) con luminosidad percibida parecida. */
val FlowTones = listOf(Color(0xFFFF6B3D), Color(0xFFFF4F8B), Color(0xFFA27BFF), Color(0xFFFFB224), Color(0xFF2EE6A6), Color(0xFF5B8CFF))
fun flowTone(id: String, k: Kami): Color {
    val c = FlowTones[abs(id.hashCode()) % FlowTones.size]
    return if (k.dark) c else lerp(c, k.ink, .22f)
}

/** `accent` llega siempre como la variante oscura de [Accents]; en claro se sustituye por la clara. */
fun kami(theme: String, systemDark: Boolean, accent: Color): Kami {
    val amoled = theme == "amoled"
    val dark = when (theme) { "washi" -> false; "auto" -> systemDark; else -> true }
    val ai = accentIndex(accent)
    return if (dark) {
        Kami(true, amoled, if (amoled) Color(0xFF000000) else Color(0xFF0F1115), if (amoled) Color(0xFF0D0F13) else Color(0xFF171A20), if (amoled) Color(0xFF181B21) else Color(0xFF232830),
            Color(0xFFECEFF4), Color(0xFFB4BBC7), Color(0xFF8E97A6), Color(0xFF5F6877), Color(0xFFECEFF4).copy(alpha = .10f),
            Color(0xFFB4BBC7), Color(0xFFF2B33D), Color(0xFF6FD3A6), Color(0xFFF0B25E), Color(0xFFFF7A70), Accents[ai].first, Accents[ai].first, AccentOnDark[ai],
            Color(0xFFFF6B3D), Color(0xFFB58CFF), Color(0xFF38E0A8))
    } else {
        val ink = Color(0xFF14171C)
        val a = AccentLight[ai]
        Kami(false, false, Color(0xFFF4F5F7), Color(0xFFFFFFFF), Color(0xFFE9ECF0), ink, Color(0xFF4A515C), Color(0xFF5F6773), Color(0xFF9AA2AE), ink.copy(alpha = .11f),
            Color(0xFF4A515C), Color(0xFF8F5F00), Color(0xFF1B6B49), Color(0xFF8A5A00), Color(0xFFB3261E), a, lerp(a, ink, .2f), Color.White,
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

/**
 * Ryoku Glass usa radios pequeños (6 · 10 · 12 · 16 · 20). Se conserva el nombre `Squircle` y el radio de cada sitio, y se
 * traducen a la escala nueva en un solo punto.
 */
fun glassRadius(r: Dp): Dp = when { r <= 8.dp -> 6.dp; r <= 14.dp -> 10.dp; r <= 22.dp -> 12.dp; r <= 30.dp -> 16.dp; else -> 20.dp }
class Squircle(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        androidx.compose.foundation.shape.RoundedCornerShape(glassRadius(radius)).createOutline(size, layoutDirection, density)
}

/** Antigua superelipse (ya sin uso; se conserva por si se recupera). */
@Suppress("unused")
private class SuperSquircle(private val radius: Dp, private val n: Float = 4.6f) : Shape {
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
