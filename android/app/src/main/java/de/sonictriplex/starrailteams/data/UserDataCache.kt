package de.sonictriplex.starrailteams.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Zuletzt vom Server geladene Teams und Builds samt Zeitpunkt (Millisekunden seit 1970). */
data class CachedUserData(val data: ExchangeFile, val savedAt: Long)

@Serializable
private data class CacheEnvelope(
    val version: Int = 1,
    val server: String,
    val username: String,
    val savedAt: Long,
    val data: ExchangeFile,
)

/**
 * Zwischenspeicher für die Teams und Builds des angemeldeten Nutzers, damit die App sie auch ohne
 * Verbindung anzeigen kann. Eine Datei im app-privaten Speicher im Austauschformat; sie gilt nur für die
 * Kombination aus Server und Benutzername, mit der sie gespeichert wurde. Der Cache ist optional: Fehler
 * beim Lesen oder Schreiben führen nie zu einem Absturz, sondern nur zu "kein Cache".
 */
class UserDataCache(private val dir: File) {
    private val file get() = File(dir, "userdata-cache.json")

    private fun normalized(server: String) = server.trim().trimEnd('/')

    /** Gibt den Cache zurück, wenn er zu diesem Server und Nutzer gehört und lesbar ist. */
    fun load(server: String, username: String): CachedUserData? = try {
        if (!file.exists()) {
            null
        } else {
            val envelope = AppJson.decodeFromString(CacheEnvelope.serializer(), file.readText(Charsets.UTF_8))
            if (envelope.version == 1 && envelope.server == normalized(server) && envelope.username == username) {
                CachedUserData(envelope.data, envelope.savedAt)
            } else {
                null
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Schreibt den Cache; die alte Datei bleibt bis zum fertigen Schreiben unverändert. */
    fun save(server: String, username: String, data: ExchangeFile, now: Long = System.currentTimeMillis()) {
        try {
            dir.mkdirs()
            val envelope = CacheEnvelope(1, normalized(server), username, now, data)
            val temp = File(dir, "userdata-cache.json.tmp")
            temp.writeText(AppJson.encodeToString(CacheEnvelope.serializer(), envelope), Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            // Cache ist nur eine Zugabe
        }
    }

    fun clear() {
        file.delete()
        File(dir, "userdata-cache.json.tmp").delete()
    }
}
