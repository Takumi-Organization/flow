package dev.flow.engine

import java.time.LocalDateTime
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private fun nodeStates(r: RunRecord) = r.nodes.associate { it.nodeId to it.state }

    @Test fun linearFlowRunsInOrderAndStoresResults() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a", params = mapOf("text" to "uno")), act("b", params = mapOf("text" to "dos {{node.a}}"))),
            edges = listOf(edge("t", "a"), edge("a", "b")))
        val rec = Recorder(); val e = engine(); e.addListener(rec)
        val r = e.run(f, "t", manual)
        assertEquals(RunStatus.SUCCESS, r.status)
        assertEquals(listOf("t", "a", "b"), r.nodes.map { it.nodeId })
        assertEquals("dos uno", r.nodes.last().output)
        assertTrue(rec.states.any { it.first == "a" && it.second == NodeState.RUNNING })
        assertEquals(1, rec.runs.size)
    }

    @Test fun conditionSkipsOtherBranch() = runBlocking {
        val env = FakeEnv(LocalDateTime.of(2026, 11, 13, 23, 0))
        val f = flow(nodes = listOf(trig(), NodeDef("c", "cond_time", params = mapOf("from" to "22:00", "to" to "07:00")), act("yes"), act("no")),
            edges = listOf(edge("t", "c"), edge("c", "yes", PORT_TRUE), edge("c", "no", PORT_FALSE)))
        val r = engine(env).run(f, "t", manual)
        assertEquals(NodeState.COMPLETED, nodeStates(r)["yes"]); assertEquals(NodeState.SKIPPED, nodeStates(r)["no"])
        env.now = LocalDateTime.of(2026, 11, 13, 12, 0)
        val r2 = engine(env).run(f, "t", manual)
        assertEquals(NodeState.SKIPPED, nodeStates(r2)["yes"]); assertEquals(NodeState.COMPLETED, nodeStates(r2)["no"])
    }

    @Test fun parallelBranchesRunInEdgeOrder() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a"), act("b"), act("c")),
            edges = listOf(edge("t", "b", order = 2), edge("t", "a", order = 1), edge("t", "c", order = 3)))
        val r = engine().run(f, "t", manual)
        assertEquals(listOf("t", "a", "b", "c"), r.nodes.map { it.nodeId })
    }

    @Test fun joinNodeRunsOnlyOnce() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a"), act("b"), act("j")),
            edges = listOf(edge("t", "a"), edge("t", "b", order = 1), edge("a", "j"), edge("b", "j")))
        val r = engine().run(f, "t", manual)
        assertEquals(1, r.nodes.count { it.nodeId == "j" })
    }

    @Test fun variablesSetAddAndPersist() = runBlocking {
        val store = MemoryVariableStore()
        val vars = listOf(VarDef("n", VarType.NUMBER, "1", persistent = true), VarDef("l", VarType.LIST), VarDef("flag", VarType.BOOLEAN, "false"))
        val f = flow(vars = vars, nodes = listOf(trig(), act("s1", "set_variable", mapOf("name" to "n", "mode" to "add", "value" to "2")),
            act("s2", "set_variable", mapOf("name" to "l", "mode" to "append", "value" to "x")),
            act("s3", "set_variable", mapOf("name" to "flag", "mode" to "toggle")),
            NodeDef("c", "cond_variable", params = mapOf("name" to "n", "op" to "ge", "value" to "3")), act("ok")),
            edges = listOf(edge("t", "s1"), edge("s1", "s2"), edge("s2", "s3"), edge("s3", "c"), edge("c", "ok", PORT_TRUE)))
        val e = engine(store = store)
        val r = e.run(f, "t", manual)
        assertEquals(RunStatus.SUCCESS, r.status); assertEquals(NodeState.COMPLETED, nodeStates(r)["ok"])
        assertEquals("3", store.load("f", "n"))        // persistente
        assertNull(store.load("f", "flag"))            // no persistente
        val r2 = engine(store = store).run(f, "t", TriggerEvent("manual"))
        assertEquals("5", store.load("f", "n"))        // continúa desde el valor guardado
    }

    @Test fun invalidVariableValueFailsWithoutRetry() = runBlocking {
        val f = flow(vars = listOf(VarDef("n", VarType.NUMBER)), nodes = listOf(trig(), act("s", "set_variable", mapOf("name" to "n", "mode" to "set", "value" to "abc"), retries = 3)),
            edges = listOf(edge("t", "s")))
        val r = engine().run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertEquals(1, r.nodes.last().attempts)
    }

    @Test fun retriesThenSucceeds() = runBlocking {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val reg = testRegistry { it.register(NodeSpec.Action("flaky", "Flaky", "t") { _, _ -> if (calls.incrementAndGet() < 3) Outcome.Failure("no aún") else Outcome.Success() }) }
        val f = flow(nodes = listOf(trig(), act("a", "flaky", retries = 3)), edges = listOf(edge("t", "a")))
        val r = engine(reg = reg).run(f, "t", manual)
        assertEquals(RunStatus.SUCCESS, r.status); assertEquals(3, r.nodes.last().attempts)
    }

    @Test fun retriesExhaustedReportsError() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a", "boom", retries = 2), act("after")), edges = listOf(edge("t", "a"), edge("a", "after")))
        val r = engine().run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status)
        assertEquals(3, r.nodes.first { it.nodeId == "a" }.attempts)
        assertEquals(NodeState.SKIPPED, nodeStates(r)["after"])
        assertTrue(r.error!!.contains("kaboom"))
    }

    @Test fun unavailableIsNotRetriedAndFlagged() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a", "denied", retries = 5)), edges = listOf(edge("t", "a")))
        val n = engine().run(f, "t", manual).nodes.last()
        assertTrue(n.unavailable); assertEquals(1, n.attempts)
    }

    @Test fun onErrorContinueFollowsNext() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a", "boom", onError = "continue"), act("after")), edges = listOf(edge("t", "a"), edge("a", "after")))
        val r = engine().run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertEquals(NodeState.COMPLETED, nodeStates(r)["after"])
    }

    @Test fun actionTimeoutBecomesFailure() = runBlocking {
        val reg = testRegistry { it.register(NodeSpec.Action("hang", "Hang", "t", timeoutMs = 50) { _, _ -> delay(10_000); Outcome.Success() }) }
        val f = flow(nodes = listOf(trig(), act("a", "hang")), edges = listOf(edge("t", "a")))
        val r = engine(reg = reg).run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertTrue(r.nodes.last().error!!.contains("Tiempo agotado"))
    }

    @Test fun cancellationMarksRunCancelled() = runBlocking {
        val rec = Recorder(); val e = engine(); e.addListener(rec)
        val f = flow(nodes = listOf(trig(), act("a", "slow"), act("b")), edges = listOf(edge("t", "a"), edge("a", "b")))
        val job = launch(Dispatchers.Default) { e.run(f, "t", manual) }
        delay(200); job.cancelAndJoin()
        val r = rec.runs.single()
        assertEquals(RunStatus.CANCELLED, r.status)
        assertEquals(NodeState.SKIPPED, nodeStates(r)["b"])
        // el flujo queda libre tras cancelar
        assertEquals(RunStatus.SUCCESS, e.run(flow(nodes = listOf(trig()), edges = emptyList()), "t", manual).status)
    }

    @Test fun runLimitStopsExecution() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a"), act("b"), act("c")), edges = listOf(edge("t", "a"), edge("a", "b"), edge("b", "c")))
        val r = engine(cfg = EngineConfig(maxNodeExecutions = 2)).run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertTrue(r.error!!.contains("Límite"))
    }

    @Test fun overallTimeLimit() = runBlocking {
        val f = flow(nodes = listOf(trig(), act("a", "slow")), edges = listOf(edge("t", "a")))
        val r = engine(cfg = EngineConfig(maxRunMs = 100)).run(f, "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertTrue(r.error!!.contains("Tiempo"))
    }

    @Test fun duplicateRunsRejected() = runBlocking {
        val f = flow(nodes = listOf(trig(type = "trigger_charging", params = mapOf("state" to "connected")), act("a")), edges = listOf(edge("t", "a")))
        val e = engine()
        val ev = TriggerEvent("charging", mapOf("state" to "connected"))
        assertEquals(RunStatus.SUCCESS, e.run(f, "t", ev).status)
        val second = e.run(f, "t", ev)
        assertEquals(RunStatus.REJECTED, second.status); assertTrue(second.error!!.contains("duplicada"))
    }

    @Test fun concurrentRunOfSameFlowRejected() = runBlocking {
        val reg = testRegistry { it.register(NodeSpec.Action("gate", "Gate", "t") { _, _ -> delay(300); Outcome.Success() }) }
        val f = flow(nodes = listOf(trig(), act("a", "gate")), edges = listOf(edge("t", "a")))
        val e = engine(reg = reg)
        val first = async(Dispatchers.Default) { e.run(f, "t", manual) }
        delay(100)
        assertEquals(RunStatus.REJECTED, e.run(f, "t", manual).status)
        assertEquals(RunStatus.SUCCESS, first.await().status)
    }

    @Test fun invalidFlowsAreNotExecuted() = runBlocking {
        val cyc = flow(nodes = listOf(trig(), act("a"), act("b")), edges = listOf(edge("t", "a"), edge("a", "b"), edge("b", "a")))
        assertEquals(RunStatus.REJECTED, engine().run(cyc, "t", manual).status)
        val noParam = flow(nodes = listOf(trig(), NodeDef("c", "cond_variable")), edges = listOf(edge("t", "c")))
        val r = engine().run(noParam, "t", manual)
        assertEquals(RunStatus.REJECTED, r.status); assertTrue(r.error!!.contains("falta"))
    }

    @Test fun nestedFlowsAndRecursionGuard() = runBlocking {
        fun callFlow(id: String, target: String?) = flow(id, nodes = buildList {
            add(trig("t", "trigger_manual")); if (target != null) add(act("r", "run_flow", mapOf("flow" to target))) else add(act("r"))
        }, edges = listOf(edge("t", "r")))
        val prov = MemProvider(listOf(callFlow("a", "b"), callFlow("b", "c"), callFlow("c", null)))
        val e = engine(provider = prov)
        assertEquals(RunStatus.SUCCESS, e.run(prov.flows[0], "t", manual).status)
        // a→b→a
        val loop = MemProvider(listOf(callFlow("a", "b"), callFlow("b", "a")))
        val r = engine(provider = loop).run(loop.flows[0], "t", manual)
        assertEquals(RunStatus.ERROR, r.status); assertTrue(r.error!!.contains("recursiva"))
        // autorreferencia: rechazada por el validador
        val self = callFlow("s", "s")
        assertEquals(RunStatus.REJECTED, engine(provider = MemProvider(listOf(self))).run(self, "t", manual).status)
        // profundidad
        val deep = MemProvider(listOf(callFlow("a", "b"), callFlow("b", "c"), callFlow("c", "d"), callFlow("d", "e"), callFlow("e", null)))
        val rd = engine(provider = deep).run(deep.flows[0], "t", manual)
        assertEquals(RunStatus.ERROR, rd.status); assertTrue(rd.error!!.contains("Profundidad"))
    }

    @Test fun dispatchMatchesTriggersAndSkipsDisabled() = runBlocking {
        val on = flow("on", nodes = listOf(trig("t", "trigger_wifi", mapOf("ssid" to "Casa", "state" to "connected")), act("a")), edges = listOf(edge("t", "a")))
        val any = flow("any", nodes = listOf(trig("t", "trigger_wifi", mapOf("state" to "connected")), act("a")), edges = listOf(edge("t", "a")))
        val off = flow("off", nodes = listOf(trig("t", "trigger_wifi", mapOf("state" to "connected")), act("a")), edges = listOf(edge("t", "a")), enabled = false)
        val other = flow("oth", nodes = listOf(trig("t", "trigger_wifi", mapOf("ssid" to "Otra", "state" to "connected")), act("a")), edges = listOf(edge("t", "a")))
        val e = engine(provider = MemProvider(listOf(on, any, off, other)))
        val runs = e.dispatch(TriggerEvent("wifi", mapOf("state" to "connected", "ssid" to "casa")))
        assertEquals(setOf("on", "any"), runs.map { it.flowId }.toSet())
        assertTrue(e.dispatch(TriggerEvent("bluetooth", mapOf("state" to "connected"))).isEmpty())
    }

    @Test fun batteryTriggerFiresOnlyOnCrossing() {
        val spec = builtinRegistry()["trigger_battery"] as NodeSpec.Trigger
        val p = mapOf("op" to "lt", "level" to "20")
        assertTrue(spec.matches(p, TriggerEvent("battery", mapOf("level" to "19", "prev" to "20"))))
        assertFalse(spec.matches(p, TriggerEvent("battery", mapOf("level" to "18", "prev" to "19"))))
        assertFalse(spec.matches(p, TriggerEvent("battery", mapOf("level" to "50", "prev" to "51"))))
    }

    @Test fun connectionAndBatteryConditions() = runBlocking {
        val env = FakeEnv().apply { battery = 15; wifi = "Casa"; bt = setOf("Buds"); charging = true }
        val reg = testRegistry()
        fun cond(type: String, p: Map<String, String>) = flow(nodes = listOf(trig(), NodeDef("c", type, params = p), act("y")), edges = listOf(edge("t", "c"), edge("c", "y", PORT_TRUE)))
        suspend fun truth(type: String, p: Map<String, String>) = nodeStates(engine(env, reg = reg).run(cond(type, p), "t", manual))["y"] == NodeState.COMPLETED
        assertTrue(truth("cond_battery", mapOf("op" to "lt", "level" to "20")))
        assertFalse(truth("cond_battery", mapOf("op" to "gt", "level" to "20")))
        assertTrue(truth("cond_connection", mapOf("what" to "wifi", "name" to "casa", "expected" to "true")))
        assertFalse(truth("cond_connection", mapOf("what" to "wifi", "name" to "otra", "expected" to "true")))
        assertTrue(truth("cond_connection", mapOf("what" to "bluetooth", "expected" to "true")))
        assertTrue(truth("cond_connection", mapOf("what" to "charging", "expected" to "true")))
        env.wifi = null
        assertTrue(truth("cond_connection", mapOf("what" to "wifi", "expected" to "false")))
        env.battery = null  // no se puede leer → error, no un falso silencioso
        val r = engine(env, reg = reg).run(cond("cond_battery", mapOf("op" to "lt", "level" to "20")), "t", manual)
        assertEquals(RunStatus.ERROR, r.status)
    }

    @Test fun weekdayAndDateConditions() = runBlocking { // 2026-11-13 es viernes
        val env = FakeEnv()
        fun f(type: String, p: Map<String, String>) = flow(nodes = listOf(trig(), NodeDef("c", type, params = p), act("y")), edges = listOf(edge("t", "c"), edge("c", "y", PORT_TRUE)))
        suspend fun ok(type: String, p: Map<String, String>) = nodeStates(engine(env).run(f(type, p), "t", manual))["y"] == NodeState.COMPLETED
        assertTrue(ok("cond_weekday", mapOf("days" to "5")))
        assertFalse(ok("cond_weekday", mapOf("days" to "6,7")))
        assertTrue(ok("cond_date", mapOf("op" to "ge", "date" to "2026-11-01")))
        assertFalse(ok("cond_date", mapOf("op" to "le", "date" to "2026-11-01")))
    }

    @Test fun templatesAndLogSink() = runBlocking {
        logs.clear()
        val f = flow(vars = listOf(VarDef("who", VarType.TEXT, "mundo")), nodes = listOf(trig(), act("l", "log_event", mapOf("message" to "Hola {{who}} a las {{now.time}} bat={{battery}} via {{trigger.k}}"))),
            edges = listOf(edge("t", "l")))
        engine().run(f, "t", TriggerEvent("manual", mapOf("k" to "v")))
        assertEquals("Hola mundo a las 23:30 bat=50 via v", logs.single())
    }

    @Test fun validatorFindsProblems() {
        val reg = testRegistry()
        val f = flow(vars = listOf(VarDef("ok")), nodes = listOf(trig(), NodeDef("c", "cond_variable", params = mapOf("name" to "nope", "op" to "eq")), NodeDef("x", "inexistente"), act("e", params = mapOf("text" to "{{nada}}"))),
            edges = listOf(edge("t", "c"), edge("c", "e", "raro")))
        val m = Validator.validate(f, reg).map { it.message }
        assertTrue(m.any { it.contains("no está declarada") }); assertTrue(m.any { it.contains("desconocido") })
        assertTrue(m.any { it.contains("salida «raro»") }); assertTrue(m.any { it.contains("{{nada}}") })
        assertTrue(Validator.validate(flow(nodes = listOf(act("a")), edges = emptyList()), reg).any { it.message.contains("disparador") })
    }
}
