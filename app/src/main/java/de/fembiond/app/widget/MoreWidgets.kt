package de.fembiond.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.fembiond.app.FembiondApp
import de.fembiond.app.R
import de.fembiond.app.Routes
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** 2×2: großer Zyklusring mit Zyklustag, Phase und Prognose. */
class RingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as FembiondApp
        val data = withTimeoutOrNull(10_000) { app.repository.data.filterNotNull().first() }
        val state = data?.let { WidgetState.from(it) }
        val ring = state?.current?.takeIf { !state.locked }?.let { ringBitmap(it, state.medicationDays, 360) }
        provideContent { RingContent(context, state, ring) }
    }
}

@Composable
private fun RingContent(context: Context, s: WidgetState?, ring: Bitmap?) {
    Box(
        GlanceModifier.fillMaxSize().background(Surface).cornerRadius(28.dp).padding(10.dp)
            .clickable(actionStartActivity(openIntent(context, null))),
        contentAlignment = Alignment.Center,
    ) {
        if (s == null || s.locked || s.current == null || ring == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(R.drawable.ic_w_ring), null, GlanceModifier.size(40.dp))
                Spacer(GlanceModifier.height(6.dp))
                Text(if (s?.locked == true) "Gesperrt" else "Fembiond", style = TextStyle(color = OnSurface, fontSize = 13.sp))
            }
        } else {
            val c = s.current
            val phase = s.phase ?: c.phase
            Image(ImageProvider(ring), "Zyklusring", GlanceModifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tag", style = TextStyle(color = OnSurfaceVariant, fontSize = 11.sp))
                Text("${c.cycleDay}", style = TextStyle(color = OnSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(8.dp).background(phaseColor(phase)).cornerRadius(4.dp)) {}
                    Spacer(GlanceModifier.width(4.dp))
                    Text(phase.label.removeSuffix("phase"), style = TextStyle(color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                Text(s.predictionText.replace("Periode ", ""), style = TextStyle(color = OnSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

/** 1×1: Schnelltaste „Unruhe erfassen“. Zeigt keine Gesundheitsdaten. */
class UnrestWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(PrimaryContainer).cornerRadius(24.dp).padding(6.dp)
                    .clickable(actionStartActivity(openIntent(context, Routes.unrest()))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.ic_w_waves), "Unruhe erfassen", GlanceModifier.size(28.dp))
                Text("Unruhe", style = TextStyle(color = androidx.glance.color.ColorProvider(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.White), fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

class RingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RingWidget() }
class UnrestWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = UnrestWidget() }
