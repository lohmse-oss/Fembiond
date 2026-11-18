package de.fembiond.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.CurrentState
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.Heatmap
import de.fembiond.app.domain.Phase
import de.fembiond.app.ui.theme.LocalPhaseColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun Category.icon(): ImageVector = when (this) {
    Category.MIND -> Icons.Rounded.Psychology
    Category.BODY -> Icons.Rounded.Accessibility
    Category.PERIMENOPAUSE -> Icons.Rounded.LocalFireDepartment
    Category.SLEEP_ENERGY -> Icons.Rounded.Bedtime
    Category.BLEEDING -> Icons.Rounded.WaterDrop
    Category.LIFE -> Icons.Rounded.Groups
    Category.MEDICATION_EFFECT -> Icons.Rounded.Medication
    Category.PANIC_SYMPTOM -> Icons.Rounded.MonitorHeart
    Category.PANIC_TRIGGER -> Icons.Rounded.TrackChanges
    Category.PANIC_HELP -> Icons.Rounded.SelfImprovement
    Category.EVENT_TYPE -> Icons.Rounded.Event
}

fun Phase.icon(): ImageVector = when (this) {
    Phase.MENSTRUATION -> Icons.Rounded.WaterDrop
    Phase.FOLLICULAR -> Icons.Rounded.Spa
    Phase.OVULATION -> Icons.Rounded.TrackChanges
    Phase.LUTEAL -> Icons.Rounded.Bedtime
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
            content()
        }
    }
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (center) TextAlign.Center else null)
}

/** Intensität 1–5 als fünf antippbare Punkte. */
@Composable
fun IntensitySelector(value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val active = value != null && i <= value
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onChange(if (value == i) null else i) }
                    .semantics { contentDescription = "Intensität $i" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$i",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Kleine Intensitätsanzeige innerhalb eines Chips. */
@Composable
fun IntensityPips(value: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i <= value) color else color.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
fun TermChip(
    label: String,
    selected: Boolean,
    intensity: Int?,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selected && intensity != null) {
                    Spacer(Modifier.width(8.dp))
                    IntensityPips(intensity, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        leadingIcon = when {
            selected -> ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) })
            icon != null -> ({ Icon(icon, null, Modifier.size(18.dp)) })
            else -> null
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
fun AddChip(label: String = "Eigener Begriff", onClick: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
fun PhaseLegend(modifier: Modifier = Modifier) {
    val pc = LocalPhaseColors.current
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Phase.entries.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(p.icon(), null, tint = pc.of(p), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(p.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Zyklusring: ein Segment pro Zyklustag in Phasenfarbe. Vergangene Tage kräftig, zukünftige blass,
 * fruchtbares Fenster als Außenband, Einnahmetage als Innenmarken, heute als Punkt.
 */
@Composable
fun CycleRing(state: CurrentState, medicationDays: Set<Int>, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val pc = LocalPhaseColors.current
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface
    val medColor = MaterialTheme.colorScheme.tertiary
    val total = max(state.expectedLength, state.cycleDay)
    val periodLen = if (state.inPeriod) max(state.cycleDay, 1) else 0
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val stroke = size.minDimension * 0.075f
            val radius = size.minDimension / 2 - stroke * 1.4f
            val c = Offset(size.width / 2, size.height / 2)
            val sweep = 360f / total
            val gap = min(1.6f, sweep * 0.25f)
            drawCircle(track, radius, c, style = Stroke(stroke))
            for (d in 1..total) {
                val length = state.expectedLength
                val pLen = if (state.inPeriod) max(periodLen, 1) else min(5, length)
                val phase = CycleEngine.phaseFor(d, max(length, d), pLen)
                val base = pc.of(phase)
                val color = if (d <= state.cycleDay) base else base.copy(alpha = 0.35f)
                drawArc(
                    color = color,
                    startAngle = -90f + (d - 1) * sweep + gap / 2,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                if (d in medicationDays) {
                    val a = Math.toRadians((-90f + (d - 0.5f) * sweep).toDouble())
                    val r1 = radius - stroke * 0.95f
                    drawCircle(medColor, stroke * 0.14f, Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()))
                }
            }
            state.fertileFrom?.let { ff ->
                val fromDay = (ff.toEpochDay() - state.cycleStart.toEpochDay()).toInt() + 1
                val toDay = (state.fertileTo!!.toEpochDay() - state.cycleStart.toEpochDay()).toInt() + 1
                val outer = radius + stroke * 0.95f
                drawArc(
                    color = pc.ovulation.copy(alpha = 0.6f),
                    startAngle = -90f + (fromDay - 1) * sweep,
                    sweepAngle = (toDay - fromDay + 1) * sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - outer, c.y - outer),
                    size = Size(outer * 2, outer * 2),
                    style = Stroke(stroke * 0.22f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                )
            }
            val a = Math.toRadians((-90f + (state.cycleDay - 0.5f) * sweep).toDouble())
            val p = Offset(c.x + (radius * cos(a)).toFloat(), c.y + (radius * sin(a)).toFloat())
            drawCircle(Color.White, stroke * 0.62f, p)
            drawCircle(marker, stroke * 0.62f, p, style = Stroke(stroke * 0.18f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().aspectRatio(1f)

/** Heatmap Begriff × Zyklustag – Begriff über der Zeile, Zellen über die volle Breite. */
@Composable
fun HeatmapView(map: Heatmap, modifier: Modifier = Modifier) {
    val pc = LocalPhaseColors.current
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = maxWidth / map.maxDay
        Column {
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                for (d in listOf(1, 7, 14, 21, 28, 35).filter { it <= map.maxDay }) {
                    Text(
                        "$d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.offset(x = cell * (d - 1)),
                    )
                }
            }
            map.rows.forEachIndexed { ri, r ->
                Text(r.term, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                Row(Modifier.fillMaxWidth().height(14.dp)) {
                    r.cells.forEachIndexed { di, v ->
                        val isSel = selected == (ri to di)
                        Box(
                            Modifier
                                .width(cell)
                                .fillMaxHeight()
                                .padding(horizontal = 0.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (map.trackedPerDay[di] == 0.0) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f) else lerp(pc.heatLow, pc.heatHigh, v.toFloat().coerceIn(0f, 1f)))
                                .then(if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)) else Modifier)
                                .clickable { selected = if (isSel) null else ri to di }
                        )
                    }
                }
            }
            selected?.let { (ri, di) ->
                val r = map.rows.getOrNull(ri) ?: return@let
                Text(
                    "${r.term}, Zyklustag ${di + 1}${if (di + 1 == map.maxDay) "+" else ""}: an ${(r.cells[di] * 100).toInt()} % der erfassten Tage",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Zyklustag →  selten", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                for (i in 0..4) Box(Modifier.padding(1.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(lerp(pc.heatLow, pc.heatHigh, i / 4f)))
                Spacer(Modifier.width(6.dp))
                Text("häufig", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Einfaches Liniendiagramm mit Punkten; null-Werte erzeugen Lücken. */
@Composable
fun LineChart(
    values: List<Double?>,
    labels: List<String>,
    minY: Double,
    maxY: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size
            if (n == 0) return@Canvas
            val stepX = if (n == 1) 0f else size.width / (n - 1)
            fun y(v: Double) = size.height - ((v - minY) / (maxY - minY)).toFloat().coerceIn(0f, 1f) * size.height
            for (i in 0..4) {
                val gy = size.height * i / 4
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            var prev: Offset? = null
            values.forEachIndexed { i, v ->
                if (v == null) { prev = null; return@forEachIndexed }
                val p = Offset(if (n == 1) size.width / 2 else i * stepX, y(v))
                prev?.let { drawLine(color, it, p, strokeWidth = 5f, cap = StrokeCap.Round) }
                drawCircle(color, 7f, p)
                prev = p
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            if (labels.size > 1) Text(labels.last(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Horizontaler Balken für Verhältnisse 0..1. */
@Composable
fun RatioBar(label: String, value: Double, valueText: String, color: Color, icon: ImageVector? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(valueText, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

@Composable
fun BigNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Farbe je Unruhe-Stufe 1–5 (5 = Panikattacke). */
@Composable
fun unrestColor(level: Int): Color {
    val dark = MaterialTheme.colorScheme.background.luminanceCompat() < 0.3f
    return when (level) {
        1 -> if (dark) Color(0xFFC5B6F2) else Color(0xFF7E6BC0)
        2 -> if (dark) Color(0xFFD7A6CF) else Color(0xFF8E4F86)
        3 -> if (dark) Color(0xFFFFB68E) else Color(0xFFC0602A)
        4 -> if (dark) Color(0xFFFF9A7A) else Color(0xFFB3401F)
        else -> MaterialTheme.colorScheme.error
    }
}

private fun Color.luminanceCompat(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Runder Stufen-Badge mit Zahl. */
@Composable
fun LevelBadge(level: Int, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val c = unrestColor(level)
    Box(Modifier.size(size).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
        Text("$level", color = if (MaterialTheme.colorScheme.background.luminanceCompat() < 0.3f) Color(0xFF22191C) else Color.White,
            style = MaterialTheme.typography.labelLarge)
    }
}
