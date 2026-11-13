package dev.flow.engine

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExportFile(val format: String = FlowExport.FORMAT, val version: Int = FlowExport.VERSION, val exportedAt: String, val flows: List<FlowDef>)

class ImportException(message: String) : Exception(message)
data class ImportResult(val flows: List<FlowDef>, val warnings: List<String>)

/** Formato versionado: {format:"flow-export", version:N, flows:[…]}. */
object FlowExport {
    const val FORMAT = "flow-export"
    const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun export(flows: List<FlowDef>, now: String = java.time.Instant.now().toString()): String =
        json.encodeToString(ExportFile.serializer(), ExportFile(exportedAt = now, flows = flows.map { it.copy(enabled = false) }))

    /** Importa con ids nuevos (no pisa nada) y siempre desactivado. Lanza [ImportException] si el fichero no sirve. */
    fun import(text: String, registry: NodeRegistry, newId: () -> String = { UUID.randomUUID().toString() }): ImportResult {
        val file = try { json.decodeFromString(ExportFile.serializer(), text) }
            catch (e: Exception) { throw ImportException("El fichero no tiene formato Flow válido") }
        if (file.format != FORMAT) throw ImportException("Formato desconocido: ${file.format}")
        if (file.version > VERSION) throw ImportException("Creado con una versión más nueva de Flow (formato v${file.version}); actualiza la app")
        if (file.version < 1) throw ImportException("Versión de formato no válida")
        val warnings = ArrayList<String>()
        val flowIds = file.flows.associate { it.id to newId() }
        val flows = file.flows.map { f ->
            val nodeIds = f.nodes.associate { it.id to newId() }
            val nodes = f.nodes.map { n ->
                var params = n.params
                if (n.type == "run_flow") params = params.mapValues { (k, v) -> if (k == "flow") flowIds[v] ?: v else v }
                if (n.type == "cond_result") params = params.mapValues { (k, v) -> if (k == "node") nodeIds[v] ?: v else v }
                // referencias {{node.ID}} en textos
                params = params.mapValues { (_, v) -> Regex("""\{\{\s*node\.([^{}\s]+)\s*\}\}""").replace(v) { m -> "{{node." + (nodeIds[m.groupValues[1]] ?: m.groupValues[1]) + "}}" } }
                n.copy(id = nodeIds.getValue(n.id), params = params)
            }
            val edges = f.edges.filter { it.from in nodeIds && it.to in nodeIds }.map {
                EdgeDef(newId(), nodeIds.getValue(it.from), it.port, nodeIds.getValue(it.to), it.order)
            }
            if (edges.size != f.edges.size) warnings += "«${f.name}»: se descartaron conexiones a nodos inexistentes"
            f.nodes.filter { registry[it.type] == null }.forEach { warnings += "«${f.name}»: tipo de nodo desconocido «${it.type}»" }
            f.copy(id = flowIds.getValue(f.id), nodes = nodes, edges = edges, enabled = false)
        }
        return ImportResult(flows, warnings)
    }
}
