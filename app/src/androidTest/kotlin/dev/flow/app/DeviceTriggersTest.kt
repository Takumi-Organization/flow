package dev.flow.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dev.flow.app.data.RunRow
import dev.flow.app.platform.*
import dev.flow.app.ui.Templates
import dev.flow.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Disparadores reales (calendario, ubicación) y validación de plantillas con el registro completo de Android. */
@RunWith(AndroidJUnit4::class)
class DeviceTriggersTest {
    @get:Rule val perms: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.ACCESS_FINE_LOCATION)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val g get() = (ctx.applicationContext as FlowApp).graph
    private fun shell(cmd: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).let { pfd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() } }

    @Test fun everyTemplatePassesValidatorWithFullAndroidRegistry() {
        for (t in Templates) {
            val errs = Validator.validate(t.flow, g.registry).filter { it.severity == Severity.ERROR }
            assertTrue("${t.id}: $errs", errs.isEmpty())
            val inst = dev.flow.app.ui.instantiate(t)
            assertTrue("${t.id} instanciada: ${Validator.validate(inst, g.registry)}", !Validator.hasErrors(Validator.validate(inst, g.registry)))
        }
        assertTrue(Templates.any { it.flow.nodes.any { n -> n.type == "trigger_calendar" } } && Templates.any { it.flow.nodes.any { n -> n.type == "trigger_location" } })
    }

    private fun localCalendar(): Long {
        val base = CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "flowtest").appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.ACCOUNT_NAME}=?", arrayOf("flowtest"), null)?.use { if (it.moveToFirst()) return it.getLong(0) }
        val v = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "flowtest"); put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "flowtest"); put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Flow test")
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER); put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1); put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "Europe/Madrid"); put(CalendarContract.Calendars.OWNER_ACCOUNT, "flowtest")
        }
        return ContentUris.parseId(ctx.contentResolver.insert(base, v)!!)
    }

    private fun waitRun(flowId: String, type: String, seconds: Int): RunRow? {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            runBlocking { g.repo.runs(50, true).first() }.firstOrNull { it.flowId == flowId && it.triggerType == type }?.let { return it }
            Thread.sleep(2000)
        }
        return null
    }

    @Test fun calendarTriggerFiresRealAlarmBeforeEvent() = runBlocking {
        shell("appops set dev.flow.app SCHEDULE_EXACT_ALARM allow")
        val cal = localCalendar()
        val start = System.currentTimeMillis() + 150_000
        val evUri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, cal); put(CalendarContract.Events.TITLE, "Flow e2e reunión")
            put(CalendarContract.Events.DTSTART, start); put(CalendarContract.Events.DTEND, start + 3_600_000); put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Madrid")
        })!!
        val f = FlowDef("cal-e2e", "Calendario e2e", true, nodes = listOf(
            NodeDef("t", "trigger_calendar", params = mapOf("minutes" to "2", "title" to "e2e")),
            NodeDef("a", "log_event", params = mapOf("message" to "Aviso: {{trigger.title}}"))), edges = listOf(EdgeDef("e", "t", PORT_OUT, "a", 0)))
        g.repo.save(f)
        try {
            Scheduler.rescheduleAll(ctx)
            assertTrue("alarma programada", g.prefs.alarmKeys.contains("cal-e2e|t"))
            val run = waitRun("cal-e2e", "calendar", 150)
            assertNotNull("el disparador de calendario debe ejecutar el flujo", run)
            assertEquals(RunStatus.SUCCESS.name, run!!.status)
            assertTrue(runBlocking { g.repo.logs(20).first() }.any { it.message == "Aviso: Flow e2e reunión" })
        } finally {
            g.repo.delete("cal-e2e"); Scheduler.rescheduleAll(ctx); ctx.contentResolver.delete(evUri, null, null)
        }
    }

    /** Requiere que el emulador reciba `emu geo fix` desde fuera: primero fuera de la zona, luego dentro. Se salta si no se pide. */
    @Test fun locationTriggerFiresOnEnteringZone() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("solo con -e geo true", args.getString("geo") == "true")
        shell("pm grant dev.flow.app android.permission.ACCESS_BACKGROUND_LOCATION")
        val f = FlowDef("geo-e2e", "Ubicación e2e", true, nodes = listOf(
            NodeDef("t", "trigger_location", params = mapOf("lat" to "37.5000", "lon" to "-4.5000", "radius" to "200", "transition" to "enter")),
            NodeDef("a", "log_event", params = mapOf("message" to "Entrada en {{trigger.lat}},{{trigger.lon}}"))), edges = listOf(EdgeDef("e", "t", PORT_OUT, "a", 0)))
        g.repo.save(f)
        try {
            val warn = LocationReceiver.sync(ctx, true)
            assertNull("sin avisos de ubicación", warn)
            val run = waitRun("geo-e2e", "location", 240)
            assertNotNull("el disparador de ubicación debe ejecutar el flujo", run)
            assertEquals(RunStatus.SUCCESS.name, run!!.status)
            assertTrue(runBlocking { g.repo.logs(20).first() }.any { it.message.startsWith("Entrada en 37.5") })
        } finally { g.repo.delete("geo-e2e"); LocationReceiver.sync(ctx, false) }
    }
}

/** Receptores reales del sistema: se disparan desde fuera con `dumpsys battery` y `svc wifi` (ver docs/ESTADO.md). */
@RunWith(AndroidJUnit4::class)
class DeviceListenersTest {
    @get:Rule val perms: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val g get() = (ctx.applicationContext as FlowApp).graph

    private fun f(id: String, type: String, p: Map<String, String>, msg: String) = FlowDef(id, id, true,
        nodes = listOf(NodeDef("t", type, params = p), NodeDef("a", "log_event", params = mapOf("message" to msg))), edges = listOf(EdgeDef("e", "t", PORT_OUT, "a", 0)))

    @Test fun batteryChargingAndWifiReceiversFire() = runBlocking {
        Assume.assumeTrue("solo con -e ext true", InstrumentationRegistry.getArguments().getString("ext") == "true")
        val flows = listOf(
            f("l-bat", "trigger_battery", mapOf("op" to "lt", "level" to "50"), "L:bat"),
            f("l-chg-on", "trigger_charging", mapOf("state" to "connected"), "L:chg-on"),
            f("l-chg-off", "trigger_charging", mapOf("state" to "disconnected"), "L:chg-off"),
            f("l-wifi-off", "trigger_wifi", mapOf("state" to "disconnected"), "L:wifi-off"),
            f("l-wifi-on", "trigger_wifi", mapOf("state" to "connected"), "L:wifi-on"))
        flows.forEach { g.repo.save(it) }
        try {
            val warn = g.syncBackground(ctx)
            assertNull("servicio de escucha: $warn", warn)
            val want = setOf("L:bat", "L:chg-on", "L:chg-off", "L:wifi-off", "L:wifi-on")
            val end = System.currentTimeMillis() + 70_000
            var seen = emptySet<String>()
            while (System.currentTimeMillis() < end && !seen.containsAll(want)) {
                seen = g.repo.logs(100).first().map { it.message }.toSet() intersect want; Thread.sleep(2000)
            }
            assertEquals("faltan: ${want - seen}", want, seen)
        } finally { flows.forEach { g.repo.delete(it.id) }; g.syncBackground(ctx) }
    }
}
