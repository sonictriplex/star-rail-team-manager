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
import androidx.compose.foundation.lazy.itemsIndexed
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
    /** Angezeigter Titel, wenn er vom [name] abweicht (Relikt-Sets: das Teil des Slots); der Name steht dann als Untertitel. */
    val title: String = name,
    /** Überschrift der Gruppe, zu der der Eintrag gehört (z. B. "Teile der gewählten Sets"); leer = keine Gruppen. */
    val group: String = "",
    val subtitle: String = "",
    val detail: String = "",
    /** Weitere durchsuchbare Namen (Slot, Name), z. B. die Teile eines Relikt-Sets; ein Treffer erscheint als eigene Zeile. */
    val keywords: List<Pair<String, String>> = emptyList(),
)

/** Eine Zeile der Trefferliste: [pick] ist der Name, der beim Antippen übernommen wird, [iconName] der Name für das Bild. */
data class PickerRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    val pick: String,
    val iconName: String,
    val group: String = "",
)

/**
 * Trefferliste zu einer Suche: Einträge, deren Name passt, und danach je passendem Teil (Eintrag.keywords) eine eigene Zeile,
 * z. B. „Vielgesichtige Maske des Diebs“ mit „Kopf · Dieb des fallenden Meteors“; Antippen übernimmt den Eintrag (das Set).
 */
fun pickerRows(items: List<PickerItem>, query: String): List<PickerRow> {
    val q = query.trim()
    fun PickerItem.hit() = name.contains(q, ignoreCase = true) || title.contains(q, ignoreCase = true)
    fun PickerItem.row() = PickerRow(name, title, if (title == name) subtitle else name, detail, name, name, group)
    val byName = items.filter { it.hit() }.map { it.row() }
    if (q.isEmpty()) return byName
    val byPiece = items.filterNot { it.hit() }.flatMap { item ->
        item.keywords.filter { it.second.contains(q, ignoreCase = true) }.map { (slot, piece) ->
            // Mit eigenem Titel (Teil des Slots) bleibt das Teil des Slots die Überschrift, der Treffer steht darunter
            if (item.title != item.name) PickerRow("${item.name}\u0000$slot\u0000$piece", item.title, "$slot: $piece · ${item.name}", item.detail, item.name, item.name, item.group)
            else PickerRow("${item.name}\u0000$slot\u0000$piece", piece, "$slot · ${item.name}", item.detail, item.name, item.name, item.group)
        }
    }
    return byName + byPiece
}

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
    val shown = remember(query, items) { pickerRows(items, query) }
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
                    itemsIndexed(shown, key = { _, row -> row.key }) { index, item ->
                        if (item.group.isNotEmpty() && (index == 0 || shown[index - 1].group != item.group)) {
                            Text(
                                item.group, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(item.pick) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (repo != null) NameIcon(item.iconName, repo, 40.dp)
                            Column {
                                Text(item.title)
                                if (item.subtitle.isNotEmpty()) {
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
