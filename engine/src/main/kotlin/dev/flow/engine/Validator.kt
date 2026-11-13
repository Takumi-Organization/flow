package dev.flow.engine

enum class Severity { ERROR, WARNING }
data class Issue(val severity: Severity, val nodeId: String?, val message: String)

object Validator {
    fun validate(flow: FlowDef, reg: NodeRegistry): List<Issue> {
        val out = ArrayList<Issue>()
        fun err(n: String?, m: String) = out.add(Issue(Severity.ERROR, n, m))
        fun warn(n: String?, m: String) = out.add(Issue(Severity.WARNING, n, m))
        val ids = flow.nodes.map { it.id }
        if (ids.size != ids.toSet().size) err(null, "Hay nodos con identificador duplicado")
        val byId = flow.nodes.associateBy { it.id }
        val varNames = flow.variables.map { it.name }.toSet()
        if (varNames.size != flow.variables.size) err(null, "Hay variables con nombre duplicado")
        flow.variables.forEach { v ->
            if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(v.name)) err(null, "Nombre de variable no válido: '${v.name}'")
            if (Value.parse(v.type, v.initial) == null && v.initial.isNotEmpty()) err(null, "Valor inicial de '${v.name}' no es ${v.type}")
        }
        if (flow.nodes.none { reg[it.type]?.kind == NodeKind.TRIGGER }) err(null, "El flujo necesita al menos un disparador")

        for (n in flow.nodes) {
            val spec = reg[n.type]
            if (spec == null) { err(n.id, "Tipo de nodo desconocido: ${n.type}"); continue }
            val name = n.label.ifBlank { spec.title }
            for (p in spec.params) {
                val v = n.params[p.key] ?: p.default
                if (p.required && v.isBlank()) err(n.id, "$name: falta «${p.label}»")
                if (v.isNotBlank()) when (p.type) {
                    ParamType.NUMBER -> if (!hasTemplate(v) && Value.parse(VarType.NUMBER, v) == null) err(n.id, "$name: «${p.label}» debe ser un número")
                    ParamType.TIME -> if (Value.parse(VarType.TIME, v) == null) err(n.id, "$name: «${p.label}» debe ser una hora HH:mm")
                    ParamType.DATE -> if (Value.parse(VarType.DATE, v) == null) err(n.id, "$name: «${p.label}» debe ser una fecha AAAA-MM-DD")
                    ParamType.CHOICE -> if (p.choices.none { it.first == v }) err(n.id, "$name: «${p.label}» tiene un valor no válido")
                    ParamType.VARIABLE -> if (v !in varNames) err(n.id, "$name: la variable «$v» no está declarada")
                    else -> {}
                }
                // referencias {{x}} a variables inexistentes
                if (p.type == ParamType.TEXT || p.type == ParamType.MULTILINE) Template.refs(v).forEach { r ->
                    val known = r in varNames || r in setOf("now.time", "now.date", "now.weekday", "battery", "flow") ||
                        r.startsWith("trigger.") || (r.startsWith("node.") && r.removePrefix("node.") in byId)
                    if (!known) warn(n.id, "$name: «{{$r}}» no existe y quedará vacío")
                }
            }
            fun num(k: String) = n.params[k]?.let { (Value.parse(VarType.NUMBER, it) as? Value.Num)?.v }
            if (n.type == "trigger_location") {
                num("lat")?.let { if (it !in -90.0..90.0) err(n.id, "$name: la latitud debe estar entre -90 y 90") }
                num("lon")?.let { if (it !in -180.0..180.0) err(n.id, "$name: la longitud debe estar entre -180 y 180") }
                (num("radius") ?: 150.0).let { if (it !in 50.0..5000.0) err(n.id, "$name: el radio debe estar entre 50 y 5000 m") }
            }
            if (n.type == "trigger_calendar") (num("minutes") ?: 10.0).let { if (it !in 0.0..1440.0) err(n.id, "$name: los minutos deben estar entre 0 y 1440") }
            if (n.retries !in 0..5) err(n.id, "$name: reintentos fuera de rango (0–5)")
            if (spec.permissionHint.isNotBlank()) warn(n.id, "$name necesita: ${spec.permissionHint}")
            val outs = flow.edges.filter { it.from == n.id }
            val ports = if (spec.kind == NodeKind.CONDITION) setOf(PORT_TRUE, PORT_FALSE) else setOf(PORT_OUT)
            outs.filter { it.port !in ports }.forEach { err(n.id, "$name: salida «${it.port}» no válida") }
            if (spec.kind == NodeKind.CONDITION && outs.isEmpty()) warn(n.id, "$name: la condición no conduce a nada")
            if (spec.kind == NodeKind.TRIGGER && flow.edges.any { it.to == n.id }) err(n.id, "$name: un disparador no puede recibir conexiones")
            if (spec.kind != NodeKind.TRIGGER && flow.edges.none { it.to == n.id }) warn(n.id, "$name: no está conectado a ningún disparador")
        }
        for (e in flow.edges) {
            if (e.from !in byId || e.to !in byId) err(null, "Conexión a un nodo inexistente")
            if (e.from == e.to) err(e.from, "Un nodo no puede conectarse consigo mismo")
        }
        findCycle(flow)?.let { err(it, "Ciclo detectado: los flujos no pueden tener bucles") }
        flow.nodes.filter { it.type == "run_flow" && it.params["flow"] == flow.id }
            .forEach { err(it.id, "Un flujo no puede ejecutarse a sí mismo") }
        return out
    }

    fun hasErrors(issues: List<Issue>) = issues.any { it.severity == Severity.ERROR }
    private fun hasTemplate(s: String) = Template.refs(s).isNotEmpty()

    /** Devuelve un nodo que pertenece a un ciclo, o null. */
    fun findCycle(flow: FlowDef): String? {
        val adj = flow.edges.groupBy { it.from }.mapValues { e -> e.value.map { it.to } }
        val state = HashMap<String, Int>() // 1 en curso, 2 hecho
        fun dfs(n: String): String? {
            state[n] = 1
            for (m in adj[n].orEmpty()) {
                when (state[m]) { 1 -> return m; null -> dfs(m)?.let { return it } }
            }
            state[n] = 2; return null
        }
        for (n in flow.nodes) if (state[n.id] == null) dfs(n.id)?.let { return it }
        return null
    }
}
