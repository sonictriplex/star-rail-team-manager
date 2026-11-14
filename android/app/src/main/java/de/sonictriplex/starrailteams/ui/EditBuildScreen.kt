package de.sonictriplex.starrailteams.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.sonictriplex.starrailteams.data.Build
import de.sonictriplex.starrailteams.data.BuildOptions
import de.sonictriplex.starrailteams.data.RELIC_SLOTS
import de.sonictriplex.starrailteams.data.activeSetBonuses
import de.sonictriplex.starrailteams.data.bonusText
import de.sonictriplex.starrailteams.data.infoText
import de.sonictriplex.starrailteams.data.pieceList
import de.sonictriplex.starrailteams.data.passiveText
import de.sonictriplex.starrailteams.data.normalized
import de.sonictriplex.starrailteams.data.slotKind
import de.sonictriplex.starrailteams.data.withSlot
import de.sonictriplex.starrailteams.data.withSubstat

/** Bearbeiten und Speichern des Builds eines Charakters. Zurück verwirft ungespeicherte Änderungen. */
@Composable
fun EditBuildScreen(vm: AppViewModel, name: String, onDone: () -> Unit) {
    val character = vm.stammdaten.character(name)
    val initial = remember { (vm.userData?.builds?.firstOrNull { it.name == name } ?: Build(name = name)).normalized() }
    var build by remember { mutableStateOf(initial) }
    val dirty = build != initial
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (dirty) confirmDiscard = true else onDone() }
    BackHandler(enabled = dirty && !vm.saving) { leave() }
    LaunchedEffect(Unit) { vm.dismissError() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = name,   // "bearbeiten" ergibt sich aus der Ansicht; der Name allein bleibt auch auf schmalen Displays lesbar
                navigation = NavigationKind.Close,
                onNavigate = leave,
                navigationEnabled = !vm.saving,
                actions = {
                    TextButton(onClick = { vm.saveBuild(build, onSaved = onDone) }, enabled = !vm.saving) {
                        if (vm.saving) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Speichern")
                    }
                },
            )
        },
    ) { padding ->
        ScrollingContent(Modifier.padding(padding), twoColumns = true) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (confirmDiscard) DiscardDialog(onConfirm = { confirmDiscard = false; onDone() }, onDismiss = { confirmDiscard = false })

            TwoColumns(
                left = {
            Card("Charakter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("Level", build.level, 1..BuildOptions.LEVEL_MAX, { build = build.copy(level = it) }, Modifier.weight(1f))
                    Column(Modifier.weight(1f)) {
                        Label("Eidolon")
                        Dropdown("E${build.eidolon}", (0..6).map { "E$it" }, { build = build.copy(eidolon = it.drop(1).toInt()) })
                    }
                }
                Label("Talente: Basis · Fertigkeit · Ultimate · Talent")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val t = build.talents
                    IntField("B", t.basic, 1..BuildOptions.TALENT_MAX_BASIC, { build = build.copy(talents = t.copy(basic = it)) }, Modifier.weight(1f))
                    IntField("F", t.skill, 1..BuildOptions.TALENT_MAX_OTHER, { build = build.copy(talents = t.copy(skill = it)) }, Modifier.weight(1f))
                    IntField("U", t.ult, 1..BuildOptions.TALENT_MAX_OTHER, { build = build.copy(talents = t.copy(ult = it)) }, Modifier.weight(1f))
                    IntField("T", t.talent, 1..BuildOptions.TALENT_MAX_OTHER, { build = build.copy(talents = t.copy(talent = it)) }, Modifier.weight(1f))
                }
            }

            Card("Lichtkegel") {
                var picking by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                    Text(build.lightCone.ifBlank { "Lichtkegel wählen" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("Level", build.lcLevel, 1..BuildOptions.LEVEL_MAX, { build = build.copy(lcLevel = it) }, Modifier.weight(1f))
                    Column(Modifier.weight(1f)) {
                        Label("Überlagerung")
                        Dropdown("S${build.lcSuperimpose}", (1..5).map { "S$it" }, { build = build.copy(lcSuperimpose = it.drop(1).toInt()) })
                    }
                }
                InfoToggle(vm.stammdaten.lightCone(build.lightCone)?.passiveText(build.lcSuperimpose))
                if (picking) {
                    // Zuerst die Lichtkegel des Pfads des Charakters, seltene zuerst, danach alle übrigen
                    val cones = vm.stammdaten.stammdaten.lightCones
                        .sortedWith(
                            compareByDescending<de.sonictriplex.starrailteams.data.LightCone> { character != null && it.path == character.path }
                                .thenByDescending { it.rarity }.thenBy { it.name },
                        )
                        .map { PickerItem(it.name, "${it.rarity} ★ · ${it.path}", it.passiveText(1).orEmpty()) }
                    SearchPickerDialog(
                        "Lichtkegel wählen", cones, vm.stammdaten, "Kein Lichtkegel",
                        onPick = { build = build.copy(lightCone = it); picking = false },
                        onDismiss = { picking = false },
                    )
                }
            }

            Card("Aktive Set-Boni") {
                val active = build.activeSetBonuses()
                if (active.isEmpty()) Text("Keine aktiven Set-Boni", style = MaterialTheme.typography.bodySmall)
                active.forEach { (setName, n) ->
                    Text("${n}× $setName")
                    InfoToggle(vm.stammdaten.relicSet(setName)?.infoText()?.let { "$setName\n\n$it" })
                }
            }

                },
                right = {
            RELIC_SLOTS.forEach { slotName ->
                val slot = build.relics.getValue(slotName)
                Card(slotName) {
                    SetPicker(slotName, slot.relicSet, vm) { v -> build = build.withSlot(slotName) { it.copy(relicSet = v) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IntField("Level", slot.level, 0..BuildOptions.RELIC_LEVEL_MAX, { v -> build = build.withSlot(slotName) { it.copy(level = v) } }, Modifier.weight(1f))
                        Dropdown(
                            "${slot.rarity}★", BuildOptions.RARITIES.map { "$it★" },
                            { v -> build = build.withSlot(slotName) { it.copy(rarity = v.dropLast(1).toInt()) } }, Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dropdown(
                            slot.main, BuildOptions.MAIN_STATS.getValue(slotName),
                            { v -> build = build.withSlot(slotName) { it.copy(main = v) } }, Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            slot.mainValue, { v -> build = build.withSlot(slotName) { it.copy(mainValue = v) } },
                            label = { Text("Wert") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                    Label("Substats")
                    slot.substats.forEachIndexed { i, sub ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dropdown(
                                sub.stat.ifBlank { BuildOptions.NONE }, listOf(BuildOptions.NONE) + BuildOptions.SUBSTATS,
                                { v ->
                                    val stat = if (v == BuildOptions.NONE) "" else v
                                    build = build.withSlot(slotName) { s -> s.withSubstat(i) { it.copy(stat = stat) } }
                                },
                                Modifier.weight(2f),
                            )
                            OutlinedTextField(
                                sub.value, { v -> build = build.withSlot(slotName) { s -> s.withSubstat(i) { it.copy(value = v) } } },
                                label = { Text("Wert") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
                },
            )
        }
    }
}

/** Auswahl des Relikt-Sets eines Slots: Kopf bis Füße nehmen Relikt-Sets, Planarsphäre und Verbindungsseil Ornamente. */
@Composable
private fun SetPicker(slotName: String, value: String, vm: AppViewModel, onPick: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val kind = slotKind(slotName)
    val none = if (kind == "Relikt") "Kein Relikt-Set" else "Kein Ornament"
    OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) { Text(value.ifBlank { none }, maxLines = 1) }
    InfoToggle(vm.stammdaten.relicSet(value)?.infoText())
    if (picking) {
        val sets = vm.stammdaten.stammdaten.relicSets.filter { it.kind == kind }
            .sortedBy { it.name.lowercase() }
            .map { PickerItem(it.name, detail = it.bonusText().orEmpty(), keywords = it.pieceList()) }
        SearchPickerDialog(
            if (kind == "Relikt") "Relikt-Set wählen" else "Planar-Ornament wählen", sets, vm.stammdaten, none,
            onPick = { onPick(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium)

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
            content()
        }
    }
}
