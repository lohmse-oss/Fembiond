package de.fembiond.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.DateInput
import de.fembiond.app.domain.MedicationPhase
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.short

/** Einnahmetreue: eingenommen / laut Schema fällig (bis heute). */
fun adherence(m: MedicationPhase, data: AppData): Pair<Int, Int> {
    val end = minOf(m.end ?: data.today, data.today)
    var due = 0
    var taken = 0
    var d = m.start
    while (!d.isAfter(end)) {
        if (CycleEngine.intakeDue(m, data.summary, d)) {
            due++
            if ((m.id to d) in data.intakes) taken++
        }
        d = d.plusDays(1)
    }
    return taken to due
}

@Composable
fun MedicationScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    var editing by remember { mutableStateOf<MedicationPhase?>(null) }
    var replacing by remember { mutableStateOf<MedicationPhase?>(null) }
    val today = data.today

    Screen(title = "Medikation", nav = nav) {
        HintText("Jede Behandlungsphase hat Start und optional Ende. Ändert sich Dosis, Form oder Schema, beginnt eine neue Phase – die alte bleibt für Vorher/Nachher-Vergleiche erhalten.")
        Button(onClick = {
            editing = MedicationPhase(name = "", form = "oral", dose = "", schedule = ScheduleType.CYCLIC, cycleDayFrom = 14, cycleDayTo = 25, start = today)
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Neue Behandlungsphase") }

        data.medications.sortedByDescending { it.start }.forEach { m ->
            val active = m.activeOn(today)
            val (taken, due) = adherence(m, data)
            SectionCard(title = m.displayName(), icon = Icons.Rounded.Medication, container = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("${m.scheduleLabel()} · ${m.start.short()} – ${m.end?.short() ?: "laufend"}")
                if (due > 0) HintText("Einnahme: $taken von $due fälligen Tagen abgehakt")
                m.reviewDate?.let { HintText("Neubefund/Arzttermin: ${it.short()}") }
                if (m.note.isNotBlank()) HintText(m.note)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = m }) { Text("Bearbeiten") }
                    if (active) {
                        OutlinedButton(onClick = { replacing = m }) { Text("Ändern") }
                        OutlinedButton(onClick = { vm.run { saveMedication(m.copy(end = today)) } }) { Text("Beenden") }
                    }
                }
            }
        }
        if (data.medications.isEmpty()) HintText("Noch keine Medikation erfasst.")
    }

    editing?.let { m ->
        MedicationDialog(m, title = if (m.id == 0L) "Neue Behandlungsphase" else "Bearbeiten", today = today,
            onDismiss = { editing = null },
            onDelete = if (m.id != 0L) ({ vm.run { deleteMedication(m.id) }; editing = null }) else null,
            onSave = { vm.run { saveMedication(it) }; editing = null })
    }
    replacing?.let { old ->
        MedicationDialog(old.copy(id = 0, start = today, end = null), title = "Neue Phase ab heute", today = today,
            onDismiss = { replacing = null }, onDelete = null,
            onSave = { new ->
                vm.run {
                    saveMedication(old.copy(end = new.start.minusDays(1)))
                    saveMedication(new)
                }
                replacing = null
            })
    }
}

@Composable
private fun MedicationDialog(
    initial: MedicationPhase,
    title: String,
    today: java.time.LocalDate,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (MedicationPhase) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var form by remember { mutableStateOf(initial.form) }
    var dose by remember { mutableStateOf(initial.dose) }
    var schedule by remember { mutableStateOf(initial.schedule) }
    var from by remember { mutableStateOf(initial.cycleDayFrom ?: 14) }
    var to by remember { mutableStateOf(initial.cycleDayTo ?: 25) }
    var start by remember { mutableStateOf(initial.start.short()) }
    var end by remember { mutableStateOf(initial.end?.short() ?: "") }
    var review by remember { mutableStateOf(initial.reviewDate?.short() ?: "") }
    var note by remember { mutableStateOf(initial.note) }
    val startDate = DateInput.parseRange(start, today.plusYears(1))?.first
    val endDate = end.takeIf { it.isNotBlank() }?.let { DateInput.parseRange(it, today.plusYears(1))?.first }
    val reviewDate = review.takeIf { it.isNotBlank() }?.let { DateInput.parseRange(it, today.plusYears(1))?.first }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Wirkstoff / Präparat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipFlow { listOf("oral", "vaginal", "Creme/Gel", "Pflaster", "andere").forEach { f -> FilterChip(form == f, { form = f }, { Text(f) }) } }
                OutlinedTextField(dose, { dose = it }, label = { Text("Dosis (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipFlow {
                    FilterChip(schedule == ScheduleType.CYCLIC, { schedule = ScheduleType.CYCLIC }, { Text("zyklisch") })
                    FilterChip(schedule == ScheduleType.CONTINUOUS, { schedule = ScheduleType.CONTINUOUS }, { Text("durchgehend") })
                }
                if (schedule == ScheduleType.CYCLIC) {
                    Stepper("ab Zyklustag", from, 1..40, "") { from = it; if (to < it) to = it }
                    Stepper("bis Zyklustag", to, from..60, "") { to = it }
                    HintText("${to - from + 1} Einnahmetage pro Zyklus")
                }
                OutlinedTextField(start, { start = it }, label = { Text("Start (TT.MM.JJJJ)") }, isError = startDate == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(end, { end = it }, label = { Text("Ende (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(review, { review = it }, label = { Text("Neubefund / Arzttermin (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Phase löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && startDate != null, onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(), form = form, dose = dose.trim(), schedule = schedule,
                        cycleDayFrom = if (schedule == ScheduleType.CYCLIC) from else null,
                        cycleDayTo = if (schedule == ScheduleType.CYCLIC) to else null,
                        start = startDate!!, end = endDate, reviewDate = reviewDate, note = note.trim(),
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
