package com.devloop.platform

import com.devloop.core.domain.enums.GatewayClientKind
import com.devloop.core.domain.model.GatewayClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeResolverTest {

    private val resolver = ScopeResolver()

    private fun client(scopes: String = "[\"*\"]", active: Boolean = true) = GatewayClient(
        id = "test-client", clientName = "Test", clientKind = GatewayClientKind.EXTERNAL,
        apiKeyHash = "hash", scopesJson = scopes, isActive = active,
        createdAtEpochMillis = System.currentTimeMillis(),
    )

    @Test
    fun `wildcard scope allows everything`() {
        val decision = resolver.resolve(client("[\"*\"]"), "SEND_MESSAGE", "instance-1")
        assertTrue(decision.allowed)
    }

    @Test
    fun `deactivated client is denied`() {
        val decision = resolver.resolve(client("[\"*\"]", active = false), "SEND_MESSAGE", "instance-1")
        assertFalse(decision.allowed)
    }

    @Test
    fun `readonly scope allows GET actions`() {
        val decision = resolver.resolve(client("[\"readonly\"]"), "GET_INSTANCES", null)
        assertTrue(decision.allowed)
    }

    @Test
    fun `readonly scope denies write actions`() {
        val decision = resolver.resolve(client("[\"readonly\"]"), "SEND_MESSAGE", null)
        assertFalse(decision.allowed)
    }

    @Test
    fun `instance scope matches specific instance`() {
        val decision = resolver.resolve(client("[\"instance:abc-123\"]"), "SEND_MESSAGE", "abc-123")
        assertTrue(decision.allowed)
    }

    @Test
    fun `instance scope denies different instance`() {
        val decision = resolver.resolve(client("[\"instance:abc-123\"]"), "SEND_MESSAGE", "xyz-456")
        assertFalse(decision.allowed)
    }

    @Test
    fun `action scope matches specific action`() {
        val decision = resolver.resolve(client("[\"action:QUERY_MEMORY\"]"), "QUERY_MEMORY", null)
        assertTrue(decision.allowed)
    }

    @Test
    fun `empty scopes deny everything`() {
        val decision = resolver.resolve(client("[]"), "SEND_MESSAGE", null)
        assertFalse(decision.allowed)
    }
}
