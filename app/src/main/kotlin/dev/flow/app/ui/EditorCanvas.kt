package dev.flow.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import dev.flow.engine.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.*

const val NODE_W = 176f
const val NODE_H = 68f
private const val PORT_R = 7f

class Viewport {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset(24f, 120f))
    var size by mutableStateOf(IntSize.Zero)
    fun centerInGraph(density: Float): Offset { val u = density * scale; return Offset((size.width / 2f - offset.x) / u, (size.height / 2f - offset.y) / u) }
    fun fit(flow: FlowDef, density: Float, readable: Boolean = false) {
        if (flow.nodes.isEmpty() || size.width == 0) return
        val minX = flow.nodes.minOf { it.x }; val maxX = flow.nodes.maxOf { it.x } + NODE_W
        val minY = flow.nodes.minOf { it.y }; val maxY = flow.nodes.maxOf { it.y } + NODE_H
        val fitS = min((size.width - 80f) / ((maxX - minX) * density), (size.height - 80f) / ((maxY - minY) * density))
        val wide = readable && fitS < 0.7f
        val s = if (wide) 0.75f else fitS.coerceIn(0.3f, 1.2f); scale = s; val u = density * s
        val cy = size.height / 2f - (minY + maxY) / 2f * u
        // si el grafo es más ancho que la pantalla, se muestra desde el principio (el disparador) en vez de empequeñecerlo hasta ser ilegible
        offset = if (wide || fitS < 0.3f) Offset(28f - minX * u, cy) else Offset(size.width / 2f - (minX + maxX) / 2f * u, cy)
    }
}

fun outPort(n: NodeDef, port: String, spec: NodeSpec?): Offset = when {
    spec?.kind == NodeKind.CONDITION -> Offset(n.x + NODE_W, n.y + if (port == PORT_TRUE) NODE_H * .3f else NODE_H * .72f)
    else -> Offset(n.x + NODE_W, n.y + NODE_H / 2)
}
fun inPort(n: NodeDef) = Offset(n.x, n.y + NODE_H / 2)

private sealed interface Hit {
    data object None : Hit
    data class Node(val id: String) : Hit
    data class Port(val id: String, val port: String) : Hit
    data class Edge(val id: String) : Hit
}

private fun bezPoint(a: Offset, c1: Offset, c2: Offset, d: Offset, t: Float): Offset {
    val m = 1 - t
    return Offset(m * m * m * a.x + 3 * m * m * t * c1.x + 3 * m * t * t * c2.x + t * t * t * d.x, m * m * m * a.y + 3 * m * m * t * c1.y + 3 * m * t * t * c2.y + t * t * t * d.y)
}
private fun bez(a: Offset, b: Offset, t: Float): Offset {
    val dx = max(60f, abs(b.x - a.x) / 2f); val c1 = Offset(a.x + dx, a.y); val c2 = Offset(b.x - dx, b.y); val m = 1 - t
    return Offset(m * m * m * a.x + 3 * m * m * t * c1.x + 3 * m * t * t * c2.x + t * t * t * b.x, m * m * m * a.y + 3 * m * m * t * c1.y + 3 * m * t * t * c2.y + t * t * t * b.y)
}

class CanvasCallbacks(
    val onSelect: (Set<String>) -> Unit, val onSelectEdge: (String?) -> Unit, val onMoveBy: (Set<String>, Float, Float) -> Unit, val onMoveEnd: () -> Unit,
    val onConnect: (String, String, String) -> Unit, val onDropEmpty: (String, String, Offset) -> Unit,
)

@Composable
fun FlowCanvas(
    flow: FlowDef, registry: NodeRegistry, vp: Viewport, selected: Set<String>, selectedEdge: String?,
    states: Map<String, NodeState>, issues: Map<String, Severity>, cb: CanvasCallbacks, modifier: Modifier = Modifier,
) {
    val kc = k
    val dens = LocalDensity.current.density
    val anyRunning = states.values.any { it == NodeState.RUNNING }
    val pulse = if (anyRunning && !reduceMotion()) rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing)), label = "pl").value else 0f
    val measurer = rememberTextMeasurer()
    val unit = Density(1f, 1f)
    val vc = LocalViewConfiguration.current
    val cur = rememberUpdatedState(arrayOf<Any?>(flow, selected, selectedEdge, cb))
    var preview by remember { mutableStateOf<Triple<String, String, Offset>?>(null) }
    var hover by remember { mutableStateOf<String?>(null) }

    fun toGraph(p: Offset): Offset { val u = dens * vp.scale; return Offset((p.x - vp.offset.x) / u, (p.y - vp.offset.y) / u) }
    fun hit(p: Offset): Hit {
        val f = cur.value[0] as FlowDef; val g = toGraph(p); val r = max(16f, 24f * dens / (dens * vp.scale))
        for (n in f.nodes.asReversed()) {
            val spec = registry[n.type]
            val ports = if (spec?.kind == NodeKind.CONDITION) listOf(PORT_TRUE, PORT_FALSE) else if (spec != null) listOf(PORT_OUT) else emptyList()
            for (pt in ports) if ((outPort(n, pt, spec) - g).getDistance() <= r) return Hit.Port(n.id, pt)
        }
        for (n in f.nodes.asReversed()) if (g.x in n.x..(n.x + NODE_W) && g.y in n.y..(n.y + NODE_H)) return Hit.Node(n.id)
        val tol = max(10f, 18f / vp.scale)
        for (e in f.edges) {
            val a = f.node(e.from) ?: continue; val b = f.node(e.to) ?: continue
            val pa = outPort(a, e.port, registry[a.type]); val pb = inPort(b)
            for (i in 0..24) if ((bez(pa, pb, i / 24f) - g).getDistance() < tol) return Hit.Edge(e.id)
        }
        return Hit.None
    }

    Canvas(modifier.fillMaxSize()
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var h = hit(down.position)
                var moved = false; var long = false; var multi = false; var total = 0f
                val startWall = System.currentTimeMillis(); val slop = vc.touchSlop
                var dragIds: Set<String> = emptySet()
                while (true) {
                    val remaining = vc.longPressTimeoutMillis - (System.currentTimeMillis() - startWall)
                    val ev = if (!moved && !long && !multi && h is Hit.Node) withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() } else awaitPointerEvent()
                    if (ev == null) { // pulsación larga sobre un nodo: selección múltiple
                        long = true
                        val sel = cur.value[1] as Set<*>; val id = (h as Hit.Node).id
                        @Suppress("UNCHECKED_CAST") cb.onSelect(if (id in sel) (sel as Set<String>) - id else (sel as Set<String>) + id)
                        continue
                    }
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed >= 2) {
                        multi = true; moved = true; h = Hit.None; preview = null
                        val z = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid()
                        val ns = (vp.scale * z).coerceIn(0.3f, 2.5f); val f = ns / vp.scale
                        vp.offset = Offset(c.x - (c.x - vp.offset.x) * f + pan.x, c.y - (c.y - vp.offset.y) * f + pan.y); vp.scale = ns
                        ev.changes.forEach { it.consume() }; continue
                    }
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                    if (!ch.pressed) { // soltar
                        val f = cur.value[0] as FlowDef
                        if (h is Hit.Port && moved) {
                            val hp = h as Hit.Port; val t = hit(ch.position)
                            if (t is Hit.Node && t.id != hp.id) cb.onConnect(hp.id, hp.port, t.id)
                            else if (t is Hit.Port && t.id != hp.id) cb.onConnect(hp.id, hp.port, t.id)
                            else if (t is Hit.None) cb.onDropEmpty(hp.id, hp.port, toGraph(ch.position))
                            preview = null; hover = null
                        } else if (!moved && !long) {
                            val sel = cur.value[1] as Set<*>
                            when (val x = h) {
                                is Hit.Node -> { cb.onSelectEdge(null)
                                    @Suppress("UNCHECKED_CAST") cb.onSelect(if (sel.size > 1) { if (x.id in sel) (sel as Set<String>) - x.id else (sel as Set<String>) + x.id } else setOf(x.id)) }
                                is Hit.Port -> { cb.onSelectEdge(null); cb.onSelect(setOf(x.id)) }
                                is Hit.Edge -> { cb.onSelect(emptySet()); cb.onSelectEdge(x.id) }
                                Hit.None -> { cb.onSelect(emptySet()); cb.onSelectEdge(null) }
                            }
                        }
                        if (dragIds.isNotEmpty()) cb.onMoveEnd()
                        break
                    }
                    val d = ch.position - ch.previousPosition
                    total += d.getDistance()
                    if (!moved && total > slop) {
                        moved = true
                        if (h is Hit.Node) { // empezar a arrastrar: si no estaba seleccionado, se selecciona solo
                            val id = (h as Hit.Node).id; val sel = cur.value[1] as Set<*>
                            @Suppress("UNCHECKED_CAST") if (id !in sel) cb.onSelect(setOf(id))
                            @Suppress("UNCHECKED_CAST") dragIds = if (id in sel) (sel as Set<String>) else setOf(id)
                        }
                        cb.onSelectEdge(null)
                    }
                    if (moved) {
                        val u = dens * vp.scale
                        when (val x = h) {
                            is Hit.Node -> cb.onMoveBy(dragIds, d.x / u, d.y / u)
                            is Hit.Port -> { preview = Triple(x.id, x.port, toGraph(ch.position)); val t = hit(ch.position); hover = (t as? Hit.Node)?.id ?: (t as? Hit.Port)?.id }
                            else -> vp.offset += d
                        }
                        ch.consume()
                    }
                }
            }
        }
        .onSizeChanged { vp.size = it },
    ) {
        val u = dens * vp.scale
        // retícula de puntos
        val step = 32f * u
        if (step > 10f) {
            val ox = vp.offset.x.mod(step); val oy = vp.offset.y.mod(step)
            var x = ox; while (x < size.width) { var y = oy; while (y < size.height) { drawCircle(kc.ink.copy(alpha = .10f), 1.3f * dens, Offset(x, y)); y += step }; x += step }
        }
        translate(vp.offset.x, vp.offset.y) { scale(u, u, Offset.Zero) {
            val f = flow
            // conexiones: cables con holgura; se iluminan con el color del tipo de origen cuando la señal pasa por ellos
            for (e in f.edges) {
                val a = f.node(e.from) ?: continue; val b = f.node(e.to) ?: continue
                val spec = registry[a.type]
                val pa = outPort(a, e.port, spec); val pb = inPort(b)
                val sa = states[a.id]; val sb = states[b.id]
                val lit = sa == NodeState.COMPLETED && (sb == NodeState.COMPLETED || sb == NodeState.RUNNING || sb == NodeState.ERROR)
                val flowing = sb == NodeState.RUNNING || (sa == NodeState.RUNNING)
                val base = when { e.port == PORT_TRUE -> kc.ok; e.port == PORT_FALSE -> kc.error; else -> kindColor(spec?.kind ?: NodeKind.ACTION, kc) }
                val col = when { e.id == selectedEdge -> kc.signal; lit || flowing -> base; else -> kc.ink.copy(alpha = if (kc.dark) .30f else .34f) }
                val dx = max(60f, abs(pb.x - pa.x) / 2f)
                val path = Path().apply { moveTo(pa.x, pa.y); cubicTo(pa.x + dx, pa.y + 6f, pb.x - dx, pb.y + 6f, pb.x, pb.y) }
                if (lit || flowing || e.id == selectedEdge) drawPath(path, col.copy(alpha = .20f), style = Stroke(10f, cap = StrokeCap.Round))
                drawPath(path, col, style = Stroke(if (e.id == selectedEdge) 4.5f else 3.2f, cap = StrokeCap.Round))
                if (flowing) { for (j in 0..2) { val t = (pulse + j / 3f) % 1f; val c = bezPoint(pa, Offset(pa.x + dx, pa.y + 6f), Offset(pb.x - dx, pb.y + 6f), pb, t); drawCircle(col.copy(alpha = .35f), 9f, c); drawCircle(Color.White, 3.6f, c) } }
                val sib = f.outgoing(e.from).size
                if (sib > 1) {
                    val idx = f.outgoing(e.from).indexOfFirst { it.id == e.id } + 1; val m = bezPoint(pa, Offset(pa.x + dx, pa.y + 6f), Offset(pb.x - dx, pb.y + 6f), pb, .22f)
                    drawCircle(kc.raised, 10f, m); drawCircle(col, 10f, m, style = Stroke(1.6f))
                    val tl = measurer.measure("$idx", TextStyle(fontFamily = Mono, fontSize = 10.sp, color = col, fontWeight = FontWeight.Bold), density = unit)
                    drawText(tl, topLeft = Offset(m.x - tl.size.width / 2f, m.y - tl.size.height / 2f))
                }
            }
            preview?.let { (id, port, to) ->
                val n = f.node(id)
                if (n != null) {
                    val a = outPort(n, port, registry[n.type])
                    drawPath(Path().apply { moveTo(a.x, a.y); val dx = max(60f, abs(to.x - a.x) / 2f); cubicTo(a.x + dx, a.y, to.x - dx, to.y, to.x, to.y) },
                        kc.accent, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
                }
            }
            // nodos
            for (n in f.nodes) drawNode(n, registry[n.type], n.id in selected, states[n.id], issues[n.id], n.id == hover, f, kc, measurer, unit)
        } }
    }
}

private fun DrawScope.drawNode(n: NodeDef, spec: NodeSpec?, sel: Boolean, st: NodeState?, issue: Severity?, hover: Boolean, f: FlowDef, kc: Kami, tm: TextMeasurer, unit: Density) {
    val kind = spec?.kind
    val col = if (spec == null) kc.error else kindColor(kind!!, kc)
    val rect = Rect(n.x, n.y, n.x + NODE_W, n.y + NODE_H)
    val rr = CornerRadius(16f)
    val dim = st == NodeState.SKIPPED
    // halo según estado
    val ring = when { st == NodeState.RUNNING -> kc.accent; st == NodeState.ERROR -> kc.error; st == NodeState.COMPLETED -> kc.ok; else -> null }
    if (ring != null) drawRoundRect(ring.copy(alpha = .22f), Offset(rect.left - 6f, rect.top - 6f), Size(NODE_W + 12f, NODE_H + 12f), CornerRadius(22f))
    if (sel) drawRoundRect(kc.signal.copy(alpha = .25f), Offset(rect.left - 7f, rect.top - 7f), Size(NODE_W + 14f, NODE_H + 14f), CornerRadius(23f))
    drawRoundRect(if (kc.dark) kc.raised else Color.White, rect.topLeft, rect.size, rr)
    drawRoundRect(kc.high.copy(alpha = if (kc.dark) .35f else .0f), rect.topLeft, Size(NODE_W, NODE_H / 2f), rr)
    val stroke = when { ring != null -> ring; sel -> kc.signal; hover -> kc.ink; else -> kc.ink.copy(alpha = if (kc.dark) .16f else .14f) }
    drawRoundRect(stroke, rect.topLeft, rect.size, rr, style = Stroke(if (ring != null || sel) 2.6f else 1.2f, pathEffect = if (dim) PathEffect.dashPathEffect(floatArrayOf(7f, 6f)) else null))
    // chip del tipo
    val chip = Offset(n.x + 22f, n.y + 22f)
    drawCircle(col.copy(alpha = if (dim) .10f else .22f), 13f, chip)
    val cc = if (dim) kc.faint else col
    when (kind) {
        NodeKind.TRIGGER -> { val p = Path().apply { moveTo(chip.x + 1.5f, chip.y - 7f); lineTo(chip.x - 5f, chip.y + 1.5f); lineTo(chip.x - .5f, chip.y + 1.5f); lineTo(chip.x - 1.5f, chip.y + 7f); lineTo(chip.x + 5f, chip.y - 1.5f); lineTo(chip.x + .5f, chip.y - 1.5f); close() }; drawPath(p, cc) }
        NodeKind.CONDITION -> { val p = Path().apply { moveTo(chip.x, chip.y - 7f); lineTo(chip.x + 7f, chip.y); lineTo(chip.x, chip.y + 7f); lineTo(chip.x - 7f, chip.y); close() }; drawPath(p, cc, style = Stroke(2f, join = StrokeJoin.Round)) }
        NodeKind.ACTION -> drawRoundRect(cc, Offset(chip.x - 6f, chip.y - 6f), Size(12f, 12f), CornerRadius(3f), style = Stroke(2f))
        null -> { val g = tm.measure("!", TextStyle(fontFamily = Mono, fontSize = 12.sp, color = kc.error, fontWeight = FontWeight.Bold), density = unit); drawText(g, topLeft = Offset(chip.x - g.size.width / 2f, chip.y - g.size.height / 2f)) }
    }
    val title = n.label.ifBlank { spec?.title ?: n.type }
    val tw = NODE_W - 48f - 26f
    val t = tm.measure(title, TextStyle(fontFamily = Mono, fontSize = 13.sp, color = if (dim) kc.faint else kc.ink, fontWeight = FontWeight.SemiBold), overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = tw.toInt().coerceAtLeast(10)), density = unit)
    drawText(t, topLeft = Offset(n.x + 44f, n.y + 22f - t.size.height / 2f))
    val sum = if (spec != null) summarize(spec, n) else "tipo desconocido: ${n.type}"
    val s = tm.measure(sum, TextStyle(fontFamily = Mono, fontSize = 10.5.sp, color = if (dim) kc.faint else kc.muted), overflow = TextOverflow.Ellipsis, maxLines = 2,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (NODE_W - 28f).toInt().coerceAtLeast(10)), density = unit)
    drawText(s, topLeft = Offset(n.x + 14f, n.y + 40f))
    // insignia de estado / validación
    val (mk, mc) = when {
        st == NodeState.COMPLETED -> "✓" to kc.ok; st == NodeState.ERROR -> "✕" to kc.error; st == NodeState.RUNNING -> "…" to kc.accent; st == NodeState.SKIPPED -> "–" to kc.faint
        issue == Severity.ERROR -> "✕" to kc.error; issue == Severity.WARNING -> "!" to kc.warn; else -> null to kc.ink
    }
    if (mk != null) {
        val c = Offset(n.x + NODE_W - 16f, n.y + 16f)
        drawCircle(mc.copy(alpha = .2f), 10f, c)
        val m = tm.measure(mk, TextStyle(fontFamily = Mono, fontSize = 11.sp, color = mc, fontWeight = FontWeight.Bold), density = unit)
        drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
    }
    // puertos (jacks)
    if (kind != NodeKind.TRIGGER && kind != null) {
        val p = inPort(n); val has = f.edges.any { it.to == n.id }
        drawCircle(kc.paper, PORT_R + 1f, p); drawCircle(kc.muted, PORT_R, p, style = Stroke(2f)); if (has) drawCircle(kc.muted, 3.2f, p)
    }
    val ports = if (kind == NodeKind.CONDITION) listOf(PORT_TRUE, PORT_FALSE) else if (kind != null) listOf(PORT_OUT) else emptyList()
    for (pt in ports) {
        val p = outPort(n, pt, spec); val c = when (pt) { PORT_TRUE -> kc.ok; PORT_FALSE -> kc.error; else -> col }
        val has = f.edges.any { it.from == n.id && it.port == pt }
        drawCircle(kc.paper, PORT_R + 1f, p); drawCircle(c, PORT_R, p, style = Stroke(2.4f)); if (has) drawCircle(c, 3.6f, p)
        if (kind == NodeKind.CONDITION) {
            val lt = tm.measure(if (pt == PORT_TRUE) "sí" else "no", TextStyle(fontFamily = Mono, fontSize = 10.sp, color = c, fontWeight = FontWeight.SemiBold), density = unit)
            drawText(lt, topLeft = Offset(p.x - 14f - lt.size.width, p.y - lt.size.height / 2f))
        }
    }
}

/** Resumen corto de los parámetros de un nodo para mostrarlo en el lienzo. */
fun summarize(spec: NodeSpec, n: NodeDef): String {
    val parts = ArrayList<String>()
    for (p in spec.params) {
        val v = n.params[p.key] ?: p.default
        if (v.isBlank()) continue
        parts += when (p.type) {
            ParamType.CHOICE -> p.choices.firstOrNull { it.first == v }?.second ?: v
            ParamType.WEEKDAYS -> parseDays(v).sorted().joinToString("") { DAY_NAMES[it - 1].take(1) }.ifEmpty { "todos" }
            else -> v.replace('\n', ' ')
        }
    }
    return parts.joinToString(" · ").ifBlank { spec.description }
}

