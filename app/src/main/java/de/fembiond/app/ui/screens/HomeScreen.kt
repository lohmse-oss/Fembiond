package de.fembiond.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import de.fembiond.app.domain.StressForecast
import de.fembiond.app.domain.WeeklyReviewBuilder
import de.fembiond.app.ui.WeekStrip
import de.fembiond.app.ui.phaseHeaderBrush
import de.fembiond.app.ui.stripDays
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.Phase
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.domain.StrawStage
import de.fembiond.app.domain.daysBetween
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.CycleRing
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.dm
import de.fembiond.app.ui.hm
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.relativeDays
import de.fembiond.app.ui.theme.LocalPhaseColors
import de.fembiond.app.ui.weekdayLong
import de.fembiond.app.ui.TermChip

@Composable
fun HomeScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    val today = data.today
    val s = data.summary
    val current = s.current
    val pc = LocalPhaseColors.current
    var phaseDialog by remember { mutableStateOf(false) }

    val override = data.settings.phaseOverride?.takeIf { data.settings.phaseOverrideCycleStart == current?.cycleStart }
    val phase = override ?: current?.phase

    val haptic = LocalHapticFeedback.current
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val profile = data.profile
    val hour = java.time.LocalTime.now().hour
    val greet = when (hour) { in 5..10 -> "Guten Morgen"; in 11..17 -> "Hallo"; in 18..22 -> "Guten Abend"; else -> "Gute Nacht" }

    val homeScroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(420.dp).background(phaseHeaderBrush(phase)))
        ScrollTopButton(homeScroll, Modifier.align(Alignment.BottomEnd).padding(16.dp).zIndex(1f))
        Column(
            Modifier.fillMaxSize().verticalScroll(homeScroll).statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (profile != null) "$greet, ${profile.name}" else greet, style = MaterialTheme.typography.headlineSmall)
                Text(today.weekdayLong(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { nav.navigate(Routes.guide("home")) }) { Icon(Icons.Rounded.HelpOutline, "Hilfe") }
        }

        if (profile?.isBirthday(today) == true) {
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cake, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Alles Liebe zum ${profile.age(today)}. Geburtstag, ${profile.name}!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (profile == null) {
            SectionCard(title = "Wie heißt du?", icon = Icons.Rounded.Face) {
                HintText("Name und Geburtsdatum machen die App persönlich und das Alter genau. Beides bleibt verschlüsselt auf diesem Gerät.")
                ProfileFields(vm, data)
            }
        }

        if (current == null) {
            SectionCard(title = "Willkommen", icon = Icons.Rounded.Info) {
                HintText("Es ist noch keine Periode erfasst. Trage vergangene Perioden nach oder starte mit der heutigen.")
                ChipFlow {
                    Button(onClick = { nav.navigate(Routes.HISTORY) }) { Text("Perioden nachtragen") }
                    OutlinedButton(onClick = { vm.run { startPeriod(today) } }) { Text("Periode heute") }
                    TextButton(onClick = { nav.navigate(Routes.guide("start")) }) { Text("Anleitung") }
                }
            }
        } else {
            val medDays = current.medicationToday.filter { it.schedule == ScheduleType.CYCLIC }
                .flatMap { (it.cycleDayFrom ?: 0)..(it.cycleDayTo ?: -1) }.toSet()
            val unrestByDay = data.episodes.filter { !it.date.isBefore(current.cycleStart) }
                .groupBy { daysBetween(current.cycleStart, it.date) + 1 }.mapValues { (_, l) -> l.maxOf { it.level } }
            SectionCard(container = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f)) {
                CycleRing(
                    current, medDays, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    unrestByDay = unrestByDay, selectedDay = selectedDay,
                    onDayTap = { d -> selectedDay = if (selectedDay == d) null else d },
                ) {
                    Text("Zyklustag", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${current.cycleDay}", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (phase != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable { phaseDialog = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(phase.icon(), null, tint = pc.of(phase), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(phase.label + if (override != null) " (manuell)" else "", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                AnimatedVisibility(selectedDay != null) {
                    selectedDay?.let { d -> DayPeek(data, current.cycleStart.plusDays(d - 1L), d, nav) { selectedDay = null } }
                }
                if (selectedDay == null) HintText("Tipp: Tippe auf einen Tag im Ring für Details. Punkte außen zeigen Unruhe.", Modifier.fillMaxWidth(), center = true)
                val pred = current.prediction
                val predText = when {
                    pred.overdueDays > 0 -> "Periode überfällig seit ${pred.overdueDays} ${if (pred.overdueDays == 1) "Tag" else "Tagen"}"
                    pred.from == pred.to -> "Nächste Periode ${relativeDays(today, pred.from)} (${pred.from.dm()})"
                    else -> "Nächste Periode ${pred.from.dm()} – ${pred.to.dm()}"
                }
                Text(predText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (pred.withdrawalBleed) {
                    HintText("Erwartet als Abbruchblutung nach der letzten Einnahme laut Schema.", Modifier.fillMaxWidth(), center = true)
                } else if (pred.overdueDays == 0) {
                    val a = daysBetween(today, pred.from).coerceAtLeast(0)
                    val b = daysBetween(today, pred.to).coerceAtLeast(0)
                    val range = if (a == b) "in $a Tagen" else "in etwa $a–$b Tagen"
                    HintText(range + if (s.naturalCompleteCount < 3) " · Prognose wird mit mehr Zyklen genauer" else "", Modifier.fillMaxWidth(), center = true)
                }
                current.fertileFrom?.let {
                    HintText("Fruchtbares Fenster (geschätzt): ${it.dm()} – ${current.fertileTo!!.dm()}", Modifier.fillMaxWidth(), center = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (current.inPeriod) {
                        OutlinedButton(onClick = { vm.run { endPeriod(today) } }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.WaterDrop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Periode endet heute")
                        }
                    } else {
                        OutlinedButton(onClick = { vm.run { startPeriod(today) } }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.WaterDrop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Periode beginnt heute")
                        }
                    }
                }
            }

            WeekStrip(stripDays(data), today) { nav.navigate(Routes.day(it)) }

            if (s.straw == StrawStage.EARLY || s.straw == StrawStage.LATE) {
                SectionCard(title = s.straw.label, icon = Icons.Rounded.Info, container = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(s.straw.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        // Schnellaktionen
        Button(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); nav.navigate(Routes.entry()) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(10.dp))
            Text("Wie geht es dir gerade?", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); nav.navigate(Routes.unrest()) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
            ) { Icon(Icons.Rounded.Waves, null); Spacer(Modifier.width(8.dp)); Text("Unruhe", maxLines = 1) }
            FilledTonalButton(
                onClick = { nav.navigate(Routes.entry(note = true)) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) { Icon(Icons.Rounded.EditNote, null); Spacer(Modifier.width(8.dp)); Text("Notiz", maxLines = 1) }
        }

        // Belastungshinweise für die nächsten 7 Tage
        StressForecast.upcoming(s, data.events, data.episodes, data.occurrences, today).forEach { r -> StressHintCard(r, data, nav) }

        // PMS-Hinweis in den Tagen vor der Periode
        if (current != null && !current.inPeriod && current.prediction.overdueDays == 0 && daysBetween(today, current.prediction.from) in 0..5) {
            val pms = remember(data) { de.fembiond.app.domain.Pms.analyze(s, data.occurrences, data.episodes, today) }
            if (pms.typical > 0 && pms.typicalSymptoms.isNotEmpty()) {
                SectionCard(title = "Die Tage vor der Periode", icon = Icons.Rounded.Psychology, container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("In dieser Zeit traten bei dir bisher oft auf: ${pms.typicalSymptoms.joinToString()}.", style = MaterialTheme.typography.bodyMedium)
                    HintText("Sei nachsichtig mit dir: etwas mehr Ruhe, Bewegung an der frischen Luft und weniger Termine können helfen. Nach Beginn der Periode klingt es meist wieder ab.")
                    TextButton(onClick = { nav.navigate(Routes.ANALYSIS) }) { Text("PMS-Auswertung") }
                }
            }
        }

        // Wochenrückblick (Sonntag und Montag)
        if (data.settings.weeklyReview && (today.dayOfWeek == java.time.DayOfWeek.SUNDAY || today.dayOfWeek == java.time.DayOfWeek.MONDAY)) {
            val review = WeeklyReviewBuilder.build(s, data.occurrences, data.episodes, data.events,
                if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) today.minusDays(1) else today)
            SectionCard(title = "Dein Wochenrückblick", icon = Icons.Rounded.AutoStories, container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(WeeklyReviewBuilder.shortText(review), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { nav.navigate(Routes.WEEKLY) }) { Text("Rückblick ansehen") }
            }
        }

        TrendsCard(data, nav)
        PeriCard(vm, data, nav)

        // Anstehende Ereignisse mit Zyklusprognose
        val upcoming = data.events.filter { !it.date.isBefore(today) && daysBetween(today, it.date) <= 21 }.sortedBy { it.date }.take(3)
        SectionCard(title = "Anstehend", icon = Icons.Rounded.Event) {
            if (upcoming.isEmpty()) HintText("Keine Ereignisse in den nächsten 3 Wochen. Plane Feiern, Essen gehen oder Konzerte, um zu sehen, in welche Zyklusphase sie fallen.")
            upcoming.forEachIndexed { i, e ->
                if (i > 0) androidx.compose.material3.HorizontalDivider()
                EventItem(e, data, onClick = { nav.navigate(Routes.EVENTS) })
            }
            TextButton(onClick = { nav.navigate(Routes.EVENTS) }) { Text(if (upcoming.isEmpty()) "Ereignis planen" else "Alle Ereignisse") }
        }

        // Blutung erfasst, aber keine Periode aktiv
        val bleedingToday = data.occurrences.any {
            it.date == today && it.category == Category.BLEEDING && it.term !in setOf("Schmierblutung", "Zwischenblutung")
        }
        if (bleedingToday && current?.inPeriod != true) {
            SectionCard(title = "Blutung erfasst", icon = Icons.Rounded.WaterDrop) {
                HintText("Soll heute als Beginn der Periode gelten?")
                Button(onClick = { vm.run { startPeriod(today) } }) { Text("Periode beginnt heute") }
            }
        }

        // Medikation
        data.medications.filter { it.activeOn(today) }.forEach { m ->
            val due = CycleEngine.intakeDue(m, s, today)
            val taken = (m.id to today) in data.intakes
            SectionCard(title = m.displayName(), icon = Icons.Rounded.Medication) {
                if (due) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.run { setIntake(m.id, today, !taken) } }) {
                        Checkbox(checked = taken, onCheckedChange = { vm.run { setIntake(m.id, today, it) } })
                        Text(if (taken) "Heute eingenommen" else "Heute fällig (${m.scheduleLabel()})", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    HintText("Heute keine Einnahme laut Schema (${m.scheduleLabel()}).")
                }
                m.reviewDate?.let { r ->
                    val days = daysBetween(today, r)
                    if (days in 0..14) HintText("Neubefund/Arzttermin ${relativeDays(today, r)} (${r.dm()}). Den Arztbericht kannst du unter Mehr → Export erstellen.")
                }
            }
        }

        if (data.entries.size < 5 && current != null) {
            SectionCard(title = "Neu hier?", icon = Icons.Rounded.Info) {
                HintText("Die Anleitung erklärt in wenigen Minuten Eingabe, Unruhe-Stufen, Ereignisse und Auswertung.")
                TextButton(onClick = { nav.navigate(Routes.guide("start")) }) { Text("Anleitung öffnen") }
            }
        }

        if (data.gadDue && data.occurrences.size > 3) {
            SectionCard(title = "Kurzer Angst-Check (GAD-7)", icon = Icons.Rounded.Quiz) {
                HintText("7 Fragen zu den letzten zwei Wochen. Ergibt einen vergleichbaren Wert über die Monate.")
                FilledTonalButton(onClick = { nav.navigate(Routes.GAD) }) { Text("Jetzt ausfüllen") }
            }
        }

        // Heutige Einträge
        val todays = data.entries.filter { it.time.toLocalDate() == today }
        val panicsToday = data.episodes.filter { it.date == today }.sortedBy { it.time }
        if (todays.isNotEmpty() || panicsToday.isNotEmpty()) {
            SectionCard(title = "Heute erfasst") {
                panicsToday.forEach { p -> EpisodeRow(p, data, onClick = { nav.navigate(Routes.day(today)) }) }
                todays.sortedBy { it.time }.forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.day(today)) }) {
                        Text(e.time.hm(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (e.terms.isNotEmpty()) ChipFlow {
                            e.terms.filter { it.term != de.fembiond.app.domain.ScaleTerms.SLEEP_QUALITY && it.term != de.fembiond.app.domain.ScaleTerms.ENERGY }
                                .forEach { t -> TermChip(t.term, true, t.intensity, {}, t.category.icon()) }
                        }
                        e.terms.filter { it.term == de.fembiond.app.domain.ScaleTerms.SLEEP_QUALITY || it.term == de.fembiond.app.domain.ScaleTerms.ENERGY }
                            .takeIf { it.isNotEmpty() }?.let { sc ->
                                HintText(sc.joinToString(" · ") { "${it.term} ${it.intensity}/5" })
                            }
                        if (e.note.isNotBlank()) Text("„${e.note}“", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        }
    }

    if (phaseDialog && current != null) {
        var choice by remember { mutableStateOf(override) }
        AlertDialog(
            onDismissRequest = { phaseDialog = false },
            title = { Text("Phase festlegen") },
            text = {
                Column {
                    HintText("Normalerweise berechnet die App die Phase. Hier kannst du sie für den aktuellen Zyklus manuell festlegen.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { choice = null }) {
                        RadioButton(choice == null, { choice = null }); Text("Automatisch (${current.phase.label})")
                    }
                    Phase.entries.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { choice = p }) {
                            RadioButton(choice == p, { choice = p }); Text(p.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = choice
                    vm.run { updateSettings { it.copy(phaseOverride = c, phaseOverrideCycleStart = if (c == null) null else current.cycleStart) } }
                    phaseDialog = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { phaseDialog = false }) { Text("Abbrechen") } },
        )
    }
}
