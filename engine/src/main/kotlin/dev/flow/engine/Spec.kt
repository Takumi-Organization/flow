package dev.flow.engine

import java.time.LocalDateTime

enum class ParamType { TEXT, MULTILINE, NUMBER, BOOLEAN, TIME, DATE, CHOICE, WEEKDAYS, VARIABLE, FLOW }

data class ParamSpec(
    val key: String,
    val label: String,
    val type: ParamType = ParamType.TEXT,
    val required: Boolean = false,
    val default: String = "",
    val choices: List<Pair<String, String>> = emptyList(), // valor → etiqueta
    val help: String = "",
)

/** Evento externo (o manual) que puede disparar flujos. */
data class TriggerEvent(
    val type: String,
    val data: Map<String, String> = emptyMap(),
    val at: LocalDateTime = LocalDateTime.now(),
)

sealed interface Outcome {
    data class Success(val output: Value? = null) : Outcome
    /** Fallo recuperable (se reintenta según la configuración del nodo). */
    data class Failure(val message: String, val retryable: Boolean = true) : Outcome
    /** Android no permite la acción (permiso o restricción). No se reintenta. */
    data class Unavailable(val message: String) : Outcome
}

/** Descripción de un tipo de nodo. Añadir uno = registrar un [NodeSpec] en [NodeRegistry]. */
sealed class NodeSpec(
    val type: String,
    val kind: NodeKind,
    val title: String,
    val description: String,
    val params: List<ParamSpec> = emptyList(),
    val category: String = "",
) {
    /** Textos que el validador usa para avisar de permisos necesarios (informativo). */
    open val permissionHint: String = ""
    open val timeoutMs: Long = 60_000

    class Trigger(
        type: String, title: String, description: String, params: List<ParamSpec> = emptyList(),
        /** true si lo programa un planificador (alarma) en vez de llegar como evento. */
        val scheduled: Boolean = false,
        /** Mecanismo de ejecución, para mostrarlo al usuario. */
        val mechanism: String = "",
        override val permissionHint: String = "",
        val matches: (params: Map<String, String>, event: TriggerEvent) -> Boolean = { _, e -> e.type == type },
    ) : NodeSpec(type, NodeKind.TRIGGER, title, description, params, "Disparadores")

    class Condition(
        type: String, title: String, description: String, params: List<ParamSpec> = emptyList(),
        val evaluate: suspend (ExecContext, Map<String, String>) -> Boolean,
    ) : NodeSpec(type, NodeKind.CONDITION, title, description, params, "Condiciones")

    class Action(
        type: String, title: String, description: String, params: List<ParamSpec> = emptyList(),
        override val permissionHint: String = "",
        override val timeoutMs: Long = 60_000,
        val execute: suspend (ExecContext, Map<String, String>) -> Outcome,
    ) : NodeSpec(type, NodeKind.ACTION, title, description, params, "Acciones")
}

class NodeRegistry {
    private val specs = LinkedHashMap<String, NodeSpec>()
    fun register(spec: NodeSpec): NodeRegistry {
        require(spec.type !in specs) { "Tipo de nodo duplicado: ${spec.type}" }
        specs[spec.type] = spec; return this
    }
    fun registerAll(vararg s: NodeSpec): NodeRegistry { s.forEach(::register); return this }
    operator fun get(type: String): NodeSpec? = specs[type]
    fun all(): List<NodeSpec> = specs.values.toList()
    fun byKind(k: NodeKind) = specs.values.filter { it.kind == k }
}

/** Estado del dispositivo, abstraído para que el motor no dependa de Android. */
interface Environment {
    fun now(): LocalDateTime
    fun batteryLevel(): Int?
    fun isCharging(): Boolean?
    /** SSID conectado; "" si hay WiFi pero no se puede leer el nombre; null si no hay WiFi. */
    fun wifiSsid(): String?
    fun bluetoothDevices(): Set<String>
    fun headsetConnected(): Boolean?
}

/** Persistencia de variables marcadas como persistentes. */
interface VariableStore {
    suspend fun load(flowId: String, name: String): String?
    suspend fun save(flowId: String, name: String, raw: String)
}

class MemoryVariableStore : VariableStore {
    private val m = java.util.concurrent.ConcurrentHashMap<String, String>()
    override suspend fun load(flowId: String, name: String) = m["$flowId/$name"]
    override suspend fun save(flowId: String, name: String, raw: String) { m["$flowId/$name"] = raw }
}

fun interface EventLogSink { suspend fun log(flowId: String, flowName: String, message: String) }
