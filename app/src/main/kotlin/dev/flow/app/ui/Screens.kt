@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package dev.flow.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import dev.flow.app.Graph
import dev.flow.app.R
import dev.flow.app.data.*
import dev.flow.app.platform.*
import dev.flow.engine.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun BottomNav(route: String, onTab: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(k.raised).border(1.dp, k.line).navigationBarsPadding()) {
        listOf("home" to "Inicio", "library" to "Biblioteca", "history" to "Historial", "settings" to "Ajustes").forEach { (r, t) ->
            val on = route == r
            Column(Modifier.weight(1f).clickable { onTab(r) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(3.dp).width(28.dp).background(if (on) k.accent else androidx.compose.ui.graphics.Color.Transparent))
                Spacer(Modifier.height(6.dp)); Text(t, style = T.small.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal), color = if (on) k.ink else k.muted)
            }
        }
    }
}

@Composable
private fun Screen(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(16.dp)); Text(title, style = T.display, color = k.ink); if (sub != null) Text(sub, style = T.small, color = k.muted)
        content()
    }
}

// ───────────────────────────── INICIO ─────────────────────────────
@Composable
fun HomeScreen(graph: Graph, openEditor: (String) -> Unit, openRun: (String) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val flows by graph.repo.flows.collectAsState(emptyList())
    val runs by graph.repo.runs(6).collectAsState(emptyList())
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

    Box(Modifier.fillMaxSize()) {
        Screen("FLOW", "CUANDO ocurre X · SI se cumple Y · ENTONCES Z") {
            Section("00", "Estado del motor")
            Panel(accent = if (missing.isEmpty()) k.ok else k.warn) {
                Text(if (active.isEmpty()) "En reposo · ningún flujo activo" else "Activo · ${active.size} ${if (active.size == 1) "flujo" else "flujos"}", style = T.h2, color = k.ink)
                Text(nextFire?.let { "Próximo: ${it.second} · ${it.first.toLocalDate().let { d -> if (d == now.toLocalDate()) "hoy" else d.toString() }} ${it.first.toLocalTime().withSecond(0).withNano(0)}" } ?: "Sin disparadores programados", style = T.small, color = k.dim)
                Text(if (Scheduler.canExact(ctx)) "Alarmas: exactas" else "Alarmas: aproximadas (concede «Alarmas exactas» en Ajustes)", style = T.small, color = if (Scheduler.canExact(ctx)) k.dim else k.warn)
                Text(if (listenerNeeded) "Servicio de escucha: encendido (hay disparadores de batería, carga o auriculares)" else "Servicio de escucha: apagado (no hace falta)", style = T.small, color = k.dim)
                missing.forEach { Text("▲ Falta: ${it.name}", style = T.small, color = k.warn) }
            }
            Section("01", "Últimos 7 días")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Total", "${stats.total}", Modifier.weight(1f)); Stat("Correctas", "${stats.ok}", Modifier.weight(1f), k.ok)
                Stat("Con error", "${stats.failed}", Modifier.weight(1f), if (stats.failed > 0) k.error else k.ink); Stat("Media", stats.avgMs?.let { fmtMs(it.toLong()) } ?: "—", Modifier.weight(1f))
            }
            val quick = flows.filter { f -> f.nodes.any { it.type == "trigger_manual" } }
            if (quick.isNotEmpty()) {
                Section("02", "Accesos rápidos")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { f -> val r = running[f.id] == true
                        FButton(if (r) "■ ${f.name}" else "▶ ${f.name}", { if (r) graph.cancel(f.id) else if (Validator.hasErrors(Validator.validate(f, graph.registry))) scope.launch { msg.showSnackbar("«${f.name}» tiene errores: ábrelo para corregirlos") } else graph.runManual(f.id) }, danger = r) }
                }
            }
            Section("03", "Automatizaciones")
            if (flows.isEmpty()) Panel { Text("Aún no tienes ninguna.", style = T.body, color = k.ink); Text("Crea una desde cero con + o parte de un ejemplo en Biblioteca.", style = T.small, color = k.muted) }
            flows.sortedByDescending { it.enabled }.forEach { f ->
                var menu by remember { mutableStateOf(false) }
                val iss = remember(f) { Validator.validate(f, graph.registry) }
                Panel(Modifier.padding(bottom = 8.dp), accent = if (f.enabled) k.accent else k.faint, onClick = { openEditor(f.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = T.h2, color = k.ink)
                            Text(f.nodes.filter { graph.registry[it.type]?.kind == NodeKind.TRIGGER }.joinToString(" · ") { graph.registry[it.type]?.title ?: it.type }.ifBlank { "sin disparador" } + " · ${f.nodes.size} nodos", style = T.small, color = k.dim)
                            if (Validator.hasErrors(iss)) Text("✕ Incompleta: ${iss.first { it.severity == Severity.ERROR }.message}", style = T.small, color = k.error)
                        }
                        Switch(f.enabled, { on ->
                            if (on && Validator.hasErrors(iss)) scope.launch { msg.showSnackbar("Corrige los errores antes de activar") }
                            else scope.launch { graph.repo.setEnabled(f.id, on); graph.syncBackground(ctx)?.let { msg.showSnackbar(it) } } })
                        Box { Text("⋮", Modifier.clickable { menu = true }.padding(10.dp), style = T.h1, color = k.ink)
                            DropdownMenu(menu, { menu = false }, containerColor = k.raised) {
                                DropdownMenuItem({ Text("Editar") }, { menu = false; openEditor(f.id) })
                                DropdownMenuItem({ Text("Duplicar") }, { menu = false; scope.launch { graph.repo.duplicate(f.id) } })
                                DropdownMenuItem({ Text("Eliminar", color = k.error) }, { menu = false; scope.launch { graph.repo.delete(f.id); graph.syncBackground(ctx) } })
                            } }
                    }
                }
            }
            Section("04", "Últimas ejecuciones")
            if (runs.isEmpty()) Text("Todavía no se ha ejecutado nada.", style = T.small, color = k.muted)
            runs.forEach { RunRowItem(it) { openRun(it.runId) } }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp).clip(R4).background(k.accent).clickable { newDialog = true }, contentAlignment = Alignment.Center) {
            Text("+", style = T.display, color = androidx.compose.ui.graphics.Color(0xFF211D1A).takeIf { k.accent.red * .2126f + k.accent.green * .7152f + k.accent.blue * .0722f > .5f } ?: androidx.compose.ui.graphics.Color(0xFFF7F2EA))
        }
        SnackbarHost(msg, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
    if (newDialog) { var name by remember { mutableStateOf("") }
        AlertDialog({ newDialog = false }, containerColor = k.raised, title = { Text("Nueva automatización", style = T.h2) }, text = { FField(name, { name = it }, "Nombre") },
            confirmButton = { TextButton({ val f = blankFlow(name.ifBlank { "Mi flujo" }); scope.launch { graph.repo.save(f); newDialog = false; openEditor(f.id) } }) { Text("Crear") } },
            dismissButton = { TextButton({ newDialog = false }) { Text("Cancelar") } }) }
}

fun blankFlow(name: String) = FlowDef(FlowRepository.uuid(), name, nodes = listOf(NodeDef(FlowRepository.uuid().take(8), "trigger_manual", 40f, 160f)))

@Composable private fun Stat(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = k.ink) =
    Column(modifier.clip(R4).border(1.dp, k.line, R4).background(k.raised).padding(10.dp)) { Text(value, style = T.h1, color = color); Label(label) }

@Composable
fun RunRowItem(r: RunRow, onClick: () -> Unit) {
    val (mk, c) = Mark(null, RunStatus.valueOf(r.status))
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(mk, style = T.h1, color = c, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) { Text(r.flowName, style = T.body, color = k.ink, maxLines = 1); Text("${fmt(r.startedAt.toLocal())} · ${triggerName(r.triggerType)}" + (r.error?.let { " · $it" } ?: ""), style = T.small, color = if (r.error != null) k.error else k.muted, maxLines = 2) }
        Text(fmtMs(r.durationMs), style = T.small, color = k.dim)
    }
    HorizontalDivider(color = k.line.copy(alpha = .4f))
}
fun triggerName(t: String) = when (t) { "manual" -> "manual"; "debug" -> "prueba"; "schedule" -> "programado"; "wifi" -> "WiFi"; "bluetooth" -> "Bluetooth"; "headset" -> "auriculares"; "battery" -> "batería"; "charging" -> "carga"; else -> t }

// ───────────────────────────── BIBLIOTECA ─────────────────────────────
@Composable
fun LibraryScreen(graph: Graph, openEditor: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val flows by graph.repo.flows.collectAsState(emptyList())
    var cat by remember { mutableStateOf<String?>(null) }
    Screen("Biblioteca", "Ejemplos reales y editables. Se añaden desactivados.") {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Todas", cat == null, { cat = null }); Templates.map { it.category }.distinct().forEach { c -> Chip(c, cat == c, { cat = c }) }
        }
        Spacer(Modifier.height(8.dp))
        Templates.filter { cat == null || it.category == cat }.forEach { t ->
            val have = flows.any { it.templateId == t.id }
            Panel(Modifier.padding(vertical = 4.dp), accent = k.gold) {
                Label(t.category); Text(t.title, style = T.h2, color = k.ink); Text(t.summary, style = T.small, color = k.dim)
                Spacer(Modifier.height(4.dp))
                Text(t.flow.nodes.mapNotNull { graph.registry[it.type]?.title }.distinct().joinToString(" · "), style = T.small, color = k.muted)
                val miss = t.flow.nodes.flatMap { capsFor(it.type) }.toSet().mapNotNull { id -> Caps.firstOrNull { it.id == id } }
                if (miss.isNotEmpty()) Text("Permisos: " + miss.joinToString { it.name }, style = T.small, color = k.warn)
                Spacer(Modifier.height(8.dp))
                FButton(if (have) "Añadir otra copia" else "Añadir y editar", { scope.launch { val f = instantiate(t); graph.repo.save(f); openEditor(f.id) } }, primary = !have)
            }
        }
        Spacer(Modifier.height(12.dp))
        FButton("Empezar desde cero", { scope.launch { val f = blankFlow("Mi flujo"); graph.repo.save(f); openEditor(f.id) } })
    }
}

fun instantiate(t: Template): FlowDef {
    val map = t.flow.nodes.associate { it.id to FlowRepository.uuid().take(8) }
    return t.flow.copy(id = FlowRepository.uuid(), enabled = false, templateId = t.id,
        nodes = t.flow.nodes.map { it.copy(id = map.getValue(it.id), params = if (it.type == "cond_result") it.params.mapValues { (kk, v) -> if (kk == "node") map[v] ?: v else v } else it.params) },
        edges = t.flow.edges.map { EdgeDef(FlowRepository.uuid().take(8), map.getValue(it.from), it.port, map.getValue(it.to), it.order) })
}

// ───────────────────────────── HISTORIAL ─────────────────────────────
@Composable
fun HistoryScreen(graph: Graph, openRun: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val runs by graph.repo.runs(300, includeRejected = true).collectAsState(emptyList())
    val logs by graph.repo.logs(200).collectAsState(emptyList())
    var tab by remember { mutableStateOf(0) }
    Screen("Historial", "Cada ejecución queda registrada (últimas ${FlowRepository.KEEP_RUNS}).") {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Todas", "Errores", "Rechazadas", "Eventos").forEachIndexed { i, t -> Chip(t, tab == i, { tab = i }) }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            3 -> { if (logs.isEmpty()) Text("Sin eventos. Los escribe la acción «Registrar evento».", style = T.small, color = k.muted)
                logs.forEach { l -> Column(Modifier.padding(vertical = 6.dp)) { Text(l.message, style = T.body, color = k.ink); Text("${fmt(l.at.toLocal())} · ${l.flowName}", style = T.small, color = k.muted) }; HorizontalDivider(color = k.line.copy(alpha = .4f)) } }
            else -> { val shown = runs.filter { when (tab) { 1 -> it.status == "ERROR" || it.status == "CANCELLED"; 2 -> it.status == "REJECTED"; else -> it.status != "REJECTED" } }
                if (shown.isEmpty()) Text("Nada que mostrar.", style = T.small, color = k.muted)
                shown.forEach { RunRowItem(it) { openRun(it.runId) } } }
        }
        Spacer(Modifier.height(16.dp)); FButton("Borrar historial", { scope.launch { graph.repo.clearHistory() } }, danger = true)
    }
}

@Composable
fun RunDetailScreen(graph: Graph, runId: String, onBack: () -> Unit) {
    var run by remember { mutableStateOf<RunRow?>(null) }; var nodes by remember { mutableStateOf<List<RunNodeRow>>(emptyList()) }
    val all by graph.repo.runs(500, true).collectAsState(emptyList())
    LaunchedEffect(runId, all) { run = all.firstOrNull { it.runId == runId }; nodes = graph.repo.runNodes(runId) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("←  Volver", style = T.body, color = k.secondary, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        val r = run ?: return@Column Text("Ejecución no encontrada (puede haberse borrado).", style = T.body, color = k.muted)
        val (mk, c) = Mark(null, RunStatus.valueOf(r.status))
        Text("$mk ${r.flowName}", style = T.h1, color = c)
        Text("${fmt(r.startedAt.toLocal())} · ${triggerName(r.triggerType)} · ${fmtMs(r.durationMs)} · ${statusText(r.status)}", style = T.small, color = k.dim)
        r.error?.let { Spacer(Modifier.height(8.dp)); Panel(accent = k.error) { Text(it, style = T.body, color = k.error) } }
        Section("01", "Nodos")
        nodes.forEach { n -> val (m, col) = Mark(NodeState.valueOf(n.state))
            Panel(Modifier.padding(bottom = 6.dp), accent = col) {
                Row { Text("$m ${n.title}", style = T.body, color = k.ink, modifier = Modifier.weight(1f)); Text(fmtMs(n.durationMs), style = T.small, color = k.dim) }
                n.output?.takeIf { it.isNotBlank() }?.let { Text("Resultado: $it", style = T.small, color = k.dim) }
                n.error?.let { Text((if (n.unavailable) "No disponible: " else "Error: ") + it, style = T.small, color = if (n.unavailable) k.warn else k.error) }
                if (n.attempts > 1) Text("${n.attempts} intentos", style = T.small, color = k.muted)
                if (n.state == "SKIPPED") Text("Omitido (rama no tomada o flujo detenido)", style = T.small, color = k.faint)
            } }
    }
}

// ───────────────────────────── AJUSTES ─────────────────────────────
@Composable
fun SettingsScreen(graph: Graph, theme: String, accent: Int, onTheme: (String) -> Unit, onAccent: (Int) -> Unit, openTutorial: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val request = rememberCapRequester { tick++ }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }
    val flows by graph.repo.flows.collectAsState(emptyList())
    val msg = remember { SnackbarHostState() }
    var exportIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { val text = graph.repo.exportJson(flows.map { it.id }); ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }; msg.showSnackbar("Exportados ${flows.size} flujos (formato v${FlowExport.VERSION})") } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: error("No se pudo leer el fichero")
                val r = graph.repo.importJson(text); msg.showSnackbar("Importados ${r.flows.size} flujos (desactivados)" + if (r.warnings.isNotEmpty()) " · ${r.warnings.size} avisos: ${r.warnings.first()}" else "")
            } catch (e: ImportException) { msg.showSnackbar(e.message ?: "Importación fallida") } catch (e: Exception) { msg.showSnackbar("Importación fallida: ${e.message}") } } }
    Box(Modifier.fillMaxSize()) {
        Screen("Ajustes") {
            key(tick) {
                Section("01", "Permisos")
                Text("Flow solo pide lo que una acción necesita. Cada permiso es opcional.", style = T.small, color = k.muted)
                Spacer(Modifier.height(6.dp))
                Caps.forEach { cap -> val ok = cap.granted(ctx)
                    Panel(Modifier.padding(bottom = 6.dp), accent = if (ok) k.ok else k.warn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text((if (ok) "✓ " else "▲ ") + cap.name, style = T.body, color = if (ok) k.ink else k.warn); Text(cap.why, style = T.small, color = k.dim) }
                            if (!ok) { Spacer(Modifier.width(8.dp)); FButton("Conceder", { request(cap) }) }
                        } } }
                Section("02", "Ejecución en segundo plano")
                Panel { Text("Cada disparador usa el mecanismo mínimo:", style = T.small, color = k.dim)
                    Text("· Hora, fecha y día: AlarmManager (${if (Scheduler.canExact(ctx)) "exacta" else "aproximada"}) + WorkManager\n· WiFi: callback de red por PendingIntent\n· Bluetooth: receptor del manifiesto\n· Batería, carga y auriculares con cable: servicio en primer plano, solo mientras haya un flujo activo que lo use\n· Tras reiniciar o cambiar la hora, todo se reprograma solo", style = T.small, color = k.ink) }
            }
            Section("03", "Notificaciones")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FButton("Probar notificación", { if (notificationsBlocked(ctx) != null) scope.launch { msg.showSnackbar(notificationsBlocked(ctx)!!) } else
                    ctx.getSystemService(NotificationManager::class.java).notify(1, NotificationCompat.Builder(ctx, Channels.MESSAGES).setSmallIcon(R.drawable.ic_flow).setContentTitle("Flow").setContentText("Las notificaciones funcionan.").setAutoCancel(true).build()) })
                FButton("Canales", { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            }
            Section("04", "Privacidad")
            Panel { Text("Todo se guarda solo en este móvil. Flow no declara el permiso de Internet: no puede enviar datos a ningún sitio. El calendario y la ubicación solo se leen si lo permites y una acción lo necesita.", style = T.small, color = k.ink) }
            Section("05", "Exportación e importación")
            Text("Formato versionado «flow-export» v${FlowExport.VERSION}. Al importar se crean flujos nuevos y desactivados.", style = T.small, color = k.muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FButton("Exportar todo", { exporter.launch("flow-export.json") }, enabled = flows.isNotEmpty())
                FButton("Importar…", { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
            Section("06", "Apariencia")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("grafito" to "Grafito (oscuro)", "washi" to "Washi (claro)", "auto" to "Según el sistema").forEach { (id, n) -> Chip(n, theme == id, { onTheme(id) }) } }
            Spacer(Modifier.height(8.dp)); Label("Acento")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Accents.forEachIndexed { i, (c, n) -> Box(Modifier.heightIn(min = 36.dp).clip(R4).border(if (accent == i) 2.dp else 1.dp, if (accent == i) k.ink else k.line, R4).clickable { onAccent(i) }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(c)); Spacer(Modifier.width(6.dp)); Text(n, style = T.small, color = k.ink) } } } }
            Section("07", "Lo que Android NO permite")
            Panel(accent = k.faint) {
                Text("Estas acciones no existen en Flow porque Android las bloquea para apps normales, y por eso no las simulamos:", style = T.small, color = k.dim); Spacer(Modifier.height(4.dp))
                Text("· Activar o desactivar WiFi, Bluetooth o datos móviles (bloqueado desde Android 10)\n· Controlar el volumen interno de otras apps\n· Abrir apps desde segundo plano sin tocar (Flow muestra una notificación para abrirla)\n· Ejecutar código arbitrario o controlar otras apps\n· Disparador de ubicación y de evento de calendario: pendientes de una versión posterior", style = T.small, color = k.ink)
            }
            Section("08", "Ayuda")
            FButton("Ver el tutorial", openTutorial)
            Text("Flow 0.1.0", style = T.small, color = k.faint, modifier = Modifier.padding(top = 16.dp))
        }
        SnackbarHost(msg, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

// ───────────────────────────── TUTORIAL ─────────────────────────────
@Composable
fun Tutorial(graph: Graph, done: (String) -> Unit) {
    val steps = listOf(
        Triple("CUANDO · SI · ENTONCES", "Una automatización es una regla.\n\nCUANDO ocurre algo (llega una hora, conectas auriculares, baja la batería…)\nSI se cumple una condición (opcional)\nENTONCES se ejecutan acciones (avisar, temporizador, guardar un dato…).", "Cada paso es un nodo; los unes con líneas."),
        Triple("El lienzo", "▶ Disparadores (azul) · ? Condiciones (dorado, con salidas sí/no) · ■ Acciones (acento).\n\nToca + para añadir un nodo. Arrastra desde el círculo de salida de un nodo hasta otro para conectarlos; si lo sueltas en vacío, eliges qué nodo crear ahí. Pellizca para hacer zoom y arrastra el fondo para moverte.", "Mantén pulsado un nodo para seleccionar varios."),
        Triple("Variables y ramas", "Una salida puede ir a varios nodos: son ramas y se ejecutan en el orden numerado (puedes reordenarlas).\n\nLas variables guardan datos entre nodos. Úsalas en textos con {{nombre}}, en condiciones y con «Guardar variable».", "Los flujos no pueden tener bucles: Flow lo detecta."),
        Triple("Probar y depurar", "Pulsa ▶ Probar: cada nodo cambia de estado en vivo (· pendiente, … ejecutando, ✓ completado, ✕ error, – omitido).\n\nLas acciones se ejecutan de verdad. Si Android no permite una, el historial te dice por qué.", "Todo queda en Historial, con duración y errores."),
        Triple("Permisos honestos", "Android limita lo que una app puede hacer. Flow te avisa de cada permiso que un nodo necesita, y propone una alternativa cuando existe (p. ej. una notificación para abrir una app).\n\nNo hay permiso de Internet: nada sale del móvil.", "Puedes concederlos ahora o más tarde en Ajustes."),
    )
    var i by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current; val request = rememberCapRequester()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Label("0${i + 1} // 0${steps.size}")
        Spacer(Modifier.height(16.dp)); Text(steps[i].first, style = T.display, color = k.ink)
        Spacer(Modifier.height(16.dp)); Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(steps[i].second, style = T.body, color = k.dim); Spacer(Modifier.height(12.dp)); Text(steps[i].third, style = T.small, color = k.muted)
            if (i == steps.lastIndex) { Spacer(Modifier.height(16.dp)); FButton("Conceder notificaciones", { request(Caps.first { it.id == "notif" }) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FButton("Saltar", { done("home") })
            Spacer(Modifier.weight(1f))
            if (i > 0) FButton("Atrás", { i-- })
            if (i < steps.lastIndex) FButton("Siguiente", { i++ }, primary = true) else FButton("Empezar", { done("library") }, primary = true)
        }
    }
}
