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
)

/** Relikt-Set (kind "Relikt", 2er und 4er Bonus) oder Planar-Ornament (kind "Ornament", nur 2er Bonus). */
@Serializable
data class RelicSet(
    val name: String,
    val kind: String = "Relikt",
    @SerialName("bonus_2pc") val bonus2pc: String? = null,
    @SerialName("bonus_4pc") val bonus4pc: String? = null,
    @SerialName("icon_path") val iconPath: String? = null,
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
