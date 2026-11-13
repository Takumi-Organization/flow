package dev.flow.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay

/** Cálculo puro de la próxima activación de un disparador programado. */
object Schedule {
    fun isScheduled(type: String) = type == "trigger_time" || type == "trigger_weekday" || type == "trigger_date"

    fun nextFire(node: NodeDef, after: LocalDateTime): LocalDateTime? {
        val at = Value.parse(VarType.TIME, node.params["at"] ?: return null)?.let { (it as Value.Time).v } ?: return null
        return when (node.type) {
            "trigger_time", "trigger_weekday" -> {
                val days = parseDays(node.params["days"].orEmpty())
                if (node.type == "trigger_weekday" && days.isEmpty()) return null
                var d = after.toLocalDate()
                repeat(8) {
                    val cand = LocalDateTime.of(d, at)
                    if (cand > after && (days.isEmpty() || cand.dayOfWeek.value in days)) return cand
                    d = d.plusDays(1)
                }
                null
            }
            "trigger_date" -> {
                val date = (Value.parse(VarType.DATE, node.params["date"] ?: return null) as? Value.Date)?.v ?: return null
                if (node.params["repeat"] == "yearly") {
                    val md = MonthDay.from(date)
                    var y = after.year
                    repeat(9) { // cubre 29-feb
                        val cand = LocalDateTime.of(md.atYear(y), at)
                        if (cand > after) return cand
                        y++
                    }
                    null
                } else LocalDateTime.of(date, at).takeIf { it > after }
            }
            else -> null
        }
    }
}
