package dev.flow.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Cobertura de comparaciones, tipos de variable, disparadores y condiciones restantes del motor. */
class CoverageTest {
    private fun states(r: RunRecord) = r.nodes.associate { it.nodeId to it.state }

    @Test fun compareAllVariableTypes() {
        assertEquals(true, Compare.test(Value.Num(3.0), "gt", "2,5"))
        assertEquals(false, Compare.test(Value.Num(3.0), "lt", "2"))
        assertEquals(true, Compare.test(Value.Text("Hola"), "eq", "hola"))
        assertEquals(true, Compare.test(Value.Text("Buenos días"), "contains", "DÍAS"))
        assertEquals(true, Compare.test(Value.Bool(true), "eq", "sí"))
        assertEquals(true, Compare.test(Value.Date(LocalDate.of(2026, 11, 13)), "ge", "2026-01-01"))
        assertEquals(true, Compare.test(Value.Time(LocalTime.of(7, 30)), "lt", "08:00"))
        assertEquals(true, Compare.test(Value.Items(listOf("a", "b")), "contains", "b"))
        assertEquals(true, Compare.test(Value.Items(listOf("a", "b")), "gt", "1"))
        assertEquals(true, Compare.test(Value.Items(listOf("a", "b")), "eq", "a, b"))
        // no comparables → null (el motor lo convierte en error del nodo, no en falso silencioso)
        assertNull(Compare.test(Value.Num(1.0), "eq", "abc"))
        assertNull(Compare.test(Value.Num(1.0), "contains", "1"))
        assertNull(Compare.test(Value.Items(emptyList()), "gt", "x"))
    }

    @Test fun conditionOnUnparsableValueIsNodeError() = runBlocking {
        val f = flow(vars = listOf(VarDef("n", VarType.NUMBER, "1")),
            nodes = listOf(trig(), NodeDef("c", "cond_variable", params = mapOf("name" to "n", "op" to "eq", "value" to "abc")), act("y")),
            edges = listOf(edge("t", "c"), edge("c", "y", PORT_TRUE)))
        val r = engine().run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status)
        assertEquals(NodeState.ERROR, states(r)["c"])
        assertEquals(NodeState.SKIPPED, states(r)["y"])
    }

    @Test fun conditionErrorWithContinueTakesFalseBranch() = runBlocking {
        val f = flow(nodes = listOf(trig(), NodeDef("c", "cond_result", params = mapOf("node" to "nada", "op" to "eq", "value" to "x"), onError = "continue"), act("si"), act("no")),
            edges = listOf(edge("t", "c"), edge("c", "si", PORT_TRUE), edge("c", "no", PORT_FALSE)))
        val s = states(engine().run(f, "t", manual))
        assertEquals(NodeState.ERROR, s["c"]); assertEquals(NodeState.COMPLETED, s["no"]); assertEquals(NodeState.SKIPPED, s["si"])
    }

    @Test fun conditionOnPreviousNodeResult() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a", params = mapOf("text" to "hecho")),
            NodeDef("c", "cond_result", params = mapOf("node" to "a", "op" to "eq", "value" to "hecho")), act("y")),
            edges = listOf(edge("t", "a"), edge("a", "c"), edge("c", "y", PORT_TRUE)))
        assertEquals(NodeState.COMPLETED, states(engine().run(f, "t", manual))["y"])
    }

    @Test fun timeConditionCrossesMidnight() = runBlocking {
        val p = mapOf("from" to "22:00", "to" to "07:00")
        suspend fun at(h: Int, m: Int): Boolean {
            val f = flow(nodes = listOf(trig(), NodeDef("c", "cond_time", params = p), act("y")), edges = listOf(edge("t", "c"), edge("c", "y", PORT_TRUE)))
            val env = FakeEnv(LocalDateTime.of(2026, 11, 13, h, m))
            return states(engine(env).run(f, "t", manual))["y"] == NodeState.COMPLETED
        }
        assertTrue(at(23, 30)); assertTrue(at(3, 0)); assertTrue(at(22, 0))
        assertFalse(at(7, 0)); assertFalse(at(12, 0)); assertFalse(at(21, 59))
    }

    @Test fun triggerMatchersForBluetoothHeadsetAndCharging() {
        val reg = builtinRegistry()
        fun m(type: String, p: Map<String, String>, e: TriggerEvent) = (reg[type] as NodeSpec.Trigger).matches(p, e)
        val bt = TriggerEvent("bluetooth", mapOf("state" to "connected", "device" to "Buds"))
        assertTrue(m("trigger_bluetooth", mapOf("state" to "connected"), bt))
        assertTrue(m("trigger_bluetooth", mapOf("state" to "connected", "device" to "buds"), bt))
        assertFalse(m("trigger_bluetooth", mapOf("state" to "connected", "device" to "Otro"), bt))
        assertFalse(m("trigger_bluetooth", mapOf("state" to "disconnected"), bt))
        assertTrue(m("trigger_headset", mapOf("state" to "connected"), TriggerEvent("headset", mapOf("state" to "connected"))))
        assertFalse(m("trigger_headset", mapOf("state" to "connected"), TriggerEvent("wifi", mapOf("state" to "connected"))))
        assertFalse(m("trigger_charging", mapOf("state" to "connected"), TriggerEvent("charging", mapOf("state" to "disconnected"))))
    }

    @Test fun setVariableListAndBoolean() = runBlocking {
        val f = flow(vars = listOf(VarDef("l", VarType.LIST), VarDef("b", VarType.BOOLEAN, "false"), VarDef("d", VarType.DATE, "2026-01-01"), VarDef("h", VarType.TIME, "07:00")),
            nodes = listOf(trig(),
                NodeDef("s1", "set_variable", params = mapOf("name" to "l", "mode" to "append", "value" to "uno")),
                NodeDef("s2", "set_variable", params = mapOf("name" to "l", "mode" to "append", "value" to "dos")),
                NodeDef("s3", "set_variable", params = mapOf("name" to "b", "mode" to "toggle")),
                NodeDef("s4", "set_variable", params = mapOf("name" to "d", "mode" to "set", "value" to "2026-12-24")),
                NodeDef("s5", "set_variable", params = mapOf("name" to "h", "mode" to "set", "value" to "22:15")),
                act("out", params = mapOf("text" to "{{l}}|{{b}}|{{d}}|{{h}}"))),
            edges = listOf(edge("t", "s1"), edge("s1", "s2"), edge("s2", "s3"), edge("s3", "s4"), edge("s4", "s5"), edge("s5", "out")))
        val r = engine().run(f, "t", manual)
        assertEquals(RunStatus.SUCCESS, r.status)
        assertEquals("uno, dos|true|2026-12-24|22:15", r.nodes.first { it.nodeId == "out" }.output)
    }

    @Test fun delayOutOfRangeFailsWithoutRetry() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("d", "delay", mapOf("seconds" to "999"), retries = 3)), edges = listOf(edge("t", "d")))
        val r = engine().run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertEquals(1, r.nodes.first { it.nodeId == "d" }.attempts)
    }

    @Test fun listenersSeeFullStateSequence() = runBlocking {
        val rec = Recorder(); val e = engine(); e.addListener(rec)
        val f = flow(nodes = listOf(trig(), act("a"), act("b")), edges = listOf(edge("t", "a"), edge("t", "b")))
        e.run(f, "t", manual)
        assertEquals(listOf(NodeState.PENDING, NodeState.RUNNING, NodeState.COMPLETED), rec.states.filter { it.first == "a" }.map { it.second })
        assertEquals(1, rec.runs.size)
    }

    @Test fun validatorChecksPortsAndTriggerInputs() {
        val reg = testRegistry()
        val bad = flow(nodes = listOf(trig("t"), trig("t2"), act("a")),
            edges = listOf(edge("t", "a", "true"), edge("t2", "t")))
        val msgs = Validator.validate(bad, reg).filter { it.severity == Severity.ERROR }.map { it.message.lowercase() }
        assertTrue(msgs.any { it.contains("salida") }); assertTrue(msgs.any { it.contains("no puede recibir") })
    }

    @Test fun scheduleHandlesWeekdayTriggerAcrossWeek() {
        val n = NodeDef("w", "trigger_weekday", params = mapOf("days" to "1", "at" to "09:00"))
        // viernes 2026-11-13 → siguiente lunes 2026-11-16
        assertEquals(LocalDateTime.of(2026, 11, 16, 9, 0), Schedule.nextFire(n, LocalDateTime.of(2026, 11, 13, 12, 0)))
        assertNull(Schedule.nextFire(n.copy(params = mapOf("days" to "", "at" to "09:00")), LocalDateTime.of(2026, 11, 13, 12, 0)))
    }
}

class GeoCalendarTest {
    @Test fun distanceIsReasonable() {
        assertEquals(0.0, Geo.distanceM(37.88, -4.78, 37.88, -4.78), 0.001)
        // 0,001° de latitud ≈ 111 m
        assertEquals(111.2, Geo.distanceM(37.0, -4.0, 37.001, -4.0), 1.0)
        // Córdoba - Sevilla ≈ 120 km en línea recta
        assertEquals(120_000.0, Geo.distanceM(37.8882, -4.7794, 37.3891, -5.9845), 5_000.0)
    }
    @Test fun crossingRules() {
        assertFalse(Geo.crossed(null, true, "enter"))       // primera lectura: no dispara
        assertTrue(Geo.crossed(false, true, "enter"))
        assertFalse(Geo.crossed(true, true, "enter"))
        assertFalse(Geo.crossed(true, false, "enter"))
        assertTrue(Geo.crossed(true, false, "exit"))
        assertFalse(Geo.crossed(false, true, "exit"))
    }
    private val ev = listOf(
        CalEvent(1, "Reunión equipo", 10_000_000), CalEvent(2, "Dentista", 20_000_000),
        CalEvent(3, "Cumpleaños", 5_000_000, allDay = true), CalEvent(4, "Reunión cliente", 30_000_000))
    @Test fun nextCalendarFire() {
        val (e, at) = CalendarSchedule.next(ev, 10, "", 0)!!
        assertEquals(1L, e.id); assertEquals(10_000_000 - 600_000, at)
        // pasado el aviso del primero, toca el segundo
        assertEquals(2L, CalendarSchedule.next(ev, 10, "", 9_400_000)!!.first.id)
        assertEquals(4L, CalendarSchedule.next(ev, 0, "cliente", 0)!!.first.id)
        assertNull(CalendarSchedule.next(ev, 10, "inexistente", 0))
        assertNull(CalendarSchedule.next(ev, 10, "", 40_000_000))
    }
    @Test fun newTriggersAreValidated() {
        val reg = builtinRegistry()
        fun f(n: NodeDef) = Validator.validate(FlowDef("f", "F", nodes = listOf(n)), reg).filter { it.severity == Severity.ERROR }.map { it.message.lowercase() }
        assertTrue(f(NodeDef("t", "trigger_location", params = mapOf("lat" to "37.8", "lon" to "-4.7", "radius" to "150"))).isEmpty())
        assertTrue(f(NodeDef("t", "trigger_location", params = mapOf("lat" to "99", "lon" to "-4.7"))).any { it.contains("latitud") })
        assertTrue(f(NodeDef("t", "trigger_location", params = mapOf("lat" to "37", "lon" to "-4", "radius" to "5"))).any { it.contains("radio") })
        assertTrue(f(NodeDef("t", "trigger_location", params = mapOf("lat" to "37"))).any { it.contains("longitud") })
        assertTrue(f(NodeDef("t", "trigger_calendar", params = mapOf("minutes" to "10"))).isEmpty())
        assertTrue(f(NodeDef("t", "trigger_calendar", params = mapOf("minutes" to "5000"))).any { it.contains("minutos") })
    }
    @Test fun scheduledTriggersAreNotDispatchedByEvents() = kotlinx.coroutines.runBlocking {
        val f = flow(nodes = listOf(NodeDef("t", "trigger_calendar", params = mapOf("minutes" to "5")), act("a")), edges = listOf(edge("t", "a")))
        val prov = MemProvider(listOf(f))
        assertTrue(engine(provider = prov).dispatch(TriggerEvent("calendar")).isEmpty())
        // pero se puede ejecutar directamente (lo hace la alarma) y expone el título al flujo
        val g = f.copy(nodes = listOf(f.nodes[0], act("a", params = mapOf("text" to "{{trigger.title}}"))))
        val r = engine().run(g, "t", TriggerEvent("calendar", mapOf("title" to "Dentista")))
        assertEquals("Dentista", r.nodes.first { it.nodeId == "a" }.output)
    }
}
