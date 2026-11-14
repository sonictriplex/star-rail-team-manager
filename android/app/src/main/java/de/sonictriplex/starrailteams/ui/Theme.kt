package de.sonictriplex.starrailteams.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Farbpalette wie in der Desktop-Version (desktop/styles.py) und der Web-Version (web/index.html). */
object Palette {
    val BgWindow = Color(0xFF0A0B17)
    val BgPanel = Color(0xFF18192A)
    val BgInput = Color(0xFF12121F)
    val BgHover = Color(0xFF242640)
    val Border = Color(0xFF34365A)
    val Accent = Color(0xFFA99BFF)
    val Gold = Color(0xFFE6C37A)
    val Epic = Color(0xFFA561C9)
    val Danger = Color(0xFFFF7A5C)
    val Text = Color(0xFFE1E1E6)
    val TextSoft = Color(0xFFB9B7CC)
}

private val AppColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.BgInput,
    primaryContainer = Palette.Accent,          // u. a. der Knopf "Neues Team"
    onPrimaryContainer = Palette.BgInput,
    secondary = Palette.Accent,
    tertiary = Palette.Gold,
    background = Color.Transparent,             // Scaffolds zeigen das Hintergrundbild (siehe AppBackground)
    onBackground = Palette.Text,
    surface = Palette.BgPanel,
    onSurface = Palette.Text,
    surfaceVariant = Palette.BgHover,
    onSurfaceVariant = Palette.TextSoft,
    outline = Color(0xFF6A6483),
    outlineVariant = Palette.Border,
    error = Palette.Danger,
)

/** Wie Desktop und Web immer dunkel. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = Typography(), content = content)
}

/** Elementfarben (wie Web und Desktop). */
fun elementColor(element: String): Color = when (element) {
    "Physisch" -> Color(0xFFC9C9C9)
    "Feuer" -> Color(0xFFF4663B)
    "Eis" -> Color(0xFF47B8E0)
    "Blitz" -> Color(0xFFD86BFF)
    "Wind" -> Color(0xFF4ED3A4)
    "Quantum" -> Color(0xFF8C7CF5)
    "Imaginär" -> Color(0xFFF2D441)
    else -> Color(0xFF8A889E)
}

/** Rahmenfarbe nach Seltenheit: 5★ golden, 4★ violett. */
fun rarityColor(rarity: Int): Color = when (rarity) {
    5 -> Palette.Gold
    4 -> Palette.Epic
    3 -> Color(0xFF5B8FD9)
    else -> Color(0xFF8A889E)
}

/**
 * Vollbild-Hintergrund wie in der Web-Version (shared/assets/images/star-rail-bg.jpg, zugeschnitten wie
 * `background-size: cover`), mit dunkler Abdeckung für die Lesbarkeit. Vor dem Laden bleibt es einfarbig.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            try {
                // Das Bild ist groß; auf Handygröße verkleinert laden spart Speicher
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                context.assets.open("assets/images/star-rail-bg.jpg").use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Palette.BgWindow)) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Palette.BgWindow.copy(alpha = 0.45f)))
        }
        content()
    }
}
