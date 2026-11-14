package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Breitenklassen wie bei Material (Compact < 600 dp, Medium < 840 dp, sonst Expanded). */
enum class WidthClass { Compact, Medium, Expanded }

@Composable
fun rememberWidthClass(): WidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> WidthClass.Compact
        width < 840 -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}

/** Maximale Inhaltsbreite einer einzelnen Spalte bzw. zweier Spalten nebeneinander. */
val SingleColumnMaxWidth: Dp = 640.dp
val TwoColumnMaxWidth: Dp = 1400.dp

/** Scrollender, bei breitem Fenster zentrierter Inhalt mit begrenzter Breite und einheitlichem Rand. */
@Composable
fun ScrollingContent(
    modifier: Modifier = Modifier,
    twoColumns: Boolean = false,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxWidth = if (twoColumns && rememberWidthClass() != WidthClass.Compact) TwoColumnMaxWidth else SingleColumnMaxWidth
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Zwei Spalten nebeneinander, sobald das Fenster nicht mehr schmal ist (ab 600 dp); sonst untereinander. */
@Composable
fun TwoColumns(
    spacing: Dp = 12.dp,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    if (rememberWidthClass() != WidthClass.Compact) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = left)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = right)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            left()
            right()
        }
    }
}
