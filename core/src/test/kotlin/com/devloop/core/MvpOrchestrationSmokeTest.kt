package com.devloop.core

import com.devloop.core.domain.enums.ProjectStatus
import com.devloop.core.domain.enums.TransferMode
import com.devloop.core.domain.enums.WorkspaceType
import com.devloop.core.domain.model.InputDraft
import com.devloop.core.domain.model.Project
import com.devloop.core.domain.model.Transfer
import com.devloop.core.domain.model.Workspace
import com.devloop.core.domain.model.ProjectId
import com.devloop.core.domain.model.WorkspaceId
import com.devloop.core.domain.repository.InputDraftRepository
import com.devloop.core.domain.repository.ProjectRepository
import com.devloop.core.domain.repository.TransferRepository
import com.devloop.core.domain.repository.WorkspaceRepository
import com.devloop.core.orchestration.EnsureProjectStructureUseCase
import com.devloop.core.orchestration.InitializeMvpUseCase
import com.devloop.core.orchestration.TransferDraftUseCase
import com.devloop.core.orchestration.UpdateDraftTextUseCase
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class InMemoryProjectRepository : ProjectRepository {
    private val projectsFlow = MutableStateFlow<List<Project>>(emptyList())
    private val projectsById = LinkedHashMap<ProjectId, Project>()

    override fun observeProjects(): Flow<List<Project>> = projectsFlow

    override suspend fun getProjectById(id: ProjectId): Project? = projectsById[id]

    override suspend fun upsertProject(project: Project) {
        projectsById[project.id] = project
        projectsFlow.value = projectsById.values.toList()
    }
}

private class InMemoryWorkspaceRepository : WorkspaceRepository {
    private val workspacesByKey = LinkedHashMap<Pair<ProjectId, WorkspaceType>, Workspace>()
    private val workspacesFlows = mutableMapOf<ProjectId, MutableStateFlow<List<Workspace>>>()

    private fun flowFor(projectId: ProjectId): MutableStateFlow<List<Workspace>> {
        return workspacesFlows.getOrPut(projectId) { MutableStateFlow(emptyList()) }
    }

    override fun observeWorkspaces(projectId: ProjectId): Flow<List<Workspace>> =
        flowFor(projectId)

    override suspend fun getWorkspaceById(id: WorkspaceId): Workspace? =
        workspacesByKey.values.firstOrNull { it.id == id }

    override suspend fun getWorkspaceByType(
        projectId: ProjectId,
        type: WorkspaceType
    ): Workspace? = workspacesByKey[projectId to type]

    override suspend fun upsertWorkspace(workspace: Workspace) {
        workspacesByKey[workspace.projectId to workspace.type] = workspace
        val updated = workspacesByKey.filterKeys { it.first == workspace.projectId }.values.toList()
        flowFor(workspace.projectId).update { updated }
    }

    override suspend fun upsertWorkspaces(workspaces: List<Workspace>) {
        workspaces.forEach { upsertWorkspace(it) }
    }
}

private class InMemoryInputDraftRepository : InputDraftRepository {
    private val draftsByKey = LinkedHashMap<Pair<ProjectId, WorkspaceId>, InputDraft>()
    private val draftFlows = mutableMapOf<Pair<ProjectId, WorkspaceId>, MutableStateFlow<InputDraft?>>()

    private fun flowFor(projectId: ProjectId, workspaceId: WorkspaceId): MutableStateFlow<InputDraft?> {
        val key = projectId to workspaceId
        return draftFlows.getOrPut(key) {
            MutableStateFlow(draftsByKey[key])
        }
    }

    override fun observeDraft(projectId: ProjectId, workspaceId: WorkspaceId): Flow<InputDraft?> =
        flowFor(projectId, workspaceId)

    override suspend fun getDraft(projectId: ProjectId, workspaceId: WorkspaceId): InputDraft? =
        draftsByKey[projectId to workspaceId]

    override suspend fun getDraftById(id: String): InputDraft? =
        draftsByKey.values.firstOrNull { it.id == id }

    override suspend fun upsertDraft(draft: InputDraft) {
        draftsByKey[draft.projectId to draft.workspaceId] = draft
        flowFor(draft.projectId, draft.workspaceId).update { draft }
    }
}

private class InMemoryTransferRepository : TransferRepository {
    private val transfersByProject = LinkedHashMap<ProjectId, MutableStateFlow<List<Transfer>>>()
    private val transfers = mutableListOf<Transfer>()

    private fun flowFor(projectId: ProjectId): MutableStateFlow<List<Transfer>> {
        return transfersByProject.getOrPut(projectId) {
            MutableStateFlow(transfers.filter { it.projectId == projectId })
        }
    }

    override suspend fun insertTransfer(transfer: Transfer) {
        transfers += transfer
        // Recompute from source-of-truth to avoid double-add when the flow is created
        // after `transfers` already contains the new element.
        flowFor(transfer.projectId).update {
            transfers.filter { it.projectId == transfer.projectId }
        }
    }

    override fun observeTransfers(projectId: ProjectId): Flow<List<Transfer>> =
        flowFor(projectId)

    override fun observeTransfersFromWorkspace(
        projectId: ProjectId,
        fromWorkspaceId: WorkspaceId
    ): Flow<List<Transfer>> =
        observeTransfers(projectId).map { list -> list.filter { it.fromWorkspaceId == fromWorkspaceId } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MvpOrchestrationSmokeTest {

    @Test
    fun `initialize creates project, workspaces and empty drafts`() = runTest {
        val projectRepo = InMemoryProjectRepository()
        val workspaceRepo = InMemoryWorkspaceRepository()
        val draftRepo = InMemoryInputDraftRepository()
        val transferRepo = InMemoryTransferRepository()

        val ensure = EnsureProjectStructureUseCase(workspaceRepo, draftRepo)
        val init = InitializeMvpUseCase(
            projectRepository = projectRepo,
            ensureProjectStructureUseCase = ensure,
            createProjectUseCase = com.devloop.core.orchestration.CreateProjectUseCase(projectRepo)
        )

        val projectId = init.ensureAtLeastOneProjectExists(defaultProjectName = "Demo")

        val chat = workspaceRepo.getWorkspaceByType(projectId, WorkspaceType.CHATGPT)
        val cursor = workspaceRepo.getWorkspaceByType(projectId, WorkspaceType.CURSOR)
        assertNotNull(chat)
        assertNotNull(cursor)

        val chatDraft = draftRepo.getDraft(projectId, chat!!.id)
        val cursorDraft = draftRepo.getDraft(projectId, cursor!!.id)
        assertNotNull(chatDraft)
        assertNotNull(cursorDraft)

        assertEquals("", chatDraft!!.text)
        assertEquals("", cursorDraft!!.text)
        assertEquals(ProjectStatus.ACTIVE, projectRepo.getProjectById(projectId)!!.status)
    }

    @Test
    fun `transfer snapshots source draft text into transferredText`() = runTest {
        val projectRepo = InMemoryProjectRepository()
        val workspaceRepo = InMemoryWorkspaceRepository()
        val draftRepo = InMemoryInputDraftRepository()
        val transferRepo = InMemoryTransferRepository()

        val ensure = EnsureProjectStructureUseCase(workspaceRepo, draftRepo)
        val createProjectUseCase = com.devloop.core.orchestration.CreateProjectUseCase(projectRepo)

        val projectId = createProjectUseCase.createProject("P")
        ensure.ensureChatAndCursorEnabled(projectId)

        val chatWorkspace = workspaceRepo.getWorkspaceByType(projectId, WorkspaceType.CHATGPT)!!
        val cursorWorkspace = workspaceRepo.getWorkspaceByType(projectId, WorkspaceType.CURSOR)!!

        // 1) Source draft gets some text, then transfer.
        val updateUseCase = UpdateDraftTextUseCase(workspaceRepo, draftRepo, ensure)
        updateUseCase.updateDraftText(projectId, WorkspaceType.CHATGPT, "SNAPSHOT_1")

        val transferUseCase = TransferDraftUseCase(
            workspaceRepository = workspaceRepo,
            inputDraftRepository = draftRepo,
            transferRepository = transferRepo,
            ensureProjectStructureUseCase = ensure
        )

        transferUseCase.transferText(
            projectId = projectId,
            fromWorkspaceType = WorkspaceType.CHATGPT,
            toWorkspaceType = WorkspaceType.CURSOR,
            mode = TransferMode.COPY
        )

        // 2) Source draft changes again, but transferredText must stay as snapshot.
        updateUseCase.updateDraftText(projectId, WorkspaceType.CHATGPT, "SNAPSHOT_2")

        val transfers = transferRepo.observeTransfersFromWorkspace(
            projectId = projectId,
            fromWorkspaceId = chatWorkspace.id
        ).first()

        assertEquals(1, transfers.size)
        assertEquals("SNAPSHOT_1", transfers.single().transferredText)

        // Also target draft should reflect snapshot for COPY.
        val cursorDraft = draftRepo.getDraft(projectId, cursorWorkspace.id)
        assertEquals("SNAPSHOT_1", cursorDraft!!.text)
    }
}

