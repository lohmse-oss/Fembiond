package de.fembiond.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.BuildConfig
import de.fembiond.app.R
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.short

/** Versionshinweise aus den App-Assets (assets/releases/vX.Y.Z.md), neueste zuerst. */
private fun releaseNotes(context: android.content.Context): List<Pair<String, String>> =
    (context.assets.list("releases") ?: emptyArray())
        .filter { it.endsWith(".md") }
        .map { it.removeSuffix(".md") }
        .sortedWith(compareByDescending<String> { v -> v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } * 1_000_000 + it.getOrElse(1) { 0 } * 1000 + it.getOrElse(2) { 0 } } })
        .map { v -> v to context.assets.open("releases/$v.md").bufferedReader().use { it.readText() } }

/** Sehr einfache Markdown-Darstellung: Überschriften (##), Aufzählungen (-), **fett**, `code`. */
@Composable
fun MarkdownText(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.size(2.dp))
                line.startsWith("## ") || line.startsWith("# ") ->
                    Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                line.startsWith("- ") -> Row {
                    Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(inline(line.removePrefix("- ")), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(inline(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val text = s.replace("`", "")
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}

@Composable
fun AboutScreen(data: AppData, nav: NavController) {
    val context = LocalContext.current
    val notes = remember { runCatching { releaseNotes(context) }.getOrDefault(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    val installed = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrNull()?.takeIf { it > 1_600_000_000_000L }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    }
    val updated = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrNull()?.takeIf { it > 1_600_000_000_000L }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    }

    Screen(title = "Über Fembiond", nav = nav) {
        SectionCard(container = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Fembiond", style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                    HintText(listOfNotNull(installed?.let { "installiert ${it.short()}" }, updated?.takeIf { it != installed }?.let { "aktualisiert ${it.short()}" }).joinToString(" · "))
                }
            }
            HintText("Zyklus, Körper und Stimmung festhalten – mit Fokus auf Perimenopause und Unruhe. Alle Daten bleiben verschlüsselt auf diesem Gerät.")
            ChipFlow {
                FilledTonalButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.RELEASES_URL + "/latest")))
                }) { Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nach Updates suchen") }
                OutlinedButton(onClick = { nav.navigate(Routes.guide()) }) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Anleitung")
                }
            }
        }

        notes.firstOrNull()?.let { (v, md) ->
            SectionCard(title = "Neu in $v", icon = Icons.Rounded.NewReleases) { MarkdownText(md) }
        }
        if (notes.size > 1) SectionCard(title = "Frühere Versionen", icon = Icons.Rounded.History) {
            if (!showAll) TextButton(onClick = { showAll = true }) { Text("${notes.size - 1} frühere Version(en) anzeigen") }
            else notes.drop(1).forEachIndexed { i, (v, md) ->
                if (i > 0) HorizontalDivider()
                Text(v, style = MaterialTheme.typography.titleMedium)
                MarkdownText(md)
            }
        }

        SectionCard(title = "Deine Daten", icon = Icons.Rounded.Lock) {
            val stats = listOf(
                "Perioden" to data.periods.size,
                "Tageseinträge" to data.entries.size,
                "Unruhe-Episoden" to data.episodes.size,
                "Ereignisse" to data.events.size,
                "Eigene Begriffe" to data.terms.count { !it.builtIn },
            )
            stats.forEach { (l, n) -> LabeledRow(l) { Text("$n", style = MaterialTheme.typography.titleSmall) } }
            HintText("Gespeichert in einer verschlüsselten Datenbank (SQLCipher, Schlüssel im Android Keystore). Kein Server, keine Cloud, keine Werbung, keine Analyse-Dienste. Android-Cloud-Backups sind ausgeschlossen – sichere deine Daten über Export & Sicherung.")
            TextButton(onClick = { nav.navigate(Routes.EXPORT) }) { Text("Zu Export & Sicherung") }
        }

        SectionCard(title = "Fachliche Grundlagen", icon = Icons.Rounded.Science) {
            listOf(
                "STRAW+10" to "Einteilung der Stadien des reproduktiven Alterns (Harlow et al., 2012) – Grundlage für die Perimenopause-Hinweise.",
                "DRSP" to "Daily Record of Severity of Problems (Endicott et al.) – Vergleich der 7 Tage vor der Periode mit Tag 4–10.",
                "Menopause Rating Scale (MRS)" to "Orientierung für Startbegriffe zu Körper, Psyche und Perimenopause.",
                "GAD-7" to "Kurzfragebogen zu Angstsymptomen der letzten zwei Wochen (Spitzer et al., 2006).",
                "DSM-5" to "Orientierung für die körperlichen Anzeichen einer Panikattacke.",
            ).forEach { (t, d) ->
                Column {
                    Text(t, style = MaterialTheme.typography.titleSmall)
                    HintText(d)
                }
            }
        }

        SectionCard(title = "Hinweis", icon = Icons.Rounded.Gavel) {
            HintText("Fembiond dokumentiert Beobachtungen und zeigt statistische Muster. Die App stellt keine Diagnosen und ersetzt keine ärztliche oder psychotherapeutische Beratung. Prognosen zu Periode und fruchtbarem Fenster sind Schätzungen und nicht zur Verhütung geeignet.")
            HintText("Bei akuter Krise: Telefonseelsorge 0800 111 0 111 oder 0800 111 0 222 (kostenlos, rund um die Uhr), im Notfall 112.")
        }
        Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.guide()) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Wie funktioniert die App?", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
