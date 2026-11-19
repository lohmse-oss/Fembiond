package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val periods = (0..5).map { PeriodRecord(start = start.plusDays(it * 28L), end = start.plusDays(it * 28L + 4)) }
    private val today = start.plusDays(5 * 28L + 18) // Zyklustag 19
    private val summary = CycleEngine.analyze(periods, emptyList(), today)

    private fun ep(d: LocalDate, level: Int, eventId: Long? = null, helped: List<String> = emptyList()) =
        Episode(time = d.atTime(18, 0), durationMinutes = null, level = level, symptoms = emptyList(), triggers = emptyList(), helped = helped, eventId = eventId)

    @Test
    fun lateLutealEventWithBadHistoryIsElevated() {
        val past = (1L..3L).map { PlannedEvent(it, start.plusDays((it - 1) * 28 + 10), "Konzert") }
        val eps = past.map { ep(it.date, 4, it.id, listOf("Atemübung")) }
        val upcoming = PlannedEvent(9, today.plusDays(5), "Konzert") // Zyklustag 24 → 5 Tage vor der Periode
        val r = StressForecast.upcoming(summary, past + upcoming, eps, emptyList(), today)
        assertEquals(1, r.size)
        assertTrue(r[0].elevated)
        assertTrue(r[0].factors.any { it.contains("vor der Periode") })
        assertTrue(r[0].factors.any { it.contains("Konzert") })
        assertTrue(r[0].tips.first().contains("Atemübung"))
    }

    @Test
    fun relaxedEventHasNoHint() {
        val e = PlannedEvent(1, start.plusDays(5 * 28L + 25 + 10), "Kino / Theater") // weit weg
        val r = StressForecast.upcoming(summary, listOf(PlannedEvent(2, today.plusDays(1), "Kino / Theater")), emptyList(), emptyList(), today)
        assertTrue(r.isEmpty())
        assertFalse(e.title.isNotBlank())
    }

    @Test
    fun weeklyReviewComparesToPreviousWeeks() {
        val eps = (0..27).filter { it % 4 == 0 }.map { ep(today.minusDays(7L + it), 2) } + (0..5).map { ep(today.minusDays(it.toLong()), 3) }
        val occ = (0..6).map { TermOccurrence(today.minusDays(it.toLong()).atTime(20, 0), ScaleTerms.SLEEP_QUALITY, Category.BODY, 2) } +
            (7..34).map { TermOccurrence(today.minusDays(it.toLong()).atTime(20, 0), ScaleTerms.SLEEP_QUALITY, Category.BODY, 4) } +
            listOf(TermOccurrence(today.atTime(9, 0), "Hitzewallung", Category.BODY))
        val r = WeeklyReviewBuilder.build(summary, occ, eps, emptyList(), today)
        assertEquals(6, r.unrestCount)
        assertTrue(r.observations.any { it.startsWith("Mehr Unruhe") })
        assertTrue(r.observations.any { it.contains("schlechter als sonst") })
        assertEquals(1, r.hotFlashes)
    }

    @Test
    fun emptyWeek() {
        val r = WeeklyReviewBuilder.build(summary, emptyList(), emptyList(), emptyList(), today)
        assertEquals(0, r.trackedDays)
        assertNull(r.meanSleep)
    }

    @Test
    fun mrsScoring() {
        val m = MrsRecord(today, listOf(3, 1, 2, 1, 2, 3, 2, 0, 0, 1, 1))
        assertEquals(16, m.total)
        assertEquals(7, m.somatic)
        assertEquals(8, m.psychological)
        assertEquals(1, m.urogenital)
        assertEquals("starke Beschwerden", Mrs.severity(m.total))
    }

    @Test
    fun profileAgeAndBirthday() {
        val p = Profile("B", LocalDate.of(1980, 6, 15))
        assertEquals(45, p.age(LocalDate.of(2026, 6, 14)))
        assertEquals(46, p.age(LocalDate.of(2026, 6, 15)))
        assertTrue(p.isBirthday(LocalDate.of(2026, 6, 15)))
        assertEquals(1, p.daysUntilBirthday(LocalDate.of(2026, 6, 14)))
    }
}

class PeriStatsTest {
    private val today = java.time.LocalDate.of(2026, 11, 18)
    private fun o(d: Long, t: String, c: Category) = TermOccurrence(today.minusDays(d).atTime(9, 0), t, c)

    @Test
    fun onlySymptomsWithEntriesAndResearchDefaults() {
        assertEquals(PeriStats.DEFAULT_QUICK, PeriStats.quickTerms(emptyList(), today))
        val occ = listOf(o(1, "Gelenkschmerzen", Category.BODY), o(2, "Gelenkschmerzen", Category.BODY), o(1, "Frühes Erwachen", Category.BODY), o(1, "Krämpfe", Category.BODY))
        val stats = PeriStats.symptomStats(occ, today.minusDays(30), today)
        assertEquals(listOf("Gelenkschmerzen", "Frühes Erwachen"), stats.map { it.term })
        assertEquals("Schmerzen", stats.first().group)
        assertEquals("Gelenkschmerzen", PeriStats.quickTerms(occ, today).first())
    }
}
