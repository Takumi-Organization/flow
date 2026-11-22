package dev.flow.app.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flow.app.R

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
 * Takumi no tiene acento. Se conserva la lista (la preferencia guarda un índice) pero todos los valores resuelven a la
 * tinta; el selector ya no se muestra.
 */
val Accents = listOf(Color(0xFFF2F2F2) to "Tinta")

/** Takumi: sin tono por flujo; cada flujo se distingue por su nombre. */
fun flowTone(@Suppress("UNUSED_PARAMETER") id: String, k: Kami): Color = k.ink

/**
 * Takumi (tokens.json). Estados (ok/aviso/error) = tinta: se distinguen por glifo (✓ ▲ ✕ ■) y texto, nunca por color.
 * Disparador / condición / acción se distinguen por luminancia (y por la forma y la etiqueta del nodo).
 */
fun kami(theme: String, systemDark: Boolean, @Suppress("UNUSED_PARAMETER") accent: Color): Kami {
    val amoled = theme == "amoled"
    val dark = when (theme) { "washi" -> false; "auto" -> systemDark; else -> true }
    return if (dark) {
        val ink = Color(0xFFF2F2F2)
        Kami(true, amoled, Color(0xFF000000), Color(0xFF080808), Color(0xFF111111),
            ink, Color(0xFFC9C9C9), Color(0xFF8C8C8C), Color(0xFF4A4A4A), Color(0xFF3A3A3A),
            Color(0xFFC9C9C9), Color(0xFF8C8C8C), ink, ink, ink, ink, ink, Color(0xFF000000),
            Color(0xFFF2F2F2), Color(0xFFB0B0B0), Color(0xFF7C7C7C))
    } else {
        val ink = Color(0xFF0A0A0A)
        Kami(false, false, Color(0xFFF4F4F4), Color(0xFFFFFFFF), Color(0xFFEBEBEB),
            ink, Color(0xFF333333), Color(0xFF6B6B6B), Color(0xFFB0B0B0), Color(0xFFC0C0C0),
            Color(0xFF333333), Color(0xFF6B6B6B), ink, ink, ink, ink, ink, Color(0xFFF4F4F4),
            Color(0xFF0A0A0A), Color(0xFF4F4F4F), Color(0xFF8A8A8A))
    }
}

val LocalKami = staticCompositionLocalOf { kami("grafito", true, Accents[0].first) }

@OptIn(ExperimentalTextApi::class)
private fun outfit(w: Int, key: FontWeight) = Font(R.font.outfit_variable, key, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
/** Outfit: pesos de la app → pesos Takumi (lo «Bold» de títulos y cifras pasa a 200, el cuerpo a 300, lo enfatizado a 500). */
private val Disp = FontFamily(outfit(200, FontWeight.SemiBold), outfit(200, FontWeight.Bold), outfit(200, FontWeight.ExtraBold))
val Mono = FontFamily(outfit(300, FontWeight.Normal), outfit(400, FontWeight.Medium), outfit(500, FontWeight.SemiBold), outfit(500, FontWeight.Bold))
/** JetBrains Mono: etiquetas y datos. */
val Jb = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_medium, FontWeight.Medium), Font(R.font.jetbrains_mono_medium, FontWeight.SemiBold), Font(R.font.jetbrains_mono_medium, FontWeight.Bold))

/** Jerarquía tipográfica: títulos y cifras en Outfit 200 ancha; datos y etiquetas en JetBrains Mono. */
object T {
    private const val TN = "tnum"
    val mega = TextStyle(fontFamily = Disp, fontWeight = FontWeight.ExtraBold, fontSize = 132.sp, lineHeight = 124.sp, letterSpacing = (-2).sp, fontFeatureSettings = TN)
    val hero = TextStyle(fontFamily = Disp, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = 1.sp, fontFeatureSettings = TN)
    val display = TextStyle(fontFamily = Disp, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 3.sp)
    val h1 = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 1.sp)
    val h2 = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = .5.sp)
    val body = TextStyle(fontFamily = Mono, fontSize = 15.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Jb, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)
    val num = TextStyle(fontFamily = Jb, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp, fontFeatureSettings = TN)
}

/**
 * Takumi: radio 0 y esquina superior derecha cortada en diagonal (10/14/22 dp). Se conserva el nombre `Squircle` y el
 * radio de cada sitio; el corte se deriva de él en un solo punto.
 */
fun takumiCut(r: Dp): Dp = when { r <= 12.dp -> 10.dp; r <= 18.dp -> 14.dp; else -> 22.dp }
class Squircle(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        androidx.compose.foundation.shape.CutCornerShape(topStart = 0.dp, topEnd = takumiCut(radius), bottomEnd = 0.dp, bottomStart = 0.dp).createOutline(size, layoutDirection, density)
}
val TkSm: Shape = Squircle(10.dp)

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
