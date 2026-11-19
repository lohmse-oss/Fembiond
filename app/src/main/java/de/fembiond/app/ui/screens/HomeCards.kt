package de.fembiond.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.runtime.remember
import de.fembiond.app.ui.dm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
            val knowledge = de.fembiond.app.domain.CycleKnowledge.forDate(data.summary, data.medications, date, data.today)
            if (knowledge != null && knowledge.facts.isNotEmpty()) {
                Text("Im Zyklus typisch", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                TypicalFacts(knowledge.facts)
            }
            Row {
                if (!date.isAfter(data.today)) TextButton(onClick = { nav.navigate(Routes.day(date)) }) { Text("Tag öffnen") }
                TextButton(onClick = { nav.navigate(Routes.knowledge(cycleDay)) }) { Text("Zyklus-Wissen") }
            }
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
    val periTerms = de.fembiond.app.domain.Lexicon.PERI_TYPICAL
    val hot = PeriStats.dailyCounts(data.occurrences, periTerms, today, days).map { it.toDouble() }
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
        if (hot.any { it > 0 }) row("Perimeno.", "${hot.sum().toInt()}×") { Sparkline(hot, (hot.maxOrNull() ?: 1.0).coerceAtLeast(3.0), LocalPhaseColors.current.follicular, Modifier.fillMaxWidth(), bars = true) }
        TextButton(onClick = { nav.navigate(Routes.ANALYSIS) }) { Text("Zur Auswertung") }
    }
}

/** Perimenopause: eigene häufige Beschwerden mit einem Tipp erfassen, MRS-Fragebogen. */
@Composable
fun PeriCard(vm: AppViewModel, data: AppData, nav: NavController) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val today = data.today
    val quick = PeriStats.quickTerms(data.occurrences, today)
    val ownCount = PeriStats.symptomStats(data.occurrences, today.minusDays(120), today).size
    val last = data.mrs.maxByOrNull { it.date }
    fun categoryOf(t: String): Category =
        data.terms.firstOrNull { it.label == t && it.category.isDaily }?.category
            ?: de.fembiond.app.domain.Lexicon.entries.firstOrNull { it.label == t && it.category.isDaily }?.category
            ?: Category.BODY
    var pending by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    SectionCard(title = "Perimenopause", icon = Icons.Rounded.LocalFireDepartment) {
        HintText(
            if (ownCount > 0) "Deine häufigsten Beschwerden – mit einem Tipp erfassen, die Uhrzeit wird gespeichert."
            else "Häufige Beschwerden der Perimenopause laut aktueller Forschung – mit einem Tipp erfassen. Danach erscheinen hier deine eigenen."
        )
        ChipFlow {
            quick.forEach { t ->
                val n = PeriStats.countOn(data.occurrences, t, today)
                androidx.compose.material3.AssistChip(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); pending = t },
                    label = { Text(if (n > 0) "$t ($n)" else t) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                )
            }
        }
        pending?.let { t ->
            de.fembiond.app.ui.StrengthPrompt(t, onCancel = { pending = null }, onSave = { level ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                vm.run { saveEntry(LocalDateTime.now(), "", listOf(Selection(t, categoryOf(t), level)), "quick") }
                android.widget.Toast.makeText(context, "„$t“ erfasst", android.widget.Toast.LENGTH_SHORT).show()
                pending = null
            })
        }
        TextButton(onClick = { nav.navigate(Routes.entry()) }) { Text("Weitere Beschwerden eintragen") }
        if (last != null) HintText("MRS zuletzt ${last.date.short()}: ${last.total} Punkte – ${Mrs.severity(last.total)}")
        if (data.mrsDue) {
            Text("Menopause-Fragebogen (MRS) ist wieder dran – 11 kurze Fragen.", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { nav.navigate(Routes.MRS) }) { Text("Jetzt ausfüllen") }
        } else TextButton(onClick = { nav.navigate(Routes.MRS) }) { Text("MRS-Fragebogen") }
    }
}

/** Ein Hinweis in „Heute wichtig“. */
data class TodayItem(
    val key: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val text: String,
    val action: String?,
    val emphasized: Boolean = false,
    val onAction: () -> Unit = {},
)

fun todayItems(data: AppData, nav: NavController): List<TodayItem> {
    val s = data.summary
    val today = data.today
    val out = mutableListOf<TodayItem>()
    de.fembiond.app.domain.StressForecast.upcoming(s, data.events, data.episodes, data.occurrences, today).forEach { r ->
        out += TodayItem("risk-${r.event.id}", Icons.Rounded.TipsAndUpdates,
            (if (r.elevated) "Gut vorbereiten: " else "Hinweis: ") + r.event.displayName,
            "${relativeDays(today, r.event.date).replaceFirstChar { it.uppercase() }}: " + r.factors.first().replaceFirstChar { it.uppercase() } + ". " + r.tips.first() + ".",
            "Details", r.elevated) { nav.navigate(Routes.EVENTS) }
    }
    val c = s.current
    if (c != null && !c.inPeriod && c.prediction.overdueDays == 0 && de.fembiond.app.domain.daysBetween(today, c.prediction.from) in 0..5) {
        val pms = de.fembiond.app.domain.Pms.analyze(s, data.occurrences, data.episodes, today)
        if (pms.typical > 0 && pms.typicalSymptoms.isNotEmpty()) out += TodayItem("pms", Icons.Rounded.Psychology, "Die Tage vor der Periode",
            "Bisher oft: ${pms.typicalSymptoms.joinToString()}. Etwas mehr Ruhe und weniger Termine können helfen.", "PMS-Auswertung") { nav.navigate(Routes.ANALYSIS) }
    }
    if (data.settings.weeklyReview && (today.dayOfWeek == java.time.DayOfWeek.SUNDAY || today.dayOfWeek == java.time.DayOfWeek.MONDAY)) {
        val review = de.fembiond.app.domain.WeeklyReviewBuilder.build(s, data.occurrences, data.episodes, data.events,
            if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) today.minusDays(1) else today)
        out += TodayItem("weekly", Icons.Rounded.AutoStories, "Dein Wochenrückblick", de.fembiond.app.domain.WeeklyReviewBuilder.shortText(review), "Ansehen") { nav.navigate(Routes.WEEKLY) }
    }
    data.medications.filter { it.activeOn(today) }.forEach { m ->
        m.reviewDate?.let { r ->
            val d = de.fembiond.app.domain.daysBetween(today, r)
            if (d in 0..14) out += TodayItem("review-${m.id}", Icons.Rounded.MedicalServices, "Arzttermin ${relativeDays(today, r)}",
                "Neubefund zu ${m.name} am ${r.dm()} – den Arztbericht kannst du vorher als PDF erstellen.", "Arztbericht") { nav.navigate(Routes.EXPORT) }
        }
    }
    if (s.straw == de.fembiond.app.domain.StrawStage.EARLY || s.straw == de.fembiond.app.domain.StrawStage.LATE)
        out += TodayItem("straw", Icons.Rounded.Info, s.straw.label, s.straw.description, "Perimenopause") { nav.navigate(Routes.ANALYSIS) }
    if (data.gadDue && data.occurrences.size > 3)
        out += TodayItem("gad", Icons.Rounded.Quiz, "Kurzer Angst-Check (GAD-7)", "7 Fragen zu den letzten zwei Wochen – ergibt einen vergleichbaren Wert über die Monate.", "Ausfüllen") { nav.navigate(Routes.GAD) }
    if (data.mrsDue && data.occurrences.size > 3)
        out += TodayItem("mrs", Icons.Rounded.LocalFireDepartment, "Menopause-Fragebogen (MRS)", "11 kurze Fragen zu Beschwerden – alle 4 Wochen für den Verlauf.", "Ausfüllen") { nav.navigate(Routes.MRS) }
    val insight = de.fembiond.app.domain.Analytics.analyze(
        de.fembiond.app.domain.AnalysisInput(s, data.occurrences, data.episodes, data.medications, data.gad, today.minusDays(120), today, events = data.events)
    ).insights.firstOrNull { !it.startsWith("Bisher gibt es") && !it.startsWith("Der übliche Zyklus") && !it.contains("STRAW") }
    if (insight != null) out += TodayItem("insight", Icons.Rounded.AutoAwesome, "Erkenntnis", insight, "Auswertung") { nav.navigate(Routes.ANALYSIS) }
    if (data.entries.size < 5) out += TodayItem("new", Icons.Rounded.Info, "Neu hier?", "Die Anleitung erklärt in wenigen Minuten Eingabe, Unruhe-Stufen, Ereignisse und Auswertung.", "Anleitung") { nav.navigate(Routes.guide("start")) }
    return out
}

/** „Heute wichtig“: wischbare Karten statt vieler Karten untereinander. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayCarousel(data: AppData, nav: NavController) {
    val items = remember(data) { todayItems(data, nav) }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Heute wichtig" + if (items.size > 1) " · ${items.size}" else "", style = MaterialTheme.typography.titleMedium)
        if (items.size == 1) TodayCard(items.first(), Modifier.fillMaxWidth())
        else {
            // Wischbare Karten: jede vollständig sichtbar, Text bricht um, alle gleich hoch
            val width = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 32.dp - 28.dp).coerceAtMost(340.dp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item -> TodayCard(item, Modifier.width(width).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun TodayCard(item: TodayItem, modifier: Modifier) {
    Card(
        modifier.heightIn(min = 150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = if (item.emphasized) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(item.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(item.text, style = MaterialTheme.typography.bodyMedium)
            item.action?.let { TextButton(onClick = item.onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(it) } }
        }
    }
}

/** Plus-Menü (Material 3 Expressive): alle Erfassungen an einer Stelle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CaptureFabMenu(
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    onEntry: () -> Unit,
    onUnrest: () -> Unit,
    onSymptom: () -> Unit,
    onNote: () -> Unit,
    onEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        if (open) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = null, indication = null) { onOpen(false) }
        )
        androidx.compose.material3.FloatingActionButtonMenu(
            expanded = open,
            button = {
                androidx.compose.material3.ToggleFloatingActionButton(
                    checked = open,
                    onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onOpen(it) },
                ) {
                    val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                    Icon(icon, if (open) "Schließen" else "Erfassen", with(androidx.compose.material3.ToggleFloatingActionButtonDefaults) { Modifier.animateIcon({ checkedProgress }) })
                }
            },
            modifier = modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp),
        ) {
            listOf(
                Triple("Wie geht es dir?", Icons.Rounded.Mood, onEntry),
                Triple("Unruhe", Icons.Rounded.Waves, onUnrest),
                Triple("Beschwerde", Icons.Rounded.LocalFireDepartment, onSymptom),
                Triple("Notiz", Icons.Rounded.EditNote, onNote),
                Triple("Ereignis planen", Icons.Rounded.Event, onEvent),
            ).forEach { (label, icon, action) ->
                FloatingActionButtonMenuItem(
                    onClick = { onOpen(false); action() },
                    text = { Text(label) },
                    icon = { Icon(icon, null) },
                )
            }
        }
    }
}
