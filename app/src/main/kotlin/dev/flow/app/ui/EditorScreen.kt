@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package dev.flow.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flow.app.FlowApp
import dev.flow.app.Graph
import dev.flow.app.LiveEvent
import dev.flow.app.data.FlowRepository
import dev.flow.engine.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class EditorState(initial: FlowDef, val registry: NodeRegistry, private val persist: (FlowDef) -> Unit) {
    var flow by mutableStateOf(initial); private set
    var selected by mutableStateOf<Set<String>>(emptySet())
    var selectedEdge by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    private val undo = ArrayList<FlowDef>()
    val canUndo get() = undo.isNotEmpty()

    private fun id() = FlowRepository.uuid().take(8)
    fun commit(next: FlowDef) { undo += flow; if (undo.size > 40) undo.removeAt(0); flow = next; persist(next) }
    fun undo() { if (undo.isEmpty()) return; flow = undo.removeAt(undo.lastIndex); persist(flow); selected = selected.filter { flow.node(it) != null }.toSet(); selectedEdge = null }
    fun setEnabledLocal(on: Boolean) { flow = flow.copy(enabled = on) }

    fun liveMove(ids: Set<String>, dx: Float, dy: Float) { flow = flow.copy(nodes = flow.nodes.map { if (it.id in ids) it.copy(x = it.x + dx, y = it.y + dy) else it }) }
    fun moveEnd() = persist(flow)

    fun updateNode(id: String, f: (NodeDef) -> NodeDef) = commit(flow.copy(nodes = flow.nodes.map { if (it.id == id) f(it) else it }))
    fun setParam(id: String, key: String, v: String) = updateNode(id) { it.copy(params = it.params + (key to v)) }

    fun firstFreePort(from: String): String? {
        val n = flow.node(from) ?: return null; val spec = registry[n.type] ?: return null
        return when (spec.kind) {
            NodeKind.CONDITION -> listOf(PORT_TRUE, PORT_FALSE).firstOrNull { p -> flow.edges.none { it.from == from && it.port == p } } ?: PORT_TRUE
            else -> PORT_OUT
        }
    }

    fun addNode(spec: NodeSpec, at: Offset, from: Pair<String, String>? = null) {
        val params = spec.params.filter { it.default.isNotEmpty() }.associate { it.key to it.default }
        val n = NodeDef(id(), spec.type, at.x, at.y, params)
        var f = flow.copy(nodes = flow.nodes + n)
        if (from != null && spec.kind != NodeKind.TRIGGER) f = f.copy(edges = f.edges + EdgeDef(id(), from.first, from.second, n.id, f.edges.count { it.from == from.first && it.port == from.second }))
        commit(f); selected = setOf(n.id); selectedEdge = null
    }

    fun connect(from: String, port: String, to: String) {
        val a = flow.node(from); val b = flow.node(to) ?: return
        val sb = registry[b.type]
        if (a == null || sb == null || sb.kind == NodeKind.TRIGGER) { message = "Un disparador no puede recibir conexiones"; return }
        if (from == to) { message = "Un nodo no puede conectarse consigo mismo"; return }
        if (flow.edges.any { it.from == from && it.port == port && it.to == to }) { message = "Esa conexión ya existe"; return }
        val next = flow.copy(edges = flow.edges + EdgeDef(id(), from, port, to, flow.edges.count { it.from == from && it.port == port }))
        if (Validator.findCycle(next) != null) { message = "Esa conexión crearía un bucle; los flujos no pueden repetirse en círculo"; return }
        commit(next); selectedEdge = null
    }

    fun deleteSelection() {
        if (selectedEdge != null) { commit(flow.copy(edges = flow.edges.filter { it.id != selectedEdge })); selectedEdge = null; return }
        val s = selected; if (s.isEmpty()) return
        commit(flow.copy(nodes = flow.nodes.filter { it.id !in s }, edges = flow.edges.filter { it.from !in s && it.to !in s })); selected = emptySet()
    }

    fun duplicateSelection() {
        val s = selected; if (s.isEmpty()) return
        val map = s.associateWith { id() }
        val copies = flow.nodes.filter { it.id in s }.map { it.copy(id = map.getValue(it.id), x = it.x + 36f, y = it.y + 36f) }
        val edges = flow.edges.filter { it.from in s && it.to in s }.map { EdgeDef(id(), map.getValue(it.from), it.port, map.getValue(it.to), it.order) }
        commit(flow.copy(nodes = flow.nodes + copies, edges = flow.edges + edges)); selected = map.values.toSet()
    }

    /** Reordena las conexiones salientes de un mismo puerto: el orden define cuál rama se ejecuta antes. */
    fun moveEdge(edgeId: String, dir: Int) {
        val e = flow.edges.firstOrNull { it.id == edgeId } ?: return
        val sib = flow.outgoing(e.from, e.port); val i = sib.indexOfFirst { it.id == edgeId }; val j = i + dir
        if (j !in sib.indices) return
        val order = sib.map { it.id }.toMutableList().also { java.util.Collections.swap(it, i, j) }
        commit(flow.copy(edges = flow.edges.map { ed -> order.indexOf(ed.id).takeIf { it >= 0 && ed.from == e.from && ed.port == e.port }?.let { ed.copy(order = it) } ?: ed }))
    }
    fun deleteEdge(edgeId: String) = commit(flow.copy(edges = flow.edges.filter { it.id != edgeId }))
    fun rename(name: String) = commit(flow.copy(name = name.ifBlank { flow.name }))
}

@Composable
fun EditorScreen(graph: Graph, flowId: String, onBack: () -> Unit, onOpenRun: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<EditorState?>(null) }
    LaunchedEffect(flowId) {
        val f = graph.repo.get(flowId) ?: return@LaunchedEffect onBack()
        state = EditorState(f, graph.registry) { fl -> scope.launch { graph.repo.save(fl); graph.syncBackground(ctx) } }
    }
    val st = state ?: return
    val dens = LocalDensity.current.density
    val vp = remember { Viewport() }
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(vp.size, st) { if (!fitted && vp.size.width > 0) { vp.fit(st.flow, dens, readable = true); fitted = true } }

    val issues = remember(st.flow) { Validator.validate(st.flow, graph.registry) }
    val worst = remember(issues) { issues.filter { it.nodeId != null }.groupBy { it.nodeId!! }.mapValues { (_, l) -> if (l.any { it.severity == Severity.ERROR }) Severity.ERROR else Severity.WARNING } }
    val errors = issues.count { it.severity == Severity.ERROR }; val warns = issues.count { it.severity == Severity.WARNING }

    // depuración visual en vivo
    val states = remember { mutableStateMapOf<String, NodeState>() }
    var lastRun by remember { mutableStateOf<RunRecord?>(null) }
    var running by remember { mutableStateOf(false) }
    // La ejecución real dura milisegundos: se reproduce a «velocidad de señal» para que el recorrido se pueda ver
    val signal = remember { kotlinx.coroutines.channels.Channel<LiveEvent.Node>(kotlinx.coroutines.channels.Channel.UNLIMITED) }
    val rmotion = reduceMotion()
    LaunchedEffect(flowId) { for (e in signal) { states[e.nodeId] = e.state; if (!rmotion) kotlinx.coroutines.delay(if (e.state == NodeState.RUNNING) 380L else 70L) } }
    LaunchedEffect(flowId) {
        graph.live.collect { e ->
            when (e) {
                is LiveEvent.Started -> if (e.flowId == flowId) { states.clear(); running = true; lastRun = null }
                is LiveEvent.Node -> if (e.flowId == flowId) signal.trySend(e)
                is LiveEvent.Finished -> if (e.record.flowId == flowId) { running = false; lastRun = e.record }
            }
        }
    }

    var showPalette by remember { mutableStateOf<Pair<Pair<String, String>?, Offset?>?>(null) }
    var showIssues by remember { mutableStateOf(false) }
    var showVars by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.message) { st.message?.let { snack.showSnackbar(it); st.message = null } }

    fun canEnable(): Boolean = errors == 0
    val one = st.selected.singleOrNull()?.let { st.flow.node(it) }

    Column(Modifier.fillMaxSize().background(k.paper).statusBarsPadding()) {
        // barra superior
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(TkSm).background(k.high).clickable(onClick = onBack), contentAlignment = Alignment.Center) { PIcon("back", k.ink, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { rename = true }) {
                Text(st.flow.name, style = T.h1.copy(fontSize = 22.sp), color = k.ink, maxLines = 1)
                Text(if (errors > 0) "✕ $errors errores" else if (warns > 0) "▲ $warns avisos" else "✓ flujo válido", style = T.small,
                    color = if (errors > 0) k.error else if (warns > 0) k.warn else k.ok, modifier = Modifier.clickable { showIssues = true })
            }
            PatchToggle(st.flow.enabled, tone = flowTone(flowId, k), onChange = { on ->
                if (on && !canEnable()) { st.message = "Corrige los errores antes de activar"; showIssues = true }
                else { st.setEnabledLocal(on); scope.launch { graph.repo.setEnabled(flowId, on); graph.syncBackground(ctx)?.let { st.message = it } } }
            })
            Box {
                Box(Modifier.size(48.dp).clickable { menu = true }, contentAlignment = Alignment.Center) { PIcon("more", k.ink, 24.dp) }
                DropdownMenu(menu, { menu = false }, containerColor = k.raised, shape = Sq20) {
                    DropdownMenuItem({ Text("Deshacer", style = T.body) }, { menu = false; st.undo() }, enabled = st.canUndo)
                    DropdownMenuItem({ Text("Encajar en pantalla", style = T.body) }, { menu = false; vp.fit(st.flow, dens) })
                    DropdownMenuItem({ Text("Variables…", style = T.body) }, { menu = false; showVars = true })
                    DropdownMenuItem({ Text("Validar…", style = T.body) }, { menu = false; showIssues = true })
                }
            }
        }
        // barra de ejecución / depuración
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!running) FButton("Probar", { if (errors > 0) { showIssues = true } else {
                val t = one?.takeIf { graph.registry[it.type]?.kind == NodeKind.TRIGGER }?.id
                graph.runManual(flowId, t) } }, primary = true, enabled = errors == 0)
            else FButton("Cancelar", { graph.cancel(flowId) }, danger = true)
            val r = lastRun
            Text(when { running -> "Ejecutando…"; r != null -> "${statusText(r.status.name)} · ${fmtMs(r.durationMs)}"; else -> "Las acciones se ejecutan de verdad" },
                style = T.small, color = if (r?.status == RunStatus.ERROR) k.error else k.muted, modifier = Modifier.weight(1f).clickable(enabled = r != null) { r?.let { onOpenRun(it.runId) } })
            if (r != null && !running) Text("detalle ›", style = T.small, color = k.secondary, modifier = Modifier.clickable { onOpenRun(r.runId) })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FlowCanvas(st.flow, graph.registry, vp, st.selected, st.selectedEdge, states, worst, CanvasCallbacks(
                onSelect = { st.selected = it }, onSelectEdge = { st.selectedEdge = it },
                onMoveBy = { ids, dx, dy -> st.liveMove(ids, dx, dy) }, onMoveEnd = { st.moveEnd() },
                onConnect = { a, p, b -> st.connect(a, p, b) },
                onDropEmpty = { a, p, pos -> showPalette = (a to p) to pos },
            ))
            if (st.flow.nodes.isEmpty()) Text("Toca + para añadir tu primer módulo", style = T.body, color = k.muted, modifier = Modifier.align(Alignment.Center))
            // zoom
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniBtn("+") { vp.scale = (vp.scale * 1.25f).coerceAtMost(2.5f) }
                MiniBtn("−") { vp.scale = (vp.scale / 1.25f).coerceAtLeast(0.3f) }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(60.dp).clip(TkSm).background(k.accent).clickable {
                val from = one?.takeIf { graph.registry[it.type]?.kind != null }?.let { it.id to (st.firstFreePort(it.id) ?: PORT_OUT) }
                showPalette = from to null }, contentAlignment = Alignment.Center) {
                PIcon("plus", k.onAccent, 28.dp)
            }
            SnackbarHost(snack, Modifier.align(Alignment.TopCenter))
        }
        // panel inferior
        val edge = st.selectedEdge?.let { id -> st.flow.edges.firstOrNull { it.id == id } }
        when {
            edge != null -> EdgePanel(st, edge, graph.registry)
            one != null -> NodePanel(st, one, graph, vp.centerInGraph(dens))
            st.selected.size > 1 -> Row(Modifier.fillMaxWidth().clip(Sq28).background(k.raised).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${st.selected.size} nodos", style = T.body, color = k.ink, modifier = Modifier.weight(1f))
                FButton("Duplicar", { st.duplicateSelection() }); FButton("Eliminar", { st.deleteSelection() }, danger = true); FButton("Limpiar", { st.selected = emptySet() })
            }
            else -> Text("Toca un nodo para editarlo · arrastra desde su punto de salida para conectar · mantén pulsado para selección múltiple",
                style = T.small, color = k.muted, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }

    showPalette?.let { (from, pos) ->
        NodePalette(graph.registry, { showPalette = null }) { spec ->
            val base: Offset = pos ?: from?.let { f -> st.flow.node(f.first)?.let { n ->
                val sibs = st.flow.edges.count { it.from == n.id }; Offset(n.x + NODE_W + 56f, n.y + sibs * (NODE_H + 24f)) } } ?: vp.centerInGraph(dens).let { Offset(it.x - NODE_W / 2, it.y - NODE_H / 2) }
            st.addNode(spec, base, from); showPalette = null
        }
    }
    if (showIssues) IssuesDialog(issues, st, { showIssues = false })
    if (showVars) VariablesDialog(st, graph, { showVars = false })
    if (rename) { var t by remember { mutableStateOf(st.flow.name) }
        AlertDialog({ rename = false }, confirmButton = { TextButton({ st.rename(t); rename = false }) { Text("Guardar") } }, dismissButton = { TextButton({ rename = false }) { Text("Cancelar") } },
            title = { Text("Nombre del flujo", style = T.h2) }, text = { FField(t, { t = it }, "Nombre") }, containerColor = k.raised) }
}

@Composable private fun MiniBtn(t: String, onClick: () -> Unit) = Box(Modifier.size(44.dp).clip(TkSm).background(k.raised).border(.8.dp, k.line, TkSm).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(t, style = T.h1, color = k.ink) }

@Composable
private fun EdgePanel(st: EditorState, e: EdgeDef, reg: NodeRegistry) {
    val a = st.flow.node(e.from); val b = st.flow.node(e.to)
    val sibs = st.flow.outgoing(e.from, e.port); val idx = sibs.indexOfFirst { it.id == e.id }
    Column(Modifier.fillMaxWidth().background(k.raised).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Conexión")
        Text("${a?.label?.ifBlank { null } ?: reg[a?.type ?: ""]?.title} → ${b?.label?.ifBlank { null } ?: reg[b?.type ?: ""]?.title}" + if (e.port != PORT_OUT) "  (${if (e.port == PORT_TRUE) "sí" else "no"})" else "", style = T.body, color = k.ink)
        if (sibs.size > 1) Text("Rama ${idx + 1} de ${sibs.size}: las ramas se ejecutan en este orden.", style = T.small, color = k.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sibs.size > 1) { FButton("▲ Antes", { st.moveEdge(e.id, -1) }, enabled = idx > 0); FButton("▼ Después", { st.moveEdge(e.id, 1) }, enabled = idx < sibs.lastIndex) }
            FButton("Eliminar", { st.deleteEdge(e.id); st.selectedEdge = null }, danger = true)
        }
    }
}

@Composable
private fun NodePanel(st: EditorState, n: NodeDef, graph: Graph, center: Offset) {
    val spec = graph.registry[n.type]
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val request = rememberCapRequester { tick++ }
    val flows by graph.repo.flows.collectAsState(emptyList())
    Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(Sq28).background(k.raised).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 4.dp).background(k.line.copy(alpha = .3f), TkSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(when (spec?.kind) { NodeKind.TRIGGER -> "Disparador"; NodeKind.CONDITION -> "Condición"; NodeKind.ACTION -> "Acción"; null -> "Desconocido" })
                Text(spec?.title ?: n.type, style = T.h2, color = k.ink)
            }
            FButton("Duplicar", { st.duplicateSelection() }); Spacer(Modifier.width(6.dp)); FButton("Eliminar", { st.deleteSelection() }, danger = true)
        }
        if (spec == null) { Text("Este tipo de nodo no existe en esta versión de Flow. Elimínalo para poder activar el flujo.", style = T.small, color = k.error); return@Column }
        Text(spec.description, style = T.small, color = k.dim)
        (spec as? NodeSpec.Trigger)?.takeIf { it.mechanism.isNotBlank() }?.let { Text("Cómo se ejecuta: ${it.mechanism}", style = T.small, color = k.muted) }
        if (spec.kind == NodeKind.TRIGGER && spec is NodeSpec.Trigger && spec.type == "trigger_manual") Text("Pulsa «▶ Probar» arriba para lanzarlo.", style = T.small, color = k.muted)
        // permisos
        key(tick) { capsFor(n.type).mapNotNull { id -> Caps.firstOrNull { it.id == id } }.filter { !it.granted(ctx) }.forEach { cap ->
            Row(Modifier.fillMaxWidth().clip(R4).border(1.dp, k.warn, R4).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("▲ ${cap.name}", style = T.small.copy(fontWeight = FontWeight.Bold), color = k.warn); Text(cap.why, style = T.small, color = k.dim) }
                Spacer(Modifier.width(8.dp)); FButton("Conceder", { request(cap) })
            }
        } }
        // parámetros
        spec.params.forEach { p -> ParamEditor(p, n, st, graph, flows.filter { it.id != st.flow.id }) }
        HorizontalDivider(color = k.line)
        FField(n.label, { v -> st.updateNode(n.id) { it.copy(label = v) } }, "Nombre personalizado (opcional)")
        if (spec.kind != NodeKind.TRIGGER) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reintentos si falla", style = T.body, color = k.ink, modifier = Modifier.weight(1f))
                FButton("−", { st.updateNode(n.id) { it.copy(retries = (it.retries - 1).coerceAtLeast(0)) } }); Text("  ${n.retries}  ", style = T.h2, color = k.ink)
                FButton("+", { st.updateNode(n.id) { it.copy(retries = (it.retries + 1).coerceAtMost(5)) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Continuar aunque falle", style = T.body, color = k.ink); Text(if (spec.kind == NodeKind.CONDITION) "Si falla, sigue por «no»" else "Si no, el flujo se detiene aquí", style = T.small, color = k.muted) }
                Switch(n.onError == "continue", { on -> st.updateNode(n.id) { it.copy(onError = if (on) "continue" else "stop") } })
            }
        }
        // conexiones salientes
        val outs = st.flow.outgoing(n.id)
        if (outs.isNotEmpty()) {
            Label("Salidas (orden de ejecución)")
            outs.forEach { e -> val t = st.flow.node(e.to)
                Row(Modifier.fillMaxWidth().clickable { st.selectedEdge = e.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (e.port == PORT_TRUE) "sí " else if (e.port == PORT_FALSE) "no " else "") + "→ " + (t?.label?.ifBlank { null } ?: graph.registry[t?.type ?: ""]?.title ?: "?"), style = T.small, color = k.ink, modifier = Modifier.weight(1f))
                    val sib = st.flow.outgoing(e.from, e.port)
                    if (sib.size > 1) { Text("▲", Modifier.clickable { st.moveEdge(e.id, -1) }.padding(8.dp), color = k.ink); Text("▼", Modifier.clickable { st.moveEdge(e.id, 1) }.padding(8.dp), color = k.ink) }
                    Text("✕", Modifier.clickable { st.deleteEdge(e.id) }.padding(8.dp), color = k.error)
                } }
        }
    }
}

@Composable
private fun ParamEditor(p: ParamSpec, n: NodeDef, st: EditorState, graph: Graph, otherFlows: List<FlowDef>) {
    val ctx = LocalContext.current
    val v = n.params[p.key] ?: p.default
    val set = { x: String -> st.setParam(n.id, p.key, x) }
    val vars = st.flow.variables
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            p.key == "package" -> { var pick by remember { mutableStateOf(false) }
                FField(v, set, p.label); FButton("Elegir de las apps instaladas", { pick = true }); if (pick) AppPicker({ pick = false }) { set(it); pick = false } }
            p.key == "node" -> { Label(p.label); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                st.flow.nodes.filter { it.id != n.id }.forEach { o -> Chip(o.label.ifBlank { graph.registry[o.type]?.title ?: o.type }, v == o.id, { set(o.id) }) } }
                if (st.flow.nodes.size < 2) Text("Añade otro nodo para poder elegirlo.", style = T.small, color = k.muted) }
            p.type == ParamType.TEXT || p.type == ParamType.MULTILINE -> {
                FField(v, set, p.label + if (p.required) " *" else "", singleLine = p.type == ParamType.TEXT, error = p.required && v.isBlank())
                if (p.help.isNotBlank()) Text(p.help, style = T.small, color = k.muted)
                if (graph.registry[n.type]?.kind != NodeKind.TRIGGER || true) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (vars.map { it.name } + listOf("now.time", "battery")).forEach { name -> Chip("{{$name}}", false, { set(v + "{{$name}}") }) }
                } }
            p.type == ParamType.NUMBER -> { FField(v, set, p.label + if (p.required) " *" else "", number = true, error = p.required && v.isBlank()); if (p.help.isNotBlank()) Text(p.help, style = T.small, color = k.muted) }
            p.type == ParamType.TIME -> { Label(p.label); FButton(v.ifBlank { "Elegir hora" }, {
                val t = runCatching { LocalTime.parse(v) }.getOrDefault(LocalTime.of(8, 0))
                TimePickerDialog(ctx, { _, h, m -> set("%02d:%02d".format(h, m)) }, t.hour, t.minute, true).show() }) }
            p.type == ParamType.DATE -> { Label(p.label); FButton(v.ifBlank { "Elegir fecha" }, {
                val d = runCatching { LocalDate.parse(v) }.getOrDefault(LocalDate.now())
                DatePickerDialog(ctx, { _, y, m, day -> set("%04d-%02d-%02d".format(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show() }) }
            p.type == ParamType.CHOICE -> { Label(p.label); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { p.choices.forEach { (value, lab) -> Chip(lab, v == value, { set(value) }) } } }
            p.type == ParamType.WEEKDAYS -> { Label(p.label); val days = parseDays(v)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { DAY_NAMES.forEachIndexed { i, nm ->
                    Chip(nm, (i + 1) in days, { set(((if ((i + 1) in days) days - (i + 1) else days + (i + 1)).sorted()).joinToString(",")) }) } } }
            p.type == ParamType.VARIABLE -> { Label(p.label); if (vars.isEmpty()) Text("Crea una variable en ⋮ → Variables.", style = T.small, color = k.warn)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { vars.forEach { Chip("${it.name} · ${typeName(it.type)}", v == it.name, { set(if (v == it.name && !p.required) "" else it.name) }) } } }
            p.type == ParamType.FLOW -> { Label(p.label); if (otherFlows.isEmpty()) Text("No hay otros flujos.", style = T.small, color = k.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { otherFlows.forEach { f -> Chip(f.name, v == f.id, { set(f.id) }) } }
                if (v.isNotBlank() && otherFlows.none { it.id == v }) Text("El flujo elegido ya no existe.", style = T.small, color = k.error) }
            else -> FField(v, set, p.label)
        }
        if (n.type == "trigger_location" && p.key == "lon") {
            var msg by remember { mutableStateOf<String?>(null) }
            FButton("Usar mi ubicación actual", {
                val loc = dev.flow.app.platform.LocationReceiver.lastKnown(ctx)
                if (loc == null) msg = "Sin permiso de ubicación o sin posición conocida todavía"
                else { st.updateNode(n.id) { it.copy(params = it.params + ("lat" to "%.5f".format(java.util.Locale.US, loc.first)) + ("lon" to "%.5f".format(java.util.Locale.US, loc.second))) }; msg = null }
            })
            msg?.let { Text(it, style = T.small, color = k.warn) }
        }
        // valores de condición que dependen de la variable (ayuda de tipo)
        if (n.type == "cond_variable" && p.key == "value") vars.firstOrNull { it.name == n.params["name"] }?.let { Text("Se compara como ${typeName(it.type)}.", style = T.small, color = k.muted) }
    }
}

fun typeName(t: VarType) = when (t) { VarType.TEXT -> "texto"; VarType.NUMBER -> "número"; VarType.BOOLEAN -> "sí/no"; VarType.DATE -> "fecha"; VarType.TIME -> "hora"; VarType.LIST -> "lista" }

@Composable
private fun AppPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }
    AlertDialog(onDismiss, confirmButton = { TextButton(onDismiss) { Text("Cerrar") } }, containerColor = k.raised, title = { Text("Apps instaladas", style = T.h2) }, text = {
        LazyColumnSimple(apps) { (name, pkg) -> Column(Modifier.fillMaxWidth().clickable { onPick(pkg) }.padding(vertical = 8.dp)) { Text(name, style = T.body, color = k.ink); Text(pkg, style = T.small, color = k.muted) } } })
}

@Composable
private fun <T> LazyColumnSimple(items: List<T>, row: @Composable (T) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) { items(items.size) { row(items[it]) } }
}

@Composable
private fun NodePalette(reg: NodeRegistry, onDismiss: () -> Unit, onPick: (NodeSpec) -> Unit) {
    ModalBottomSheet(onDismiss, containerColor = k.paper, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((kind, title) in listOf(NodeKind.TRIGGER to "01 // Disparadores · CUANDO ocurre", NodeKind.CONDITION to "02 // Condiciones · SI se cumple", NodeKind.ACTION to "03 // Acciones · ENTONCES")) {
                item { Label(title, Modifier.padding(top = 12.dp)) }
                items(reg.byKind(kind).size) { i -> val s = reg.byKind(kind)[i]
                    Panel(accent = kindColor(kind, k), onClick = { onPick(s) }) { Text(s.title, style = T.h2, color = k.ink); Text(s.description, style = T.small, color = k.dim)
                        if (s.permissionHint.isNotBlank()) Text("Necesita: ${s.permissionHint}", style = T.small, color = k.warn) } }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun IssuesDialog(issues: List<Issue>, st: EditorState, onDismiss: () -> Unit) {
    AlertDialog(onDismiss, confirmButton = { TextButton(onDismiss) { Text("Cerrar") } }, containerColor = k.raised, title = { Text("Validación del flujo", style = T.h2) }, text = {
        if (issues.isEmpty()) Text("✓ Sin problemas. El flujo está listo para activarse.", style = T.body, color = k.ok)
        else LazyColumnSimple(issues.sortedBy { it.severity }) { i -> Row(Modifier.fillMaxWidth().clickable { i.nodeId?.let { st.selected = setOf(it) }; onDismiss() }.padding(vertical = 6.dp)) {
            Text(if (i.severity == Severity.ERROR) "✕ " else "▲ ", color = if (i.severity == Severity.ERROR) k.error else k.warn, style = T.body); Text(i.message, style = T.small, color = k.ink) } }
    })
}

@Composable
private fun VariablesDialog(st: EditorState, graph: Graph, onDismiss: () -> Unit) {
    val values by graph.repo.varValues(st.flow.id).collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }; var type by remember { mutableStateOf(VarType.TEXT) }; var initial by remember { mutableStateOf("") }; var persist by remember { mutableStateOf(false) }
    val nameOk = Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name) && st.flow.variables.none { it.name == name }
    val initOk = initial.isEmpty() || Value.parse(type, initial) != null
    AlertDialog(onDismiss, confirmButton = { TextButton(onDismiss) { Text("Cerrar") } }, containerColor = k.raised, title = { Text("Variables del flujo", style = T.h2) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Úsalas en textos con {{nombre}}, en condiciones y con la acción «Guardar variable».", style = T.small, color = k.muted)
            st.flow.variables.forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("${v.name}  ·  ${typeName(v.type)}${if (v.persistent) "  ·  persistente" else ""}", style = T.body, color = k.ink)
                    val cur = values.firstOrNull { it.name == v.name }?.raw; Text("inicial: ${v.initial.ifEmpty { "—" }}" + if (v.persistent) "   actual: ${cur ?: "—"}" else "", style = T.small, color = k.muted) }
                if (v.persistent) Text("↺", Modifier.clickable { scope.launch { graph.repo.resetVar(st.flow.id, v.name) } }.padding(8.dp), color = k.secondary)
                Text("✕", Modifier.clickable { st.commit(st.flow.copy(variables = st.flow.variables - v)) }.padding(8.dp), color = k.error) } }
            HorizontalDivider(color = k.line)
            Label("Nueva variable")
            FField(name, { name = it }, "Nombre (letras, números, _)", error = name.isNotEmpty() && !nameOk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { VarType.entries.forEach { Chip(typeName(it), type == it, { type = it }) } }
            FField(initial, { initial = it }, "Valor inicial (opcional)" + when (type) { VarType.DATE -> " AAAA-MM-DD"; VarType.TIME -> " HH:mm"; VarType.LIST -> " a, b, c"; VarType.BOOLEAN -> " true/false"; else -> "" }, error = !initOk)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(persist, { persist = it }); Spacer(Modifier.width(8.dp)); Text("Recordar entre ejecuciones", style = T.small, color = k.ink) }
            FButton("Añadir variable", { st.commit(st.flow.copy(variables = st.flow.variables + VarDef(name, type, initial, persist))); name = ""; initial = "" }, enabled = nameOk && initOk, primary = true)
        }
    })
}
