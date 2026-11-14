package dev.flow.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.flow.app.ui.Templates
import dev.flow.app.ui.instantiate
import dev.flow.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/** Carga datos de ejemplo en la app real para hacer capturas y medir jank: `-e seed true` (o `-e unseed true` para limpiar). */
@RunWith(AndroidJUnit4::class)
class DeviceSeedTest {
    private val g get() = (ApplicationProvider.getApplicationContext<android.content.Context>() as FlowApp).graph
    private val args get() = InstrumentationRegistry.getArguments()

    @Test fun seed() = runBlocking {
        Assume.assumeTrue(args.getString("seed") == "true")
        g.repo.flows.first().forEach { g.repo.delete(it.id) }
        g.prefs.onboarded = true
        val flows = Templates.map { instantiate(it).copy(id = "seed-" + it.id) }.mapIndexed { i, f -> f.copy(enabled = i % 2 == 0) }
        flows.forEach { g.repo.save(it) }
        // historial: ejecuciones reales de los flujos manuales (la acción de No molestar sin acceso deja una con error)
        val manual = flows.filter { f -> f.nodes.any { it.type == "trigger_manual" } }
        repeat(4) { for (f in manual) g.runManual(f.id, null).join() }
        flows.filter { it.enabled && it.nodes.none { n -> n.type == "trigger_manual" } }.forEach { f ->
            val t = f.nodes.first { g.registry[it.type]?.kind == NodeKind.TRIGGER }
            g.engine.run(f, t.id, TriggerEvent("debug"))
        }
        g.syncBackground(ApplicationProvider.getApplicationContext())
        Unit
    }

    @Test fun unseed() = runBlocking {
        Assume.assumeTrue(args.getString("unseed") == "true")
        g.repo.flows.first().forEach { g.repo.delete(it.id) }; g.repo.clearHistory(); g.syncBackground(ApplicationProvider.getApplicationContext())
        Unit
    }
}
