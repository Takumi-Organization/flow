package dev.flow.engine

import kotlin.math.*

/** Cálculos puros para el disparador de ubicación (sin dependencia de Android). */
object Geo {
    private const val R = 6_371_000.0

    /** Distancia en metros entre dos coordenadas (haversine). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(a)))
    }

    /**
     * ¿Hay que disparar? [prevInside] null = primera lectura (solo se memoriza, no se dispara, para no activar
     * flujos por el simple hecho de empezar a escuchar). [mode]: "enter" o "exit".
     */
    fun crossed(prevInside: Boolean?, inside: Boolean, mode: String): Boolean =
        prevInside != null && prevInside != inside && inside == (mode == "enter")
}

/** Un evento de calendario reducido a lo que necesita el planificador. */
data class CalEvent(val id: Long, val title: String, val startMs: Long, val allDay: Boolean = false)

/** Cálculo puro de la próxima activación del disparador «N minutos antes de un evento». */
object CalendarSchedule {
    /**
     * Primer evento (por hora de inicio) cuyo aviso (inicio − [minutesBefore]) cae estrictamente después de [afterMs].
     * [titleFilter] vacío = cualquier evento; si no, el título debe contenerlo (sin distinguir mayúsculas).
     * Los eventos de día completo se ignoran: no tienen una hora a la que avisar con antelación.
     */
    fun next(events: List<CalEvent>, minutesBefore: Int, titleFilter: String, afterMs: Long): Pair<CalEvent, Long>? =
        events.asSequence()
            .filter { !it.allDay && (titleFilter.isBlank() || it.title.contains(titleFilter.trim(), ignoreCase = true)) }
            .map { it to it.startMs - minutesBefore * 60_000L }
            .filter { it.second > afterMs }
            .minByOrNull { it.second }
}
