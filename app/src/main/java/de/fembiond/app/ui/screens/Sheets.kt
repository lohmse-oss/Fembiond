package de.fembiond.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.data.Selection
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.Lexicon
import de.fembiond.app.domain.PeriStats
import de.fembiond.app.domain.daysBetween
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.UnrestLevelPicker
import de.fembiond.app.ui.morphShapes
import de.fembiond.app.ui.relativeDays
import java.time.LocalDateTime

/** Begriff für Tage ohne Unruhe – damit ruhige Tage in Vergleichen als 0 zählen. */
const val CALM_TERM = "ausgeglichen"

/** Unruhe schnell erfassen, ohne den Startbildschirm zu verlassen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickUnrestSheet(vm: AppViewModel, data: AppData, nav: NavController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var level by remember { mutableStateOf<Int?>(null) }
    var eventId by remember { mutableStateOf<Long?>(null) }
    val events = data.events.filter { daysBetween(data.today, it.date) in -1..14 }.sortedBy { it.date }.take(5)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Unruhe erfassen", style = MaterialTheme.typography.titleLarge)
            UnrestLevelPicker(level, { level = it })
            androidx.compose.material3.TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                vm.run { saveEntry(LocalDateTime.now(), "", listOf(Selection(CALM_TERM, Category.MIND)), "calm") }
                Toast.makeText(context, "Ruhiger Tag erfasst", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Heute keine Unruhe – ruhigen Tag eintragen") }
            if (events.isNotEmpty()) {
                Text("Hängt es mit einem Ereignis zusammen?", style = MaterialTheme.typography.labelLarge)
                ChipFlow {
                    events.forEach { e ->
                        FilterChip(eventId == e.id, { eventId = if (eventId == e.id) null else e.id },
                            { Text("${e.displayName} · ${relativeDays(data.today, e.date)}", maxLines = 1) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onDismiss(); nav.navigate(Routes.unrest(eventId)) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Mehr Details") }
                Button(
                    enabled = level != null,
                    onClick = {
                        val l = level ?: return@Button
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.run {
                            saveEpisode(LocalDateTime.now(), null, l, emptyList(), if (eventId != null) listOf("Anstehendes Ereignis") else emptyList(), emptyList(), "", eventId = eventId)
                        }
                        Toast.makeText(context, "Unruhe Stufe $l erfasst", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    shapes = morphShapes(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Speichern") }
            }
        }
    }
}

/** Beschwerden (Perimenopause, PMS …) mit einem Tipp erfassen – mehrere nacheinander möglich. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSymptomSheet(vm: AppViewModel, data: AppData, nav: NavController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val own = PeriStats.quickTerms(data.occurrences, data.today, 8).filter { t -> PeriStats.symptomStats(data.occurrences, data.today.minusDays(120), data.today).any { it.term == t } }
    val more = PeriStats.GROUPS.flatMap { it.second }.filter { it !in own }
    fun categoryOf(t: String): Category =
        data.terms.firstOrNull { it.label == t && it.category.isDaily }?.category
            ?: Lexicon.entries.firstOrNull { it.label == t && it.category.isDaily }?.category ?: Category.BODY
    var pending by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Beschwerde erfassen", style = MaterialTheme.typography.titleLarge)
            HintText("Beschwerde antippen, Stärke wählen (optional) und speichern. Die Uhrzeit wird mitgespeichert; mehrere nacheinander sind möglich.")
            pending?.let { t ->
                de.fembiond.app.ui.StrengthPrompt(t, onCancel = { pending = null }, onSave = { level ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    vm.run { saveEntry(LocalDateTime.now(), "", listOf(Selection(t, categoryOf(t), level)), "quick") }
                    Toast.makeText(context, "„$t“ erfasst", Toast.LENGTH_SHORT).show()
                    pending = null
                })
            }
            @Composable
            fun chips(list: List<String>) = ChipFlow {
                list.forEach { t ->
                    val n = PeriStats.countOn(data.occurrences, t, data.today)
                    AssistChip(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); pending = t },
                        label = { Text(if (n > 0) "$t ($n)" else t) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                    )
                }
            }
            if (own.isNotEmpty()) {
                Text("Deine häufigsten", style = MaterialTheme.typography.labelLarge)
                chips(own)
            }
            Text(if (own.isEmpty()) "Typische Beschwerden" else "Weitere typische", style = MaterialTheme.typography.labelLarge)
            chips(more.take(18))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onDismiss(); nav.navigate(Routes.entry()) }, modifier = Modifier.weight(1f)) { Text("Freitext / mehr") }
                Button(onClick = onDismiss, shapes = morphShapes(), modifier = Modifier.weight(1f)) { Text("Fertig") }
            }
        }
    }
}
