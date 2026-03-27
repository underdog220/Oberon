package com.devloop.core.discovery

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Ergebnis einer Oberon-Discovery.
 */
data class OberonServerInfo(
    /** IP-Adresse des Servers. */
    val address: InetAddress,
    /** HTTP-Port (Standard: 17900). */
    val port: Int,
    /** HTTPS-Port (0 = deaktiviert). */
    val httpsPort: Int = 0,
    /** Server-Hostname. */
    val host: String = "",
    /** Server-Version. */
    val version: String = "",
    /** Token-Hint (erste 4 Zeichen — fuer Identifizierung). */
    val tokenHint: String = "",
    /** Domaenen (z.B. SYSTEM, GUTACHTEN). */
    val domains: List<String> = emptyList(),
    /** Uptime in Millisekunden. */
    val uptimeMs: Long = 0,
) {
    /** HTTP-URL zum Server. */
    val httpUrl: String get() = "http://${address.hostAddress}:$port"
    /** HTTPS-URL zum Server (leer wenn deaktiviert). */
    val httpsUrl: String get() = if (httpsPort > 0) "https://${address.hostAddress}:$httpsPort" else ""
}

/**
 * Oberon-Discovery-Client: Findet Oberon-Server im Netzwerk per UDP-Broadcast.
 *
 * Verwendung:
 * ```kotlin
 * // Einfachste Variante: Ersten Server finden (blockiert max 5s)
 * val server = OberonDiscovery.findFirst()
 * if (server != null) {
 *     println("Oberon gefunden: ${server.httpUrl}")
 * }
 *
 * // Alle Server im Netzwerk finden (wartet 3s)
 * val servers = OberonDiscovery.findAll(timeoutMs = 3000)
 *
 * // Kontinuierlich lauschen
 * OberonDiscovery.listen { server ->
 *     println("Oberon: ${server.httpUrl} (${server.host})")
 * }
 * ```
 */
object OberonDiscovery {

    /** Standard-Beacon-Port (gleich wie OberonBeacon). */
    const val DEFAULT_BEACON_PORT = 17901

    /**
     * Findet den ersten Oberon-Server im Netzwerk.
     * Blockiert maximal [timeoutMs] Millisekunden.
     *
     * @return Server-Info oder null wenn kein Server gefunden.
     */
    fun findFirst(
        beaconPort: Int = DEFAULT_BEACON_PORT,
        timeoutMs: Int = 5000,
    ): OberonServerInfo? {
        return try {
            val socket = DatagramSocket(beaconPort)
            socket.soTimeout = timeoutMs
            socket.reuseAddress = true

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            try {
                socket.receive(packet)
                parseBeacon(packet)
            } catch (_: SocketTimeoutException) {
                null
            } finally {
                socket.close()
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Findet alle Oberon-Server im Netzwerk innerhalb von [timeoutMs].
     * Gibt eine Liste aller gefundenen Server zurueck (dedupliziert per IP).
     */
    fun findAll(
        beaconPort: Int = DEFAULT_BEACON_PORT,
        timeoutMs: Int = 10000,
    ): List<OberonServerInfo> {
        val found = mutableMapOf<String, OberonServerInfo>()
        val deadline = System.currentTimeMillis() + timeoutMs

        try {
            val socket = DatagramSocket(beaconPort)
            socket.reuseAddress = true

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100)
                socket.soTimeout = remaining

                try {
                    socket.receive(packet)
                    val info = parseBeacon(packet)
                    if (info != null) {
                        found[info.address.hostAddress] = info
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }

            socket.close()
        } catch (_: Throwable) {}

        return found.values.toList()
    }

    /**
     * Lauscht kontinuierlich auf Oberon-Beacons.
     * Ruft [onFound] fuer jeden empfangenen Beacon auf.
     * Blockiert den aufrufenden Thread — in einem Hintergrund-Thread verwenden.
     *
     * @param onFound Callback mit Server-Info bei jedem Beacon
     * @return Aufrufbare Stop-Funktion
     */
    fun listen(
        beaconPort: Int = DEFAULT_BEACON_PORT,
        onFound: (OberonServerInfo) -> Unit,
    ): () -> Unit {
        val running = java.util.concurrent.atomic.AtomicBoolean(true)

        val thread = Thread({
            try {
                val socket = DatagramSocket(beaconPort)
                socket.reuseAddress = true
                socket.soTimeout = 2000

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (running.get()) {
                    try {
                        socket.receive(packet)
                        val info = parseBeacon(packet)
                        if (info != null) {
                            onFound(info)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Timeout — nochmal versuchen
                    }
                }

                socket.close()
            } catch (_: Throwable) {}
        }, "oberon-discovery")
        thread.isDaemon = true
        thread.start()

        return { running.set(false) }
    }

    /**
     * Parst ein empfangenes UDP-Paket als Oberon-Beacon.
     */
    private fun parseBeacon(packet: DatagramPacket): OberonServerInfo? {
        return try {
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val json = JSONObject(text)

            if (json.optString("service") != "oberon") return null

            OberonServerInfo(
                address = packet.address,
                port = json.optInt("port", 17900),
                httpsPort = json.optInt("httpsPort", 0),
                host = json.optString("host", ""),
                version = json.optString("version", ""),
                tokenHint = json.optString("tokenHint", ""),
                domains = json.optJSONArray("domains")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                uptimeMs = json.optLong("uptimeMs", 0),
            )
        } catch (_: Throwable) {
            null
        }
    }
}
