package de.sonictriplex.starrailteams.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.sonictriplex.starrailteams.data.MAX_TEAM_NAME
import de.sonictriplex.starrailteams.data.MAX_TEAM_SIZE
import de.sonictriplex.starrailteams.data.Team
import de.sonictriplex.starrailteams.data.moved
import de.sonictriplex.starrailteams.data.validateTeamName

/** Neues Team anlegen ([originalName] = null) oder ein vorhandenes bearbeiten. */
@Composable
fun EditTeamScreen(vm: AppViewModel, originalName: String?, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(originalName.orEmpty()) }
    val members = remember {
        mutableStateListOf<String>().also { list ->
            originalName?.let { n -> vm.userData?.teams?.firstOrNull { it.name == n }?.let { list.addAll(it.members) } }
        }
    }
    val initialName = originalName.orEmpty()
    val initialMembers = remember { members.toList() }
    val dirty = name.trim() != initialName || members.toList() != initialMembers
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (dirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = dirty && !vm.saving) { leave() }
    var nameError by remember { mutableStateOf<String?>(null) }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { vm.dismissError() }

    fun move(from: Int, to: Int) {
        val reordered = members.toList().moved(from, to)
        members.clear()
        members.addAll(reordered)
    }

    fun save() {
        val existing = vm.userData?.teams.orEmpty().map { it.name }
        nameError = validateTeamName(name, existing, originalName)
        if (nameError == null) vm.saveTeam(Team(name.trim(), members.toList()), originalName, onSaved = onDone)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = originalName ?: "Neues Team",
                navigation = NavigationKind.Close,
                onNavigate = leave,
                navigationEnabled = !vm.saving,
                actions = {
                    TextButton(onClick = ::save, enabled = !vm.saving) {
                        if (vm.saving) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Speichern")
                    }
                },
            )
        },
    ) { padding ->
        ScrollingContent(Modifier.padding(padding), spacing = 16.dp) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (confirmDiscard) DiscardDialog(onConfirm = { confirmDiscard = false; onDone() }, onDismiss = { confirmDiscard = false })
            OutlinedTextField(
                name, { name = it.take(MAX_TEAM_NAME); nameError = null },
                label = { Text("Teamname") }, singleLine = true, isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Charaktere (${members.size}/$MAX_TEAM_SIZE)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(MAX_TEAM_SIZE) { slot ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val member = members.getOrNull(slot)
                        if (member != null) {
                            NameIcon(member, vm.stammdaten, 72.dp, Modifier.clickable { pickingSlot = slot })
                            Text(member, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row {
                                MoveButton("‹", enabled = slot > 0) { move(slot, slot - 1) }
                                MoveButton("›", enabled = slot < members.size - 1) { move(slot, slot + 1) }
                            }
                        } else {
                            EmptySlot { pickingSlot = members.size }
                        }
                    }
                }
            }
            Text("Tippe auf einen Charakter, um ihn zu ersetzen oder zu entfernen. Mit ‹ und › änderst du die Reihenfolge.", style = MaterialTheme.typography.bodySmall)
        }
    }

    pickingSlot?.let { slot ->
        val filled = slot < members.size
        // Jeder Charakter nur einmal im Team: schon gewählte (außer dem zu ersetzenden) ausblenden
        val taken = members.filterIndexed { i, _ -> i != slot }.toSet()
        val candidates = vm.stammdaten.stammdaten.characters
            .filter { it.name !in taken }
            .sortedBy { it.name }
            .map { PickerItem(it.name, "${it.rarity} ★ · ${it.element} · ${it.path}") }
        SearchPickerDialog(
            title = if (filled) "Charakter ersetzen" else "Charakter hinzufügen",
            items = candidates,
            repo = vm.stammdaten,
            noneLabel = if (filled) "Aus dem Team entfernen" else "Abbrechen",
            onPick = { picked ->
                when {
                    picked.isEmpty() -> if (filled) members.removeAt(slot)
                    filled -> members[slot] = picked
                    else -> members.add(picked)
                }
                pickingSlot = null
            },
            onDismiss = { pickingSlot = null },
        )
    }
}

@Composable
private fun MoveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = Modifier.size(width = 36.dp, height = 40.dp), contentPadding = PaddingValues(0.dp)) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Surface(
        Modifier.size(72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) { Text("+", style = MaterialTheme.typography.headlineMedium) }
    }
}
