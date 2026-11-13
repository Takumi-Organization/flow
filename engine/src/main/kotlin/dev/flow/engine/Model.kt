package dev.flow.engine

import kotlinx.serialization.Serializable

enum class NodeKind { TRIGGER, CONDITION, ACTION }

@Serializable
data class VarDef(val name: String, val type: VarType = VarType.TEXT, val initial: String = "", val persistent: Boolean = false)

@Serializable
data class NodeDef(
    val id: String,
    val type: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val params: Map<String, String> = emptyMap(),
    val label: String = "",
    /** Reintentos adicionales ante fallos recuperables (0 = ninguno). */
    val retries: Int = 0,
    val retryDelayMs: Long = 1000,
    /** "stop" corta el flujo si el nodo falla; "continue" sigue por el puerto de salida. */
    val onError: String = "stop",
)

@Serializable
data class EdgeDef(val id: String, val from: String, val port: String = "out", val to: String, val order: Int = 0)

@Serializable
data class FlowDef(
    val id: String,
    val name: String,
    val enabled: Boolean = false,
    val description: String = "",
    val nodes: List<NodeDef> = emptyList(),
    val edges: List<EdgeDef> = emptyList(),
    val variables: List<VarDef> = emptyList(),
    val templateId: String = "",
) {
    fun node(id: String) = nodes.firstOrNull { it.id == id }
    fun outgoing(nodeId: String, port: String? = null) =
        edges.filter { it.from == nodeId && (port == null || it.port == port) }.sortedBy { it.order }
}

const val PORT_OUT = "out"
const val PORT_TRUE = "true"
const val PORT_FALSE = "false"
