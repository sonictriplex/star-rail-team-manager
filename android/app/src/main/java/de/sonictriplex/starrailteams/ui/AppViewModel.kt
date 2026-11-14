package de.sonictriplex.starrailteams.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sonictriplex.starrailteams.BuildConfig
import de.sonictriplex.starrailteams.data.ApiClient
import de.sonictriplex.starrailteams.data.ApiException
import de.sonictriplex.starrailteams.data.Build
import de.sonictriplex.starrailteams.data.moved
import de.sonictriplex.starrailteams.data.ExchangeFile
import de.sonictriplex.starrailteams.data.SessionStore
import de.sonictriplex.starrailteams.data.NetworkException
import de.sonictriplex.starrailteams.data.StammdatenRepository
import de.sonictriplex.starrailteams.data.Team
import de.sonictriplex.starrailteams.data.UnauthorizedException
import de.sonictriplex.starrailteams.data.UserDataCache
import de.sonictriplex.starrailteams.data.StammdatenCheck
import de.sonictriplex.starrailteams.data.UpdateRequest
import de.sonictriplex.starrailteams.data.validateNewPassword
import de.sonictriplex.starrailteams.data.validateRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zustand der App: Anmeldung, geladene Nutzerdaten, Lade- und Fehlerstatus.
 *
 * Offline-Cache: Beim Start erscheinen sofort die zuletzt geladenen Teams und Builds, danach wird im
 * Hintergrund aktualisiert. Ist der Server nicht erreichbar, bleibt der Cache sichtbar ([offline]) und
 * Änderungen sind gesperrt, bis wieder eine Verbindung besteht.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val session = SessionStore(app)
    val stammdaten = StammdatenRepository(app)
    private val cache = UserDataCache(app.filesDir)

    var loggedIn by mutableStateOf(session.token != null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var userData by mutableStateOf<ExchangeFile?>(null)
        private set

    /** Fehlermeldung des Dialogs „Konto löschen“ (z. B. falsches Passwort); getrennt von [error]. */
    var accountError by mutableStateOf<String?>(null)
        private set

    /** true, wenn das Konto auf dem Server Administrator ist (zeigt „Stammdaten aktualisieren“ im Menü). */
    var isAdmin by mutableStateOf(false)
        private set

    /** Fehlermeldung des Dialogs „Passwort ändern“; getrennt von [error]. */
    var passwordError by mutableStateOf<String?>(null)
        private set

    // Dialog „Stammdaten aktualisieren“ (nur Administratoren)
    var updateCheck by mutableStateOf<StammdatenCheck?>(null)
        private set
    var updateBusy by mutableStateOf(false)
        private set
    var updateError by mutableStateOf<String?>(null)
        private set
    var updateResult by mutableStateOf<String?>(null)
        private set

    /** true, wenn die angezeigten Daten aus dem Cache stammen, weil der Server nicht erreichbar war. */
    var offline by mutableStateOf(false)
        private set

    /** Zeitpunkt (ms), zu dem [userData] zuletzt mit dem Server übereinstimmte. */
    var dataSavedAt by mutableStateOf<Long?>(null)
        private set

    init {
        if (loggedIn) {
            cache.load(serverUrl, session.username)?.let {
                userData = it.data
                dataSavedAt = it.savedAt
            }
            refresh()
        }
    }

    /**
     * Der Server: die zuletzt bei der Anmeldung eingegebene Adresse, sonst die voreingestellte
     * ([BuildConfig.DEFAULT_SERVER_URL]).
     */
    val serverUrl: String
        get() = session.serverUrl.ifEmpty { BuildConfig.DEFAULT_SERVER_URL }

    private fun api(baseUrl: String = serverUrl) = ApiClient(baseUrl, { session.token })

    fun login(serverUrl: String, username: String, password: String) {
        val url = serverUrl.trim().trimEnd('/')
        if (url.isEmpty() || username.isBlank() || password.isEmpty()) {
            error = "Bitte Server, Benutzername und Passwort eingeben."
            return
        }
        authenticate(url) { it.login(username.trim(), password) }
    }

    /** Legt ein neues Konto an und meldet danach an (Ablauf wie [login]). */
    fun register(serverUrl: String, username: String, password: String, repeat: String) {
        val url = serverUrl.trim().trimEnd('/')
        if (url.isEmpty()) {
            error = "Bitte einen Server angeben."
            return
        }
        validateRegistration(username, password, repeat)?.let { error = it; return }
        authenticate(url) { it.register(username.trim(), password) }
    }

    /** Gemeinsamer Ablauf von Anmeldung und Registrierung: Token holen, Sitzung speichern, Daten laden. */
    private fun authenticate(url: String, action: suspend (ApiClient) -> Pair<String, String>) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val (token, name) = action(api(url))
                session.serverUrl = url
                session.username = name
                session.token = token
                loggedIn = true
                refresh()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val fresh = api().fetchUserData()
                userData = fresh
                offline = false
                storeCache(fresh)
                syncInBackground()
            } catch (e: UnauthorizedException) {
                clearSession()
                error = "Sitzung abgelaufen. Bitte erneut anmelden."
            } catch (e: NetworkException) {
                // Mit Cache: den letzten Stand weiter zeigen. Ohne Cache bleibt nur die Fehlermeldung.
                if (userData != null) offline = true else error = e.message
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    /** Fragt im Hintergrund den Admin-Status ab und holt neuere Stammdaten vom Server; Fehler sind hier unkritisch. */
    private fun syncInBackground() {
        viewModelScope.launch {
            runCatching { isAdmin = api().isAdmin() }
            runCatching { stammdaten.syncFromServer(api()) }
        }
    }

    /** Ruft die Daten erneut ab, wenn der Server zuletzt nicht erreichbar war (z. B. beim Zurückkehren in die App). */
    fun retryIfOffline() {
        if (loggedIn && offline && !busy) refresh()
    }

    private suspend fun storeCache(data: ExchangeFile) {
        val now = System.currentTimeMillis()
        dataSavedAt = now
        val server = serverUrl
        val user = session.username
        withContext(Dispatchers.IO) { cache.save(server, user, data, now) }
    }

    fun logout() {
        viewModelScope.launch {
            busy = true
            error = null
            try { api().logout() } finally { clearSession(); busy = false }
        }
    }

    /** Löscht das Konto auf dem Server und meldet danach lokal ab. Fehler erscheinen im Dialog ([accountError]). */
    fun deleteAccount(password: String) {
        viewModelScope.launch {
            saving = true
            accountError = null
            try {
                api().deleteAccount(password)
                session.username = ""   // der gelöschte Name soll nicht mehr im Anmeldefeld stehen
                clearSession()
            } catch (e: UnauthorizedException) {
                clearSession()
                error = "Sitzung abgelaufen. Bitte erneut anmelden."
            } catch (e: ApiException) {
                accountError = e.message
            } finally {
                saving = false
            }
        }
    }

    fun dismissAccountError() { accountError = null }

    /** Ändert das Passwort. Prüft vorab wie der Server (6–72 Zeichen); Fehler erscheinen im Dialog ([passwordError]). */
    fun changePassword(old: String, new: String, repeat: String, onDone: () -> Unit) {
        validateNewPassword(old, new, repeat)?.let { passwordError = it; return }
        viewModelScope.launch {
            saving = true
            passwordError = null
            try {
                api().changePassword(old, new)
                onDone()
            } catch (e: UnauthorizedException) {
                clearSession()
                error = "Sitzung abgelaufen. Bitte erneut anmelden."
            } catch (e: ApiException) {
                passwordError = e.message
            } finally {
                saving = false
            }
        }
    }

    fun dismissPasswordError() { passwordError = null }

    /** Administrator: fragt den Server, welche Charaktere, Lichtkegel und Relikt-Sets in der Datenbank fehlen. */
    fun checkStammdaten() {
        viewModelScope.launch {
            updateBusy = true
            updateError = null
            updateResult = null
            updateCheck = null
            try {
                updateCheck = api().checkStammdaten()
            } catch (e: UnauthorizedException) {
                clearSession()
                error = "Sitzung abgelaufen. Bitte erneut anmelden."
            } catch (e: ApiException) {
                updateError = e.message
            } finally {
                updateBusy = false
            }
        }
    }

    /** Administrator: trägt die Auswahl auf dem Server ein und holt die neuen Einträge danach in die App. */
    fun applyStammdaten(selection: UpdateRequest) {
        viewModelScope.launch {
            updateBusy = true
            updateError = null
            try {
                val result = api().applyStammdaten(selection)
                val images = result.images
                updateResult = "${result.added} Einträge ergänzt. Bilder: ${images.geladen} geladen, " +
                    "${images.vorhanden} schon vorhanden" + if (images.fehler > 0) ", ${images.fehler} FEHLGESCHLAGEN." else "."
                updateCheck = null
                runCatching { stammdaten.syncFromServer(api()) }
            } catch (e: UnauthorizedException) {
                clearSession()
                error = "Sitzung abgelaufen. Bitte erneut anmelden."
            } catch (e: ApiException) {
                updateError = e.message
            } finally {
                updateBusy = false
            }
        }
    }

    fun dismissUpdate() {
        updateCheck = null
        updateError = null
        updateResult = null
    }

    /** Führt eine Schreibaktion aus: Ladezustand, Fehlermeldung und abgelaufene Sitzung einheitlich behandelt. */
    private fun write(action: suspend (ApiClient) -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            saving = true
            error = null
            try {
                action(api())
                onSuccess()
                userData?.let { storeCache(it) }   // der Cache folgt den eigenen Änderungen
            } catch (e: UnauthorizedException) {
                clearSession()
                error = "Sitzung abgelaufen. Bitte erneut anmelden."
            } catch (e: ApiException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }

    /** Speichert einen Build auf dem Server und übernimmt ihn danach in die lokale Ansicht. */
    fun saveBuild(build: Build, onSaved: () -> Unit) = write({ it.saveBuild(build) }) {
        userData = userData?.let { d -> d.copy(builds = d.builds.filterNot { it.name == build.name } + build) }
        onSaved()
    }

    /**
     * Speichert ein Team. [originalName] ist der bisherige Name beim Bearbeiten (null bei einem neuen Team);
     * wurde das Team umbenannt, wird der alte Eintrag danach gelöscht.
     */
    fun saveTeam(team: Team, originalName: String?, onSaved: () -> Unit) = write({ api ->
        api.saveTeam(team)
        if (originalName != null && originalName != team.name) api.deleteTeam(originalName)
    }) {
        userData = userData?.let { d ->
            val inPlace = originalName != null && originalName == team.name
            d.copy(
                teams = if (inPlace) {
                    d.teams.map { if (it.name == team.name) team else it }
                } else {
                    // Neue und umbenannte Teams stehen auf dem Server am Ende der Reihenfolge
                    d.teams.filterNot { it.name == team.name || it.name == originalName } + team
                },
            )
        }
        onSaved()
    }

    /** Verschiebt ein Team in der Liste um [delta] Plätze (-1 = nach oben) und speichert die Reihenfolge auf dem Server. */
    fun moveTeam(name: String, delta: Int) {
        val teams = userData?.teams ?: return
        val from = teams.indexOfFirst { it.name == name }
        val to = from + delta
        if (from < 0 || to !in teams.indices) return
        val moved = teams.moved(from, to)
        write({ it.saveTeamOrder(moved.map { t -> t.name }) }) {
            userData = userData?.copy(teams = moved)
        }
    }

    fun deleteTeam(name: String) = write({ it.deleteTeam(name) }) {
        userData = userData?.let { d -> d.copy(teams = d.teams.filterNot { it.name == name }) }
    }

    fun dismissError() { error = null }

    private fun clearSession() {
        session.token = null
        loggedIn = false
        userData = null
        offline = false
        dataSavedAt = null
        isAdmin = false
        dismissUpdate()
        cache.clear()   // Daten eines abgemeldeten Nutzers nicht auf dem Gerät lassen
    }
}
