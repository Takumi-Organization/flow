package dev.flow.app.data

import androidx.room.*

@Entity(tableName = "flows")
data class FlowRow(
    @PrimaryKey val id: String, val name: String, val description: String, val enabled: Boolean,
    val templateId: String, val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "nodes", primaryKeys = ["flowId", "id"], foreignKeys = [ForeignKey(FlowRow::class, ["id"], ["flowId"], onDelete = ForeignKey.CASCADE)], indices = [Index("flowId")])
data class NodeRow(
    val id: String, val flowId: String, val type: String, val label: String,
    val x: Float, val y: Float, val paramsJson: String, val retries: Int, val retryDelayMs: Long, val onError: String,
)

@Entity(tableName = "edges", primaryKeys = ["flowId", "id"], foreignKeys = [ForeignKey(FlowRow::class, ["id"], ["flowId"], onDelete = ForeignKey.CASCADE)], indices = [Index("flowId")])
data class EdgeRow(val id: String, val flowId: String, val fromId: String, val port: String, val toId: String, val ord: Int)

@Entity(tableName = "variables", primaryKeys = ["flowId", "name"], foreignKeys = [ForeignKey(FlowRow::class, ["id"], ["flowId"], onDelete = ForeignKey.CASCADE)])
data class VarRow(val flowId: String, val name: String, val type: String, val initial: String, val persistent: Boolean)

/** Valor actual de las variables persistentes. */
@Entity(tableName = "variable_values", primaryKeys = ["flowId", "name"], foreignKeys = [ForeignKey(FlowRow::class, ["id"], ["flowId"], onDelete = ForeignKey.CASCADE)])
data class VarValueRow(val flowId: String, val name: String, val raw: String)

@Entity(tableName = "runs", indices = [Index("startedAt"), Index("flowId")])
data class RunRow(
    @PrimaryKey val runId: String, val flowId: String, val flowName: String, val startedAt: Long,
    val durationMs: Long, val status: String, val error: String?, val triggerType: String,
)

@Entity(tableName = "run_nodes", foreignKeys = [ForeignKey(RunRow::class, ["runId"], ["runId"], onDelete = ForeignKey.CASCADE)], indices = [Index("runId")])
data class RunNodeRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val runId: String, val seq: Int, val nodeId: String, val title: String,
    val state: String, val durationMs: Long, val output: String?, val error: String?, val attempts: Int, val unavailable: Boolean,
)

@Entity(tableName = "event_log", indices = [Index("at")])
data class LogRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val at: Long, val flowId: String, val flowName: String, val message: String)

data class StatsRow(val total: Int, val ok: Int, val failed: Int, val avgMs: Double?)
