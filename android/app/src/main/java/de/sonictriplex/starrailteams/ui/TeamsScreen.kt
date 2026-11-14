package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TeamsScreen(vm: AppViewModel, onOpenCharacter: (String) -> Unit, onNewTeam: () -> Unit, onEditTeam: (String) -> Unit) {
    val data = vm.userData
    var deleting by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var updatingData by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Team & Build Manager",
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Neu laden")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Abmelden") },
                                onClick = { menuOpen = false; vm.logout() },
                                enabled = !vm.busy,
                            )
                            if (vm.isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Stammdaten aktualisieren") },
                                    onClick = { menuOpen = false; updatingData = true },
                                    enabled = !vm.busy && !vm.offline,
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Passwort ändern") },
                                onClick = { menuOpen = false; vm.dismissPasswordError(); changingPassword = true },
                                enabled = !vm.busy && !vm.offline,
                            )
                            DropdownMenuItem(
                                text = { Text("Über") },
                                onClick = { menuOpen = false; showAbout = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Konto löschen", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; vm.dismissAccountError(); deletingAccount = true },
                                enabled = !vm.busy && !vm.offline,
                            )
                        }
                    }
                },
            )
        },
        // Neue Teams lassen sich nur mit Verbindung anlegen
        floatingActionButton = {
            if (!vm.offline) {
                FloatingActionButton(onClick = onNewTeam) {
                    Icon(Icons.Filled.Add, contentDescription = "Neues Team")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy && data != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.offline) OfflineBanner(vm.dataSavedAt)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            when {
                data == null && vm.busy -> CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
                data == null -> Unit
                data.teams.isEmpty() -> Text("Noch keine Teams. Tippe auf „Neues Team“, um das erste anzulegen.", Modifier.padding(16.dp))
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val columns = when (rememberWidthClass()) {
                        WidthClass.Compact -> 1
                        WidthClass.Medium -> 2
                        WidthClass.Expanded -> 3
                    }
                    LazyVerticalGrid(
                    GridCells.Fixed(columns),
                    Modifier.widthIn(max = if (columns > 1) TwoColumnMaxWidth else SingleColumnMaxWidth).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(data.teams, key = { _, team -> team.name }) { index, team ->
                        AppCard(alpha = 0.4f) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Der Name bekommt den ganzen Platz; die Aktionen sind kompakte Symbole
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        team.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { vm.moveTeam(team.name, -1) }, enabled = !vm.offline && !vm.saving && index > 0) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Team nach oben")
                                    }
                                    IconButton(onClick = { vm.moveTeam(team.name, 1) }, enabled = !vm.offline && !vm.saving && index < data.teams.lastIndex) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Team nach unten")
                                    }
                                    IconButton(onClick = { onEditTeam(team.name) }, enabled = !vm.offline) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Team bearbeiten")
                                    }
                                    IconButton(onClick = { deleting = team.name }, enabled = !vm.offline) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Team löschen")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    team.members.forEach { member ->
                                        Column(Modifier.width(64.dp).clickable { onOpenCharacter(member) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                            NameIcon(member, vm.stammdaten, 64.dp, level = data.builds.firstOrNull { it.name == member }?.level ?: 80, flatBackground = true)
                                            Text(member, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showAbout) AboutDialog(vm.stammdaten, onDismiss = { showAbout = false })

    if (deletingAccount) {
        DeleteAccountDialog(vm, onDismiss = { deletingAccount = false })
    }

    if (changingPassword) ChangePasswordDialog(vm, onDismiss = { changingPassword = false })
    if (updatingData && vm.isAdmin) StammdatenUpdateDialog(vm, onDismiss = { updatingData = false })

    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Team löschen?") },
            text = { Text("Das Team „$name“ wird gelöscht. Die Builds der Charaktere bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { vm.deleteTeam(name); deleting = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }
}

/** Fragt vor dem endgültigen Löschen des Kontos das Passwort ab. Nach Erfolg meldet die App von selbst ab. */
@Composable
private fun DeleteAccountDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.saving) onDismiss() },
        title = { Text("Konto löschen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Dein Konto „${vm.session.username}“ wird samt allen Teams und Builds endgültig gelöscht, " +
                        "auch in der Web-Version. Das lässt sich nicht rückgängig machen.",
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text("Passwort zur Bestätigung") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = vm.accountError != null,
                    supportingText = vm.accountError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.deleteAccount(password) }, enabled = password.isNotEmpty() && !vm.saving) {
                if (vm.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Endgültig löschen", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.saving) { Text("Abbrechen") } },
    )
}
