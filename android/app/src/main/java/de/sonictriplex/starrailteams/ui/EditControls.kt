package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.sonictriplex.starrailteams.data.StammdatenRepository

/**
 * Zahlenfeld. Die obere Grenze greift sofort, die untere beim Übernehmen (so lässt sich "1" → "15" tippen).
 */
@Composable
fun IntField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(3)
            val n = digits.toIntOrNull()
            text = if (n != null && n > range.last) range.last.toString() else digits
            n?.let { onChange(it.coerceIn(range)) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Auswahlfeld mit Menü für kurze Listen. */
@Composable
fun Dropdown(selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { "–" }, maxLines = 1)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** Rückfrage beim Verlassen mit ungespeicherten Änderungen. */
@Composable
fun DiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Änderungen verwerfen?") },
        text = { Text("Deine Änderungen wurden noch nicht gespeichert.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Verwerfen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Weiter bearbeiten") } },
    )
}

/** Eintrag einer Suchliste; [withIcon] zeigt das Bild aus den Assets an. */
data class PickerItem(
    val name: String,
    val subtitle: String = "",
    val detail: String = "",
    /** Weitere durchsuchbare Namen (Slot, Name), z. B. die Teile eines Relikt-Sets; ein Treffer wird als Untertitel gezeigt. */
    val keywords: List<Pair<String, String>> = emptyList(),
)

/** Aufklappbare Beschreibung (Set-Boni der Relikte): auf dem Handy gibt es keinen Hover-Tooltip. Ohne Text wird nichts angezeigt. */
@Composable
fun InfoToggle(text: String?) {
    if (text == null) return
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(if (open) "ⓘ Beschreibung ausblenden" else "ⓘ Beschreibung anzeigen") }
        if (open) Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Vollbild-ähnlicher Dialog mit Suchfeld für lange Listen (Lichtkegel, Relikt-Sets). */
@Composable
fun SearchPickerDialog(
    title: String,
    items: List<PickerItem>,
    repo: StammdatenRepository?,
    noneLabel: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, items) {
        val q = query.trim()
        items.filter { item -> item.name.contains(q, ignoreCase = true) || item.keywords.any { it.second.contains(q, ignoreCase = true) } }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    query, { query = it }, label = { Text("Suchen") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    item {
                        Text(
                            noneLabel,
                            Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(shown, key = { it.name }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(item.name) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (repo != null) NameIcon(item.name, repo, 40.dp)
                            Column {
                                Text(item.name)
                                val q = query.trim()
                                val hit = if (q.isNotEmpty() && !item.name.contains(q, ignoreCase = true))
                                    item.keywords.firstOrNull { it.second.contains(q, ignoreCase = true) } else null
                                if (hit != null) {
                                    Text("${hit.first}: ${hit.second}", style = MaterialTheme.typography.bodySmall)
                                } else if (item.subtitle.isNotEmpty()) {
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                                }
                                if (item.detail.isNotEmpty()) {
                                    Text(
                                        item.detail, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onDismiss, Modifier.align(Alignment.End)) { Text("Abbrechen") }
            }
        }
    }
}
