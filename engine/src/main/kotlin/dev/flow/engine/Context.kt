package dev.flow.engine

import java.time.LocalDateTime

/** Estado mutable de UNA ejecución. */
class ExecContext(
    val flow: FlowDef,
    val event: TriggerEvent,
    val env: Environment,
    val store: VariableStore,
    val engine: FlowEngine,
    val depth: Int = 0,
    val chain: List<String> = listOf(flow.id),
) {
    val vars = LinkedHashMap<String, Value>()
    val results = LinkedHashMap<String, Value>()
    var lastError: String? = null

    suspend fun init() {
        for (v in flow.variables) {
            val raw = if (v.persistent) store.load(flow.id, v.name) ?: v.initial else v.initial
            vars[v.name] = Value.parse(v.type, raw) ?: Value.parse(v.type, v.initial) ?: defaultOf(v.type)
        }
    }

    suspend fun setVar(name: String, value: Value) {
        val d = flow.variables.firstOrNull { it.name == name } ?: error("Variable no declarada: $name")
        require(d.type == value.type) { "La variable '$name' es ${d.type}, no ${value.type}" }
        vars[name] = value
        if (d.persistent) store.save(flow.id, name, value.raw())
    }

    fun now(): LocalDateTime = env.now()

    /** Sustituye {{...}} en [text]. Lo desconocido queda vacío. */
    fun render(text: String): String = Template.render(text) { key -> resolve(key) }

    fun resolve(key: String): String? {
        val k = key.trim()
        vars[k]?.let { return it.display() }
        return when {
            k == "now.time" -> now().toLocalTime().withSecond(0).withNano(0).toString()
            k == "now.date" -> now().toLocalDate().toString()
            k == "now.weekday" -> now().dayOfWeek.value.toString()
            k == "battery" -> env.batteryLevel()?.toString()
            k == "flow" -> flow.name
            k.startsWith("trigger.") -> event.data[k.removePrefix("trigger.")]
            k.startsWith("node.") -> results[k.removePrefix("node.")]?.display()
            else -> null
        }
    }

    companion object {
        fun defaultOf(t: VarType): Value = when (t) {
            VarType.TEXT -> Value.Text(""); VarType.NUMBER -> Value.Num(0.0); VarType.BOOLEAN -> Value.Bool(false)
            VarType.DATE -> Value.Date(java.time.LocalDate.EPOCH); VarType.TIME -> Value.Time(java.time.LocalTime.MIDNIGHT)
            VarType.LIST -> Value.Items(emptyList())
        }
    }
}

object Template {
    private val re = Regex("""\{\{([^{}]+)\}\}""")
    fun render(text: String, lookup: (String) -> String?): String =
        re.replace(text) { lookup(it.groupValues[1]) ?: "" }
    /** Claves referenciadas por la plantilla. */
    fun refs(text: String): List<String> = re.findAll(text).map { it.groupValues[1].trim() }.toList()
}

object Compare {
    val ops = listOf("eq" to "es igual a", "ne" to "es distinto de", "gt" to "mayor que", "lt" to "menor que",
        "ge" to "mayor o igual que", "le" to "menor o igual que", "contains" to "contiene")

    /** Compara [left] con el literal [rawRight], interpretado según el tipo de [left]. Null = no comparable. */
    fun test(left: Value, op: String, rawRight: String): Boolean? {
        if (left is Value.Items) {
            return when (op) {
                "contains" -> rawRight in left.v
                "eq" -> left.v == (Value.parse(VarType.LIST, rawRight) as? Value.Items)?.v
                "ne" -> left.v != (Value.parse(VarType.LIST, rawRight) as? Value.Items)?.v
                "gt" -> rawRight.trim().toIntOrNull()?.let { left.v.size > it }
                "lt" -> rawRight.trim().toIntOrNull()?.let { left.v.size < it }
                "ge" -> rawRight.trim().toIntOrNull()?.let { left.v.size >= it }
                "le" -> rawRight.trim().toIntOrNull()?.let { left.v.size <= it }
                else -> null
            }
        }
        val right = Value.parse(left.type, rawRight) ?: return null
        if (op == "contains") return if (left is Value.Text && right is Value.Text) left.v.contains(right.v, ignoreCase = true) else null
        val c: Int = when {
            left is Value.Text && right is Value.Text -> left.v.compareTo(right.v)
            left is Value.Num && right is Value.Num -> left.v.compareTo(right.v)
            left is Value.Bool && right is Value.Bool -> left.v.compareTo(right.v)
            left is Value.Date && right is Value.Date -> left.v.compareTo(right.v)
            left is Value.Time && right is Value.Time -> left.v.compareTo(right.v)
            else -> return null
        }
        return when (op) {
            "eq" -> if (left is Value.Text) left.v.equals((right as Value.Text).v, true) else c == 0
            "ne" -> if (left is Value.Text) !left.v.equals((right as Value.Text).v, true) else c != 0
            "gt" -> c > 0; "lt" -> c < 0; "ge" -> c >= 0; "le" -> c <= 0
            else -> null
        }
    }

    fun ints(op: String, left: Int, right: Int): Boolean = when (op) {
        "eq" -> left == right; "ne" -> left != right; "gt" -> left > right
        "lt" -> left < right; "ge" -> left >= right; "le" -> left <= right
        else -> false
    }
}
