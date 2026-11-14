package dev.flow.app.ui

import dev.flow.engine.*

data class Template(val id: String, val category: String, val title: String, val summary: String, val flow: FlowDef)

internal fun n(id: String, type: String, x: Float, y: Float, vararg p: Pair<String, String>, onError: String = "stop", label: String = "") =
    NodeDef(id, type, x * 1.32f, y, mapOf(*p), label, onError = onError)
internal fun e(a: String, b: String, port: String = PORT_OUT, order: Int = 0) = EdgeDef("$a-$port-$b", a, port, b, order)

/** Ejemplos reales: todos usan nodos que se ejecutan, sin simulaciones. Son plantillas editables. */
private val baseTemplates: List<Template> = listOf(
    Template("morning", "Rutinas", "Rutina matutina", "A las 07:30 (lun–vie): saludo, próximos eventos del calendario y recordatorio de objetivos.",
        FlowDef("morning", "Rutina matutina", description = "Empieza el día con tu agenda y tus objetivos.",
            variables = listOf(VarDef("eventos", VarType.LIST), VarDef("objetivos", VarType.TEXT, "1) Lo más importante  2) Estudiar 1 h  3) Moverme")),
            nodes = listOf(
                n("t", "trigger_time", 40f, 200f, "at" to "07:30", "days" to "1,2,3,4,5"),
                n("hi", "notify", 280f, 200f, "title" to "Buenos días", "text" to "Son las {{now.time}}. Vamos a por el día."),
                n("cal", "calendar_upcoming", 520f, 200f, "hours" to "16", "max" to "5", "save" to "eventos", onError = "continue"),
                n("any", "cond_variable", 760f, 200f, "name" to "eventos", "op" to "gt", "value" to "0"),
                n("ev", "notify", 1000f, 120f, "title" to "Hoy tienes", "text" to "{{eventos}}"),
                n("none", "notify", 1000f, 290f, "title" to "Agenda libre", "text" to "No hay eventos en las próximas horas."),
                n("goals", "notify", 1240f, 200f, "title" to "Tus objetivos", "text" to "{{objetivos}}"),
            ),
            edges = listOf(e("t", "hi"), e("hi", "cal"), e("cal", "any"), e("any", "ev", PORT_TRUE), e("any", "none", PORT_FALSE), e("ev", "goals"), e("none", "goals")))),
    Template("focus", "Trabajo y estudio", "Modo concentración", "Manual: cuenta la sesión, activa «No molestar» si lo permites, lanza un temporizador de 25 min y lo registra.",
        FlowDef("focus", "Modo concentración", description = "Sesiones de trabajo con temporizador.",
            variables = listOf(VarDef("sesiones", VarType.NUMBER, "0", persistent = true)),
            nodes = listOf(
                n("t", "trigger_manual", 40f, 200f),
                n("count", "set_variable", 270f, 200f, "name" to "sesiones", "mode" to "add", "value" to "1"),
                n("dnd", "set_dnd", 500f, 200f, "mode" to "priority", onError = "continue"),
                n("msg", "notify", 730f, 200f, "title" to "Concentración", "text" to "Sesión nº {{sesiones}}: 25 minutos. Sin distracciones."),
                n("timer", "start_timer", 960f, 200f, "minutes" to "25", "label" to "Concentración"),
                n("log", "log_event", 1190f, 200f, "message" to "Sesión de concentración nº {{sesiones}} iniciada a las {{now.time}}"),
            ),
            edges = listOf(e("t", "count"), e("count", "dnd"), e("dnd", "msg"), e("msg", "timer"), e("timer", "log")))),
    Template("night", "Sueño y noche", "Modo nocturno", "A las 22:30: recordatorio, silencia lo no esencial, baja el volumen y avisa si no estás cargando.",
        FlowDef("night", "Modo nocturno", description = "Prepara el móvil y la cabeza para dormir.",
            nodes = listOf(
                n("t", "trigger_time", 40f, 200f, "at" to "22:30"),
                n("rem", "notify", 270f, 200f, "title" to "Hora de desconectar", "text" to "Deja las pantallas y prepara mañana."),
                n("dnd", "set_dnd", 500f, 200f, "mode" to "alarms", onError = "continue"),
                n("vol", "set_volume", 730f, 200f, "stream" to "music", "percent" to "20", onError = "continue"),
                n("chg", "cond_connection", 960f, 200f, "what" to "charging", "expected" to "false"),
                n("plug", "notify", 1190f, 120f, "title" to "Conecta el cargador", "text" to "Batería al {{battery}} %. Y revisa que la alarma esté puesta."),
                n("ok", "log_event", 1190f, 290f, "message" to "Modo nocturno aplicado; ya estaba cargando"),
            ),
            edges = listOf(e("t", "rem"), e("rem", "dnd"), e("dnd", "vol"), e("vol", "chg"), e("chg", "plug", PORT_TRUE), e("chg", "ok", PORT_FALSE)))),
    Template("battery", "Batería y energía", "Ahorro de batería", "Cuando la batería baja de 20 %: avisa y recomienda qué hacer (solo si no estás cargando).",
        FlowDef("battery", "Ahorro de batería", description = "Aviso con recomendaciones al bajar del umbral.",
            nodes = listOf(
                n("t", "trigger_battery", 40f, 200f, "op" to "lt", "level" to "20"),
                n("chg", "cond_connection", 270f, 200f, "what" to "charging", "expected" to "false"),
                n("msg", "notify", 500f, 200f, "title" to "Batería al {{battery}} %", "text" to "Activa el ahorro de energía, baja el brillo y cierra apps en segundo plano."),
                n("log", "log_event", 730f, 200f, "message" to "Aviso de batería baja ({{battery}} %)"),
            ),
            edges = listOf(e("t", "chg"), e("chg", "msg", PORT_TRUE), e("msg", "log")))),
    Template("headphones", "Auriculares y Bluetooth", "Conexión de auriculares", "Al enchufar auriculares con cable: avisa y ajusta el volumen multimedia (configurable).",
        FlowDef("headphones", "Conexión de auriculares", description = "Acción al conectar auriculares con cable/USB-C.",
            nodes = listOf(
                n("t", "trigger_headset", 40f, 200f, "state" to "connected"),
                n("vol", "set_volume", 270f, 200f, "stream" to "music", "percent" to "40", onError = "continue"),
                n("msg", "notify", 500f, 200f, "title" to "Auriculares conectados", "text" to "Volumen multimedia al 40 %."),
            ),
            edges = listOf(e("t", "vol"), e("vol", "msg")))),
    Template("bluetooth", "Auriculares y Bluetooth", "Altavoz o auriculares Bluetooth", "Al conectarse cualquier dispositivo Bluetooth: muestra cuál y registra el evento.",
        FlowDef("bluetooth", "Bluetooth conectado", description = "Reacciona a un dispositivo Bluetooth (indica su nombre para filtrar).",
            nodes = listOf(
                n("t", "trigger_bluetooth", 40f, 200f, "state" to "connected", "device" to ""),
                n("msg", "notify", 270f, 200f, "title" to "Bluetooth", "text" to "Conectado: {{trigger.device}}"),
                n("log", "log_event", 500f, 200f, "message" to "Bluetooth conectado: {{trigger.device}}"),
            ),
            edges = listOf(e("t", "msg"), e("msg", "log")))),
    Template("meeting", "Trabajo y estudio", "Antes de una reunión", "10 min antes de un evento del calendario cuyo título contenga «reunión»: avisa con el título y la hora.",
        FlowDef("meeting", "Antes de una reunión", description = "Recordatorio previo a eventos del calendario (ajusta el filtro del título).",
            nodes = listOf(
                n("t", "trigger_calendar", 40f, 200f, "minutes" to "10", "title" to "reunión"),
                n("msg", "notify", 270f, 200f, "title" to "Empieza pronto", "text" to "{{trigger.title}} en {{trigger.minutes}} min."),
                n("log", "log_event", 500f, 200f, "message" to "Aviso previo: {{trigger.title}}"),
            ),
            edges = listOf(e("t", "msg"), e("msg", "log")))),
    Template("arrive", "Ubicación y horarios", "Llegar a un sitio", "Al entrar en una zona (elige el punto con «Usar mi ubicación actual»): avisa y registra la llegada.",
        FlowDef("arrive", "Llegar a un sitio", description = "Acción al entrar en una zona circular. Edita latitud, longitud y radio.",
            nodes = listOf(
                n("t", "trigger_location", 40f, 200f, "lat" to "37.8882", "lon" to "-4.7794", "radius" to "150", "transition" to "enter"),
                n("msg", "notify", 270f, 200f, "title" to "Has llegado", "text" to "Son las {{now.time}}."),
                n("log", "log_event", 500f, 200f, "message" to "Llegada a la zona a las {{now.time}}"),
            ),
            edges = listOf(e("t", "msg"), e("msg", "log")))),
)

/** Catálogo completo: las plantillas base más las de [extraTemplates] (ver PresetsExtra.kt). */
val Templates: List<Template> = baseTemplates + extraTemplates
