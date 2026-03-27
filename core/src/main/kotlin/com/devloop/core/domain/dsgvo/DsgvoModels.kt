package com.devloop.core.domain.dsgvo

import java.time.Instant
import java.time.LocalDate

// PII-Scan Ergebnis
data class PiiScanResult(
    val textLength: Int,
    val matches: List<PiiMatchResult>,
    val scanDurationMs: Long,
    val scannerUsed: String, // "FAST", "DEEP", "FAST+DEEP"
)

data class PiiMatchResult(
    val category: String,     // "PERSON", "IBAN", "FLUR" etc.
    val originalText: String,
    val position: IntRange,
    val confidence: Double,
)

// Anonymisierungs-Mapping
data class AnonymizationMapping(
    val mappingId: String,
    val sessionId: String,
    val clientId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val mappingCount: Int,
    val used: Boolean,
)

// DSGVO Proxy Request/Response
data class DsgvoProxyRequest(
    val clientId: String,
    val domain: String,
    val prompt: String,
    val systemPrompt: String? = null,
    val model: String? = null,
    val maxTokens: Int = 2000,
    val workspaceId: String? = null,
)

data class DsgvoProxyResponse(
    val status: String,
    val response: String,
    val piiFound: Boolean,
    val piiTypes: List<String>,
    val anonymized: Boolean,
    val routingDecision: String,
    val auditId: String,
    val durationMs: Long,
)

// DSGVO Sanitize Request/Response
data class DsgvoSanitizeRequest(
    val clientId: String,
    val domain: String,
    val prompt: String,
    val systemPrompt: String? = null,
)

data class DsgvoSanitizeResponse(
    val status: String,
    val sanitizedPrompt: String,
    val sanitizedSystemPrompt: String?,
    val mappingId: String,
    val piiFound: Boolean,
    val piiTypes: List<String>,
    val auditId: String,
)

// DSGVO De-Anonymisierung
data class DsgvoDeanonymizeRequest(
    val mappingId: String,
    val response: String,
)

data class DsgvoDeanonymizeResponse(
    val status: String,
    val deanonymizedResponse: String,
    val auditId: String,
)

// Fallback-Log
data class FallbackLogEntry(
    val timestamp: Instant,
    val clientId: String,
    val domain: String,
    val fallbackGrund: String,
    val promptHash: String,
    val piiGeprueft: Boolean,
)

// Tagesbericht
data class DsgvoTagesberichtData(
    val datum: LocalDate,
    val gesamtAnfragen: Int,
    val davonLokal: Int,
    val davonAnonymisiert: Int,
    val davonExtern: Int,
    val davonProxy: Int,
    val davonKorrektor: Int,
    val davonFallback: Int,
    val piiTypenStatistik: Map<String, Int>,
    val keineExterneUebertragungMitPii: Boolean,
)
