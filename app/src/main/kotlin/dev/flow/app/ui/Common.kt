package dev.flow.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flow.engine.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val R4: Shape = Sq14
val k: Kami @Composable get() = LocalKami.current

// ───────────── hápticos ─────────────
enum class Hap { TICK, CLICK, CONFIRM, REJECT, HEAVY }

fun View.hap(h: Hap) {
    val c = when (h) {
        Hap.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Hap.CLICK -> if (Build.VERSION.SDK_INT >= 23) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.VIRTUAL_KEY
        Hap.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Hap.REJECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Hap.HEAVY -> HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(c)
}

// ───────────── texto ─────────────
@Composable fun Label(text: String, modifier: Modifier = Modifier, color: Color = k.muted) = Text(text.uppercase(), style = T.label, color = color, modifier = modifier)

/** Título de grupo, al modo de los ajustes de iOS: rótulo contundente y mucho aire. */
@Composable
fun Section(num: String, title: String, modifier: Modifier = Modifier) {
    Text(title, style = T.h1, color = k.ink, modifier = modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp))
}
@Composable fun Section(num: String, title: String) = Section(num, title, Modifier)

/** Pulsación con muelle: la pieza se hunde y vuelve con un pequeño rebote. */
@Composable
fun Modifier.springPress(src: MutableInteractionSource, depth: Float = .965f): Modifier {
    val pressed by src.collectIsPressedAsState(); val rm = reduceMotion()
    val s by animateFloatAsState(if (pressed && !rm) depth else 1f, spring(.45f, 520f), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Tarjeta plana: superficie elevada con filete de 1 dp (sin sombra ni resplandor). */
@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val kk = k
    val bg by animateColorAsState(if (pressed) kk.high else kk.raised, tween(120), label = "bg")
    val rim = SolidColor(kk.line)
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.springPress(src, .975f) else Modifier)
        .then(if (!kk.dark) Modifier else Modifier)
        .clip(Sq28).background(bg)
        .border(.8.dp, rim, Sq28)
        .then(if (onClick != null) Modifier.clickable(src, null, role = Role.Button, onClick = onClick) else Modifier)
        .padding(18.dp), content = content)
}

@Composable
fun FButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, danger: Boolean = false) {
    val view = LocalView.current; val src = remember { MutableInteractionSource() }
    val kk = k
    val fg = if (!enabled) kk.faint else if (primary) kk.onAccent else if (danger) kk.error else kk.ink
    val fill: Brush = when {
        !enabled -> SolidColor(kk.high.copy(alpha = .5f))
        primary -> SolidColor(kk.accent)
        danger -> SolidColor(Color.Transparent)
        else -> SolidColor(kk.high)
    }
    Box(modifier.heightIn(min = 52.dp).springPress(src).clip(Sq20).background(fill).then(if (primary || !enabled) Modifier else Modifier.border(1.dp, if (danger) kk.ink else kk.line, Sq20))
        .clickable(src, null, enabled = enabled, role = Role.Button) { view.hap(if (primary) Hap.CONFIRM else Hap.CLICK); onClick() }
        .padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(if (danger) "✕ " + text.uppercase() else text.uppercase(), style = T.label.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Selector en cápsula: el seleccionado se invierte (tinta sobre papel) con un fundido de color. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current; val src = remember { MutableInteractionSource() }; val kk = k
    val bg by animateColorAsState(if (selected) kk.ink else kk.high, spring(.8f, 700f), label = "chipbg")
    val fg by animateColorAsState(if (selected) kk.paper else kk.dim, spring(.8f, 700f), label = "chipfg")
    Box(modifier.heightIn(min = 44.dp).springPress(src, .94f).clip(TkSm).background(bg).border(1.dp, if (selected) kk.ink else kk.line, TkSm)
        .clickable(src, null, role = Role.Tab) { view.hap(Hap.TICK); onClick() }.padding(horizontal = 18.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), style = T.label.copy(fontSize = 12.sp), color = fg)
    }
}

/** Campo de texto: relleno suave, etiqueta pequeña y un aro de color que aparece con muelle al enfocar. */
@Composable
fun FField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, number: Boolean = false, error: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState(); val kk = k
    val ring by animateColorAsState(if (error) kk.error else if (focused) kk.signal else Color.Transparent, tween(160), label = "ring")
    Column(modifier.fillMaxWidth()) {
        Text(label, style = T.label.copy(letterSpacing = .3.sp), color = if (error) kk.error else if (focused) kk.signal else kk.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(value, onChange, Modifier.fillMaxWidth().clip(Sq14).background(kk.high).border(2.dp, ring, Sq14),
            textStyle = T.body.copy(color = kk.ink), singleLine = singleLine, minLines = if (singleLine) 1 else 2, interactionSource = src,
            cursorBrush = SolidColor(kk.signal), keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
            decorationBox = { inner -> Box(Modifier.heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 13.dp), contentAlignment = Alignment.CenterStart) { inner() } })
    }
}

@Composable fun Mark(state: NodeState?, status: RunStatus? = null): Pair<String, Color> = when {
    state == NodeState.COMPLETED -> "✓" to k.ok
    state == NodeState.ERROR -> "✕" to k.error
    state == NodeState.RUNNING -> "…" to k.accent
    state == NodeState.SKIPPED -> "–" to k.faint
    state == NodeState.PENDING -> "·" to k.muted
    status == RunStatus.SUCCESS -> "✓" to k.ok
    status == RunStatus.ERROR -> "✕" to k.error
    status == RunStatus.CANCELLED -> "■" to k.warn
    status == RunStatus.REJECTED -> "–" to k.faint
    else -> "?" to k.muted
}

private val dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
fun fmt(t: LocalDateTime): String = t.format(dtf)
fun fmtMs(ms: Long): String = if (ms < 1000) "$ms ms" else "%.1f s".format(ms / 1000.0)
fun statusText(s: String) = when (s) { "SUCCESS" -> "Correcta"; "ERROR" -> "Con error"; "CANCELLED" -> "Cancelada"; "REJECTED" -> "Rechazada"; else -> s }

/** Disparador = coral; condición = violeta; acción = menta. Fijos, para que un flujo se lea igual con cualquier acento. */
fun kindColor(kind: NodeKind, k: Kami): Color = when (kind) { NodeKind.TRIGGER -> k.trig; NodeKind.CONDITION -> k.cond; NodeKind.ACTION -> k.act }
