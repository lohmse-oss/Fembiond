package de.fembiond.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.ui.HintText

data class GuideSection(val id: String, val title: String, val icon: ImageVector, val markdown: String)

object Guide {
    val sections = listOf(
        GuideSection("start", "Erste Schritte", Icons.Rounded.RocketLaunch, """
- Trage unter **Mehr → Historie & Perioden** deine vergangenen Perioden ein, z. B. „03.09. – 07.09.“. Ohne Jahr nimmt die App das letzte passende Datum.
- Schon 3–6 vergangene Zyklen machen die Prognose deutlich persönlicher.
- Lege unter **Mehr → Medikation** laufende Medikamente als Behandlungsphase an (z. B. Progesteron, Zyklustag 14–25).
- Füge das **Widget** zum Startbildschirm hinzu – dann reicht ein Blick und ein Tipp.
""".trimIndent()),
        GuideSection("home", "Startbildschirm & Zyklusring", Icons.Rounded.Spa, """
- Der Ring zeigt den aktuellen Zyklus: ein Segment pro Tag, eingefärbt nach Phase. Vergangene Tage sind kräftig, kommende blass. Der Punkt markiert heute.
- Kleine Punkte innen markieren Einnahmetage laut Medikationsschema, das gestrichelte Band außen das geschätzte fruchtbare Fenster.
- **Phase antippen**, um sie für den aktuellen Zyklus manuell festzulegen.
- Die nächste Periode wird als **Spanne** angegeben. Bei unregelmäßigen Zyklen wird sie breiter; unter zyklischem Progesteron rechnet die App mit der Abbruchblutung nach der letzten Einnahme.
- **Periode beginnt/endet heute** setzt Beginn und Ende mit einem Tipp.
- **Tag im Ring antippen** zeigt eine Kurzinfo zu diesem Tag. Farbige Punkte außen am Ring markieren Unruhe – je größer und röter, desto stärker.
- Der Kopfbereich ist in der Farbe der aktuellen Phase hinterlegt.
- Der **Wochenstreifen** unter dem Ring zeigt die letzten Tage mit Phase (Balken), Unruhe (Punkt) und Schlafqualität (Mond). Nach links wischen für frühere Wochen, antippen öffnet den Tag.
- **Letzte 14 Tage**: kleine Verläufe für Unruhe, Schlaf, Energie und Hitzewallungen.
""".trimIndent()),
        GuideSection("planning", "Belastungshinweis", Icons.Rounded.TipsAndUpdates, """
- Für Ereignisse in den nächsten 7 Tagen prüft die App, ob mehrere Belastungen zusammenkommen:
- das Ereignis fällt voraussichtlich in die Tage vor der Periode,
- diese Art Ereignis war bisher oft mit starker Unruhe verbunden,
- du hast zuletzt schlecht geschlafen oder warst bereits unruhig,
- direkt davor oder danach steht noch etwas an.
- Dann erscheint auf dem Startbildschirm ein freundlicher Hinweis mit Ideen, was helfen kann – auch mit dem, was dir bisher geholfen hat. Am Vortag kommt er auch als Erinnerung.
""".trimIndent()),
        GuideSection("weekly", "Wochenrückblick", Icons.Rounded.AutoStories, """
- Jeden Sonntag fasst die App deine Woche zusammen: Unruhe, Schlaf, Hitzewallungen und gute Tage – jeweils im Vergleich zu den vier Wochen davor.
- Dazu siehst du die Ereignisse der nächsten Woche mit voraussichtlicher Zyklusphase.
- Der Rückblick erscheint sonntags und montags auf dem Startbildschirm und als Benachrichtigung. Abschaltbar unter Einstellungen.
""".trimIndent()),
        GuideSection("pms", "PMS – prämenstruelle Beschwerden", Icons.Rounded.Psychology, """
- Die App prüft nach den Kriterien der amerikanischen Fachgesellschaft für Gynäkologie (ACOG), ob in den **5 Tagen vor der Periode** Beschwerden der **Stimmung** (z. B. gereizt, traurig, ängstlich, innere Unruhe, Stimmungsschwankungen, Rückzug) **und** des **Körpers** (z. B. Brustspannen, Blähungen, Kopfschmerzen, Wassereinlagerungen) auftreten, die an **Tag 4–10 nach Beginn der Periode wieder abklingen**.
- Zeigt sich das in **3 Zyklen hintereinander**, gilt es als PMS-typisch. Bei sehr vielen, deutlichen Beschwerden in mehreren Zyklen weist die App auf eine mögliche PMDS hin – das sollte ärztlich abgeklärt werden.
- Unter „Details“ siehst du für jede Periode, welche Beschwerden davor auftraten und danach abklangen.
- In den Tagen vor der erwarteten Periode erinnert dich der Startbildschirm an deine typischen Beschwerden.
- Wichtig: An ruhigen Tagen kurz eintragen (z. B. „ausgeglichen“) – sonst fehlt der Vergleich „danach“.
""".trimIndent()),
        GuideSection("peri", "Perimenopause & MRS", Icons.Rounded.LocalFireDepartment, """
- Auf dem Startbildschirm erfasst du **Hitzewallungen** und **Nachtschweiß** mit einem Tipp, die Uhrzeit wird automatisch gespeichert.
- Der **MRS-Fragebogen** (Menopause Rating Scale) fragt alle 4 Wochen 11 Beschwerden ab (0 = keine bis 4 = sehr stark). Er ergibt einen Gesamtwert (0–44) und drei Teilwerte: körperlich, psychisch, urogenital.
- Einordnung des Gesamtwerts: 0–4 keine bis gering, 5–8 leicht, 9–15 mittel, ab 16 stark.
- In der Auswertung findest du unter **Perimenopause** die STRAW-Einordnung, Hitzewallungen pro Monat und nach Phase sowie den MRS-Verlauf.
""".trimIndent()),
        GuideSection("entry", "Befinden eintragen – Text & Sprache", Icons.Rounded.Mic, """
- Tippe auf **Wie geht es dir gerade?** und schreibe oder sprich frei, z. B. „ziemlich müde, Kopfweh, innere Unruhe Stärke 3“.
- Die App erkennt Begriffe automatisch und ordnet sie Kategorien zu – auch Umgangssprache („Kopfweh“), Tippfehler und Verneinungen („keine Kopfschmerzen“).
- Wörter wie „leicht“, „ziemlich“, „extrem“ oder „Stärke drei“ setzen die **Intensität 1–5**. Du kannst sie unter „Erfasst – bitte prüfen“ anpassen.
- **Nicht erkannte Wörter** antippen, um sie als eigenen Begriff zu speichern oder einem vorhandenen zuzuordnen. Die App merkt sich das.
- Unter den Kategorien findest du Vorschläge als Chips – häufig und zuletzt Genutztes steht vorne.
- Über das Datum unten links trägst du für vergangene Tage nach.
""".trimIndent()),
        GuideSection("note", "Notizen & Lebensumstände", Icons.Rounded.EditNote, """
- **Notiz** auf dem Startbildschirm: frei erzählen, z. B. „letzter Arbeitstag, abends Streit, kaum geschlafen“.
- Äußere Umstände (Arbeit, Familie, Feiern, Infekt …) landen unter **Lebensumstände**, auf Wunsch mit Bewertung belastend / neutral / positiv.
- So kann die Auswertung äußere Belastungen vom Einfluss des Zyklus unterscheiden.
""".trimIndent()),
        GuideSection("unrest", "Unruhe erfassen", Icons.Rounded.Waves, """
- Tippe auf **Unruhe** (Startbildschirm, Widget oder Kalendertag) und wähle die Stufe:
- **1 leicht** – innerlich etwas angespannt, gut auszuhalten
- **2 spürbar** – Gedanken kreisen, Ablenkung fällt schwer
- **3 stark** – körperliche Anzeichen wie Herzklopfen, Zittern, Magen
- **4 sehr stark** – kurz vor der Panik, Drang abzusagen oder zu flüchten
- **5 Panikattacke**
- Optional: Bezug zu einem Ereignis, körperliche Anzeichen, Auslöser und was geholfen hat. Nur die Stufe ist Pflicht – lieber kurz erfassen als gar nicht.
- Mehrere Einträge am Tag sind ausdrücklich erwünscht, z. B. wenn die Unruhe vor einem Termin zunimmt.
""".trimIndent()),
        GuideSection("events", "Ereignisse planen", Icons.Rounded.Event, """
- Unter **Mehr → Ereignisse** oder über die Karte „Anstehend“ planst du Feiern, Essen gehen, Konzerte und Termine.
- Die App zeigt, in welche **Zyklusphase** das Ereignis voraussichtlich fällt, und weist darauf hin, wenn es in die Tage vor der Periode fällt – hilfreich bei der Planung.
- Am Vortag erinnert die App an das Ereignis.
- Danach hältst du fest, wie es gelaufen ist: **war dort**, **früher gegangen** oder **abgesagt**.
- Die Zahl neben vergangenen Ereignissen ist die höchste Unruhe-Stufe in den 3 Tagen davor bis zum Ereignistag.
""".trimIndent()),
        GuideSection("meds", "Medikation", Icons.Rounded.Medication, """
- Jede **Behandlungsphase** hat Start und optional Ende. Ändert sich Dosis oder Schema, nutze **Ändern** – die alte Phase bleibt für Vergleiche erhalten.
- Bei zyklischem Schema erinnert die App an fälligen Tagen; die Einnahme hakst du auf dem Startbildschirm oder direkt im Widget ab.
- Zyklen unter Medikation werden markiert und fließen nicht in die natürliche Grundlinie und die Perimenopause-Einordnung ein.
""".trimIndent()),
        GuideSection("calendar", "Kalender", Icons.Rounded.CalendarMonth, """
- Gefüllte Kreise: Periode. Umrandete Kreise: Prognose. Helles Lila: fruchtbares Fenster.
- Punkte unter dem Datum: Eintrag (Beere), Unruhe (Farbe nach Stufe, rot = Panik), Ereignis (kleines Quadrat).
- Tag antippen für Details, Löschen, Nachtragen und um Periodenbeginn oder -ende zu korrigieren.
""".trimIndent()),
        GuideSection("analysis", "Auswertung verstehen", Icons.Rounded.Analytics, """
- Oben wählst du **Zeitraum** (z. B. 6 Monate oder „unter Progesteron“) und **Bereiche** (Geist, Körper, Perimenopause, Schlaf, Leben, Medikation). Mit einem Bereich gefiltert, zeigt die Auswertung nur die passenden Karten – „Alle zeigen“ hebt den Filter auf.
- **Erkenntnisse** fassen auffällige Muster in Sätzen zusammen – mit Hinweis, wenn es noch zu wenige Daten gibt.
- **Zykluslänge**: Balken pro Zyklus (Beere = Periode). Warnsymbol = deutliche Abweichung, Kapsel = unter Medikation.
- **Wann tritt was auf?** (Heatmap): je dunkler, desto häufiger tritt ein Begriff an diesem Zyklustag auf. Feld antippen für Details.
- **Vor der Periode vs. danach**: vergleicht die 7 Tage vor der Periode mit Tag 4–10 (DRSP-Methode). Ab etwa 30 % Zunahme gilt ein Muster als deutlich.
- **Unruhe nach Phase**: Anzahl, mittlere Stufe und relative Häufigkeit (1× = Durchschnitt; berücksichtigt, wie lang die Phasen sind).
- **Unruhe rund um Ereignisse**: mittlere höchste Stufe an den Tagen vor und nach Ereignissen – zeigt, wie sich Erwartungsunruhe aufbaut. Dazu Auswertungen nach Ereignisart und nach Zeitpunkt im Zyklus.
- **Jahresübersicht**: ein Kästchen pro Tag der letzten 12 Monate, gefärbt nach Unruhe; Periodentage umrandet.
- **Zyklen übereinander**: die letzten Zyklen nach Zyklustag übereinandergelegt (Unruhe, Schlaf, Energie oder Hitzewallungen). Die kräftige Linie ist der Mittelwert.
- **Zusammenhänge** sind Hinweise, keine Beweise für Ursachen.
""".trimIndent()),
        GuideSection("terms", "Begriffe verwalten", Icons.AutoMirrored.Rounded.Label, """
- Unter **Mehr → Begriffe verwalten** kannst du Begriffe umbenennen, einer anderen Kategorie zuordnen oder ausblenden.
- Ausgeblendete Begriffe erscheinen nicht mehr als Vorschlag, bleiben aber in der Statistik erhalten.
""".trimIndent()),
        GuideSection("widget", "Widget einrichten", Icons.Rounded.Widgets, """
- Startbildschirm lange gedrückt halten → **Widgets** → **Fembiond** → auf den Startbildschirm ziehen.
- Es gibt drei Widgets: **Fembiond Zyklus** (4×1, skalierbar), **Fembiond Ring** (2×2, großer Ring) und **Fembiond Unruhe** (1×1, Schnelltaste ohne Gesundheitsdaten).
- 4×1: Mini-Zyklusring mit Zyklustag, Phase und Prognose. Kapsel antippen hakt die Einnahme ab. Bei genug Breite gibt es eine Plus-Taste für einen Eintrag.
- Auf 4×2 vergrößert: zusätzlich Phasenbalken, nächstes Ereignis und Schnelltasten „Befinden“ und „Unruhe“.
- Ist die App-Sperre aktiv, zeigt das Widget keine Gesundheitsdaten.
""".trimIndent()),
        GuideSection("export", "Export & Sicherung", Icons.Rounded.Backup, """
- **Für einen KI-Agenten**: strukturierter Text mit Erklärung und Daten (JSON) – über Teilen oder Kopieren an einen Assistenten geben.
- **Arztbericht (PDF)**: Zyklen, Medikation, Unruhe nach Phase und rund um Ereignisse, Häufigkeiten nach Zyklustag.
- **Sicherung**: passwortgeschützte Datei. Da es keine Cloud gibt, regelmäßig sichern, z. B. auf einen Computer. Ohne Passwort lässt sie sich nicht öffnen.
""".trimIndent()),
        GuideSection("privacy", "Datenschutz & App-Sperre", Icons.Rounded.Lock, """
- Alle Daten liegen verschlüsselt nur auf diesem Gerät. Es gibt keinen Server und keine Cloud.
- Unter **Einstellungen** kannst du eine App-Sperre per Fingerabdruck oder Geräte-PIN aktivieren.
- Exporte und Sicherungen verlassen das Gerät nur, wenn du sie selbst teilst.
""".trimIndent()),
        GuideSection("tips", "Tipps für aussagekräftige Daten", Icons.Rounded.Lightbulb, """
- Kurz und regelmäßig schlägt ausführlich und selten – ein Eintrag mit zwei Begriffen genügt.
- Auch gute Tage eintragen („ausgeglichen“, „gut geschlafen“) – sonst fehlt der Vergleich.
- Ereignisse vorab planen, dann zeigt die Auswertung, wie sich die Unruhe davor entwickelt.
- Muster zeigen sich meist nach 2–3 Zyklen; für Vergleiche vor/unter Medikation braucht es mehrere Zyklen je Phase.
""".trimIndent()),
    )
}

@Composable
fun GuideScreen(nav: NavController, open: String? = null) {
    val expanded = remember { mutableStateMapOf<String, Boolean>().apply { open?.let { put(it, true) } } }
    Screen(title = "Anleitung", nav = nav) {
        HintText("Tippe auf ein Thema, um es aufzuklappen.")
        Guide.sections.forEach { s ->
            val isOpen = expanded[s.id] == true
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = if (isOpen) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded[s.id] = !isOpen }.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (isOpen) "Zuklappen" else "Aufklappen")
                    }
                    AnimatedVisibility(isOpen) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MarkdownText(s.markdown)
                        }
                    }
                }
            }
        }
    }
}
