package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.sonictriplex.starrailteams.BuildConfig
import de.sonictriplex.starrailteams.data.StammdatenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MEDIA_MATRIX_URL = "https://www.media-matrix.de"

/** Kleiner „Über“-Dialog: Name, Version, Kurzbeschreibung, Fanprojekt-Hinweis und das media-matrix-Logo. */
@Composable
fun AboutDialog(repo: StammdatenRepository, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val paimon by produceState<ImageBitmap?>(null) { value = withContext(Dispatchers.IO) { repo.loadBitmap("assets/images/ui/paimon.png")?.asImageBitmap() } }
    val logo by produceState<ImageBitmap?>(null) { value = withContext(Dispatchers.IO) { repo.loadBitmap("assets/images/ui/mm_logo.png")?.asImageBitmap() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = null,
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                paimon?.let { Image(it, contentDescription = null, Modifier.height(72.dp), contentScale = ContentScale.Fit) }
                Text("Honkai: Star Rail – Team & Build Manager", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text("Android-Version ${BuildConfig.VERSION_NAME}")
                Text(
                    "Teams aus bis zu vier Charakteren und die Builds deiner Charaktere verwalten, " +
                        "abgeglichen mit der Web-Version.",
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Inoffizielles Fanprojekt, nicht mit HoYoverse verbunden. Honkai: Star Rail sowie zugehörige " +
                        "Namen und Bilder sind Eigentum von HoYoverse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                logo?.let {
                    // Das Logo öffnet die Webseite von the media-matrix im Browser
                    Image(
                        it, contentDescription = "the media-matrix, öffnet www.media-matrix.de",
                        Modifier.height(41.dp).clickable(onClickLabel = "Webseite öffnen") { uriHandler.openUri(MEDIA_MATRIX_URL) },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        },
    )
}


