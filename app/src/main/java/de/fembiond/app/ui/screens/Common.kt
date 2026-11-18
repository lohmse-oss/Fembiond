package de.fembiond.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

/**
 * Sprungmarken: Karten mit Titel melden ihre Position (im Fenster), die Seite zeigt sie als Leiste.
 * [anchors] Titel → y-Position; [viewportTop] y-Position des Scrollbereichs.
 */
class JumpRegistry {
    /** Positionen werden ohne Neuzeichnen aktualisiert; nur die Menge der Titel ist beobachtbar. */
    val anchors = HashMap<String, Float>()
    val titles: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    var viewportTop = 0f
    /** Wird erhöht, sobald eine Karte erstmals positioniert wurde – sortiert die Leiste neu. */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
}

val LocalJumpRegistry = compositionLocalOf<JumpRegistry?> { null }

/** Meldet eine Karte als Sprungmarke (nur wenn die Seite Sprungmarken anbietet). */
@Composable
fun Modifier.jumpAnchor(title: String?): Modifier {
    val reg = LocalJumpRegistry.current ?: return this
    if (title == null) return this
    DisposableEffect(title) {
        reg.titles[title] = true
        onDispose { reg.anchors.remove(title); reg.titles.remove(title) }
    }
    return this.onGloballyPositioned { if (reg.anchors.put(title, it.positionInRoot().y) == null) reg.version++ }
}

/** „Nach oben“-Taste, erscheint nach etwas Scrollen. */
@Composable
fun ScrollTopButton(scroll: ScrollState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val visible by remember { derivedStateOf { scroll.value > 900 } }
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        SmallFloatingActionButton(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); scope.launch { scroll.animateScrollTo(0) } },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) { Icon(Icons.Rounded.KeyboardArrowUp, "Nach oben") }
    }
}

/** Waagerechte Leiste mit Sprungmarken in Reihenfolge der Seite. */
@Composable
fun JumpBar(reg: JumpRegistry, scroll: ScrollState, labels: (String) -> String = { it }) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    @Suppress("UNUSED_VARIABLE") val v = reg.version
    val entries = reg.titles.keys.toList().sortedBy { reg.anchors[it] ?: Float.MAX_VALUE }
    if (entries.size < 3) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Bookmarks, "Springen zu", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            entries.forEach { title ->
                SuggestionChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val y = reg.anchors[title] ?: return@SuggestionChip
                        val target = (scroll.value + (y - reg.viewportTop) - 8f).toInt().coerceAtLeast(0)
                        scope.launch { scroll.animateScrollTo(target) }
                    },
                    label = { Text(labels(title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(50),
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    nav: NavController?,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scroll: Boolean = true,
    /** Sprungmarken-Leiste aus den Kartentiteln anzeigen. */
    jumpBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val registry = remember { JumpRegistry() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (nav != null) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (jumpBar && scroll) JumpBar(registry, scrollState) { shortLabel(it) }
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = { if (scroll) ScrollTopButton(scrollState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val mod = Modifier.fillMaxSize().padding(pad)
        CompositionLocalProvider(LocalJumpRegistry provides if (jumpBar) registry else null) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    (if (scroll) mod.onGloballyPositioned { registry.viewportTop = it.positionInRoot().y }.verticalScroll(scrollState) else mod)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    content()
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

/** Kurze Beschriftung für Sprungmarken. */
fun shortLabel(title: String): String = when {
    title.startsWith("Vor der Periode") -> "Vorher/Nachher"
    title.startsWith("PMS") -> "PMS"
    title.startsWith("Unruhe rund um") -> "Ereignisse"
    title.startsWith("Unruhe nach") -> "Unruhe"
    title.startsWith("Zykluslänge") -> "Zykluslänge"
    title.startsWith("Wann tritt") -> "Heatmap"
    title.startsWith("Vorher / Nachher Medikation") -> "Medikation"
    title.startsWith("Verläufe") -> "Verläufe"
    title.startsWith("Zyklen übereinander") -> "Überlagerung"
    title.length > 22 -> title.take(20) + "…"
    else -> title
}

@Composable
fun LabeledRow(label: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        content()
    }
}

val ContentPadding = PaddingValues(horizontal = 16.dp)
