package de.fembiond.app.ui.screens

import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.UnrestLevel
import de.fembiond.app.domain.daysBetween
import de.fembiond.app.ui.AddChip
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.LevelBadge
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.TermChip
import de.fembiond.app.ui.hm
import de.fembiond.app.ui.relativeDays
import de.fembiond.app.ui.unrestColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private val durations = listOf(10 to "< 15 Min.", 30 to "bis 1 Std.", 120 to "einige Std.", 480 to "fast den ganzen Tag")
private val whenOptions = listOf(0 to "gerade eben", 60 to "vor 1 Std.", 180 to "vor 3 Std.", 720 to "heute früher")

@Composable
fun UnrestScreen(vm: AppViewModel, data: AppData, nav: NavController, preselectEvent: Long? = null, day: LocalDate? = null) {
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    var duration by rememberSaveable { mutableStateOf<Int?>(null) }
    var ago by rememberSaveable { mutableStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    var eventId by rememberSaveable { mutableStateOf(preselectEvent) }
    val symptoms = remember { mutableStateListOf<String>() }
    val triggers = remember { mutableStateListOf<String>() }
    val helped = remember { mutableStateListOf<String>() }
    var addFor by remember { mutableStateOf<Category?>(null) }
    var newEvent by remember { mutableStateOf(false) }
    val baseDay = day ?: data.today

    val nearbyEvents = data.events
        .filter { daysBetween(baseDay, it.date) in -2..21 }
        .sortedBy { kotlin.math.abs(daysBetween(baseDay, it.date)) }
        .take(6)

    Screen(
        title = "Unruhe erfassen",
        nav = nav,
        jumpBar = true,
        actions = { androidx.compose.material3.IconButton(onClick = { nav.navigate(de.fembiond.app.Routes.guide("unrest")) }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.HelpOutline, "Hilfe") } },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    val l = level
                    Button(
                        enabled = l != null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val time = if (day != null && day != data.today) day.atTime(12, 0) else LocalDateTime.now().minusMinutes(ago.toLong())
                            vm.run { saveEpisode(time, duration, l!!, symptoms.toList(), triggers.toList(), helped.toList(), note, eventId = eventId) }
                            nav.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = if (l == 5) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                        else ButtonDefaults.buttonColors(),
                    ) { Text(if (l == null) "Stufe wählen" else "Speichern", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) {
        SectionCard(title = "Wie stark ist die Unruhe?", icon = Icons.Rounded.Waves) {
            UnrestLevel.entries.forEach { u ->
                val selected = level == u.level
                Card(
                    onClick = { haptic.performHapticFeedback(if (u.level >= 4) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentTick); level = u.level },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = if (selected) BorderStroke(2.dp, unrestColor(u.level)) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(u.level)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.label, style = MaterialTheme.typography.titleSmall)
                            Text(u.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        SectionCard(title = "Hängt es mit einem Ereignis zusammen?", icon = Icons.Rounded.Event) {
            if (nearbyEvents.isEmpty()) HintText("Keine Ereignisse in den nächsten Wochen geplant.")
            ChipFlow {
                nearbyEvents.forEach { e ->
                    val sel = eventId == e.id
                    FilterChip(
                        selected = sel,
                        onClick = {
                            eventId = if (sel) null else e.id
                            if (!sel && "Anstehendes Ereignis" !in triggers && !e.date.isBefore(baseDay)) triggers.add("Anstehendes Ereignis")
                        },
                        label = { Text("${e.displayName} · ${relativeDays(baseDay, e.date)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                AddChip("Ereignis anlegen") { newEvent = true }
            }
        }

        if (day == null) SectionCard(title = "Wann und wie lange", icon = Icons.Rounded.Schedule) {
            ChipFlow { whenOptions.forEach { (m, l) -> FilterChip(ago == m, { ago = m }, { Text(l) }) } }
            ChipFlow { durations.forEach { (m, l) -> FilterChip(duration == m, { duration = if (duration == m) null else m }, { Text(l) }) } }
        }

        listOf(
            Triple(Category.PANIC_SYMPTOM, symptoms, Icons.Rounded.Air),
            Triple(Category.PANIC_TRIGGER, triggers, Icons.Rounded.TrackChanges),
            Triple(Category.PANIC_HELP, helped, Icons.Rounded.SelfImprovement),
        ).forEach { (cat, list, icon) ->
            SectionCard(title = cat.label.removePrefix("Unruhe: "), icon = icon) {
                ChipFlow {
                    val shown = (data.suggestions(cat).map { it.label } + list).distinct()
                    shown.forEach { label ->
                        TermChip(label, label in list, null, { if (label in list) list.remove(label) else list.add(label) })
                    }
                    AddChip { addFor = cat }
                }
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }

    addFor?.let { cat ->
        NewTermDialog(
            data = data, initial = "", initialCategory = cat, categories = listOf(cat),
            onDismiss = { addFor = null },
            onNew = { label, _ ->
                when (cat) {
                    Category.PANIC_SYMPTOM -> symptoms.add(label)
                    Category.PANIC_TRIGGER -> triggers.add(label)
                    else -> helped.add(label)
                }
                addFor = null
            },
        )
    }
    if (newEvent) {
        EventDialog(data, null, onDismiss = { newEvent = false }, onSave = { e ->
            newEvent = false
            scope.launch {
                eventId = vm.repo.saveEvent(e)
                if ("Anstehendes Ereignis" !in triggers) triggers.add("Anstehendes Ereignis")
            }
        })
    }
}

/** Unruhe-Episode kompakt in Listen. */
@Composable
fun EpisodeRow(e: de.fembiond.app.domain.Episode, data: AppData, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelBadge(e.level, 26.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${e.time.hm()} · ${if (e.isPanic) "Panikattacke" else "Unruhe ${e.unrestLevel.label}"}" +
                    (e.durationMinutes?.let { d -> durations.firstOrNull { it.first == d }?.let { " · ${it.second}" } ?: " · ca. $d Min." } ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            val event = e.eventId?.let { id -> data.events.firstOrNull { it.id == id } }
            val details = listOfNotNull(
                event?.let { "Bezug: ${it.displayName} (${relativeDays(e.date, it.date)})" },
                e.symptoms.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Anzeichen: "),
                e.triggers.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Auslöser: "),
                e.helped.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Geholfen: "),
            )
            details.forEach { HintText(it) }
            if (e.note.isNotBlank()) Text("„${e.note}“", style = MaterialTheme.typography.bodyMedium)
        }
        trailing()
    }
}
