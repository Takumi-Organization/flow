package dev.flow.app.ui

import dev.flow.engine.*

/**
 * Presets ampliados. Solo usan nodos que existen hoy (disparadores, condiciones y acciones del motor y de Android).
 * Los datos de ejemplo (nombre de la WiFi, del coche, coordenadas, fecha) son editables en el editor.
 */

private class S(val type: String, val p: Map<String, String>, val onError: String = "stop")
private fun s(type: String, vararg p: Pair<String, String>, onError: String = "stop") = S(type, mapOf(*p), onError)
private fun nt(title: String, text: String) = s("notify", "title" to title, "text" to text)
private fun log(msg: String) = s("log_event", "message" to msg)
private fun dnd(mode: String) = s("set_dnd", "mode" to mode, onError = "continue")
private fun vol(stream: String, pct: Int) = s("set_volume", "stream" to stream, "percent" to "$pct", onError = "continue")
private fun timer(min: Int, label: String) = s("start_timer", "minutes" to "$min", "label" to label)
private fun app(pkg: String) = s("open_app", "package" to pkg, onError = "continue")
private fun charging(expected: Boolean) = s("cond_connection", "what" to "charging", "expected" to "$expected")
private fun between(from: String, to: String) = s("cond_time", "from" to from, "to" to to)
private fun batt(op: String, lvl: Int) = s("cond_battery", "op" to op, "level" to "$lvl")
private fun at(time: String, days: String = "") = s("trigger_time", "at" to time, "days" to days)
private fun workdays(time: String) = s("trigger_weekday", "days" to "1,2,3,4,5", "at" to time)
private fun wifi(ssid: String, state: String) = s("trigger_wifi", "ssid" to ssid, "state" to state)
private fun bt(device: String, state: String) = s("trigger_bluetooth", "device" to device, "state" to state)
private fun headset(state: String) = s("trigger_headset", "state" to state)
private fun zone(transition: String) = s("trigger_location", "lat" to "37.8882", "lon" to "-4.7794", "radius" to "150", "transition" to transition)
private fun isCondition(t: String) = t.startsWith("cond_")

private const val MAPS = "com.google.android.apps.maps"
private const val MUSIC = "com.spotify.music"

/** Cada cadena es lineal; una condición continúa por «true» (si no se cumple, el flujo termina ahí). */
private fun preset(id: String, cat: String, title: String, summary: String, desc: String, vars: List<VarDef> = emptyList(), chains: List<List<S>>): Template {
    val nodes = ArrayList<NodeDef>(); val edges = ArrayList<EdgeDef>()
    chains.forEachIndexed { ci, chain ->
        chain.forEachIndexed { i, st ->
            val nid = "c${ci}n$i"
            nodes += NodeDef(nid, st.type, (40f + 230f * i) * 1.32f, 70f + 160f * ci, st.p, onError = st.onError)
            if (i > 0) { val prev = "c${ci}n${i - 1}"; edges += e(prev, nid, if (isCondition(chain[i - 1].type)) PORT_TRUE else PORT_OUT) }
        }
    }
    return Template(id, cat, title, summary, FlowDef(id, title, description = desc, variables = vars, nodes = nodes, edges = edges))
}

private const val SLEEP = "Sueño y noche"
private const val WORK = "Trabajo y estudio"
private const val CAR = "Conducción y coche"
private const val POWER = "Batería y energía"
private const val AUDIO = "Auriculares y Bluetooth"
private const val HOME = "Casa y WiFi"
private const val PLACES = "Ubicación y horarios"
private const val PRIV = "Privacidad"
private const val SCREEN = "Pantalla y sonido"

val extraTemplates: List<Template> = listOf(
    // ───────── Sueño y noche ─────────
    preset("sleep_full", SLEEP, "Buenas noches", "A las 23:00: «No molestar» solo alarmas, timbre silenciado y multimedia al 15 %.",
        "Deja el móvil listo para dormir sin perder la alarma.", chains = listOf(
            listOf(at("23:00"), nt("Buenas noches", "Silencio activado. Solo sonarán las alarmas."), dnd("alarms"), vol("ring", 0), vol("music", 15), log("Modo dormir aplicado a las {{now.time}}")))),
    preset("sleep_end", SLEEP, "Buenos días: salir del silencio", "Lun–vie a las 07:00: quita «No molestar», sube el timbre y saluda.",
        "Deshace el modo noche por la mañana.", chains = listOf(
            listOf(workdays("07:00"), dnd("off"), vol("ring", 60), nt("Buenos días", "Son las {{now.time}}. Timbre al 60 %.")))),
    preset("sleep_weekend", SLEEP, "Despertar de fin de semana", "Sáb y dom a las 10:00: quita «No molestar» y avisa con calma.",
        "Más tarde que entre semana.", chains = listOf(
            listOf(at("10:00", "6,7"), dnd("off"), vol("ring", 50), nt("Fin de semana", "Sin prisa. Son las {{now.time}}.")))),
    preset("sleep_charge", SLEEP, "Enchufar antes de dormir", "A las 23:15: si no estás cargando, recuerda enchufar el móvil.",
        "Evita despertarte con poca batería.", chains = listOf(
            listOf(at("23:15"), charging(false), nt("Enchufa el móvil", "Batería al {{battery}} %. Mañana te lo agradecerás.")))),
    preset("sleep_night_charge", SLEEP, "Cargando de noche", "Al conectar el cargador entre las 22:00 y las 07:00: «No molestar» solo alarmas y lo registra.",
        "Detecta que te vas a dormir porque enchufas el móvil.", chains = listOf(
            listOf(s("trigger_charging", "state" to "connected"), between("22:00", "07:00"), dnd("alarms"), log("Carga nocturna iniciada con {{battery}} %")))),
    preset("sleep_nap", SLEEP, "Siesta de 20 minutos", "Manual: silencia, pone un temporizador de 20 min y lo registra.",
        "Siesta corta con aviso al terminar.", chains = listOf(
            listOf(s("trigger_manual"), dnd("priority"), timer(20, "Siesta"), log("Siesta iniciada a las {{now.time}}")))),
    preset("sleep_reading", SLEEP, "Lectura antes de dormir", "A las 22:00: baja el volumen multimedia y pone 30 min de lectura.",
        "Un ritual corto para desconectar.", chains = listOf(
            listOf(at("22:00"), vol("music", 10), nt("A leer", "30 minutos sin pantallas brillantes."), timer(30, "Lectura"))))
    ,

    // ───────── Trabajo y estudio ─────────
    preset("work_start", WORK, "Inicio de jornada", "Lun–vie a las 08:45: saluda y muestra los eventos de las próximas 10 horas.",
        "Tu agenda al sentarte a trabajar.", listOf(VarDef("eventos", VarType.TEXT)), chains = listOf(
            listOf(workdays("08:45"), s("calendar_upcoming", "hours" to "10", "max" to "6", "save" to "eventos", onError = "continue"), nt("Hoy en la agenda", "{{eventos}}")))),
    preset("work_end", WORK, "Fin de jornada", "Lun–vie a las 18:00: quita «No molestar», sube el timbre y recuerda desconectar.",
        "Cierra el trabajo y vuelve al modo normal.", chains = listOf(
            listOf(workdays("18:00"), dnd("off"), vol("ring", 70), nt("Fin de jornada", "Cierra lo pendiente y desconecta."), log("Fin de jornada a las {{now.time}}")))),
    preset("work_breaks", WORK, "Pausas activas", "Lun–vie a las 11:00 y a las 16:00: aviso para levantarte y estirar.",
        "Dos disparadores en un mismo flujo.", chains = listOf(
            listOf(workdays("11:00"), nt("Pausa activa", "Levántate, estira y bebe agua.")),
            listOf(workdays("16:00"), nt("Pausa activa", "Cinco minutos lejos de la pantalla.")))),
    preset("class_hours", WORK, "Horario de clase", "Lun–vie: 08:00 silencia el timbre y activa «No molestar»; 15:00 lo restablece.",
        "Dos cadenas: entrada y salida de clase.", chains = listOf(
            listOf(workdays("08:00"), dnd("priority"), vol("ring", 0), nt("Empieza la clase", "Móvil en silencio.")),
            listOf(workdays("15:00"), dnd("off"), vol("ring", 60), nt("Fin de clases", "Móvil con sonido otra vez.")))),
    preset("study_50", WORK, "Estudio 50 minutos", "Manual: activa «No molestar», cuenta la sesión y lanza un temporizador de 50 min.",
        "Bloques largos de estudio con contador persistente.", listOf(VarDef("bloques", VarType.NUMBER, "0", persistent = true)), chains = listOf(
            listOf(s("trigger_manual"), s("set_variable", "name" to "bloques", "mode" to "add", "value" to "1"), dnd("priority"), timer(50, "Estudio"), log("Bloque de estudio nº {{bloques}} a las {{now.time}}")))),
    preset("study_afternoon", WORK, "Repaso de la tarde", "Lun–vie a las 17:00: recuerda repasar y muestra los próximos eventos.",
        "Un empujón para estudiar por la tarde.", listOf(VarDef("eventos", VarType.TEXT)), chains = listOf(
            listOf(workdays("17:00"), s("calendar_upcoming", "hours" to "8", "max" to "4", "save" to "eventos", onError = "continue"), nt("Toca repasar", "Antes de acabar el día:\n{{eventos}}")))),
    preset("meeting_silence", WORK, "Silencio en reuniones", "1 min antes de un evento con «reunión» en el título: «No molestar» prioritario y timbre a 0.",
        "Para que el móvil no suene en medio de una reunión.", chains = listOf(
            listOf(s("trigger_calendar", "minutes" to "1", "title" to "reunión"), dnd("priority"), vol("ring", 0), log("Silencio por: {{trigger.title}}")))),
    preset("meeting_end", WORK, "Recuperar el sonido tras una reunión", "Cuando faltan 0 min para un evento «descanso»: quita el silencio.",
        "Pareja de «Silencio en reuniones»: crea un evento «descanso» tras la reunión.", chains = listOf(
            listOf(s("trigger_calendar", "minutes" to "0", "title" to "descanso"), dnd("off"), vol("ring", 60), log("Sonido recuperado a las {{now.time}}")))),
    preset("deadline", WORK, "Aviso de entrega", "1 h antes de un evento con «entrega» en el título: avisa con el título.",
        "Recordatorio de entregas apuntadas en el calendario.", chains = listOf(
            listOf(s("trigger_calendar", "minutes" to "60", "title" to "entrega"), nt("Entrega en 1 hora", "{{trigger.title}}"), log("Aviso de entrega: {{trigger.title}}")))),
    preset("exam_day", WORK, "Recordatorio de examen", "En una fecha concreta (edítala) a las 08:00: avisa y activa «No molestar».",
        "Una vez, en la fecha que elijas.", chains = listOf(
            listOf(s("trigger_date", "date" to "2027-06-15", "at" to "08:00", "repeat" to "once"), nt("Hoy hay examen", "Respira. Lo tienes preparado."), dnd("priority")))),

    // ───────── Conducción y coche ─────────
    preset("car_connect", CAR, "Subir al coche", "Al conectarse el Bluetooth «Coche»: volumen multimedia al 70 %, abre Maps y avisa.",
        "Edita el nombre del dispositivo para que coincida con el de tu coche.", chains = listOf(
            listOf(bt("Coche", "connected"), vol("music", 70), app(MAPS), nt("Modo coche", "Conectado a {{trigger.device}}.")))),
    preset("car_park", CAR, "Dónde aparqué", "Al desconectarse el Bluetooth «Coche»: guarda la hora y avisa de dónde dejaste el coche.",
        "Memoriza la hora de aparcamiento (persistente).", listOf(VarDef("aparcado", VarType.TEXT, persistent = true)), chains = listOf(
            listOf(bt("Coche", "disconnected"), s("set_variable", "name" to "aparcado", "mode" to "set", "value" to "{{now.date}} {{now.time}}"), nt("Coche aparcado", "Aparcaste el {{aparcado}}."), log("Aparcamiento: {{aparcado}}")))),
    preset("car_music", CAR, "Música al conducir", "Al conectar el Bluetooth «Coche»: «No molestar» prioritario y abre el reproductor de música.",
        "Cambia el paquete de la app si no usas Spotify.", chains = listOf(
            listOf(bt("Coche", "connected"), dnd("priority"), app(MUSIC), log("Música de coche a las {{now.time}}")))),
    preset("car_low_battery", CAR, "Cargar en el coche", "Al conectar el Bluetooth «Coche» con menos de 30 % de batería: recuerda usar el cargador.",
        "Solo avisa cuando hace falta.", chains = listOf(
            listOf(bt("Coche", "connected"), batt("lt", 30), nt("Cargador del coche", "Batería al {{battery}} %. Conéctalo al cargador.")))),
    preset("car_leave", CAR, "Salida de casa en coche", "Lun–vie a las 07:50: abre Maps y avisa de salir.",
        "Para ver el tráfico antes de salir.", chains = listOf(
            listOf(workdays("07:50"), nt("Hora de salir", "Mira el tráfico antes de arrancar."), app(MAPS)))),
    preset("car_unplug_dnd", CAR, "Fin del trayecto", "Al desconectarse el Bluetooth «Coche»: quita «No molestar» y devuelve el volumen multimedia al 40 %.",
        "Vuelve a la normalidad al bajar del coche.", chains = listOf(
            listOf(bt("Coche", "disconnected"), dnd("off"), vol("music", 40), log("Fin de trayecto a las {{now.time}}")))),

    // ───────── Batería y energía ─────────
    preset("power_critical", POWER, "Batería crítica", "Si baja de 10 % sin cargar: aviso fuerte con sonido y registro.",
        "Última llamada antes de apagarse.", chains = listOf(
            listOf(s("trigger_battery", "op" to "lt", "level" to "10"), charging(false), nt("Batería crítica", "Solo queda el {{battery}} %. Enchufa ya."), s("play_sound", "kind" to "notification", "seconds" to "2", onError = "continue"), log("Batería crítica ({{battery}} %)")))),
    preset("power_unplug80", POWER, "Desenchufar al 80 %", "Al subir de 80 % mientras carga: avisa de desenchufar para cuidar la batería.",
        "Ayuda a alargar la vida útil.", chains = listOf(
            listOf(s("trigger_battery", "op" to "gt", "level" to "80"), charging(true), nt("Ya está al {{battery}} %", "Puedes desenchufar para cuidar la batería.")))),
    preset("power_full", POWER, "Batería completa", "Al llegar al 100 %: aviso con sonido para que desenchufes.",
        "Para cargas puntuales.", chains = listOf(
            listOf(s("trigger_battery", "op" to "gt", "level" to "99"), charging(true), nt("Cargada", "Batería completa."), s("play_sound", "kind" to "notification", "seconds" to "2", onError = "continue")))),
    preset("power_plugged", POWER, "Cargador conectado", "Al conectar el cargador: confirma con el nivel actual y lo registra.",
        "Confirmación de que el cable funciona.", chains = listOf(
            listOf(s("trigger_charging", "state" to "connected"), nt("Cargando", "Batería al {{battery}} %."), log("Cargador conectado con {{battery}} %")))),
    preset("power_unplugged", POWER, "Cargador desconectado", "Al desconectar el cargador: registra el nivel de batería.",
        "Útil para llevar un historial de cargas.", chains = listOf(
            listOf(s("trigger_charging", "state" to "disconnected"), log("Cargador retirado con {{battery}} %")))),
    preset("power_cycles", POWER, "Contador de cargas", "Cuenta cada vez que conectas el cargador (se conserva entre reinicios).",
        "Cuántas veces cargas el móvil.", listOf(VarDef("cargas", VarType.NUMBER, "0", persistent = true)), chains = listOf(
            listOf(s("trigger_charging", "state" to "connected"), s("set_variable", "name" to "cargas", "mode" to "add", "value" to "1"), log("Carga nº {{cargas}} con {{battery}} %")))),
    preset("power_night_low", POWER, "Batería baja de noche", "Si baja de 30 % entre las 23:00 y las 07:00 sin cargar: avisa para enchufar.",
        "Evita que se apague mientras duermes.", chains = listOf(
            listOf(s("trigger_battery", "op" to "lt", "level" to "30"), between("23:00", "07:00"), charging(false), nt("Batería baja", "{{battery}} %. Enchufa el móvil antes de dormir.")))),
    preset("power_half", POWER, "Aviso a mitad de batería", "Al bajar de 50 %: registra la hora, para saber cuánto dura tu día.",
        "Medidor de autonomía casero.", chains = listOf(
            listOf(s("trigger_battery", "op" to "lt", "level" to "50"), log("Mitad de batería a las {{now.time}}")))),

    // ───────── Auriculares y Bluetooth ─────────
    preset("audio_unplug", AUDIO, "Auriculares fuera: silencio", "Al desenchufar los auriculares con cable: volumen multimedia al 0 para que no suene por el altavoz.",
        "Evita sustos en público.", chains = listOf(
            listOf(headset("disconnected"), vol("music", 0), log("Auriculares desconectados a las {{now.time}}")))),
    preset("audio_music", AUDIO, "Auriculares: abrir música", "Al enchufar auriculares con cable: abre el reproductor de música.",
        "Cambia el paquete si no usas Spotify.", chains = listOf(
            listOf(headset("connected"), app(MUSIC)))),
    preset("audio_night_vol", AUDIO, "Volumen seguro por la noche", "Al enchufar auriculares: 25 % si es de noche (22:00–07:00), 45 % de día.",
        "Protege los oídos y a quien duerme al lado.", chains = listOf()),
    preset("bt_lost", AUDIO, "Aviso de Bluetooth perdido", "Al desconectarse un dispositivo Bluetooth: avisa con su nombre.",
        "Por si se te cae un auricular o te dejas el altavoz.", chains = listOf(
            listOf(bt("", "disconnected"), nt("Bluetooth desconectado", "{{trigger.device}} se ha desconectado."), log("Bluetooth desconectado: {{trigger.device}}")))),
    preset("bt_count", AUDIO, "Contador de conexiones Bluetooth", "Cuenta cuántas veces se conecta un Bluetooth (persistente) y lo registra.",
        "Estadística de uso de tus auriculares.", listOf(VarDef("conexiones", VarType.NUMBER, "0", persistent = true)), chains = listOf(
            listOf(bt("", "connected"), s("set_variable", "name" to "conexiones", "mode" to "add", "value" to "1"), log("Conexión nº {{conexiones}}: {{trigger.device}}")))),
    preset("bt_speaker", AUDIO, "Altavoz Bluetooth: volumen medio", "Al conectar un Bluetooth: multimedia al 50 % y aviso.",
        "Punto de partida cómodo para cualquier altavoz.", chains = listOf(
            listOf(bt("", "connected"), vol("music", 50), nt("Bluetooth", "{{trigger.device}} conectado. Multimedia al 50 %.")))),

    // ───────── Casa y WiFi ─────────
    preset("home_arrive", HOME, "Llegar a casa (WiFi)", "Al conectarse a la WiFi «MiCasa»: quita «No molestar», sube el timbre y da la bienvenida.",
        "Cambia «MiCasa» por el nombre de tu red. Leer el nombre pide permiso de ubicación.", chains = listOf(
            listOf(wifi("MiCasa", "connected"), dnd("off"), vol("ring", 60), nt("Bienvenido a casa", "Son las {{now.time}}."), log("Llegada a casa a las {{now.time}}")))),
    preset("home_leave", HOME, "Salir de casa (WiFi)", "Al perder la WiFi «MiCasa»: recuerda llaves, cartera y cargador.",
        "Una lista rápida al salir.", chains = listOf(
            listOf(wifi("MiCasa", "disconnected"), nt("¿Llevas todo?", "Llaves, cartera, cargador y mascarilla."), log("Salida de casa a las {{now.time}}")))),
    preset("home_charge", HOME, "Enchufar al llegar", "Al conectarse a «MiCasa» con menos de 40 % de batería: recuerda enchufar el móvil.",
        "Aprovecha que estás en casa.", chains = listOf(
            listOf(wifi("MiCasa", "connected"), batt("lt", 40), charging(false), nt("Enchufa el móvil", "Batería al {{battery}} %.")))),
    preset("home_work_wifi", HOME, "WiFi del trabajo", "Al conectarse a «Oficina»: «No molestar» prioritario y timbre al 20 %.",
        "Cambia «Oficina» por tu red.", chains = listOf(
            listOf(wifi("Oficina", "connected"), dnd("priority"), vol("ring", 20), log("Llegada al trabajo (WiFi) a las {{now.time}}")))),
    preset("home_wifi_log", HOME, "Registro de redes WiFi", "Cada vez que te conectas a una WiFi: apunta el nombre y la hora en el registro.",
        "Historial de redes (el nombre exige permiso de ubicación).", chains = listOf(
            listOf(wifi("", "connected"), log("WiFi conectada: {{trigger.ssid}} a las {{now.time}}")))),
    preset("home_wifi_lost", HOME, "Se cayó la WiFi", "Al desconectarse de cualquier WiFi: lo registra con la hora.",
        "Para detectar cortes de conexión.", chains = listOf(
            listOf(wifi("", "disconnected"), log("WiFi desconectada a las {{now.time}}")))),

    // ───────── Ubicación y horarios ─────────
    preset("place_leave_home", PLACES, "Salir de casa (ubicación)", "Al salir de la zona de casa: recuerda llaves y cartera, y lo registra.",
        "Edita latitud, longitud y radio con «Usar mi ubicación actual».", chains = listOf(
            listOf(zone("exit"), nt("¿Llevas todo?", "Llaves, cartera y cargador."), log("Salida de la zona a las {{now.time}}")))),
    preset("place_work", PLACES, "Llegar al trabajo", "Al entrar en la zona del trabajo: «No molestar» prioritario y timbre al 20 %.",
        "Edita la zona para que sea tu trabajo.", chains = listOf(
            listOf(zone("enter"), dnd("priority"), vol("ring", 20), nt("En el trabajo", "Silencio activado a las {{now.time}}.")))),
    preset("place_work_exit", PLACES, "Salir del trabajo", "Al salir de la zona del trabajo en día laborable: quita «No molestar» y sube el timbre.",
        "Vuelta al modo normal.", chains = listOf(
            listOf(zone("exit"), s("cond_weekday", "days" to "1,2,3,4,5"), dnd("off"), vol("ring", 70), log("Salida del trabajo a las {{now.time}}")))),
    preset("place_gym", PLACES, "Llegar al gimnasio", "Al entrar en la zona del gimnasio: abre la música y sube el volumen multimedia.",
        "Edita la zona y la app de música.", chains = listOf(
            listOf(zone("enter"), vol("music", 60), app(MUSIC), log("Entrenamiento iniciado a las {{now.time}}")))),
    preset("place_shop", PLACES, "Lista de la compra", "Al entrar en la zona del supermercado: muestra tu lista (editable en la variable).",
        "Cambia el texto inicial de la variable «compra».", listOf(VarDef("compra", VarType.TEXT, "Leche, pan, fruta, huevos")), chains = listOf(
            listOf(zone("enter"), nt("Lista de la compra", "{{compra}}")))),
    preset("time_meds", PLACES, "Medicación", "Cada día a las 09:00 y a las 21:00: recuerda tomar la medicación y lo registra.",
        "Dos horas, un solo flujo.", chains = listOf(
            listOf(at("09:00"), nt("Medicación", "Toca tomarla (mañana)."), log("Recordatorio de medicación (mañana)")),
            listOf(at("21:00"), nt("Medicación", "Toca tomarla (noche)."), log("Recordatorio de medicación (noche)")))),
    preset("time_water", PLACES, "Beber agua", "Cuatro avisos al día (10:30, 13:00, 16:30, 19:00) con el contador de vasos.",
        "El contador es persistente.", listOf(VarDef("vasos", VarType.NUMBER, "0", persistent = true)), chains = listOf(
            listOf(at("10:30"), s("set_variable", "name" to "vasos", "mode" to "add", "value" to "1"), nt("Agua", "Aviso nº {{vasos}}: bebe un vaso.")),
            listOf(at("13:00"), s("set_variable", "name" to "vasos", "mode" to "add", "value" to "1"), nt("Agua", "Aviso nº {{vasos}}: bebe un vaso.")),
            listOf(at("16:30"), s("set_variable", "name" to "vasos", "mode" to "add", "value" to "1"), nt("Agua", "Aviso nº {{vasos}}: bebe un vaso.")),
            listOf(at("19:00"), s("set_variable", "name" to "vasos", "mode" to "add", "value" to "1"), nt("Agua", "Aviso nº {{vasos}}: bebe un vaso.")))),
    preset("time_birthday", PLACES, "Cumpleaños cada año", "Cada año en la fecha elegida (edítala) a las 09:00: aviso para felicitar.",
        "Repetición anual.", chains = listOf(
            listOf(s("trigger_date", "date" to "2027-03-14", "at" to "09:00", "repeat" to "yearly"), nt("Cumpleaños", "Hoy toca felicitar."), log("Aviso de cumpleaños")))),

    // ───────── Privacidad ─────────
    preset("priv_discreet", PRIV, "Modo discreto (interruptor)", "Manual: alterna entre silencio total con volumen a 0 y modo normal.",
        "Cada ejecución invierte el estado (variable persistente).", listOf(VarDef("discreto", VarType.BOOLEAN, "false", persistent = true)),
        chains = listOf()),
    preset("priv_other_wifi", PRIV, "WiFi ajena: precaución", "Al conectarte a una WiFi que no es «MiCasa»: aviso para no usar datos sensibles.",
        "Cambia «MiCasa» por tu red de confianza.", chains = listOf(
            listOf(wifi("", "connected"), s("cond_connection", "what" to "wifi", "name" to "MiCasa", "expected" to "false"), nt("Red no habitual", "Evita bancos y contraseñas en esta WiFi."), log("WiFi no habitual: {{trigger.ssid}}")))),
    preset("priv_quiet_calendar", PRIV, "Sin sonidos en eventos privados", "5 min antes de un evento con «privado» en el título: silencio total.",
        "Para citas médicas, entrevistas y similares.", chains = listOf(
            listOf(s("trigger_calendar", "minutes" to "5", "title" to "privado"), dnd("none"), vol("ring", 0), log("Silencio por evento privado")))),
    preset("priv_night_media", PRIV, "Multimedia a cero por la noche", "A las 23:30: volumen multimedia y timbre a 0 para que nada suene por sorpresa.",
        "Evita ruidos inesperados al dormir.", chains = listOf(
            listOf(at("23:30"), vol("music", 0), vol("ring", 0), log("Sonidos silenciados a las {{now.time}}")))),
    preset("priv_unplug_headset_dnd", PRIV, "Bluetooth fuera de casa", "Al conectar un Bluetooth fuera de casa (WiFi «MiCasa» ausente): registra el dispositivo.",
        "Deja constancia de qué se conecta y dónde estás.", chains = listOf(
            listOf(bt("", "connected"), s("cond_connection", "what" to "wifi", "name" to "MiCasa", "expected" to "false"), log("Bluetooth fuera de casa: {{trigger.device}} a las {{now.time}}")))),

    // ───────── Pantalla y sonido ─────────
    preset("snd_test", SCREEN, "Probar sonido", "Manual: reproduce una alarma 3 s para comprobar el volumen.",
        "Útil tras cambiar ajustes de sonido.", chains = listOf(
            listOf(s("trigger_manual"), s("play_sound", "kind" to "alarm", "seconds" to "3"), log("Prueba de sonido a las {{now.time}}")))),
    preset("snd_schedule", SCREEN, "Volumen por franjas", "Cada día: timbre al 70 % a las 08:00 y al 20 % a las 22:00.",
        "Dos horas, dos volúmenes.", chains = listOf(
            listOf(at("08:00"), vol("ring", 70), log("Timbre de día")),
            listOf(at("22:00"), vol("ring", 20), log("Timbre de noche")))),
    preset("snd_cinema", SCREEN, "Modo cine", "Manual: solo alarmas, multimedia al 60 % y temporizador de 2 h.",
        "Sin notificaciones durante la película.", chains = listOf(
            listOf(s("trigger_manual"), dnd("alarms"), vol("music", 60), timer(120, "Cine"), log("Modo cine a las {{now.time}}")))),
    preset("snd_silence_hour", SCREEN, "Silencio total una hora", "Manual: silencio total y un temporizador de 60 min para recordar quitarlo.",
        "Cuando necesitas paz de verdad.", chains = listOf(
            listOf(s("trigger_manual"), dnd("none"), timer(60, "Silencio total"), log("Silencio total hasta dentro de 1 h")))),
    preset("snd_alarm_night_charge", SCREEN, "Alarma audible al enchufar", "Al enchufar de noche (22:00–07:00): alarma al 80 % para no quedarte sin despertador.",
        "Asegura que la alarma suena.", chains = listOf(
            listOf(s("trigger_charging", "state" to "connected"), between("22:00", "07:00"), vol("alarm", 80), log("Alarma al 80 % a las {{now.time}}")))),
    preset("snd_kitchen", SCREEN, "Temporizador de cocina", "Manual: temporizador de 10 min con aviso sonoro.",
        "Cámbialo a tu receta en el editor.", chains = listOf(
            listOf(s("trigger_manual"), timer(10, "Cocina"), log("Temporizador de cocina de 10 min")))),
).map { if (it.id == "audio_night_vol") nightVolume(it) else if (it.id == "priv_discreet") discreet(it) else it }

/** Presets con ramas explícitas (verdadero/falso) que no encajan en una cadena lineal. */
private fun nightVolume(base: Template): Template = base.copy(flow = base.flow.copy(
    nodes = listOf(
        n("t", "trigger_headset", 40f, 200f, "state" to "connected"),
        n("night", "cond_time", 270f, 200f, "from" to "22:00", "to" to "07:00"),
        n("low", "set_volume", 500f, 110f, "stream" to "music", "percent" to "25", onError = "continue"),
        n("high", "set_volume", 500f, 290f, "stream" to "music", "percent" to "45", onError = "continue"),
        n("msg", "notify", 730f, 200f, "title" to "Auriculares conectados", "text" to "Volumen ajustado según la hora ({{now.time}})."),
    ),
    edges = listOf(e("t", "night"), e("night", "low", PORT_TRUE), e("night", "high", PORT_FALSE), e("low", "msg"), e("high", "msg"))))

private fun discreet(base: Template): Template = base.copy(flow = base.flow.copy(
    nodes = listOf(
        n("t", "trigger_manual", 40f, 200f),
        n("flip", "set_variable", 270f, 200f, "name" to "discreto", "mode" to "toggle"),
        n("on", "cond_variable", 500f, 200f, "name" to "discreto", "op" to "eq", "value" to "true"),
        n("d1", "set_dnd", 730f, 110f, "mode" to "none", onError = "continue"),
        n("v1", "set_volume", 960f, 110f, "stream" to "ring", "percent" to "0", onError = "continue"),
        n("m1", "notify", 1190f, 110f, "title" to "Modo discreto", "text" to "Activado: silencio total."),
        n("d2", "set_dnd", 730f, 290f, "mode" to "off", onError = "continue"),
        n("v2", "set_volume", 960f, 290f, "stream" to "ring", "percent" to "60", onError = "continue"),
        n("m2", "notify", 1190f, 290f, "title" to "Modo discreto", "text" to "Desactivado: sonido normal."),
    ),
    edges = listOf(e("t", "flip"), e("flip", "on"), e("on", "d1", PORT_TRUE), e("d1", "v1"), e("v1", "m1"),
        e("on", "d2", PORT_FALSE), e("d2", "v2"), e("v2", "m2"))))
