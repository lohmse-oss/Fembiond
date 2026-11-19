package de.fembiond.app.domain

import java.time.LocalDate

enum class FactKind(val label: String) {
    EVENT("Ereignis"),
    HORMONE("Hormone"),
    BODY("Körper"),
    MOOD("Befinden"),
    LAB("Labor"),
    MEDICATION("Medikation"),
}

/** Ein Sachverhalt, der laut Fachliteratur typischerweise an bestimmten Zyklustagen gilt. */
data class CycleFact(
    val id: String,
    val title: String,
    val text: String,
    val fromDay: Int,
    val toDay: Int,
    val kind: FactKind,
    val source: String,
) {
    fun covers(day: Int): Boolean = day in fromDay..toDay
    val dayLabel: String get() = if (fromDay == toDay) "Tag $fromDay" else "Tag $fromDay–$toDay"
}

/** Zyklustag samt passendem Wissen für ein Datum. */
data class DayKnowledge(val cycleDay: Int, val length: Int, val periodLength: Int, val facts: List<CycleFact>)

/**
 * Wissen zu typischen Ereignissen und Zuständen im Zyklus – auf die eigene Zykluslänge umgerechnet.
 *
 * Grundprinzip: Die Zeit nach dem Eisprung (Lutealphase) ist mit etwa 12–14 Tagen recht konstant,
 * die Zeit davor schwankt. „Eisprung an Tag 14“ gilt daher nur für 28-Tage-Zyklen; die App
 * rechnet vom erwarteten Periodenbeginn zurück. Alle Angaben sind Schätzungen.
 */
object CycleKnowledge {

    const val DISCLAIMER =
        "Alle Tage sind Schätzungen aus Durchschnittswerten der Fachliteratur, umgerechnet auf die eigene Zykluslänge. " +
            "Einzelne Zyklen weichen ab – in der Perimenopause häufiger. Nicht zur Verhütung oder Diagnose geeignet."

    fun facts(length: Int, periodLength: Int, cyclic: MedicationPhase? = null): List<CycleFact> {
        val l = length.coerceAtLeast(15)
        val p = periodLength.coerceIn(1, l - 3)
        val ov = CycleEngine.ovulationDay(l, p).coerceAtMost(l - 1)
        val out = mutableListOf<CycleFact>()
        fun add(id: String, title: String, text: String, from: Int, to: Int, kind: FactKind, source: String) {
            val a = from.coerceAtLeast(1)
            val b = to.coerceAtMost(l)
            if (a <= b) out += CycleFact(id, title, text, a, b, kind, source)
        }

        add(
            "menses", "Menstruation",
            "Östrogen und Progesteron sind auf dem Tiefpunkt, die Gebärmutterschleimhaut wird abgestoßen. Eine Blutung dauert typischerweise 3–7 Tage; Müdigkeit und Kopfschmerzen sind in dieser Zeit häufiger.",
            1, p, FactKind.BODY, "ACOG: Menstruation in Girls and Adolescents (2015)",
        )
        add(
            "cramps", "Stärkste Blutung und Krämpfe",
            "Prostaglandine sind am 1. und 2. Tag am höchsten – Regelschmerzen und die stärkste Blutung treten meist dann auf und lassen danach nach.",
            1, 2, FactKind.BODY, "ACOG Committee Opinion 760: Dysmenorrhea (2018)",
        )
        add(
            "pms-relief", "Prämenstruelle Beschwerden klingen ab",
            "Beschwerden der Tage vor der Periode lassen nach Kriterien für PMS spätestens bis zum 4. Tag der Blutung nach. Diese Tage dienen als Vergleich „danach“ in der PMS-Auswertung.",
            1, 4, FactKind.MOOD, "ACOG Clinical Guidance: Management of Premenstrual Disorders (2023)",
        )
        add(
            "lab-early", "Zeitfenster für Hormonwerte",
            "Für FSH, LH und Östradiol wird meist eine Blutabnahme an Zyklustag 2–5 empfohlen. In der Perimenopause schwanken die Werte stark – ein einzelner Wert ist wenig aussagekräftig; die Diagnose stützt sich vor allem auf Alter, Zyklusverlauf und Beschwerden.",
            2, 5, FactKind.LAB, "NICE NG23 Menopause (2024); S3-Leitlinie Peri- und Postmenopause (AWMF 2020)",
        )
        add(
            "follicular", "Follikelphase: Östrogen steigt",
            "Ein Follikel reift heran und bildet zunehmend Östradiol; die Schleimhaut baut sich wieder auf. Viele erleben in dieser Zeit mehr Energie und eine stabilere Stimmung. Die Länge dieser Phase schwankt am stärksten – in der Perimenopause wird sie oft kürzer.",
            p + 1, ov - 2, FactKind.HORMONE, "Bull et al., npj Digital Medicine 2019; Hale et al., Climacteric 2009",
        )
        add(
            "fertile", "Fruchtbares Fenster",
            "Eine Empfängnis ist an den 5 Tagen vor dem Eisprung und am Eisprungtag selbst möglich, da Spermien mehrere Tage überleben. Die Grenzen sind hier nur geschätzt – nicht zur Verhütung verwenden.",
            ov - 5, ov, FactKind.EVENT, "Wilcox et al., New England Journal of Medicine 1995",
        )
        add(
            "lh", "Östrogen-Gipfel und LH-Anstieg",
            "Östradiol erreicht 1–2 Tage vor dem Eisprung seinen Höchstwert und löst den LH-Anstieg aus; der Eisprung folgt etwa 24–36 Stunden nach dessen Beginn. Darauf beruhen Ovulationstests. Der Zervixschleim wird klar und dehnbar.",
            ov - 2, ov - 1, FactKind.HORMONE, "Speroff: Clinical Gynecologic Endocrinology and Infertility",
        )
        add(
            "ovulation", "Eisprung (geschätzt)",
            "Im Lehrbuch-Zyklus von 28 Tagen ist das „Tag 14“. Konstanter ist aber die Zeit danach: etwa 12–14 Tage bis zur nächsten Periode. Bei einer Zykluslänge von $l Tagen liegt der Eisprung daher um Tag $ov. Nur ein kleiner Teil aller Zyklen dauert genau 28 Tage; in der Perimenopause bleibt der Eisprung manchmal ganz aus.",
            ov, ov, FactKind.EVENT, "Bull et al., npj Digital Medicine 2019 (über 600.000 Zyklen)",
        )
        add(
            "mittelschmerz", "Mittelschmerz möglich",
            "Rund um den Eisprung spüren manche einen einseitigen, ziehenden Unterleibsschmerz für einige Stunden bis 1–2 Tage, gelegentlich mit leichter Schmierblutung.",
            ov - 1, ov + 1, FactKind.BODY, "Merck Manual: Mittelschmerz",
        )
        add(
            "luteal", "Lutealphase: körpereigenes Progesteron",
            "Nach dem Eisprung bildet der Gelbkörper im Eierstock körpereigenes Progesteron – unabhängig von einer Einnahme. Die Basaltemperatur steigt nach dem Eisprung um etwa 0,2–0,5 °C und bleibt bis zur Periode erhöht. Es kann müde machen und den Schlaf vertiefen, aber auch Brustspannen und Blähungen begünstigen.",
            ov + 2, l, FactKind.HORMONE, "Speroff: Clinical Gynecologic Endocrinology and Infertility",
        )
        add(
            "lab-progesterone", "Progesteron-Höchstwert",
            "Etwa 7 Tage nach dem Eisprung – also rund 7 Tage vor der erwarteten Periode – ist Progesteron am höchsten. Bekannt als „Tag-21-Progesteron“ beim 28-Tage-Zyklus; ein Wert in diesem Fenster bestätigt, dass ein Eisprung stattgefunden hat." +
                if (cyclic != null) " Während einer Progesteron-Einnahme ist der Messwert nicht aussagekräftig." else "",
            ov + 6, ov + 8, FactKind.LAB, "RCOG/NICE CG156 Fertility problems (2013, aktualisiert 2017)",
        )
        add(
            "pms", "Prämenstruelle Tage",
            "Östrogen und Progesteron fallen ab, wenn keine Schwangerschaft eintritt. In den 5 Tagen vor der Periode treten PMS-typische Beschwerden auf – etwa Reizbarkeit, innere Unruhe, gedrückte Stimmung, Schlafprobleme, Brustspannen und Blähungen. Entspannte Planung kann hier entlasten.",
            l - 4, l, FactKind.MOOD, "ACOG Clinical Guidance: Management of Premenstrual Disorders (2023)",
        )
        add(
            "next", "Nächste Periode erwartet",
            "Nach der geschätzten Zykluslänge ist das der letzte Zyklustag; die nächste Blutung wird ab dem Folgetag erwartet. Abweichungen von einigen Tagen sind normal.",
            l, l, FactKind.EVENT, "Eigene Zyklusdaten (Median der letzten Zyklen)",
        )

        if (cyclic != null && cyclic.schedule == ScheduleType.CYCLIC) {
            val a = cyclic.cycleDayFrom ?: 0
            val b = cyclic.cycleDayTo ?: -1
            if (a in 1..b) {
                out += CycleFact(
                    "intake", "Einnahme: ${cyclic.name}",
                    "Laut Schema wird ${cyclic.displayName()} an Zyklustag $a–$b eingenommen. Die zugeführten Hormone überlagern die natürlichen Schwankungen dieser Tage.",
                    a, b, FactKind.MEDICATION, "Eigenes Medikationsschema",
                )
                out += CycleFact(
                    "withdrawal", "Abbruchblutung möglich",
                    "Nach dem Absetzen des Progesterons sinkt der Spiegel ab; eine Blutung setzt meist innerhalb von etwa 2–7 Tagen nach der letzten Einnahme ein.",
                    b + 2, b + 7, FactKind.EVENT, "S3-Leitlinie Peri- und Postmenopause (AWMF 2020)",
                )
            }
        }
        return out.sortedWith(compareBy({ it.fromDay }, { it.toDay }))
    }

    /** Ein Zyklus, der länger dauert als erwartet. */
    fun lateFact(length: Int): CycleFact = CycleFact(
        "late", "Länger als erwartet",
        "Der Zyklus dauert länger als die üblichen $length Tage. Häufigste Ursache ist ein verspäteter Eisprung, etwa durch Stress, Krankheit oder – in der Perimenopause – schwankende Hormone. Bei einer Blutungspause ab 60 Tagen spricht STRAW+10 von einer späten Übergangsphase.",
        length + 1, length + 365, FactKind.EVENT, "Harlow et al., STRAW+10 (2012)",
    )

    fun factsFor(day: Int, length: Int, periodLength: Int, cyclic: MedicationPhase? = null): List<CycleFact> =
        if (day > length) facts(maxOf(length, day), periodLength, cyclic).filter { it.covers(day) && it.kind == FactKind.MEDICATION } + lateFact(length)
        else facts(length, periodLength, cyclic).filter { it.covers(day) }

    /** Wissen für ein Datum: vergangene Zyklen mit ihrer echten Länge, der aktuelle/kommende mit der Prognose. */
    fun forDate(summary: CycleSummary, medications: List<MedicationPhase>, date: LocalDate, today: LocalDate): DayKnowledge? {
        val info = (if (date.isAfter(today)) CycleEngine.projectDay(summary, date) else CycleEngine.dayInfo(summary, date)) ?: return null
        val cycle = summary.cycles.firstOrNull { it.index == info.cycleIndex }
        val periodLength = cycle?.periodLength ?: summary.baselinePeriodLength
        val length = cycle?.length
            ?: info.daysBeforeNext?.takeIf { info.predicted }?.let { info.cycleDay - 1 + it }
            ?: summary.current?.expectedLength ?: summary.baselineLength
        val cyclic = medications.firstOrNull { it.schedule == ScheduleType.CYCLIC && it.activeOn(date) }
        return DayKnowledge(info.cycleDay, length, periodLength, factsFor(info.cycleDay, length, periodLength, cyclic))
    }
}
