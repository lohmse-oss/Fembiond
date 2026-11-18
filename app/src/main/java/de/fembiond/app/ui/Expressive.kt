package de.fembiond.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fembiond.app.domain.UnrestLevel

/** Schaltflächen-Form, die sich beim Drücken verändert (Material 3 Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun morphShapes(): ButtonShapes = ButtonDefaults.shapes()

/** Ausdrucksstarke Ladeanzeige. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallLoading() { LoadingIndicator(Modifier.size(36.dp)) }

/** Unruhe-Stufen 1–5 als verbundene Tastengruppe, darunter die Beschreibung der gewählten Stufe. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnrestLevelPicker(level: Int?, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val darkBg = MaterialTheme.colorScheme.background.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } < 0.3f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            UnrestLevel.entries.forEachIndexed { i, u ->
                val shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    UnrestLevel.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                val c = unrestColor(u.level)
                ToggleButton(
                    checked = level == u.level,
                    onCheckedChange = {
                        haptic.performHapticFeedback(if (u.level >= 4) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentTick)
                        onChange(u.level)
                    },
                    shapes = shapes,
                    colors = ToggleButtonDefaults.colors(
                        containerColor = c.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = c,
                        checkedContentColor = if (darkBg) Color(0xFF22191C) else Color.White,
                    ),
                    modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "Stufe ${u.level}: ${u.label}" },
                ) { Text("${u.level}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            }
        }
        AnimatedContent(level, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "level") { l ->
            val u = l?.let { UnrestLevel.of(it) }
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = if (u != null) unrestColor(u.level).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (u != null) {
                        LevelBadge(u.level)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(u.label, style = MaterialTheme.typography.titleSmall)
                            Text(u.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("1 leicht · 2 spürbar · 3 stark · 4 sehr stark · 5 Panikattacke", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
