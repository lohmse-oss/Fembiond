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
import de.fembiond.app.domain.Episode
import de.fembiond.app.domain.EventOutcome
import de.fembiond.app.domain.PlannedEvent
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
        val panics = mutableListOf<Episode>()
        val events = mutableListOf<PlannedEvent>()
        val types = listOf("Geburtstagsfeier", "Essen gehen", "Konzert", "Treffen mit Freunden", "Familienfeier")
        var evDay = starts[3].plusDays(5)
        var evId = 1L
        while (evDay.isBefore(today.plusDays(20))) {
            val outcome = if (evDay.isAfter(today)) EventOutcome.PLANNED else if (rnd.nextDouble() < 0.2) EventOutcome.CANCELLED else EventOutcome.ATTENDED
            events += PlannedEvent(evId++, evDay, types[rnd.nextInt(types.size)], if (evId == 4L) "Annas 40." else "", "", outcome)
            evDay = evDay.plusDays(9L + rnd.nextInt(8))
        }
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
            if (rnd.nextDouble() < 0.25) occ += TermOccurrence(day.atTime(14, 0), "Hitzewallung", Category.PERIMENOPAUSE, 2 + rnd.nextInt(2))
            if (rnd.nextDouble() < 0.12) occ += TermOccurrence(t, "Arbeitsstress", Category.LIFE, null, Valence.BURDEN)
            if ((info?.cycleDay ?: 0) in 8..14 && rnd.nextDouble() < 0.5) occ += TermOccurrence(t, "ausgeglichen", Category.MIND, 4)
            val ev = events.firstOrNull { e -> val d0 = de.fembiond.app.domain.daysBetween(day, e.date); d0 in 0..3 && !e.date.isAfter(today) }
            if (ev != null) {
                val d0 = de.fembiond.app.domain.daysBetween(day, ev.date)
                val boost = if (before <= 7) 1 else 0
                val lvl = (when (d0) { 0 -> 3; 1 -> 2; else -> 1 } + boost + rnd.nextInt(2)).coerceAtMost(5)
                if (rnd.nextDouble() < 0.8) panics += Episode(0, day.atTime(19, 0), 30, lvl, listOf("Herzklopfen", "Magen / Bauchgrummeln"),
                    listOf("Anstehendes Ereignis"), listOf("Atemübung"), eventId = ev.id)
            } else if (before in 1..4 && rnd.nextDouble() < 0.3) panics += Episode(
                0, day.atTime(22, 30), 15, 2 + rnd.nextInt(3), listOf("Herzrasen"), listOf("nachts aus dem Schlaf"), listOf("Atemübung"),
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
            episodes = panics,
            events = events,
            mrs = listOf(
                de.fembiond.app.domain.MrsRecord(today.minusDays(84), listOf(2, 1, 2, 1, 2, 2, 2, 1, 0, 1, 1)),
                de.fembiond.app.domain.MrsRecord(today.minusDays(56), listOf(2, 1, 3, 2, 2, 3, 2, 1, 0, 1, 1)),
                de.fembiond.app.domain.MrsRecord(today.minusDays(28), listOf(3, 1, 2, 1, 2, 2, 2, 1, 0, 1, 2)),
                de.fembiond.app.domain.MrsRecord(today.minusDays(2), listOf(2, 1, 2, 1, 1, 2, 1, 1, 0, 1, 1)),
            ),
            profile = de.fembiond.app.domain.Profile("Mia", LocalDate.of(1986, 3, 14)),
            gad = listOf(GadRecord(today.minusDays(60), 11), GadRecord(today.minusDays(30), 9), GadRecord(today.minusDays(5), 8)),
            terms = terms,
            summary = summary0,
            parser = TermParser(vocab),
        )
    }
}
