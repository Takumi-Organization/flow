package dev.flow.engine

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleExportTest {
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi)
    private val fri = at(2026, 11, 13, 10, 0)

    @Test fun dailyTime() {
        val n = trig("t", "trigger_time", mapOf("at" to "08:00"))
        assertEquals(at(2026, 11, 14, 8, 0), Schedule.nextFire(n, fri))
        assertEquals(at(2026, 11, 13, 8, 0), Schedule.nextFire(n, at(2026, 11, 13, 7, 59)))
        assertEquals(at(2026, 11, 14, 8, 0), Schedule.nextFire(n, at(2026, 11, 13, 8, 0))) // estrictamente posterior
    }
    @Test fun weekdays() {
        val n = trig("t", "trigger_weekday", mapOf("at" to "09:00", "days" to "1,2,3,4,5"))
        assertEquals(at(2026, 11, 16, 9, 0), Schedule.nextFire(n, at(2026, 11, 13, 9, 30))) // viernes tarde → lunes
        assertNull(Schedule.nextFire(trig("t", "trigger_weekday", mapOf("at" to "09:00", "days" to "")), fri))
    }
    @Test fun dateOnceAndYearly() {
        val once = trig("t", "trigger_date", mapOf("date" to "2026-12-24", "at" to "20:00", "repeat" to "once"))
        assertEquals(at(2026, 12, 24, 20, 0), Schedule.nextFire(once, fri))
        assertNull(Schedule.nextFire(once, at(2026, 12, 25, 0, 0)))
        val y = trig("t", "trigger_date", mapOf("date" to "2000-02-29", "at" to "10:00", "repeat" to "yearly"))
        assertEquals(at(2027, 2, 28, 10, 0), Schedule.nextFire(y, fri))
        val bday = trig("t", "trigger_date", mapOf("date" to "1990-11-13", "at" to "10:00", "repeat" to "yearly"))
        assertEquals(at(2026, 11, 13, 10, 0).plusYears(1), Schedule.nextFire(bday, fri))
    }
    @Test fun badParamsGiveNull() = assertNull(Schedule.nextFire(trig("t", "trigger_time", mapOf("at" to "25:99")), fri))

    private val reg = testRegistry()
    private fun sample(id: String, target: String? = null) = flow(id,
        nodes = listOf(trig("t", "trigger_manual"), act("a", params = mapOf("text" to "x")),
            NodeDef("c", "cond_result", params = mapOf("node" to "a", "op" to "eq", "value" to "x")),
            act("l", "log_event", mapOf("message" to "{{node.a}}")),
            *(if (target != null) arrayOf(act("r", "run_flow", mapOf("flow" to target))) else emptyArray())),
        edges = listOf(edge("t", "a"), edge("a", "c"), edge("c", "l", PORT_TRUE)), vars = listOf(VarDef("v", VarType.NUMBER, "3", true)))

    @Test fun exportImportRoundTripRemapsIds() {
        val a = sample("A", "B"); val b = sample("B")
        val json = FlowExport.export(listOf(a, b))
        assertTrue(json.contains("\"version\": 1"))
        var n = 0
        val res = FlowExport.import(json, reg) { "id${n++}" }
        assertEquals(2, res.flows.size); assertTrue(res.flows.none { it.enabled })
        val ia = res.flows[0]; val ib = res.flows[1]
        assertNotEquals("A", ia.id)
        assertEquals(ib.id, ia.nodes.first { it.type == "run_flow" }.params["flow"])
        val cond = ia.nodes.first { it.type == "cond_result" }
        assertEquals(ia.nodes.first { it.id != cond.id && it.type == "echo" }.id, cond.params["node"])
        assertTrue(ia.nodes.first { it.type == "log_event" }.params["message"]!!.contains(cond.params["node"]!!))
        assertTrue(ia.edges.all { e -> ia.node(e.from) != null && ia.node(e.to) != null })
        assertFalse(Validator.hasErrors(Validator.validate(ia.copy(nodes = ia.nodes.filter { it.type != "run_flow" }), reg)))
        assertEquals(a.variables, ia.variables)
    }
    @Test fun importRejectsBadInput() {
        assertThrows(ImportException::class.java) { FlowExport.import("no es json", reg) }
        assertThrows(ImportException::class.java) { FlowExport.import("""{"format":"otro","version":1,"exportedAt":"x","flows":[]}""", reg) }
        val e = assertThrows(ImportException::class.java) { FlowExport.import("""{"format":"flow-export","version":99,"exportedAt":"x","flows":[]}""", reg) }
        assertTrue(e.message!!.contains("más nueva"))
    }
    @Test fun importWarnsOnUnknownNodeTypes() {
        val f = flow("z", nodes = listOf(trig(), NodeDef("q", "futuro")), edges = listOf(edge("t", "q")))
        assertTrue(FlowExport.import(FlowExport.export(listOf(f)), reg).warnings.any { it.contains("futuro") })
    }
    @Test fun valueRoundTrip() {
        for ((t, raw) in listOf(VarType.NUMBER to "3.5", VarType.BOOLEAN to "true", VarType.DATE to "2026-01-02", VarType.TIME to "07:30", VarType.LIST to "[\"a\",\"b\"]", VarType.TEXT to "hola")) {
            val v = Value.parse(t, raw)!!; assertEquals(v, Value.parse(t, v.raw()))
        }
        assertNull(Value.parse(VarType.NUMBER, "x")); assertNull(Value.parse(VarType.TIME, "99:00")); assertNull(Value.parse(VarType.BOOLEAN, "quizá"))
    }
}
