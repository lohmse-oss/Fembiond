package de.fembiond.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.CycleFact
import de.fembiond.app.domain.CycleKnowledge
import de.fembiond.app.domain.FactKind
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.dm
import de.fembiond.app.ui.weekdayLong
import java.time.LocalDate

fun FactKind.icon(): ImageVector = when (this) {
    FactKind.EVENT -> Icons.Rounded.Flag
    FactKind.HORMONE -> Icons.Rounded.Timeline
    FactKind.BODY -> Icons.Rounded.SelfImprovement
    FactKind.MOOD -> Icons.Rounded.Psychology
    FactKind.LAB -> Icons.Rounded.Biotech
    FactKind.MEDICATION -> Icons.Rounded.Medication
}

@Composable
fun FactKind.color(): Color = when (this) {
    FactKind.EVENT -> MaterialTheme.colorScheme.primary
    FactKind.HORMONE -> MaterialTheme.colorScheme.tertiary
    FactKind.BODY -> MaterialTheme.colorScheme.secondary
    FactKind.MOOD -> MaterialTheme.colorScheme.error
    FactKind.LAB -> MaterialTheme.colorScheme.outline
    FactKind.MEDICATION -> MaterialTheme.colorScheme.tertiary
}

/** Kompakte Liste „Im Zyklus typisch“ für Tagesansichten. */
@Composable
fun TypicalFacts(facts: List<CycleFact>, expanded: Boolean = false) {
    facts.forEach { f ->
        var open by rememberSaveable(f.id) { mutableStateOf(expanded) }
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Icon(f.kind.icon(), null, tint = f.kind.color(), modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall)
                AnimatedVisibility(open) {
                    Column {
                        Text(f.text, style = MaterialTheme.typography.bodyMedium)
                        Text("Quelle: ${f.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Zyklus-Wissen: typische Ereignisse und Zustände je Zyklustag, auf die eigene Zykluslänge umgerechnet. */
@Composable
fun KnowledgeScreen(data: AppData, nav: NavController, initialDay: Int? = null) {
    val current = data.summary.current
    val length = current?.expectedLength ?: data.summary.baselineLength.takeIf { data.summary.hasData } ?: 28
    val period = data.summary.cycles.lastOrNull()?.periodLength ?: data.settings.typicalPeriod
    val cyclic = data.medications.firstOrNull { it.schedule == ScheduleType.CYCLIC && it.activeOn(data.today) }
    val facts = CycleKnowledge.facts(length, period, cyclic)
    val todayDay = current?.cycleDay
    val maxDay = maxOf(length, todayDay ?: 0)
    var day by rememberSaveable { mutableStateOf((initialDay ?: todayDay ?: 1).coerceIn(1, maxDay)) }
    var kind by rememberSaveable { mutableStateOf<FactKind?>(null) }
    val ov = facts.firstOrNull { it.id == "ovulation" }?.fromDay
    fun dateOf(d: Int): LocalDate? = current?.cycleStart?.plusDays(d - 1L)

    Screen(title = "Zyklus-Wissen", nav = nav, jumpBar = true,
        actions = { androidx.compose.material3.IconButton(onClick = { nav.navigate(Routes.guide("knowledge")) }) { Icon(androidx.compose.material.icons.Icons.Rounded.HelpOutline, "Hilfe") } },
    ) {
        SectionCard(title = "Dein Zyklus", icon = Icons.Rounded.AutoStories) {
            Text(
                "Zykluslänge $length Tage · Periode $period Tage" + (ov?.let { " · Eisprung um Tag $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            HintText(
                "„Eisprung an Tag 14“ gilt nur für einen 28-Tage-Zyklus. Ziemlich konstant ist dagegen die Zeit nach dem Eisprung " +
                    "(etwa 12–14 Tage). Deshalb rechnet die App vom erwarteten Periodenbeginn zurück." +
                    if (current == null) " Solange keine Periode erfasst ist, gilt ein Standardzyklus von 28 Tagen." else "",
            )
        }

        SectionCard(title = "Was ist an Tag $day typisch?", icon = Icons.Rounded.Lightbulb) {
            val date = dateOf(day)
            Text(
                "Zyklustag $day" + (date?.let { " · ${it.weekdayLong()}" } ?: "") + if (day == todayDay) " · heute" else "",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = day.toFloat(), onValueChange = { day = it.toInt() },
                valueRange = 1f..maxDay.toFloat(), steps = (maxDay - 2).coerceAtLeast(0),
            )
            if (todayDay != null && day != todayDay) TextButton(onClick = { day = todayDay }) { Text("Zu heute (Tag $todayDay)") }
            val now = CycleKnowledge.factsFor(day, length, period, cyclic)
            if (now.isEmpty()) HintText("Für diesen Tag gibt es keinen festen Sachverhalt.")
            TypicalFacts(now, expanded = now.size <= 2)
        }

        SectionCard(title = "Zeitfenster im Zyklus", icon = Icons.Rounded.Timeline) {
            ChipFlow {
                FilterChip(kind == null, { kind = null }, { Text("Alle") })
                facts.map { it.kind }.distinct().forEach { k ->
                    FilterChip(kind == k, { kind = if (kind == k) null else k }, { Text(k.label) },
                        leadingIcon = { Icon(k.icon(), null, Modifier.size(16.dp)) })
                }
            }
            val shown = facts.filter { kind == null || it.kind == kind }
            shown.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider()
                FactTimelineRow(f, maxDay, day, dateOf(f.fromDay), dateOf(f.toDay)) { day = f.fromDay }
            }
        }

        HintText(CycleKnowledge.DISCLAIMER)
    }
}

@Composable
private fun FactTimelineRow(f: CycleFact, maxDay: Int, selected: Int, from: LocalDate?, to: LocalDate?, onSelect: () -> Unit) {
    var open by rememberSaveable("tl" + f.id) { mutableStateOf(false) }
    val color = f.kind.color()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val active = f.covers(selected)
    Column(Modifier.fillMaxWidth().clickable { open = !open; onSelect() }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(f.kind.icon(), null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (active) FontWeight.Bold else null, modifier = Modifier.weight(1f))
            Text(
                f.dayLabel + (from?.let { if (f.fromDay == f.toDay) " · ${it.dm()}" else " · ${it.dm()}–${to!!.dm()}" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(h / 2, h / 2)
            drawRoundRect(track, size = Size(w, h), cornerRadius = r)
            val unit = w / maxDay
            drawRoundRect(color, topLeft = Offset((f.fromDay - 1) * unit, 0f), size = Size((f.toDay - f.fromDay + 1) * unit, h), cornerRadius = r)
            val x = (selected - 0.5f) * unit
            drawLine(marker, Offset(x, -2f), Offset(x, h + 2f), strokeWidth = 2.dp.toPx())
        }
        AnimatedVisibility(open) {
            Column {
                Text(f.text, style = MaterialTheme.typography.bodyMedium)
                Text("Quelle: ${f.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
