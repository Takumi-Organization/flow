package dev.flow.engine

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

enum class NodeState { PENDING, RUNNING, COMPLETED, ERROR, SKIPPED }
enum class RunStatus { SUCCESS, ERROR, CANCELLED, REJECTED }

data class NodeRecord(
    val nodeId: String, val title: String, val state: NodeState,
    val durationMs: Long = 0, val output: String? = null, val error: String? = null,
    val attempts: Int = 0, val unavailable: Boolean = false,
)

data class RunRecord(
    val runId: String, val flowId: String, val flowName: String, val startedAt: LocalDateTime,
    val durationMs: Long, val status: RunStatus, val error: String?, val triggerType: String,
    val nodes: List<NodeRecord>,
)

/** Observador de ejecuciones: historial, depuración visual, etc. Se invoca desde el hilo del motor. */
interface RunListener {
    fun onRunStarted(runId: String, flow: FlowDef, event: TriggerEvent) {}
    fun onNodeState(runId: String, flowId: String, nodeId: String, state: NodeState, detail: String? = null) {}
    fun onRunFinished(record: RunRecord) {}
}

interface FlowProvider {
    suspend fun get(id: String): FlowDef?
    suspend fun enabled(): List<FlowDef>
}

data class EngineConfig(
    val maxNodeExecutions: Int = 200,
    val maxRunMs: Long = 10 * 60_000,
    val maxDepth: Int = 3,
    /** Ventana en la que un mismo disparador del mismo flujo se considera duplicado. */
    val dedupeWindowMs: Long = 1_000,
)

class FlowEngine(
    val registry: NodeRegistry,
    val env: Environment,
    val store: VariableStore,
    val provider: FlowProvider,
    val log: EventLogSink = EventLogSink { _, _, _ -> },
    val config: EngineConfig = EngineConfig(),
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<RunListener>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val lastStart = ConcurrentHashMap<String, Long>()
    fun addListener(l: RunListener) { listeners += l }
    fun removeListener(l: RunListener) { listeners -= l }

    /** Dispara los flujos activos cuyos disparadores (no programados) encajan con [event]. */
    suspend fun dispatch(event: TriggerEvent): List<RunRecord> = coroutineScope {
        provider.enabled().flatMap { f ->
            f.nodes.filter { n ->
                val s = registry[n.type] as? NodeSpec.Trigger
                s != null && !s.scheduled && s.matches(withDefaults(s, n), event)
            }.map { n -> async { run(f, n.id, event) } }
        }.awaitAll()
    }

    /** Ejecuta un flujo desde un disparador concreto (alarma, manual, test). */
    suspend fun run(flow: FlowDef, startNodeId: String, event: TriggerEvent, depth: Int = 0, chain: List<String> = listOf(flow.id)): RunRecord {
        val runId = UUID.randomUUID().toString()
        val t0 = monotonicMs()
        val started = env.now()
        fun reject(msg: String): RunRecord {
            val r = RunRecord(runId, flow.id, flow.name, started, 0, RunStatus.REJECTED, msg, event.type, emptyList())
            listeners.forEach { it.onRunFinished(r) }; return r
        }
        val startNode = flow.node(startNodeId)
        if (startNode == null || registry[startNode.type]?.kind != NodeKind.TRIGGER) return reject("Disparador inexistente")
        val issues = Validator.validate(flow, registry)
        if (Validator.hasErrors(issues)) return reject("Flujo inválido: " + issues.first { it.severity == Severity.ERROR }.message)
        if (depth > config.maxDepth) return reject("Profundidad máxima de flujos anidados superada")
        if (event.type != "manual" && event.type != "debug") {
            val key = flow.id + "/" + startNodeId
            val prev = lastStart[key]
            if (prev != null && t0 - prev < config.dedupeWindowMs) return reject("Ejecución duplicada ignorada")
            lastStart[key] = t0
        }
        if (!running.add(flow.id)) return reject("El flujo ya se está ejecutando")
        try { return execute(flow, startNode, event, depth, chain, runId, t0, started) } finally { running.remove(flow.id) }
    }

    private fun withDefaults(spec: NodeSpec, n: NodeDef): Map<String, String> =
        spec.params.associate { it.key to (n.params[it.key] ?: it.default) }

    private suspend fun execute(
        flow: FlowDef, start: NodeDef, event: TriggerEvent, depth: Int, chain: List<String>,
        runId: String, t0: Long, started: LocalDateTime,
    ): RunRecord {
        val ctx = ExecContext(flow, event, env, store, this, depth, chain)
        ctx.init()
        val reachable = reachableFrom(flow, start.id)
        val records = LinkedHashMap<String, NodeRecord>()
        fun title(n: NodeDef) = n.label.ifBlank { registry[n.type]?.title ?: n.type }
        fun emit(n: NodeDef, st: NodeState, detail: String? = null) =
            listeners.forEach { it.onNodeState(runId, flow.id, n.id, st, detail) }
        listeners.forEach { it.onRunStarted(runId, flow, event) }
        reachable.forEach { id -> emit(flow.node(id)!!, NodeState.PENDING) }

        var status = RunStatus.SUCCESS
        var firstError: String? = null
        var current: NodeDef? = null
        var count = 0
        try {
            withTimeout(config.maxRunMs) {
                val stack = ArrayDeque<String>().apply { addLast(start.id) }
                val done = HashSet<String>()
                while (stack.isNotEmpty()) {
                    val id = stack.removeLast()
                    if (!done.add(id)) continue // un nodo se ejecuta una sola vez por ejecución
                    if (++count > config.maxNodeExecutions) {
                        status = RunStatus.ERROR; firstError = firstError ?: "Límite de nodos por ejecución superado"; break
                    }
                    val node = flow.node(id)!!
                    current = node
                    val spec = registry[node.type]!!
                    emit(node, NodeState.RUNNING)
                    val s0 = monotonicMs()
                    var port: String? = PORT_OUT
                    var rec: NodeRecord
                    when (spec) {
                        is NodeSpec.Trigger -> {
                            ctx.results[node.id] = Value.Text(event.type)
                            rec = NodeRecord(node.id, title(node), NodeState.COMPLETED, output = event.type, attempts = 1)
                        }
                        is NodeSpec.Condition -> {
                            val params = rendered(ctx, spec, node)
                            try {
                                val r = spec.evaluate(ctx, params)
                                ctx.results[node.id] = Value.Bool(r)
                                port = if (r) PORT_TRUE else PORT_FALSE
                                rec = NodeRecord(node.id, title(node), NodeState.COMPLETED, output = r.toString(), attempts = 1)
                            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                port = if (node.onError == "continue") PORT_FALSE else null
                                rec = NodeRecord(node.id, title(node), NodeState.ERROR, error = e.message ?: e.toString(), attempts = 1)
                            }
                        }
                        is NodeSpec.Action -> {
                            val (outcome, attempts) = runAction(ctx, spec, node)
                            rec = when (outcome) {
                                is Outcome.Success -> {
                                    outcome.output?.let { ctx.results[node.id] = it }
                                    NodeRecord(node.id, title(node), NodeState.COMPLETED, output = outcome.output?.display(), attempts = attempts)
                                }
                                is Outcome.Failure -> NodeRecord(node.id, title(node), NodeState.ERROR, error = outcome.message, attempts = attempts)
                                is Outcome.Unavailable -> NodeRecord(node.id, title(node), NodeState.ERROR, error = outcome.message, attempts = attempts, unavailable = true)
                            }
                            if (rec.state == NodeState.ERROR && node.onError != "continue") port = null
                        }
                    }
                    rec = rec.copy(durationMs = monotonicMs() - s0)
                    records[node.id] = rec
                    emit(node, rec.state, rec.error ?: rec.output)
                    if (rec.state == NodeState.ERROR) {
                        ctx.lastError = rec.error
                        status = RunStatus.ERROR
                        firstError = firstError ?: "${rec.title}: ${rec.error}"
                    }
                    current = null
                    if (port != null) flow.outgoing(node.id, port).reversed().forEach { stack.addLast(it.to) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            status = RunStatus.ERROR; firstError = firstError ?: "Tiempo máximo de ejecución superado"
            current?.let { records[it.id] = NodeRecord(it.id, title(it), NodeState.ERROR, error = "Tiempo agotado"); emit(it, NodeState.ERROR, "Tiempo agotado") }
        } catch (e: CancellationException) {
            current?.let { records[it.id] = NodeRecord(it.id, title(it), NodeState.ERROR, error = "Cancelado"); emit(it, NodeState.ERROR, "Cancelado") }
            finish(flow, reachable, records, ::emit, runId, started, t0, RunStatus.CANCELLED, "Ejecución cancelada", event)
            throw e
        } catch (e: Exception) {
            status = RunStatus.ERROR; firstError = firstError ?: (e.message ?: e.toString())
        }
        return finish(flow, reachable, records, ::emit, runId, started, t0, status, firstError, event)
    }

    private fun finish(
        flow: FlowDef, reachable: Set<String>, records: MutableMap<String, NodeRecord>,
        emit: (NodeDef, NodeState, String?) -> Unit, runId: String, started: LocalDateTime, t0: Long,
        status: RunStatus, error: String?, event: TriggerEvent,
    ): RunRecord {
        for (id in reachable) if (id !in records) {
            val n = flow.node(id)!!
            records[id] = NodeRecord(id, n.label.ifBlank { registry[n.type]?.title ?: n.type }, NodeState.SKIPPED)
            emit(n, NodeState.SKIPPED, null)
        }
        val r = RunRecord(runId, flow.id, flow.name, started, monotonicMs() - t0, status, error, event.type, records.values.toList())
        listeners.forEach { it.onRunFinished(r) }
        return r
    }

    private fun reachableFrom(flow: FlowDef, id: String): Set<String> {
        val seen = LinkedHashSet<String>(); val q = ArrayDeque<String>().apply { add(id) }
        while (q.isNotEmpty()) { val n = q.removeFirst(); if (seen.add(n)) flow.outgoing(n).forEach { q.add(it.to) } }
        return seen
    }

    private fun rendered(ctx: ExecContext, spec: NodeSpec, node: NodeDef): Map<String, String> =
        spec.params.associate { p -> p.key to (node.params[p.key] ?: p.default).let { v ->
            if (p.type in setOf(ParamType.VARIABLE, ParamType.FLOW, ParamType.CHOICE, ParamType.WEEKDAYS)) v else ctx.render(v)
        } }

    private suspend fun runAction(ctx: ExecContext, spec: NodeSpec.Action, node: NodeDef): Pair<Outcome, Int> {
        val params = rendered(ctx, spec, node)
        var attempt = 0
        while (true) {
            attempt++
            val outcome: Outcome = try {
                withTimeout(spec.timeoutMs) { spec.execute(ctx, params) }
            } catch (e: TimeoutCancellationException) { Outcome.Failure("Tiempo agotado (${spec.timeoutMs / 1000}s)")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { Outcome.Failure(e.message ?: e.toString()) }
            if (outcome is Outcome.Failure && outcome.retryable && attempt <= node.retries) {
                delay(minOf(node.retryDelayMs * (1L shl (attempt - 1)), 30_000)); continue
            }
            return outcome to attempt
        }
    }

    /** Usado por la acción run_flow. */
    suspend fun runNested(parent: ExecContext, targetId: String): Outcome {
        if (parent.depth + 1 > config.maxDepth) return Outcome.Failure("Profundidad máxima de flujos anidados (${config.maxDepth})", false)
        if (targetId in parent.chain) return Outcome.Failure("Llamada recursiva entre flujos bloqueada", false)
        val target = provider.get(targetId) ?: return Outcome.Failure("El flujo de destino no existe", false)
        val trig = target.nodes.firstOrNull { it.type == "trigger_manual" }
            ?: return Outcome.Failure("«${target.name}» no tiene un disparador «Acción manual»", false)
        val r = run(target, trig.id, TriggerEvent("manual", mapOf("caller" to parent.flow.name), env.now()), parent.depth + 1, parent.chain + target.id)
        return if (r.status == RunStatus.SUCCESS) Outcome.Success(Value.Text("ok"))
        else Outcome.Failure("«${target.name}»: ${r.error ?: r.status.name}", false)
    }
}
