package com.devloop.core.domain.repository

import com.devloop.core.domain.model.GatewayClient
import com.devloop.core.domain.model.GatewayClientId

interface GatewayClientRepository {
    suspend fun getByApiKeyHash(hash: String): GatewayClient?
    suspend fun getById(id: GatewayClientId): GatewayClient?
    suspend fun getAll(): List<GatewayClient>
    suspend fun upsert(client: GatewayClient)
    suspend fun deactivate(id: GatewayClientId)
}
