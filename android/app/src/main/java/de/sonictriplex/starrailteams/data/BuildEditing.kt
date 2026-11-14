package de.sonictriplex.starrailteams.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/** Auswahllisten der Build-Bearbeitung (identisch zu web/index.html und desktop/models.py). */
object BuildOptions {
    const val NONE = "— Keiner —"
    val RARITIES = listOf(5, 4, 3, 2)
    val SUBSTATS = listOf(
        "LP (Fix)", "ANG (Fix)", "VTD (Fix)", "LP%", "ANG%", "VTD%", "Initiative", "KT%", "KSCH%",
        "Effekttreffer%", "Effektresistenz%", "Zerbrechungseffekt%",
    )
    val MAIN_STATS: Map<String, List<String>> = mapOf(
        "Kopf" to listOf("LP (Fix)"),
        "Hände" to listOf("ANG (Fix)"),
        "Körper" to listOf("LP%", "ANG%", "VTD%", "KT%", "KSCH%", "Heilungsbonus%", "Effekttreffer%"),
        "Füße" to listOf("LP%", "ANG%", "VTD%", "Initiative"),
        "Planarsphäre" to listOf(
            "LP%", "ANG%", "VTD%", "Physisch-SCH%", "Feuer-SCH%", "Eis-SCH%", "Blitz-SCH%", "Wind-SCH%",
            "Quantum-SCH%", "Imaginär-SCH%",
        ),
        "Verbindungsseil" to listOf("LP%", "ANG%", "VTD%", "Zerbrechungseffekt%", "Energieregeneration%"),
    )

    const val LEVEL_MAX = 80
    const val RELIC_LEVEL_MAX = 15
    /** Höchste Talentstufen (mit Eidolons): wie Web und Server. */
    const val TALENT_MAX_BASIC = 7
    const val TALENT_MAX_OTHER = 12
}

/** Platzhalter wie "— Kein Relikt-Set —" gelten als "nichts gewählt". */
fun isPlaceholder(value: String) = value.isBlank() || value.startsWith("—")

/**
 * Bringt einen Build in die Form für die Bearbeitung: alle sechs Relikt-Slots vorhanden, je 4 Substats,
 * Hauptwert aus der erlaubten Liste (wie die Web-Version, die immer alle Slots speichert).
 */
fun Build.normalized(): Build = copy(
    lightCone = if (isPlaceholder(lightCone)) "" else lightCone,
    relics = RELIC_SLOTS.associateWith { slotName ->
        val mains = BuildOptions.MAIN_STATS.getValue(slotName)
        val slot = relics[slotName] ?: RelicSlot()
        slot.copy(
            relicSet = if (isPlaceholder(slot.relicSet)) "" else slot.relicSet,
            main = slot.main.takeIf { it in mains } ?: mains.first(),
            substats = (slot.substats + List(4) { Substat() }).take(4),
        )
    },
)

/** Set-Boni (2er, 4er) als Text, oder null, wenn das Set keine Beschreibung hat. */
fun RelicSet.bonusText(): String? {
    val parts = listOf("2er" to bonus2pc, "4er" to bonus4pc)
        .filter { !it.second.isNullOrBlank() }.map { "${it.first}-Bonus: ${it.second}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
}

/** Aktive Set-Boni: (Setname, 2 oder 4). Ornamente zählen nur bis 2; sortiert nach Teilezahl, dann Name. */
fun Build.activeSetBonuses(): List<Pair<String, Int>> =
    relics.values.map { it.relicSet }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        .filterValues { it >= 2 }.map { (name, n) -> name to if (n >= 4) 4 else 2 }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })

fun Build.withSlot(slotName: String, change: (RelicSlot) -> RelicSlot): Build {
    val slot = relics[slotName] ?: return this
    return copy(relics = relics + (slotName to change(slot)))
}

fun RelicSlot.withSubstat(index: Int, change: (Substat) -> Substat): RelicSlot =
    copy(substats = substats.mapIndexed { i, s -> if (i == index) change(s) else s })

/** JSON mit allen Feldern (auch Standardwerten), wie der Server sie erwartet. */
private val SaveJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

/** Body für `POST api/transfer.php` im Modus "merge": nur die übergebenen Builds und Teams werden geschrieben. */
fun importPayload(builds: List<Build> = emptyList(), teams: List<Team> = emptyList()): String = SaveJson.encodeToString(
    ImportRequest.serializer(),
    ImportRequest("merge", ExchangeFile(EXCHANGE_FORMAT, EXCHANGE_VERSION, builds = builds, teams = teams)),
)

/** Anfrage an teams.php, die die Reihenfolge der Teams speichert: {"order": ["Team A", "Team B", ...]}. */
fun teamOrderPayload(names: List<String>): String =
    buildJsonObject { put("order", JsonArray(names.map { JsonPrimitive(it) })) }.toString()

/** Verschiebt das Element an Position [from] nach [to] (z. B. um die Reihenfolge im Team zu ändern). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

const val MAX_TEAM_SIZE = 4
const val MAX_TEAM_NAME = 255

/**
 * Prüft einen Teamnamen und gibt eine Fehlermeldung zurück, oder null wenn er gültig ist.
 * [original] ist der bisherige Name beim Bearbeiten (darf unverändert bleiben).
 */
fun validateTeamName(name: String, existing: List<String>, original: String?): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Bitte einen Teamnamen eingeben."
        trimmed.length > MAX_TEAM_NAME -> "Der Teamname ist zu lang."
        trimmed != original && trimmed in existing -> "Ein Team mit diesem Namen gibt es schon."
        else -> null
    }
}

@kotlinx.serialization.Serializable
data class ImportRequest(val mode: String, val data: ExchangeFile)

@kotlinx.serialization.Serializable
data class ImportResponse(val builds: Int = 0, val teams: Int = 0, val skipped: List<String> = emptyList())
