package dev.flow.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.flow.app.ui.Templates
import dev.flow.app.ui.instantiate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Gestos multitáctil reales (pellizco) sobre el lienzo del editor, inyectados con UiAutomation. */
@RunWith(AndroidJUnit4::class)
class DeviceUiTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val g get() = (ctx as FlowApp).graph

    private fun clickText(text: String): Boolean {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            val n = ua.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
            if (n != null) {
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                return (c ?: n).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            Thread.sleep(500)
        }
        return false
    }

    private fun pinch(cx: Float, cy: Float, r0: Float, r1: Float, steps: Int = 15) {
        val t0 = SystemClock.uptimeMillis()
        fun props(r: Float) = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } } to
            Array(2) { i -> MotionEvent.PointerCoords().apply { x = cx + (if (i == 0) -r else r); y = cy; pressure = 1f; size = 1f } }
        fun send(action: Int, t: Long, n: Int, r: Float) {
            val (p, c) = props(r)
            val ev = MotionEvent.obtain(t0, t, action, n, p, c, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            ua.injectInputEvent(ev, true); ev.recycle()
        }
        send(MotionEvent.ACTION_DOWN, t0, 1, r0)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), t0 + 10, 2, r0)
        for (i in 1..steps) { send(MotionEvent.ACTION_MOVE, t0 + 20 + i * 16L, 2, r0 + (r1 - r0) * i / steps); Thread.sleep(16) }
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), t0 + 400, 2, r1)
        send(MotionEvent.ACTION_UP, t0 + 410, 1, r1)
    }

    private fun shot(name: String): Bitmap {
        val b = ua.takeScreenshot()
        File(ctx.getExternalFilesDir(null), "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return b
    }
    private fun diff(a: Bitmap, b: Bitmap): Double {
        var d = 0; val w = a.width; val h = a.height
        for (y in 0 until h step 4) for (x in 0 until w step 4) if (a.getPixel(x, y) != b.getPixel(x, y)) d++
        return d * 16.0 / (w * h)
    }

    @Test fun pinchZoomsTheCanvas() {
        val t = Templates.first { it.id == "morning" }
        val f = instantiate(t).copy(name = "Pellizco e2e")
        runBlocking { g.repo.flows.first().forEach { g.repo.delete(it.id) }; g.repo.save(f) }
        g.prefs.onboarded = true
        try {
            ActivityScenario.launch<MainActivity>(android.content.Intent(ctx, MainActivity::class.java).putExtra("flowId", f.id)).use {
                Thread.sleep(3500) // abre directamente el editor del flujo (enlace por extra «flowId»)
                val a = shot("pinch_0_inicial")
                val w = a.width.toFloat(); val h = a.height.toFloat()
                pinch(w / 2, h * 0.42f, w * 0.06f, w * 0.30f); Thread.sleep(800)
                val b = shot("pinch_1_zoom_in")
                pinch(w / 2, h * 0.42f, w * 0.30f, w * 0.06f); Thread.sleep(800)
                val c = shot("pinch_2_zoom_out")
                assertTrue("el pellizco hacia fuera debe cambiar el lienzo (${diff(a, b)})", diff(a, b) > 0.01)
                assertTrue("el pellizco hacia dentro debe cambiar de nuevo (${diff(b, c)})", diff(b, c) > 0.01)
            }
        } finally { runBlocking { g.repo.delete(f.id) } }
    }
}
