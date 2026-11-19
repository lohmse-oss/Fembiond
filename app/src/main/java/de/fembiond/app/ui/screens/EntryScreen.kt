package de.fembiond.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.data.Selection
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.domain.Valence
import de.fembiond.app.ui.AddChip
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.IntensitySelector
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.TermChip
import de.fembiond.app.ui.dm
import de.fembiond.app.ui.icon
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private typealias Key = Pair<String, Category>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(vm: AppViewModel, data: AppData, nav: NavController, day: LocalDate?, noteMode: Boolean, initialText: String = "") {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(initialText) }
    var usedVoice by rememberSaveable { mutableStateOf(false) }
    var date by remember { mutableStateOf(day ?: data.today) }
    val selections = remember { mutableStateMapOf<Key, Selection>() }
    val autoKeys = remember { mutableStateListOf<Key>() }
    val rejected = remember { mutableStateListOf<Key>() }
    var unknown by remember { mutableStateOf(emptyList<String>()) }
    var tab by rememberSaveable { mutableStateOf(Category.MIND) }
    var sleep by rememberSaveable { mutableStateOf<Int?>(null) }
    var energy by rememberSaveable { mutableStateOf<Int?>(null) }
    var newTermFor by remember { mutableStateOf<Pair<String, Category>?>(null) }
    var showDate by remember { mutableStateOf(false) }

    // Automatische Erkennung beim Tippen/Sprechen
    LaunchedEffect(text) {
        delay(350)
        val result = data.parser.parse(text)
        val found = result.terms.map { it.label to it.category }.toSet()
        autoKeys.filter { it !in found }.forEach { k -> selections.remove(k); autoKeys.remove(k) }
        result.terms.forEach { t ->
            val k = t.label to t.category
            if (k in rejected) return@forEach
            val existing = selections[k]
            if (existing == null) {
                selections[k] = Selection(t.label, t.category, t.intensity, t.valence)
                autoKeys.add(k)
            } else if (k in autoKeys) {
                selections[k] = existing.copy(intensity = t.intensity ?: existing.intensity, valence = t.valence ?: existing.valence)
            }
        }
        unknown = result.unknown
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                text = if (text.isBlank()) spoken else "$text. $spoken"
                usedVoice = true
            }
        }
    }

    fun save() {
        val list = selections.values.toMutableList()
        sleep?.let { list += Selection(ScaleTerms.SLEEP_QUALITY, Category.SLEEP_ENERGY, it) }
        energy?.let { list += Selection(ScaleTerms.ENERGY, Category.SLEEP_ENERGY, it) }
        val time = if (date == data.today) LocalDateTime.now() else date.atTime(LocalTime.NOON)
        val backfilled = date.isBefore(data.today.minusDays(2))
        val source = if (usedVoice) "voice" else if (text.isNotBlank()) "text" else "chips"
        vm.run { saveEntry(time, text, list, source, backfilled) }
        nav.popBackStack()
    }

    val count = selections.size + listOfNotNull(sleep, energy).size
    Screen(
        title = if (noteMode) "Notiz" else "Wie geht es dir?",
        nav = nav,
        actions = { IconButton(onClick = { nav.navigate(de.fembiond.app.Routes.guide(if (noteMode) "note" else "entry")) }) { Icon(androidx.compose.material.icons.Icons.Rounded.HelpOutline, "Hilfe") } },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDate = true }) {
                        Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (date == data.today) "Heute" else date.dm())
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = ::save, enabled = count > 0 || text.isNotBlank(), modifier = Modifier.height(52.dp), shapes = de.fembiond.app.ui.morphShapes()) {
                        Text(if (count > 0) "Speichern ($count)" else "Speichern")
                    }
                }
            }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = if (noteMode) 160.dp else 96.dp),
            placeholder = {
                Text(
                    if (noteMode) "z. B. „Heute war mein letzter Arbeitstag, abends Streit, kaum geschlafen“"
                    else "Eintippen oder einsprechen, z. B. „ziemlich müde, Kopfweh, innere Unruhe Stärke 3“"
                )
            },
            trailingIcon = {
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Wie geht es dir?")
                    }
                    runCatching { speech.launch(intent) }.onFailure {
                        android.widget.Toast.makeText(context, "Spracheingabe nicht verfügbar", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Rounded.Mic, "Spracheingabe", tint = MaterialTheme.colorScheme.primary) }
            },
            shape = MaterialTheme.shapes.medium,
        )

        if (selections.isNotEmpty()) {
            SectionCard(title = "Erfasst – bitte prüfen", icon = Icons.Rounded.AutoAwesome) {
                selections.values.sortedBy { it.category.ordinal }.forEach { sel ->
                    val k = sel.label to sel.category
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(sel.category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sel.label, style = MaterialTheme.typography.titleSmall)
                                Text(sel.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                selections.remove(k)
                                if (k in autoKeys) { autoKeys.remove(k); rejected.add(k) }
                            }) { Icon(Icons.Rounded.Close, "Entfernen") }
                        }
                        if (sel.category != Category.BLEEDING) {
                            de.fembiond.app.ui.StrengthSelector(sel.intensity, sel.sudden, { selections[k] = sel.copy(intensity = it) }, { selections[k] = sel.copy(sudden = it) }, Modifier.padding(start = 28.dp))
                        }
                        if (sel.category == Category.LIFE) {
                            Row(Modifier.padding(start = 28.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Valence.entries.forEach { v ->
                                    FilterChip(selected = sel.valence == v, onClick = { selections[k] = sel.copy(valence = if (sel.valence == v) null else v) }, label = { Text(v.label) })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (unknown.isNotEmpty()) {
            SectionCard(title = "Nicht erkannt", icon = Icons.Rounded.HelpOutline) {
                HintText("Tippe ein Wort an, um es als eigenen Begriff zu speichern oder einem vorhandenen zuzuordnen. Die App merkt sich das.")
                ChipFlow { unknown.forEach { w -> AddChip(w) { newTermFor = w to tab } } }
            }
        }

        // Kategorien mit Vorschlägen
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.filter { it.isDaily }.forEach { c ->
                FilterChip(
                    selected = tab == c,
                    onClick = { tab = c },
                    label = { Text(c.shortLabel) },
                    leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) },
                )
            }
        }
        SectionCard(title = tab.label, icon = tab.icon()) {
            if (tab == Category.SLEEP_ENERGY) {
                Text("Schlafqualität", style = MaterialTheme.typography.labelLarge)
                IntensitySelector(sleep, { sleep = it })
                Text("Energie", style = MaterialTheme.typography.labelLarge)
                IntensitySelector(energy, { energy = it })
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            ChipFlow {
                data.suggestions(tab).forEach { t ->
                    val k = t.label to t.category
                    val sel = selections[k]
                    TermChip(t.label, sel != null, sel?.intensity, onClick = {
                        if (sel != null) { selections.remove(k); autoKeys.remove(k) } else {
                            selections[k] = Selection(t.label, t.category, valence = t.valence); rejected.remove(k)
                        }
                    })
                }
                AddChip { newTermFor = "" to tab }
            }
        }
        if (date != data.today) HintText("Eintrag für ${date.dm()}" + if (date.isBefore(data.today.minusDays(2))) " – wird als nachgetragen markiert." else "")
    }

    newTermFor?.let { (word, cat) ->
        NewTermDialog(
            data = data,
            initial = word,
            initialCategory = cat,
            onDismiss = { newTermFor = null },
            onNew = { label, c ->
                val k = label to c
                selections[k] = Selection(label, c)
                if (word.isNotBlank()) unknown = unknown - word
                if (word.isNotBlank() && word.lowercase() != label.lowercase()) vm.run { learnSynonym(word, label, c) }
                newTermFor = null
            },
            onMap = { label, c ->
                selections[label to c] = Selection(label, c)
                unknown = unknown - word
                vm.run { learnSynonym(word, label, c) }
                newTermFor = null
            },
        )
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(data.today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}

/** Dialog: unbekanntes Wort als neuen Begriff anlegen oder einem vorhandenen zuordnen. */
@Composable
fun NewTermDialog(
    data: AppData,
    initial: String,
    initialCategory: Category,
    categories: List<Category> = Category.entries.filter { it.isDaily },
    onDismiss: () -> Unit,
    onNew: (String, Category) -> Unit,
    onMap: ((String, Category) -> Unit)? = null,
) {
    var label by remember { mutableStateOf(initial.replaceFirstChar { it.uppercase() }) }
    var cat by remember { mutableStateOf(if (initialCategory in categories) initialCategory else categories.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.isBlank()) "Eigener Begriff" else "„$initial“ zuordnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, label = { Text("Begriff") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Kategorie", style = MaterialTheme.typography.labelLarge)
                ChipFlow {
                    categories.forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.shortLabel) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp)) })
                    }
                }
                if (onMap != null && initial.isNotBlank()) {
                    Text("… oder bedeutet dasselbe wie:", style = MaterialTheme.typography.labelLarge)
                    ChipFlow {
                        data.suggestions(cat).take(14).forEach { t -> TermChip(t.label, false, null, { onMap(t.label, t.category) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (label.isNotBlank()) onNew(label.trim(), cat) }) { Text("Neu anlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
