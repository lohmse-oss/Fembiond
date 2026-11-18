package de.fembiond.app.ui.screens

import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.PhaseLegend
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.TermChip
import de.fembiond.app.ui.sharedDay
import de.fembiond.app.ui.hm
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.monthYear
import de.fembiond.app.ui.theme.LocalPhaseColors
import de.fembiond.app.ui.weekdayLong
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayMarks(val period: Set<LocalDate>, val predicted: Set<LocalDate>, val fertile: Set<LocalDate>)

fun dayMarks(data: AppData): DayMarks {
    val s = data.summary
    val period = data.periods.flatMap { p ->
        val end = p.end ?: p.start.plusDays(s.baselinePeriodLength - 1L).let { if (it.isAfter(data.today)) data.today else it }
        generateSequence(p.start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }.toSet()
    val c = s.current
    val predicted = if (c == null) emptySet() else {
        val start = c.prediction.from
        generateSequence(start) { it.plusDays(1) }.take(s.baselinePeriodLength).toSet() +
            generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(c.prediction.to) }.toSet()
    }
    val fertile = if (c?.fertileFrom == null) emptySet() else
        generateSequence(c.fertileFrom) { it.plusDays(1) }.takeWhile { !it.isAfter(c.fertileTo) }.toSet()
    return DayMarks(period, predicted - period, fertile)
}

@Composable
fun CalendarScreen(data: AppData, nav: NavController) {
    var month by rememberSaveable { mutableStateOf(YearMonth.from(data.today).toString()) }
    val ym = YearMonth.parse(month)
    val marks = dayMarks(data)
    val pc = LocalPhaseColors.current
    val entryDays = data.entries.map { it.time.toLocalDate() }.toSet()
    val unrestDays = data.episodes.groupBy { it.date }.mapValues { (_, l) -> l.maxOf { it.level } }
    val eventDays = data.events.map { it.date }.toSet()

    Screen(title = "Kalender", nav = null, actions = {
        androidx.compose.material3.IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, "Suchen") }
        androidx.compose.material3.IconButton(onClick = { nav.navigate(de.fembiond.app.Routes.guide("calendar")) }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.HelpOutline, "Hilfe") }
    }) {
        SectionCard(container = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Vorheriger Monat") }
                Text(ym.atDay(1).monthYear(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Nächster Monat") }
            }
            Row {
                listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                    Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val first = ym.atDay(1)
            val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
            val days = (0 until offset).map { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
            days.chunked(7).forEach { week ->
                Row {
                    for (i in 0 until 7) {
                        val d = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (d != null) {
                                val isPeriod = d in marks.period
                                val isPred = d in marks.predicted
                                val isFertile = d in marks.fertile
                                val bg = when {
                                    isPeriod -> pc.menstruation
                                    isFertile -> pc.fertile
                                    else -> Color.Transparent
                                }
                                Column(
                                    Modifier.fillMaxWidth().sharedDay(d).clip(MaterialTheme.shapes.small).clickable { nav.navigate(Routes.day(d)) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier.size(34.dp).clip(CircleShape).background(bg)
                                            .then(if (isPred) Modifier.border(2.dp, pc.menstruation, CircleShape) else Modifier)
                                            .then(if (d == data.today) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${d.dayOfMonth}",
                                            color = if (isPeriod) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (d == data.today) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        if (d in entryDays) Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                        unrestDays[d]?.let { lvl -> Box(Modifier.size(5.dp).clip(CircleShape).background(de.fembiond.app.ui.unrestColor(lvl))) }
                                        if (d in eventDays) Box(Modifier.size(5.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.tertiary))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SectionCard(title = "Legende") {
            LegendRow(pc.menstruation, filled = true, "Periode")
            LegendRow(pc.menstruation, filled = false, "Prognose nächste Periode")
            LegendRow(pc.fertile, filled = true, "Fruchtbares Fenster (geschätzt)")
            LegendRow(MaterialTheme.colorScheme.primary, filled = true, "Eintrag vorhanden", small = true)
            LegendRow(de.fembiond.app.ui.unrestColor(3), filled = true, "Unruhe (Farbe nach Stufe, rot = Panik)", small = true)
            LegendRow(MaterialTheme.colorScheme.tertiary, filled = true, "Ereignis", small = true)
        }
    }
}

@Composable
private fun LegendRow(color: Color, filled: Boolean, label: String, small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val size = if (small) 8.dp else 18.dp
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size).clip(CircleShape)
                    .then(if (filled) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DayScreen(vm: AppViewModel, data: AppData, nav: NavController, date: LocalDate) {
    val info = CycleEngine.dayInfo(data.summary, date)
    val pc = LocalPhaseColors.current
    val periodHere = data.periods.firstOrNull { p -> !date.isBefore(p.start) && (p.end?.let { !date.isAfter(it) } ?: (date == p.start)) }
    val startsHere = data.periods.firstOrNull { it.start == date }
    val entries = data.entries.filter { it.time.toLocalDate() == date }.sortedBy { it.time }
    val panics = data.episodes.filter { it.date == date }.sortedBy { it.time }
    val dayEvents = data.events.filter { it.date == date }

    Screen(title = date.weekdayLong(), nav = nav) {
        SectionCard(modifier = Modifier.sharedDay(date)) {
            if (info != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(info.phase.icon(), null, tint = pc.of(info.phase))
                    Spacer(Modifier.width(8.dp))
                    Text("Zyklustag ${info.cycleDay} · ${info.phase.label}", style = MaterialTheme.typography.titleMedium)
                }
                if (info.medicated) HintText("Zyklus unter Medikation")
            } else HintText("Für diesen Tag ist noch kein Zyklus erfasst.")
            ChipFlow {
                if (startsHere != null) {
                    OutlinedButton(onClick = { vm.run { deletePeriod(startsHere.id) } }) { Text("Periodenbeginn entfernen") }
                } else {
                    OutlinedButton(onClick = { vm.run { startPeriod(date) } }) {
                        Icon(Icons.Rounded.WaterDrop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Periode begann")
                    }
                }
                val lastStart = data.periods.lastOrNull { !it.start.isAfter(date) }
                if (lastStart != null && startsHere == null && periodHere?.end != date) {
                    OutlinedButton(onClick = { vm.run { endPeriod(date) } }) { Text("Periode endete") }
                }
            }
        }

        data.medications.filter { CycleEngine.intakeDue(it, data.summary, date) }.forEach { m ->
            val taken = (m.id to date) in data.intakes
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Medication, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(m.displayName(), Modifier.weight(1f))
                    Checkbox(taken, { vm.run { setIntake(m.id, date, it) } })
                }
            }
        }

        if (dayEvents.isNotEmpty()) SectionCard(title = "Ereignisse", icon = Icons.Rounded.Event) {
            dayEvents.forEach { e ->
                EventItem(e, data, onClick = { nav.navigate(Routes.EVENTS) }, showOutcome = !date.isAfter(data.today),
                    onOutcome = { o -> vm.run { saveEvent(e.copy(outcome = o)) } })
            }
        }

        if (panics.isNotEmpty()) SectionCard(title = "Unruhe", icon = Icons.Rounded.Waves) {
            panics.forEach { p ->
                EpisodeRow(p, data) {
                    IconButton(onClick = { vm.run { deletePanic(p.id) } }) { Icon(Icons.Rounded.Delete, "Löschen") }
                }
            }
        }

        if (entries.isNotEmpty()) SectionCard(title = "Einträge") {
            entries.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider()
                Row {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.time.hm() + if (e.backfilled) " · nachgetragen" else "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChipFlow {
                            e.terms.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
                                .forEach { t -> TermChip(t.term, true, t.intensity, {}, t.category.icon()) }
                        }
                        e.terms.filter { it.term == ScaleTerms.SLEEP_QUALITY || it.term == ScaleTerms.ENERGY }.takeIf { it.isNotEmpty() }
                            ?.let { HintText(it.joinToString(" · ") { s -> "${s.term} ${s.intensity}/5" }) }
                        if (e.note.isNotBlank()) Text("„${e.note}“")
                    }
                    IconButton(onClick = { vm.run { deleteEntry(e.id) } }) { Icon(Icons.Rounded.Delete, "Löschen") }
                }
            }
        }

        if (entries.isEmpty() && panics.isEmpty() && dayEvents.isEmpty()) {
            EmptyState(Icons.Rounded.EditNote, if (date.isAfter(data.today)) "Noch in der Zukunft" else "Keine Einträge",
                if (date.isAfter(data.today)) "Für diesen Tag kannst du ein Ereignis planen." else "Für diesen Tag wurde noch nichts erfasst. Du kannst es hier nachtragen.")
        }
        if (!date.isAfter(data.today)) {
            Button(onClick = { nav.navigate(Routes.entry(date)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Eintrag für diesen Tag")
            }
            OutlinedButton(onClick = { nav.navigate(Routes.unrest(day = date)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Waves, null); Spacer(Modifier.width(8.dp)); Text("Unruhe für diesen Tag")
            }
        }
        PhaseLegend()
    }
}
