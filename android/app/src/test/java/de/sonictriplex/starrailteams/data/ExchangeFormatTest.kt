package de.sonictriplex.starrailteams.data

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/** Prüft den Client gegen die Stammdaten aus shared/ und das Austauschformat (gleiches Format wie Web und Desktop). */
class ExchangeFormatTest {
    private val shared = File(System.getProperty("shared.dir"))

    private val webExport = """
        {"format":"star-rail-team-manager","version":1,"exported_at":"2026-11-14T12:00:00","builds":[
          {"name":"Kafka","level":80,"eidolon":2,"light_cone":"Along the Passing Shore","lc_level":70,"lc_superimpose":3,
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
        assertEquals("Along the Passing Shore", build.lightCone)
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
    fun setBonusenWerdenBerechnet() {
        fun slot(set: String) = RelicSlot(relicSet = set)
        val build = Build(
            name = "X",
            relics = mapOf(
                "Kopf" to slot("A"), "Hände" to slot("A"), "Körper" to slot("A"), "Füße" to slot("A"),
                "Planarsphäre" to slot("O"), "Verbindungsseil" to slot("O"),
            ),
        )
        assertEquals(listOf("A" to 4, "O" to 2), build.activeSetBonuses())
        assertEquals(listOf("A" to 2, "O" to 2), build.copy(relics = build.relics + ("Füße" to slot("B"))).activeSetBonuses())
        assertEquals(emptyList<Pair<String, Int>>(), Build(name = "X").activeSetBonuses())
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
}
