package dev.flow.app.data

import dev.flow.engine.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val paramsSer = MapSerializer(String.serializer(), String.serializer())
fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

class FlowRepository(private val db: FlowDb, private val registry: NodeRegistry) : FlowProvider, VariableStore, EventLogSink {
    private val fd = db.flows(); private val hd = db.history()

    val flows: Flow<List<FlowDef>> = combine(fd.observeFlows(), fd.observeNodes(), fd.observeEdges(), fd.observeVars()) { f, n, e, v ->
        val ns = n.groupBy { it.flowId }; val es = e.groupBy { it.flowId }; val vs = v.groupBy { it.flowId }
        f.map { assemble(it, ns[it.id].orEmpty(), es[it.id].orEmpty(), vs[it.id].orEmpty()) }
    }

    private fun assemble(f: FlowRow, n: List<NodeRow>, e: List<EdgeRow>, v: List<VarRow>) = FlowDef(
        f.id, f.name, f.enabled, f.description,
        n.map { NodeDef(it.id, it.type, it.x, it.y, Json.decodeFromString(paramsSer, it.paramsJson), it.label, it.retries, it.retryDelayMs, it.onError) },
        e.sortedBy { it.ord }.map { EdgeDef(it.id, it.fromId, it.port, it.toId, it.ord) },
        v.map { VarDef(it.name, VarType.valueOf(it.type), it.initial, it.persistent) },
        f.templateId,
    )

    override suspend fun get(id: String): FlowDef? = fd.flow(id)?.let { assemble(it, fd.nodes(id), fd.edges(id), fd.vars(id)) }
    override suspend fun enabled(): List<FlowDef> {
        val on = fd.flows().filter { it.enabled }; if (on.isEmpty()) return emptyList()
        val n = fd.allNodes().groupBy { it.flowId }; val e = fd.allEdges().groupBy { it.flowId }; val v = fd.allVars().groupBy { it.flowId }
        return on.map { assemble(it, n[it.id].orEmpty(), e[it.id].orEmpty(), v[it.id].orEmpty()) }
    }

    suspend fun save(flow: FlowDef) {
        val now = System.currentTimeMillis()
        val created = fd.flow(flow.id)?.createdAt ?: now
        fd.replace(
            FlowRow(flow.id, flow.name, flow.description, flow.enabled, flow.templateId, created, now),
            flow.nodes.map { NodeRow(it.id, flow.id, it.type, it.label, it.x, it.y, Json.encodeToString(paramsSer, it.params), it.retries, it.retryDelayMs, it.onError) },
            flow.edges.mapIndexed { i, e -> EdgeRow(e.id, flow.id, e.from, e.port, e.to, if (e.order != 0) e.order else i) },
            flow.variables.map { VarRow(flow.id, it.name, it.type.name, it.initial, it.persistent) },
        )
    }

    suspend fun delete(id: String) = fd.delete(id)
    suspend fun setEnabled(id: String, on: Boolean) = fd.setEnabled(id, on, System.currentTimeMillis())

    suspend fun duplicate(id: String): FlowDef? {
        val f = get(id) ?: return null
        val map = f.nodes.associate { it.id to uuid() }
        val copy = f.copy(
            id = uuid(), name = f.name + " (copia)", enabled = false,
            nodes = f.nodes.map { it.copy(id = map.getValue(it.id)) },
            edges = f.edges.map { EdgeDef(uuid(), map.getValue(it.from), it.port, map.getValue(it.to), it.order) },
        )
        save(copy); return copy
    }

    suspend fun exportJson(ids: List<String>): String = FlowExport.export(ids.mapNotNull { get(it) })

    /** Importa y guarda (desactivados). */
    suspend fun importJson(text: String): ImportResult {
        val r = FlowExport.import(text, registry); r.flows.forEach { save(it) }; return r
    }

    // ---- variables persistentes ----
    override suspend fun load(flowId: String, name: String) = fd.varValue(flowId, name)
    override suspend fun save(flowId: String, name: String, raw: String) = fd.putVarValue(VarValueRow(flowId, name, raw))
    fun varValues(flowId: String) = fd.observeVarValues(flowId)
    suspend fun resetVar(flowId: String, name: String) = fd.clearVarValue(flowId, name)

    // ---- historial ----
    suspend fun record(r: RunRecord) {
        hd.insertRun(RunRow(r.runId, r.flowId, r.flowName, r.startedAt.toMillis(), r.durationMs, r.status.name, r.error, r.triggerType))
        hd.insertRunNodes(r.nodes.mapIndexed { i, n -> RunNodeRow(runId = r.runId, seq = i, nodeId = n.nodeId, title = n.title, state = n.state.name,
            durationMs = n.durationMs, output = n.output?.take(500), error = n.error?.take(500), attempts = n.attempts, unavailable = n.unavailable) })
        hd.prune(KEEP_RUNS)
    }
    override suspend fun log(flowId: String, flowName: String, message: String) {
        hd.insertLog(LogRow(at = System.currentTimeMillis(), flowId = flowId, flowName = flowName, message = message.take(1000))); hd.pruneLogs(KEEP_LOGS)
    }
    fun runs(limit: Int = 200, includeRejected: Boolean = false) = if (includeRejected) hd.observeRuns(limit) else hd.observeRealRuns(limit)
    suspend fun runNodes(id: String) = hd.runNodes(id)
    fun stats(sinceMs: Long) = hd.observeStats(sinceMs)
    fun logs(limit: Int = 200) = hd.observeLogs(limit)
    suspend fun clearHistory() { hd.clearRuns(); hd.clearLogs() }

    companion object {
        const val KEEP_RUNS = 500; const val KEEP_LOGS = 500
        fun uuid() = UUID.randomUUID().toString()
    }
}
