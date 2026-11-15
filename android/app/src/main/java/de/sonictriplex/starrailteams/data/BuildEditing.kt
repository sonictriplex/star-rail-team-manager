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
fun Build.normalized(): Build {
    val cleaned = copy(
        lightCone = if (isPlaceholder(lightCone)) "" else lightCone,
        relicSet = if (isPlaceholder(relicSet)) "" else relicSet,
        relicSet2 = if (isPlaceholder(relicSet2)) "" else relicSet2,
        ornamentSet = if (isPlaceholder(ornamentSet)) "" else ornamentSet,
        relics = RELIC_SLOTS.associateWith { slotName ->
            val mains = BuildOptions.MAIN_STATS.getValue(slotName)
            val slot = relics[slotName] ?: RelicSlot()
            slot.copy(
                relicSet = if (isPlaceholder(slot.relicSet)) "" else slot.relicSet,
                piece = if (isPlaceholder(slot.piece)) "" else slot.piece,
                main = slot.main.takeIf { it in mains } ?: mains.first(),
                substats = (slot.substats + List(4) { Substat() }).take(4),
            )
        },
    )
    // Builds aus der Zeit vor den Set-Feldern (ältere Daten, Zwischenspeicher): die Sets aus den Slots ableiten, wie der Server
    return if (cleaned.relicSet.isEmpty() && cleaned.relicSet2.isEmpty() && cleaned.ornamentSet.isEmpty()) cleaned.withDerivedSets() else cleaned
}

/** Leitet die Sets des Builds aus den Sets der Slots ab: häufigstes Set in Kopf bis Füße, zweites Set nur bei 2+2, Ornament-Set. */
fun Build.withDerivedSets(): Build {
    fun counts(slots: List<String>) = slots.mapNotNull { relics[it]?.relicSet?.takeIf { s -> s.isNotBlank() } }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }   // stabil: bei Gleichstand gilt die Slot-Reihenfolge
    val relic = counts(RELIC_SLOTS.take(4))
    val second = if (relic.size > 1 && relic[0].value == 2 && relic[1].value >= 2) relic[1].key else ""
    return copy(
        relicSet = relic.firstOrNull()?.key.orEmpty(),
        relicSet2 = second,
        ornamentSet = counts(RELIC_SLOTS.drop(4)).firstOrNull()?.key.orEmpty(),
    )
}

/** Beschreibung der Fähigkeit bei der gegebenen Überlagerung (1–5), oder null, wenn der Lichtkegel keine hat. */
fun LightCone.passiveText(superimpose: Int): String? {
    val texts = passive?.text?.takeIf { it.isNotEmpty() } ?: return null
    val step = superimpose.coerceIn(1, texts.size)
    return "${passive.name} (Überlagerung $step)\n\n${texts[step - 1]}"
}

/** Set-Boni (2er, 4er) als Text, oder null, wenn das Set keine Beschreibung hat. */
fun RelicSet.bonusText(): String? {
    val parts = listOf("2er" to bonus2pc, "4er" to bonus4pc)
        .filter { !it.second.isNullOrBlank() }.map { "${it.first}-Bonus: ${it.second}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
}

/** Die einzelnen Teile eines Sets als (Slot, Name), in der Reihenfolge der Stammdaten. */
fun RelicSet.pieceList(): List<Pair<String, String>> = pieces.flatMap { (slot, names) -> names.map { slot to it } }

/** Name des Teils, das dieses Set im Slot [slotName] liefert (z. B. Kopf -> „Weinende Maske einer Mimin“); null ohne Angaben. */
fun RelicSet.pieceFor(slotName: String): String? = pieces[slotName]?.firstOrNull()

/** Set-Boni plus die Namen der einzelnen Teile, für die Info-Anzeige. */
fun RelicSet.infoText(): String? {
    val pieceLines = pieceList().joinToString("\n") { "${it.first}: ${it.second}" }
    return listOfNotNull(bonusText(), pieceLines.takeIf { it.isNotEmpty() }?.let { "Teile:\n$it" })
        .joinToString("\n\n").ifEmpty { null }
}

/** Aktive Set-Boni aus den Sets des Builds: (Setname, 2 oder 4). Ein Relikt-Set gilt als 4er, mit zweitem Relikt-Set als 2+2; Ornamente zählen 2. */
fun Build.activeSetBonuses(): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Int>()
    if (relicSet.isNotBlank()) counts[relicSet] = if (relicSet2.isNotBlank() && relicSet2 != relicSet) 2 else 4
    if (relicSet2.isNotBlank() && relicSet2 != relicSet) counts[relicSet2] = 2
    if (ornamentSet.isNotBlank()) counts[ornamentSet] = 2
    return counts.map { it.key to it.value }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
}

/** Das Teil, das im Slot angezeigt wird: das gespeicherte Teil, sonst das Teil des Sets (ältere Daten kennen nur das Set). */
fun RelicSlot.displayPiece(slotName: String, set: RelicSet?): String =
    piece.ifBlank { if (relicSet.isBlank()) "" else set?.pieceFor(slotName) ?: relicSet }

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
