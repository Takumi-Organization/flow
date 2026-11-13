package dev.flow.engine

import java.time.DayOfWeek
import java.time.LocalTime

val DAY_NAMES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** "1,2,5" (ISO, 1=lunes) → conjunto. Vacío = todos los días. */
fun parseDays(s: String): Set<Int> =
    s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

private val opChoices = Compare.ops
private val cmpNum = listOf("lt" to "es menor que", "le" to "es ≤", "eq" to "es igual a", "ge" to "es ≥", "gt" to "es mayor que", "ne" to "es distinto de")
private val stateChoices = listOf("connected" to "se conecta", "disconnected" to "se desconecta")

private fun P(key: String, label: String, type: ParamType = ParamType.TEXT, required: Boolean = false,
              default: String = "", choices: List<Pair<String, String>> = emptyList(), help: String = "") =
    ParamSpec(key, label, type, required, default, choices, help)

/** Nodos que no dependen de Android. Las acciones de plataforma se registran desde la app. */
fun builtinRegistry(): NodeRegistry = NodeRegistry().registerAll(
    // ---------- Disparadores ----------
    NodeSpec.Trigger("trigger_manual", "Acción manual", "Se ejecuta cuando pulsas «Ejecutar» o desde un acceso rápido.",
        mechanism = "Directo (sin segundo plano)"),
    NodeSpec.Trigger("trigger_time", "Hora", "Cada día a una hora (opcionalmente solo algunos días).",
        listOf(P("at", "Hora", ParamType.TIME, true, "08:00"), P("days", "Días (vacío = todos)", ParamType.WEEKDAYS)),
        scheduled = true, mechanism = "AlarmManager + WorkManager"),
    NodeSpec.Trigger("trigger_weekday", "Día de la semana", "A una hora en los días elegidos.",
        listOf(P("days", "Días", ParamType.WEEKDAYS, true, "1,2,3,4,5"), P("at", "Hora", ParamType.TIME, true, "09:00")),
        scheduled = true, mechanism = "AlarmManager + WorkManager"),
    NodeSpec.Trigger("trigger_date", "Fecha", "Una fecha concreta (una vez o cada año) a una hora.",
        listOf(P("date", "Fecha", ParamType.DATE, true), P("at", "Hora", ParamType.TIME, true, "09:00"),
            P("repeat", "Repetición", ParamType.CHOICE, true, "once", listOf("once" to "Una vez", "yearly" to "Cada año"))),
        scheduled = true, mechanism = "AlarmManager + WorkManager"),
    NodeSpec.Trigger("trigger_wifi", "Conexión WiFi", "Al conectarse o desconectarse de una red WiFi.",
        listOf(P("ssid", "Nombre de red (vacío = cualquiera)", help = "Leer el nombre exige permiso de ubicación; sin él solo se detecta «cualquier WiFi»."),
            P("state", "Cuando", ParamType.CHOICE, true, "connected", stateChoices)),
        mechanism = "NetworkCallback con PendingIntent", permissionHint = "ubicación (solo para filtrar por nombre de red)") { p, e ->
        e.type == "wifi" && e.data["state"] == p["state"] &&
            (p["ssid"].isNullOrBlank() || e.data["ssid"].equals(p["ssid"], true))
    },
    NodeSpec.Trigger("trigger_bluetooth", "Bluetooth", "Al conectarse o desconectarse un dispositivo Bluetooth.",
        listOf(P("device", "Nombre del dispositivo (vacío = cualquiera)"),
            P("state", "Cuando", ParamType.CHOICE, true, "connected", stateChoices)),
        mechanism = "BroadcastReceiver en el manifiesto", permissionHint = "dispositivos cercanos (Bluetooth)") { p, e ->
        e.type == "bluetooth" && e.data["state"] == p["state"] &&
            (p["device"].isNullOrBlank() || e.data["device"].equals(p["device"], true))
    },
    NodeSpec.Trigger("trigger_headset", "Auriculares (cable)", "Al conectar o desconectar auriculares con cable/USB-C. Los Bluetooth usan el disparador Bluetooth.",
        listOf(P("state", "Cuando", ParamType.CHOICE, true, "connected", stateChoices)),
        mechanism = "Servicio de escucha (Android no permite recibirlo en segundo plano de otro modo)") { p, e ->
        e.type == "headset" && e.data["state"] == p["state"]
    },
    NodeSpec.Trigger("trigger_battery", "Nivel de batería", "Cuando la batería cruza un porcentaje.",
        listOf(P("op", "Cuando", ParamType.CHOICE, true, "lt", listOf("lt" to "baja de", "gt" to "sube de")),
            P("level", "Porcentaje", ParamType.NUMBER, true, "20")),
        mechanism = "Servicio de escucha") { p, e ->
        val lvl = e.data["level"]?.toIntOrNull(); val prev = e.data["prev"]?.toIntOrNull()
        val t = p["level"]?.toIntOrNull()
        e.type == "battery" && lvl != null && prev != null && t != null &&
            Compare.ints(p["op"] ?: "lt", lvl, t) && !Compare.ints(p["op"] ?: "lt", prev, t)
    },
    NodeSpec.Trigger("trigger_charging", "Carga", "Al conectar o desconectar el cargador.",
        listOf(P("state", "Cuando", ParamType.CHOICE, true, "connected", stateChoices)),
        mechanism = "Servicio de escucha") { p, e -> e.type == "charging" && e.data["state"] == p["state"] },

    // ---------- Condiciones ----------
    NodeSpec.Condition("cond_time", "Hora entre", "Cierto si la hora actual está en el intervalo (admite cruzar medianoche).",
        listOf(P("from", "Desde", ParamType.TIME, true, "22:00"), P("to", "Hasta", ParamType.TIME, true, "07:00"))) { c, p ->
        val from = LocalTime.parse(p["from"]); val to = LocalTime.parse(p["to"]); val t = c.now().toLocalTime()
        if (from <= to) t >= from && t < to else t >= from || t < to
    },
    NodeSpec.Condition("cond_date", "Fecha", "Compara la fecha de hoy.",
        listOf(P("op", "Hoy", ParamType.CHOICE, true, "ge", listOf("eq" to "es", "ge" to "es o es posterior a", "le" to "es o es anterior a")),
            P("date", "Fecha", ParamType.DATE, true))) { c, p ->
        Compare.test(Value.Date(c.now().toLocalDate()), p["op"]!!, p["date"]!!) ?: error("Fecha no válida")
    },
    NodeSpec.Condition("cond_weekday", "Día de la semana", "Cierto si hoy es uno de los días elegidos.",
        listOf(P("days", "Días", ParamType.WEEKDAYS, true, "6,7"))) { c, p ->
        c.now().dayOfWeek.value in parseDays(p["days"] ?: "")
    },
    NodeSpec.Condition("cond_battery", "Batería", "Compara el nivel de batería actual.",
        listOf(P("op", "Nivel", ParamType.CHOICE, true, "lt", cmpNum), P("level", "Porcentaje", ParamType.NUMBER, true, "20"))) { c, p ->
        val l = c.env.batteryLevel() ?: error("No se puede leer la batería")
        Compare.ints(p["op"]!!, l, p["level"]!!.toDouble().toInt())
    },
    NodeSpec.Condition("cond_connection", "Estado de conexión", "Comprueba WiFi, Bluetooth, auriculares o carga en este momento.",
        listOf(P("what", "Comprobar", ParamType.CHOICE, true, "wifi", listOf("wifi" to "WiFi", "bluetooth" to "Bluetooth", "headset" to "Auriculares con cable", "charging" to "Cargando")),
            P("name", "Nombre (WiFi/Bluetooth, vacío = cualquiera)"),
            P("expected", "Debe estar", ParamType.CHOICE, true, "true", listOf("true" to "conectado/activo", "false" to "desconectado/inactivo")))) { c, p ->
        val name = p["name"].orEmpty()
        val actual = when (p["what"]) {
            "wifi" -> c.env.wifiSsid().let { s -> s != null && (name.isBlank() || s.equals(name, true)) }
            "bluetooth" -> c.env.bluetoothDevices().let { d -> if (name.isBlank()) d.isNotEmpty() else d.any { it.equals(name, true) } }
            "headset" -> c.env.headsetConnected() ?: error("No se puede leer el estado de los auriculares")
            "charging" -> c.env.isCharging() ?: error("No se puede leer el estado de carga")
            else -> error("Comprobación desconocida")
        }
        actual == (p["expected"] == "true")
    },
    NodeSpec.Condition("cond_variable", "Variable", "Compara una variable con un valor.",
        listOf(P("name", "Variable", ParamType.VARIABLE, true), P("op", "Operador", ParamType.CHOICE, true, "eq", opChoices),
            P("value", "Valor"))) { c, p ->
        val v = c.vars[p["name"]] ?: error("Variable «${p["name"]}» no declarada")
        Compare.test(v, p["op"]!!, p["value"].orEmpty()) ?: error("No se puede comparar ${v.type} con «${p["value"]}»")
    },
    NodeSpec.Condition("cond_result", "Resultado de un nodo", "Compara lo que devolvió un nodo anterior (texto/número/booleano).",
        listOf(P("node", "Id del nodo", required = true, help = "Elige el nodo en el panel."), P("op", "Operador", ParamType.CHOICE, true, "eq", opChoices),
            P("value", "Valor"))) { c, p ->
        val v = c.results[p["node"]] ?: error("El nodo «${p["node"]}» aún no tiene resultado")
        Compare.test(v, p["op"]!!, p["value"].orEmpty()) ?: error("No se puede comparar ${v.type} con «${p["value"]}»")
    },

    // ---------- Acciones independientes de la plataforma ----------
    NodeSpec.Action("delay", "Esperar", "Pausa el flujo (máx. 5 min).",
        listOf(P("seconds", "Segundos", ParamType.NUMBER, true, "5")), timeoutMs = 6 * 60_000) { _, p ->
        val s = p["seconds"]!!.toDouble()
        if (s < 0 || s > 300) Outcome.Failure("Espera fuera de rango (0–300 s)", false)
        else { kotlinx.coroutines.delay((s * 1000).toLong()); Outcome.Success() }
    },
    NodeSpec.Action("set_variable", "Guardar variable", "Cambia una variable del flujo (y la guarda si es persistente).",
        listOf(P("name", "Variable", ParamType.VARIABLE, true),
            P("mode", "Operación", ParamType.CHOICE, true, "set", listOf("set" to "Asignar", "add" to "Sumar (número)", "append" to "Añadir a lista", "toggle" to "Invertir (booleano)")),
            P("value", "Valor"))) { c, p ->
        val name = p["name"]!!; val cur = c.vars[name] ?: return@Action Outcome.Failure("Variable «$name» no declarada", false)
        val raw = p["value"].orEmpty()
        val next: Value? = when (p["mode"]) {
            "set" -> Value.parse(cur.type, raw)
            "add" -> (cur as? Value.Num)?.let { n -> Value.parse(VarType.NUMBER, raw)?.let { Value.Num(n.v + (it as Value.Num).v) } }
            "append" -> (cur as? Value.Items)?.let { Value.Items(it.v + raw) }
            "toggle" -> (cur as? Value.Bool)?.let { Value.Bool(!it.v) }
            else -> null
        }
        if (next == null) Outcome.Failure("«$raw» no es válido para ${cur.type} (${p["mode"]})", false)
        else { c.setVar(name, next); Outcome.Success(next) }
    },
    NodeSpec.Action("log_event", "Registrar evento", "Escribe un mensaje en el registro de eventos.",
        listOf(P("message", "Mensaje", ParamType.MULTILINE, true, "Evento de {{flow}}"))) { c, p ->
        c.engine.log.log(c.flow.id, c.flow.name, p["message"]!!); Outcome.Success(Value.Text(p["message"]!!))
    },
    NodeSpec.Action("run_flow", "Ejecutar otro flujo", "Lanza otro flujo (debe tener un disparador «Acción manual»). Se bloquean los ciclos.",
        listOf(P("flow", "Flujo", ParamType.FLOW, true)), timeoutMs = 5 * 60_000) { c, p -> c.engine.runNested(c, p["flow"]!!) },
)
