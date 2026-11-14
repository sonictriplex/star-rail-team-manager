package de.sonictriplex.starrailteams.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON-Konfiguration für alle Dateien: unbekannte Felder (neuere Versionen) werden ignoriert. */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

// --- Stammdaten (shared/stammdaten.json) ---

@Serializable
data class Stammdaten(
    val characters: List<Character> = emptyList(),
    @SerialName("light_cones") val lightCones: List<LightCone> = emptyList(),
    @SerialName("relic_sets") val relicSets: List<RelicSet> = emptyList(),
)

@Serializable
data class Character(
    val name: String,
    val element: String = "",
    val path: String = "",
    val rarity: Int = 4,
    @SerialName("icon_path") val iconPath: String? = null,
)

@Serializable
data class LightCone(
    val name: String,
    val path: String = "",
    val rarity: Int = 4,
    @SerialName("icon_path") val iconPath: String? = null,
    /** Fähigkeit des Lichtkegels; null, wenn der Server oder die Stammdaten keine liefern. */
    val passive: LightConePassive? = null,
)

/** Beschreibung der Lichtkegel-Fähigkeit: [text] hat je einen Eintrag für Überlagerung 1 bis 5. */
@Serializable
data class LightConePassive(val name: String = "", val text: List<String> = emptyList())

/** Relikt-Set (kind "Relikt", 2er und 4er Bonus) oder Planar-Ornament (kind "Ornament", nur 2er Bonus). */
@Serializable
data class RelicSet(
    val name: String,
    val kind: String = "Relikt",
    @SerialName("bonus_2pc") val bonus2pc: String? = null,
    @SerialName("bonus_4pc") val bonus4pc: String? = null,
    @SerialName("icon_path") val iconPath: String? = null,
    /** Namen der einzelnen Teile je Slot, z. B. "Kopf" -> ["Vielgesichtige Maske des Diebs"]. */
    val pieces: Map<String, List<String>> = emptyMap(),
)

// --- Austauschformat (identisch zu Web und Desktop) ---

const val EXCHANGE_FORMAT = "star-rail-team-manager"
const val EXCHANGE_VERSION = 1

@Serializable
data class ExchangeFile(
    val format: String,
    val version: Int,
    @SerialName("exported_at") val exportedAt: String? = null,
    val builds: List<Build> = emptyList(),
    val teams: List<Team> = emptyList(),
)

@Serializable
data class Build(
    val name: String,
    val level: Int = 80,
    val eidolon: Int = 0,
    @SerialName("light_cone") val lightCone: String = "",
    @SerialName("lc_level") val lcLevel: Int = 80,
    @SerialName("lc_superimpose") val lcSuperimpose: Int = 1,
    val talents: Talents = Talents(),
    /** Die sechs Relikte nach Slotname (siehe [RELIC_SLOTS]). */
    val relics: Map<String, RelicSlot> = emptyMap(),
)

@Serializable
data class Talents(val basic: Int = 1, val skill: Int = 1, val ult: Int = 1, val talent: Int = 1)

@Serializable
data class RelicSlot(
    /** Name des Relikt-Sets bzw. Planar-Ornaments; leer = nicht gewählt. */
    @SerialName("set") val relicSet: String = "",
    val rarity: Int = 5,
    val level: Int = 15,
    val main: String = "",
    @SerialName("main_value") val mainValue: String = "",
    val substats: List<Substat> = emptyList(),
)

@Serializable
data class Substat(
    val stat: String = "",
    // Im JSON heißt das Feld "val", was in Kotlin ein Schlüsselwort ist
    @SerialName("val") val value: String = "",
)

@Serializable
data class Team(val name: String, val members: List<String> = emptyList())

/** Reihenfolge der Relikt-Slots in der Oberfläche: Kopf bis Füße nehmen Relikt-Sets, die letzten beiden Planar-Ornamente. */
val RELIC_SLOTS = listOf("Kopf", "Hände", "Körper", "Füße", "Planarsphäre", "Verbindungsseil")

/** Art des Sets, das in den Slot passt ("Relikt" oder "Ornament"). */
fun slotKind(slotName: String): String = if (slotName == "Planarsphäre" || slotName == "Verbindungsseil") "Ornament" else "Relikt"

/** Liest eine Export-Datei und prüft Format und Version wie die anderen Clients. */
fun parseExchange(text: String): ExchangeFile {
    val file = AppJson.decodeFromString<ExchangeFile>(text)
    require(file.format == EXCHANGE_FORMAT) { "Die Datei ist keine Export-Datei des Star-Rail-Team-Managers." }
    require(file.version <= EXCHANGE_VERSION) { "Die Datei stammt aus einer neueren Version und kann nicht gelesen werden." }
    return file
}

// --- Konto und Stammdaten-Aktualisierung (Antworten der Web-API) ---

@Serializable
data class StatusResponse(val username: String = "", @SerialName("is_admin") val isAdmin: Boolean = false)

/** Ein Eintrag der Liste „Stammdaten aktualisieren“ (Felder je nach Gruppe). */
@Serializable
data class UpdateEntry(
    val name: String,
    val element: String = "",
    val path: String = "",
    val rarity: Int = 0,
    val kind: String = "",
) {
    /** Kurzbeschreibung für die Liste, z. B. „5★ Blitz, Die Nichtigkeit“. */
    fun summary(): String = listOf(
        if (rarity > 0) "$rarity★" else "",
        listOf(element, path).filter { it.isNotBlank() }.joinToString(", "),
        kind,
    ).filter { it.isNotBlank() }.joinToString(" ")
}

@Serializable
data class UpdateGroups(
    val characters: List<UpdateEntry> = emptyList(),
    @SerialName("light_cones") val lightCones: List<UpdateEntry> = emptyList(),
    @SerialName("relic_sets") val relicSets: List<UpdateEntry> = emptyList(),
) {
    val total: Int get() = characters.size + lightCones.size + relicSets.size
}

/** Ergebnis von `stammdaten.php?action=check`: was in der Datenbank fehlt, und was sich nicht eintragen lässt. */
@Serializable
data class StammdatenCheck(@SerialName("new") val found: UpdateGroups = UpdateGroups(), val skipped: List<String> = emptyList())

/** Auswahl, die an den Server geht (nur Namen; der Server rechnet die Einträge selbst aus der Quelle). */
@Serializable
data class UpdateRequest(
    val characters: List<String>,
    @SerialName("light_cones") val lightCones: List<String>,
    @SerialName("relic_sets") val relicSets: List<String>,
)

@Serializable
data class UpdateImages(val geladen: Int = 0, val vorhanden: Int = 0, val fehler: Int = 0)

@Serializable
data class UpdateResult(val added: Int = 0, val images: UpdateImages = UpdateImages())

/** Prüft ein neues Passwort wie der Server: 6 bis 72 Zeichen. Rückgabe: Fehlermeldung oder null. */
fun validateNewPassword(old: String, new: String, repeat: String): String? = when {
    old.isEmpty() -> "Bitte das aktuelle Passwort eingeben."
    new.length < 6 -> "Das neue Passwort muss mindestens 6 Zeichen lang sein."
    new.toByteArray().size > 72 -> "Das neue Passwort darf höchstens 72 Zeichen lang sein."
    new == old -> "Das neue Passwort muss sich vom aktuellen unterscheiden."
    new != repeat -> "Die beiden neuen Passwörter stimmen nicht überein."
    else -> null
}
