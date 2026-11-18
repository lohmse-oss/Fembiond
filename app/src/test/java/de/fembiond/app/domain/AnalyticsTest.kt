package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {

    private val start = LocalDate.of(2026, 1, 1)
    private val periods = (0..4).map { PeriodRecord(start = start.plusDays(it * 28L), end = start.plusDays(it * 28L + 4)) }

    private fun build(): AnalysisInput {
        val summary = CycleEngine.analyze(periods, emptyList(), start.plusDays(4 * 28L + 3))
        val occ = mutableListOf<TermOccurrence>()
        val panics = mutableListOf<PanicRecord>()
        for (c in 0 until 4) {
            val cs = start.plusDays(c * 28L)
            for (d in 0 until 28) {
                val date = cs.plusDays(d.toLong())
                occ += TermOccurrence(date.atTime(20, 0), ScaleTerms.SLEEP_QUALITY, Category.SLEEP_ENERGY, 3)
                if (d >= 21) occ += TermOccurrence(date.atTime(9, 0), "innere Unruhe", Category.MIND, 4)
                if (d in 5..7) occ += TermOccurrence(date.atTime(9, 0), "gereizt", Category.MIND, 2)
                if (d == 25) panics += PanicRecord(time = date.atTime(22, 0), durationMinutes = 10, intensity = 3,
                    symptoms = listOf("Herzrasen"), triggers = emptyList(), helped = emptyList())
            }
        }
        return AnalysisInput(summary, occ, panics, emptyList(), emptyList(), start, start.plusDays(4 * 28L + 3))
    }

    @Test
    fun premenstrualIncreaseIsDetected() {
        val r = Analytics.analyze(build())
        val unruhe = r.drsp.first { it.term == "innere Unruhe" }
        assertTrue(unruhe.significant)
        assertTrue(unruhe.premenstrual > unruhe.postmenstrual)
        assertTrue(r.insights.any { it.contains("innere Unruhe") })
    }

    @Test
    fun panicClustersInLutealPhase() {
        val r = Analytics.analyze(build())
        assertEquals(4, r.panicTotal)
        val luteal = r.panicByPhase.first { it.phase == Phase.LUTEAL }
        assertEquals(4, luteal.count)
        assertTrue((luteal.relativeFrequency ?: 0.0) > 1.5)
    }

    @Test
    fun heatmapAndCycleStats() {
        val r = Analytics.analyze(build())
        assertEquals(28.0, r.cycles.median!!, 0.01)
        val row = r.heatmap.rows.first { it.term == "innere Unruhe" }
        assertTrue(row.cells[24] > 0.9)
        assertTrue(row.cells[2] < 0.1)
    }

    @Test
    fun filterByCategory() {
        val r = Analytics.analyze(build().copy(categories = setOf(Category.BODY)))
        assertTrue(r.heatmap.rows.none { it.term == "gereizt" })
    }

    @Test
    fun slopeOfIncreasingSeries() {
        assertEquals(2.0, Analytics.slope(listOf(26.0, 28.0, 30.0, 32.0)), 0.001)
    }
}
