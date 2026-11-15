package de.sonictriplex.starrailteams.data

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/** Prüft den Client gegen die Stammdaten aus shared/ und das Austauschformat (gleiches Format wie Web und Desktop). */
class ExchangeFormatTest {
    private val shared = File(System.getProperty("shared.dir"))

    private val webExport = """
        {"format":"star-rail-team-manager","version":1,"exported_at":"2026-11-14T12:00:00","builds":[
          {"name":"Kafka","level":80,"eidolon":2,"light_cone":"Entlang am vorbeiziehenden Ufer","lc_level":70,"lc_superimpose":3,
           "relics":{"Kopf":{"set":"Band of Sizzling Thunder","rarity":5,"level":15,"main":"LP (Fix)","main_value":"705.6",
              "substats":[{"stat":"KT%","val":"5,2"},{"stat":"","val":""},{"stat":"","val":""},{"stat":"","val":""}]}},
           "talents":{"basic":6,"skill":10,"ult":10,"talent":10}}],
         "teams":[{"name":"Team","members":["Kafka","Acheron"]}]}
    """.trimIndent()

    @Test
    fun stammdatenWerdenGelesen() {
        val data = AppJson.decodeFromString<Stammdaten>(File(shared, "stammdaten.json").readText())
        assertTrue(data.characters.size > 50)
        assertTrue(data.lightCones.size > 100)
        assertTrue(data.relicSets.any { it.kind == "Relikt" && !it.bonus4pc.isNullOrBlank() })
        assertTrue(data.relicSets.any { it.kind == "Ornament" && it.bonus4pc.isNullOrBlank() })
        val kafka = data.characters.first { it.name == "Kafka" }
        assertEquals("Blitz", kafka.element)
        assertEquals("Die Nichtigkeit", kafka.path)
        // Jeder Eintrag hat ein Bild, das im Repo liegt
        (data.characters.mapNotNull { it.iconPath } + data.lightCones.mapNotNull { it.iconPath }).forEach {
            assertTrue("Bild fehlt: $it", File(shared, it).exists())
        }
    }

    @Test
    fun exportDerWebVersionWirdGelesen() {
        val file = parseExchange(webExport)
        val build = file.builds.single()
        assertEquals(2, build.eidolon)
        assertEquals("Entlang am vorbeiziehenden Ufer", build.lightCone)
        assertEquals(3, build.lcSuperimpose)
        assertEquals(6, build.talents.basic)
        val kopf = build.relics.getValue("Kopf")
        assertEquals("Band of Sizzling Thunder", kopf.relicSet)
        assertEquals("LP (Fix)", kopf.main)
        assertEquals("5,2", kopf.substats[0].value)
        assertEquals(listOf("Kafka", "Acheron"), file.teams.single().members)
    }

    @Test
    fun fremdeOderNeuereDateienWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException::class.java) {
            parseExchange("""{"format":"genshin-team-manager","version":1,"builds":[],"teams":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseExchange("""{"format":"star-rail-team-manager","version":99,"builds":[],"teams":[]}""")
        }
    }

    @Test
    fun normalisierenErgaenztAlleSlotsUndSubstats() {
        val build = Build(name = "Kafka").normalized()
        assertEquals(RELIC_SLOTS, build.relics.keys.toList())
        build.relics.values.forEach { assertEquals(4, it.substats.size) }
        assertEquals("LP (Fix)", build.relics.getValue("Kopf").main)
        assertEquals("Initiative".takeIf { it in BuildOptions.MAIN_STATS.getValue("Füße") }, "Initiative")
        // Ein unerlaubter Hauptwert fällt auf den ersten Eintrag zurück
        val fixed = Build(name = "X", relics = mapOf("Kopf" to RelicSlot(main = "ANG%"))).normalized()
        assertEquals("LP (Fix)", fixed.relics.getValue("Kopf").main)
        assertEquals("", Build(name = "X", lightCone = "— Kein —").normalized().lightCone)
    }

    @Test
    fun setBonusenKommenVonDenSetsDesBuilds() {
        val build = Build(name = "X", relicSet = "A", ornamentSet = "O")
        assertEquals(listOf("A" to 4, "O" to 2), build.activeSetBonuses())
        assertEquals(listOf("A" to 2, "B" to 2, "O" to 2), build.copy(relicSet2 = "B").activeSetBonuses())   // 2+2
        assertEquals(emptyList<Pair<String, Int>>(), Build(name = "X").activeSetBonuses())
    }

    @Test
    fun setsWerdenAusDenSlotsAbgeleitet() {
        fun slot(set: String) = RelicSlot(relicSet = set)
        val slots = mapOf(
            "Kopf" to slot("A"), "Hände" to slot("A"), "Körper" to slot("B"), "Füße" to slot("B"),
            "Planarsphäre" to slot("O"), "Verbindungsseil" to slot("O"),
        )
        val old = Build(name = "X", relics = slots).normalized()      // Altbestand ohne Set-Felder
        assertEquals(Triple("A", "B", "O"), Triple(old.relicSet, old.relicSet2, old.ornamentSet))
        val four = Build(name = "X", relics = slots + ("Körper" to slot("A")) + ("Füße" to slot("A"))).normalized()
        assertEquals(Triple("A", "", "O"), Triple(four.relicSet, four.relicSet2, four.ornamentSet))
        // gewählte Sets gehen vor
        val chosen = Build(name = "X", relicSet = "C", relics = slots).normalized()
        assertEquals(Triple("C", "", ""), Triple(chosen.relicSet, chosen.relicSet2, chosen.ornamentSet))
        // Teil: gespeichert, sonst das Teil des Sets
        val set = RelicSet(name = "A", pieces = mapOf("Kopf" to listOf("Maske")))
        assertEquals("Maske", RelicSlot(relicSet = "A").displayPiece("Kopf", set))
        assertEquals("Eigenes", RelicSlot(relicSet = "A", piece = "Eigenes").displayPiece("Kopf", set))
        assertEquals("", RelicSlot().displayPiece("Kopf", null))
    }

    @Test
    fun setsUndTeileGehenDurchDenAustausch() {
        val build = Build(name = "X", relicSet = "A", relicSet2 = "B", ornamentSet = "O",
            relics = mapOf("Kopf" to RelicSlot(relicSet = "A", piece = "Maske"))).normalized()
        val back = parseExchange(importPayload(listOf(build)).let { AppJson.parseToJsonElement(it).jsonObject["data"]!!.toString() }).builds.single()
        assertEquals(Triple("A", "B", "O"), Triple(back.relicSet, back.relicSet2, back.ornamentSet))
        assertEquals("Maske", back.relics.getValue("Kopf").piece)
    }

    @Test
    fun slotArten() {
        assertEquals(listOf("Relikt", "Relikt", "Relikt", "Relikt", "Ornament", "Ornament"), RELIC_SLOTS.map(::slotKind))
    }

    @Test
    fun importPayloadHatDasFormatDerApi() {
        val body = AppJson.parseToJsonElement(importPayload(builds = listOf(Build(name = "Kafka").normalized()))).jsonObject
        assertEquals("merge", body.getValue("mode").jsonPrimitive.content)
        val data = body.getValue("data").jsonObject
        assertEquals(EXCHANGE_FORMAT, data.getValue("format").jsonPrimitive.content)
        val relic = data.getValue("builds").let { (it as kotlinx.serialization.json.JsonArray)[0].jsonObject }
            .getValue("relics").jsonObject.getValue("Kopf").jsonObject
        assertTrue(relic.containsKey("main_value"))   // Feldnamen wie im Server (helpers.php normalize_relics)
        assertTrue(relic.containsKey("set"))
    }

    @Test
    fun teamNamenWerdenGeprueft() {
        assertEquals("Bitte einen Teamnamen eingeben.", validateTeamName("  ", emptyList(), null))
        assertEquals("Ein Team mit diesem Namen gibt es schon.", validateTeamName("A", listOf("A"), null))
        assertEquals(null, validateTeamName("A", listOf("A"), "A"))
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(1, 0))
    }

    @Test
    fun neuesPasswortWirdWieAmServerGeprueft() {
        assertEquals("Bitte das aktuelle Passwort eingeben.", validateNewPassword("", "neuespw", "neuespw"))
        assertEquals("Das neue Passwort muss mindestens 6 Zeichen lang sein.", validateNewPassword("alt", "abc", "abc"))
        assertEquals("Das neue Passwort darf höchstens 72 Zeichen lang sein.", validateNewPassword("alt", "x".repeat(73), "x".repeat(73)))
        assertEquals("Das neue Passwort muss sich vom aktuellen unterscheiden.", validateNewPassword("gleich1", "gleich1", "gleich1"))
        assertEquals("Die beiden neuen Passwörter stimmen nicht überein.", validateNewPassword("alt", "neuespw", "anderes"))
        assertEquals(null, validateNewPassword("alt", "neuespw", "neuespw"))
    }

    @Test
    fun antwortDerStammdatenPruefungWirdGelesen() {
        val json = """{"new":{"characters":[{"name":"Kafka","element":"Blitz","path":"Die Nichtigkeit","rarity":5}],
            "light_cones":[{"name":"X","path":"Die Jagd","rarity":4}],"relic_sets":[{"name":"Y","kind":"Ornament"}]},
            "skipped":["Charakter Z: unbekannter Pfad"]}"""
        val check = AppJson.decodeFromString<StammdatenCheck>(json)
        assertEquals(3, check.found.total)
        assertEquals("5★ Blitz, Die Nichtigkeit", check.found.characters.single().summary())
        assertEquals("4★ Die Jagd", check.found.lightCones.single().summary())
        assertEquals("Ornament", check.found.relicSets.single().summary())
        assertEquals(1, check.skipped.size)
        val leer = AppJson.decodeFromString<StammdatenCheck>("""{"new":{"characters":[],"light_cones":[],"relic_sets":[]},"skipped":[]}""")
        assertEquals(0, leer.found.total)
        // Antwort von apply
        val result = AppJson.decodeFromString<UpdateResult>("""{"added":4,"images":{"geladen":3,"vorhanden":1,"fehler":0}}""")
        assertEquals(4, result.added); assertEquals(3, result.images.geladen)
        // Auswahl geht mit den Feldnamen des Servers hinaus
        val body = AppJson.encodeToString(UpdateRequest.serializer(), UpdateRequest(listOf("Kafka"), emptyList(), listOf("Y")))
        assertTrue(body.contains("\"light_cones\":[]") && body.contains("\"relic_sets\":[\"Y\"]"))
    }

    @Test
    fun lichtkegelHabenFaehigkeitenAufDeutsch() {
        val data = AppJson.decodeFromString<Stammdaten>(File(shared, "stammdaten.json").readText())
        assertTrue(data.lightCones.all { it.passive?.text?.size == 5 })
        val shore = data.lightCones.first { it.name == "Entlang am vorbeiziehenden Ufer" }
        assertEquals("Fährmann", shore.passive?.name)
        assertTrue(shore.passiveText(1)!!.contains("36%"))
        assertTrue(shore.passiveText(5)!!.contains("60%"))
        assertTrue(shore.passiveText(9)!!.contains("Überlagerung 5"))   // außerhalb 1..5 wird begrenzt
        assertEquals(null, LightCone(name = "X").passiveText(1))
        assertTrue(data.relicSets.all { !it.bonus2pc.isNullOrBlank() })
    }

    @Test
    fun relikteHabenTeilenamenAufDeutsch() {
        val data = AppJson.decodeFromString<Stammdaten>(File(shared, "stammdaten.json").readText())
        assertTrue(data.relicSets.all { it.pieceList().size == if (it.kind == "Relikt") 4 else 2 })
        val thief = data.relicSets.first { it.name == "Dieb des fallenden Meteors" }
        assertEquals("Kopf" to "Vielgesichtige Maske des Diebs", thief.pieceList().first())
        assertTrue(thief.infoText()!!.contains("Teile:\nKopf: Vielgesichtige Maske des Diebs"))
        assertEquals(null, RelicSet(name = "X").infoText())
    }

    @Test
    fun gesuchtesTeilErscheintAlsEigeneZeile() {
        val data = AppJson.decodeFromString<Stammdaten>(File(shared, "stammdaten.json").readText())
        val items = data.relicSets.filter { it.kind == "Relikt" }
            .map { de.sonictriplex.starrailteams.ui.PickerItem(it.name, keywords = it.pieceList()) }
        val rows = de.sonictriplex.starrailteams.ui.pickerRows(items, "vielgesichtige maske")
        assertEquals(1, rows.size)
        assertEquals("Vielgesichtige Maske des Diebs", rows[0].title)
        assertEquals("Kopf · Dieb des fallenden Meteors", rows[0].subtitle)
        assertEquals("Dieb des fallenden Meteors", rows[0].pick)             // Antippen übernimmt das Set
        assertEquals(items.size, de.sonictriplex.starrailteams.ui.pickerRows(items, "").size)
    }

    @Test
    fun setPickerShowsPieceOfTheSlot() {
        val data = AppJson.decodeFromString<Stammdaten>(File(shared, "stammdaten.json").readText())
        for (slot in RELIC_SLOTS) {
            val items = data.relicSets.filter { it.kind == slotKind(slot) }
                .map { de.sonictriplex.starrailteams.ui.PickerItem(it.name, title = it.pieceFor(slot) ?: it.name, keywords = it.pieceList()) }
            val rows = de.sonictriplex.starrailteams.ui.pickerRows(items, "")
            assertEquals(items.size, rows.size)
            data.relicSets.filter { it.kind == slotKind(slot) }.forEach { set ->
                val piece = set.pieceFor(slot)
                assertNotNull("$slot fehlt bei ${set.name}", piece)
                val row = rows.single { it.pick == set.name }
                assertEquals(piece, row.title)          // Titel ist das Teil des Slots
                assertEquals(set.name, row.subtitle)    // Untertitel das Set
            }
        }
    }
}
