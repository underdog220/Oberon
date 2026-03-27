package com.devloop.oberon

import com.devloop.oberon.routing.*
import com.devloop.oberon.service.OberonPlatformServices
import com.devloop.oberon.util.errorJson
// AuthResult not used in test-host mode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.json.JSONObject
import java.nio.file.Files
import kotlin.test.*

class OberonApiTest {

    private fun createTestConfig(): OberonConfig {
        val tempDir = Files.createTempDirectory("oberon-test")
        return OberonConfig(
            host = "0.0.0.0",
            port = 0,
            dbPath = tempDir.resolve("test.db"),
            mssqlJdbcUrl = null,
            token = "test-token",
            domains = listOf("SYSTEM", "GUTACHTEN"),
            syncIntervalMs = 999_999, // kein Sync im Test
            dataDir = tempDir,
            openAiApiKey = "",
            openAiBaseUrl = "https://api.openai.com",
            openAiModel = "gpt-4o-mini",
            httpsPort = 0,
            tlsEnabled = false,
            dsgvoEnabled = true,
            dsgvoLocalLlmUrl = "http://localhost:11434",
            dsgvoAlwaysAnonymize = false,
            dsgvoAuditRetentionDays = 90,
            dsgvoSessionTtlMinutes = 60,
        )
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val config = createTestConfig()
        val services = OberonPlatformServices.create(config)

        application {
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respondText(errorJson(cause.message ?: "error").toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                }
            }

            routing {
                get("/api/health") { call.respondText("ok") }
                // Im Test ohne Auth — Produktiv-Auth wird separat getestet
                platformRoutes(services)
                instanceRoutes(services)
                chatRoutes(services)
                memoryRoutes(services)
                clientRoutes(services)
                auditRoutes(services)
            }
        }

        block()
    }

    @Test
    fun `health endpoint returns ok without auth`() = testApp {
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `platform status returns ok`() = testApp {
        val response = client.get("/api/v2/platform/status") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val json = JSONObject(response.bodyAsText())
        assertEquals("ok", json.getString("status"))
        assertEquals("Oberon", json.getString("server"))
        assertEquals(2, json.getInt("apiVersion"))
    }

    @Test
    fun `domains endpoint returns configured domains`() = testApp {
        val response = client.get("/api/v2/domains") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val json = JSONObject(response.bodyAsText())
        val domains = json.getJSONArray("domains")
        assertEquals(2, domains.length())
    }

    @Test
    fun `create and retrieve virtual instance`() = testApp {
        // Create
        val createResponse = client.post("/api/v2/instances") {
            // auth header removed for test-host
            contentType(ContentType.Application.Json)
            setBody("""{"projectId":"proj-1","label":"Test Instance","type":"TOPIC_FOCUS","domain":"SYSTEM"}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = JSONObject(createResponse.bodyAsText())
        val instanceId = created.getString("id")
        assertTrue(instanceId.isNotBlank())

        // List
        val listResponse = client.get("/api/v2/instances") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listJson = JSONObject(listResponse.bodyAsText())
        assertTrue(listJson.getInt("count") >= 1)

        // Get by ID
        val getResponse = client.get("/api/v2/instances/$instanceId") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val instance = JSONObject(getResponse.bodyAsText())
        assertEquals("Test Instance", instance.getString("label"))
    }

    @Test
    fun `send and retrieve messages`() = testApp {
        // Create instance first
        val createResp = client.post("/api/v2/instances") {
            // auth header removed for test-host
            contentType(ContentType.Application.Json)
            setBody("""{"projectId":"proj-1","label":"Chat Test","type":"TOPIC_FOCUS"}""")
        }
        val instanceId = JSONObject(createResp.bodyAsText()).getString("id")

        // Send message
        val sendResp = client.post("/api/v2/instances/$instanceId/messages") {
            // auth header removed for test-host
            contentType(ContentType.Application.Json)
            setBody("""{"role":"USER","content":"Hallo Oberon!"}""")
        }
        assertEquals(HttpStatusCode.Created, sendResp.status)

        // Retrieve messages
        val msgsResp = client.get("/api/v2/instances/$instanceId/messages") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, msgsResp.status)
        val msgs = JSONObject(msgsResp.bodyAsText())
        assertEquals(1, msgs.getInt("count"))
        assertEquals("Hallo Oberon!", msgs.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test
    fun `memory CRUD operations`() = testApp {
        // Create
        val createResp = client.post("/api/v2/memory") {
            // auth header removed for test-host
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Testregel","content":"Niemals X tun","memoryKind":"STABLE_KNOWLEDGE","domain":"SYSTEM"}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val memId = JSONObject(createResp.bodyAsText()).getString("id")

        // Query
        val queryResp = client.get("/api/v2/memory") {
            // auth header removed for test-host
        }
        val entries = JSONObject(queryResp.bodyAsText())
        assertTrue(entries.getInt("count") >= 1)

        // Search
        val searchResp = client.get("/api/v2/memory?q=Testregel") {
            // auth header removed for test-host
        }
        val searchResults = JSONObject(searchResp.bodyAsText())
        assertTrue(searchResults.getInt("count") >= 1)

        // Delete
        val delResp = client.delete("/api/v2/memory/$memId") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, delResp.status)
    }

    @Test
    fun `register client returns API key`() = testApp {
        val resp = client.post("/api/v2/clients") {
            // auth header removed for test-host
            contentType(ContentType.Application.Json)
            setBody("""{"clientName":"TestClient","clientKind":"OCTOPUS","allowedDomains":"[\"SYSTEM\"]"}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val json = JSONObject(resp.bodyAsText())
        assertTrue(json.getString("apiKey").isNotBlank())
        assertEquals("TestClient", json.getString("clientName"))
    }

    @Test
    fun `context endpoint returns boost context`() = testApp {
        // Create instance
        val createResp = client.post("/api/v2/instances") {
            // auth header removed for test-host
            contentType(ContentType.Application.Json)
            setBody("""{"projectId":"proj-1","label":"Context Test","type":"TOPIC_FOCUS"}""")
        }
        val instanceId = JSONObject(createResp.bodyAsText()).getString("id")

        // Get context (should work even with no data)
        val ctxResp = client.get("/api/v2/instances/$instanceId/context") {
            // auth header removed for test-host
        }
        assertEquals(HttpStatusCode.OK, ctxResp.status)
        val ctx = JSONObject(ctxResp.bodyAsText())
        assertEquals(instanceId, ctx.getString("virtualInstanceId"))
    }
}
