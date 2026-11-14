package de.sonictriplex.starrailteams.data

/** Mindestlängen wie in web/api/auth.php (der Server prüft sie ebenfalls). */
const val MIN_USERNAME_LENGTH = 3
const val MIN_PASSWORD_LENGTH = 6

/**
 * Prüft die Eingaben für ein neues Konto vorab und gibt eine Fehlermeldung zurück, oder null, wenn alles passt.
 * So sieht man Tippfehler sofort, ohne dass eine Anfrage zum Server nötig ist.
 */
fun validateRegistration(username: String, password: String, repeat: String): String? = when {
    username.trim().length < MIN_USERNAME_LENGTH -> "Der Benutzername muss mindestens $MIN_USERNAME_LENGTH Zeichen lang sein."
    password.length < MIN_PASSWORD_LENGTH -> "Das Passwort muss mindestens $MIN_PASSWORD_LENGTH Zeichen lang sein."
    password != repeat -> "Die Passwörter stimmen nicht überein."
    else -> null
}
