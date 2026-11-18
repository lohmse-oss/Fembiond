package de.fembiond.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.CurrentState
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.Phase
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.ui.theme.LocalPhaseColors
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Weicher Farbverlauf in der Farbe der aktuellen Phase für den Kopfbereich. */
@Composable
fun phaseHeaderBrush(phase: Phase?): Brush {
    val pc = LocalPhaseColors.current
    val base = phase?.let { pc.of(it) } ?: MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(listOf(lerp(bg, base, 0.32f), lerp(bg, base, 0.12f), bg))
}

/**
 * Zyklusring: ein Segment pro Zyklustag in Phasenfarbe, baut sich beim Öffnen animiert auf.
 * Außen: Unruhe-Punkte (Farbe/Größe nach Stufe) und fruchtbares Fenster; innen: Einnahmetage.
 * Antippen eines Segments meldet den Zyklustag.
 */
@Composable
fun CycleRing(
    state: CurrentState,
    medicationDays: Set<Int>,
    modifier: Modifier = Modifier,
    unrestByDay: Map<Int, Int> = emptyMap(),
    selectedDay: Int? = null,
    onDayTap: ((Int) -> Unit)? = null,
    center: @Composable () -> Unit,
) {
    val pc = LocalPhaseColors.current
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface
    val medColor = MaterialTheme.colorScheme.tertiary
    val levelColors = (1..5).associateWith { unrestColor(it) }
    val haptic = LocalHapticFeedback.current
    val total = max(state.expectedLength, state.cycleDay)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.cycleStart) { progress.snapTo(0f); progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Zyklusring, Zyklustag ${state.cycleDay} von etwa ${state.expectedLength}" }
                .pointerInput(total, onDayTap) {
                    if (onDayTap == null) return@pointerInput
                    detectTapGestures { pos ->
                        val stroke = size.width * 0.075f
                        val radius = size.width / 2f - stroke * 1.6f
                        val dx = pos.x - size.width / 2f
                        val dy = pos.y - size.height / 2f
                        if (kotlin.math.abs(hypot(dx, dy) - radius) <= stroke * 1.8f) {
                            var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                            if (deg < 0) deg += 360f
                            val day = (deg / (360f / total)).toInt() + 1
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onDayTap(day.coerceIn(1, total))
                        }
                    }
                },
        ) {
            val stroke = size.minDimension * 0.075f
            val radius = size.minDimension / 2 - stroke * 1.6f
            val c = Offset(size.width / 2, size.height / 2)
            val sweep = 360f / total
            val gap = min(1.6f, sweep * 0.25f)
            val shown = progress.value * total
            drawCircle(track, radius, c, style = Stroke(stroke))
            val pLen = if (state.inPeriod) max(state.cycleDay, 1) else min(5, state.expectedLength)
            for (d in 1..total) {
                if (d - 1 > shown) break
                val part = (shown - (d - 1)).coerceIn(0f, 1f)
                val phase = CycleEngine.phaseFor(d, max(state.expectedLength, d), pLen)
                val base = pc.of(phase)
                val color = if (d <= state.cycleDay) base else base.copy(alpha = 0.35f)
                val sel = d == selectedDay
                drawArc(
                    color = color,
                    startAngle = -90f + (d - 1) * sweep + gap / 2,
                    sweepAngle = (sweep - gap) * part,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(if (sel) stroke * 1.45f else stroke, cap = StrokeCap.Butt),
                )
                val a = Math.toRadians((-90f + (d - 0.5f) * sweep).toDouble())
                if (d in medicationDays && part >= 1f) {
                    val r1 = radius - stroke * 1.0f
                    drawCircle(medColor, stroke * 0.14f, Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()))
                }
                unrestByDay[d]?.let { lvl ->
                    if (part >= 1f) {
                        val r2 = radius + stroke * 1.15f
                        drawCircle(levelColors[lvl.coerceIn(1, 5)]!!, stroke * (0.12f + lvl * 0.035f), Offset(c.x + (r2 * cos(a)).toFloat(), c.y + (r2 * sin(a)).toFloat()))
                    }
                }
            }
            if (progress.value >= 0.98f) {
                state.fertileFrom?.let { ff ->
                    val fromDay = (ff.toEpochDay() - state.cycleStart.toEpochDay()).toInt() + 1
                    val toDay = (state.fertileTo!!.toEpochDay() - state.cycleStart.toEpochDay()).toInt() + 1
                    val inner = radius - stroke * 1.0f
                    drawArc(
                        color = pc.ovulation.copy(alpha = 0.55f),
                        startAngle = -90f + (fromDay - 1) * sweep,
                        sweepAngle = (toDay - fromDay + 1) * sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - inner, c.y - inner),
                        size = Size(inner * 2, inner * 2),
                        style = Stroke(stroke * 0.18f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                    )
                }
                val a = Math.toRadians((-90f + (state.cycleDay - 0.5f) * sweep).toDouble())
                val p = Offset(c.x + (radius * cos(a)).toFloat(), c.y + (radius * sin(a)).toFloat())
                drawCircle(Color.White, stroke * 0.62f, p)
                drawCircle(marker, stroke * 0.62f, p, style = Stroke(stroke * 0.18f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

/** Ein Tag im Wochenstreifen. */
data class StripDay(val date: LocalDate, val phase: Phase?, val unrest: Int?, val sleep: Int?, val hasEntry: Boolean)

fun stripDays(data: AppData, days: Int = 28): List<StripDay> {
    val byDayUnrest = data.episodes.groupBy { it.date }.mapValues { (_, l) -> l.maxOf { it.level } }
    val sleep = data.occurrences.filter { it.term == ScaleTerms.SLEEP_QUALITY }.groupBy { it.date }
        .mapValues { (_, l) -> l.mapNotNull { it.intensity }.average().let { v -> if (v.isNaN()) null else Math.round(v).toInt() } }
    val entryDays = data.entries.map { it.time.toLocalDate() }.toSet()
    return (days - 1 downTo 0).map { back ->
        val d = data.today.minusDays(back.toLong())
        StripDay(d, CycleEngine.dayInfo(data.summary, d)?.phase, byDayUnrest[d], sleep[d], d in entryDays || byDayUnrest.containsKey(d))
    }
}

/** Wischbarer Streifen der letzten Wochen: Phase, Unruhe-Stufe, Schlaf. Heute ganz rechts. */
@Composable
fun WeekStrip(days: List<StripDay>, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val pc = LocalPhaseColors.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (days.size - 7).coerceAtLeast(0))
    val haptic = LocalHapticFeedback.current
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items(days, key = { it.date.toEpochDay() }) { d ->
            val isToday = d.date == today
            Column(
                Modifier.width(44.dp).sharedDay(d.date).clip(RoundedCornerShape(14.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onDay(d.date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, DE).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.titleSmall, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                Box(Modifier.width(24.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(d.phase?.let { pc.of(it) } ?: MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                    when {
                        d.unrest != null -> Box(Modifier.size((6 + d.unrest).dp).clip(CircleShape).background(unrestColor(d.unrest)))
                        d.hasEntry -> Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, null, Modifier.size(10.dp), tint = if (d.sleep != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)
                    Text(d.sleep?.toString() ?: "–", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

/** Kleiner Verlauf (Sparkline) mit Füllung; null = keine Daten an dem Tag. */
@Composable
fun Sparkline(values: List<Double?>, maxY: Double, color: Color, modifier: Modifier = Modifier, bars: Boolean = false) {
    Canvas(modifier.height(36.dp)) {
        val n = values.size
        if (n == 0) return@Canvas
        val step = size.width / max(1, n - 1)
        fun y(v: Double) = size.height - (v / maxY).toFloat().coerceIn(0f, 1f) * (size.height - 4f) - 2f
        if (bars) {
            val bw = size.width / n
            values.forEachIndexed { i, v ->
                if (v != null && v > 0) drawRoundRect(color, Offset(i * bw + bw * 0.2f, y(v)), Size(bw * 0.6f, size.height - y(v)),
                    androidx.compose.ui.geometry.CornerRadius(3f, 3f))
            }
            return@Canvas
        }
        val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(i * step, y(it)) } }
        if (pts.isEmpty()) return@Canvas
        val path = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        val fill = Path().apply { addPath(path); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
        drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
        drawCircle(color, 6f, pts.last())
    }
}

/** Jahresübersicht: ein Kästchen pro Tag (Spalten = Wochen), gefärbt nach Unruhe, Periodentage umrandet. */
@Composable
fun YearHeatmap(data: AppData, periodDays: Set<LocalDate>, onDay: (LocalDate) -> Unit) {
    val pc = LocalPhaseColors.current
    val today = data.today
    val start = today.minusWeeks(52).with(java.time.DayOfWeek.MONDAY)
    val weeks = ((today.toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
    val unrest = data.episodes.groupBy { it.date }.mapValues { (_, l) -> l.maxOf { it.level } }
    val tracked = (data.entries.map { it.time.toLocalDate() } + data.episodes.map { it.date }).toSet()
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val calm = MaterialTheme.colorScheme.secondaryContainer
    val scroll = rememberScrollState(Int.MAX_VALUE)
    var sel by remember { mutableStateOf<LocalDate?>(null) }
    Column {
        Row(Modifier.horizontalScroll(scroll)) {
            Column(Modifier.padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("Mo", "", "Mi", "", "Fr", "", "So").forEach {
                    Box(Modifier.height(13.dp)) { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
            for (w in 0 until weeks) {
                Column(Modifier.padding(end = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (dow in 0 until 7) {
                        val d = start.plusDays(w * 7L + dow)
                        val lvl = unrest[d]
                        val color = when {
                            d.isAfter(today) -> Color.Transparent
                            lvl != null -> unrestColor(lvl)
                            d in tracked -> calm
                            else -> empty
                        }
                        Box(
                            Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(color)
                                .then(if (d in periodDays) Modifier.border(1.5.dp, pc.menstruation, RoundedCornerShape(3.dp)) else Modifier)
                                .then(if (d == sel) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp)) else Modifier)
                                .clickable(enabled = !d.isAfter(today)) { sel = d }
                        )
                    }
                }
            }
        }
        sel?.let { d ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.weekdayLong() + ": " + (unrest[d]?.let { "Unruhe Stufe $it" } ?: if (d in tracked) "ohne Unruhe" else "keine Einträge") +
                        if (d in periodDays) " · Periode" else "",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { onDay(d) }) { Text("Öffnen") }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(calm)); Text("ruhig", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            (1..5).forEach { Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(unrestColor(it))) }
            Text("Unruhe 1–5", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(11.dp).border(1.5.dp, pc.menstruation, RoundedCornerShape(2.dp))); Text("Periode", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Mehrere Zyklen übereinandergelegt: x = Zyklustag, y = Wert; Mittelwert hervorgehoben. */
@Composable
fun CycleOverlayChart(series: List<List<Double?>>, mean: List<Double?>, maxY: Double, color: Color, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val pc = LocalPhaseColors.current
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = max(mean.size, series.maxOfOrNull { it.size } ?: 0)
            if (n < 2) return@Canvas
            val step = size.width / (n - 1)
            fun y(v: Double) = size.height - (v / maxY).toFloat().coerceIn(0f, 1f) * size.height
            for (i in 0..4) drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1f)
            // Prämenstruelle Woche dezent markieren (letzte 7 Tage des mittleren Zyklus)
            drawRect(pc.luteal.copy(alpha = 0.08f), Offset(size.width - 7 * step, 0f), Size(7 * step, size.height))
            series.forEachIndexed { si, s ->
                val alpha = 0.18f + 0.3f * (si + 1) / series.size
                var prev: Offset? = null
                s.forEachIndexed { i, v ->
                    if (v == null) { prev = null; return@forEachIndexed }
                    val p = Offset(i * step, y(v))
                    prev?.let { drawLine(color.copy(alpha = alpha), it, p, 3f, StrokeCap.Round) }
                    prev = p
                }
            }
            var prev: Offset? = null
            mean.forEachIndexed { i, v ->
                if (v == null) { prev = null; return@forEachIndexed }
                val p = Offset(i * step, y(v))
                prev?.let { drawLine(color, it, p, 7f, StrokeCap.Round) }
                prev = p
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tag 1", style = MaterialTheme.typography.labelSmall)
            Text("Tag ${mean.size}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Daten für die Zyklus-Überlagerung: pro abgeschlossenem Zyklus ein Tageswert. */
fun overlaySeries(data: AppData, cycles: Int, value: (LocalDate) -> Double?): Pair<List<List<Double?>>, List<Double?>> {
    val complete = data.summary.cycles.filter { it.isComplete }.takeLast(cycles)
    if (complete.isEmpty()) return emptyList<List<Double?>>() to emptyList()
    val len = complete.mapNotNull { it.length }.let { l -> l.sorted()[l.size / 2] }.coerceIn(21, 40)
    val series = complete.map { c -> (0 until len).map { i -> c.start.plusDays(i.toLong()).takeIf { it.isBefore(c.nextStart) }?.let(value) } }
    val mean = (0 until len).map { i -> series.mapNotNull { it.getOrNull(i) }.takeIf { it.isNotEmpty() }?.average() }
    return series to mean
}
