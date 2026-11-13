package dev.flow.engine

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class FakeEnv(var now: LocalDateTime = LocalDateTime.of(2026, 11, 13, 23, 30)) : Environment {
    var battery: Int? = 50; var charging: Boolean? = false; var wifi: String? = null
    var bt: Set<String> = emptySet(); var headset: Boolean? = false
    override fun now() = now
    override fun batteryLevel() = battery
    override fun isCharging() = charging
    override fun wifiSsid() = wifi
    override fun bluetoothDevices() = bt
    override fun headsetConnected() = headset
}

class MemProvider(var flows: List<FlowDef> = emptyList()) : FlowProvider {
    override suspend fun get(id: String) = flows.firstOrNull { it.id == id }
    override suspend fun enabled() = flows.filter { it.enabled }
}

class Recorder : RunListener {
    val states = ArrayList<Triple<String, NodeState, String?>>()
    val runs = ArrayList<RunRecord>()
    override fun onNodeState(runId: String, flowId: String, nodeId: String, state: NodeState, detail: String?) { states += Triple(nodeId, state, detail) }
    override fun onRunFinished(record: RunRecord) { runs += record }
}

val logs = ArrayList<String>()

fun testRegistry(extra: (NodeRegistry) -> Unit = {}): NodeRegistry = builtinRegistry().also {
    it.register(NodeSpec.Action("echo", "Eco", "test", listOf(ParamSpec("text", "Texto", ParamType.TEXT))) { _, p -> Outcome.Success(Value.Text(p["text"].orEmpty())) })
    it.register(NodeSpec.Action("boom", "Fallo", "test") { _, _ -> Outcome.Failure("kaboom") })
    it.register(NodeSpec.Action("denied", "Denegado", "test") { _, _ -> Outcome.Unavailable("sin permiso") })
    it.register(NodeSpec.Action("slow", "Lento", "test") { _, _ -> kotlinx.coroutines.delay(60_000); Outcome.Success() })
    extra(it)
}

fun trig(id: String = "t", type: String = "trigger_manual", params: Map<String, String> = emptyMap()) = NodeDef(id, type, params = params)
fun act(id: String, type: String = "echo", params: Map<String, String> = emptyMap(), retries: Int = 0, onError: String = "stop") =
    NodeDef(id, type, params = params, retries = retries, retryDelayMs = 1, onError = onError)
fun edge(from: String, to: String, port: String = PORT_OUT, order: Int = 0) = EdgeDef("$from-$port-$to", from, port, to, order)
fun flow(id: String = "f", nodes: List<NodeDef>, edges: List<EdgeDef>, vars: List<VarDef> = emptyList(), enabled: Boolean = true) =
    FlowDef(id, "Flujo $id", enabled, nodes = nodes, edges = edges, variables = vars)

fun engine(env: FakeEnv = FakeEnv(), provider: MemProvider = MemProvider(), reg: NodeRegistry = testRegistry(), cfg: EngineConfig = EngineConfig(), store: VariableStore = MemoryVariableStore()) =
    FlowEngine(reg, env, store, provider, EventLogSink { _, _, m -> logs += m }, cfg)

val manual = TriggerEvent("manual")
