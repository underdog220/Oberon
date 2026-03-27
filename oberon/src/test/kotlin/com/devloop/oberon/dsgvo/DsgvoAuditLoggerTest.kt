package com.devloop.oberon.dsgvo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDate

class DsgvoAuditLoggerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loggt Event und liest es zurueck`() {
        val logger = DsgvoAuditLogger(tempDir.toString())

        val event = DsgvoAuditEvent(
            clientId = "test-client",
            domain = "GUTACHTEN",
            piiFound = true,
            piiTypes = listOf(PiiCategory.PERSON, PiiCategory.FLURNUMMER),
            anonymized = true,
            routingDecision = RoutingDecision.PROXY,
            processingDurationMs = 150,
            resultStatus = "ok",
        )

        logger.log(event)

        val events = logger.getEventsForDate(LocalDate.now())
        assertEquals(1, events.size)
        assertEquals("test-client", events[0].clientId)
        assertTrue(events[0].piiFound)
    }

    @Test
    fun `generiert Tagesbericht`() {
        val logger = DsgvoAuditLogger(tempDir.toString())

        // Mehrere Events loggen
        repeat(5) { i ->
            logger.log(DsgvoAuditEvent(
                clientId = "client-$i",
                domain = "GUTACHTEN",
                piiFound = i % 2 == 0,
                anonymized = i % 2 == 0,
                routingDecision = if (i % 2 == 0) RoutingDecision.PROXY else RoutingDecision.DIRECT,
                processingDurationMs = 100L + i * 10,
                resultStatus = "ok",
            ))
        }

        val bericht = logger.generateDailyReport(LocalDate.now())
        assertEquals(5, bericht.gesamtAnfragen)
        assertTrue(bericht.keineExterneUebertragungMitPii)
    }
}
