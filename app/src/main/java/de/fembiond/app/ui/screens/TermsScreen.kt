package de.fembiond.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.data.TermInfo
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.domain.TermSimilarity
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.icon
import kotlinx.coroutines.launch

private enum class TermFilter(val label: String) { ALL("Alle"), UNUSED("Nie genutzt"), RARE("Selten (bis 2×)"), HIDDEN("Ausgeblendet") }

/** Vorschlagslisten pflegen: umbenennen, Kategorie ändern, ausblenden, zusammenführen, löschen. */
@Composable
fun TermsScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by rememberSaveable { mutableStateOf(Category.BODY) }
    var filter by rememberSaveable { mutableStateOf(TermFilter.ALL) }
    var editing by remember { mutableStateOf<TermInfo?>(null) }
    var merging by remember { mutableStateOf<List<TermInfo>?>(null) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }

    val all = data.terms.filter { it.category == cat && it.label != ScaleTerms.SLEEP_QUALITY && it.label != ScaleTerms.ENERGY }
    val terms = all.filter {
        when (filter) {
            TermFilter.ALL -> true
            TermFilter.UNUSED -> it.useCount == 0
            TermFilter.RARE -> it.useCount <= 2
            TermFilter.HIDDEN -> it.hidden
        }
    }.sortedByDescending { it.score }
    val dupes = remember(all) {
        val visible = all.filter { !it.hidden }
        TermSimilarity.duplicates(visible.map { it.label to it.category }).mapNotNull { (a, b) ->
            val ta = visible.first { it.label == a.first }; val tb = visible.first { it.label == b.first }
            listOf(ta, tb)
        }
    }
    fun chosen() = data.terms.filter { it.id in selected }

    Screen(
        title = "Begriffe verwalten", nav = nav,
        actions = {
            IconButton(onClick = { selectMode = !selectMode; selected.clear() }) { Icon(Icons.Rounded.Checklist, if (selectMode) "Auswahl beenden" else "Mehrere auswählen") }
        },
        bottomBar = {
            if (selectMode && selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val l = chosen(); vm.run { l.forEach { updateTerm(it, hidden = true) } }; selected.clear() }, modifier = Modifier.weight(1f)) { Text("Ausblenden") }
                    OutlinedButton(onClick = {
                        val l = chosen()
                        scope.launch {
                            val done = l.count { vm.repo.deleteTermIfUnused(it) }
                            val kept = l.size - done
                            Toast.makeText(context, "$done gelöscht" + if (kept > 0) " · $kept werden noch verwendet (ausblenden oder zusammenführen)" else "", Toast.LENGTH_LONG).show()
                        }
                        selected.clear()
                    }, modifier = Modifier.weight(1f)) { Text("Löschen") }
                    FilledTonalButton(onClick = { merging = chosen() }, enabled = selected.size >= 2, modifier = Modifier.weight(1f)) { Text("Zusammen­führen") }
                }
            }
        },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { c ->
                FilterChip(cat == c, { cat = c; selected.clear() }, { Text(c.shortLabel) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp)) })
            }
        }
        ChipFlow { TermFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, { Text(f.label) }) } }
        HintText(
            "Ausgeblendete Begriffe erscheinen nicht mehr als Vorschlag, bleiben aber in der Statistik. Löschen geht nur bei Begriffen, die nie verwendet wurden – " +
                "verwendete lassen sich mit einem anderen Begriff zusammenführen. Oben rechts: mehrere auswählen.",
        )

        if (dupes.isNotEmpty() && filter == TermFilter.ALL) SectionCard(title = "Mögliche Doppelte", icon = Icons.Rounded.ContentCopy, container = MaterialTheme.colorScheme.tertiaryContainer) {
            dupes.forEach { pair ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("„${pair[0].label}“ und „${pair[1].label}“", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { merging = pair }) { Text("Zusammenführen") }
                }
            }
        }

        SectionCard(title = cat.label, icon = cat.icon()) {
            terms.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    if (selectMode) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) } else editing = t
                }) {
                    if (selectMode) Checkbox(t.id in selected, { on -> if (on) selected.add(t.id) else selected.remove(t.id) })
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(t.label, style = MaterialTheme.typography.bodyLarge, color = if (t.hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${t.useCount}× genutzt · " + (if (t.builtIn) "Startbegriff" else "eigener Begriff") + if (t.hidden) " · ausgeblendet" else " · in der Vorauswahl",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!selectMode) IconButton(onClick = { vm.run { updateTerm(t, hidden = !t.hidden) } }) {
                        Icon(if (t.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (t.hidden) "Einblenden" else "Ausblenden")
                    }
                }
            }
            if (terms.isEmpty()) HintText("Keine Begriffe für diese Auswahl.")
        }
    }

    editing?.let { t -> TermEditDialog(vm, data, t, onMerge = { editing = null; merging = it }, onClose = { editing = null }) }
    merging?.let { list -> MergeDialog(list, onDismiss = { merging = null }) { keep ->
        val others = list.filter { it.id != keep.id }
        vm.run { others.forEach { mergeTerms(it, keep) } }
        Toast.makeText(context, "In „${keep.label}“ zusammengeführt", Toast.LENGTH_SHORT).show()
        selected.clear(); merging = null
    } }
}

@Composable
private fun TermEditDialog(vm: AppViewModel, data: AppData, t: TermInfo, onMerge: (List<TermInfo>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember(t) { mutableStateOf(t.label) }
    var c by remember(t) { mutableStateOf(t.category) }
    var usage by remember(t) { mutableStateOf<Int?>(null) }
    LaunchedEffect(t) { usage = vm.repo.termUsage(t) }
    val similar = remember(t, data.terms) {
        val pool = data.terms.filter { it.id != t.id && (it.category == t.category || (it.category.isSymptom && t.category.isSymptom)) }
        TermSimilarity.similar(t.label, pool.map { it.label to it.category }, max = 4)
            .mapNotNull { s -> pool.firstOrNull { it.label == s.label && it.category == s.category } }
    }
    val sameCat = data.terms.filter { it.id != t.id && it.category == t.category && it.label != ScaleTerms.SLEEP_QUALITY && it.label != ScaleTerms.ENERGY }
        .sortedByDescending { it.score }
    var pickAll by remember(t) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Begriff bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipFlow { Category.entries.forEach { k -> FilterChip(c == k, { c = k }, { Text(k.shortLabel) }) } }
                usage?.let { HintText(if (it == 0) "Wird in keinem Eintrag verwendet." else "Wird in $it Einträgen verwendet.") }
                Text("Zusammenführen mit …", style = MaterialTheme.typography.labelLarge)
                HintText("Alle Einträge wandern zum gewählten Begriff; der alte Name wird weiter erkannt.")
                ChipFlow {
                    (if (pickAll) sameCat else similar.ifEmpty { sameCat.take(6) }).forEach { o ->
                        FilterChip(false, { onMerge(listOf(t, o)) }, { Text(o.label) }, leadingIcon = { Icon(Icons.Rounded.CallMerge, null, Modifier.size(16.dp)) })
                    }
                    if (!pickAll && sameCat.size > 6) TextButton(onClick = { pickAll = true }) { Text("Alle zeigen") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.run { updateTerm(t, label, c) }; onClose() }) { Text("Speichern") } },
        dismissButton = {
            TextButton(enabled = usage == 0, onClick = {
                scope.launch {
                    if (vm.repo.deleteTermIfUnused(t)) Toast.makeText(context, "„${t.label}“ gelöscht", Toast.LENGTH_SHORT).show()
                }
                onClose()
            }) { Text("Löschen") }
        },
    )
}

@Composable
private fun MergeDialog(list: List<TermInfo>, onDismiss: () -> Unit, onMerge: (TermInfo) -> Unit) {
    var keep by remember(list) { mutableStateOf(list.maxBy { it.useCount }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zusammenführen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText("Welcher Begriff soll bleiben? Die Einträge der anderen werden übernommen, ihre Namen werden weiter erkannt.")
                list.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { keep = t }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(keep.id == t.id, { keep = t })
                        Text("${t.label} (${t.useCount}×, ${t.category.shortLabel})")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMerge(keep) }) { Text("Zusammenführen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
