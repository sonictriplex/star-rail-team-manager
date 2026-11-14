package de.sonictriplex.starrailteams.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


/**
 * Stammdaten und Bilder aus den Assets (Quelle: shared/ im Monorepo). Neuere Einträge, die der Server schon kennt
 * (z. B. nach einer Aktualisierung durch den Administrator), kommen per [syncFromServer] dazu: Sie liegen in
 * `files/stammdaten-extra.json`, ihre Bilder in `files/extra-images/`. So braucht es dafür keinen neuen Build.
 */
class StammdatenRepository(private val context: Context) {

    private val base: Stammdaten by lazy {
        val text = context.assets.open("stammdaten.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        AppJson.decodeFromString<Stammdaten>(text)
    }
    private val extraFile = File(context.filesDir, "stammdaten-extra.json")
    private val extraImages = File(context.filesDir, "extra-images")

    /** Alle Stammdaten (Assets plus nachgeladene Einträge); ein Compose-State, die Oberfläche folgt Änderungen. */
    var stammdaten: Stammdaten by mutableStateOf(base.withExtras(readExtras()))
        private set

    private var charactersByName = stammdaten.characters.associateBy { it.name }
    private var lightConesByName = stammdaten.lightCones.associateBy { it.name }
    private var relicSetsByName = stammdaten.relicSets.associateBy { it.name }

    private fun readExtras(): Stammdaten = try {
        if (extraFile.exists()) AppJson.decodeFromString(extraFile.readText(Charsets.UTF_8)) else Stammdaten()
    } catch (_: Exception) {
        Stammdaten()   // beschädigte Datei: ignorieren, der nächste Abgleich schreibt sie neu
    }

    private fun publish(extras: Stammdaten) {
        val merged = base.withExtras(extras)
        charactersByName = merged.characters.associateBy { it.name }
        lightConesByName = merged.lightCones.associateBy { it.name }
        relicSetsByName = merged.relicSets.associateBy { it.name }
        stammdaten = merged
    }

    /**
     * Gleicht mit dem Server ab: Einträge, die weder in den Assets noch schon nachgeladen sind, werden samt Bild
     * gespeichert. Gibt die Zahl neuer Einträge zurück. Fehler (kein Netz) sind unkritisch, der alte Stand bleibt.
     */
    suspend fun syncFromServer(api: ApiClient): Int {
        val server = api.fetchMasterData()
        val known = base
        val extras = Stammdaten(
            characters = server.characters.filter { c -> known.characters.none { it.name == c.name } },
            lightCones = server.lightCones.filter { c -> known.lightCones.none { it.name == c.name } },
            relicSets = server.relicSets.filter { c -> known.relicSets.none { it.name == c.name } },
        )
        withContext(Dispatchers.IO) {
            extraImages.mkdirs()
            val paths = extras.characters.mapNotNull { it.iconPath } + extras.lightCones.mapNotNull { it.iconPath } +
                extras.relicSets.mapNotNull { it.iconPath }
            paths.forEach { path ->
                val target = File(extraImages, path.removePrefix("assets/images/")).also { it.parentFile?.mkdirs() }
                if (!target.exists()) api.downloadImage(path)?.let { target.writeBytes(it) }
            }
            if (extras != readExtras()) extraFile.writeText(AppJson.encodeToString(Stammdaten.serializer(), extras), Charsets.UTF_8)
        }
        val before = stammdaten
        publish(extras)
        return stammdaten.characters.size + stammdaten.lightCones.size + stammdaten.relicSets.size -
            (before.characters.size + before.lightCones.size + before.relicSets.size)
    }

    fun character(name: String): Character? = charactersByName[name]
    fun lightCone(name: String): LightCone? = lightConesByName[name]
    fun relicSet(name: String): RelicSet? = relicSetsByName[name]

    /** Lädt das Icon eines Charakters, Lichtkegels oder Relikt-Sets, oder null, wenn es keins gibt. */
    fun iconFor(name: String): Bitmap? {
        val explicit = character(name)?.iconPath ?: lightCone(name)?.iconPath ?: relicSet(name)?.iconPath
        return explicit?.let { loadBitmap(it) ?: loadExtraBitmap(it) } ?: loadBitmap(iconPathFor("char", name))
    }

    private fun loadExtraBitmap(path: String): Bitmap? =
        File(extraImages, path.removePrefix("assets/images/")).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Lädt ein beliebiges Bild aus den Assets (z. B. `assets/images/ui/mm_logo.png`), oder null. */
    fun loadBitmap(path: String): Bitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    } catch (_: java.io.IOException) {
        null
    }
}

/** Leitet den Icon-Pfad aus einem Namen ab (wie Desktop und Web), z. B. characters/kafka.png. */
fun iconPathFor(prefix: String, name: String): String {
    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val dir = mapOf("char" to "characters", "lc" to "light_cones", "relic" to "relic_sets").getValue(prefix)
    return "assets/images/$dir/$slug.png"
}

/** Ergänzt die Stammdaten um nachgeladene Einträge (Namen aus den Assets haben Vorrang). */
private fun Stammdaten.withExtras(extras: Stammdaten): Stammdaten = Stammdaten(
    characters = characters + extras.characters.filter { e -> characters.none { it.name == e.name } },
    lightCones = lightCones + extras.lightCones.filter { e -> lightCones.none { it.name == e.name } },
    relicSets = relicSets + extras.relicSets.filter { e -> relicSets.none { it.name == e.name } },
)
