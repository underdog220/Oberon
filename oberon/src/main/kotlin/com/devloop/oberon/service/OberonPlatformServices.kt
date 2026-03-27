package com.devloop.oberon.service

import com.devloop.desktop.data.sqlite.SqliteDevLoopDatabase
import com.devloop.desktop.data.sqlite.SqlitePlatformRepositories
import com.devloop.oberon.OberonConfig
import com.devloop.oberon.dsgvo.DsgvoAuditLogger
import com.devloop.oberon.dsgvo.DsgvoService
import com.devloop.oberon.llm.OberonLlmService
import com.devloop.platform.*
import com.devloop.platform.auth.AuditService
import com.devloop.platform.auth.TokenAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Verdrahtet alle Oberon-Services (analog zu DesktopBootstrap fuer den Server-Betrieb).
 */
class OberonPlatformServices private constructor(
    val config: OberonConfig,
    val scope: CoroutineScope,
    val repos: SqlitePlatformRepositories,
    val instanceManager: VirtualInstanceManager,
    val conversationPersistence: ConversationPersistenceService,
    val contextBooster: ContextBoosterService,
    val contextAssembler: SupervisorContextAssembler,
    val scopeResolver: ScopeResolver,
    val dataSyncService: DataSyncService,
    val tokenAuth: TokenAuthenticator,
    val auditService: AuditService,
    val llmService: OberonLlmService,
    /** DSGVO-Service fuer PII-Scanning, Anonymisierung und Audit */
    val dsgvoService: DsgvoService,
) {
    companion object {
        fun create(config: OberonConfig): OberonPlatformServices {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val db = SqliteDevLoopDatabase(config.dbPath)
            val repos = SqlitePlatformRepositories(db)

            val instanceManager = VirtualInstanceManager(repos.instances)
            val conversationPersistence = ConversationPersistenceService(repos.conversations, repos.resumeContexts)
            val contextBooster = ContextBoosterService(repos.instances, repos.memory, repos.conversations, repos.resumeContexts)
            val contextAssembler = SupervisorContextAssembler(repos.conversations, repos.resumeContexts, contextBooster)
            val scopeResolver = ScopeResolver()
            val tokenAuth = TokenAuthenticator(config.token, repos.gatewayClients)
            val auditService = AuditService(repos.auditLog)

            val dataSyncService = DataSyncService(repos.syncQueue, scope, config.mssqlJdbcUrl, config.syncIntervalMs)
            dataSyncService.start()

            val llmService = OberonLlmService(config)

            // DSGVO-Service initialisieren
            val dsgvoAuditLogger = DsgvoAuditLogger(config.dataDir.toString())
            val dsgvoService = DsgvoService(config, dsgvoAuditLogger)

            return OberonPlatformServices(
                config, scope, repos, instanceManager, conversationPersistence,
                contextBooster, contextAssembler, scopeResolver, dataSyncService,
                tokenAuth, auditService, llmService, dsgvoService,
            )
        }
    }
}
