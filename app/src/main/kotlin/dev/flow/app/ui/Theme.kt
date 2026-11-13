package dev.flow.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import dev.flow.app.R

/** Tokens de Ryoku Kami (Grafito/Washi). Plano, sin sombras, bordes finos y un acento por tema. */
@Immutable
class Kami(
    val dark: Boolean, val paper: Color, val raised: Color, val high: Color, val ink: Color, val dim: Color, val muted: Color,
    val faint: Color, val line: Color, val secondary: Color, val gold: Color, val ok: Color, val warn: Color, val error: Color, val accent: Color,
)

val Accents = listOf(Color(0xFFE2342A) to "Bermellón", Color(0xFFFF7A1A) to "Naranja", Color(0xFFB6E83A) to "Lima", Color(0xFFFF3D9A) to "Fucsia", Color(0xFF3AA0FF) to "Azul")

fun kami(dark: Boolean, accent: Color): Kami = if (dark) Kami(
    true, Color(0xFF211D1A), Color(0xFF2A2521), Color(0xFF352F2A), Color(0xFFECE4D8), Color(0xFFC9C0B3), Color(0xFFA69D90), Color(0xFF8F867A),
    Color(0xFFECE4D8).copy(alpha = .30f), Color(0xFF8FBDD1), Color(0xFFD9A441), Color(0xFF7FD0AE), Color(0xFFF0B25E), Color(0xFFF27A70), accent,
) else Kami(
    false, Color(0xFFF3ECE0), Color(0xFFFAF5EC), Color(0xFFE9E0D1), Color(0xFF26211C), Color(0xFF4A423A), Color(0xFF675D52), Color(0xFF8A8074),
    Color(0xFF26211C).copy(alpha = .30f), Color(0xFF2B6A86), Color(0xFF9A6B00), Color(0xFF1F6F4D), Color(0xFF8A5A00), Color(0xFFB3261E), accent,
)

val LocalKami = staticCompositionLocalOf { kami(true, Accents[0].first) }
val Mono = FontFamily(Font(R.font.komiki_mono_regular, FontWeight.Normal), Font(R.font.komiki_mono_bold, FontWeight.Bold))

object T {
    val display = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    val h1 = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val h2 = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.9.sp)
}

@Composable
fun FlowTheme(theme: String, accent: Int, content: @Composable () -> Unit) {
    val dark = when (theme) { "washi" -> false; "auto" -> isSystemInDarkTheme(); else -> true }
    val k = kami(dark, Accents[accent.coerceIn(0, Accents.lastIndex)].first)
    val onAccent = if (k.accent.luminanceApprox() > .5f) Color(0xFF211D1A) else Color(0xFFF7F2EA)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = k.accent, onPrimary = onAccent, background = k.paper, onBackground = k.ink, surface = k.paper, onSurface = k.ink,
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

private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
