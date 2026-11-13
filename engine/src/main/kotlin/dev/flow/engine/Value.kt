package dev.flow.engine

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

enum class VarType { TEXT, NUMBER, BOOLEAN, DATE, TIME, LIST }

/** Valor tipado de una variable. La forma persistida es (tipo, texto) vía [raw]/[parse]. */
sealed interface Value {
    val type: VarType
    data class Text(val v: String) : Value { override val type get() = VarType.TEXT }
    data class Num(val v: Double) : Value { override val type get() = VarType.NUMBER }
    data class Bool(val v: Boolean) : Value { override val type get() = VarType.BOOLEAN }
    data class Date(val v: LocalDate) : Value { override val type get() = VarType.DATE }
    data class Time(val v: LocalTime) : Value { override val type get() = VarType.TIME }
    data class Items(val v: List<String>) : Value { override val type get() = VarType.LIST }

    /** Representación textual (para plantillas). */
    fun display(): String = when (this) {
        is Text -> v
        is Num -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        is Bool -> v.toString()
        is Date -> v.toString()
        is Time -> v.toString()
        is Items -> v.joinToString(", ")
    }

    /** Forma serializable; round-trip con [parse]. */
    fun raw(): String = when (this) {
        is Items -> Json.encodeToString(ListSerializer(String.serializer()), v)
        else -> display()
    }

    companion object {
        /** Devuelve null si [raw] no es válido para [type]. */
        fun parse(type: VarType, raw: String): Value? = try {
            when (type) {
                VarType.TEXT -> Text(raw)
                VarType.NUMBER -> Num(raw.trim().replace(',', '.').toDouble().also { require(it.isFinite()) })
                VarType.BOOLEAN -> when (raw.trim().lowercase()) {
                    "true", "sí", "si", "1", "yes" -> Bool(true)
                    "false", "no", "0" -> Bool(false)
                    else -> null
                }
                VarType.DATE -> Date(LocalDate.parse(raw.trim()))
                VarType.TIME -> Time(LocalTime.parse(raw.trim()))
                VarType.LIST -> {
                    val t = raw.trim()
                    Items(if (t.startsWith("[")) Json.decodeFromString(ListSerializer(String.serializer()), t)
                          else t.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        } catch (_: Exception) { null }
    }
}
