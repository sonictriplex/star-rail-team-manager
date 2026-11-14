package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.sonictriplex.starrailteams.data.UpdateEntry
import de.sonictriplex.starrailteams.data.UpdateRequest

/** Passwort ändern: aktuelles Passwort und zweimal das neue. Der Server prüft das aktuelle Passwort. */
@Composable
fun ChangePasswordDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var old by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var done by rememberSaveable { mutableStateOf(false) }

    if (done) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Passwort geändert") },
            text = { Text("Dein Passwort wurde geändert. Andere angemeldete Geräte müssen sich neu anmelden; diese App bleibt angemeldet.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }

    @Composable
    fun field(label: String, value: String, onChange: (String) -> Unit, error: Boolean = false) = OutlinedTextField(
        value, { onChange(it); vm.dismissPasswordError() }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error, modifier = Modifier.fillMaxWidth(),
    )

    AlertDialog(
        onDismissRequest = { if (!vm.saving) onDismiss() },
        title = { Text("Passwort ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Aktuelles Passwort", old, { old = it }, vm.passwordError != null)
                field("Neues Passwort (6 bis 72 Zeichen)", new, { new = it })
                field("Neues Passwort wiederholen", repeat, { repeat = it })
                vm.passwordError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.changePassword(old, new, repeat) { done = true } },
                enabled = old.isNotEmpty() && new.isNotEmpty() && repeat.isNotEmpty() && !vm.saving,
            ) {
                if (vm.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Ändern")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.saving) { Text("Abbrechen") } },
    )
}

/**
 * Administrator: gleicht die Datenquelle mit der Datenbank ab und trägt die ausgewählten Charaktere, Lichtkegel und
 * Relikt-Sets samt Bildern ein (wie in der Web-Version). Die Quelle kann Einträge enthalten, die im Spiel noch nicht
 * erschienen sind; deshalb wählt man aus.
 */
@Composable
fun StammdatenUpdateDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.checkStammdaten() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val check = vm.updateCheck
    LaunchedEffect(check) {
        selected.clear()
        check?.found?.let { g ->
            g.characters.forEach { selected["c|${it.name}"] = true }
            g.lightCones.forEach { selected["l|${it.name}"] = true }
            g.relicSets.forEach { selected["r|${it.name}"] = true }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!vm.updateBusy) { vm.dismissUpdate(); onDismiss() } },
        title = { Text("Stammdaten aktualisieren") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Die Quelle kann Einträge enthalten, die im Spiel noch nicht erschienen sind. Trage nur ein, was wirklich verfügbar ist.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.updateBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                vm.updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.updateResult?.let { Text(it) }
                if (check != null) {
                    if (check.found.total == 0) Text("Alles aktuell. Es fehlt nichts.")
                    Group("Charaktere", "c", check.found.characters, selected)
                    Group("Lichtkegel", "l", check.found.lightCones, selected)
                    Group("Relikt-Sets", "r", check.found.relicSets, selected)
                    if (check.skipped.isNotEmpty()) {
                        Text("Nicht eintragbar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                        check.skipped.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = {
            if (check != null && check.found.total > 0) {
                TextButton(
                    onClick = {
                        fun names(prefix: String, list: List<UpdateEntry>) = list.filter { selected["$prefix|${it.name}"] == true }.map { it.name }
                        vm.applyStammdaten(
                            UpdateRequest(names("c", check.found.characters), names("l", check.found.lightCones), names("r", check.found.relicSets)),
                        )
                    },
                    enabled = selected.values.any { it } && !vm.updateBusy,
                ) { Text("Ausgewählte eintragen") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.dismissUpdate(); onDismiss() }, enabled = !vm.updateBusy) { Text("Schließen") } },
    )
}

@Composable
private fun Group(title: String, prefix: String, entries: List<UpdateEntry>, selected: MutableMap<String, Boolean>) {
    if (entries.isEmpty()) return
    Text("$title · ${entries.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
    entries.forEach { e ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected["$prefix|${e.name}"] == true, { selected["$prefix|${e.name}"] = it })
            Column {
                Text(e.name)
                if (e.summary().isNotEmpty()) Text(e.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
