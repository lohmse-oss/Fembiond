package de.fembiond.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.Category
import de.fembiond.app.ui.AddChip
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.IntensitySelector
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.TermChip
import java.time.LocalDateTime

private val durations = listOf(3 to "< 5 Min.", 10 to "5–15 Min.", 22 to "15–30 Min.", 45 to "30–60 Min.", 90 to "> 1 Std.")
private val whenOptions = listOf(0 to "gerade eben", 30 to "vor 30 Min.", 60 to "vor 1 Std.", 180 to "vor 3 Std.", 720 to "heute Nacht")

@Composable
fun PanicScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    var intensity by rememberSaveable { mutableStateOf<Int?>(3) }
    var duration by rememberSaveable { mutableStateOf<Int?>(null) }
    var ago by rememberSaveable { mutableStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    val symptoms = remember { mutableStateListOf<String>() }
    val triggers = remember { mutableStateListOf<String>() }
    val helped = remember { mutableStateListOf<String>() }
    var addFor by remember { mutableStateOf<Category?>(null) }

    Screen(
        title = "Panikattacke",
        nav = nav,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = {
                            val time = LocalDateTime.now().minusMinutes(ago.toLong())
                            vm.run { savePanic(time, duration, intensity ?: 3, symptoms.toList(), triggers.toList(), helped.toList(), note) }
                            nav.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    ) { Text("Speichern", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) {
        HintText("Nur das Nötigste – alles Weitere ist optional und kann auch später ergänzt werden.")
        SectionCard(title = "Stärke", icon = Icons.Rounded.MonitorHeart) {
            IntensitySelector(intensity, { intensity = it })
        }
        SectionCard(title = "Wann und wie lange", icon = Icons.Rounded.Schedule) {
            ChipFlow { whenOptions.forEach { (m, l) -> FilterChip(ago == m, { ago = m }, { Text(l) }) } }
            ChipFlow { durations.forEach { (m, l) -> FilterChip(duration == m, { duration = if (duration == m) null else m }, { Text(l) }) } }
        }
        listOf(
            Triple(Category.PANIC_SYMPTOM, symptoms, Icons.Rounded.Air),
            Triple(Category.PANIC_TRIGGER, triggers, Icons.Rounded.TrackChanges),
            Triple(Category.PANIC_HELP, helped, Icons.Rounded.SelfImprovement),
        ).forEach { (cat, list, icon) ->
            SectionCard(title = cat.label.removePrefix("Panik: "), icon = icon) {
                ChipFlow {
                    val shown = (data.suggestions(cat).map { it.label } + list).distinct()
                    shown.forEach { label ->
                        TermChip(label, label in list, null, { if (label in list) list.remove(label) else list.add(label) })
                    }
                    AddChip { addFor = cat }
                }
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }

    addFor?.let { cat ->
        NewTermDialog(
            data = data, initial = "", initialCategory = cat, categories = listOf(cat),
            onDismiss = { addFor = null },
            onNew = { label, _ ->
                when (cat) {
                    Category.PANIC_SYMPTOM -> symptoms.add(label)
                    Category.PANIC_TRIGGER -> triggers.add(label)
                    else -> helped.add(label)
                }
                addFor = null
            },
        )
    }
}
