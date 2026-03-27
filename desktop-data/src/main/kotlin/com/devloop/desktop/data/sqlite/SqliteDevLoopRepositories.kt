package com.devloop.desktop.data.sqlite

import com.devloop.core.domain.enums.IntegrationTargetKind
import com.devloop.core.domain.enums.ProjectStatus
import com.devloop.core.domain.enums.TransferMode
import com.devloop.core.domain.enums.WorkspaceType
import com.devloop.core.domain.model.InputDraft
import com.devloop.core.domain.model.InputDraftId
import com.devloop.core.domain.model.IntegrationTarget
import com.devloop.core.domain.model.IntegrationTargetId
import com.devloop.core.domain.model.Project
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.Transfer
import com.devloop.core.domain.model.Workspace
import com.devloop.core.domain.model.WorkspaceId
import com.devloop.core.domain.repository.InputDraftRepository
import com.devloop.core.domain.repository.IntegrationTargetRepository
import com.devloop.core.domain.repository.ProjectRepository
import com.devloop.core.domain.repository.TransferRepository
import com.devloop.core.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet

/**
 * SQLite-backed repositories for the desktop bridge.
 */
class SqliteDevLoopRepositories(dbPath: Path) :
    ProjectRepository,
    WorkspaceRepository,
    InputDraftRepository,
    TransferRepository,
    IntegrationTargetRepository {

    /** Zugriff auf die unterliegende Datenbank fuer SqlitePlatformRepositories. */
    val database = SqliteDevLoopDatabase(dbPath)
    private val db get() = database

    private val projectsCache = MutableStateFlow<List<Project>>(emptyList())
    private val workspacesCache = MutableStateFlow<List<Workspace>>(emptyList())
    private val draftsCache = MutableStateFlow<List<InputDraft>>(emptyList())
    private val transfersCache = MutableStateFlow<List<Transfer>>(emptyList())
    private val targetsCache = MutableStateFlow<List<IntegrationTarget>>(emptyList())

    init {
        refreshAll()
    }

    fun refreshAll() {
        db.tx {
            projectsCache.value = loadProjects()
            workspacesCache.value = loadWorkspaces()
            draftsCache.value = loadDrafts()
            transfersCache.value = loadTransfers()
            targetsCache.value = loadTargets()
        }
    }

    /** Nur Entwürfe — vermeidet Voll-Refresh bei häufigem Draft-Persist (weniger UI-Churn). */
    private fun refreshDraftsOnly() {
        db.tx {
            draftsCache.value = loadDrafts()
        }
    }

    /** Nur Transfers (nach insertTransfer). */
    private fun refreshTransfersOnly() {
        db.tx {
            transfersCache.value = loadTransfers()
        }
    }

    override fun observeProjects(): Flow<List<Project>> = projectsCache.asStateFlow()

    override suspend fun getProjectById(id: ProjectId): Project? = withContext(Dispatchers.IO) {
        projectsCache.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertProject(project: Project) = withContext(Dispatchers.IO) {
        db.tx { upsertProjectRow(project) }
        refreshAll()
    }

    override fun observeWorkspaces(projectId: ProjectId): Flow<List<Workspace>> =
        workspacesCache.map { list -> list.filter { it.projectId == projectId } }.distinctUntilChanged()

    override suspend fun getWorkspaceById(id: WorkspaceId): Workspace? = withContext(Dispatchers.IO) {
        workspacesCache.value.firstOrNull { it.id == id }
    }

    override suspend fun getWorkspaceByType(projectId: ProjectId, type: WorkspaceType): Workspace? =
        withContext(Dispatchers.IO) {
            workspacesCache.value.firstOrNull { it.projectId == projectId && it.type == type }
        }

    override suspend fun upsertWorkspace(workspace: Workspace) = withContext(Dispatchers.IO) {
        db.tx { upsertWorkspaceRow(workspace) }
        refreshAll()
    }

    override suspend fun upsertWorkspaces(workspaces: List<Workspace>) = withContext(Dispatchers.IO) {
        db.tx { workspaces.forEach { upsertWorkspaceRow(it) } }
        refreshAll()
    }

    override fun observeDraft(projectId: ProjectId, workspaceId: WorkspaceId): Flow<InputDraft?> =
        draftsCache
            .map { list -> list.firstOrNull { it.projectId == projectId && it.workspaceId == workspaceId } }
            .distinctUntilChanged()

    override suspend fun getDraft(projectId: ProjectId, workspaceId: WorkspaceId): InputDraft? =
        withContext(Dispatchers.IO) {
            draftsCache.value.firstOrNull { it.projectId == projectId && it.workspaceId == workspaceId }
        }

    override suspend fun getDraftById(id: InputDraftId): InputDraft? = withContext(Dispatchers.IO) {
        draftsCache.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertDraft(draft: InputDraft) = withContext(Dispatchers.IO) {
        db.tx { upsertDraftRow(draft) }
        refreshDraftsOnly()
    }

    override suspend fun insertTransfer(transfer: Transfer) = withContext(Dispatchers.IO) {
        db.tx { insertTransferRow(transfer) }
        refreshTransfersOnly()
    }

    override fun observeTransfers(projectId: ProjectId): Flow<List<Transfer>> =
        transfersCache
            .map { list -> list.filter { it.projectId == projectId }.sortedBy { it.createdAtEpochMillis } }
            .distinctUntilChanged()

    override fun observeTransfersFromWorkspace(
        projectId: ProjectId,
        fromWorkspaceId: WorkspaceId
    ): Flow<List<Transfer>> =
        transfersCache
            .map { list ->
                list.filter { it.projectId == projectId && it.fromWorkspaceId == fromWorkspaceId }
                    .sortedBy { it.createdAtEpochMillis }
            }
            .distinctUntilChanged()

    override fun observeTargets(projectId: ProjectId): Flow<List<IntegrationTarget>> =
        targetsCache
            .map { list -> list.filter { it.projectId == projectId } }
            .distinctUntilChanged()

    override suspend fun getTarget(id: IntegrationTargetId): IntegrationTarget? = withContext(Dispatchers.IO) {
        targetsCache.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertTarget(target: IntegrationTarget) = withContext(Dispatchers.IO) {
        db.tx { upsertTargetRow(target) }
        refreshAll()
    }

    override suspend fun deleteTarget(id: IntegrationTargetId) = withContext(Dispatchers.IO) {
        db.tx {
            prepareStatement("DELETE FROM integration_targets WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
        }
        refreshAll()
    }

    /** Supervisor Portfolio Context (JSON pro Projekt). */
    fun loadSupervisorPortfolioJson(projectId: String): String? =
        db.tx { loadSupervisorPortfolioRow(projectId) }

    fun upsertSupervisorPortfolioJson(projectId: String, json: String) {
        db.tx { upsertSupervisorPortfolioRow(projectId, json, System.currentTimeMillis()) }
    }
}

private fun Connection.loadProjects(): List<Project> {
    val out = ArrayList<Project>()
    createStatement().use { st ->
        st.executeQuery(
            "SELECT id, name, status, createdAtEpochMillis, desktopPrimaryCodingAgentId, desktopCodexSandbox, " +
                "desktopPreferredIntegrationTargetId FROM projects ORDER BY createdAtEpochMillis DESC"
        ).use { rs ->
            while (rs.next()) {
                out.add(rs.toProject())
            }
        }
    }
    return out
}

private fun Connection.loadWorkspaces(): List<Workspace> {
    val out = ArrayList<Workspace>()
    createStatement().use { st ->
        st.executeQuery("SELECT * FROM workspaces").use { rs ->
            while (rs.next()) {
                out.add(rs.toWorkspace())
            }
        }
    }
    return out
}

private fun Connection.loadDrafts(): List<InputDraft> {
    val out = ArrayList<InputDraft>()
    createStatement().use { st ->
        st.executeQuery("SELECT * FROM input_drafts").use { rs ->
            while (rs.next()) {
                out.add(rs.toDraft())
            }
        }
    }
    return out
}

private fun Connection.loadTransfers(): List<Transfer> {
    val out = ArrayList<Transfer>()
    createStatement().use { st ->
        st.executeQuery("SELECT * FROM transfers").use { rs ->
            while (rs.next()) {
                out.add(rs.toTransfer())
            }
        }
    }
    return out
}

private fun Connection.loadTargets(): List<IntegrationTarget> {
    val out = ArrayList<IntegrationTarget>()
    createStatement().use { st ->
        st.executeQuery("SELECT * FROM integration_targets").use { rs ->
            while (rs.next()) {
                out.add(rs.toTarget())
            }
        }
    }
    return out
}

private fun Connection.upsertProjectRow(p: Project) {
    prepareStatement(
        """
        INSERT OR REPLACE INTO projects (
          id, name, status, createdAtEpochMillis,
          desktopPrimaryCodingAgentId, desktopCodexSandbox, desktopPreferredIntegrationTargetId
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, p.id)
        ps.setString(2, p.name)
        ps.setString(3, p.status.name)
        ps.setLong(4, p.createdAtEpochMillis)
        ps.setString(5, p.desktopPrimaryCodingAgentId)
        ps.setString(6, p.desktopCodexSandbox)
        ps.setString(7, p.desktopPreferredIntegrationTargetId)
        ps.executeUpdate()
    }
}

private fun Connection.upsertWorkspaceRow(w: Workspace) {
    prepareStatement(
        """
        INSERT OR REPLACE INTO workspaces (id, projectId, type, displayName, createdAtEpochMillis)
        VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, w.id)
        ps.setString(2, w.projectId)
        ps.setString(3, w.type.name)
        ps.setString(4, w.displayName)
        ps.setLong(5, w.createdAtEpochMillis)
        ps.executeUpdate()
    }
}

private fun Connection.upsertDraftRow(d: InputDraft) {
    prepareStatement(
        """
        INSERT OR REPLACE INTO input_drafts
        (id, projectId, workspaceId, createdAtEpochMillis, updatedAtEpochMillis, text, inputChannel)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, d.id)
        ps.setString(2, d.projectId)
        ps.setString(3, d.workspaceId)
        ps.setLong(4, d.createdAtEpochMillis)
        ps.setLong(5, d.updatedAtEpochMillis)
        ps.setString(6, d.text)
        if (d.inputChannel == null) ps.setString(7, null) else ps.setString(7, d.inputChannel)
        ps.executeUpdate()
    }
}

private fun Connection.insertTransferRow(t: Transfer) {
    prepareStatement(
        """
        INSERT INTO transfers
        (id, projectId, fromWorkspaceId, toWorkspaceId, mode, createdAtEpochMillis, sourceDraftId, transferredText)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, t.id)
        ps.setString(2, t.projectId)
        ps.setString(3, t.fromWorkspaceId)
        ps.setString(4, t.toWorkspaceId)
        ps.setString(5, t.mode.name)
        ps.setLong(6, t.createdAtEpochMillis)
        ps.setString(7, t.sourceDraftId)
        ps.setString(8, t.transferredText)
        ps.executeUpdate()
    }
}

private fun Connection.upsertTargetRow(t: IntegrationTarget) {
    prepareStatement(
        """
        INSERT OR REPLACE INTO integration_targets
        (id, projectId, kind, label, configJson, createdAtEpochMillis)
        VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, t.id)
        ps.setString(2, t.projectId)
        ps.setString(3, t.kind.name)
        ps.setString(4, t.label)
        ps.setString(5, t.configJson)
        ps.setLong(6, t.createdAtEpochMillis)
        ps.executeUpdate()
    }
}

private fun ResultSet.toProject(): Project = Project(
    id = getString("id"),
    name = getString("name"),
    status = ProjectStatus.valueOf(getString("status")),
    createdAtEpochMillis = getLong("createdAtEpochMillis"),
    desktopPrimaryCodingAgentId = getNullableString("desktopPrimaryCodingAgentId"),
    desktopCodexSandbox = getNullableString("desktopCodexSandbox"),
    desktopPreferredIntegrationTargetId = getNullableString("desktopPreferredIntegrationTargetId"),
)

private fun ResultSet.getNullableString(column: String): String? {
    val v = getString(column)
    return if (wasNull()) null else v?.trim()?.takeIf { it.isNotEmpty() }
}

private fun ResultSet.toWorkspace(): Workspace = Workspace(
    id = getString("id"),
    projectId = getString("projectId"),
    type = WorkspaceType.valueOf(getString("type")),
    displayName = getString("displayName"),
    createdAtEpochMillis = getLong("createdAtEpochMillis")
)

private fun ResultSet.toDraft(): InputDraft = InputDraft(
    id = getString("id"),
    projectId = getString("projectId"),
    workspaceId = getString("workspaceId"),
    createdAtEpochMillis = getLong("createdAtEpochMillis"),
    updatedAtEpochMillis = getLong("updatedAtEpochMillis"),
    text = getString("text"),
    inputChannel = getString("inputChannel")
)

private fun ResultSet.toTransfer(): Transfer = Transfer(
    id = getString("id"),
    projectId = getString("projectId"),
    fromWorkspaceId = getString("fromWorkspaceId"),
    toWorkspaceId = getString("toWorkspaceId"),
    mode = TransferMode.valueOf(getString("mode")),
    createdAtEpochMillis = getLong("createdAtEpochMillis"),
    sourceDraftId = getString("sourceDraftId"),
    transferredText = getString("transferredText")
)

private fun ResultSet.toTarget(): IntegrationTarget = IntegrationTarget(
    id = getString("id"),
    projectId = getString("projectId"),
    kind = IntegrationTargetKind.valueOf(getString("kind")),
    label = getString("label"),
    configJson = getString("configJson"),
    createdAtEpochMillis = getLong("createdAtEpochMillis")
)

private fun Connection.loadSupervisorPortfolioRow(projectId: String): String? {
    prepareStatement("SELECT json FROM supervisor_portfolio_context WHERE projectId = ?").use { ps ->
        ps.setString(1, projectId)
        ps.executeQuery().use { rs ->
            return if (rs.next()) rs.getString(1) else null
        }
    }
}

private fun Connection.upsertSupervisorPortfolioRow(projectId: String, json: String, updatedAt: Long) {
    prepareStatement(
        """
        INSERT INTO supervisor_portfolio_context (projectId, json, updatedAtEpochMillis)
        VALUES (?,?,?)
        ON CONFLICT(projectId) DO UPDATE SET
          json = excluded.json,
          updatedAtEpochMillis = excluded.updatedAtEpochMillis
        """.trimIndent(),
    ).use { ps ->
        ps.setString(1, projectId)
        ps.setString(2, json)
        ps.setLong(3, updatedAt)
        ps.executeUpdate()
    }
}
