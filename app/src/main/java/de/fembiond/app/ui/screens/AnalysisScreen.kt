package de.fembiond.app.ui.screens

import androidx.compose.material.icons.rounded.HelpOutline
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
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import de.fembiond.app.ui.unrestColor
import de.fembiond.app.ui.YearHeatmap
import de.fembiond.app.ui.CycleOverlayChart
import de.fembiond.app.ui.overlaySeries
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import de.fembiond.app.domain.UnrestLevel
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
            data.periods.minOfOrNull { it.start }, data.occurrences.minOfOrNull { it.date }, data.episodes.minOfOrNull { it.date },
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
        val daily = FILTER_CATEGORIES
        FilterChip(categories.containsAll(daily), { onCategories(Category.entries.toSet()) }, { Text("Alle") })
        daily.forEach { c ->
            val on = c in categories && !categories.containsAll(daily)
            FilterChip(on, {
                onCategories(if (categories.containsAll(daily)) setOf(c) else if (on) (categories - c).ifEmpty { Category.entries.toSet() } else categories + c)
            }, { Text(c.shortLabel) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp)) })
        }
    }
    if (!categories.containsAll(FILTER_CATEGORIES)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Filter: " + FILTER_CATEGORIES.filter { it in categories }.joinToString { it.shortLabel } + " – es werden nur passende Auswertungen gezeigt.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = { onCategories(Category.entries.toSet()) }) { Text("Alle zeigen") }
        }
    }
}

val FILTER_CATEGORIES = Category.entries.filter { it.isDaily && it != Category.BLEEDING }

@Composable
fun AnticipationChart(points: List<de.fembiond.app.domain.AnticipationPoint>) {
    val maxH = 96.dp
    Row(Modifier.fillMaxWidth().height(maxH + 36.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        points.forEach { p ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val v = (p.meanMaxLevel ?: 0.0)
                Text(if (p.meanMaxLevel == null) "–" else v.fmt1(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth().height(maxH * (v / 5.0).toFloat().coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (p.offset == 0) MaterialTheme.colorScheme.primary else unrestColor(v.roundToInt().coerceIn(1, 5)).copy(alpha = 0.75f))
                )
                Text(if (p.offset == 0) "Tag" else "%+d".format(p.offset), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun LegendSwatch(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 16.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** PMS-Einschätzung nach ACOG-Kriterien, mit Zyklus-Übersicht und typischen Beschwerden. */
@Composable
fun PmsCard(pms: de.fembiond.app.domain.PmsResult) {
    val pc = LocalPhaseColors.current
    var showCycles by remember { mutableStateOf(false) }
    SectionCard(title = "PMS – prämenstruelle Beschwerden", icon = Icons.Rounded.Psychology) {
        HintText("Geprüft wird, ob in den 5 Tagen vor der Periode Beschwerden der Stimmung und des Körpers auftreten, die nach der Periode (Tag 4–10) wieder abklingen. Für PMS gilt das als typisch, wenn es sich in 3 Zyklen hintereinander zeigt.")
        Text(pms.verdict, style = MaterialTheme.typography.bodyMedium,
            color = if (pms.typical > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        pms.pmddHint?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${pms.assessed} von ${pms.cycles.size} Zyklen auswertbar · ${pms.typical}× PMS-typisch",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { showCycles = !showCycles }) { Text(if (showCycles) "ausblenden" else "Details") }
        }
        androidx.compose.animation.AnimatedVisibility(showCycles) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pms.cycles.sortedByDescending { it.periodStart }.forEach { c ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            when { !c.assessable -> Icons.Rounded.RemoveCircleOutline; c.pmsTypical -> Icons.Rounded.CheckCircle; else -> Icons.Rounded.RadioButtonUnchecked }, null,
                            tint = if (c.pmsTypical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp).size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Periode ab ${c.periodStart.short()}" + if (c.medicated) " (Zyklus davor unter Medikation)" else "", style = MaterialTheme.typography.bodyMedium)
                            if (!c.assessable) HintText(c.reason ?: "")
                            else HintText(listOfNotNull(
                                "Stimmung: " + (c.affective.joinToString().ifEmpty { "–" }),
                                "Körper: " + (c.somatic.joinToString().ifEmpty { "–" }),
                                c.other.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Weitere: "),
                            ).joinToString(" · ") + if (c.pmsTypical) " → PMS-typisch" else "")
                        }
                    }
                }
            }
        }
        val top = pms.symptoms.filter { it.preRate > 0 }.take(6)
        if (top.isNotEmpty()) {
            HorizontalDivider()
            Text("Beschwerden vor vs. nach der Periode", style = MaterialTheme.typography.titleSmall)
            top.forEach { sym ->
                RatioBar("${sym.term} (${sym.group})", sym.preRate, "vorher ${de.fembiond.app.domain.Pms.pct(sym.preRate)}", pc.luteal)
                HintText("danach ${de.fembiond.app.domain.Pms.pct(sym.postRate)} der erfassten Tage · in ${sym.cycles} Zyklen vor der Periode")
            }
        }
        HintText("Keine Diagnose – die Einschätzung hilft, Beschwerden für ein ärztliches Gespräch zu dokumentieren.")
    }
}

/** Vergleich „vor der Periode“ / „danach“ mit Datenbasis und Einordnung in Alltagssprache. */
@Composable
fun DrspCard(pairs: List<de.fembiond.app.domain.DrspPair>, results: List<de.fembiond.app.domain.DrspResult>) {
    val pc = LocalPhaseColors.current
    var showBasis by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val counted = pairs.count { it.counted }
    SectionCard(title = "Vor der Periode vs. danach", icon = Icons.Rounded.CompareArrows) {
        Text("So funktioniert's", style = MaterialTheme.typography.titleSmall)
        HintText("Für jede Periode werden zwei Wochen verglichen: die 7 Tage davor und Tag 4–10 danach. Pro Tag zählt die Stärke (0–5) – bei Unruhe die höchste Stufe des Tages. Ein Tag mit Einträgen, an dem etwas nicht auftrat, zählt als 0. Tage ohne Einträge zählen nicht.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Datenbasis: $counted von ${pairs.size} Perioden auswertbar",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                color = if (counted >= 2) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            androidx.compose.material3.TextButton(onClick = { showBasis = !showBasis }) { Text(if (showBasis) "ausblenden" else "Details") }
        }
        if (counted < 2) HintText("Eine Periode zählt, wenn in der Woche davor und an Tag 4–10 danach jeweils an mindestens ${de.fembiond.app.domain.Analytics.DRSP_MIN_DAYS} Tagen etwas eingetragen wurde. Für ein verlässliches Muster braucht es mindestens 2 solcher Perioden.")
        androidx.compose.animation.AnimatedVisibility(showBasis) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pairs.isEmpty()) HintText("Im Zeitraum liegt noch kein Periodenbeginn mit Vorgeschichte.")
                pairs.sortedByDescending { it.periodStart }.forEach { p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            if (p.counted) Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircleOutline, null,
                            tint = if (p.counted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp).size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Periode ab ${p.periodStart.short()}", style = MaterialTheme.typography.bodyMedium)
                            HintText("Erfasste Tage: davor ${p.preDays}/7 · danach ${p.postDays}/7" + (p.reason?.let { " – $it" } ?: " – ausgewertet"))
                        }
                    }
                }
            }
        }
        if (results.isEmpty()) {
            HintText("Noch keine Werte – sobald eine Periode auswertbar ist, erscheinen hier die Vergleiche.")
        } else {
            HorizontalDivider()
            val shown = if (showAll) results else results.take(5)
            shown.forEachIndexed { idx, d ->
                if (idx > 0) Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.term, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                            color = if (d.significant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(d.changePercent?.let { val r = it.roundToInt(); (if (r > 0) "+" else if (r == 0) "±" else "") + r + " %" } ?: "nur vorher", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(d.verdict, style = MaterialTheme.typography.bodySmall,
                        color = if (d.significant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    RatioBar("7 Tage davor", d.premenstrual / 5, "Ø ${d.premenstrual.fmt1()}", pc.luteal)
                    HintText("an ${d.preHits} von ${d.preDays} erfassten Tagen aufgetreten")
                    RatioBar("Tag 4–10 danach", d.postmenstrual / 5, "Ø ${d.postmenstrual.fmt1()}", pc.follicular)
                    HintText("an ${d.postHits} von ${d.postDays} erfassten Tagen aufgetreten")
                }
            }
            if (results.size > 5) androidx.compose.material3.TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Weniger anzeigen" else "Alle ${results.size} Begriffe anzeigen")
            }
            HintText("Prozent = Unterschied „davor“ gegenüber „danach“. Ein deutliches Muster (farbig) gilt ab +30 % über mindestens 2 Perioden.")
        }
    }
}

/** Zyklen übereinandergelegt – nur Kennzahlen, zu denen es Einträge gibt. */
@Composable
fun OverlayCard(data: AppData, categories: Set<Category>) {
    val unrest = data.episodes.groupBy { it.date }.mapValues { (_, l) -> l.maxOf { it.level }.toDouble() }
    val tracked = (data.entries.map { it.time.toLocalDate() } + data.episodes.map { it.date }).toSet()
    fun scale(term: String): (java.time.LocalDate) -> Double? {
        val m = data.occurrences.filter { it.term == term }.groupBy { it.date }.mapValues { (_, l) -> l.mapNotNull { it.intensity }.average() }
        return { d -> m[d]?.takeIf { !it.isNaN() } }
    }
    val peri = data.occurrences.filter { it.category == Category.PERIMENOPAUSE }.groupingBy { it.date }.eachCount()
    val sleepTerm = de.fembiond.app.domain.ScaleTerms.SLEEP_QUALITY
    val energyTerm = de.fembiond.app.domain.ScaleTerms.ENERGY
    val metrics = buildList<Triple<String, (java.time.LocalDate) -> Double?, Double>> {
        if (Category.MIND in categories && unrest.isNotEmpty()) add(Triple("Unruhe", { d -> unrest[d] ?: if (d in tracked) 0.0 else null }, 5.0))
        if (Category.SLEEP_ENERGY in categories && data.occurrences.any { it.term == sleepTerm }) add(Triple("Schlaf", scale(sleepTerm), 5.0))
        if (Category.SLEEP_ENERGY in categories && data.occurrences.any { it.term == energyTerm }) add(Triple("Energie", scale(energyTerm), 5.0))
        if (Category.PERIMENOPAUSE in categories && peri.isNotEmpty()) add(Triple("Perimenopause", { d -> peri[d]?.toDouble() ?: if (d in tracked) 0.0 else null }, (peri.values.maxOrNull() ?: 3).coerceAtLeast(3).toDouble()))
    }
    if (metrics.isEmpty()) return
    var metric by remember { mutableStateOf(0) }
    val m = metrics[metric.coerceIn(0, metrics.size - 1)]
    val (series, mean) = remember(data, m.first) { overlaySeries(data, 6, m.second) }
    if (series.size < 2) return
    SectionCard(title = "Zyklen übereinander", icon = Icons.Rounded.Layers) {
        HintText("Die letzten ${series.size} Zyklen nach Zyklustag übereinandergelegt. Die kräftige Linie ist der Mittelwert, neuere Zyklen sind dunkler. Hinterlegt: die letzte Woche vor der Periode.")
        if (metrics.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEachIndexed { i, x -> FilterChip(metric == i, { metric = i }, { Text(x.first) }) }
        } else Text(m.first, style = MaterialTheme.typography.labelLarge)
        CycleOverlayChart(series, mean, m.third, when (m.first) {
            "Unruhe" -> unrestColor(4); "Perimenopause" -> LocalPhaseColors.current.follicular; else -> MaterialTheme.colorScheme.secondary
        })
    }
}

/**
 * Perimenopause nach aktueller Studienlage: alle erfassten typischen Beschwerden (Schlaf, Stimmung, Konzentration,
 * Erschöpfung, Schmerzen, Herzklopfen, Blutung, Hitze …) – nur solche mit Einträgen –, dazu STRAW und MRS.
 */
@Composable
fun PerimenopauseCard(data: AppData, from: java.time.LocalDate, to: java.time.LocalDate, trackedDays: Int) {
    val stats = de.fembiond.app.domain.PeriStats.symptomStats(data.occurrences, from, to)
    val mrs = data.mrs.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
    val straw = data.summary.straw
    val strawRelevant = straw == de.fembiond.app.domain.StrawStage.EARLY || straw == de.fembiond.app.domain.StrawStage.LATE
    if (stats.isEmpty() && mrs.isEmpty() && !strawRelevant) return
    val pc = LocalPhaseColors.current
    var showAll by remember { mutableStateOf(false) }
    SectionCard(title = "Perimenopause", icon = Icons.Rounded.LocalFireDepartment) {
        if (strawRelevant || data.summary.naturalCompleteCount >= 4) {
            Text("Zyklus (STRAW+10): ${straw.label}", style = MaterialTheme.typography.titleSmall)
            HintText(straw.description + (data.age?.let { " Alter: $it Jahre." } ?: ""))
        }
        if (stats.isNotEmpty()) {
            HorizontalDivider()
            Text("Erfasste typische Beschwerden", style = MaterialTheme.typography.titleSmall)
            HintText("Nach aktueller Forschung (u. a. SWAN-Studie) gehören neben Hitzewallungen vor allem Schlafprobleme, Reizbarkeit, neue Ängste, Konzentrationsprobleme, Erschöpfung, Gelenkschmerzen und Herzklopfen dazu. Gezeigt wird nur, was du erfasst hast – Anteil der Tage mit Einträgen.")
            val shown = if (showAll) stats else stats.take(6)
            shown.forEach { st ->
                val byPhase = data.occurrences.filter { it.term == st.term && !it.date.isBefore(from) && !it.date.isAfter(to) }
                    .mapNotNull { de.fembiond.app.domain.CycleEngine.dayInfo(data.summary, it.date)?.phase }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }
                RatioBar("${st.term} · ${st.group}", if (trackedDays > 0) st.days.toDouble() / trackedDays else 0.0, if (st.days == 1) "1 Tag" else "${st.days} Tage", pc.follicular)
                byPhase?.takeIf { st.days >= 3 }?.let { HintText("am häufigsten in der ${it.key.label}") }
            }
            if (stats.size > 6) androidx.compose.material3.TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Weniger anzeigen" else "Alle ${stats.size} Beschwerden anzeigen")
            }
            val months = generateSequence(java.time.YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { !it.isAfter(java.time.YearMonth.from(to)) }.toList()
            val periDays = data.occurrences.filter { it.term in de.fembiond.app.domain.PeriStats.SYMPTOMS && !it.date.isBefore(from) && !it.date.isAfter(to) }
                .map { it.date }.toSet()
            if (months.size >= 2 && periDays.size >= 3) {
                Text("Tage mit Beschwerden pro Monat", style = MaterialTheme.typography.labelLarge)
                LineChart(months.map { m -> periDays.count { java.time.YearMonth.from(it) == m }.toDouble() },
                    months.map { "%02d/%02d".format(it.monthValue, it.year % 100) }, 0.0,
                    months.maxOf { m -> periDays.count { java.time.YearMonth.from(it) == m } }.coerceAtLeast(4).toDouble(), pc.follicular)
            }
        }
        if (mrs.isNotEmpty()) {
            HorizontalDivider()
            val last = mrs.last()
            Text("Menopause Rating Scale (MRS)", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber("${last.total}", "Gesamt\n(0–44)")
                BigNumber("${last.somatic}", "körperlich\n(0–16)")
                BigNumber("${last.psychological}", "psychisch\n(0–16)")
                BigNumber("${last.urogenital}", "urogenital\n(0–12)")
            }
            HintText("${last.date.short()}: ${de.fembiond.app.domain.Mrs.severity(last.total)}")
            if (mrs.size >= 2) LineChart(mrs.takeLast(12).map { it.total.toDouble() }, mrs.takeLast(12).map { it.date.short() }, 0.0, 44.0, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AnalysisScreen(data: AppData, nav: NavController) {
    var range by remember { mutableStateOf<RangeOption>(RangeOption.Months(6)) }
    var categories by remember { mutableStateOf(Category.entries.toSet()) }
    val (from, to) = range.range(data)
    val result = remember(data, from, to, categories) {
        Analytics.analyze(AnalysisInput(data.summary, data.occurrences, data.episodes, data.medications, data.gad, from, to, categories, events = data.events))
    }
    val pc = LocalPhaseColors.current
    val all = categories.containsAll(FILTER_CATEGORIES)
    val showMind = Category.MIND in categories
    val showPeri = Category.PERIMENOPAUSE in categories
    val showPms = Category.MIND in categories || Category.BODY in categories
    val pms = remember(data, from, to) { de.fembiond.app.domain.Pms.analyze(data.summary, data.occurrences, data.episodes, data.today, from, to) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var tabKey by rememberSaveable { mutableStateOf("overview") }
    val hasYear = showMind && data.episodes.any { !it.date.isBefore(from) && !it.date.isAfter(to) }
    val hasTrends = result.trends.size >= 2 && (
        (showMind && result.trends.any { it.unrestCount > 0 || it.gad != null }) ||
            (Category.SLEEP_ENERGY in categories && result.trends.any { it.meanSleep != null || it.meanEnergy != null }))
    val periStats = de.fembiond.app.domain.PeriStats.symptomStats(data.occurrences, from, to)
    val tabs = buildList {
        add("overview" to "Überblick")
        if ((all && result.cycles.bars.isNotEmpty()) || result.heatmap.rows.isNotEmpty() || (all && result.compare.size >= 2)) add("cycle" to "Zyklus")
        if (showMind && (result.unrestTotal > 0 || result.anticipation.any { it.events > 0 } || result.coOccurrence.isNotEmpty() || result.events.isNotEmpty())) add("unrest" to "Unruhe")
        if (result.drsp.isNotEmpty() || (showPms && pms.symptoms.any { it.preRate > 0 || it.postRate > 0 })) add("pms" to "PMS")
        if (showPeri && (periStats.isNotEmpty() || data.mrs.isNotEmpty() || data.summary.straw == de.fembiond.app.domain.StrawStage.EARLY || data.summary.straw == de.fembiond.app.domain.StrawStage.LATE)) add("peri" to "Perimenopause")
    }
    val tab = tabs.firstOrNull { it.first == tabKey }?.first ?: "overview"

    Screen(title = "Auswertung", jumpBar = true, scrollState = scrollState,
        belowTopBar = {
            if (tabs.size > 1) androidx.compose.material3.PrimaryScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                tabs.forEach { (key, label) ->
                    androidx.compose.material3.Tab(
                        selected = key == tab,
                        onClick = { tabKey = key; scope.launch { scrollState.scrollTo(0) } },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }
        },
        nav = null, actions = { androidx.compose.material3.IconButton(onClick = { nav.navigate(de.fembiond.app.Routes.guide("analysis")) }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.HelpOutline, "Hilfe") } }) {
        RangeAndFilter(data, range, { range = it }, categories, { categories = it })
        HintText("Zeitraum ${from.short()} – ${to.short()} · ${result.trackedDays} Tage mit Einträgen")

        if (tab == "overview" && result.insights.isEmpty() && !hasYear && !hasTrends) {
            EmptyState(
                Icons.Rounded.AutoAwesome, "Noch nicht genug Daten",
                if (tabs.size > 1) "Für den Überblick braucht es noch etwas mehr Einträge. Schau in die anderen Reiter – dort erscheint bereits, wozu es Daten gibt."
                else "Im gewählten Zeitraum gibt es noch kaum Einträge. Sobald etwas erfasst ist, erscheinen hier nur die Auswertungen, zu denen es Daten gibt.",
            )
        }
        if (tab == "overview" && result.insights.isNotEmpty()) SectionCard(title = "Erkenntnisse", icon = Icons.Rounded.AutoAwesome, container = MaterialTheme.colorScheme.primaryContainer) {
            result.insights.forEach { Text("• $it", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }

        if (tab == "overview" && hasYear) SectionCard(title = "Jahresübersicht", icon = Icons.Rounded.CalendarViewMonth) {
            HintText("Ein Kästchen pro Tag der letzten 12 Monate, gefärbt nach der höchsten Unruhe-Stufe. Periodentage sind umrandet. Feld antippen für Details.")
            YearHeatmap(data, dayMarks(data).period) { nav.navigate(de.fembiond.app.Routes.day(it)) }
        }

        // Zyklen
        if (tab == "cycle" && all && result.cycles.bars.isNotEmpty()) SectionCard(title = "Zykluslänge & Abweichungen", icon = Icons.Rounded.Timeline) {
            val cs = result.cycles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(cs.median?.roundToInt()?.toString() ?: "–", "Tage (Median)")
                BigNumber(cs.stdDev?.let { "±" + it.fmt1() } ?: "–", "Schwankung")
                BigNumber(if (cs.min != null) "${cs.min}–${cs.max}" else "–", "Spanne")
            }
            val maxLen = (cs.bars.mapNotNull { it.length }.maxOrNull() ?: 35).coerceAtLeast(35)
            cs.bars.takeLast(12).reversed().forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.start.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.")), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, softWrap = false, modifier = Modifier.width(52.dp))
                    Box(Modifier.weight(1f).height(16.dp)) {
                        val len = b.length ?: (data.today.toEpochDay() - b.start.toEpochDay() + 1).toInt()
                        Row(Modifier.fillMaxWidth(len.toFloat() / maxLen).height(16.dp).clip(RoundedCornerShape(8.dp))) {
                            Box(Modifier.weight(b.periodLength.toFloat().coerceAtMost(len.toFloat())).height(16.dp).background(if (b.periodEstimated) pc.menstruation.copy(alpha = 0.45f) else pc.menstruation))
                            Box(Modifier.weight((len - b.periodLength).coerceAtLeast(1).toFloat()).height(16.dp)
                                .background(if (b.length == null) pc.luteal.copy(alpha = 0.35f) else pc.luteal.copy(alpha = 0.7f)))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(b.length?.toString() ?: "…", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, modifier = Modifier.width(32.dp))
                    Box(Modifier.width(36.dp)) {
                        Row {
                            if (b.deviation != null) Icon(Icons.Rounded.Warning, b.deviation.label, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            if (b.medicated) Icon(Icons.Rounded.Medication, "unter Medikation", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (cs.bars.isEmpty()) HintText("Noch keine Zyklen im Zeitraum.")
            ChipFlow {
                LegendSwatch(pc.menstruation, "Periode")
                LegendSwatch(pc.menstruation.copy(alpha = 0.45f), "Periode geschätzt (kein Ende eingetragen)")
                LegendSwatch(pc.luteal.copy(alpha = 0.7f), "übriger Zyklus")
            }
            HintText("Zahl = Zykluslänge in Tagen (… = laufender Zyklus) · Warnsymbol = deutliche Abweichung · Kapsel = unter Medikation (nicht Teil der Grundlinie).")
            HintText("Perimenopause (STRAW+10): ${data.summary.straw.label}")
        }

        if (tab == "cycle") OverlayCard(data, categories)

        if (tab == "cycle" && result.heatmap.rows.isNotEmpty()) SectionCard(title = "Wann tritt was auf?", icon = Icons.Rounded.GridOn) {
            HintText("Anteil der erfassten Tage je Zyklustag. Tippe ein Feld an für Details.")
            HeatmapView(result.heatmap)
        }

        if (tab == "pms" && result.drsp.isNotEmpty()) DrspCard(result.drspPairs, result.drsp)

        if (tab == "pms" && showPms && pms.symptoms.any { it.preRate > 0 || it.postRate > 0 }) PmsCard(pms)
        if (tab == "peri" && showPeri) PerimenopauseCard(data, from, to, result.trackedDays)

        if (tab == "unrest" && showMind && result.unrestTotal > 0) SectionCard(title = "Unruhe nach Zyklusphase", icon = Icons.Rounded.Waves) {
            if (result.unrestTotal == 0) HintText("Im Zeitraum keine Unruhe-Episoden erfasst.") else {
                Text("Stufen-Verteilung (${result.unrestTotal} Episoden)", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))) {
                    result.levelCounts.forEachIndexed { i, n ->
                        if (n > 0) Box(Modifier.weight(n.toFloat()).height(22.dp).background(unrestColor(i + 1)), contentAlignment = Alignment.Center) {
                            Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface, maxLines = 1)
                        }
                    }
                }
                ChipFlow {
                    UnrestLevel.entries.forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(unrestColor(u.level)))
                            Spacer(Modifier.width(4.dp))
                            Text("${u.level} ${u.label}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider()
                val max = result.unrestByPhase.maxOf { it.count }.coerceAtLeast(1)
                result.unrestByPhase.forEach { p ->
                    RatioBar(
                        p.phase.label, p.count.toDouble() / max,
                        listOfNotNull("${p.count}", p.meanLevel?.let { "Ø ${it.fmt1()}" }, p.relativeFrequency?.let { "${it.fmt1()}×" }).joinToString(" · "),
                        pc.of(p.phase), p.phase.icon(),
                    )
                    if (p.panicCount > 0) HintText("davon ${p.panicCount}× Panikattacke")
                }
                HintText("Anzahl · mittlere Stufe · relative Häufigkeit (1× = Durchschnitt, berücksichtigt die Länge der Phasen).")
            }
        }

        if (tab == "unrest" && showMind && result.anticipation.any { it.events > 0 }) SectionCard(title = "Unruhe rund um Ereignisse", icon = Icons.Rounded.Event) {
            HintText("Mittlere höchste Unruhe-Stufe an den Tagen vor und nach Ereignissen (0 = Ereignistag).")
            AnticipationChart(result.anticipation)
            if (result.eventTypes.isNotEmpty()) {
                HorizontalDivider()
                Text("Nach Ereignisart", style = MaterialTheme.typography.titleSmall)
                result.eventTypes.take(8).forEach { t ->
                    RatioBar(
                        t.type, t.meanMaxLevel / 5,
                        "Ø ${t.meanMaxLevel.fmt1()} · ${t.events}×",
                        unrestColor(t.meanMaxLevel.roundToInt().coerceIn(1, 5)),
                    )
                    val extra = listOfNotNull(
                        "Unruhe vorher in ${(t.shareWithUnrest * 100).roundToInt()} %",
                        t.panics.takeIf { it > 0 }?.let { "$it× Panik" },
                        t.cancelled.takeIf { it > 0 }?.let { "$it× abgesagt oder früher gegangen" },
                    )
                    HintText(extra.joinToString(" · "))
                }
            }
            if (result.eventPhases.size >= 2) {
                HorizontalDivider()
                Text("Zeitpunkt im Zyklus", style = MaterialTheme.typography.titleSmall)
                result.eventPhases.forEach { p ->
                    RatioBar(p.label, p.meanMaxLevel / 5, "Ø ${p.meanMaxLevel.fmt1()} · ${p.events}×", MaterialTheme.colorScheme.tertiary)
                }
                HintText("Höchste Unruhe-Stufe in den 3 Tagen vor bis zum Ereignistag, gruppiert danach, wann im Zyklus das Ereignis lag.")
            }
        }

        if (tab == "unrest" && (result.coOccurrence.isNotEmpty() || result.nextDay.isNotEmpty() || result.events.isNotEmpty())) {
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

        if (tab == "cycle" && all && result.compare.size >= 2) SectionCard(title = "Vorher / Nachher Medikation", icon = Icons.Rounded.Medication) {
            result.compare.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label, style = MaterialTheme.typography.titleSmall)
                    HintText(listOfNotNull(
                        "${c.trackedDays} Tage",
                        c.unrestPer30?.let { "Unruhe ${it.fmt1()}/30 Tage" },
                        c.meanUnrestLevel?.let { "Ø Stufe ${it.fmt1()}" },
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

        val trendData = result.trends.size >= 2 && (
            (showMind && result.trends.any { it.unrestCount > 0 || it.gad != null }) ||
                (Category.SLEEP_ENERGY in categories && result.trends.any { it.meanSleep != null || it.meanEnergy != null }))
        if (tab == "overview" && trendData) SectionCard(title = "Verläufe pro Monat", icon = Icons.Rounded.ShowChart) {
            val labels = result.trends.map { it.month.monthValue.toString().padStart(2, '0') + "/" + (it.month.year % 100) }
            val showSleep = Category.SLEEP_ENERGY in categories
            if (showSleep && result.trends.any { it.meanSleep != null }) {
                Text("Schlafqualität (1–5)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.meanSleep }, labels, 1.0, 5.0, MaterialTheme.colorScheme.secondary)
            }
            if (showSleep && result.trends.any { it.meanEnergy != null }) {
                Text("Energie (1–5)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.meanEnergy }, labels, 1.0, 5.0, MaterialTheme.colorScheme.tertiary)
            }
            if (showMind && result.trends.any { it.unrestCount > 0 }) {
            Text("Unruhe-Episoden pro Monat", style = MaterialTheme.typography.labelLarge)
            LineChart(result.trends.map { it.unrestCount.toDouble() }, labels, 0.0, (result.trends.maxOf { it.unrestCount }.coerceAtLeast(4)).toDouble(), MaterialTheme.colorScheme.tertiary)
            }
            if (showMind && result.trends.any { it.meanUnrestLevel != null }) {
                Text("Mittlere Unruhe-Stufe (1–5)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.meanUnrestLevel }, labels, 1.0, 5.0, unrestColor(3))
            }
            if (showMind && result.trends.any { it.panicCount > 0 }) {
            Text("Panikattacken pro Monat", style = MaterialTheme.typography.labelLarge)
            LineChart(result.trends.map { it.panicCount.toDouble() }, labels, 0.0, (result.trends.maxOf { it.panicCount }.coerceAtLeast(4)).toDouble(), MaterialTheme.colorScheme.error)
            }
            if (showMind && result.trends.any { it.gad != null }) {
                Text("GAD-7 (0–21)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.gad }, labels, 0.0, 21.0, MaterialTheme.colorScheme.primary)
            }
        }
    }
}
