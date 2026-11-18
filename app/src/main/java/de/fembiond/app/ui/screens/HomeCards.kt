package de.fembiond.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.EventRisk
import de.fembiond.app.domain.Mrs
import de.fembiond.app.domain.PeriStats
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.domain.de1
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.LevelBadge
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.Sparkline
import de.fembiond.app.ui.TermChip
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.relativeDays
import de.fembiond.app.ui.short
import de.fembiond.app.ui.theme.LocalPhaseColors
import de.fembiond.app.ui.unrestColor
import de.fembiond.app.ui.weekdayLong
import java.time.LocalDate
import java.time.LocalDateTime

/** Kurzinfo zu einem im Ring angetippten Tag. */
@Composable
fun DayPeek(data: AppData, date: LocalDate, cycleDay: Int, nav: NavController, onClose: () -> Unit) {
    val pc = LocalPhaseColors.current
    val info = if (date.isAfter(data.today)) CycleEngine.projectDay(data.summary, date) else CycleEngine.dayInfo(data.summary, date)
    val terms = data.occurrences.filter { it.date == date && it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
        .groupBy { it.term }.keys.take(5)
    val maxUnrest = data.episodes.filter { it.date == date }.maxOfOrNull { it.level }
    val events = data.events.filter { it.date == date }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date.weekdayLong(), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        info?.let {
                            Icon(it.phase.icon(), null, tint = pc.of(it.phase), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("Zyklustag $cycleDay" + (info?.let { " · ${it.phase.label}" } ?: "") + if (date.isAfter(data.today)) " (Prognose)" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (maxUnrest != null) LevelBadge(maxUnrest, 26.dp)
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Schließen") }
            }
            events.forEach { HintText("Ereignis: ${it.displayName}") }
            if (terms.isNotEmpty()) ChipFlow { terms.forEach { TermChip(it, false, null, {}) } }
            if (terms.isEmpty() && maxUnrest == null && events.isEmpty()) HintText(if (date.isAfter(data.today)) "Dieser Tag liegt in der Zukunft." else "Keine Einträge an diesem Tag.")
            if (!date.isAfter(data.today)) TextButton(onClick = { nav.navigate(Routes.day(date)) }) { Text("Tag öffnen") }
        }
    }
}

/** Freundlicher Planungshinweis für ein anstehendes Ereignis. */
@Composable
fun StressHintCard(r: EventRisk, data: AppData, nav: NavController) {
    SectionCard(
        title = (if (r.elevated) "Gut vorbereiten: " else "Hinweis: ") + r.event.displayName,
        icon = Icons.Rounded.TipsAndUpdates,
        container = if (r.elevated) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text("${r.event.date.weekdayLong()} (${relativeDays(data.today, r.event.date)})", style = MaterialTheme.typography.labelLarge)
        r.factors.forEach { Text("• ${it.replaceFirstChar { c -> c.uppercase() }}", style = MaterialTheme.typography.bodyMedium) }
        Text("Was helfen kann", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        r.tips.forEach { HintText("• $it") }
        TextButton(onClick = { nav.navigate(Routes.EVENTS) }) { Text("Zum Ereignis") }
    }
}

/** Mini-Verläufe der letzten 14 Tage. */
@Composable
fun TrendsCard(data: AppData, nav: NavController) {
    val days = 14
    val today = data.today
    val unrest = (days - 1 downTo 0).map { b -> val d = today.minusDays(b.toLong()); data.episodes.filter { it.date == d }.maxOfOrNull { it.level }?.toDouble() ?: 0.0 }
    fun scale(term: String) = (days - 1 downTo 0).map { b ->
        val d = today.minusDays(b.toLong())
        data.occurrences.filter { it.term == term && it.date == d }.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average()
    }
    val sleep = scale(ScaleTerms.SLEEP_QUALITY)
    val energy = scale(ScaleTerms.ENERGY)
    val hot = PeriStats.dailyCounts(data.occurrences, PeriStats.HOT_TERMS, today, days).map { it.toDouble() }
    if (unrest.all { it == 0.0 } && sleep.all { it == null } && energy.all { it == null } && hot.all { it == 0.0 }) return
    SectionCard(title = "Letzte 14 Tage", icon = Icons.Rounded.ShowChart) {
        @Composable
        fun row(label: String, value: String, content: @Composable () -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) { content() }
            }
        }
        row("Unruhe", "${unrest.count { it > 0 }} Tage") { Sparkline(unrest, 5.0, unrestColor(3), Modifier.fillMaxWidth(), bars = true) }
        if (sleep.any { it != null }) row("Schlaf", "Ø " + de1(sleep.filterNotNull().average())) { Sparkline(sleep, 5.0, MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth()) }
        if (energy.any { it != null }) row("Energie", "Ø " + de1(energy.filterNotNull().average())) { Sparkline(energy, 5.0, MaterialTheme.colorScheme.tertiary, Modifier.fillMaxWidth()) }
        if (hot.any { it > 0 }) row("Hitze", "${hot.sum().toInt()}×") { Sparkline(hot, (hot.maxOrNull() ?: 1.0).coerceAtLeast(3.0), LocalPhaseColors.current.follicular, Modifier.fillMaxWidth(), bars = true) }
        TextButton(onClick = { nav.navigate(Routes.ANALYSIS) }) { Text("Zur Auswertung") }
    }
}

/** Perimenopause: Hitzewallung / Nachtschweiß mit einem Tipp erfassen, MRS-Fragebogen. */
@Composable
fun PeriCard(vm: AppViewModel, data: AppData, nav: NavController) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val today = data.today
    val hotToday = PeriStats.countOn(data.occurrences, "Hitzewallung", today)
    val sweatToday = PeriStats.countOn(data.occurrences, "Nachtschweiß", today)
    val last = data.mrs.maxByOrNull { it.date }
    SectionCard(title = "Perimenopause", icon = Icons.Rounded.LocalFireDepartment) {
        HintText("Mit einem Tipp erfassen, sobald es auftritt – die Uhrzeit wird automatisch gespeichert.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                vm.run { saveEntry(LocalDateTime.now(), "", listOf(Selection("Hitzewallung", Category.PERIMENOPAUSE)), "quick") }
                android.widget.Toast.makeText(context, "Hitzewallung erfasst", android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Hitze" + if (hotToday > 0) " ($hotToday)" else "", maxLines = 1)
            }
            FilledTonalButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                vm.run { saveEntry(LocalDateTime.now(), "", listOf(Selection("Nachtschweiß", Category.PERIMENOPAUSE)), "quick") }
                android.widget.Toast.makeText(context, "Nachtschweiß erfasst", android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.NightsStay, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Nachtschweiß" + if (sweatToday > 0) " ($sweatToday)" else "", maxLines = 1)
            }
        }
        if (last != null) HintText("MRS zuletzt ${last.date.short()}: ${last.total} Punkte – ${Mrs.severity(last.total)}")
        if (data.mrsDue) {
            Text("Menopause-Fragebogen (MRS) ist wieder dran – 11 kurze Fragen.", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { nav.navigate(Routes.MRS) }) { Text("Jetzt ausfüllen") }
        } else TextButton(onClick = { nav.navigate(Routes.MRS) }) { Text("MRS-Fragebogen") }
    }
}
