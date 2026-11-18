package de.fembiond.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.data.TermInfo
import de.fembiond.app.data.ThemeMode
import de.fembiond.app.domain.Category
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.icon
import de.fembiond.app.ui.short

@Composable
fun MoreScreen(data: AppData, nav: NavController) {
    Screen(title = "Mehr", nav = null) {
        SectionCard {
            MoreItem(Icons.Rounded.Event, "Ereignisse", "Feiern, Essen gehen, Konzerte planen und auswerten") { nav.navigate(Routes.EVENTS) }
            HorizontalDivider()
            MoreItem(Icons.Rounded.Medication, "Medikation", "Behandlungsphasen, Einnahme, Neubefund") { nav.navigate(Routes.MEDS) }
            HorizontalDivider()
            MoreItem(Icons.Rounded.History, "Historie & Perioden", "Vergangene Perioden und Unruhe nachtragen") { nav.navigate(Routes.HISTORY) }
            HorizontalDivider()
            MoreItem(Icons.Rounded.IosShare, "Export & Sicherung", "KI-Export, Arztbericht, Sicherung") { nav.navigate(Routes.EXPORT) }
            HorizontalDivider()
            MoreItem(Icons.Rounded.Quiz, "GAD-7 Angst-Check", data.gad.lastOrNull()?.let { "Zuletzt ${it.date.short()}: ${it.score} Punkte" } ?: "Noch nicht ausgefüllt") { nav.navigate(Routes.GAD) }
            HorizontalDivider()
            MoreItem(Icons.AutoMirrored.Rounded.Label, "Begriffe verwalten", "Umbenennen, Kategorie ändern, ausblenden") { nav.navigate(Routes.TERMS) }
            HorizontalDivider()
            MoreItem(Icons.Rounded.Settings, "Einstellungen", "Darstellung, App-Sperre, Erinnerungen, Grunddaten") { nav.navigate(Routes.SETTINGS) }
        }
        SectionCard {
            MoreItem(Icons.AutoMirrored.Rounded.MenuBook, "Anleitung", "So funktioniert Fembiond – Schritt für Schritt") { nav.navigate(Routes.guide()) }
            HorizontalDivider()
            MoreItem(Icons.Rounded.Info, "Über Fembiond", "Version ${de.fembiond.app.BuildConfig.VERSION_NAME}, Neuerungen, Updates, Datenschutz") { nav.navigate(Routes.ABOUT) }
        }
        HintText("Fembiond speichert alle Daten verschlüsselt und ausschließlich auf diesem Gerät. Die App stellt keine Diagnosen.")
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    Screen(title = "Historie & Perioden", nav = nav) {
        HistoryEditor(vm, data)
        PanicBackfill(vm, data)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    val s = data.settings
    val context = LocalContext.current
    val canLock = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS

    Screen(title = "Einstellungen", nav = nav) {
        SectionCard(title = "Darstellung") {
            ThemeMode.entries.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.run { updateSettings { it.copy(theme = m) } } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(s.theme == m, { vm.run { updateSettings { it.copy(theme = m) } } }); Text(m.label)
                }
            }
        }
        SectionCard(title = "Datenschutz") {
            LabeledRow("App-Sperre (Fingerabdruck/PIN)") {
                Switch(s.lockEnabled, { on -> vm.run { updateSettings { it.copy(lockEnabled = on) } } }, enabled = canLock || s.lockEnabled)
            }
            if (!canLock) HintText("Richte zuerst eine Displaysperre auf dem Gerät ein.")
        }
        SectionCard(title = "Erinnerungen") {
            LabeledRow("Einnahme- und Check-Erinnerung") {
                Switch(s.reminderEnabled, { on -> vm.run { updateSettings { it.copy(reminderEnabled = on) } } })
            }
            Stepper("Uhrzeit", s.reminderHour, 6..23, "Uhr") { h -> vm.run { updateSettings { it.copy(reminderHour = h) } } }
            HintText("Erinnert an fällige Einnahmen laut Schema (verschiebt sich mit dem Zyklus), an Ereignisse am Vortag und an die Fragebögen.")
            Stepper("GAD-7 alle", s.gadIntervalDays, 7..60, "Tage") { d -> vm.run { updateSettings { it.copy(gadIntervalDays = d) } } }
            Stepper("MRS alle", s.mrsIntervalDays, 14..90, "Tage") { d -> vm.run { updateSettings { it.copy(mrsIntervalDays = d) } } }
            LabeledRow("Wochenrückblick am Sonntag") {
                Switch(s.weeklyReview, { on -> vm.run { updateSettings { it.copy(weeklyReview = on) } } })
            }
        }
        SectionCard(title = "Über dich & Zyklus") {
            ProfileFields(vm, data)
            HorizontalDivider()
            Stepper("Typische Zykluslänge", s.typicalCycle, 18..60, "Tage") { v -> vm.run { updateSettings { it.copy(typicalCycle = v) } } }
            Stepper("Typische Periodenlänge", s.typicalPeriod, 1..12, "Tage") { v -> vm.run { updateSettings { it.copy(typicalPeriod = v) } } }
            HintText("Diese Werte werden nur genutzt, solange zu wenige echte Zyklen erfasst sind.")
        }
    }
}

@Composable
fun TermsScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    var cat by remember { mutableStateOf(Category.MIND) }
    var editing by remember { mutableStateOf<TermInfo?>(null) }
    Screen(title = "Begriffe verwalten", nav = nav) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { c ->
                FilterChip(cat == c, { cat = c }, { Text(c.shortLabel) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(16.dp)) })
            }
        }
        HintText("Sortiert nach Häufigkeit und letzter Nutzung. Ausgeblendete Begriffe erscheinen nicht mehr als Vorschlag, bleiben aber in der Statistik.")
        SectionCard(title = cat.label, icon = cat.icon()) {
            val terms = data.terms.filter { it.category == cat && it.label != de.fembiond.app.domain.ScaleTerms.SLEEP_QUALITY && it.label != de.fembiond.app.domain.ScaleTerms.ENERGY }
                .sortedByDescending { it.score }
            terms.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { editing = t }) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(t.label, style = MaterialTheme.typography.bodyLarge, color = if (t.hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        Text("${t.useCount}× genutzt" + if (t.builtIn) " · Startbegriff" else " · eigener Begriff", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.run { updateTerm(t, hidden = !t.hidden) } }) {
                        Icon(if (t.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (t.hidden) "Einblenden" else "Ausblenden")
                    }
                }
            }
            if (terms.isEmpty()) HintText("Keine Begriffe in dieser Kategorie.")
        }
    }
    editing?.let { t ->
        var label by remember(t) { mutableStateOf(t.label) }
        var c by remember(t) { mutableStateOf(t.category) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Begriff bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ChipFlow { Category.entries.forEach { k -> FilterChip(c == k, { c = k }, { Text(k.shortLabel) }) } }
                }
            },
            confirmButton = { TextButton(onClick = { vm.run { updateTerm(t, label, c) }; editing = null }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } },
        )
    }
}

private val gadQuestions = listOf(
    "Nervosität, Ängstlichkeit oder Anspannung",
    "Nicht in der Lage sein, Sorgen zu stoppen oder zu kontrollieren",
    "Übermäßige Sorgen bezüglich verschiedener Angelegenheiten",
    "Schwierigkeiten zu entspannen",
    "So unruhig, dass Stillsitzen schwerfällt",
    "Schnelle Verärgerung oder Gereiztheit",
    "Gefühl der Angst, so als würde etwas Schlimmes passieren",
)
private val gadAnswers = listOf("Überhaupt nicht", "An einzelnen Tagen", "An mehr als der Hälfte der Tage", "Beinahe jeden Tag")

@Composable
fun GadScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    val answers = remember { mutableStateListOf<Int?>(*arrayOfNulls(7)) }
    Screen(title = "GAD-7", nav = nav) {
        HintText("Wie oft fühltest du dich im Verlauf der letzten 2 Wochen durch die folgenden Beschwerden beeinträchtigt?")
        gadQuestions.forEachIndexed { i, q ->
            SectionCard {
                Text("${i + 1}. $q", style = MaterialTheme.typography.titleSmall)
                gadAnswers.forEachIndexed { v, a ->
                    Row(Modifier.fillMaxWidth().clickable { answers[i] = v }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(answers[i] == v, { answers[i] = v }); Text(a)
                    }
                }
            }
        }
        val complete = answers.all { it != null }
        val score = answers.sumOf { it ?: 0 }
        if (complete) {
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text("Summe: $score von 21", style = MaterialTheme.typography.titleMedium)
                HintText(
                    when {
                        score <= 4 -> "Minimale Angstsymptomatik"
                        score <= 9 -> "Leichte Angstsymptomatik"
                        score <= 14 -> "Mittelgradige Angstsymptomatik"
                        else -> "Schwere Angstsymptomatik"
                    } + " (Einordnung nach GAD-7; ersetzt keine Diagnose)."
                )
            }
        }
        Button(enabled = complete, onClick = { vm.run { saveGad(data.today, answers.map { it ?: 0 }) }; nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Speichern")
        }
        if (data.gad.isNotEmpty()) SectionCard(title = "Bisherige Werte") {
            data.gad.takeLast(10).reversed().forEach { Text("${it.date.short()}: ${it.score}") }
        }
    }
}
