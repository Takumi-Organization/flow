package dev.flow.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.flow.app.data.*
import dev.flow.app.platform.*
import dev.flow.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDateTime

/** Contenedor único de dependencias (sin framework de DI: la app es pequeña). */
class Graph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = Prefs(app)
    val env = AndroidEnv(app, prefs)
    val registry: NodeRegistry = builtinRegistry().also { r -> androidActions(app).forEach(r::register) }
    val db = Room.databaseBuilder(app, FlowDb::class.java, "flow.db").build()
    val repo = FlowRepository(db, registry)
    val engine = FlowEngine(registry, env, repo, repo, repo)
    /** Estados en vivo para la depuración visual y la pantalla de inicio. */
    val live = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)

    init {
        engine.addListener(object : RunListener {
            override fun onRunStarted(runId: String, flow: FlowDef, event: TriggerEvent) { live.tryEmit(LiveEvent.Started(runId, flow.id)) }
            override fun onNodeState(runId: String, flowId: String, nodeId: String, state: NodeState, detail: String?) { live.tryEmit(LiveEvent.Node(runId, flowId, nodeId, state, detail)) }
            override fun onRunFinished(record: RunRecord) {
                live.tryEmit(LiveEvent.Finished(record))
                if (record.status != RunStatus.REJECTED || record.nodes.isNotEmpty()) scope.launch { repo.record(record) }
            }
        })
    }

    private val active = java.util.concurrent.ConcurrentHashMap<String, Job>()
    /** Ejecución desde la app (cancelable). Sin [nodeId] usa el disparador manual o, si no hay, el primero. */
    fun runManual(flowId: String, nodeId: String? = null): Job {
        val job = scope.launch {
            val f = repo.get(flowId) ?: return@launch
            val t = (nodeId?.let { f.node(it) } ?: f.nodes.firstOrNull { it.type == "trigger_manual" } ?: f.nodes.firstOrNull { registry[it.type]?.kind == NodeKind.TRIGGER }) ?: return@launch
            engine.run(f, t.id, TriggerEvent(if (t.type == "trigger_manual") "manual" else "debug", at = LocalDateTime.now()))
        }
        active[flowId] = job; job.invokeOnCompletion { active.remove(flowId, job) }
        return job
    }
    fun cancel(flowId: String) { active[flowId]?.cancel() }
    fun isRunning(flowId: String) = active[flowId]?.isActive == true

    /** Vuelve a programar alarmas y a activar/desactivar escuchas según los flujos activos. */
    suspend fun syncBackground(ctx: Context): String? {
        val flows = repo.enabled()
        val types = flows.flatMap { f -> f.nodes.map { it.type } }.toSet()
        Scheduler.rescheduleAll(ctx)
        WifiReceiver.sync(ctx, "trigger_wifi" in types)
        CalendarSupport.syncPeriodic(ctx, "trigger_calendar" in types)
        LocationReceiver.sync(ctx, "trigger_location" in types)
        return ListenerService.sync(ctx, types.any { it in ListenerService.TYPES })
    }
}

sealed interface LiveEvent {
    data class Started(val runId: String, val flowId: String) : LiveEvent
    data class Node(val runId: String, val flowId: String, val nodeId: String, val state: NodeState, val detail: String?) : LiveEvent
    data class Finished(val record: RunRecord) : LiveEvent
}

class FlowApp : Application() {
    lateinit var graph: Graph
    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
        graph = Graph(this)
        graph.scope.launch { graph.syncBackground(this@FlowApp) }
    }
}
