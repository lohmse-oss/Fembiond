package de.fembiond.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.DateInput
import de.fembiond.app.domain.MedicationPhase
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.IntensitySelector
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.short
import java.time.LocalDate

@Composable
fun OnboardingScreen(vm: AppViewModel, data: AppData) {
    var step by rememberSaveable { mutableStateOf(0) }
    var birthYear by rememberSaveable { mutableStateOf(data.settings.birthYear?.toString() ?: "") }
    var cycle by rememberSaveable { mutableStateOf(data.settings.typicalCycle) }
    var period by rememberSaveable { mutableStateOf(data.settings.typicalPeriod) }
    val steps = 4

    fun finish() {
        vm.run {
            updateSettings { it.copy(onboardingDone = true, birthYear = birthYear.toIntOrNull(), typicalCycle = cycle, typicalPeriod = period) }
        }
    }

    Screen(
        title = "Einrichtung ${step + 1}/$steps",
        nav = null,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (step > 0) TextButton(onClick = { step-- }) { Text("Zurück") }
                        Spacer(Modifier.weight(1f))
                        if (step in 2 until steps - 1) TextButton(onClick = { step++ }) { Text("Überspringen") }
                        Button(onClick = {
                            if (step == 1) vm.run { updateSettings { it.copy(birthYear = birthYear.toIntOrNull(), typicalCycle = cycle, typicalPeriod = period) } }
                            if (step < steps - 1) step++ else finish()
                        }, modifier = Modifier.height(52.dp)) { Text(if (step < steps - 1) "Weiter" else "Los geht’s") }
                    }
                }
            }
        },
    ) {
        when (step) {
            0 -> {
                SectionCard(title = "Willkommen bei Fembiond", icon = Icons.Rounded.Spa) {
                    Text("Fembiond hilft dir, Zyklus, Körper und Stimmung festzuhalten und Zusammenhänge zu erkennen – etwa zwischen Zyklusphase, innerer Unruhe und Panikattacken.")
                    HintText("Die App dokumentiert Muster und ersetzt keine ärztliche Beratung.")
                }
                SectionCard(title = "Deine Daten bleiben bei dir", icon = Icons.Rounded.Lock) {
                    Text("Alles wird verschlüsselt nur auf diesem Gerät gespeichert. Es gibt keinen Server und keine Cloud. Exporte und Sicherungen teilst du nur, wenn du es selbst möchtest.")
                }
            }
            1 -> SectionCard(title = "Grunddaten") {
                OutlinedTextField(
                    birthYear, { birthYear = it.filter(Char::isDigit).take(4) },
                    label = { Text("Geburtsjahr (für die Einordnung Perimenopause)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Stepper("Typische Zykluslänge", cycle, 18..60, "Tage") { cycle = it }
                Stepper("Typische Periodenlänge", period, 1..12, "Tage") { period = it }
                HintText("Schätzwerte genügen. Sobald Perioden erfasst sind, rechnet die App mit deinen echten Werten.")
            }
            2 -> {
                HintText("Trage vergangene Perioden aus deinen Notizen ein. Schon 3–6 Zyklen machen die Prognose deutlich besser.")
                HistoryEditor(vm, data)
            }
            3 -> MedicationQuickStart(vm, data)
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 4.dp))
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }) { Icon(Icons.Rounded.Remove, "weniger") }
        Text("$value $unit".trim(), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }) { Icon(Icons.Rounded.Add, "mehr") }
    }
}

/** Listeneingabe für vergangene Perioden mit Plausibilitätsprüfung. */
@Composable
fun HistoryEditor(vm: AppViewModel, data: AppData) {
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var approx by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard(title = "Periode hinzufügen", icon = Icons.Rounded.History) {
        OutlinedTextField(
            input, { input = it; error = null },
            label = { Text("Datum, z. B. 03.09. – 07.09.") },
            supportingText = { Text(error ?: "Ende optional · ohne Jahr = letztes Vorkommen") },
            isError = error != null,
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(note, { note = it }, label = { Text("Notiz dazu (optional), z. B. „starke Krämpfe, unruhig“") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { approx = !approx }) {
            Checkbox(approx, { approx = it }); Text("Datum ist ungefähr")
        }
        Row {
            FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = {
                val r = DateInput.parseRange(input, data.today)
                if (r == null) { error = "Datum nicht erkannt" } else {
                    val (start, end) = r
                    val parsed = data.parser.parse(note)
                    vm.run {
                        addPeriod(start, end, approx, backfilled = true)
                        if (note.isNotBlank()) saveEntry(start.atTime(12, 0), note, parsed.terms.map {
                            de.fembiond.app.data.Selection(it.label, it.category, it.intensity, it.valence)
                        }, "backfill", backfilled = true)
                    }
                    input = ""; note = ""; approx = false
                }
            }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("Periode hinzufügen") }
        }
    }

    val periods = data.periods.sortedByDescending { it.start }
    val complete = (data.periods.size - 1).coerceAtLeast(0)
    SectionCard(title = "Erfasst: ${data.periods.size} Perioden · $complete vollständige Zyklen") {
        LinearProgressIndicator(progress = { (complete / 6f).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
        HintText(
            when {
                complete < 3 -> "Ab 3 Zyklen wird die Prognose persönlicher."
                complete < 6 -> "Gut! Ab 6 Zyklen sind Schwankungen zuverlässig erkennbar."
                else -> "Sehr gut – genug Daten für eine verlässliche Grundlinie."
            }
        )
        DateInput.warnings(data.periods.map { it.start }).forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(w, style = MaterialTheme.typography.bodySmall)
            }
        }
        periods.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.start.short() + (p.end?.let { " – ${it.short()}" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                    val tags = listOfNotNull("nachgetragen".takeIf { p.backfilled })
                    if (tags.isNotEmpty()) HintText(tags.joinToString(" · "))
                }
                androidx.compose.material3.FilterChip(p.approximate, { vm.run { updatePeriod(p.copy(approximate = !p.approximate)) } }, { Text("ca.") })
                IconButton(onClick = { vm.run { deletePeriod(p.id) } }) { Icon(Icons.Rounded.Delete, "Löschen") }
            }
        }
    }
}

@Composable
fun PanicBackfill(vm: AppViewModel, data: AppData) {
    var input by remember { mutableStateOf("") }
    var intensity by remember { mutableStateOf<Int?>(5) }
    var error by remember { mutableStateOf(false) }
    SectionCard(title = "Bekannte Unruhe / Panikattacken nachtragen", icon = Icons.Rounded.MonitorHeart) {
        OutlinedTextField(input, { input = it; error = false }, label = { Text("Datum, z. B. 12.10.") }, isError = error, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Stufe: 1 leicht · 2 spürbar · 3 stark · 4 sehr stark · 5 Panikattacke", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IntensitySelector(intensity, { intensity = it })
        OutlinedButton(onClick = {
            val d = DateInput.parseRange(input, data.today)?.first
            if (d == null) error = true else {
                vm.run { saveEpisode(d.atTime(12, 0), null, intensity ?: 3, emptyList(), emptyList(), emptyList(), "", backfilled = true) }
                input = ""
            }
        }) { Text("Nachtragen") }
        val back = data.episodes.filter { it.backfilled }
        if (back.isNotEmpty()) HintText("Nachgetragen: " + back.joinToString { "${it.date.short()} (Stufe ${it.level})" })
    }
}

@Composable
private fun MedicationQuickStart(vm: AppViewModel, data: AppData) {
    val has = data.medications.any { it.activeOn(data.today) || it.start.isAfter(data.today) }
    SectionCard(title = "Medikation", icon = Icons.Rounded.Medication) {
        Text("Medikamente wie Progesteron beeinflussen die Blutung. Die App trennt deshalb Zyklen mit und ohne Medikation in Prognose und Statistik.")
        if (has) {
            data.medications.forEach { HintText("${it.displayName()} · ${it.scheduleLabel()} · ab ${it.start.short()}") }
        } else {
            FilledTonalButton(onClick = {
                vm.run {
                    saveMedication(
                        MedicationPhase(
                            name = "Progesteron", form = "oral", dose = "", schedule = ScheduleType.CYCLIC,
                            cycleDayFrom = 14, cycleDayTo = 25, start = data.today, note = "12 Tage ab Zyklustag 14, danach Neubefund",
                        )
                    )
                }
            }) { Text("Progesteron oral, Zyklustag 14–25 anlegen") }
            HintText("Weitere oder andere Medikamente kannst du jederzeit unter Mehr → Medikation anlegen, ändern oder beenden.")
        }
    }
    SectionCard(title = "Fast geschafft") {
        Text("Eine ausführliche Anleitung findest du jederzeit unter Mehr → Anleitung, Versionsinfos unter Mehr → Über Fembiond. Tipp: Füge das Widget zum Startbildschirm hinzu.")
    }
}

@Suppress("unused")
private fun LocalDate.isPast(today: LocalDate) = isBefore(today)
