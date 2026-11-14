package dev.flow.app

import android.content.ContextWrapper
import dev.flow.app.platform.androidActions
import dev.flow.app.ui.Templates
import dev.flow.engine.*
import org.junit.Assert.*
import org.junit.Test

/** El catálogo de presets debe cargar y validar con el registro real (motor + acciones de Android). */
class TemplatesTest {
    private val registry = builtinRegistry().also { r -> androidActions(ContextWrapper(null)).forEach(r::register) }

    @Test fun hayAlMenosCuarentaPresets() = assertTrue("solo ${Templates.size}", Templates.size >= 40)

    @Test fun idsYNombresUnicos() {
        assertEquals(Templates.size, Templates.map { it.id }.toSet().size)
        assertEquals(Templates.size, Templates.map { it.title.lowercase() }.toSet().size)
        assertEquals(Templates.size, Templates.map { it.flow.name.lowercase() }.toSet().size)
    }

    @Test fun todosValidanSinErrores() {
        for (t in Templates) {
            val errors = Validator.validate(t.flow, registry).filter { it.severity == Severity.ERROR }
            assertTrue("${t.id}: $errors", errors.isEmpty())
        }
    }

    @Test fun sinNodosHuerfanosNiTiposDesconocidos() {
        for (t in Templates) {
            val f = t.flow
            assertEquals("${t.id}: ids de nodo repetidos", f.nodes.size, f.nodes.map { it.id }.toSet().size)
            assertEquals("${t.id}: ids de arista repetidos", f.edges.size, f.edges.map { it.id }.toSet().size)
            for (n in f.nodes) assertNotNull("${t.id}: tipo ${n.type}", registry[n.type])
            // todo nodo no disparador debe tener una arista entrante; todo disparador debe conducir a algo
            for (n in f.nodes) {
                if (registry[n.type]!!.kind == NodeKind.TRIGGER) assertTrue("${t.id}: disparador ${n.id} sin salida", f.edges.any { it.from == n.id })
                else assertTrue("${t.id}: nodo huérfano ${n.id}", f.edges.any { it.to == n.id })
            }
            // alcanzables desde algún disparador
            val seen = HashSet<String>(); val stack = ArrayDeque(f.nodes.filter { registry[it.type]!!.kind == NodeKind.TRIGGER }.map { it.id })
            while (stack.isNotEmpty()) { val x = stack.removeLast(); if (seen.add(x)) f.outgoing(x).forEach { stack += it.to } }
            assertEquals("${t.id}: nodos inalcanzables", f.nodes.map { it.id }.toSet(), seen)
        }
    }

    @Test fun textosYCategorias() {
        for (t in Templates) {
            assertTrue("${t.id}: título", t.title.isNotBlank()); assertTrue("${t.id}: resumen", t.summary.length >= 20)
            assertTrue("${t.id}: descripción", t.flow.description.isNotBlank()); assertTrue("${t.id}: categoría", t.category.isNotBlank())
        }
        assertTrue(Templates.map { it.category }.distinct().size >= 8)
    }

    @Test fun cargaDeUnaPlantillaNoUsaRunFlow() = assertTrue(Templates.none { t -> t.flow.nodes.any { it.type == "run_flow" } })
}
