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

/** Zyklen übereinandergelegt – wiederkehrende Muster werden sichtbar. */
@Composable
fun OverlayCard(data: AppData) {
    var metric by remember { mutableStateOf(0) }
    val metrics = listOf("Unruhe", "Schlaf", "Energie", "Hitzewallungen")
    val unrest = data.episodes.groupBy { it.date }.mapValues { (_, l) -> l.maxOf { it.level }.toDouble() }
    val tracked = (data.entries.map { it.time.toLocalDate() } + data.episodes.map { it.date }).toSet()
    fun scale(term: String): (java.time.LocalDate) -> Double? {
        val m = data.occurrences.filter { it.term == term }.groupBy { it.date }.mapValues { (_, l) -> l.mapNotNull { it.intensity }.average() }
        return { d -> m[d]?.takeIf { !it.isNaN() } }
    }
    val hot = data.occurrences.filter { it.term in de.fembiond.app.domain.PeriStats.HOT_TERMS }.groupingBy { it.date }.eachCount()
    val fn: (java.time.LocalDate) -> Double? = when (metric) {
        0 -> { d -> unrest[d] ?: if (d in tracked) 0.0 else null }
        1 -> scale(de.fembiond.app.domain.ScaleTerms.SLEEP_QUALITY)
        2 -> scale(de.fembiond.app.domain.ScaleTerms.ENERGY)
        else -> { d -> hot[d]?.toDouble() ?: if (d in tracked) 0.0 else null }
    }
    val (series, mean) = remember(data, metric) { overlaySeries(data, 6, fn) }
    if (series.size < 2) return
    val maxY = if (metric == 3) (series.flatten().filterNotNull().maxOrNull() ?: 3.0).coerceAtLeast(3.0) else 5.0
    SectionCard(title = "Zyklen übereinander", icon = Icons.Rounded.Layers) {
        HintText("Die letzten ${series.size} Zyklen nach Zyklustag übereinandergelegt. Die kräftige Linie ist der Mittelwert, neuere Zyklen sind dunkler. Hinterlegt: die letzte Woche vor der Periode.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEachIndexed { i, m -> FilterChip(metric == i, { metric = i }, { Text(m) }) }
        }
        CycleOverlayChart(series, mean, maxY, if (metric == 0) unrestColor(4) else if (metric == 3) LocalPhaseColors.current.follicular else MaterialTheme.colorScheme.secondary)
    }
}

/** Perimenopause: STRAW-Einordnung, Zyklusvariabilität, Hitzewallungen und MRS-Verlauf. */
@Composable
fun PerimenopauseCard(data: AppData, from: java.time.LocalDate, to: java.time.LocalDate) {
    val hot = data.occurrences.filter { it.term in de.fembiond.app.domain.PeriStats.HOT_TERMS && !it.date.isBefore(from) && !it.date.isAfter(to) }
    val months = generateSequence(java.time.YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { !it.isAfter(java.time.YearMonth.from(to)) }.toList()
    val mrs = data.mrs.sortedBy { it.date }
    SectionCard(title = "Perimenopause", icon = Icons.Rounded.LocalFireDepartment) {
        Text("STRAW+10: ${data.summary.straw.label}", style = MaterialTheme.typography.titleSmall)
        HintText(data.summary.straw.description + (data.age?.let { " Alter: $it Jahre." } ?: ""))
        if (hot.isNotEmpty() && months.size >= 2) {
            Text("Hitzewallungen & Nachtschweiß pro Monat", style = MaterialTheme.typography.labelLarge)
            LineChart(months.map { m -> hot.count { java.time.YearMonth.from(it.date) == m }.toDouble() },
                months.map { "%02d/%02d".format(it.monthValue, it.year % 100) }, 0.0,
                months.maxOf { m -> hot.count { java.time.YearMonth.from(it.date) == m } }.coerceAtLeast(4).toDouble(), LocalPhaseColors.current.follicular)
            val byPhase = hot.groupBy { de.fembiond.app.domain.CycleEngine.dayInfo(data.summary, it.date)?.phase }.filterKeys { it != null }
            if (byPhase.isNotEmpty()) HintText("Nach Phase: " + byPhase.entries.sortedByDescending { it.value.size }.joinToString { "${it.key!!.label} ${it.value.size}×" })
        } else if (hot.isEmpty()) HintText("Noch keine Hitzewallungen oder Nachtschweiß erfasst – auf dem Startbildschirm geht das mit einem Tipp.")
        if (mrs.isNotEmpty()) {
            val last = mrs.last()
            Text("Menopause Rating Scale (MRS)", style = MaterialTheme.typography.labelLarge)
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

    Screen(title = "Auswertung", nav = null, actions = { androidx.compose.material3.IconButton(onClick = { nav.navigate(de.fembiond.app.Routes.guide("analysis")) }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.HelpOutline, "Hilfe") } }) {
        RangeAndFilter(data, range, { range = it }, categories, { categories = it })
        HintText("Zeitraum ${from.short()} – ${to.short()} · ${result.trackedDays} Tage mit Einträgen")

        if (result.insights.isNotEmpty()) SectionCard(title = "Erkenntnisse", icon = Icons.Rounded.AutoAwesome, container = MaterialTheme.colorScheme.primaryContainer) {
            result.insights.forEach { Text("• $it", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }

        SectionCard(title = "Jahresübersicht", icon = Icons.Rounded.CalendarViewMonth) {
            HintText("Ein Kästchen pro Tag der letzten 12 Monate, gefärbt nach der höchsten Unruhe-Stufe. Periodentage sind umrandet. Feld antippen für Details.")
            YearHeatmap(data, dayMarks(data).period) { nav.navigate(de.fembiond.app.Routes.day(it)) }
        }

        // Zyklen
        SectionCard(title = "Zykluslänge & Abweichungen", icon = Icons.Rounded.Timeline) {
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
                            Box(Modifier.weight(b.periodLength.toFloat().coerceAtMost(len.toFloat())).height(16.dp).background(pc.menstruation))
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
            HintText("Zahl = Länge in Tagen (… = laufender Zyklus) · Warnsymbol = deutliche Abweichung · Kapsel = unter Medikation (nicht Teil der Grundlinie).")
            HintText("Perimenopause (STRAW+10): ${data.summary.straw.label}")
        }

        OverlayCard(data)

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

        PerimenopauseCard(data, from, to)

        SectionCard(title = "Unruhe nach Zyklusphase", icon = Icons.Rounded.Waves) {
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

        if (result.anticipation.any { it.events > 0 }) SectionCard(title = "Unruhe rund um Ereignisse", icon = Icons.Rounded.Event) {
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
            Text("Unruhe-Episoden pro Monat", style = MaterialTheme.typography.labelLarge)
            LineChart(result.trends.map { it.unrestCount.toDouble() }, labels, 0.0, (result.trends.maxOf { it.unrestCount }.coerceAtLeast(4)).toDouble(), MaterialTheme.colorScheme.tertiary)
            if (result.trends.any { it.meanUnrestLevel != null }) {
                Text("Mittlere Unruhe-Stufe (1–5)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.meanUnrestLevel }, labels, 1.0, 5.0, unrestColor(3))
            }
            Text("Panikattacken pro Monat", style = MaterialTheme.typography.labelLarge)
            LineChart(result.trends.map { it.panicCount.toDouble() }, labels, 0.0, (result.trends.maxOf { it.panicCount }.coerceAtLeast(4)).toDouble(), MaterialTheme.colorScheme.error)
            if (result.trends.any { it.gad != null }) {
                Text("GAD-7 (0–21)", style = MaterialTheme.typography.labelLarge)
                LineChart(result.trends.map { it.gad }, labels, 0.0, 21.0, MaterialTheme.colorScheme.primary)
            }
        }
    }
}
