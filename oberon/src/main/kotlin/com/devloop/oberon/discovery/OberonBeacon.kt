package com.devloop.oberon.discovery

import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * UDP-Broadcast-Beacon: Macht Oberon im Netzwerk auffindbar.
 *
 * Sendet alle [intervalMs] Millisekunden ein JSON-Paket per UDP-Broadcast
 * auf Port [beaconPort]. Clients koennen mit [OberonDiscovery] lauschen
 * und den Server automatisch finden — auch nach IP-Wechsel oder Serverumzug.
 *
 * Beacon-Paket (JSON):
 * ```json
 * {
 *   "service": "oberon",
 *   "version": "1.0.0",
 *   "port": 17900,
 *   "httpsPort": 17901,
 *   "host": "WORKRYZEN",
 *   "token": "abc...(first 4 chars)",
 *   "domains": ["SYSTEM","GUTACHTEN"],
 *   "uptime": 12345
 * }
 * ```
 */
class OberonBeacon(
    private val serverPort: Int,
    private val httpsPort: Int = 0,
    private val token: String = "",
    private val domains: List<String> = emptyList(),
    private val version: String = "1.0.0",
    private val beaconPort: Int = DEFAULT_BEACON_PORT,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    private val log = LoggerFactory.getLogger("OberonBeacon")
    private val startTimeMs = System.currentTimeMillis()

    @Volatile
    private var running = false
    private var beaconThread: Thread? = null

    /**
     * Startet den Beacon im Hintergrund.
     */
    fun start() {
        if (running) return
        running = true

        beaconThread = thread(isDaemon = true, name = "oberon-beacon") {
            val socket = DatagramSocket()
            socket.broadcast = true

            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            log.info("Beacon gestartet: UDP-Broadcast auf Port $beaconPort (alle ${intervalMs}ms)")

            while (running) {
                try {
                    val payload = buildBeaconPayload()
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, beaconPort)
                    socket.send(packet)
                } catch (e: Throwable) {
                    if (running) {
                        log.warn("Beacon-Fehler: ${e.message}")
                    }
                }

                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    break
                }
            }

            socket.close()
            log.info("Beacon gestoppt")
        }
    }

    /**
     * Stoppt den Beacon.
     */
    fun stop() {
        running = false
        beaconThread?.interrupt()
        beaconThread = null
    }

    private fun buildBeaconPayload(): String {
        val hostname = try { InetAddress.getLocalHost().hostName } catch (_: Throwable) { "unknown" }
        val uptimeMs = System.currentTimeMillis() - startTimeMs

        return JSONObject().apply {
            put("service", "oberon")
            put("version", version)
            put("port", serverPort)
            put("httpsPort", httpsPort)
            put("host", hostname)
            // Nur die ersten 4 Zeichen des Tokens (fuer Identifizierung, nicht fuer Auth)
            put("tokenHint", if (token.length > 4) token.take(4) + "..." else "")
            put("domains", domains)
            put("uptimeMs", uptimeMs)
        }.toString()
    }

    companion object {
        /** Standard-Beacon-Port (UDP). */
        const val DEFAULT_BEACON_PORT = 17901
        /** Standard-Broadcast-Intervall: 30 Sekunden. */
        const val DEFAULT_INTERVAL_MS = 30_000L
    }
}
