package de.fembiond.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.Analytics
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.EventOutcome
import de.fembiond.app.domain.PlannedEvent
import de.fembiond.app.ui.AddChip
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.LevelBadge
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.TermChip
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.relativeDays
import de.fembiond.app.ui.theme.LocalPhaseColors
import de.fembiond.app.ui.weekdayLong
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Zyklus-Einordnung eines Ereignisdatums (bei Zukunft als Prognose). */
fun eventTiming(data: AppData, date: LocalDate): Pair<de.fembiond.app.domain.DayInfo?, String?> {
    val info = if (date.isAfter(data.today)) CycleEngine.projectDay(data.summary, date) else CycleEngine.dayInfo(data.summary, date)
    val hint = when {
        info == null -> null
        (info.daysBeforeNext ?: 99) in 1..7 -> "in den Tagen vor der Periode"
        info.phase == de.fembiond.app.domain.Phase.MENSTRUATION -> "während der Periode"
        else -> null
    }
    return info to hint
}

@Composable
fun EventItem(e: PlannedEvent, data: AppData, onClick: () -> Unit, showOutcome: Boolean = false, onOutcome: (EventOutcome) -> Unit = {}) {
    val pc = LocalPhaseColors.current
    val (info, hint) = eventTiming(data, e.date)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    e.date.weekdayLong() + " · " + relativeDays(data.today, e.date) + if (e.title.isNotBlank()) " · ${e.type}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info != null) Row(verticalAlignment = Alignment.Top) {
                    Icon(info.phase.icon(), null, tint = pc.of(info.phase), modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Zyklustag ${info.cycleDay} · ${info.phase.label}" + (if (e.date.isAfter(data.today)) " (Prognose)" else "") + (hint?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hint != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!e.date.isAfter(data.today)) {
                val lvl = Analytics.anticipatoryLevel(e, data.episodes)
                if (lvl > 0) { Spacer(Modifier.width(8.dp)); LevelBadge(lvl, 24.dp) }
            }
        }
        if (showOutcome) ChipFlow {
            EventOutcome.entries.filter { it != EventOutcome.PLANNED }.forEach { o ->
                FilterChip(e.outcome == o, { onOutcome(if (e.outcome == o) EventOutcome.PLANNED else o) }, { Text(o.label) })
            }
        }
    }
}

@Composable
fun EventsScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    var editing by remember { mutableStateOf<PlannedEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    val upcoming = data.events.filter { !it.date.isBefore(data.today) }.sortedBy { it.date }
    val past = data.events.filter { it.date.isBefore(data.today) }.sortedByDescending { it.date }

    Screen(title = "Ereignisse", nav = nav) {
        HintText("Plane Feiern, Essen gehen, Konzerte und Termine. Die App zeigt, in welche Zyklusphase sie voraussichtlich fallen, und wertet aus, wie sich die Unruhe davor entwickelt.")
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Ereignis planen")
        }
        SectionCard(title = "Anstehend", icon = Icons.Rounded.EventAvailable) {
            if (upcoming.isEmpty()) HintText("Nichts geplant.")
            upcoming.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider()
                EventItem(e, data, onClick = { editing = e })
                OutlinedButton(onClick = { nav.navigate(Routes.unrest(e.id)) }) { Text("Unruhe dazu erfassen") }
            }
        }
        if (past.isNotEmpty()) SectionCard(title = "Vergangen – wie ist es gelaufen?", icon = Icons.Rounded.History) {
            HintText("Die Zahl rechts zeigt die höchste Unruhe-Stufe in den 3 Tagen davor bis zum Ereignistag.")
            past.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider()
                EventItem(e, data, onClick = { editing = e }, showOutcome = true, onOutcome = { o -> vm.run { saveEvent(e.copy(outcome = o)) } })
            }
        }
    }

    if (creating) EventDialog(data, null, onDismiss = { creating = false }, onSave = { vm.run { saveEvent(it) }; creating = false })
    editing?.let { e ->
        EventDialog(data, e, onDismiss = { editing = null }, onSave = { vm.run { saveEvent(it) }; editing = null },
            onDelete = { vm.run { deleteEvent(e.id) }; editing = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    data: AppData,
    initial: PlannedEvent?,
    onDismiss: () -> Unit,
    onSave: (PlannedEvent) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var date by remember { mutableStateOf(initial?.date ?: data.today.plusDays(7)) }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var addType by remember { mutableStateOf(false) }
    val types = (data.suggestions(Category.EVENT_TYPE).map { it.label } + listOfNotNull(type.takeIf { it.isNotBlank() })).distinct()
    val (info, hint) = eventTiming(data, date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ereignis planen" else "Ereignis bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(date.weekdayLong())
                }
                if (info != null) HintText(
                    "Zyklustag ${info.cycleDay}, ${info.phase.label}" + (if (date.isAfter(data.today)) " (Prognose)" else "") + (hint?.let { " – $it" } ?: "")
                )
                Text("Art", style = MaterialTheme.typography.labelLarge)
                ChipFlow {
                    types.forEach { t -> TermChip(t, t == type, null, { type = t }) }
                    AddChip("Andere") { addType = true }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Bezeichnung (optional), z. B. „Annas 40.“") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Ereignis löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = type.isNotBlank(), onClick = {
                onSave((initial ?: PlannedEvent(date = date, type = type)).copy(date = date, type = type, title = title, note = note))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
    if (addType) {
        NewTermDialog(data = data, initial = "", initialCategory = Category.EVENT_TYPE, categories = listOf(Category.EVENT_TYPE),
            onDismiss = { addType = false }, onNew = { label, _ -> type = label; addType = false })
    }
}
