package com.devloop.core.domain.agent

/**
 * Reserved for a second-pass review agent (e.g. different model). Not wired in the UI yet.
 */
data class ReviewResponse(
    val reviewerAgentId: String,
    val text: String,
    val ok: Boolean = true,
)
