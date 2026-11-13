package dev.flow.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Query("SELECT * FROM flows ORDER BY updatedAt DESC") fun observeFlows(): Flow<List<FlowRow>>
    @Query("SELECT * FROM nodes") fun observeNodes(): Flow<List<NodeRow>>
    @Query("SELECT * FROM edges") fun observeEdges(): Flow<List<EdgeRow>>
    @Query("SELECT * FROM variables") fun observeVars(): Flow<List<VarRow>>
    @Query("SELECT * FROM flows") suspend fun flows(): List<FlowRow>
    @Query("SELECT * FROM flows WHERE id = :id") suspend fun flow(id: String): FlowRow?
    @Query("SELECT * FROM nodes WHERE flowId = :id") suspend fun nodes(id: String): List<NodeRow>
    @Query("SELECT * FROM edges WHERE flowId = :id") suspend fun edges(id: String): List<EdgeRow>
    @Query("SELECT * FROM variables WHERE flowId = :id") suspend fun vars(id: String): List<VarRow>
    @Query("SELECT * FROM nodes") suspend fun allNodes(): List<NodeRow>
    @Query("SELECT * FROM edges") suspend fun allEdges(): List<EdgeRow>
    @Query("SELECT * FROM variables") suspend fun allVars(): List<VarRow>

    @Upsert suspend fun upsertFlow(f: FlowRow)
    @Insert suspend fun insertNodes(n: List<NodeRow>)
    @Insert suspend fun insertEdges(e: List<EdgeRow>)
    @Insert suspend fun insertVars(v: List<VarRow>)
    @Query("DELETE FROM nodes WHERE flowId = :id") suspend fun clearNodes(id: String)
    @Query("DELETE FROM edges WHERE flowId = :id") suspend fun clearEdges(id: String)
    @Query("DELETE FROM variables WHERE flowId = :id") suspend fun clearVars(id: String)
    @Query("DELETE FROM flows WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE flows SET enabled = :on, updatedAt = :now WHERE id = :id") suspend fun setEnabled(id: String, on: Boolean, now: Long)

    @Query("SELECT raw FROM variable_values WHERE flowId = :f AND name = :n") suspend fun varValue(f: String, n: String): String?
    @Upsert suspend fun putVarValue(v: VarValueRow)
    @Query("SELECT * FROM variable_values WHERE flowId = :f") fun observeVarValues(f: String): Flow<List<VarValueRow>>
    @Query("DELETE FROM variable_values WHERE flowId = :f AND name = :n") suspend fun clearVarValue(f: String, n: String)

    @Transaction
    suspend fun replace(f: FlowRow, nodes: List<NodeRow>, edges: List<EdgeRow>, vars: List<VarRow>) {
        upsertFlow(f); clearNodes(f.id); clearEdges(f.id); clearVars(f.id)
        insertNodes(nodes); insertEdges(edges); insertVars(vars)
    }
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRun(r: RunRow)
    @Insert suspend fun insertRunNodes(n: List<RunNodeRow>)
    @Query("SELECT * FROM runs ORDER BY startedAt DESC LIMIT :limit") fun observeRuns(limit: Int): Flow<List<RunRow>>
    @Query("SELECT * FROM runs WHERE status != 'REJECTED' ORDER BY startedAt DESC LIMIT :limit") fun observeRealRuns(limit: Int): Flow<List<RunRow>>
    @Query("SELECT * FROM run_nodes WHERE runId = :id ORDER BY seq") suspend fun runNodes(id: String): List<RunNodeRow>
    @Query("""SELECT COUNT(*) AS total, SUM(status = 'SUCCESS') AS ok, SUM(status = 'ERROR') AS failed, AVG(durationMs) AS avgMs
              FROM runs WHERE startedAt >= :since AND status != 'REJECTED'""")
    fun observeStats(since: Long): Flow<StatsRow>
    @Query("DELETE FROM runs") suspend fun clearRuns()
    @Query("DELETE FROM runs WHERE runId NOT IN (SELECT runId FROM runs ORDER BY startedAt DESC LIMIT :keep)") suspend fun prune(keep: Int)
    @Insert suspend fun insertLog(l: LogRow)
    @Query("SELECT * FROM event_log ORDER BY at DESC LIMIT :limit") fun observeLogs(limit: Int): Flow<List<LogRow>>
    @Query("DELETE FROM event_log") suspend fun clearLogs()
    @Query("DELETE FROM event_log WHERE id NOT IN (SELECT id FROM event_log ORDER BY at DESC LIMIT :keep)") suspend fun pruneLogs(keep: Int)
}

@Database(
    entities = [FlowRow::class, NodeRow::class, EdgeRow::class, VarRow::class, VarValueRow::class, RunRow::class, RunNodeRow::class, LogRow::class],
    version = 1, exportSchema = true,
)
abstract class FlowDb : RoomDatabase() {
    abstract fun flows(): FlowDao
    abstract fun history(): HistoryDao
}
