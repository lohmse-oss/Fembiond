package de.fembiond.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.fembiond.app.FembiondApp
import de.fembiond.app.MainActivity
import de.fembiond.app.R
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.CurrentState
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.Phase
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.domain.daysBetween
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Phasenfarben für Bitmaps – mittlere Töne, auf hellem und dunklem Grund gut sichtbar. */
private fun phaseArgb(p: Phase): Int = when (p) {
    Phase.MENSTRUATION -> 0xFFD81B60.toInt()
    Phase.FOLLICULAR -> 0xFFE8894A.toInt()
    Phase.OVULATION -> 0xFF9A88D6.toInt()
    Phase.LUTEAL -> 0xFFA0569A.toInt()
}

private val Surface = ColorProvider(day = Color(0xFFFCF0F2), night = Color(0xFF271F24))
private val OnSurface = ColorProvider(day = Color(0xFF22191C), night = Color(0xFFEFDFE3))
private val OnSurfaceVariant = ColorProvider(day = Color(0xFF514347), night = Color(0xFFD5C2C7))
private val Primary = ColorProvider(day = Color(0xFFA0295B), night = Color(0xFFFFB1C8))
private val PrimaryContainer = ColorProvider(day = Color(0xFFA0295B), night = Color(0xFF7E2949))
private val Chip = ColorProvider(day = Color(0xFFF0E4E7), night = Color(0xFF3C3339))

private fun phaseColor(p: Phase) = ColorProvider(day = Color(phaseArgb(p)), night = Color(phaseArgb(p)))

/** Zustand, den das Widget anzeigt. */
data class WidgetState(
    val locked: Boolean,
    val current: CurrentState?,
    val phase: Phase?,
    val predictionText: String,
    val medName: String?,
    val medDue: Boolean,
    val medTaken: Boolean,
    val medPhaseId: Long,
    val medicationDays: Set<Int>,
    val deviation: Boolean,
    val nextEvent: String?,
) {
    companion object {
        private val dm = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMANY)

        fun from(d: AppData): WidgetState {
            val c = d.summary.current
            val override = d.settings.phaseOverride?.takeIf { d.settings.phaseOverrideCycleStart == c?.cycleStart }
            val today = d.today
            val pred = c?.prediction
            val text = when {
                c == null -> "Noch keine Periode erfasst"
                c.inPeriod -> "Periode läuft"
                pred!!.overdueDays > 0 -> "Periode überfällig (${pred.overdueDays} T.)"
                else -> {
                    val a = daysBetween(today, pred.from).coerceAtLeast(0)
                    val b = daysBetween(today, pred.to).coerceAtLeast(0)
                    when {
                        a == 0 && b == 0 -> "Periode heute erwartet"
                        a == b -> "Periode in $a T."
                        a == 0 -> "Periode ab heute"
                        else -> "Periode in $a–$b T."
                    }
                }
            }
            val med = d.medications.firstOrNull { it.activeOn(today) }
            return WidgetState(
                locked = d.settings.lockEnabled,
                current = c,
                phase = override ?: c?.phase,
                predictionText = text,
                medName = med?.name,
                medDue = med != null && CycleEngine.intakeDue(med, d.summary, today),
                medTaken = med != null && (med.id to today) in d.intakes,
                medPhaseId = med?.id ?: 0,
                medicationDays = d.medications.filter { it.activeOn(today) && it.schedule == ScheduleType.CYCLIC }
                    .flatMap { (it.cycleDayFrom ?: 0)..(it.cycleDayTo ?: -1) }.toSet(),
                deviation = (c?.prediction?.overdueDays ?: 0) > 0,
                nextEvent = d.events.filter { !it.date.isBefore(today) && daysBetween(today, it.date) <= 14 }.minByOrNull { it.date }?.let { e ->
                    val n = daysBetween(today, e.date)
                    e.displayName + " " + when (n) { 0 -> "heute"; 1 -> "morgen"; else -> "in $n T." }
                },
            )
        }
    }
}

class CycleWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, ROOMY, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as FembiondApp
        val data = withTimeoutOrNull(10_000) { app.repository.data.filterNotNull().first() }
        val state = data?.let { WidgetState.from(it) }
        val ring = state?.current?.takeIf { !state.locked }?.let { ringBitmap(it, state.medicationDays, 160) }
        val bar = state?.current?.takeIf { !state.locked }?.let { barBitmap(it, state.medicationDays, 600, 36) }
        provideContent { WidgetContent(context, state, ring, bar) }
    }

    companion object {
        val SMALL = DpSize(180.dp, 48.dp)
        val WIDE = DpSize(250.dp, 48.dp)
        val ROOMY = DpSize(320.dp, 48.dp)
        val TALL = DpSize(250.dp, 110.dp)

        suspend fun refresh(context: Context) = runCatching { CycleWidget().updateAll(context) }
    }
}

private fun openIntent(context: Context, route: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
    }

@Composable
private fun WidgetContent(context: Context, s: WidgetState?, ring: Bitmap?, bar: Bitmap?) {
    val size = LocalSize.current
    val tall = size.height >= 100.dp
    val roomy = size.width >= 300.dp
    val title = TextStyle(color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val sub = TextStyle(color = OnSurfaceVariant, fontSize = 12.sp)

    Column(
        GlanceModifier.fillMaxSize().background(Surface).cornerRadius(24.dp).padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity(openIntent(context, null))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(GlanceModifier.fillMaxWidth().let { if (tall) it else it.fillMaxHeight() }, verticalAlignment = Alignment.CenterVertically) {
            when {
                s == null -> Text("Fembiond wird geladen …", style = sub)
                s.locked || s.current == null -> {
                    Image(ImageProvider(R.drawable.ic_w_ring), null, GlanceModifier.size(30.dp))
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("Fembiond", style = title, maxLines = 1)
                        Text(if (s.locked) "Gesperrt – zum Öffnen tippen" else s.predictionText, style = sub, maxLines = 1)
                    }
                }
                else -> {
                    val c = s.current
                    val phase = s.phase ?: c.phase
                    Box(GlanceModifier.size(46.dp), contentAlignment = Alignment.Center) {
                        if (ring != null) Image(ImageProvider(ring), "Zyklusring", GlanceModifier.fillMaxSize())
                        Text("${c.cycleDay}", style = TextStyle(color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size(9.dp).background(phaseColor(phase)).cornerRadius(5.dp)) {}
                            Spacer(GlanceModifier.width(5.dp))
                            Text(phase.label, style = title, maxLines = 1)
                        }
                        Text("Tag ${c.cycleDay} · ${s.predictionText}", style = sub.copy(color = if (s.deviation) Primary else OnSurfaceVariant), maxLines = 1)
                    }
                    if (s.medName != null && (s.medDue || s.medTaken)) {
                        Spacer(GlanceModifier.width(6.dp))
                        val icon = if (s.medTaken) R.drawable.ic_w_check else R.drawable.ic_w_capsule
                        val action = if (s.medTaken) actionStartActivity(openIntent(context, Routes.MEDS))
                        else actionRunCallback<TakeIntakeAction>(actionParametersOf(TakeIntakeAction.PHASE to s.medPhaseId))
                        Box(
                            GlanceModifier.size(36.dp).background(Chip).cornerRadius(18.dp).clickable(action),
                            contentAlignment = Alignment.Center,
                        ) { Image(ImageProvider(icon), if (s.medTaken) "${s.medName} eingenommen" else "${s.medName}: Einnahme abhaken", GlanceModifier.size(20.dp)) }
                    }
                    if (roomy) {
                        Spacer(GlanceModifier.width(6.dp))
                        Box(
                            GlanceModifier.size(36.dp).background(PrimaryContainer).cornerRadius(18.dp)
                                .clickable(actionStartActivity(openIntent(context, Routes.entry()))),
                            contentAlignment = Alignment.Center,
                        ) { Image(ImageProvider(R.drawable.ic_w_add), "Eintrag", GlanceModifier.size(22.dp)) }
                    }
                }
            }
        }
        if (tall && s != null && !s.locked && s.current != null) {
            Spacer(GlanceModifier.height(4.dp))
            if (bar != null) Image(ImageProvider(bar), "Zyklusverlauf", GlanceModifier.fillMaxWidth().height(12.dp))
            if (s.nextEvent != null) Text("Anstehend: ${s.nextEvent}", style = sub, maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp))
            Spacer(GlanceModifier.height(4.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                QuickButton(context, "Befinden", R.drawable.ic_w_add, Routes.entry(), PrimaryContainer, ColorProvider(Color.White, Color.White), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                QuickButton(context, "Unruhe", R.drawable.ic_w_pulse, Routes.unrest(), Chip, OnSurface, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun QuickButton(
    context: Context, label: String, icon: Int, route: String,
    bg: androidx.glance.unit.ColorProvider, fg: androidx.glance.unit.ColorProvider, modifier: GlanceModifier,
) {
    Row(
        modifier.background(bg).cornerRadius(16.dp).padding(vertical = 6.dp, horizontal = 10.dp)
            .clickable(actionStartActivity(openIntent(context, route))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(ImageProvider(icon), null, GlanceModifier.size(18.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(label, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

/** Abhaken der Einnahme direkt im Widget. */
class TakeIntakeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[PHASE] ?: return
        val app = context.applicationContext as FembiondApp
        app.repository.setIntake(id, LocalDate.now(), true)
        CycleWidget.refresh(context)
    }

    companion object {
        val PHASE = ActionParameters.Key<Long>("phaseId")
    }
}

class CycleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CycleWidget()
}

// ---------- Bitmaps ----------

private fun phaseOfDay(c: CurrentState, d: Int): Phase {
    val length = max(c.expectedLength, d)
    val pLen = if (c.inPeriod) max(c.cycleDay, 1) else min(5, c.expectedLength)
    return CycleEngine.phaseFor(d, length, pLen)
}

fun ringBitmap(c: CurrentState, medDays: Set<Int>, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val stroke = px * 0.13f
    val r = px / 2f - stroke * 0.8f
    val rect = RectF(px / 2f - r, px / 2f - r, px / 2f + r, px / 2f + r)
    val total = max(c.expectedLength, c.cycleDay)
    val sweep = 360f / total
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
    for (d in 1..total) {
        val col = phaseArgb(phaseOfDay(c, d))
        p.color = if (d <= c.cycleDay) col else (col and 0x00FFFFFF) or 0x55000000
        cv.drawArc(rect, -90f + (d - 1) * sweep + 0.6f, sweep - 1.2f, false, p)
    }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8C4A1F.toInt() }
    medDays.filter { it in 1..total }.forEach { d ->
        val a = Math.toRadians((-90f + (d - 0.5f) * sweep).toDouble())
        val rr = r - stroke * 0.95f
        cv.drawCircle(px / 2f + (rr * cos(a)).toFloat(), px / 2f + (rr * sin(a)).toFloat(), stroke * 0.16f, dot)
    }
    val a = Math.toRadians((-90f + (c.cycleDay - 0.5f) * sweep).toDouble())
    val cx = px / 2f + (r * cos(a)).toFloat()
    val cy = px / 2f + (r * sin(a)).toFloat()
    cv.drawCircle(cx, cy, stroke * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    cv.drawCircle(cx, cy, stroke * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22191C.toInt(); style = Paint.Style.STROKE; strokeWidth = stroke * 0.2f })
    return bmp
}

/** Horizontaler Zyklusverlauf: Phasensegmente, heute als Marker, Einnahmetage als Punkte. */
fun barBitmap(c: CurrentState, medDays: Set<Int>, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val total = max(c.expectedLength, c.cycleDay)
    val seg = w.toFloat() / total
    val barTop = h * 0.3f
    val barBottom = h * 0.7f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    for (d in 1..total) {
        val col = phaseArgb(phaseOfDay(c, d))
        p.color = if (d <= c.cycleDay) col else (col and 0x00FFFFFF) or 0x55000000
        cv.drawRoundRect(RectF((d - 1) * seg + 1, barTop, d * seg - 1, barBottom), 4f, 4f, p)
    }
    p.color = 0xFF8C4A1F.toInt()
    medDays.filter { it in 1..total }.forEach { d -> cv.drawCircle((d - 0.5f) * seg, h * 0.9f, h * 0.08f, p) }
    val x = (c.cycleDay - 0.5f) * seg
    p.color = 0xFF22191C.toInt()
    cv.drawRoundRect(RectF(x - 3, 0f, x + 3, h * 0.8f), 3f, 3f, p)
    return bmp
}
