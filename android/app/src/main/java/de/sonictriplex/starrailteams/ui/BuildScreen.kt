package de.sonictriplex.starrailteams.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.sonictriplex.starrailteams.data.RELIC_SLOTS
import de.sonictriplex.starrailteams.data.activeSetBonuses
import de.sonictriplex.starrailteams.data.bonusText
import de.sonictriplex.starrailteams.data.Build

/** Ansicht des Builds eines Charakters; Bearbeiten über die Leiste oben. */
@Composable
fun BuildScreen(vm: AppViewModel, name: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val character = vm.stammdaten.character(name)
    val build: Build? = vm.userData?.builds?.firstOrNull { it.name == name }

    Scaffold(
        topBar = {
            AppTopBar(
                title = name,
                navigation = NavigationKind.Back,
                onNavigate = onBack,
                actions = { TextButton(onClick = onEdit, enabled = !vm.offline) { Text(if (build == null) "Anlegen" else "Bearbeiten") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.offline) OfflineBanner(vm.dataSavedAt)
            ScrollingContent(twoColumns = true) {
                if (build == null) {
                    BuildHeader(name, character, vm)
                    Text("Für diesen Charakter ist noch kein Build gespeichert.")
                } else {
                    TwoColumns(
                        left = {
                            BuildHeader(name, character, vm)
                            Section("Charakter") {
                                Text("Level ${build.level} · Eidolon E${build.eidolon}")
                                Text("Talente: ${build.talents.basic} / ${build.talents.skill} / ${build.talents.ult} / ${build.talents.talent}")
                                Text("Basis / Fertigkeit / Ultimate / Talent", style = MaterialTheme.typography.bodySmall)
                            }
                            Section("Lichtkegel") {
                                if (build.lightCone.isBlank()) Text("Kein Lichtkegel") else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        NameIcon(build.lightCone, vm.stammdaten, 56.dp)
                                        Column {
                                            Text(build.lightCone)
                                            Text("Level ${build.lcLevel} · Überlagerung S${build.lcSuperimpose}")
                                        }
                                    }
                                }
                            }
                            Section("Relikt-Sets") {
                                val active = build.activeSetBonuses()
                                if (active.isEmpty()) Text("Keine aktiven Set-Boni")
                                active.forEach { (setName, n) ->
                                    Text("${n}× $setName")
                                    InfoToggle(vm.stammdaten.relicSet(setName)?.bonusText()?.let { "$setName\n\n$it" })
                                }
                            }
                        },
                        right = {
                            RELIC_SLOTS.forEach { slotName ->
                                val slot = build.relics[slotName] ?: return@forEach
                                Section("$slotName · ${slot.rarity}★ · +${slot.level}") {
                                    if (slot.relicSet.isNotBlank()) Text(slot.relicSet)
                                    Text("${slot.main}: ${slot.mainValue}", style = MaterialTheme.typography.titleSmall)
                                    slot.substats.filter { it.stat.isNotBlank() }.forEach { Text("${it.stat}  ${it.value}") }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildHeader(name: String, character: de.sonictriplex.starrailteams.data.Character?, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(elementGradient(elementColor(character?.element.orEmpty()), 0.35f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameIcon(name, vm.stammdaten, 96.dp)
        Column {
            if (character != null) {
                Text(character.element, color = elementColor(character.element), style = MaterialTheme.typography.titleMedium)
                                Text("${character.rarity} ★")
                Text(character.path)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
            content()
        }
    }
}
