package de.fembiond.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.AnalysisInput
import de.fembiond.app.domain.Analytics
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.MedicationPhase
import de.fembiond.app.ui.BigNumber
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HeatmapView
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.LineChart
import de.fembiond.app.ui.PhaseLegend
import de.fembiond.app.ui.RatioBar
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.dm
import de.fembiond.app.ui.fmt1
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.short
import de.fembiond.app.ui.theme.LocalPhaseColors
import java.time.LocalDate
import kotlin.math.roundToInt

/** Zeitraum-Auswahl, gemeinsam für Auswertung und Export. */
sealed class RangeOption(val label: String) {
    data object LastCycle : RangeOption("Letzter Zyklus")
    data class Months(val n: Int) : RangeOption("$n Monate")
    data object All : RangeOption("Alles")
    data class Medication(val phase: MedicationPhase) : RangeOption(phase.name)

    fun range(data: AppData): Pair<LocalDate, LocalDate> {
        val today = data.today
        val earliest = listOfNotNull(
            data.periods.minOfOrNull { it.start }, data.occurrences.minOfOrNull { it.date }, data.panics.minOfOrNull { it.date },
        ).minOrNull() ?: today
        return when (this) {
            LastCycle -> {
                val c = data.summary.cycles
                val last = c.lastOrNull { it.isComplete } ?: c.lastOrNull()
                (last?.start ?: today.minusDays(28)) to (last?.nextStart?.minusDays(1) ?: today)
            }
            is Months -> today.minusMonths(n.toLong()) to today
            All -> earliest to today
            is Medication -> phase.start to (phase.end ?: today)
        }
    }

    companion object {
        fun options(data: AppData): List<RangeOption> =
            listOf(LastCycle, Months(3), Months(6), Months(12), All) + data.medications.map { Medication(it) }
    }
}

@Composable
fun RangeAndFilter(
    data: AppData,
    range: RangeOption,
    onRange: (RangeOption) -> Unit,
    categories: Set<Category>,
    onCategories: (Set<Category>) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RangeOption.options(data).forEach { o ->
            FilterChip(range == o, { onRange(o) }, { Text(o.label) },
                leadingIcon = if (o is RangeOption.Medication) ({ Icon(Icons.Rounded.Medication, null, Modifier.size(16.dp)) }) else null)
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val daily = Category.entries.filter { it.isDaily && it != Category.BLEEDING }
        FilterChip(categories.containsAll(daily), { onCategories(Category.entries.toSet()) }, { Text("Alle") })
        daily.forEach { c ->
            val on = c in categories && !categories.containsAll(daily)
            FilterChip(on, {
                onCategories(if (categories.containsAll(daily)) setOf(c, Category.PANIC_SYMPTOM) else if (on) (categories - c).ifEmpty { Category.entries.toSet() } else categories + c)
            }, { Text(c.shortLabel) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
fun AnalysisScreen(data: AppData, nav: NavController) {
    var range by remember { mutableStateOf<RangeOption>(RangeOption.Months(6)) }
    var categories by remember { mutableStateOf(Category.entries.toSet()) }
    val (from, to) = range.range(data)
    val result = remember(data, from, to, categories) {
        Analytics.analyze(AnalysisInput(data.summary, data.occurrences, data.panics, data.medications, data.gad, from, to, categories))
    }
    val pc = LocalPhaseColors.current

    Screen(title = "Auswertung", nav = null) {
        RangeAndFilter(data, range, { range = it }, categories, { categories = it })
        HintText("Zeitraum ${from.short()} – ${to.short()} · ${result.trackedDays} Tage mit Einträgen")

        if (result.insights.isNotEmpty()) SectionCard(title = "Erkenntnisse", icon = Icons.Rounded.AutoAwesome, container = MaterialTheme.colorScheme.primaryContainer) {
            result.insights.forEach { Text("• $it", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }

        // Zyklen
        SectionCard(title = "Zykluslänge & Abweichungen", icon = Icons.Rounded.Timeline) {
            val cs = result.cycles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(cs.median?.roundToInt()?.toString() ?: "–", "Median\nTage")
                BigNumber(cs.stdDev?.let { "±" + it.fmt1() } ?: "–", "Schwankung")
                BigNumber(if (cs.min != null) "${cs.min}–${cs.max}" else "–", "Spanne")
            }
            val maxLen = (cs.bars.mapNotNull { it.length }.maxOrNull() ?: 35).coerceAtLeast(35)
            cs.bars.takeLast(12).reversed().forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.start.dm(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                    Box(Modifier.weight(1f).height(16.dp)) {
                        val len = b.length ?: (data.today.toEpochDay() - b.start.toEpochDay() + 1).toInt()
                        Row(Modifier.fillMaxWidth(len.toFloat() / maxLen).height(16.dp).clip(RoundedCornerShape(8.dp))) {
                            Box(Modifier.weight(b.periodLength.toFloat().coerceAtMost(len.toFloat())).height(16.dp).background(pc.menstruation))
                            Box(Modifier.weight((len - b.periodLength).coerceAtLeast(1).toFloat()).height(16.dp)
                                .background(if (b.length == null) pc.luteal.copy(alpha = 0.35f) else pc.luteal.copy(alpha = 0.7f)))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(b.length?.toString() ?: "läuft", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                    Box(Modifier.width(40.dp)) {
                        Row {
                            if (b.deviation != null) Icon(Icons.Rounded.Warning, b.deviation.label, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            if (b.medicated) Icon(Icons.Rounded.Medication, "unter Medikation", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (cs.bars.isEmpty()) HintText("Noch keine Zyklen im Zeitraum.")
            HintText("Warnsymbol = deutliche Abweichung · Kapsel = unter Medikation. Zyklen mit Medikation fließen nicht in die Grundlinie ein.")
            HintText("Einordnung Perimenopause (STRAW+10): ${data.summary.straw.label}. ${data.summary.straw.description}")
        }

        if (result.heatmap.rows.isNotEmpty()) SectionCard(title = "Wann tritt was auf?", icon = Icons.Rounded.GridOn) {
            HintText("Anteil der erfassten Tage je Zyklustag. Tippe ein Feld an für Details.")
            HeatmapView(result.heatmap)
        }

        val drsp = result.drsp.filter { it.cycles >= 1 }.take(8)
        if (drsp.isNotEmpty()) SectionCard(title = "Vor der Periode vs. danach", icon = Icons.Rounded.CompareArrows) {
            HintText("Mittlere Stärke (0–5) in den 7 Tagen vor der Periode verglichen mit Tag 4–10 – Methode nach DRSP. Hervorgehoben: mindestens 30 % stärker bei 2+ Zyklen.")
            drsp.forEach { d ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.term, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                            color = if (d.significant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(d.changePercent?.let { (if (it >= 0) "+" else "") + it.roundToInt() + " %" } ?: "nur prämenstruell", style = MaterialTheme.typography.labelLarge)
                    }
                    RatioBar("vorher", d.premenstrual / 5, d.premenstrual.fmt1(), pc.luteal)
                    RatioBar("danach", d.postmenstrual / 5, d.postmenstrual.fmt1(), pc.follicular)
                }
            }
            HintText("Ausgewertete Zyklen: ${drsp.first().cycles}")
        }

        SectionCard(title = "Panikattacken nach Phase", icon = Icons.Rounded.MonitorHeart) {
            if (result.panicTotal == 0) HintText("Im Zeitraum keine Panikattacken erfasst.") else {
                val max = result.panicByPhase.maxOf { it.count }.coerceAtLeast(1)
                result.panicByPhase.forEach { p ->
                    RatioBar(
                        p.phase.label, p.count.toDouble() / max,
                        "${p.count}" + (p.meanIntensity?.let { " · Ø ${it.fmt1()}" } ?: "") + (p.relativeFrequency?.let { " · ${it.fmt1()}×" } ?: ""),
                        pc.of(p.phase), p.phase.icon(),
                    )
                }
                HintText("Anzahl · mittlere Stärke · relative Häufigkeit (1× = Durchschnitt, berücksichtigt die Länge der Phasen).")
                PhaseLegend()
            }
        }

        if (result.coOccurrence.isNotEmpty() || result.nextDay.isNotEmpty() || result.events.isNotEmpty()) {
            SectionCard(title = "Zusammenhänge", icon = Icons.Rounded.Hub) {
                if (result.events.isNotEmpty()) {
                    Text("Lebensumstände", style = MaterialTheme.typography.titleSmall)
                    result.events.take(5).forEach {
                        RatioBar("${it.event} → ${it.target}", it.rateWith, "${(it.rateWith * 100).roundToInt()} % (sonst ${(it.rateWithout * 100).roundToInt()} %)", MaterialTheme.colorScheme.tertiary)
                    }
                    HorizontalDivider()
                }
                if (result.coOccurrence.isNotEmpty()) {
                    Text("Treten oft gemeinsam auf", style = MaterialTheme.typography.titleSmall)
                    result.coOccurrence.take(5).forEach { HintText("${it.a} + ${it.b}: ${it.days.roundToInt()} Tage (${it.lift.fmt1()}× häufiger als zufällig)") }
                }
                if (result.nextDay.isNotEmpty()) {
                    Text("Am Folgetag", style = MaterialTheme.typography.titleSmall)
                    result.nextDay.take(5).forEach { HintText("${it.a} → ${it.b} am nächsten Tag: ${it.days.roundToInt()}× (${it.lift.fmt1()}× häufiger)") }
                }
                HintText("Zusammenhänge sind Hinweise, keine Beweise für Ursachen.")
            }
        }

        if (result.compare.size >= 2) SectionCard(title = "Vorher / Nachher Medikation", icon = Icons.Rounded.Medication) {
            result.compare.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label, style = MaterialTheme.typography.titleSmall)
                    HintText(listOfNotNull(
                        "${c.trackedDays} Tage",
                        c.panicPer30?.let { "Panik ${it.fmt1()}/30 Tage" },
                        c.meanSleep?.let { "Schlaf Ø ${it.fmt1()}" },
                        c.meanEnergy?.let { "Energie Ø ${it.fmt1()}" },
                        c.medianCycle?.let { "Zyklus ${it.roundToInt()} Tage" },
                    ).joinToString(" · "))
                    if (c.topTerms.isNotEmpty()) HintText("Häufig: " + c.topTerms.take(4).joinToString { "${it.first} ${(it.second * 100).roundToInt()} %" })
                }
            }
            HintText("Für belastbare Vergleiche braucht jede Phase mehrere Zyklen.")
        }

        if (result.trends.size >= 2) SectionCard(title = "Verläufe pro Monat", icon = Icons.Rounded.ShowChart) {
            val labels = result.trends.map { it.month.monthValue.toString().padStart(2, '0') + "/" + (it.month.year % 100) }
            if (result.trends.any { it.meanSleep != null }) {
                Text("Schlafqualität (1–5)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.meanSleep }, labels, 1.0, 5.0, MaterialTheme.colorScheme.secondary)
            }
            if (result.trends.any { it.meanEnergy != null }) {
                Text("Energie (1–5)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.meanEnergy }, labels, 1.0, 5.0, MaterialTheme.colorScheme.tertiary)
            }
            Text("Panikattacken", style = MaterialTheme.typography.labelLarge)
            LineChart(result.trends.map { it.panicCount.toDouble() }, labels, 0.0, (result.trends.maxOf { it.panicCount }.coerceAtLeast(4)).toDouble(), MaterialTheme.colorScheme.error)
            if (result.trends.any { it.gad != null }) {
                Text("GAD-7 (0–21)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.gad }, labels, 0.0, 21.0, MaterialTheme.colorScheme.primary)
            }
        }
    }
}
