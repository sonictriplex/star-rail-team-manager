package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sonictriplex.starrailteams.data.StammdatenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icon eines Charakters, Lichtkegels oder Relikt-Sets aus den Assets. Charaktere bekommen einen Hintergrund in ihrer
 * Elementfarbe, Charaktere und Lichtkegel einen Rahmen in der Farbe ihrer Seltenheit. Ohne Bild erscheint
 * der erste Buchstabe.
 */
@Composable
fun NameIcon(name: String, repo: StammdatenRepository, size: Dp, modifier: Modifier = Modifier, level: Int? = null, flatBackground: Boolean = false) {
    val image by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { repo.iconFor(name)?.asImageBitmap() }
    }
    val character = repo.character(name)
    val rarity = character?.rarity ?: repo.lightCone(name)?.rarity
    val background: Brush = character?.let { if (flatBackground) SolidColor(elementColor(it.element).copy(alpha = 0.35f)) else elementGradient(elementColor(it.element), 0.55f) }
        ?: SolidColor(MaterialTheme.colorScheme.surfaceVariant)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier.size(size).clip(shape).background(background).let {
            if (rarity != null) it.border(1.5.dp, rarityColor(rarity).copy(alpha = 0.9f), shape) else it
        },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = image
        if (bmp != null) {
            Image(bmp, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(name.take(1), fontSize = (size.value / 2).sp)
        }
        // Charakterlevel klein oben links im Bild
        if (level != null) {
            Text(
                level.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 4f)),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

/** Verlauf von der Elementfarbe (oben) zum dunklen Panel (unten). */
fun elementGradient(color: Color, topAlpha: Float): Brush =
    Brush.verticalGradient(listOf(color.copy(alpha = topAlpha), Palette.BgInput))

/** Halbtransparente Karte im Stil der Desktop- und Web-Version. */
@Composable
fun AppCard(modifier: Modifier = Modifier, alpha: Float = 0.85f, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Palette.BgPanel.copy(alpha = alpha)),
        border = BorderStroke(1.dp, Palette.Border),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

/** Hinweis, dass die Anzeige aus dem Cache stammt: Zeitpunkt des letzten Abgleichs und gesperrte Änderungen. */
@Composable
fun OfflineBanner(savedAt: Long?, modifier: Modifier = Modifier) {
    val stand = savedAt?.let {
        java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM)
            .withLocale(java.util.Locale.GERMAN)
            .format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
    }
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().background(Palette.Gold.copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Offline" + (stand?.let { " – Stand vom $it" } ?: "") +
                ". Bearbeiten ist erst wieder mit Verbindung möglich.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Gold,
        )
    }
}
