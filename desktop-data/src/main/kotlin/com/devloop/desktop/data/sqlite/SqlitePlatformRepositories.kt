package com.devloop.desktop.data.sqlite

import com.devloop.core.domain.enums.*
import com.devloop.core.domain.model.*
import com.devloop.core.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLite-Implementierung aller Plattform-Repositories.
 *
 * Container-Klasse die einzelne Repository-Implementierungen bereitstellt.
 * Vermeidet Konflikte durch gleichnamige Methoden in verschiedenen Interfaces.
 */
class SqlitePlatformRepositories(private val db: SqliteDevLoopDatabase) {

    val instances: VirtualInstanceRepository = VirtualInstanceRepoImpl()
    val conversations: ConversationRepository = ConversationRepoImpl()
    val memory: MemoryRepository = MemoryRepoImpl()
    val gatewayClients: GatewayClientRepository = GatewayClientRepoImpl()
    val auditLog: AuditLogRepository = AuditLogRepoImpl()
    val resumeContexts: ResumeContextRepository = ResumeContextRepoImpl()
    val syncQueue: SyncQueueAccess = SyncQueueAccessImpl()

    // ═══════════ VirtualInstanceRepository ═══════════

    private inner class VirtualInstanceRepoImpl : VirtualInstanceRepository {
        private val cache = MutableStateFlow<List<VirtualInstance>>(emptyList())
        init { cache.value = loadAll() }

        private fun loadAll(): List<VirtualInstance> = db.tx {
            val list = mutableListOf<VirtualInstance>()
            createStatement().use { st ->
                st.executeQuery("SELECT * FROM virtual_instances ORDER BY lastActiveAtEpochMillis DESC").use { rs ->
                    while (rs.next()) list += mapInstance(rs)
                }
            }
            list
        }

        private fun mapInstance(rs: java.sql.ResultSet) = VirtualInstance(
            id = rs.getString("id"),
            projectId = rs.getString("projectId"),
            label = rs.getString("label"),
            instanceType = VirtualInstanceType.valueOf(rs.getString("instanceType")),
            status = VirtualInstanceStatus.valueOf(rs.getString("status")),
            scopeJson = rs.getString("scopeJson") ?: "{}",
            createdAtEpochMillis = rs.getLong("createdAtEpochMillis"),
            lastActiveAtEpochMillis = rs.getLong("lastActiveAtEpochMillis"),
            domain = try { rs.getString("domain") ?: "SYSTEM" } catch (_: Exception) { "SYSTEM" },
        )

        override fun observeInstances(projectId: ProjectId): Flow<List<VirtualInstance>> =
            cache.map { all -> all.filter { it.projectId == projectId } }

        override suspend fun getAll(): List<VirtualInstance> = cache.value
        override suspend fun getById(id: VirtualInstanceId): VirtualInstance? = cache.value.find { it.id == id }
        override suspend fun getByProject(projectId: ProjectId): List<VirtualInstance> = cache.value.filter { it.projectId == projectId }

        override suspend fun upsert(instance: VirtualInstance) {
            withContext(Dispatchers.IO) {
                db.tx {
                    prepareStatement("INSERT OR REPLACE INTO virtual_instances (id,projectId,label,instanceType,status,scopeJson,createdAtEpochMillis,lastActiveAtEpochMillis,domain) VALUES (?,?,?,?,?,?,?,?,?)").use { ps ->
                        ps.setString(1, instance.id); ps.setString(2, instance.projectId); ps.setString(3, instance.label)
                        ps.setString(4, instance.instanceType.name); ps.setString(5, instance.status.name); ps.setString(6, instance.scopeJson)
                        ps.setLong(7, instance.createdAtEpochMillis); ps.setLong(8, instance.lastActiveAtEpochMillis)
                        ps.setString(9, instance.domain); ps.executeUpdate()
                    }
                }
            }
            cache.value = loadAll()
        }

        override suspend fun updateStatus(id: VirtualInstanceId, status: VirtualInstanceStatus) {
            withContext(Dispatchers.IO) {
                db.tx {
                    prepareStatement("UPDATE virtual_instances SET status = ? WHERE id = ?").use { ps ->
                        ps.setString(1, status.name); ps.setString(2, id); ps.executeUpdate()
                    }
                }
            }
            cache.value = loadAll()
        }
    }

    // ═══════════ ConversationRepository ═══════════

    private inner class ConversationRepoImpl : ConversationRepository {
        private val trigger = MutableStateFlow(0L)

        private fun loadMessages(viId: VirtualInstanceId, limit: Int): List<ConversationMessage> = db.tx {
            val list = mutableListOf<ConversationMessage>()
            prepareStatement("SELECT * FROM conversation_messages WHERE virtualInstanceId = ? ORDER BY createdAtEpochMillis DESC LIMIT ?").use { ps ->
                ps.setString(1, viId); ps.setInt(2, limit)
                ps.executeQuery().use { rs -> while (rs.next()) list += mapMsg(rs) }
            }
            list.reversed()
        }

        private fun mapMsg(rs: java.sql.ResultSet) = ConversationMessage(
            id = rs.getString("id"), virtualInstanceId = rs.getString("virtualInstanceId"),
            role = ConversationRole.valueOf(rs.getString("role")), content = rs.getString("content"),
            contentType = try { ContentType.valueOf(rs.getString("contentType")) } catch (_: Exception) { ContentType.TEXT },
            metadata = rs.getString("metadata") ?: "{}", createdAtEpochMillis = rs.getLong("createdAtEpochMillis"),
            domain = try { rs.getString("domain") ?: "SYSTEM" } catch (_: Exception) { "SYSTEM" },
        )

        override fun observeMessages(virtualInstanceId: VirtualInstanceId): Flow<List<ConversationMessage>> =
            trigger.map { loadMessages(virtualInstanceId, 200) }

        override suspend fun getRecentMessages(virtualInstanceId: VirtualInstanceId, limit: Int): List<ConversationMessage> =
            withContext(Dispatchers.IO) { loadMessages(virtualInstanceId, limit) }

        override suspend fun insertMessage(message: ConversationMessage) {
            withContext(Dispatchers.IO) {
                db.tx {
                    prepareStatement("INSERT OR REPLACE INTO conversation_messages (id,virtualInstanceId,role,content,contentType,metadata,createdAtEpochMillis,domain) VALUES (?,?,?,?,?,?,?,?)").use { ps ->
                        ps.setString(1, message.id); ps.setString(2, message.virtualInstanceId); ps.setString(3, message.role.name)
                        ps.setString(4, message.content); ps.setString(5, message.contentType.name); ps.setString(6, message.metadata)
                        ps.setLong(7, message.createdAtEpochMillis); ps.setString(8, message.domain); ps.executeUpdate()
                    }
                }
            }
            trigger.value = System.currentTimeMillis()
        }

        override suspend fun getMessageById(id: ConversationMessageId): ConversationMessage? = withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM conversation_messages WHERE id = ?").use { ps ->
                    ps.setString(1, id); ps.executeQuery().use { rs -> if (rs.next()) mapMsg(rs) else null }
                }
            }
        }

        override suspend fun getMessageCount(virtualInstanceId: VirtualInstanceId): Int = withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT COUNT(*) FROM conversation_messages WHERE virtualInstanceId = ?").use { ps ->
                    ps.setString(1, virtualInstanceId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        }
    }

    // ═══════════ MemoryRepository ═══════════

    private inner class MemoryRepoImpl : MemoryRepository {

        private fun queryMemory(where: String, params: List<String>, kind: MemoryKind?): List<MemoryEntry> = db.tx {
            val kindFilter = if (kind != null) " AND memoryKind = ?" else ""
            prepareStatement("SELECT * FROM memory_entries WHERE $where$kindFilter ORDER BY relevanceScore DESC, updatedAtEpochMillis DESC").use { ps ->
                var idx = 1
                params.forEach { ps.setString(idx++, it) }
                if (kind != null) ps.setString(idx, kind.name)
                ps.executeQuery().use { rs -> buildList(rs) }
            }
        }

        private fun buildList(rs: java.sql.ResultSet): List<MemoryEntry> {
            val list = mutableListOf<MemoryEntry>()
            while (rs.next()) list += MemoryEntry(
                id = rs.getString("id"), virtualInstanceId = rs.getString("virtualInstanceId"),
                projectId = rs.getString("projectId"), memoryKind = MemoryKind.valueOf(rs.getString("memoryKind")),
                category = rs.getString("category") ?: "", title = rs.getString("title"), content = rs.getString("content"),
                relevanceScore = rs.getDouble("relevanceScore"),
                validFromEpochMillis = rs.getLong("validFromEpochMillis").takeIf { !rs.wasNull() },
                validUntilEpochMillis = rs.getLong("validUntilEpochMillis").takeIf { !rs.wasNull() },
                createdAtEpochMillis = rs.getLong("createdAtEpochMillis"), updatedAtEpochMillis = rs.getLong("updatedAtEpochMillis"),
                domain = try { rs.getString("domain") ?: "SYSTEM" } catch (_: Exception) { "SYSTEM" },
            )
            return list
        }

        override suspend fun getForInstance(virtualInstanceId: VirtualInstanceId, kind: MemoryKind?) =
            withContext(Dispatchers.IO) { queryMemory("virtualInstanceId = ?", listOf(virtualInstanceId), kind) }

        override suspend fun getForProject(projectId: ProjectId, kind: MemoryKind?) =
            withContext(Dispatchers.IO) { queryMemory("projectId = ?", listOf(projectId), kind) }

        override suspend fun getGlobal(kind: MemoryKind?) =
            withContext(Dispatchers.IO) { queryMemory("virtualInstanceId IS NULL AND projectId IS NULL", emptyList(), kind) }

        override suspend fun upsert(entry: MemoryEntry) { withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("INSERT OR REPLACE INTO memory_entries (id,virtualInstanceId,projectId,memoryKind,category,title,content,relevanceScore,validFromEpochMillis,validUntilEpochMillis,createdAtEpochMillis,updatedAtEpochMillis,domain) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)").use { ps ->
                    ps.setString(1, entry.id); ps.setString(2, entry.virtualInstanceId); ps.setString(3, entry.projectId)
                    ps.setString(4, entry.memoryKind.name); ps.setString(5, entry.category); ps.setString(6, entry.title); ps.setString(7, entry.content)
                    ps.setDouble(8, entry.relevanceScore)
                    val vf = entry.validFromEpochMillis; if (vf != null) ps.setLong(9, vf) else ps.setNull(9, java.sql.Types.INTEGER)
                    val vu = entry.validUntilEpochMillis; if (vu != null) ps.setLong(10, vu) else ps.setNull(10, java.sql.Types.INTEGER)
                    ps.setLong(11, entry.createdAtEpochMillis); ps.setLong(12, entry.updatedAtEpochMillis)
                    ps.setString(13, entry.domain); ps.executeUpdate()
                }
            }
        } }

        override suspend fun delete(id: MemoryEntryId) { withContext(Dispatchers.IO) {
            db.tx { prepareStatement("DELETE FROM memory_entries WHERE id = ?").use { ps -> ps.setString(1, id); ps.executeUpdate() } }
        } }

        override suspend fun search(query: String, limit: Int) = withContext(Dispatchers.IO) {
            db.tx {
                val like = "%${query.replace("%", "").replace("_", "")}%"
                prepareStatement("SELECT * FROM memory_entries WHERE title LIKE ? OR content LIKE ? ORDER BY relevanceScore DESC LIMIT ?").use { ps ->
                    ps.setString(1, like); ps.setString(2, like); ps.setInt(3, limit)
                    ps.executeQuery().use { rs -> buildList(rs) }
                }
            }
        }
    }

    // ═══════════ GatewayClientRepository ═══════════

    private inner class GatewayClientRepoImpl : GatewayClientRepository {

        private fun mapClient(rs: java.sql.ResultSet) = GatewayClient(
            id = rs.getString("id"), clientName = rs.getString("clientName"),
            clientKind = GatewayClientKind.valueOf(rs.getString("clientKind")),
            apiKeyHash = rs.getString("apiKeyHash"), scopesJson = rs.getString("scopesJson") ?: "[]",
            permissionsJson = rs.getString("permissionsJson") ?: "{}", isActive = rs.getInt("isActive") == 1,
            createdAtEpochMillis = rs.getLong("createdAtEpochMillis"),
            lastSeenAtEpochMillis = rs.getLong("lastSeenAtEpochMillis").takeIf { !rs.wasNull() },
            allowedDomains = try { rs.getString("allowedDomains") ?: "[\"*\"]" } catch (_: Exception) { "[\"*\"]" },
        )

        override suspend fun getByApiKeyHash(hash: String) = withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM gateway_clients WHERE apiKeyHash = ? AND isActive = 1").use { ps ->
                    ps.setString(1, hash); ps.executeQuery().use { rs -> if (rs.next()) mapClient(rs) else null }
                }
            }
        }

        override suspend fun getById(id: GatewayClientId) = withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM gateway_clients WHERE id = ?").use { ps ->
                    ps.setString(1, id); ps.executeQuery().use { rs -> if (rs.next()) mapClient(rs) else null }
                }
            }
        }

        override suspend fun getAll() = withContext(Dispatchers.IO) {
            db.tx {
                val list = mutableListOf<GatewayClient>()
                createStatement().use { st -> st.executeQuery("SELECT * FROM gateway_clients ORDER BY clientName").use { rs -> while (rs.next()) list += mapClient(rs) } }
                list
            }
        }

        override suspend fun upsert(client: GatewayClient) { withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("INSERT OR REPLACE INTO gateway_clients (id,clientName,clientKind,apiKeyHash,scopesJson,permissionsJson,isActive,createdAtEpochMillis,lastSeenAtEpochMillis,allowedDomains) VALUES (?,?,?,?,?,?,?,?,?,?)").use { ps ->
                    ps.setString(1, client.id); ps.setString(2, client.clientName); ps.setString(3, client.clientKind.name)
                    ps.setString(4, client.apiKeyHash); ps.setString(5, client.scopesJson); ps.setString(6, client.permissionsJson)
                    ps.setInt(7, if (client.isActive) 1 else 0); ps.setLong(8, client.createdAtEpochMillis)
                    val ls = client.lastSeenAtEpochMillis; if (ls != null) ps.setLong(9, ls) else ps.setNull(9, java.sql.Types.INTEGER)
                    ps.setString(10, client.allowedDomains); ps.executeUpdate()
                }
            }
        } }

        override suspend fun deactivate(id: GatewayClientId) { withContext(Dispatchers.IO) {
            db.tx { prepareStatement("UPDATE gateway_clients SET isActive = 0 WHERE id = ?").use { ps -> ps.setString(1, id); ps.executeUpdate() } }
        } }
    }

    // ═══════════ AuditLogRepository ═══════════

    private inner class AuditLogRepoImpl : AuditLogRepository {

        private fun mapEntry(rs: java.sql.ResultSet) = AuditLogEntry(
            id = rs.getString("id"), clientId = rs.getString("clientId"),
            virtualInstanceId = rs.getString("virtualInstanceId"), action = rs.getString("action"),
            resourcePath = rs.getString("resourcePath"), requestSummary = rs.getString("requestSummary") ?: "",
            responseCode = rs.getInt("responseCode"),
            durationMillis = rs.getLong("durationMillis").takeIf { !rs.wasNull() },
            createdAtEpochMillis = rs.getLong("createdAtEpochMillis"),
        )

        override suspend fun insert(entry: AuditLogEntry) { withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("INSERT INTO audit_log (id,clientId,virtualInstanceId,action,resourcePath,requestSummary,responseCode,durationMillis,createdAtEpochMillis) VALUES (?,?,?,?,?,?,?,?,?)").use { ps ->
                    ps.setString(1, entry.id); ps.setString(2, entry.clientId); ps.setString(3, entry.virtualInstanceId)
                    ps.setString(4, entry.action); ps.setString(5, entry.resourcePath); ps.setString(6, entry.requestSummary)
                    ps.setInt(7, entry.responseCode)
                    val dm = entry.durationMillis; if (dm != null) ps.setLong(8, dm) else ps.setNull(8, java.sql.Types.INTEGER)
                    ps.setLong(9, entry.createdAtEpochMillis); ps.executeUpdate()
                }
            }
        } }

        override suspend fun getRecent(limit: Int, offset: Int) = withContext(Dispatchers.IO) {
            db.tx {
                val list = mutableListOf<AuditLogEntry>()
                prepareStatement("SELECT * FROM audit_log ORDER BY createdAtEpochMillis DESC LIMIT ? OFFSET ?").use { ps ->
                    ps.setInt(1, limit); ps.setInt(2, offset); ps.executeQuery().use { rs -> while (rs.next()) list += mapEntry(rs) }
                }
                list
            }
        }

        override suspend fun getByClient(clientId: GatewayClientId, limit: Int) = withContext(Dispatchers.IO) {
            db.tx {
                val list = mutableListOf<AuditLogEntry>()
                prepareStatement("SELECT * FROM audit_log WHERE clientId = ? ORDER BY createdAtEpochMillis DESC LIMIT ?").use { ps ->
                    ps.setString(1, clientId); ps.setInt(2, limit); ps.executeQuery().use { rs -> while (rs.next()) list += mapEntry(rs) }
                }
                list
            }
        }
    }

    // ═══════════ ResumeContextRepository ═══════════

    private inner class ResumeContextRepoImpl : ResumeContextRepository {

        override suspend fun getForInstance(virtualInstanceId: VirtualInstanceId) = withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT * FROM resume_contexts WHERE virtualInstanceId = ?").use { ps ->
                    ps.setString(1, virtualInstanceId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) ResumeContext(
                            virtualInstanceId = rs.getString("virtualInstanceId"),
                            lastConversationMessageId = rs.getString("lastConversationMessageId"),
                            openAiThreadSnapshot = rs.getString("openAiThreadSnapshot") ?: "[]",
                            activityState = try { ActivityState.valueOf(rs.getString("activityState")) } catch (_: Exception) { ActivityState.IDLE },
                            resumeHints = rs.getString("resumeHints") ?: "",
                            lastContextJson = rs.getString("lastContextJson") ?: "{}",
                            updatedAtEpochMillis = rs.getLong("updatedAtEpochMillis"),
                        ) else null
                    }
                }
            }
        }

        override suspend fun upsert(resumeContext: ResumeContext) { withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("INSERT OR REPLACE INTO resume_contexts (virtualInstanceId,lastConversationMessageId,openAiThreadSnapshot,activityState,resumeHints,lastContextJson,updatedAtEpochMillis) VALUES (?,?,?,?,?,?,?)").use { ps ->
                    ps.setString(1, resumeContext.virtualInstanceId); ps.setString(2, resumeContext.lastConversationMessageId)
                    ps.setString(3, resumeContext.openAiThreadSnapshot); ps.setString(4, resumeContext.activityState.name)
                    ps.setString(5, resumeContext.resumeHints); ps.setString(6, resumeContext.lastContextJson)
                    ps.setLong(7, resumeContext.updatedAtEpochMillis); ps.executeUpdate()
                }
            }
        } }
    }

    // ═══════════ SyncQueue (SQLite → SQL Server) ═══════════

    private inner class SyncQueueAccessImpl : SyncQueueAccess {

        override suspend fun enqueue(tableName: String, recordId: String, operation: String, payload: String) {
            withContext(Dispatchers.IO) {
                db.tx {
                    prepareStatement("INSERT INTO sync_queue (tableName,recordId,operation,payload,createdAtEpochMillis) VALUES (?,?,?,?,?)").use { ps ->
                        ps.setString(1, tableName); ps.setString(2, recordId); ps.setString(3, operation)
                        ps.setString(4, payload); ps.setLong(5, System.currentTimeMillis()); ps.executeUpdate()
                    }
                }
            }
        }

        override suspend fun getPending(limit: Int): List<SyncQueueEntry> = withContext(Dispatchers.IO) {
            db.tx {
                val list = mutableListOf<SyncQueueEntry>()
                prepareStatement("SELECT * FROM sync_queue WHERE syncedAtEpochMillis IS NULL ORDER BY id ASC LIMIT ?").use { ps ->
                    ps.setInt(1, limit)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) list += SyncQueueEntry(
                            id = rs.getLong("id"),
                            tableName = rs.getString("tableName"),
                            recordId = rs.getString("recordId"),
                            operation = rs.getString("operation"),
                            payload = rs.getString("payload"),
                            createdAtEpochMillis = rs.getLong("createdAtEpochMillis"),
                        )
                    }
                }
                list
            }
        }

        override suspend fun markSynced(ids: List<Long>) {
            if (ids.isEmpty()) return
            withContext(Dispatchers.IO) {
                db.tx {
                    val placeholders = ids.joinToString(",") { "?" }
                    prepareStatement("UPDATE sync_queue SET syncedAtEpochMillis = ? WHERE id IN ($placeholders)").use { ps ->
                        ps.setLong(1, System.currentTimeMillis())
                        ids.forEachIndexed { idx, id -> ps.setLong(idx + 2, id) }
                        ps.executeUpdate()
                    }
                }
            }
        }

        override suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
            db.tx {
                createStatement().use { st ->
                    st.executeQuery("SELECT COUNT(*) FROM sync_queue WHERE syncedAtEpochMillis IS NULL").use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
            }
        }

        override suspend fun cleanupSynced(olderThanEpochMillis: Long) {
            withContext(Dispatchers.IO) {
                db.tx {
                    prepareStatement("DELETE FROM sync_queue WHERE syncedAtEpochMillis IS NOT NULL AND syncedAtEpochMillis < ?").use { ps ->
                        ps.setLong(1, olderThanEpochMillis); ps.executeUpdate()
                    }
                }
            }
        }

        override suspend fun getSyncState(key: String): String? = withContext(Dispatchers.IO) {
            db.tx {
                prepareStatement("SELECT value FROM sync_state WHERE key = ?").use { ps ->
                    ps.setString(1, key); ps.executeQuery().use { rs -> if (rs.next()) rs.getString("value") else null }
                }
            }
        }

        override suspend fun setSyncState(key: String, value: String) {
            withContext(Dispatchers.IO) {
                db.tx {
                    prepareStatement("INSERT OR REPLACE INTO sync_state (key,value,updatedAtEpochMillis) VALUES (?,?,?)").use { ps ->
                        ps.setString(1, key); ps.setString(2, value); ps.setLong(3, System.currentTimeMillis()); ps.executeUpdate()
                    }
                }
            }
        }
    }
}

/**
 * Zugriff auf die Sync-Queue (SQLite → SQL Server Replikation).
 */
interface SyncQueueAccess {
    suspend fun enqueue(tableName: String, recordId: String, operation: String, payload: String)
    suspend fun getPending(limit: Int = 100): List<SyncQueueEntry>
    suspend fun markSynced(ids: List<Long>)
    suspend fun pendingCount(): Int
    suspend fun cleanupSynced(olderThanEpochMillis: Long)
    suspend fun getSyncState(key: String): String?
    suspend fun setSyncState(key: String, value: String)
}

data class SyncQueueEntry(
    val id: Long,
    val tableName: String,
    val recordId: String,
    val operation: String,
    val payload: String,
    val createdAtEpochMillis: Long,
)
