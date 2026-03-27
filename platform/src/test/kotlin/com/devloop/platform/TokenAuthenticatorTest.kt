package com.devloop.platform

import com.devloop.core.domain.enums.GatewayClientKind
import com.devloop.core.domain.model.GatewayClient
import com.devloop.core.domain.model.GatewayClientId
import com.devloop.core.domain.repository.GatewayClientRepository
import com.devloop.platform.auth.AuthResult
import com.devloop.platform.auth.TokenAuthenticator
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class TokenAuthenticatorTest {

    private val testApiKey = "test-key-12345"
    private val testClient = GatewayClient(
        id = "client-1", clientName = "TestClient", clientKind = GatewayClientKind.DEVLOOP_DESKTOP,
        apiKeyHash = TokenAuthenticator.sha256(testApiKey), isActive = true,
        createdAtEpochMillis = System.currentTimeMillis(),
    )

    private val repo = object : GatewayClientRepository {
        override suspend fun getByApiKeyHash(hash: String): GatewayClient? =
            if (hash == testClient.apiKeyHash) testClient else null
        override suspend fun getById(id: GatewayClientId) = null
        override suspend fun getAll() = listOf(testClient)
        override suspend fun upsert(client: GatewayClient) {}
        override suspend fun deactivate(id: GatewayClientId) {}
    }

    private val auth = TokenAuthenticator("legacy-token", repo)

    @Test
    fun `client API key authenticates successfully`() {
        val result = auth.authenticate(testApiKey)
        assertIs<AuthResult.Authenticated>(result)
        assert(result.client != null)
        assert(result.client!!.id == "client-1")
    }

    @Test
    fun `legacy token authenticates without client`() {
        val result = auth.authenticate("legacy-token")
        assertIs<AuthResult.Authenticated>(result)
        assertNull(result.client)
    }

    @Test
    fun `invalid token is denied`() {
        val result = auth.authenticate("wrong-token")
        assertIs<AuthResult.Denied>(result)
    }

    @Test
    fun `empty token is denied`() {
        val result = auth.authenticate("")
        assertIs<AuthResult.Denied>(result)
    }

    @Test
    fun `sha256 is deterministic`() {
        val hash1 = TokenAuthenticator.sha256("test")
        val hash2 = TokenAuthenticator.sha256("test")
        assert(hash1 == hash2)
        assert(hash1.length == 64) // SHA-256 = 64 hex chars
    }
}
