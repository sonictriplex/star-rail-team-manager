package de.sonictriplex.starrailteams.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fehler der Web-API mit verständlicher Meldung für die Oberfläche. */
open class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Der Server war nicht erreichbar (kein Netz, Zeitüberschreitung, falsche Adresse). */
class NetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)

/** Der Token ist ungültig oder abgelaufen: neu anmelden. */
class UnauthorizedException(message: String) : ApiException(message)

@Serializable
private data class TokenRequest(val username: String, val password: String, val device: String)

@Serializable
private data class PasswordRequest(val password: String)

@Serializable
private data class ChangePasswordRequest(
    @kotlinx.serialization.SerialName("old_password") val oldPassword: String,
    @kotlinx.serialization.SerialName("new_password") val newPassword: String,
)

@Serializable
private data class RegisterRequest(val username: String, val password: String)

@Serializable
private data class TokenResponse(val token: String, val username: String = "")

@Serializable
private data class ErrorResponse(val error: String = "")

/**
 * Zugriff auf die PHP-API der Web-Version (web/api/). Die App spricht nie direkt mit MySQL.
 * [baseUrl] ist die Adresse des Servers ohne /api, z. B. https://example.org
 */
class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val http: OkHttpClient = defaultClient,
) {
    /** Baut die URL eines Endpunkts; eine ungültige Server-Adresse wird zur verständlichen Meldung. */
    private fun endpoint(file: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val base = (baseUrl.trimEnd('/') + "/api/" + file).toHttpUrlOrNull()
            ?: throw ApiException("Ungültige Server-Adresse.")
        return base.newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    }

    /**
     * Anfrage mit Token. Der Token geht in zwei Headern mit: "Authorization: Bearer" (Standard) und
     * "X-Auth-Token". Viele Hoster reichen den Authorization-Header nicht an PHP durch (z. B. nginx mit
     * PHP-FPM); eigene Header kommen immer an. Der Server nimmt, was er bekommt.
     */
    private fun authorized(url: HttpUrl): Request.Builder {
        val token = tokenProvider() ?: throw UnauthorizedException("Nicht eingeloggt")
        return Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("X-Auth-Token", token)
    }

    /** Meldet sich an und gibt Token und Benutzernamen zurück. */
    suspend fun login(username: String, password: String): Pair<String, String> {
        val body = AppJson.encodeToString(TokenRequest.serializer(), TokenRequest(username, password, deviceName()))
        val request = Request.Builder().url(endpoint("auth.php", mapOf("action" to "token")))
            .post(body.toRequestBody(JSON_TYPE)).build()
        val parsed = decode(TokenResponse.serializer(), execute(request))
        return parsed.token to parsed.username.ifBlank { username }
    }

    /**
     * Legt ein neues Konto an und meldet sich danach an. Die API liefert bei der Registrierung nur eine Session
     * (Cookie), keinen Token; deshalb folgt der normale Token-Login mit denselben Daten.
     * Serverfehler (Name vergeben, zu kurz) kommen als [ApiException] mit der Meldung des Servers an.
     */
    suspend fun register(username: String, password: String): Pair<String, String> {
        val body = AppJson.encodeToString(RegisterRequest.serializer(), RegisterRequest(username, password))
        val request = Request.Builder().url(endpoint("auth.php", mapOf("action" to "register")))
            .post(body.toRequestBody(JSON_TYPE)).build()
        execute(request)
        return try {
            login(username, password)
        } catch (e: ApiException) {
            throw ApiException("Das Konto wurde angelegt, die Anmeldung ist aber fehlgeschlagen. Bitte melde dich an.", e)
        }
    }

    /** Widerruft den Token auf dem Server. Fehler sind unkritisch, die lokale Abmeldung zählt. */
    suspend fun logout() {
        try {
            val request = authorized(endpoint("auth.php", mapOf("action" to "logout")))
                .post("".toRequestBody(JSON_TYPE)).build()
            execute(request)
        } catch (_: ApiException) { }
    }

    /**
     * Löscht das Konto samt allen Teams und Builds endgültig. Der Server verlangt das Passwort erneut und
     * antwortet bei einem falschen mit 403 (nicht 401), das erscheint hier als [ApiException] mit der Meldung.
     */
    suspend fun deleteAccount(password: String) {
        val body = AppJson.encodeToString(PasswordRequest.serializer(), PasswordRequest(password))
        val request = authorized(endpoint("auth.php", mapOf("action" to "delete_account")))
            .post(body.toRequestBody(JSON_TYPE)).build()
        execute(request)
    }

    /** Ändert das Passwort. Ein falsches aktuelles Passwort meldet der Server mit 403 (Meldung erscheint als [ApiException]). */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val body = AppJson.encodeToString(ChangePasswordRequest.serializer(), ChangePasswordRequest(oldPassword, newPassword))
        val request = authorized(endpoint("auth.php", mapOf("action" to "change_password")))
            .post(body.toRequestBody(JSON_TYPE)).build()
        execute(request)
    }

    /** true, wenn das Konto auf dem Server als Administrator eingetragen ist (siehe web/api/admins.local.php). */
    suspend fun isAdmin(): Boolean {
        val request = authorized(endpoint("auth.php", mapOf("action" to "status"))).get().build()
        return decode(StatusResponse.serializer(), execute(request)).isAdmin
    }

    /** Nur Administratoren: prüft die Datenquelle und liefert, was in der Datenbank fehlt (ändert nichts). */
    suspend fun checkStammdaten(): StammdatenCheck {
        val request = authorized(endpoint("stammdaten.php", mapOf("action" to "check"))).get().build()
        return decode(StammdatenCheck.serializer(), execute(request))
    }

    /** Nur Administratoren: trägt die ausgewählten Einträge samt Bildern auf dem Server ein. */
    suspend fun applyStammdaten(selection: UpdateRequest): UpdateResult {
        val body = AppJson.encodeToString(UpdateRequest.serializer(), selection)
        val request = authorized(endpoint("stammdaten.php"))
            .header("X-Requested-With", "fetch")   // der Server nimmt Änderungen nur so an
            .post(body.toRequestBody(JSON_TYPE)).build()
        return decode(UpdateResult.serializer(), execute(request))
    }

    /** Die Stammdaten des Servers (öffentlich): Charaktere, Lichtkegel und Relikt-Sets, auch neuere als in der APK. */
    suspend fun fetchMasterData(): Stammdaten {
        suspend fun <T> list(file: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
            decode(kotlinx.serialization.builtins.ListSerializer(serializer), execute(Request.Builder().url(endpoint(file)).get().build()))
        return Stammdaten(
            characters = list("characters.php", Character.serializer()),
            lightCones = list("light_cones.php", LightCone.serializer()),
            relicSets = list("relic_sets.php", RelicSet.serializer()),
        )
    }

    /** Lädt ein Bild des Servers (z. B. `assets/images/characters/x.png`), oder null. */
    suspend fun downloadImage(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = (baseUrl.trimEnd('/') + "/" + path.trimStart('/')).toHttpUrlOrNull() ?: return@withContext null
            http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                if (r.isSuccessful) r.body.bytes() else null
            }
        } catch (_: IOException) {
            null
        }
    }

    /** Builds und Teams des angemeldeten Nutzers im gemeinsamen Austauschformat. */
    suspend fun fetchUserData(): ExchangeFile {
        val request = authorized(endpoint("transfer.php")).get().build()
        return try {
            parseExchange(execute(request))
        } catch (e: IllegalArgumentException) {
            throw ApiException(e.message ?: "Ungültige Antwort des Servers", e)
        } catch (e: SerializationException) {
            throw ApiException("Ungültige Antwort des Servers", e)
        }
    }

    /**
     * Speichert einen Build des Nutzers (überschreibt den vorhandenen Build des Charakters).
     * Der Server begrenzt die Werte auf die erlaubten Bereiche.
     */
    suspend fun saveBuild(build: Build) {
        val result = import(importPayload(builds = listOf(build)))
        if (result.builds < 1) throw ApiException("Der Server hat den Build nicht übernommen (Charakter unbekannt).")
    }

    /** Speichert ein Team (legt es an oder überschreibt das gleichnamige). Builds bleiben unberührt. */
    suspend fun saveTeam(team: Team) {
        val result = import(importPayload(teams = listOf(team)))
        if (result.teams < 1) throw ApiException("Der Server hat das Team nicht übernommen.")
    }

    /** Löscht das Team mit diesem Namen. */
    suspend fun deleteTeam(name: String) {
        val request = authorized(endpoint("teams.php", mapOf("name" to name))).delete().build()
        execute(request)
    }

    /** Speichert die Reihenfolge der Teams (Namen in der gewünschten Reihenfolge). */
    suspend fun saveTeamOrder(names: List<String>) {
        val request = authorized(endpoint("teams.php")).post(teamOrderPayload(names).toRequestBody(JSON_TYPE)).build()
        execute(request)
    }

    private suspend fun import(payload: String): ImportResponse {
        val request = authorized(endpoint("transfer.php")).post(payload.toRequestBody(JSON_TYPE)).build()
        return decode(ImportResponse.serializer(), execute(request))
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, text: String): T = try {
        AppJson.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw ApiException("Ungültige Antwort des Servers", e)
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) return@use text
                val message = runCatching { AppJson.decodeFromString(ErrorResponse.serializer(), text).error }
                    .getOrNull().orEmpty().ifBlank { "Serverfehler (${response.code})" }
                if (response.code == 401 && request.header("Authorization") != null) throw UnauthorizedException(message)
                throw ApiException(message)
            }
        } catch (e: IOException) {
            throw NetworkException("Server nicht erreichbar. Adresse und Netzwerk prüfen.", e)
        }
    }

    private fun deviceName() = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().take(100)

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)   // ohne Netz früh aufgeben und den Cache zeigen
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
