package de.fembiond.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.Mrs
import de.fembiond.app.domain.WeeklyReviewBuilder
import de.fembiond.app.domain.de1
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.BigNumber
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.LineChart
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.dm
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.short
import de.fembiond.app.ui.theme.LocalPhaseColors
import de.fembiond.app.ui.weekdayLong

@Composable
fun MrsScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    val answers = remember { mutableStateListOf<Int?>(*arrayOfNulls(Mrs.ITEMS.size)) }
    val haptic = LocalHapticFeedback.current
    Screen(title = "Menopause-Fragebogen", nav = nav) {
        HintText("Menopause Rating Scale (MRS): Welche der folgenden Beschwerden hast du derzeit, und wie stark? Kreuze bei jeder Beschwerde an.")
        Mrs.ITEMS.forEachIndexed { i, (title, desc) ->
            SectionCard {
                Text("${i + 1}. $title", style = MaterialTheme.typography.titleSmall)
                HintText(desc)
                ChipFlow {
                    Mrs.ANSWERS.forEachIndexed { v, a ->
                        FilterChip(answers[i] == v, { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); answers[i] = v }, { Text(a) })
                    }
                }
            }
        }
        val complete = answers.all { it != null }
        if (complete) {
            val list = answers.map { it ?: 0 }
            val r = de.fembiond.app.domain.MrsRecord(data.today, list)
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text("Gesamt: ${r.total} von 44 – ${Mrs.severity(r.total)}", style = MaterialTheme.typography.titleMedium)
                HintText("Körperlich ${r.somatic}/16 · Psychisch ${r.psychological}/16 · Urogenital ${r.urogenital}/12")
                HintText("Der Wert dient dem Verlauf und ersetzt keine Diagnose.")
            }
        }
        Button(enabled = complete, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            vm.run { saveMrs(data.today, answers.map { it ?: 0 }) }
            nav.popBackStack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
        if (data.mrs.size >= 2) SectionCard(title = "Verlauf", icon = Icons.Rounded.Insights) {
            val l = data.mrs.sortedBy { it.date }.takeLast(12)
            LineChart(l.map { it.total.toDouble() }, l.map { it.date.short() }, 0.0, 44.0, MaterialTheme.colorScheme.primary)
            l.reversed().take(6).forEach { HintText("${it.date.short()}: ${it.total} (körperl. ${it.somatic}, psych. ${it.psychological}, urogen. ${it.urogenital})") }
        }
    }
}

@Composable
fun WeeklyReviewScreen(data: AppData, nav: NavController) {
    val pc = LocalPhaseColors.current
    val end = if (data.today.dayOfWeek == java.time.DayOfWeek.MONDAY) data.today.minusDays(1) else data.today
    val r = WeeklyReviewBuilder.build(data.summary, data.occurrences, data.episodes, data.events, end)
    Screen(title = "Wochenrückblick", nav = nav) {
        HintText("${r.from.dm()} – ${r.to.dm()}")
        SectionCard(container = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber("${r.trackedDays}", "Tage mit\nEinträgen")
                BigNumber("${r.unrestCount}", "Unruhe-\nEpisoden")
                BigNumber(r.meanSleep?.let { de1(it) } ?: "–", "Schlaf\n(Ø 1–5)")
                if (r.hotFlashes > 0) BigNumber("${r.hotFlashes}", "Hitze-\nwallungen")
            }
        }
        SectionCard(title = "Beobachtungen", icon = Icons.Rounded.AutoStories) {
            r.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (r.nextWeek.isNotEmpty()) SectionCard(title = "Nächste Woche", icon = Icons.Rounded.Event) {
            r.nextWeek.forEach { (e, info) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    info?.let { Icon(it.phase.icon(), null, tint = pc.of(it.phase), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                    Column {
                        Text(e.displayName, style = MaterialTheme.typography.titleSmall)
                        HintText(e.date.weekdayLong() + (info?.let { " · voraussichtlich ${it.phase.label}" + if ((it.daysBeforeNext ?: 99) in 1..7) ", vor der Periode" else "" } ?: ""))
                    }
                }
            }
            TextButton(onClick = { nav.navigate(Routes.EVENTS) }) { Text("Ereignisse") }
        }
        TextButton(onClick = { nav.navigate(Routes.ANALYSIS) }) { Text("Zur ausführlichen Auswertung") }
    }
}
