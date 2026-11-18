package de.fembiond.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.fembiond.app.data.AppData
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.short
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dmy = DateTimeFormatter.ofPattern("d.M.yyyy")

fun parseBirthDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim(), dmy) }.getOrNull()
    ?.takeIf { it.year in 1920..LocalDate.now().year }

/** Name und Geburtsdatum – werden nur lokal in der verschlüsselten Datenbank gespeichert. */
@Composable
fun ProfileFields(vm: AppViewModel, data: AppData, buttonLabel: String = "Speichern", onSaved: () -> Unit = {}) {
    var name by remember { mutableStateOf(data.profile?.name ?: "") }
    var birth by remember { mutableStateOf(data.profile?.birthDate?.short() ?: "") }
    val parsed = parseBirthDate(birth)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Vorname") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        OutlinedTextField(birth, { birth = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("Geburtsdatum (TT.MM.JJJJ)") },
            singleLine = true, isError = birth.isNotBlank() && parsed == null, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("Für Alter und Perimenopause-Einordnung. Bleibt nur auf diesem Gerät.") })
        Button(enabled = name.isNotBlank() && parsed != null, onClick = {
            vm.run { saveProfile(name, parsed) }
            onSaved()
        }) { Text(buttonLabel) }
    }
}
