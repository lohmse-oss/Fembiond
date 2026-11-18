package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnrestPhaseTest {
    private val start = LocalDate.of(2026, 1, 5)
    private val periods = (0..3).map { PeriodRecord(start = start.plusDays(it * 24L), end = start.plusDays(it * 24L + 6)) }
    private val today = start.plusDays(3 * 24L + 10)
    private val summary = CycleEngine.analyze(periods, emptyList(), today)

    private fun ep(d: LocalDate, l: Int) = Episode(time = d.atTime(12, 0), durationMinutes = null, level = l, symptoms = emptyList(), triggers = emptyList(), helped = emptyList())

    @Test
    fun meanLevelOnlyFromEpisodesAndNoFrequencyWithoutCalmDays() {
        // Nur Unruhe-Tage erfasst (wie bei reiner Episoden-Erfassung)
        val eps = listOf(ep(start.plusDays(2), 2), ep(start.plusDays(3), 4), ep(start.plusDays(20), 1), ep(start.plusDays(21), 1))
        val r = Analytics.analyze(AnalysisInput(summary, emptyList(), eps, emptyList(), emptyList(), start, today))
        val men = r.unrestByPhase.first { it.phase == Phase.MENSTRUATION }
        assertEquals(3.0, men.meanLevel!!, 0.001)            // (2+4)/2 – Tage ohne Einträge zählen nicht
        assertEquals(2, men.daysTracked)
        assertNull(men.relativeFrequency)                      // ohne ruhige Tage kein Häufigkeitsvergleich
        assertEquals(0, r.calmDays)
    }

    @Test
    fun frequencyWithCalmDays() {
        val eps = listOf(ep(start.plusDays(20), 3), ep(start.plusDays(21), 3), ep(start.plusDays(22), 3))
        val calm = (1..12).map { TermOccurrence(start.plusDays(it.toLong()).atTime(20, 0), "ausgeglichen", Category.MIND) }
        val r = Analytics.analyze(AnalysisInput(summary, calm, eps, emptyList(), emptyList(), start, today))
        assertTrue(r.calmDays >= 3)
        val luteal = r.unrestByPhase.first { it.phase == Phase.LUTEAL }
        assertNotNull(luteal.relativeFrequency)
        assertTrue(luteal.relativeFrequency!! > 1.0)
        assertEquals(3.0, luteal.meanLevel!!, 0.001)
    }

    @Test
    fun weeklyTrendsLeaveGapsForUntrackedWeeks() {
        val eps = listOf(ep(start, 2), ep(start.plusDays(1), 4), ep(start.plusDays(21), 1))
        val r = Analytics.analyze(AnalysisInput(summary, emptyList(), eps, emptyList(), emptyList(), start, today))
        assertTrue(r.trends.first().label.startsWith("KW "))
        assertEquals(2, r.trends.first().unrestCount)
        assertEquals(3.0, r.trends.first().meanUnrestLevel!!, 0.001)
        assertNull(r.trends[1].unrestCount)                    // Woche ohne Einträge = Lücke, nicht 0
        assertNull(r.trends[1].meanUnrestLevel)
    }
}
