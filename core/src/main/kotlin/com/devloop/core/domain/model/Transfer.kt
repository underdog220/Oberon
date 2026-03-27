package com.devloop.core.domain.model

import com.devloop.core.domain.enums.TransferMode

data class Transfer(
    val id: TransferId,
    val projectId: ProjectId,
    val fromWorkspaceId: WorkspaceId,
    val toWorkspaceId: WorkspaceId,
    val mode: TransferMode,
    val createdAtEpochMillis: Long,
    val sourceDraftId: InputDraftId,
    /**
     * Snapshot, unabhängig davon, wie der Draft aktuell aussieht.
     * Damit Transfers reproduzierbar und historisierbar werden.
     */
    val transferredText: String
)

