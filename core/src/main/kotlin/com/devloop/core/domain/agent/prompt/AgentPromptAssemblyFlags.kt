package com.devloop.core.domain.agent.prompt

/**
 * Which path produced [BuiltCodingAgentPrompt.promptForAgent] (for forensic tracing).
 */
data class AgentPromptAssemblyFlags(
    val internalComposerUsed: Boolean = false,
    val codexTemplateUsed: Boolean = false,
    val directRawIntentAsPrompt: Boolean = false,
    /** UI „Prompt an Coding-Agent“ direkt: kein Template/Composer/Appendix in der Desktop-Pipeline. */
    val manualAgentFieldBypass: Boolean = false,
)
