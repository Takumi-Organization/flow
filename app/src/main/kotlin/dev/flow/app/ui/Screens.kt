@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package dev.flow.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import dev.flow.app.Graph
import dev.flow.app.R
import dev.flow.app.data.*
import dev.flow.app.platform.*
import dev.flow.engine.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

// ───────────────────────────── barra de pestañas flotante ─────────────────────────────
private val TABS = listOf("home" to "Inicio", "library" to "Presets", "history" to "Actividad", "settings" to "Ajustes")

/** Barra flotante de cristal (desenfoque real del contenido) con un selector que se desliza con muelle entre pestañas. */
@Composable
fun BottomNav(route: String, onTab: (String) -> Unit) {
    val view = LocalView.current; val kk = k; val rm = reduceMotion()
    val idx = TABS.indexOfFirst { it.first == route }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        val tabW = maxWidth / TABS.size
        val x by animateDpAsState(tabW * idx, if (rm) snap() else spring(.72f, 520f), label = "tabx")
        Box(Modifier.fillMaxWidth().height(68.dp).glass(Squircle(20.dp))) {
            Box(Modifier.offset(x = x).width(tabW).fillMaxHeight().padding(5.dp).clip(Squircle(14.dp)).background(kk.ink))
            Row(Modifier.fillMaxSize()) {
                TABS.forEachIndexed { i, (r, t) ->
                    val on = i == idx
                    val c by animateColorAsState(if (on) kk.paper else kk.muted, tween(if (rm) 0 else 200), label = "tabc")
                    val lift by animateFloatAsState(if (on) -1f else 0f, spring(.6f, 600f), label = "lift")
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(null, null, role = Role.Tab) { if (!on) { view.hap(Hap.TICK); onTab(r) } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        PIcon(r, c, 25.dp, Modifier.offset(y = (2.dp * lift)))
                        Spacer(Modifier.height(2.dp))
                        Text(t.uppercase(), style = T.label.copy(fontSize = 10.sp, letterSpacing = .8.sp), color = c)
                    }
                }
            }
        }
    }
}

/** Cabecera de pantalla: título grande, jerarquía generosa. */
@Composable
private fun Screen(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 128.dp)) {
        Spacer(Modifier.height(26.dp))
        Text(title, style = T.display, color = k.ink)
        if (sub != null) { Spacer(Modifier.height(4.dp)); Text(sub, style = T.body, color = k.muted) }
        content()
    }
}

@Composable
private fun rememberPhase(active: Boolean, ms: Int = 1700): State<Float> =
    if (active) rememberInfiniteTransition(label = "phase").animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "p")
    else remember { mutableStateOf(0f) }

@Composable
private fun StatusStrip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(Sq20).background(color.copy(alpha = .13f)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon("warn", color, 20.dp); Spacer(Modifier.width(10.dp)); Text(text, style = T.small, color = color, modifier = Modifier.weight(1f))
    }
}

// ───────────────────────────── INICIO ─────────────────────────────
@Composable
fun HomeScreen(graph: Graph, openEditor: (String) -> Unit, openRun: (String) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val view = LocalView.current; val kk = k
    val flows by graph.repo.flows.collectAsState(emptyList())
    val runs by graph.repo.runs(40).collectAsState(emptyList())
    val stats by graph.repo.stats(System.currentTimeMillis() - 7 * 86_400_000L).collectAsState(StatsRow(0, 0, 0, null))
    var newDialog by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val msg = remember { SnackbarHostState() }
    val running = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) { graph.live.collect { e -> when (e) { is dev.flow.app.LiveEvent.Started -> running[e.flowId] = true; is dev.flow.app.LiveEvent.Finished -> running[e.record.flowId] = false; else -> {} } } }
    val active = flows.filter { it.enabled }
    val now = LocalDateTime.now()
    val nextFire = active.flatMap { f -> f.nodes.mapNotNull { n -> Schedule.nextFire(n, now)?.let { it to f.name } } }.minByOrNull { it.first }
    val listenerNeeded = active.any { f -> f.nodes.any { it.type in ListenerService.TYPES } }
    val missing = remember(flows, tick) { active.flatMap { f -> f.nodes.flatMap { capsFor(it.type) } }.toSet().mapNotNull { id -> Caps.firstOrNull { it.id == id } }.filter { !it.granted(ctx) } }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5000); tick++ } }
    val anyRunning = running.values.any { it }
    val phase = rememberPhase(anyRunning)
    val recentError = runs.firstOrNull()?.status == "ERROR"
    val heroTone by animateColorAsState(when { anyRunning -> kk.accent; recentError -> kk.error; active.isNotEmpty() -> kk.accent; else -> kk.muted }, tween(600), label = "hero")
    
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 140.dp)) {
            item {
                Spacer(Modifier.height(26.dp))
                Text("Flow", style = T.display, color = kk.ink)
                Text("Cuando pasa algo, haz algo.", style = T.body, color = kk.muted)
                Spacer(Modifier.height(18.dp))
                Panel(Modifier.stagger(0), accent = heroTone) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumber(active.size, T.mega, if (active.isEmpty()) kk.faint else kk.ink, pad = 2)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.padding(bottom = 18.dp)) {
                            Text(if (active.size == 1) "flujo activo" else "flujos activos", style = T.h2, color = kk.dim)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Led(active.isNotEmpty(), heroTone, size = 12.dp, breathe = true); Spacer(Modifier.width(8.dp))
                                Text(if (active.isEmpty()) "Sin señal" else if (anyRunning) "Ejecutando" else "En línea", style = T.h1, color = heroTone)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(nextFire?.let { "Próximo · ${it.first.toLocalTime().withSecond(0).withNano(0)}${if (it.first.toLocalDate() != now.toLocalDate()) " · ${it.first.toLocalDate()}" else ""} · ${it.second}" } ?: "Sin disparadores programados", style = T.small, color = kk.dim)
                    Text((if (Scheduler.canExact(ctx)) "Alarmas exactas" else "Alarmas aproximadas") + "  ·  " + (if (listenerNeeded) "escucha encendida" else "escucha apagada"), style = T.small, color = kk.muted)
                }
                if (!Scheduler.canExact(ctx) && active.any { f -> f.nodes.any { Schedule.isScheduled(it.type) } }) StatusStrip("Concede «Alarmas exactas» para disparar a la hora exacta", kk.warn)
                missing.forEach { cap -> StatusStrip("Falta: ${cap.name}", kk.warn) }
                Spacer(Modifier.height(14.dp))
                Panel(Modifier.stagger(1)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Actividad", style = T.h2, color = kk.ink, modifier = Modifier.weight(1f)); Text("últimos 7 días", style = T.small, color = kk.muted) }
                    Scope(runs, Modifier.fillMaxWidth().height(96.dp).padding(top = 10.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Readout("${stats.total}", "ejecuciones", Modifier.weight(1.1f)); Readout("${stats.ok}", "correctas", Modifier.weight(1f), kk.ok)
                        Readout("${stats.failed}", "con error", Modifier.weight(1f), if (stats.failed > 0) kk.error else kk.faint)
                        Readout(stats.avgMs?.let { fmtMs(it.toLong()).replace(" ", "") } ?: "—", "media", Modifier.weight(.9f))
                    }
                }
                val quick = flows.filter { f -> f.nodes.any { it.type == "trigger_manual" } }
                if (quick.isNotEmpty()) {
                    Section("", "Disparo manual")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        quick.forEachIndexed { i, f -> val r = running[f.id] == true; val tone = flowTone(f.id, kk); val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            Column(Modifier.stagger(i).width(160.dp).height(118.dp).springPress(src, .94f).clip(Sq28).background(tone.copy(alpha = .14f)).border(.8.dp, tone.copy(alpha = .35f), Sq28)
                                .clickable(src, null, role = Role.Button) { view.hap(Hap.HEAVY)
                                    if (r) graph.cancel(f.id) else if (Validator.hasErrors(Validator.validate(f, graph.registry))) scope.launch { msg.showSnackbar("«${f.name}» tiene errores: ábrelo para corregirlos") } else graph.runManual(f.id) }.padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween) {
                                Box(Modifier.size(34.dp).background(tone, TkSm), contentAlignment = Alignment.Center) { PIcon(if (r) "stop" else "play", kk.paper, 18.dp) }
                                Text(f.name, style = T.h2.copy(fontSize = 16.sp), color = kk.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } }
                    }
                }
                Section("", "Módulos")
                if (flows.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        NoSignal(Modifier.padding(vertical = 12.dp))
                        Text("Nada conectado", style = T.h1, color = kk.ink)
                        Text("Un flujo es: cuando pasa algo, si se cumple una condición, entonces actúa. Crea uno desde cero o parte de un preset.", style = T.body, color = kk.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                        FButton("Crear el primer flujo", { newDialog = true }, primary = true)
                    }
                }
            }
            items(flows.sortedByDescending { it.enabled }, key = { it.id }) { f ->
                var menu by remember { mutableStateOf(false) }
                val iss = remember(f) { Validator.validate(f, graph.registry) }
                val isRun = running[f.id] == true; val tone = flowTone(f.id, kk)
                Panel(Modifier.padding(bottom = 14.dp).animateItem(fadeInSpec = spring(stiffness = 400f), placementSpec = spring(.8f, 380f), fadeOutSpec = spring(stiffness = 400f)),
                    accent = if (f.enabled) tone else null, onClick = { openEditor(f.id) }) {
                    MiniPatch(f, graph.registry, Modifier.fillMaxWidth().height(74.dp).clip(Sq20).background(kk.paper.copy(alpha = if (kk.dark) .55f else .5f)), phase = if (isRun) ({ phase.value }) else null, lit = f.enabled, tone = tone)
                    Spacer(Modifier.height(14.dp))
                    Text(f.name, style = T.h1, color = if (f.enabled) kk.ink else kk.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Led(f.enabled, tone, size = 8.dp, breathe = isRun); Spacer(Modifier.width(6.dp)); Text(if (isRun) "Ejecutando" else if (f.enabled) "Activo" else "Apagado", style = T.small, color = if (f.enabled) tone else kk.muted)
                        Text("  ·  " + f.nodes.filter { graph.registry[it.type]?.kind == NodeKind.TRIGGER }.joinToString(", ") { graph.registry[it.type]?.title ?: it.type }.ifBlank { "sin disparador" } + " · ${f.nodes.size} nodos", style = T.small, color = kk.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (Validator.hasErrors(iss)) Text("Incompleta: ${iss.first { it.severity == Severity.ERROR }.message}", style = T.small, color = kk.error, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PatchToggle(f.enabled, { on ->
                            if (on && Validator.hasErrors(iss)) { view.hap(Hap.REJECT); scope.launch { msg.showSnackbar("Corrige los errores antes de activar") } }
                            else scope.launch { graph.repo.setEnabled(f.id, on); graph.syncBackground(ctx)?.let { msg.showSnackbar(it) } } }, tone = tone)
                        Spacer(Modifier.weight(1f))
                        Box {
                            Box(Modifier.size(48.dp).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) { PIcon("more", kk.dim, 24.dp) }
                            DropdownMenu(menu, { menu = false }, containerColor = kk.raised, shape = Sq20) {
                                DropdownMenuItem({ Text("Editar", style = T.h2) }, { menu = false; openEditor(f.id) })
                                DropdownMenuItem({ Text("Duplicar", style = T.h2) }, { menu = false; scope.launch { graph.repo.duplicate(f.id) } })
                                DropdownMenuItem({ Text("Eliminar", style = T.h2, color = kk.error) }, { menu = false; scope.launch { graph.repo.delete(f.id); graph.syncBackground(ctx) } })
                            }
                        }
                    }
                }
            }
            item {
                Section("", "Últimas ejecuciones")
                if (runs.isEmpty()) Text("Todavía no se ha ejecutado nada. Pulsa un disparo manual o espera a un evento.", style = T.body, color = kk.muted)
                else Panel { runs.take(6).forEachIndexed { i, r -> RunRowItem(r, Modifier, last = i == minOf(runs.size, 6) - 1) { openRun(r.runId) } } }
            }
        }
        // botón principal
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 22.dp, bottom = 104.dp).stagger(3).springPress(src, .93f)
            .clip(TkSm)
            .background(kk.accent).clickable(src, null, role = Role.Button) { view.hap(Hap.CONFIRM); newDialog = true }
            .padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("plus", kk.onAccent, 22.dp); Spacer(Modifier.width(8.dp)); Text("Nuevo", style = T.h2, color = kk.onAccent)
        }
        PatchSnackHost(msg, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
    if (newDialog) { var name by remember { mutableStateOf("") }
        AlertDialog({ newDialog = false }, containerColor = kk.raised, shape = Sq28, title = { Text("Nuevo flujo", style = T.h1) }, text = { FField(name, { name = it }, "Nombre") },
            confirmButton = { FButton("Crear", { val f = blankFlow(name.ifBlank { "Mi flujo" }); scope.launch { graph.repo.save(f); newDialog = false; openEditor(f.id) } }, primary = true) },
            dismissButton = { FButton("Cancelar", { newDialog = false }) }) }
}

@Composable
fun PatchSnackHost(state: SnackbarHostState, modifier: Modifier = Modifier) =
    SnackbarHost(state, modifier.padding(12.dp)) { d -> Snackbar(d, shape = Sq20, containerColor = k.ink, contentColor = k.paper, actionColor = k.signal) }

fun blankFlow(name: String) = FlowDef(FlowRepository.uuid(), name, nodes = listOf(NodeDef(FlowRepository.uuid().take(8), "trigger_manual", 40f, 160f)))

/** Una ejecución: punto de estado, nombre, hora y duración. */
@Composable
fun RunRowItem(r: RunRow, modifier: Modifier = Modifier, last: Boolean = false, onClick: () -> Unit) {
    val (mk, c) = Mark(null, RunStatus.valueOf(r.status))
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(c.copy(alpha = .16f), TkSm), contentAlignment = Alignment.Center) { PIcon(if (r.status == "SUCCESS") "check" else if (r.status == "ERROR") "cross" else "more", c, 18.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.flowName, style = T.h2, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${fmt(r.startedAt.toLocal())} · ${triggerName(r.triggerType)}" + (r.error?.let { " · $it" } ?: ""), style = T.small, color = if (r.error != null) k.error else k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(fmtMs(r.durationMs), style = T.small.copy(fontFeatureSettings = "tnum"), color = k.dim)
        }
        if (!last) HorizontalDivider(color = k.line, modifier = Modifier.padding(start = 46.dp))
    }
}
fun triggerName(t: String) = when (t) { "manual" -> "manual"; "debug" -> "prueba"; "schedule" -> "programado"; "wifi" -> "WiFi"; "bluetooth" -> "Bluetooth"; "headset" -> "auriculares"; "battery" -> "batería"; "charging" -> "carga"; "calendar" -> "calendario"; "location" -> "ubicación"; else -> t }

// ───────────────────────────── PRESETS (biblioteca) ─────────────────────────────
@Composable
fun LibraryScreen(graph: Graph, openEditor: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val flows by graph.repo.flows.collectAsState(emptyList()); val kk = k
    var cat by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var noPerms by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(PAGE) }
    val cats = remember { Templates.groupingBy { it.category }.eachCount() }
    val q = query.trim().lowercase()
    val matches = remember(cat, q, noPerms) {
        Templates.filter { t ->
            (cat == null || t.category == cat) && (!noPerms || t.flow.nodes.none { capsFor(it.type).isNotEmpty() }) &&
                (q.isEmpty() || q.split(' ').all { w -> (t.title + " " + t.summary + " " + t.category + " " + t.flow.nodes.mapNotNull { graph.registry[it.type]?.title }.joinToString(" ")).lowercase().contains(w) })
        }
    }
    Screen("Presets", "${Templates.size} ejemplos reales y editables. Se cargan apagados.") {
        Spacer(Modifier.height(18.dp))
        FField(query, { query = it; shown = PAGE }, "Buscar (batería, coche, WiFi, noche…)")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Todos · ${Templates.size}", cat == null, { cat = null; shown = PAGE }); cats.forEach { (c, n) -> Chip("$c · $n", cat == c, { cat = c; shown = PAGE }) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Sin permisos especiales", noPerms, { noPerms = !noPerms; shown = PAGE })
        }
        Spacer(Modifier.height(10.dp))
        Text(if (matches.size == Templates.size) "Mostrando todos" else "${matches.size} resultado${if (matches.size == 1) "" else "s"}", style = T.small, color = kk.muted)
        Spacer(Modifier.height(6.dp))
        if (matches.isEmpty()) EmptyHint("Sin resultados", "Prueba otra palabra, quita el filtro de categoría o empieza desde cero.")
        matches.take(shown).forEachIndexed { i, t ->
            val have = flows.any { it.templateId == t.id }; val tone = flowTone(t.id, kk)
            Panel(Modifier.padding(vertical = 7.dp).stagger(i % PAGE), accent = tone) {
                Label(t.category, color = tone)
                Text(t.title, style = T.h1.copy(fontSize = 26.sp, lineHeight = 30.sp), color = kk.ink, modifier = Modifier.padding(top = 4.dp))
                MiniPatch(t.flow, graph.registry, Modifier.fillMaxWidth().height(84.dp).padding(vertical = 10.dp).clip(Sq20).background(kk.paper.copy(alpha = .5f)), lit = true, tone = tone)
                Text(t.summary, style = T.body, color = kk.dim)
                Spacer(Modifier.height(6.dp))
                Text(t.flow.nodes.mapNotNull { graph.registry[it.type]?.title }.distinct().joinToString(" · "), style = T.small, color = kk.muted)
                val miss = t.flow.nodes.flatMap { capsFor(it.type) }.toSet().mapNotNull { id -> Caps.firstOrNull { it.id == id } }
                if (miss.isNotEmpty()) Text("Permisos: " + miss.joinToString { it.name }, style = T.small, color = kk.warn, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(14.dp))
                FButton(if (have) "Cargar otra copia" else "Cargar y editar", { scope.launch { val f = instantiate(t); graph.repo.save(f); openEditor(f.id) } }, Modifier.fillMaxWidth(), primary = !have)
            }
        }
        if (matches.size > shown) {
            Spacer(Modifier.height(10.dp))
            FButton("Mostrar más (${matches.size - shown})", { shown += PAGE }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        FButton("Empezar desde cero", { scope.launch { val f = blankFlow("Mi flujo"); graph.repo.save(f); openEditor(f.id) } }, Modifier.fillMaxWidth())
    }
}

private const val PAGE = 10

fun instantiate(t: Template): FlowDef {
    val map = t.flow.nodes.associate { it.id to FlowRepository.uuid().take(8) }
    return t.flow.copy(id = FlowRepository.uuid(), enabled = false, templateId = t.id,
        nodes = t.flow.nodes.map { it.copy(id = map.getValue(it.id), params = if (it.type == "cond_result") it.params.mapValues { (kk, v) -> if (kk == "node") map[v] ?: v else v } else it.params) },
        edges = t.flow.edges.map { EdgeDef(FlowRepository.uuid().take(8), map.getValue(it.from), it.port, map.getValue(it.to), it.order) })
}

// ───────────────────────────── ACTIVIDAD (historial) ─────────────────────────────
@Composable
fun HistoryScreen(graph: Graph, openRun: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val kk = k
    val runs by graph.repo.runs(300, includeRejected = true).collectAsState(emptyList())
    val logs by graph.repo.logs(200).collectAsState(emptyList())
    var tab by remember { mutableStateOf(0) }
    Screen("Actividad", "Cada ejecución queda registrada (últimas ${FlowRepository.KEEP_RUNS}).") {
        Spacer(Modifier.height(18.dp))
        Panel(Modifier.stagger(0)) {
            Scope(runs, Modifier.fillMaxWidth().height(132.dp), max = 60)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(kk.ink)); Spacer(Modifier.width(6.dp)); Text("correcta", style = T.small, color = kk.muted) }
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).border(1.5.dp, kk.ink)); Spacer(Modifier.width(6.dp)); Text("con error", style = T.small, color = kk.muted) }
                Text("altura = duración", style = T.small, color = kk.faint, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todas", "Errores", "Rechazadas", "Eventos").forEachIndexed { i, t -> Chip(t, tab == i, { tab = i }) }
        }
        Spacer(Modifier.height(14.dp))
        when (tab) {
            3 -> { if (logs.isEmpty()) EmptyHint("Sin eventos", "Los escribe la acción «Registrar evento».") else Panel {
                logs.forEachIndexed { i, l -> Column(Modifier.padding(vertical = 10.dp)) { Text(l.message, style = T.body, color = kk.ink); Text("${fmt(l.at.toLocal())} · ${l.flowName}", style = T.small, color = kk.muted) }; if (i < logs.lastIndex) HorizontalDivider(color = kk.line) } } }
            else -> { val shown = runs.filter { when (tab) { 1 -> it.status == "ERROR" || it.status == "CANCELLED"; 2 -> it.status == "REJECTED"; else -> it.status != "REJECTED" } }.take(80)
                if (shown.isEmpty()) EmptyHint(if (tab == 1) "Sin errores" else "Sin señal", if (tab == 1) "Nada ha fallado. Así es como debe verse." else "Aún no hay ejecuciones que mostrar.")
                else Panel { shown.forEachIndexed { i, r -> RunRowItem(r, Modifier, last = i == shown.lastIndex) { openRun(r.runId) } } } }
        }
        Spacer(Modifier.height(22.dp)); FButton("Borrar historial", { scope.launch { graph.repo.clearHistory() } }, danger = true)
    }
}

@Composable
private fun EmptyHint(title: String, body: String) = Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    NoSignal(Modifier.padding(bottom = 10.dp)); Text(title, style = T.h1, color = k.ink); Text(body, style = T.body, color = k.muted, textAlign = TextAlign.Center)
}

@Composable
fun RunDetailScreen(graph: Graph, runId: String, onBack: () -> Unit) {
    var run by remember { mutableStateOf<RunRow?>(null) }; var nodes by remember { mutableStateOf<List<RunNodeRow>>(emptyList()) }
    val all by graph.repo.runs(500, true).collectAsState(emptyList()); val kk = k
    LaunchedEffect(runId, all) { run = all.firstOrNull { it.runId == runId }; nodes = graph.repo.runNodes(runId) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 40.dp)) {
        Box(Modifier.padding(top = 10.dp).size(48.dp).clip(TkSm).background(kk.high).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) { PIcon("back", kk.ink, 24.dp) }
        val r = run ?: return@Column Text("Ejecución no encontrada (puede haberse borrado).", style = T.body, color = kk.muted, modifier = Modifier.padding(top = 20.dp))
        val (_, c) = Mark(null, RunStatus.valueOf(r.status))
        Spacer(Modifier.height(18.dp))
        Text("${fmt(r.startedAt.toLocal())} · ${triggerName(r.triggerType)}", style = T.small, color = kk.muted)
        Text(r.flowName, style = T.display, color = kk.ink)
        Spacer(Modifier.height(8.dp))
        Panel(accent = c) {
            Text(statusText(r.status), style = T.hero, color = c)
            Text(fmtMs(r.durationMs), style = T.num, color = kk.ink)
            r.error?.let { Spacer(Modifier.height(10.dp)); Text(it, style = T.body, color = kk.error) }
        }
        Section("", "Recorrido")
        nodes.forEachIndexed { i, n -> val (m, col) = Mark(NodeState.valueOf(n.state))
            Row(Modifier.stagger(i).height(IntrinsicSize.Min)) {
                Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(16.dp).background(if (n.state == "SKIPPED") Color.Transparent else col, TkSm).border(2.dp, col, TkSm))
                    if (i < nodes.lastIndex) Box(Modifier.width(2.dp).weight(1f).clip(RectangleShape).background(col.copy(alpha = .45f)))
                }
                Column(Modifier.weight(1f).padding(bottom = 20.dp, start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(n.title, style = T.h2, color = if (n.state == "SKIPPED") kk.faint else kk.ink, modifier = Modifier.weight(1f)); Text("$m ${fmtMs(n.durationMs)}", style = T.small, color = col) }
                    n.output?.takeIf { it.isNotBlank() }?.let { Text("→ $it", style = T.small, color = kk.dim) }
                    n.error?.let { Text((if (n.unavailable) "No disponible: " else "Error: ") + it, style = T.small, color = if (n.unavailable) kk.warn else kk.error) }
                    if (n.attempts > 1) Text("${n.attempts} intentos", style = T.small, color = kk.muted)
                    if (n.state == "SKIPPED") Text("Omitido (rama no tomada o flujo detenido)", style = T.small, color = kk.faint)
                }
            } }
    }
}

// ───────────────────────────── AJUSTES ─────────────────────────────
/** Lee hasta [limit] bytes sin `InputStream.readNBytes` (API 33): Flow admite Android 8+. */
private fun readCapped(ins: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = ins.read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

@Composable
private fun KV(key: String, value: String, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
    Text(key, style = T.small, color = k.muted, modifier = Modifier.width(112.dp)); Text(value, style = T.small, color = k.ink, modifier = Modifier.weight(1f))
}

@Composable
fun SettingsScreen(graph: Graph, theme: String, accent: Int, onTheme: (String) -> Unit, onAccent: (Int) -> Unit, openTutorial: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val view = LocalView.current; val kk = k
    var tick by remember { mutableIntStateOf(0) }
    val request = rememberCapRequester { tick++ }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }
    val flows by graph.repo.flows.collectAsState(emptyList())
    val msg = remember { SnackbarHostState() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { val text = graph.repo.exportJson(flows.map { it.id }); ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }; msg.showSnackbar("Exportados ${flows.size} flujos (formato v${FlowExport.VERSION})") } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { val text = ctx.contentResolver.openInputStream(uri)?.use { ins -> val b = readCapped(ins, MAX_IMPORT_BYTES + 1); if (b.size > MAX_IMPORT_BYTES) throw ImportException("El fichero es demasiado grande para ser una exportación de Flow"); String(b) } ?: error("No se pudo leer el fichero")
                val r = graph.repo.importJson(text); msg.showSnackbar("Importados ${r.flows.size} flujos (desactivados)" + if (r.warnings.isNotEmpty()) " · ${r.warnings.size} avisos: ${r.warnings.first()}" else "")
            } catch (e: ImportException) { msg.showSnackbar(e.message ?: "Importación fallida") } catch (e: Exception) { msg.showSnackbar("Importación fallida: ${e.message}") } } }
    Box(Modifier.fillMaxSize()) {
        Screen("Ajustes") {
            key(tick) {
                Section("", "Permisos")
                Text("Flow solo pide lo que una acción necesita. Cada permiso es opcional.", style = T.body, color = kk.muted)
                Spacer(Modifier.height(12.dp))
                Panel {
                    Caps.forEachIndexed { i, cap -> val ok = cap.granted(ctx)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background((if (ok) kk.ok else kk.warn).copy(alpha = .16f), TkSm), contentAlignment = Alignment.Center) { PIcon(if (ok) "check" else "warn", if (ok) kk.ok else kk.warn, 18.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(cap.name, style = T.h2, color = kk.ink); Text(cap.why, style = T.small, color = kk.muted) }
                            if (!ok) { Spacer(Modifier.width(8.dp)); FButton("Conceder", { request(cap) }, Modifier.heightIn(min = 44.dp)) }
                        }
                        if (i < Caps.lastIndex) HorizontalDivider(color = kk.line) }
                }
                Section("", "Ejecución")
                Panel {
                    KV("Hora · fecha", "AlarmManager (${if (Scheduler.canExact(ctx)) "exacta" else "aproximada"}) + WorkManager")
                    KV("Bluetooth", "receptor del manifiesto")
                    KV("Ubicación", "LocationManager con receptor del manifiesto (60 s / 50 m)")
                    KV("Calendario", "alarma para el próximo evento; se relee cada hora")
                    KV("WiFi · batería", "servicio en primer plano, solo con un flujo activo que lo use")
                    KV("Carga · jack", "el mismo servicio de escucha")
                    KV("Tras reiniciar", "todo se reprograma solo")
                }
            }
            Section("", "Notificaciones")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FButton("Probar notificación", { if (notificationsBlocked(ctx) != null) scope.launch { msg.showSnackbar(notificationsBlocked(ctx)!!) } else
                    ctx.getSystemService(NotificationManager::class.java).notify(1, NotificationCompat.Builder(ctx, Channels.MESSAGES).setSmallIcon(R.drawable.ic_flow).setContentTitle("Flow").setContentText("Las notificaciones funcionan.").setAutoCancel(true).build()) })
                FButton("Canales", { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            }
            Section("", "Privacidad")
            Panel { Text("Todo se guarda solo en este móvil. Flow no declara el permiso de Internet: no puede enviar datos a ningún sitio. El calendario y la ubicación solo se leen si lo permites y una acción lo necesita.", style = T.body, color = kk.dim) }
            Section("", "Exportación e importación")
            Text("Formato versionado «flow-export» v${FlowExport.VERSION}. Al importar se crean flujos nuevos y apagados.", style = T.body, color = kk.muted)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FButton("Exportar todo", { exporter.launch("flow-export.json") }, enabled = flows.isNotEmpty())
                FButton("Importar…", { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
            Section("", "Apariencia")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("grafito" to "Noche", "washi" to "Día", "amoled" to "AMOLED", "auto" to "Sistema").forEach { (id, n) ->
                    val kt = kami(id, true, Accents[accent.coerceIn(0, Accents.lastIndex)].first); val sel = theme == id
                    val ring by animateColorAsState(if (sel) kk.signal else Color.Transparent, tween(180), label = "ring")
                    Column(Modifier.weight(1f).clickable(null, null, role = Role.RadioButton) { view.hap(Hap.TICK); onTheme(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(86.dp).clip(Sq20).background(kt.paper).border(2.dp, ring, Sq20)) {
                            Box(Modifier.padding(10.dp).size(26.dp, 8.dp).background(kt.accent))
                            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth().height(34.dp).clip(Sq14).background(kt.raised).border(.5.dp, kt.line, Sq14))
                            if (id == "auto") Box(Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight().background(kami("washi", false, kt.accent).paper))
                        }
                        Spacer(Modifier.height(8.dp)); Text(n, style = T.small, color = if (sel) kk.ink else kk.muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Movimiento: " + (if (reduceMotion()) "reducido (ajuste del sistema «Quitar animaciones»)" else "completo; se reduce solo con «Quitar animaciones» de Android") + ". Los toques llevan respuesta háptica.", style = T.small, color = kk.muted)
            Section("", "Lo que Android no permite")
            Panel {
                Text("Estas acciones no existen en Flow porque Android las bloquea para apps normales, y por eso no las simulamos:", style = T.small, color = kk.dim); Spacer(Modifier.height(8.dp))
                Text("· Activar o desactivar WiFi, Bluetooth o datos móviles (bloqueado desde Android 10)\n· Controlar el volumen interno de otras apps\n· Abrir apps desde segundo plano sin tocar (Flow muestra una notificación para abrirla)\n· Ejecutar código arbitrario o controlar otras apps", style = T.small, color = kk.ink)
            }
            Section("", "Ayuda")
            FButton("Ver el tutorial", openTutorial)
            Text("Flow 0.1.0", style = T.small, color = kk.faint, modifier = Modifier.padding(top = 22.dp))
        }
        PatchSnackHost(msg, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
}

// ───────────────────────────── BIENVENIDA ─────────────────────────────
/** Demostración animada del cableado: disparador (coral), condición (violeta) y acción (menta) que se encienden al paso de la señal. */
@Composable
private fun PatchDemo(step: Int, modifier: Modifier = Modifier) {
    val rm = reduceMotion(); val kc = k
    val tr = rememberInfiniteTransition(label = "demo")
    val p by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing)), label = "p")
    val t = if (rm) 1f else p
    Canvas(modifier) {
        val u = density; val w = size.width; val h = size.height
        val a = Offset(w * .14f, h * .5f); val b = Offset(w * .5f, h * (if (step == 2) .25f else .5f)); val b2 = Offset(w * .5f, h * .78f); val c = Offset(w * .86f, h * .5f)
        fun seg(from: Offset, to: Offset, lo: Float, hi: Float, col: Color) {
            val dx = (to.x - from.x) / 2f
            val c1 = Offset(from.x + dx, from.y); val c2 = Offset(to.x - dx, to.y)
            drawPath(Path().apply { moveTo(from.x, from.y); cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y) }, kc.ink.copy(alpha = .14f), style = Stroke(4f * u, cap = StrokeCap.Round))
            val f = ((t - lo) / (hi - lo)).coerceIn(0f, 1f)
            if (f > 0f) { val n = 32; val lit = Path(); for (i in 0..(n * f).toInt()) { val q = bez(from, c1, c2, to, i / n.toFloat()); if (i == 0) lit.moveTo(q.x, q.y) else lit.lineTo(q.x, q.y) }
                drawPath(lit, col.copy(alpha = .25f), style = Stroke(12f * u, cap = StrokeCap.Round)); drawPath(lit, col, style = Stroke(4f * u, cap = StrokeCap.Round)) }
        }
        seg(a, b, .05f, .4f, kc.trig); seg(b, c, .45f, .85f, kc.act)
        if (step == 2) seg(b, b2, .45f, .85f, kc.cond)
        val l1 = t > .05f; val l2 = t > .4f; val l3 = t > .85f
        if (l1) drawCircle(kc.trig.copy(alpha = .22f), 34f * u, a)
        drawCircle(if (l1) kc.trig else kc.high, 22f * u, a)
        if (l2) drawCircle(kc.cond.copy(alpha = .22f), 38f * u, b)
        val d = Path().apply { moveTo(b.x, b.y - 28f * u); lineTo(b.x + 28f * u, b.y); lineTo(b.x, b.y + 28f * u); lineTo(b.x - 28f * u, b.y); close() }
        drawPath(d, if (l2) kc.cond else kc.high)
        if (l3) drawCircle(kc.act.copy(alpha = .22f), 38f * u, c)
        drawRoundRect(if (l3) kc.act else kc.high, c - Offset(24f * u, 24f * u), Size(48f * u, 48f * u), androidx.compose.ui.geometry.CornerRadius(12f * u))
        if (step == 2) drawRoundRect(kc.high, b2 - Offset(24f * u, 24f * u), Size(48f * u, 48f * u), androidx.compose.ui.geometry.CornerRadius(12f * u))
    }
}

@Composable
fun Tutorial(graph: Graph, done: (String) -> Unit) {
    data class Step(val big: String, val body: String, val foot: String)
    val steps = listOf(
        Step("Cuando.\nSi.\nEntonces.", "Una automatización es una regla: cuando ocurre algo (llega una hora, conectas auriculares, baja la batería…), si se cumple una condición (opcional), entonces se ejecutan acciones.", "Cada paso es un módulo; los unes con cables."),
        Step("Cablea.", "Toca + para añadir un módulo. Arrastra desde el punto de salida de uno hasta otro para conectarlos; si lo sueltas en vacío, eliges qué módulo crear ahí. Pellizca para hacer zoom y arrastra el fondo para moverte.", "Mantén pulsado un módulo para seleccionar varios."),
        Step("Ramifica.", "Una condición tiene dos salidas, sí y no, y una salida puede ir a varios módulos: son ramas, y se ejecutan en el orden numerado. Las variables guardan datos entre módulos; úsalas con {{nombre}}.", "Los flujos no pueden tener bucles: Flow lo detecta."),
        Step("Prueba.", "Pulsa Probar y mira cómo la señal recorre el cableado: cada módulo se enciende al ejecutarse. Las acciones se ejecutan de verdad; si Android no permite una, la actividad te dice por qué.", "Todo queda en Actividad, con duración y errores."),
        Step("Sin trucos.", "Android limita lo que una app puede hacer. Flow te avisa de cada permiso que un módulo necesita y propone una alternativa cuando existe. No hay permiso de Internet: nada sale del móvil.", "Puedes concederlos ahora o más tarde en Ajustes."),
    )
    var i by remember { mutableIntStateOf(0) }
    var dir by remember { mutableIntStateOf(1) }
    val request = rememberCapRequester(); val rm = reduceMotion(); val kk = k
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { steps.forEachIndexed { n, _ ->
            val c by animateColorAsState(if (n <= i) kk.ink else kk.line, tween(220), label = "seg"); Box(Modifier.weight(1f).height(4.dp).background(c)) } }
        AnimatedContent(i, Modifier.weight(1f).fillMaxWidth(), label = "tut", transitionSpec = {
            if (rm) EnterTransition.None togetherWith ExitTransition.None
            else (slideInHorizontally(spring(.8f, 320f)) { it * dir / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(180)) { -it * dir / 4 } + fadeOut(tween(140)))
        }) { n ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(36.dp))
                Text(steps[n].big, style = T.hero.copy(fontSize = if (steps[n].big.contains('\n')) 60.sp else 76.sp, lineHeight = if (steps[n].big.contains('\n')) 62.sp else 78.sp), color = kk.ink)
                PatchDemo(minOf(n, 3), Modifier.fillMaxWidth().height(160.dp).padding(vertical = 14.dp))
                Text(steps[n].body, style = T.body.copy(fontSize = 16.sp, lineHeight = 24.sp), color = kk.dim); Spacer(Modifier.height(14.dp)); Text(steps[n].foot, style = T.small, color = kk.muted)
                if (n == steps.lastIndex) { Spacer(Modifier.height(18.dp)); FButton("Conceder notificaciones", { request(Caps.first { it.id == "notif" }) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FButton("Saltar", { done("home") })
            Spacer(Modifier.weight(1f))
            if (i > 0) FButton("Atrás", { dir = -1; i-- })
            if (i < steps.lastIndex) FButton("Siguiente", { dir = 1; i++ }, primary = true) else FButton("Empezar", { done("library") }, primary = true)
        }
    }
}
