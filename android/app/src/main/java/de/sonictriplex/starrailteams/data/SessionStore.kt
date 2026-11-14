package de.sonictriplex.starrailteams.data

import android.content.Context
import androidx.core.content.edit

/**
 * Speichert Server-Adresse, Benutzername und API-Token im app-privaten Speicher.
 * Der Token liegt nur verschlüsselt ([TokenCipher]) vor.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit { putString("server_url", value) }

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit { putString("username", value) }

    var token: String?
        get() {
            prefs.getString("token_enc", null)?.let { return TokenCipher.decrypt(it) }
            // Version 0.1: Klartext-Token einmalig verschlüsseln und das alte Feld entfernen
            val legacy = prefs.getString("token", null) ?: return null
            token = legacy
            return legacy
        }
        set(value) = prefs.edit {
            remove("token")
            val encrypted = value?.let { TokenCipher.encrypt(it) }
            if (encrypted == null) remove("token_enc") else putString("token_enc", encrypted)
        }
}
