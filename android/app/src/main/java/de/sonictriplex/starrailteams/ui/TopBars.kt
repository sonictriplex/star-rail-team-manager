package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Art des Symbols links in der Kopfzeile. */
enum class NavigationKind(val icon: ImageVector, val description: String) {
    Back(Icons.AutoMirrored.Filled.ArrowBack, "Zurück"),
    Close(Icons.Filled.Close, "Abbrechen"),
}

/**
 * Einheitliche Kopfzeile: links ein Symbol (Zurück oder Abbrechen), der Titel immer einzeilig mit Auslassung
 * (er bricht auch bei schmalem Display oder großer Schrift nicht um), rechts höchstens kurze Aktionen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    navigation: NavigationKind? = null,
    onNavigate: () -> Unit = {},
    navigationEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Zu lange Titel (z. B. Charakternamen) werden verkleinert, bis sie ganz lesbar sind (mindestens 12 sp)
    var titleSize by remember(title) { mutableStateOf(20f) }
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = titleSize.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow && titleSize > 12f) titleSize = (titleSize - 1f).coerceAtLeast(12f) },
            )
        },
        navigationIcon = {
            if (navigation != null) {
                IconButton(onClick = onNavigate, enabled = navigationEnabled) {
                    Icon(navigation.icon, contentDescription = navigation.description)
                }
            }
        },
        actions = actions,
    )
}
