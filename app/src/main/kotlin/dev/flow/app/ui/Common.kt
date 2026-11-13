package dev.flow.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.flow.engine.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val R4 = RoundedCornerShape(4.dp)
val k: Kami @Composable get() = LocalKami.current

@Composable fun Label(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), style = T.label, color = k.muted, modifier = modifier)
@Composable fun Section(num: String, title: String) = Label("$num // $title", Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(R4).background(k.raised).border(1.dp, k.line, R4)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Row(m) {
        if (accent != null) Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(12.dp).weight(1f), content = content)
    }
}

@Composable
fun FButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, danger: Boolean = false) {
    val bg = if (primary) k.ink else Color.Transparent
    val fg = if (!enabled) k.faint else if (primary) k.paper else if (danger) k.error else k.ink
    Box(modifier.heightIn(min = 40.dp).clip(R4).background(if (enabled) bg else Color.Transparent)
        .border(1.dp, if (!enabled) k.faint.copy(alpha = .5f) else if (primary) k.ink else if (danger) k.error else k.line, R4)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.body.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 36.dp).clip(R4).background(if (selected) k.accent else Color.Transparent)
        .border(1.dp, if (selected) k.accent else k.line, R4).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.small.copy(fontWeight = FontWeight.Bold), color = if (selected) Color(0xFF211D1A).takeIf { k.accent.luminanceApprox() > .5f } ?: Color(0xFFF7F2EA) else k.ink)
    }
}
private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun FField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, number: Boolean = false, error: Boolean = false) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label, style = T.small) }, singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        isError = error, shape = R4, textStyle = T.body,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = k.ink, unfocusedBorderColor = k.line, errorBorderColor = k.error, focusedLabelColor = k.ink, cursorColor = k.ink))
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

fun kindColor(kind: NodeKind, k: Kami): Color = when (kind) { NodeKind.TRIGGER -> k.secondary; NodeKind.CONDITION -> k.gold; NodeKind.ACTION -> k.accent }
