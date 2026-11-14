package de.sonictriplex.starrailteams.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory


/** Stammdaten und Bilder aus den Assets (Quelle: shared/ im Monorepo). */
class StammdatenRepository(private val context: Context) {

    val stammdaten: Stammdaten by lazy {
        val text = context.assets.open("stammdaten.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        AppJson.decodeFromString<Stammdaten>(text)
    }

    private val charactersByName by lazy { stammdaten.characters.associateBy { it.name } }
    private val lightConesByName by lazy { stammdaten.lightCones.associateBy { it.name } }
    private val relicSetsByName by lazy { stammdaten.relicSets.associateBy { it.name } }

    fun character(name: String): Character? = charactersByName[name]
    fun lightCone(name: String): LightCone? = lightConesByName[name]
    fun relicSet(name: String): RelicSet? = relicSetsByName[name]

    /** Lädt das Icon eines Charakters, Lichtkegels oder Relikt-Sets, oder null, wenn es keins gibt. */
    fun iconFor(name: String): Bitmap? {
        val explicit = character(name)?.iconPath ?: lightCone(name)?.iconPath ?: relicSet(name)?.iconPath
        return explicit?.let(::loadBitmap)
            ?: loadBitmap(iconPathFor("char", name))
    }

    /** Lädt ein beliebiges Bild aus den Assets (z. B. `assets/images/mm_logo.png`), oder null. */
    fun loadBitmap(path: String): Bitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    } catch (_: java.io.IOException) {
        null
    }
}

/** Leitet den Icon-Pfad aus einem Namen ab (wie Desktop und Web), z. B. char_kafka.png. */
fun iconPathFor(prefix: String, name: String): String {
    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return "assets/images/${prefix}_$slug.png"
}
