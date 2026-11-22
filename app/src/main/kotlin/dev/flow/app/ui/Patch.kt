package dev.flow.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.flow.app.data.RunRow
import dev.flow.engine.*
import kotlinx.coroutines.delay
import kotlin.math.*

// ───────────── iconografía propia: trazo fino de 1,8 dp, terminaciones y uniones redondeadas, rejilla de 24 ─────────────
@Composable
fun PIcon(name: String, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, 1.8f * u, StrokeCap.Round)
        fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) =
            drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else st)
        when (name) {
            "home" -> { val pa = Path().apply { moveTo(4f * u, 11f * u); lineTo(12f * u, 4f * u); lineTo(20f * u, 11f * u); lineTo(20f * u, 19f * u); lineTo(15f * u, 19f * u); lineTo(15f * u, 14f * u); lineTo(9f * u, 14f * u); lineTo(9f * u, 19f * u); lineTo(4f * u, 19f * u); close() }; drawPath(pa, color, style = st) }
            "library" -> { rr(4f, 4f, 6.5f, 6.5f, 2f); rr(13.5f, 4f, 6.5f, 6.5f, 2f); rr(4f, 13.5f, 6.5f, 6.5f, 2f); rr(13.5f, 13.5f, 6.5f, 6.5f, 2f) }
            "history" -> { val pa = Path().apply { moveTo(3f * u, 13f * u); lineTo(7f * u, 13f * u); lineTo(9.5f * u, 5f * u); lineTo(13f * u, 19f * u); lineTo(15.5f * u, 10f * u); lineTo(17f * u, 13f * u); lineTo(21f * u, 13f * u) }; drawPath(pa, color, style = st) }
            "settings" -> { listOf(7f to 8f, 12f to 15f, 17f to 10f).forEach { (x, y) -> line(p(x, 3.5f), p(x, 20.5f)); drawCircle(color, 2.6f * u, p(x, y)); drawCircle(Color.Transparent, 0f, p(x, y)) } }
            "bolt" -> { val pa = Path().apply { moveTo(13.5f * u, 2.5f * u); lineTo(5f * u, 13.5f * u); lineTo(11f * u, 13.5f * u); lineTo(10f * u, 21.5f * u); lineTo(19f * u, 10f * u); lineTo(13f * u, 10f * u); close() }; drawPath(pa, color) }
            "diamond" -> { val pa = Path().apply { moveTo(12f * u, 2.5f * u); lineTo(21.5f * u, 12f * u); lineTo(12f * u, 21.5f * u); lineTo(2.5f * u, 12f * u); close() }; drawPath(pa, color, style = st) }
            "square" -> rr(4.5f, 4.5f, 15f, 15f, 4f)
            "plus" -> { line(p(12f, 4.5f), p(12f, 19.5f)); line(p(4.5f, 12f), p(19.5f, 12f)) }
            "play" -> { val pa = Path().apply { moveTo(7f * u, 4.5f * u); lineTo(20f * u, 12f * u); lineTo(7f * u, 19.5f * u); close() }; drawPath(pa, color, style = androidx.compose.ui.graphics.drawscope.Fill) }
            "stop" -> rr(6f, 6f, 12f, 12f, 3f, fill = true)
            "back" -> { line(p(15f, 4.5f), p(7.5f, 12f)); line(p(7.5f, 12f), p(15f, 19.5f)) }
            "more" -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.9f * u, p(it, 12f)) }
            "check" -> { line(p(5f, 12.5f), p(10f, 17.5f)); line(p(10f, 17.5f), p(19f, 7f)) }
            "cross" -> { line(p(6f, 6f), p(18f, 18f)); line(p(18f, 6f), p(6f, 18f)) }
            "warn" -> { val pa = Path().apply { moveTo(12f * u, 3.5f * u); lineTo(21f * u, 19.5f * u); lineTo(3f * u, 19.5f * u); close() }; drawPath(pa, color, style = st); line(p(12f, 9.5f), p(12f, 13.5f)); drawCircle(color, 1f * u, p(12f, 16.5f)) }
        }
    }
}

// ───────────── luz de estado ─────────────
@Composable
fun Led(on: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp, breathe: Boolean = false) {
    val rm = reduceMotion()
    val tr = rememberInfiniteTransition(label = "led")
    val a by tr.animateFloat(.55f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val off = k.faint
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (on) { val br = if (breathe && !rm) a else 1f; drawCircle(color.copy(alpha = .28f * br), r); drawCircle(color, r * (.50f + .08f * br)) }
        else drawCircle(off.copy(alpha = .6f), r * .5f)
    }
}

/** Interruptor al estilo iOS: pista que cambia de color, pieza que se estira al pulsar y rebota al soltar. */
@Composable
fun PatchToggle(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tone: Color = k.accent) {
    val view = LocalView.current; val rm = reduceMotion(); val kk = k
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val x by animateFloatAsState(if (on) 1f else 0f, if (rm) snap() else spring(.55f, 650f), label = "knob")
    val grow by animateFloatAsState(if (pressed && !rm) 1f else 0f, spring(.6f, 800f), label = "grow")
    val track by animateColorAsState(if (on) tone else kk.high, tween(if (rm) 0 else 220), label = "track")
    Box(modifier.sizeIn(minWidth = 64.dp, minHeight = 52.dp).toggleable(on, src, null, enabled, Role.Switch) { v -> view.hap(if (v) Hap.CONFIRM else Hap.CLICK); onChange(v) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(58.dp, 34.dp).background(track, TkSm).border(1.dp, kk.line, TkSm)) {
            val kw = 28.dp + 6.dp * grow
            Box(Modifier.offset(x = 3.dp + (58.dp - kw - 6.dp) * x).align(Alignment.CenterStart).size(kw, 28.dp).background(if (on) kk.paper else kk.muted, TkSm))
        }
    }
}

// ───────────── entrada escalonada ─────────────
@Composable
fun Modifier.stagger(index: Int, stepMs: Int = 50): Modifier {
    val rm = reduceMotion()
    var shown by remember { mutableStateOf(rm) }
    LaunchedEffect(Unit) { if (!rm) { delay(index.coerceAtMost(9) * stepMs.toLong()); shown = true } }
    val a by animateFloatAsState(if (shown) 1f else 0f, spring(.78f, 260f), label = "stagger")
    return this.graphicsLayer { alpha = a.coerceIn(0f, 1f); translationY = (1f - a) * 44f; val s = .97f + .03f * a; scaleX = s; scaleY = s }
}

// ───────────── cifras que ruedan ─────────────
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, pad: Int = 1) {
    val rm = reduceMotion()
    var prev by remember { mutableIntStateOf(value) }
    val dir = if (value >= prev) 1 else -1
    SideEffect { prev = value }
    val s = value.toString().padStart(pad, '0')
    Row(modifier) {
        s.forEachIndexed { i, ch ->
            AnimatedContent(ch, label = "digit$i", transitionSpec = {
                if (rm) ContentTransform(EnterTransition.None, ExitTransition.None)
                else (slideInVertically(spring(.72f, 420f)) { it * dir } + fadeIn(tween(140))) togetherWith (slideOutVertically(spring(.9f, 500f)) { -it * dir } + fadeOut(tween(100)))
            }) { c -> Text(c.toString(), style = style, color = color, maxLines = 1) }
        }
    }
}

// ───────────── miniatura del cableado ─────────────
/** Los nodos de un flujo como piezas diminutas con el color de su tipo y cables luminosos del [tone] del flujo; con [phase] viajan pulsos. */
@Composable
fun MiniPatch(flow: FlowDef, registry: NodeRegistry, modifier: Modifier = Modifier, phase: (() -> Float)? = null, lit: Boolean = false, tone: Color = k.signal) {
    val kc = k
    Canvas(modifier) {
        if (flow.nodes.isEmpty()) return@Canvas
        val pad = 14f * density
        val minX = flow.nodes.minOf { it.x }; val maxX = flow.nodes.maxOf { it.x }; val minY = flow.nodes.minOf { it.y }; val maxY = flow.nodes.maxOf { it.y }
        val w = max(1f, maxX - minX); val h = max(1f, maxY - minY)
        val s = min((size.width - 2 * pad) / w, (size.height - 2 * pad) / h).coerceAtMost(.55f * density)
        val ox = (size.width - w * s) / 2f - minX * s; val oy = (size.height - h * s) / 2f - minY * s
        fun pos(n: NodeDef) = Offset(n.x * s + ox, n.y * s + oy)
        val cable = if (lit) tone.copy(alpha = .75f) else kc.ink.copy(alpha = .22f)
        flow.edges.forEachIndexed { i, e ->
            val a = flow.node(e.from)?.let(::pos) ?: return@forEachIndexed; val b = flow.node(e.to)?.let(::pos) ?: return@forEachIndexed
            val dx = max(12f * density, abs(b.x - a.x) / 2f)
            val path = Path().apply { moveTo(a.x, a.y); cubicTo(a.x + dx, a.y, b.x - dx, b.y, b.x, b.y) }
            if (lit) drawPath(path, tone.copy(alpha = .18f), style = Stroke(5f * density, cap = StrokeCap.Round))
            drawPath(path, cable, style = Stroke(1.8f * density, cap = StrokeCap.Round))
            if (phase != null) { val t = (phase() + i * .21f) % 1f; val pt = bez(a, Offset(a.x + dx, a.y), Offset(b.x - dx, b.y), b, t); drawCircle(tone.copy(alpha = .35f), 6f * density, pt); drawCircle(Color.White, 2.6f * density, pt) }
        }
        val r = 4.4f * density
        for (n in flow.nodes) {
            val c = pos(n); val kind = registry[n.type]?.kind
            val col = if (kind == null) kc.error else kindColor(kind, kc)
            val fill = if (lit) col else col.copy(alpha = .55f)
            if (lit) drawCircle(col.copy(alpha = .22f), r * 2f, c)
            when (kind) {
                NodeKind.TRIGGER -> drawCircle(fill, r, c)
                NodeKind.CONDITION -> { val rr = r * 1.25f; val d = Path().apply { moveTo(c.x, c.y - rr); lineTo(c.x + rr, c.y); lineTo(c.x, c.y + rr); lineTo(c.x - rr, c.y); close() }; drawPath(d, fill) }
                else -> drawRoundRect(fill, c - Offset(r, r), Size(2 * r, 2 * r), CornerRadius(r * .45f))
            }
        }
    }
}

fun bez(a: Offset, b: Offset, c: Offset, d: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(u * u * u * a.x + 3 * u * u * t * b.x + 3 * u * t * t * c.x + t * t * t * d.x, u * u * u * a.y + 3 * u * u * t * b.y + 3 * u * t * t * c.y + t * t * t * d.y)
}

// ───────────── actividad de ejecuciones ─────────────
/**
 * Cada ejecución es una barra redondeada: altura = duración (escala logarítmica), menta si salió bien, rosa si falló.
 * Una onda de luz recorre las barras de izquierda a derecha.
 */
@Composable
fun Scope(runs: List<RunRow>, modifier: Modifier = Modifier, max: Int = 40) {
    val rm = reduceMotion(); val kc = k
    val tr = rememberInfiniteTransition(label = "scan")
    val scan by tr.animateFloat(-.2f, 1.2f, infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing)), label = "scan")
    val data = remember(runs) { runs.sortedBy { it.startedAt }.takeLast(max) }
    Canvas(modifier) {
        val base = size.height - 10f * density
        val top = 12f * density
        for (i in 0..2) { val y = top + (base - top) * i / 2f; drawLine(kc.ink.copy(alpha = .07f), Offset(0f, y), Offset(size.width, y), 1f * density) }
        val maxLn = max(ln(1f + (data.maxOfOrNull { it.durationMs } ?: 1L).toFloat()), 1f)
        val slots = max(data.size, 14)
        val slot = size.width / slots
        val n = data.size
        data.forEachIndexed { i, r ->
            val x = size.width - (n - i) * slot + slot / 2f
            val hgt = (.22f + .78f * ln(1f + r.durationMs.toFloat()) / maxLn).coerceIn(.22f, 1f) * (base - top)
            val ok = r.status == "SUCCESS"; val rej = r.status == "REJECTED"
            val col = when { ok -> kc.ok; rej -> kc.faint; r.status == "CANCELLED" -> kc.warn; else -> kc.error }
            val pos = x / size.width
            val wave = if (rm) 0f else (1f - (abs(pos - scan) / .16f)).coerceIn(0f, 1f)
            val bw = (slot * .5f).coerceIn(3f * density, 8f * density)
            val a = .55f + .45f * wave
            if (wave > 0f) drawRoundRect(col.copy(alpha = .22f * wave), Offset(x - bw, base - hgt - bw * .5f), Size(bw * 2f, hgt + bw), CornerRadius(bw))
            drawRoundRect(col.copy(alpha = a), Offset(x - bw / 2f, base - hgt), Size(bw, hgt), CornerRadius(bw / 2f))
        }
        if (n == 0) { val y = base; val pa = Path().apply { moveTo(0f, y); lineTo(size.width, y) }; drawPath(pa, kc.muted.copy(alpha = .4f), style = Stroke(2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * density, 8f * density)), cap = StrokeCap.Round)) }
    }
}

// ───────────── «sin señal» ─────────────
/** Un enchufe y su toma, separados, con una chispa que parpadea. Para estados vacíos. */
@Composable
fun NoSignal(modifier: Modifier = Modifier, color: Color = k.muted) {
    val rm = reduceMotion()
    val tr = rememberInfiniteTransition(label = "spark")
    val on by tr.animateFloat(0f, 1f, infiniteRepeatable(keyframes { durationMillis = 2600; 0f at 0; 0f at 1800; 1f at 1860; 0.2f at 1960; 1f at 2060; 0f at 2300 }), label = "s")
    val sc = k.signal
    Canvas(modifier.size(220.dp, 84.dp)) {
        val u = density; val cy = size.height / 2f; val st = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(4f * u, cy), Offset(58f * u, cy), 2.6f * u, StrokeCap.Round)
        drawRoundRect(color, Offset(58f * u, cy - 19f * u), Size(34f * u, 38f * u), CornerRadius(9f * u), style = st)
        drawLine(color, Offset(92f * u, cy - 9f * u), Offset(106f * u, cy - 9f * u), 2.6f * u, StrokeCap.Round); drawLine(color, Offset(92f * u, cy + 9f * u), Offset(106f * u, cy + 9f * u), 2.6f * u, StrokeCap.Round)
        drawRoundRect(color, Offset(size.width - 78f * u, cy - 25f * u), Size(42f * u, 50f * u), CornerRadius(11f * u), style = st)
        drawCircle(color, 2.6f * u, Offset(size.width - 64f * u, cy - 7f * u)); drawCircle(color, 2.6f * u, Offset(size.width - 64f * u, cy + 7f * u))
        drawLine(color, Offset(size.width - 36f * u, cy), Offset(size.width - 4f * u, cy), 2.6f * u, StrokeCap.Round)
        val a = if (rm) 0f else on
        if (a > 0f) { val z = Path().apply { moveTo(112f * u, cy); lineTo(124f * u, cy - 11f * u); lineTo(132f * u, cy + 9f * u); lineTo(144f * u, cy - 7f * u); lineTo(size.width - 84f * u, cy) }; drawPath(z, sc.copy(alpha = a), style = st) }
    }
}

/** Cifra grande con etiqueta pequeña, al estilo de un resumen de Salud. */
@Composable
fun Readout(value: String, label: String, modifier: Modifier = Modifier, color: Color = k.ink) {
    Column(modifier) {
        Text(value, style = T.num, color = color, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = T.small, color = k.muted)
    }
}
