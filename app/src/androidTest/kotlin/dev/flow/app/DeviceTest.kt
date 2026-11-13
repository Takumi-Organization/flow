package dev.flow.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.flow.app.data.*
import dev.flow.app.platform.*
import dev.flow.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    @get:Rule val perms: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CALENDAR)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FlowDb
    private lateinit var repo: FlowRepository
    private lateinit var engine: FlowEngine
    private lateinit var registry: NodeRegistry

    @Before fun setUp() {
        Channels.create(ctx)
        registry = builtinRegistry().also { r -> androidActions(ctx).forEach(r::register) }
        db = Room.inMemoryDatabaseBuilder(ctx, FlowDb::class.java).build()
        repo = FlowRepository(db, registry)
        engine = FlowEngine(registry, AndroidEnv(ctx, Prefs(ctx)), repo, repo, repo)
        engine.addListener(object : RunListener { override fun onRunFinished(record: RunRecord) { runBlocking { repo.record(record) } } })
    }
    @After fun tearDown() { db.close() }

    private fun flow(id: String, vararg actions: NodeDef, vars: List<VarDef> = emptyList()): FlowDef {
        val nodes = listOf(NodeDef("t", "trigger_manual")) + actions
        val edges = nodes.zipWithNext().mapIndexed { i, (a, b) -> EdgeDef("e$i", a.id, PORT_OUT, b.id, 0) }
        return FlowDef(id, "T $id", true, nodes = nodes, edges = edges, variables = vars)
    }
    private fun run(f: FlowDef) = runBlocking { repo.save(f); engine.run(f, "t", TriggerEvent("manual")) }
    private fun node(id: String, type: String, vararg p: Pair<String, String>) = NodeDef(id, type, params = mapOf(*p))

    @Test fun roomRoundTripDuplicateDeleteAndHistory() = runBlocking {
        val f = FlowDef("f1", "Uno", true, "d", listOf(NodeDef("t", "trigger_manual", 1f, 2f), NodeDef("a", "notify", 3f, 4f, mapOf("title" to "x", "text" to "y"), "lbl", 2, 500, "continue")),
            listOf(EdgeDef("e", "t", PORT_OUT, "a", 0)), listOf(VarDef("n", VarType.NUMBER, "1", true)), "tpl")
        repo.save(f)
        assertEquals(f, repo.get("f1"))
        val copy = repo.duplicate("f1")!!
        assertNotEquals("f1", copy.id); assertFalse(copy.enabled); assertEquals(2, copy.nodes.size); assertEquals(1, copy.edges.size)
        assertTrue(copy.edges.all { e -> copy.node(e.from) != null && copy.node(e.to) != null })
        assertEquals(1, repo.enabled().size)
        repo.save("f1", "n", "5"); assertEquals("5", repo.load("f1", "n"))
        val r = engine.run(f, "t", TriggerEvent("manual"))
        assertEquals(RunStatus.SUCCESS, r.status)
        assertEquals(2, repo.runNodes(r.runId).size)
        repo.delete("f1"); assertNull(repo.get("f1")); assertNull(repo.load("f1", "n")) // cascada
        assertEquals(1, db.flows().allNodes().groupBy { it.flowId }.size)
    }

    @Test fun notifyPostsARealNotification() {
        val r = run(flow("n", node("a", "notify", "title" to "Hola flow", "text" to "cuerpo {{now.time}}")))
        assertEquals(r.nodes.last().error, RunStatus.SUCCESS, r.status)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertTrue(nm.activeNotifications.any { it.notification.extras.getString("android.title") == "Hola flow" })
    }

    @Test fun volumeActuallyChanges() {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val r = run(flow("v", node("a", "set_volume", "stream" to "music", "percent" to "40")))
        assertEquals(r.nodes.last().error, RunStatus.SUCCESS, r.status)
        assertEquals(Math.round(max * 0.4).toInt(), am.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test fun timerWithoutExactPermissionDegradesAndSaysSo() {
        Assume.assumeFalse("requiere alarmas exactas denegadas", ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
        val r = run(flow("tm0", node("a", "start_timer", "minutes" to "5", "label" to "Aprox")))
        assertEquals(r.nodes.last().error, RunStatus.SUCCESS, r.status)
        assertTrue(r.nodes.last().output!!.contains("aproximado"))
    }

    @Test fun timerSchedulesRealAlarmAndOngoingNotification() {
        Assume.assumeTrue("requiere alarmas exactas concedidas", ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
        val r = run(flow("tm", node("a", "start_timer", "minutes" to "5", "label" to "Prueba timer")))
        assertEquals(r.nodes.last().error, RunStatus.SUCCESS, r.status)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = am.nextAlarmClock; assertNotNull(next)
        val delta = next!!.triggerTime - System.currentTimeMillis()
        assertTrue("alarma a ~5 min, fue $delta", delta in 240_000..310_000)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertTrue(nm.activeNotifications.any { it.notification.extras.getString("android.title") == "Prueba timer" })
    }

    @Test fun dndWithoutAccessIsReportedUnavailableNotFaked() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        Assume.assumeFalse(nm.isNotificationPolicyAccessGranted)
        val r = run(flow("d", node("a", "set_dnd", "mode" to "priority")))
        assertEquals(RunStatus.ERROR, r.status)
        assertTrue(r.nodes.last().unavailable); assertEquals(1, r.nodes.last().attempts)
    }

    @Test fun calendarReadWorksAndFillsListVariable() {
        val r = run(flow("c", node("a", "calendar_upcoming", "hours" to "48", "max" to "5", "save" to "ev"), vars = listOf(VarDef("ev", VarType.LIST))))
        assertEquals(r.nodes.last().error, RunStatus.SUCCESS, r.status)
    }

    @Test fun openAppFromBackgroundFallsBackToNotification() {
        AppState.inForeground = false
        val r = run(flow("o", node("a", "open_app", "package" to "com.android.settings")))
        assertEquals(r.nodes.last().error, RunStatus.SUCCESS, r.status)
        assertEquals("notificación para abrir", r.nodes.last().output)
        val bad = run(flow("o2", node("a", "open_app", "package" to "no.existe.app")))
        assertEquals(RunStatus.ERROR, bad.status)
    }

    @Test fun exactAlarmSchedulingRegistersAlarmForTimeTrigger() = runBlocking {
        val g = (ctx.applicationContext as FlowApp).graph
        val f = FlowDef("sched-test", "Sched", true, nodes = listOf(NodeDef("t", "trigger_time", params = mapOf("at" to "03:33")), NodeDef("a", "log_event", params = mapOf("message" to "x"))), edges = listOf(EdgeDef("e", "t", PORT_OUT, "a", 0)))
        g.repo.save(f)
        try {
            Scheduler.rescheduleAll(ctx)
            assertTrue(g.prefs.alarmKeys.contains("sched-test|t"))
            g.repo.delete("sched-test"); Scheduler.rescheduleAll(ctx)
            assertFalse(g.prefs.alarmKeys.contains("sched-test|t"))
        } finally { g.repo.delete("sched-test") }
    }
}
