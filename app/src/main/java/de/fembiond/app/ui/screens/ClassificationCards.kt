package de.fembiond.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.ClassificationResult
import de.fembiond.app.domain.SymptomAssessment
import de.fembiond.app.domain.SymptomClass
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.theme.LocalPhaseColors
import de.fembiond.app.domain.Phase

private fun SymptomClass.icon(): ImageVector = when (this) {
    SymptomClass.PERIOD_UNUSUAL -> Icons.Rounded.ReportProblem
    SymptomClass.PERIOD_USUAL -> Icons.Rounded.WaterDrop
    SymptomClass.PMS -> Icons.Rounded.Event
    SymptomClass.PERI -> Icons.Rounded.LocalFireDepartment
    SymptomClass.UNCLEAR -> Icons.Rounded.HelpOutline
}

/** Reiter „Einordnung“: Beschwerden erst in der Auswertung Periode, PMS oder Perimenopause zuordnen. */
@Composable
fun ClassificationCards(r: ClassificationResult, categories: Set<Category>) {
    val shown = r.symptoms.filter { it.category in categories }
    SectionCard(title = "So ordnet die App ein", icon = Icons.Rounded.Category) {
        HintText(
            "Beim Eintragen musst du nicht entscheiden, ob etwas PMS, Periode oder Perimenopause ist. Die App schaut, wann eine Beschwerde " +
                "im Zyklus auftritt: während der Blutung, in den 5 Tagen davor oder ohne Bezug zu einer Phase – dazu Stärke, Verlauf und Forschungsstand. " +
                "Eine Beschwerde kann mehreren Gruppen zugeordnet sein (PMS verstärkt sich z. B. oft in der Perimenopause).",
        )
        LegendRow()
    }

    val unusual = shown.filter { SymptomClass.PERIOD_UNUSUAL in it.classes }
    if (r.redFlags.isNotEmpty() || unusual.isNotEmpty()) SectionCard(
        title = SymptomClass.PERIOD_UNUSUAL.label, icon = SymptomClass.PERIOD_UNUSUAL.icon(),
        container = MaterialTheme.colorScheme.errorContainer,
    ) {
        HintText(SymptomClass.PERIOD_UNUSUAL.description)
        r.redFlags.forEach { f ->
            Text(f.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(f.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Quelle: ${f.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        unusual.forEachIndexed { i, s -> if (i > 0 || r.redFlags.isNotEmpty()) HorizontalDivider(); SymptomRow(s) }
    }

    listOf(SymptomClass.PERIOD_USUAL, SymptomClass.PMS, SymptomClass.PERI).forEach { c ->
        val list = shown.filter { c in it.classes && SymptomClass.PERIOD_UNUSUAL !in it.classes }
        val signals = if (c == SymptomClass.PERI) r.periSignals else emptyList()
        if (list.isNotEmpty() || signals.isNotEmpty()) SectionCard(title = c.label, icon = c.icon()) {
            HintText(c.description)
            signals.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            list.forEachIndexed { i, s -> if (i > 0 || signals.isNotEmpty()) HorizontalDivider(); SymptomRow(s) }
        }
    }

    val unclear = shown.filter { it.classes == listOf(SymptomClass.UNCLEAR) }
    if (unclear.isNotEmpty()) SectionCard(title = SymptomClass.UNCLEAR.label, icon = SymptomClass.UNCLEAR.icon()) {
        HintText(SymptomClass.UNCLEAR.description + " Mit weiteren Einträgen – auch an ruhigen Tagen – wird die Einordnung genauer.")
        unclear.forEachIndexed { i, s -> if (i > 0) HorizontalDivider(); SymptomRow(s) }
    }
    if (shown.isEmpty() && r.redFlags.isEmpty() && r.periSignals.isEmpty()) {
        EmptyState(Icons.Rounded.Category, "Noch nichts einzuordnen", "Sobald Beschwerden an mindestens zwei Tagen erfasst sind, erscheinen sie hier.")
    }
    HintText("Die Einordnung ist eine Orientierung aus den eigenen Einträgen, keine Diagnose. Auffälligkeiten bitte ärztlich besprechen.")
}

@Composable
private fun LegendRow() {
    val pc = LocalPhaseColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(pc.of(Phase.MENSTRUATION) to "Blutung", pc.of(Phase.LUTEAL) to "5 Tage davor", MaterialTheme.colorScheme.outline to "übrige Tage").forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(c) }
                Spacer(Modifier.width(4.dp))
                Text(l, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SymptomRow(s: SymptomAssessment) {
    val pc = LocalPhaseColors.current
    val colors = listOf(pc.of(Phase.MENSTRUATION), pc.of(Phase.LUTEAL), MaterialTheme.colorScheme.outline)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.term, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "${s.days} Tage" + (s.meanIntensity?.let { " · Ø Stärke ${de.fembiond.app.domain.de1(it)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val shares = listOf(s.periodShare, s.preShare, s.otherShare)
        if (shares.sum() > 0) Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRect(track, size = size)
            var x = 0f
            shares.forEachIndexed { i, v ->
                val w = (v * size.width).toFloat()
                if (w > 0) drawRect(colors[i], topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
        s.reasons.forEach { HintText("• $it") }
        val other = s.classes.filter { it != SymptomClass.UNCLEAR }
        if (other.size > 1) HintText("Auch: " + other.joinToString { it.label })
    }
}

