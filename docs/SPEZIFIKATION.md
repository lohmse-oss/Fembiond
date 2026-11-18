# Fembiond – Spezifikation

Android-App zum Tracken von Zyklus sowie körperlicher und mentaler Verfassung, mit Fokus auf
Perimenopause, innere Unruhe und Panikattacken. Zielgerät: Google Pixel 7. Sprache: Deutsch.

## 1. Grundprinzipien

- **Lokal:** Alle Daten bleiben auf dem Gerät. Kein Server, keine Cloud, keine Analyse-SDKs.
  Die Datenbank ist verschlüsselt. Optional gibt es eine App-Sperre per Biometrie oder PIN.
- **Schnell im Alltag:** Eintippen oder einsprechen, die App kategorisiert automatisch, ein Tipp bestätigt.
- **Dokumentation, keine Diagnose:** Die App zeigt Muster und gibt keine medizinischen Empfehlungen.
  Hinweise (z. B. auf Perimenopause) sind vorsichtig formuliert und verweisen auf ärztliche Abklärung.
- **Ehrlich mit wenig Daten:** Bei zu geringer Datenbasis weist die App darauf hin, statt
  Zufallsmuster als Erkenntnis darzustellen.
- **Orientierung an Clue:** schlicht, wissenschaftlich fundiert, Zyklusring, Kacheln, Zyklusvergleich.
  Eigenes Design, kein Nachbau.

## 2. Zyklus

### 2.1 Eingabe
- Periodenbeginn und -ende, Blutungsstärke täglich (keine, Schmierblutung, leicht, mittel, stark, sehr stark,
  Zwischenblutung).
- Die Phase kann manuell gesetzt werden. Standardmäßig wird sie berechnet.

### 2.2 Berechnung
- Zyklus = Beginn einer Periode bis Tag vor der nächsten.
- Durchschnittliche Zyklus- und Periodenlänge aus den letzten bis zu 12 natürlichen Zyklen,
  robust gegen Ausreißer (Median). Ungefähre Daten werden geringer gewichtet.
- **Phasen:** Menstruation (Blutungstage), Follikelphase, Ovulation (ca. 14 Tage vor nächster Periode,
  Fenster ±1 Tag), Lutealphase. Fruchtbares Fenster: 5 Tage vor bis 1 Tag nach Ovulation.
- **Prognose:** nächste Periode als Spanne (Median ± Streuung). Je unregelmäßiger, desto breiter.
- **Abweichungen:** Ein Zyklus gilt als auffällig, wenn er außerhalb 21–35 Tagen liegt oder um mindestens
  7 Tage vom persönlichen Median abweicht.
- **Perimenopause (STRAW+10):**
  - frühe Übergangsphase: wiederholte Abweichung von ≥ 7 Tagen zwischen aufeinanderfolgenden Zyklen
    (mindestens 2-mal innerhalb von 10 Zyklen)
  - späte Übergangsphase: Blutungspause ≥ 60 Tage
  - Ausgabe als neutraler Hinweis, nicht als Diagnose.
- Zyklen unter Medikation werden markiert und fließen nicht in Grundlinie und STRAW-Einordnung ein.

## 3. Tageseinträge (Verfassung)

- Ein Eintrag besteht aus einzelnen Begriffen mit optionaler Intensität 1–5.
- Beliebig viele Einträge pro Tag, jeweils mit Uhrzeit.
- Begriffe wachsen: Jeder neue Freitext-Begriff wird gespeichert und als Chip vorgeschlagen.
- **Sortierung:** Kombination aus Häufigkeit und Aktualität (Häufigkeit mit zeitlichem Abklingen).

### 3.1 Kategorien mit Startbegriffen
| Kategorie | Startbegriffe |
|---|---|
| Stimmung / Geist | gereizt, traurig, ängstlich, angespannt, innere Unruhe, überfordert, Stimmungsschwankungen, ausgeglichen, fröhlich, konzentrationsschwach, vergesslich |
| Körper | Krämpfe, Kopfschmerzen, Migräne, Brustspannen, Blähungen, Rückenschmerzen, Gelenkschmerzen, Herzklopfen, Übelkeit, Hautunreinheiten |
| Perimenopause | Hitzewallung, Nachtschweiß, Herzrasen, Scheidentrockenheit |
| Schlaf & Energie | müde, erschöpft, energiegeladen, Einschlafprobleme, Durchschlafprobleme, schlecht geschlafen, gut geschlafen |
| Blutung | siehe 2.1 |
| Lebensumstände | Arbeit, Beziehung & Familie, Soziales, Reisen, Krankheit/Infekt, Sport, Alkohol, Koffein, Termine (mit Bewertung belastend/neutral/positiv) |
| Medikation / Nebenwirkungen | Schläfrigkeit, Schwindel, Stimmungsveränderung |

Grundlagen: DRSP (Daily Record of Severity of Problems), Menopause Rating Scale (MRS), STRAW+10.

## 4. Schnelle Eingabe und automatische Kategorisierung

- Freitext oder Spracheingabe (Android-Spracherkennung, offline auf dem Pixel 7).
- **Stufe 1 (Standard):** Wörterbuch mit Synonymen und Umgangssprache, Tippfehlertoleranz,
  Intensitätswörter („leicht“ = 2, „ziemlich“ = 4, „sehr/extrem“ = 5, „Stärke drei“ = 3), Verneinungen
  („keine Kopfschmerzen“ wird nicht erfasst).
- **Lernen:** Ordnet die Nutzerin ein unbekanntes Wort einer Kategorie zu, wird diese Zuordnung gespeichert.
- **Bestätigung:** Vor dem Speichern zeigt ein Bildschirm die erkannten Begriffe als Chips zur Korrektur.
- **Stufe 2 (später, optional):** lokales Sprachmodell (z. B. Gemma via MediaPipe/LiteRT) als
  austauschbarer Baustein. Gemini Nano ist auf dem Pixel 7 nicht verfügbar.

## 5. Unruhe-Episoden und Ereignisse

Im Vordergrund stehen Unruhezustände in verschiedenen Schweregraden. Im schlimmsten Fall münden sie in eine Panikattacke,
oft im Vorfeld anstehender Ereignisse (Geburtstagsfeier, Essen gehen, Konzert …).

### 5.1 Unruhe-Episode (Schnelleintrag, auch im Widget)
- **Stufe 1–5:** 1 leicht · 2 spürbar · 3 stark (körperliche Anzeichen) · 4 sehr stark (kurz vor Panik) · 5 Panikattacke
- Optional Bezug zu einem Ereignis (Auslöser „Anstehendes Ereignis“ wird dann vorgeschlagen)
- Zeitpunkt, Dauer, körperliche Anzeichen, Auslöser und was geholfen hat (jeweils wachsende Listen), Notiz
- „Innere Unruhe“ bleibt zusätzlich als Begriff im Tageseintrag für das Grundgefühl.

### 5.2 Ereignisse
- Planen mit Datum, Art (wachsende Liste: Geburtstagsfeier, Essen gehen, Konzert, Party/Feier, Familienfeier,
  Treffen mit Freunden, Kino/Theater, Arzttermin, Termin bei der Arbeit, Reise, Einkaufen/Menschenmenge),
  optionaler Bezeichnung und Notiz.
- Anzeige der voraussichtlichen Zyklusphase am Ereignistag, mit Hinweis, wenn es in die Tage vor der Periode fällt.
- Nach dem Ereignis Ausgang erfassen: war dort, früher gegangen, abgesagt.
- Erinnerung am Vortag.

### 5.3 Auswertung
- Verteilung der Stufen, Unruhe nach Zyklusphase (Anzahl, mittlere Stufe, relative Häufigkeit, Panik-Anteil)
- Anteil der Episoden, die bis zur Panik eskalieren
- Unruhe rund um Ereignisse: mittlere höchste Stufe von 7 Tagen vorher bis 1 Tag danach
- Nach Ereignisart: Anteil mit Unruhe vorher, mittlere Stufe, Panik, Absagen
- Nach Zeitpunkt im Zyklus: Ereignisse in den 7 Tagen vor der Periode im Vergleich zur übrigen Zeit

Optional: GAD-7-Fragebogen alle 14 Tage.

## 5a. Belastungshinweis und Wochenrückblick

- **Belastungshinweis** für Ereignisse der nächsten 7 Tage, wenn Faktoren zusammenkommen: voraussichtlich in den 7 Tagen vor der Periode
  (mit Vergleich zur bisherigen Erfahrung), Ereignisart bisher mit Ø Stufe ≥ 3, Schlafqualität der letzten 3 Tage Ø ≤ 2,
  Unruhe der letzten 3 Tage ≥ Stufe 3, weiteres Ereignis innerhalb eines Tages. Ab 2 Faktoren „gut vorbereiten“.
  Tipps: zuerst, was bisher geholfen hat, sonst allgemeine Strategien. Auch in der Vortags-Erinnerung.
- **Wochenrückblick** sonntags (Startbildschirm So/Mo, Benachrichtigung): letzte 7 Tage im Vergleich zu den 4 Wochen davor
  (Unruhe, Panik, Schlaf, Hitzewallungen, gute Tage) und Ausblick auf Ereignisse der nächsten Woche mit Phasenprognose.

## 5b. Perimenopause vertieft

- Schnellerfassung Hitzewallung / Nachtschweiß mit einem Tipp (Startbildschirm).
- MRS (Menopause Rating Scale) alle 28 Tage: 11 Items 0–4, Gesamt 0–44, Subskalen somatisch / psychisch / urogenital,
  Einordnung 0–4 / 5–8 / 9–15 / ≥ 16.
- Auswertung: STRAW+10, Hitzewallungen pro Monat und nach Phase, MRS-Verlauf und Teilwerte; MRS in KI-Export und PDF.

## 5c. Personalisierung

- Die App ist für genau eine Person. Name und Geburtsdatum werden einmalig auf dem Gerät abgefragt und nur in der
  verschlüsselten Datenbank gespeichert (nicht im Quellcode, da das Repository öffentlich ist).
- Begrüßung nach Tageszeit, genaues Alter, Geburtstagsgruß. Der Name erscheint nicht in Exporten.

## 6. Notizen und Lebensumstände

- Freie Notiz (Text oder Sprache), Original wird gespeichert.
- Die Notiz wird interpretiert: Gesundheitsbegriffe landen in ihren Kategorien, äußere Umstände
  in „Lebensumstände“ (vorkategorisiert, wachsend), mit Bewertung belastend/neutral/positiv.
- Zweck: Die Statistik kann äußere Belastungen vom Zykluseinfluss trennen.

## 7. Medikation

- Allgemeines Modul, zeitlich begrenzte **Behandlungsphasen**: Wirkstoff, Form, Dosis, Schema,
  Start, optional Ende. Jede Änderung beginnt eine neue Phase. Die Historie bleibt erhalten.
- **Schemata:** zyklisch (Zyklustag X bis Y) oder durchgehend.
- **Aktuell:** Progesteron oral, Zyklustag 14 bis 25 (12 Tage), danach Neubefund. Kann sich ändern oder entfallen.
- Einnahme täglich abhaken, Erinnerung zyklusbezogen (verschiebt sich mit dem Zyklus).
- **Prognose:** Bei zyklischem Schema wird eine Abbruchblutung etwa 2–7 Tage nach der letzten Einnahme erwartet.
- Blutungen unter Medikation werden von natürlichen unterschieden.
- Optional Erinnerung an Arzttermin bzw. Neubefund.

## 8. Historische Daten (Pflegedialog)

Beim ersten Start, schrittweise:
1. Geburtsjahr, typische Zyklus- und Periodenlänge
2. Vergangene Perioden als Liste („03.09.–07.09.“) oder im Kalender, Ende optional, Markierung „ungefähr“
3. Optional Notizen pro Datum (werden interpretiert) und bekannte Panikattacken

Plausibilitätsprüfung (Zyklus < 15 oder > 90 Tage löst eine Rückfrage aus). Nachgetragene Daten werden markiert
und in Korrelationen geringer gewichtet. Später ist alles über den Kalender editierbar.

## 9. Auswertung

- **Zeitraum:** letzter Zyklus, 3/6/12 Monate, alles, frei wählbar, nach Medikationsphase.
- **Filter:** Kategorien und einzelne Begriffe.
- **Verfahren:**
  - Zykluslänge und Variabilität (Median, Standardabweichung, Trend)
  - Zyklustag-Überlagerung (mittlerer Verlauf über mehrere Zyklen)
  - Heatmap Begriff × Zyklustag
  - Phasenvergleich späte Lutealphase gegenüber Follikelphase (DRSP-Methode, ≥ 30 % Zunahme)
  - Panikattacken pro Phase (Häufigkeit, Stärke)
  - gemeinsames Auftreten von Begriffen (auch am Folgetag)
  - Einfluss von Ereignissen (Rate an/nach Ereignistagen gegenüber ohne)
  - Vorher/Nachher-Vergleich von Medikationsphasen
  - Verläufe mit gleitendem Mittelwert (Schlaf, Energie, GAD-7, Zykluslänge)
- Ergebnisse in Alltagssprache mit Hinweis auf die Datenmenge.

## 10. Export

- **Für KI-Agenten:** Markdown mit Einleitung zur Bedeutung der Daten und eingebettetem JSON
  (Alter, Zyklen, Abweichungen, STRAW-Einordnung, Einträge, Panikattacken, Medikationsphasen,
  Korrelationen). Teilen über das Android-Teilen-Menü bzw. die Zwischenablage.
- **Für den Arzt:** PDF mit Zyklusübersicht, Diagrammen, Panik pro Phase, häufigsten Beschwerden, Medikation.
- **Backup:** verschlüsselte Sicherungsdatei, manuell abzulegen, wiederherstellbar.

## 11. Design

- Material 3 (Expressive), Chips als zentrales Bedienelement, große Touchflächen.
- Feminine Palette: Beere (Primär), Rosé, Mauve, Pflaume, Apricot-Akzent.
- Phasenfarben: Menstruation Beere, Follikel Apricot, Ovulation Lavendel, Luteal Pflaume, Prognose gestrichelt.
- Hell- und Dunkelmodus (System, manuell umschaltbar). Im Dunkelmodus ein tiefes, violett getöntes Grau.
- Kontraste mindestens WCAG AA. Information nie nur über Farbe.
- Keine Emojis, sondern Material Symbols (Rounded) und eigene Piktogramme.
- Visualisierungen: Zyklusring, Heatmap, Zyklusvergleich, Verläufe, Vorher/Nachher. Antippbar.

## 11a. Visualisierung und Bedienung

- Kopfbereich im Farbverlauf der aktuellen Phase
- Animierter, antippbarer Zyklusring mit Unruhe-Punkten und Tages-Kurzinfo
- Wischbarer Wochenstreifen (Phase, Unruhe, Schlaf), Mini-Verläufe der letzten 14 Tage
- Jahresübersicht als Kalender-Heatmap, Zyklen-Überlagerung mit Mittelwert
- Haptisches Feedback, Übergangsanimationen, Predictive Back
- Widgets: 4×1 (skalierbar), 2×2 Ring, 1×1 Unruhe-Schnelltaste
- Seiten „Über“ (Version, Versionshinweise, Updates) und „Anleitung“ (kontextbezogen über das Fragezeichen)

## 11b. Bedienung (v0.5)

- Auswertung in Reitern (Überblick, Zyklus, Unruhe, PMS, Perimenopause), nur Reiter mit Daten
- Plus-Menü (FAB-Menü) auf dem Startbildschirm, Schnelleingaben als Bottom Sheets (Unruhe, Beschwerden)
- „Heute wichtig“ als Karussell, mitwandernde große Kopfzeile, Sprungleiste und „Nach oben“ auf langen Seiten
- App-Shortcuts, Schnelleinstellungs-Kachel, Aktionen in Benachrichtigungen („Eingenommen“, „Unruhe erfassen“)
- Suche über Einträge, Notizen, Unruhe-Episoden und Ereignisse
- Geteilte Übergänge (Tag → Tagesansicht), Material 3 Expressive (Tastengruppe, Formwechsel, Ladeanzeige),
  leere Zustände mit Illustration, optional Systemfarben (Material You)

## 12. Technik

- Kotlin, Jetpack Compose, Material 3, Room (verschlüsselt per SQLCipher), DataStore
- minSdk 31, targetSdk 36
- Domänenlogik in reinem Kotlin, durch Unit-Tests abgesichert

## 13. Umsetzungsreihenfolge

1. Projektgerüst, Theme, Datenmodell
2. Zyklus-Engine, Begriffserkennung, Statistik (mit Tests)
3. Startbildschirm, Eingabe, Panik-Schnelleintrag, Notizen
4. Pflegedialog, Kalender, Medikation
5. Auswertung
6. Export (KI, PDF), Backup, App-Sperre
7. Später: Widget, lokales Sprachmodell, GAD-7-Erinnerungen
