package com.devloop.core.domain.agent

/**
 * How the Codex CLI adapter invokes `codex exec` against the repo.
 *
 * - [READ_ONLY]: `codex exec "<prompt>"` (no `--sandbox workspace-write`).
 * - [WORKSPACE_WRITE]: `codex exec --sandbox workspace-write "<prompt>"`.
 */
enum class CodexSandboxMode {
    READ_ONLY,
    WORKSPACE_WRITE,
}
