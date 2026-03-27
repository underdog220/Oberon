package com.devloop.platform

import com.devloop.core.domain.model.GatewayClient
import com.devloop.core.domain.model.VirtualInstanceId
import org.json.JSONArray
import java.util.logging.Logger

/**
 * Prueft ob ein Gateway-Client eine bestimmte Aktion auf einem Fokusraum ausfuehren darf.
 *
 * Evaluiert das scopesJson des Clients gegen die angeforderte Ressource.
 * Scopes-Format: JSON-Array mit Strings, z. B.:
 * - "*" = alles erlaubt
 * - "project:<projectId>" = Zugriff auf alle Instanzen dieses Projekts
 * - "instance:<instanceId>" = Zugriff auf eine bestimmte Instanz
 * - "action:<actionName>" = bestimmte Aktion erlaubt (z. B. "action:SEND_MESSAGE")
 * - "readonly" = nur lesende Aktionen
 */
class ScopeResolver {
    private val log = Logger.getLogger("ScopeResolver")

    fun resolve(
        client: GatewayClient,
        action: String,
        resourceVirtualInstanceId: VirtualInstanceId?,
    ): ScopeDecision {
        if (!client.isActive) {
            return ScopeDecision(allowed = false, reason = "Client deaktiviert")
        }

        val scopes = try {
            val arr = JSONArray(client.scopesJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }

        // Wildcard: alles erlaubt
        if ("*" in scopes) {
            return ScopeDecision(allowed = true, reason = "Wildcard-Scope")
        }

        // Readonly-Check
        val isReadAction = action.startsWith("GET_") || action.startsWith("LIST_") || action == "QUERY"
        if ("readonly" in scopes && !isReadAction) {
            return ScopeDecision(allowed = false, reason = "Client hat nur Lese-Rechte, Aktion: $action")
        }
        if ("readonly" in scopes && isReadAction) {
            return ScopeDecision(allowed = true, reason = "Readonly-Scope, lesende Aktion")
        }

        // Instanz-spezifischer Scope
        if (resourceVirtualInstanceId != null && "instance:$resourceVirtualInstanceId" in scopes) {
            return ScopeDecision(allowed = true, reason = "Instanz-Scope Match")
        }

        // Aktions-spezifischer Scope
        if ("action:$action" in scopes) {
            return ScopeDecision(allowed = true, reason = "Aktions-Scope Match")
        }

        // Kein passender Scope gefunden
        log.warning("Scope-Pruefung fehlgeschlagen: Client=${client.clientName}, Action=$action, Instance=$resourceVirtualInstanceId")
        return ScopeDecision(allowed = false, reason = "Kein passender Scope")
    }
}

data class ScopeDecision(
    val allowed: Boolean,
    val reason: String,
)
