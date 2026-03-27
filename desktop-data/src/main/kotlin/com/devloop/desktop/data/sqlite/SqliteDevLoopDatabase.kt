package com.devloop.desktop.data.sqlite

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Single-file SQLite DB (desktop bridge). Schema aligned with Android Room entities + integration_targets.
 */
class SqliteDevLoopDatabase(dbPath: Path) {

    private val jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

    private val connection: Connection by lazy {
        Files.createDirectories(dbPath.parent)
        DriverManager.getConnection(jdbcUrl).also { conn ->
            conn.createStatement().use { st ->
                st.execute("PRAGMA foreign_keys = ON")
            }
            initSchema(conn)
        }
    }

    @Synchronized
    fun <T> tx(block: Connection.() -> T): T = connection.block()

    private fun initSchema(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS projects (
                  id TEXT PRIMARY KEY NOT NULL,
                  name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  createdAtEpochMillis INTEGER NOT NULL
                );
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS workspaces (
                  id TEXT PRIMARY KEY NOT NULL,
                  projectId TEXT NOT NULL,
                  type TEXT NOT NULL,
                  displayName TEXT NOT NULL,
                  createdAtEpochMillis INTEGER NOT NULL
                );
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS input_drafts (
                  id TEXT PRIMARY KEY NOT NULL,
                  projectId TEXT NOT NULL,
                  workspaceId TEXT NOT NULL,
                  createdAtEpochMillis INTEGER NOT NULL,
                  updatedAtEpochMillis INTEGER NOT NULL,
                  text TEXT NOT NULL,
                  inputChannel TEXT
                );
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS transfers (
                  id TEXT PRIMARY KEY NOT NULL,
                  projectId TEXT NOT NULL,
                  fromWorkspaceId TEXT NOT NULL,
                  toWorkspaceId TEXT NOT NULL,
                  mode TEXT NOT NULL,
                  createdAtEpochMillis INTEGER NOT NULL,
                  sourceDraftId TEXT NOT NULL,
                  transferredText TEXT NOT NULL
                );
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS integration_targets (
                  id TEXT PRIMARY KEY NOT NULL,
                  projectId TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  label TEXT NOT NULL,
                  configJson TEXT NOT NULL,
                  createdAtEpochMillis INTEGER NOT NULL
                );
                """.trimIndent()
            )
            migrateProjectDesktopColumns(conn)
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS supervisor_portfolio_context (
                  projectId TEXT PRIMARY KEY NOT NULL,
                  json TEXT NOT NULL,
                  updatedAtEpochMillis INTEGER NOT NULL
                );
                """.trimIndent(),
            )

            // —— Zentrale KI-Plattform Tabellen ——

            st.execute("""
                CREATE TABLE IF NOT EXISTS virtual_instances (
                  id TEXT PRIMARY KEY NOT NULL,
                  projectId TEXT NOT NULL,
                  label TEXT NOT NULL,
                  instanceType TEXT NOT NULL,
                  status TEXT NOT NULL,
                  scopeJson TEXT NOT NULL DEFAULT '{}',
                  createdAtEpochMillis INTEGER NOT NULL,
                  lastActiveAtEpochMillis INTEGER NOT NULL
                );
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS conversation_messages (
                  id TEXT PRIMARY KEY NOT NULL,
                  virtualInstanceId TEXT NOT NULL,
                  role TEXT NOT NULL,
                  content TEXT NOT NULL,
                  contentType TEXT NOT NULL DEFAULT 'TEXT',
                  metadata TEXT NOT NULL DEFAULT '{}',
                  createdAtEpochMillis INTEGER NOT NULL
                );
            """.trimIndent())
            st.execute("CREATE INDEX IF NOT EXISTS idx_conv_msg_instance ON conversation_messages(virtualInstanceId, createdAtEpochMillis)")

            st.execute("""
                CREATE TABLE IF NOT EXISTS memory_entries (
                  id TEXT PRIMARY KEY NOT NULL,
                  virtualInstanceId TEXT,
                  projectId TEXT,
                  memoryKind TEXT NOT NULL,
                  category TEXT NOT NULL DEFAULT '',
                  title TEXT NOT NULL,
                  content TEXT NOT NULL,
                  relevanceScore REAL NOT NULL DEFAULT 1.0,
                  validFromEpochMillis INTEGER,
                  validUntilEpochMillis INTEGER,
                  createdAtEpochMillis INTEGER NOT NULL,
                  updatedAtEpochMillis INTEGER NOT NULL
                );
            """.trimIndent())
            st.execute("CREATE INDEX IF NOT EXISTS idx_mem_instance ON memory_entries(virtualInstanceId)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_mem_project ON memory_entries(projectId)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_mem_kind ON memory_entries(memoryKind)")

            st.execute("""
                CREATE TABLE IF NOT EXISTS resume_contexts (
                  virtualInstanceId TEXT PRIMARY KEY NOT NULL,
                  lastConversationMessageId TEXT,
                  openAiThreadSnapshot TEXT NOT NULL DEFAULT '[]',
                  activityState TEXT NOT NULL DEFAULT 'IDLE',
                  resumeHints TEXT NOT NULL DEFAULT '',
                  lastContextJson TEXT NOT NULL DEFAULT '{}',
                  updatedAtEpochMillis INTEGER NOT NULL
                );
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS gateway_clients (
                  id TEXT PRIMARY KEY NOT NULL,
                  clientName TEXT NOT NULL,
                  clientKind TEXT NOT NULL,
                  apiKeyHash TEXT NOT NULL,
                  scopesJson TEXT NOT NULL DEFAULT '[]',
                  permissionsJson TEXT NOT NULL DEFAULT '{}',
                  isActive INTEGER NOT NULL DEFAULT 1,
                  createdAtEpochMillis INTEGER NOT NULL,
                  lastSeenAtEpochMillis INTEGER
                );
            """.trimIndent())

            st.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                  id TEXT PRIMARY KEY NOT NULL,
                  clientId TEXT,
                  virtualInstanceId TEXT,
                  action TEXT NOT NULL,
                  resourcePath TEXT NOT NULL,
                  requestSummary TEXT NOT NULL DEFAULT '',
                  responseCode INTEGER NOT NULL,
                  durationMillis INTEGER,
                  createdAtEpochMillis INTEGER NOT NULL
                );
            """.trimIndent())
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log(createdAtEpochMillis)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_client ON audit_log(clientId)")

            // —— Sync-Queue fuer SQL Server Replikation ——
            st.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  tableName TEXT NOT NULL,
                  recordId TEXT NOT NULL,
                  operation TEXT NOT NULL,
                  payload TEXT NOT NULL,
                  createdAtEpochMillis INTEGER NOT NULL,
                  syncedAtEpochMillis INTEGER
                );
            """.trimIndent())
            st.execute("CREATE INDEX IF NOT EXISTS idx_sync_pending ON sync_queue(syncedAtEpochMillis)")

            st.execute("""
                CREATE TABLE IF NOT EXISTS sync_state (
                  key TEXT PRIMARY KEY NOT NULL,
                  value TEXT NOT NULL,
                  updatedAtEpochMillis INTEGER NOT NULL
                );
            """.trimIndent())

            migrateDomainColumns(conn)
        }
    }

    private fun migrateDomainColumns(conn: Connection) {
        fun addColumn(sql: String) {
            try { conn.createStatement().use { it.execute(sql) } } catch (_: Exception) {}
        }
        addColumn("ALTER TABLE virtual_instances ADD COLUMN domain TEXT NOT NULL DEFAULT 'SYSTEM'")
        addColumn("ALTER TABLE memory_entries ADD COLUMN domain TEXT NOT NULL DEFAULT 'SYSTEM'")
        addColumn("ALTER TABLE conversation_messages ADD COLUMN domain TEXT NOT NULL DEFAULT 'SYSTEM'")
        addColumn("ALTER TABLE audit_log ADD COLUMN domain TEXT NOT NULL DEFAULT 'SYSTEM'")
        addColumn("ALTER TABLE gateway_clients ADD COLUMN allowedDomains TEXT NOT NULL DEFAULT '[\"*\"]'")
        // Domain-Index fuer schnelle Filterung
        try { conn.createStatement().use { it.execute("CREATE INDEX IF NOT EXISTS idx_vi_domain ON virtual_instances(domain)") } } catch (_: Exception) {}
        try { conn.createStatement().use { it.execute("CREATE INDEX IF NOT EXISTS idx_mem_domain ON memory_entries(domain)") } } catch (_: Exception) {}
    }

    private fun migrateProjectDesktopColumns(conn: Connection) {
        fun addColumn(sql: String) {
            try {
                conn.createStatement().use { it.execute(sql) }
            } catch (_: Exception) {
                // Spalte existiert bereits
            }
        }
        addColumn("ALTER TABLE projects ADD COLUMN desktopPrimaryCodingAgentId TEXT")
        addColumn("ALTER TABLE projects ADD COLUMN desktopCodexSandbox TEXT")
        addColumn("ALTER TABLE projects ADD COLUMN desktopPreferredIntegrationTargetId TEXT")
    }
}
