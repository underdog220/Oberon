package com.devloop.oberon.tls

import io.ktor.network.tls.certificates.*
import java.io.File
import java.security.KeyStore
import java.util.logging.Logger

/**
 * Automatisches TLS-Setup fuer Oberon.
 *
 * Generiert ein Self-Signed-Zertifikat wenn keins vorhanden ist.
 * Das Zertifikat wird im Datenverzeichnis gespeichert und bei
 * Neustarts wiederverwendet.
 *
 * Funktioniert ohne DNS — rein ueber IP-Adresse.
 */
object OberonTlsSetup {

    private val log = Logger.getLogger("OberonTlsSetup")

    private const val KEYSTORE_FILE = "oberon-keystore.jks"
    private const val KEYSTORE_PASSWORD = "oberon-tls"
    private const val KEY_ALIAS = "oberon"

    /**
     * Stellt sicher dass ein KeyStore existiert. Erstellt ein Self-Signed-Zertifikat
     * falls keins vorhanden ist.
     *
     * @param dataDir Datenverzeichnis (z.B. ~/.oberon oder /data)
     * @return KeyStore-Konfiguration oder null wenn TLS deaktiviert ist
     */
    fun ensureKeyStore(dataDir: File): TlsConfig? {
        val keystoreFile = File(dataDir, KEYSTORE_FILE)

        if (keystoreFile.exists()) {
            log.info("TLS KeyStore gefunden: ${keystoreFile.absolutePath}")
            return TlsConfig(
                keystoreFile = keystoreFile,
                keystorePassword = KEYSTORE_PASSWORD,
                keyAlias = KEY_ALIAS,
                keyPassword = KEYSTORE_PASSWORD,
            )
        }

        // Neues Self-Signed-Zertifikat generieren
        log.info("Generiere Self-Signed TLS-Zertifikat...")
        try {
            dataDir.mkdirs()
            val keyStore = buildKeyStore {
                certificate(KEY_ALIAS) {
                    password = KEYSTORE_PASSWORD
                    daysValid = 3650
                    keySizeInBits = 2048
                }
            }
            keyStore.saveToFile(keystoreFile, KEYSTORE_PASSWORD)
            log.info("TLS-Zertifikat erstellt: ${keystoreFile.absolutePath} (Self-Signed, 10 Jahre gueltig)")

            return TlsConfig(
                keystoreFile = keystoreFile,
                keystorePassword = KEYSTORE_PASSWORD,
                keyAlias = KEY_ALIAS,
                keyPassword = KEYSTORE_PASSWORD,
            )
        } catch (e: Throwable) {
            log.warning("TLS-Zertifikat konnte nicht erstellt werden: ${e.message}")
            log.warning("HTTPS deaktiviert — nur HTTP verfuegbar.")
            return null
        }
    }
}

data class TlsConfig(
    val keystoreFile: File,
    val keystorePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)
