package de.fembiond.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.AgentExport
import de.fembiond.app.domain.AnalysisInput
import de.fembiond.app.domain.Analytics
import de.fembiond.app.domain.Category
import de.fembiond.app.export.Backup
import de.fembiond.app.export.PdfReport
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.short
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportScreen(vm: AppViewModel, data: AppData, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf<RangeOption>(RangeOption.Months(6)) }
    var categories by remember { mutableStateOf(Category.entries.toSet()) }
    val (from, to) = range.range(data)
    val input = AnalysisInput(data.summary, data.occurrences, data.panics, data.medications, data.gad, from, to, categories)
    val result = remember(data, from, to, categories) { Analytics.analyze(input) }

    var passwordFor by remember { mutableStateOf<String?>(null) } // "backup" | "restore"
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupPassword by remember { mutableStateOf<CharArray?>(null) }

    fun agentText(): String {
        val notes = data.entries.filter { it.note.isNotBlank() && !it.time.toLocalDate().isBefore(from) && !it.time.toLocalDate().isAfter(to) }
            .map { it.time.toLocalDate() to it.note }.sortedBy { it.first }
        return AgentExport.build(input, result, data.settings.age, data.intakes, notes, data.today)
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = pendingBackupPassword
        if (uri != null && pw != null) scope.launch {
            val ok = runCatching {
                val json = vm.repo.backupJson()
                val bytes = withContext(Dispatchers.Default) { Backup.encrypt(json, pw) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
            }.isSuccess
            toast(context, if (ok) "Sicherung gespeichert" else "Sicherung fehlgeschlagen")
        }
        pendingBackupPassword = null
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingRestore = uri; passwordFor = "restore" }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { PdfReport(input, result, data.settings.age).write(it) } }
            toast(context, "Bericht gespeichert")
        }
    }

    Screen(title = "Export & Sicherung", nav = nav) {
        RangeAndFilter(data, range, { range = it }, categories, { categories = it })
        HintText("Zeitraum ${from.short()} – ${to.short()}. Exporte enthalten keine Namen. Geteilt wird nur, was du selbst teilst.")

        SectionCard(title = "Für einen KI-Agenten", icon = Icons.Rounded.SmartToy) {
            HintText("Strukturierter Text mit Erklärung der Daten und eingebettetem JSON: Zyklen, Abweichungen, Perimenopause-Einordnung, Einträge pro Zyklustag, Panikattacken, Medikation und errechnete Zusammenhänge.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, agentText()) }
                    context.startActivity(Intent.createChooser(send, "Daten teilen"))
                }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("Teilen") }
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Fembiond Export", agentText()))
                    toast(context, "In die Zwischenablage kopiert")
                }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Kopieren") }
            }
        }

        SectionCard(title = "Arztbericht (PDF)", icon = Icons.Rounded.PictureAsPdf) {
            HintText("Übersicht mit Zyklusdiagramm, Medikation, Panikattacken nach Phase, Vergleich vor/nach der Periode und Häufigkeiten nach Zyklustag.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                            File(dir, "fembiond-bericht-${data.today}.pdf").also { f -> f.outputStream().use { PdfReport(input, result, data.settings.age).write(it) } }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Bericht teilen"))
                    }
                }) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("Teilen") }
                OutlinedButton(onClick = { savePdf.launch("fembiond-bericht-${data.today}.pdf") }) { Text("Speichern unter …") }
            }
        }

        SectionCard(title = "Sicherung", icon = Icons.Rounded.Backup) {
            HintText("Da es keine Cloud gibt, gehen bei Verlust des Handys alle Daten verloren. Lege regelmäßig eine passwortgeschützte Sicherung an, z. B. auf einem USB-Stick oder Computer.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { passwordFor = "backup" }) { Text("Sicherung erstellen") }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text("Wiederherstellen") }
            }
        }
    }

    passwordFor?.let { mode ->
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        val isBackup = mode == "backup"
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text(if (isBackup) "Passwort für die Sicherung" else "Sicherung wiederherstellen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isBackup) HintText("Achtung: Alle aktuellen Daten werden durch die Sicherung ersetzt.")
                    OutlinedTextField(pw, { pw = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (isBackup) {
                        OutlinedTextField(pw2, { pw2 = it }, label = { Text("Passwort wiederholen") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        HintText("Ohne Passwort lässt sich die Sicherung nicht öffnen – gut aufbewahren.")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = pw.length >= 6 && (!isBackup || pw == pw2), onClick = {
                    passwordFor = null
                    if (isBackup) {
                        pendingBackupPassword = pw.toCharArray()
                        createBackup.launch("fembiond-sicherung-${data.today}.fembiond")
                    } else {
                        val uri = pendingRestore ?: return@TextButton
                        scope.launch {
                            val ok = runCatching {
                                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                                val json = withContext(Dispatchers.Default) { Backup.decrypt(bytes, pw.toCharArray()) }
                                vm.repo.restoreJson(json)
                            }.isSuccess
                            toast(context, if (ok) "Sicherung wiederhergestellt" else "Falsches Passwort oder ungültige Datei")
                        }
                    }
                }) { Text(if (isBackup) "Weiter" else "Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { passwordFor = null }) { Text("Abbrechen") } },
        )
    }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
