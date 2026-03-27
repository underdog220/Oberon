package com.devloop.platform.auth

import com.devloop.core.domain.model.GatewayClient
import com.devloop.core.domain.repository.GatewayClientRepository
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.logging.Logger

/**
 * Kern-Authentifizierungslogik (plattformunabhaengig, kein HTTP-Framework).
 *
 * Prueft Bearer-Token gegen:
 * 1. Per-Client API-Key (SHA-256 Hash in DB)
 * 2. Legacy Shared Token (Fallback)
 */
class TokenAuthenticator(
    private val legacyToken: String,
    private val clientRepository: GatewayClientRepository,
) {
    private val log = Logger.getLogger("TokenAuthenticator")

    fun authenticate(bearerToken: String): AuthResult {
        if (bearerToken.isBlank()) return AuthResult.Denied("Kein Token")

        // 1. Per-Client API-Key
        val keyHash = sha256(bearerToken)
        val client = runBlocking { clientRepository.getByApiKeyHash(keyHash) }
        if (client != null) {
            runBlocking { clientRepository.upsert(client.copy(lastSeenAtEpochMillis = System.currentTimeMillis())) }
            return AuthResult.Authenticated(client)
        }

        // 2. Legacy Token
        if (bearerToken == legacyToken) {
            return AuthResult.Authenticated(client = null)
        }

        return AuthResult.Denied("Ungueltiger Token")
    }

    companion object {
        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}

sealed class AuthResult {
    data class Authenticated(val client: GatewayClient?) : AuthResult()
    data class Denied(val reason: String) : AuthResult()
}
