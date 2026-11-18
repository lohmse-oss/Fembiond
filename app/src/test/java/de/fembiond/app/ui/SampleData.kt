package de.fembiond.app.ui

import de.fembiond.app.data.AppData
import de.fembiond.app.data.EntryView
import de.fembiond.app.data.Settings
import de.fembiond.app.data.TermInfo
import de.fembiond.app.domain.Category
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.GadRecord
import de.fembiond.app.domain.Lexicon
import de.fembiond.app.domain.MedicationPhase
import de.fembiond.app.domain.PanicRecord
import de.fembiond.app.domain.PeriodRecord
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.domain.TermOccurrence
import de.fembiond.app.domain.TermParser
import de.fembiond.app.domain.Valence
import de.fembiond.app.domain.VocabItem
import java.time.LocalDate
import kotlin.random.Random

/** Realistische Beispieldaten für Screenshots: 7 Zyklen, zuletzt mit Progesteron. */
object SampleData {
    fun build(today: LocalDate = LocalDate.of(2026, 11, 18)): AppData {
        val rnd = Random(7)
        val lengths = listOf(27, 29, 26, 35, 25, 33, 28)
        val starts = mutableListOf<LocalDate>()
        var d = today.minusDays(lengths.sum().toLong() + 15)
        for (l in lengths) { starts += d; d = d.plusDays(l.toLong()) }
        starts += d
        val periods = starts.mapIndexed { i, s -> PeriodRecord(i + 1L, s, s.plusDays(4), approximate = i == 0, backfilled = i < 4) }
        val med = MedicationPhase(1, "Progesteron", "oral", "100 mg", ScheduleType.CYCLIC, 14, 25, starts[6], null, today.plusDays(9), "")
        val summary0 = CycleEngine.analyze(periods, listOf(med), today)

        val occ = mutableListOf<TermOccurrence>()
        val panics = mutableListOf<PanicRecord>()
        var day = starts[3]
        while (!day.isAfter(today)) {
            val info = CycleEngine.dayInfo(summary0, day)
            val before = info?.daysBeforeNext ?: 99
            val t = day.atTime(20, 0)
            occ += TermOccurrence(t, ScaleTerms.SLEEP_QUALITY, Category.SLEEP_ENERGY, if (before <= 6) 2 else 3 + rnd.nextInt(2))
            occ += TermOccurrence(t, ScaleTerms.ENERGY, Category.SLEEP_ENERGY, if (before <= 6) 2 else 3 + rnd.nextInt(2))
            if (before <= 6 && rnd.nextDouble() < 0.8) occ += TermOccurrence(t, "innere Unruhe", Category.MIND, 3 + rnd.nextInt(2))
            if (before <= 5 && rnd.nextDouble() < 0.6) occ += TermOccurrence(t, "gereizt", Category.MIND, 2 + rnd.nextInt(3))
            if ((info?.cycleDay ?: 99) <= 2) occ += TermOccurrence(t, "Krämpfe", Category.BODY, 3 + rnd.nextInt(2))
            if ((info?.cycleDay ?: 99) in 1..3 && rnd.nextDouble() < 0.5) occ += TermOccurrence(t, "Kopfschmerzen", Category.BODY, 3)
            if (rnd.nextDouble() < 0.15) occ += TermOccurrence(t, "Nachtschweiß", Category.PERIMENOPAUSE, 2)
            if (rnd.nextDouble() < 0.12) occ += TermOccurrence(t, "Arbeitsstress", Category.LIFE, null, Valence.BURDEN)
            if ((info?.cycleDay ?: 0) in 8..14 && rnd.nextDouble() < 0.5) occ += TermOccurrence(t, "ausgeglichen", Category.MIND, 4)
            if (before in 1..4 && rnd.nextDouble() < 0.35) panics += PanicRecord(
                0, day.atTime(22, 30), 15, 3 + rnd.nextInt(2), listOf("Herzrasen", "Atemnot"), listOf("nachts aus dem Schlaf"), listOf("Atemübung"),
            )
            day = day.plusDays(1)
        }
        occ += TermOccurrence(today.atTime(9, 15), "müde", Category.SLEEP_ENERGY, 4)
        occ += TermOccurrence(today.atTime(9, 15), "Brustspannen", Category.BODY, 2)
        val entries = occ.groupBy { it.time }.entries.mapIndexed { i, (time, list) -> EntryView(i.toLong(), time, if (time.toLocalDate() == today) "Schlecht geschlafen, aber okay" else "", "text", false, list) }

        val terms = Lexicon.entries.filter { it.suggested }.mapIndexed { i, e ->
            val uses = occ.count { it.term == e.label }
            TermInfo(i.toLong(), e.label, e.category, e.valence, true, uses, false, uses.toDouble())
        }
        val vocab = Lexicon.entries.flatMap { e -> (listOf(e.label) + e.synonyms).map { VocabItem(it, e.label, e.category, e.valence) } }
        val intakes = generateSequence(starts[6].plusDays(13)) { it.plusDays(1) }.takeWhile { !it.isAfter(today.minusDays(1)) }
            .filter { CycleEngine.intakeDue(med, summary0, it) }.map { 1L to it }.toSet()

        return AppData(
            today = today,
            settings = Settings(onboardingDone = true, birthYear = 1986),
            periods = periods,
            medications = listOf(med),
            intakes = intakes,
            occurrences = occ,
            entries = entries,
            panics = panics,
            gad = listOf(GadRecord(today.minusDays(60), 11), GadRecord(today.minusDays(30), 9), GadRecord(today.minusDays(5), 8)),
            terms = terms,
            summary = summary0,
            parser = TermParser(vocab),
        )
    }
}
