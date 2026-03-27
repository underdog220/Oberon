package com.devloop.core.domain.enums

/**
 * How a [com.devloop.core.domain.model.IntegrationTarget] connects to Cursor-related tooling.
 * Not tied to desktop windows — session/process/cloud identifiers only.
 */
enum class IntegrationTargetKind {
    /** Local repo + Cursor CLI / agent subprocess. */
    CLI_WORKDIR,

    /** Agent Client Protocol (editor-attached); config holds endpoint/session refs. */
    ACP_SESSION,

    /** Cursor Cloud Agents API — async cloud agent id. */
    CLOUD_AGENT
}
