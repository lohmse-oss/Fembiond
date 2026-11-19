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
        val panics = mutableListOf<Episode>()
        for (c in 0 until 4) {
            val cs = start.plusDays(c * 28L)
            for (d in 0 until 28) {
                val date = cs.plusDays(d.toLong())
                occ += TermOccurrence(date.atTime(20, 0), ScaleTerms.SLEEP_QUALITY, Category.BODY, 3)
                if (d >= 21) occ += TermOccurrence(date.atTime(9, 0), "innere Unruhe", Category.MIND, 4)
                if (d in 5..7) occ += TermOccurrence(date.atTime(9, 0), "gereizt", Category.MIND, 2)
                if (d == 25) panics += Episode(time = date.atTime(22, 0), durationMinutes = 10, level = 5,
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
        val luteal = r.unrestByPhase.first { it.phase == Phase.LUTEAL }
        assertEquals(4, luteal.count)
        assertEquals(4, luteal.panicCount)
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
    fun unrestRisesBeforeEvents() {
        val base = build()
        val events = (0 until 4).map { c -> PlannedEvent(id = c + 1L, date = start.plusDays(c * 28L + 12), type = "Konzert") }
        val eps = events.flatMap { e ->
            listOf(
                Episode(time = e.date.minusDays(2).atTime(18, 0), durationMinutes = null, level = 2, symptoms = emptyList(), triggers = emptyList(), helped = emptyList()),
                Episode(time = e.date.atTime(17, 0), durationMinutes = null, level = 4, symptoms = emptyList(), triggers = emptyList(), helped = emptyList(), eventId = e.id),
            )
        }
        val r = Analytics.analyze(base.copy(episodes = base.episodes + eps, events = events))
        val day0 = r.anticipation.first { it.offset == 0 }
        val week = r.anticipation.first { it.offset == -6 }
        assertEquals(4.0, day0.meanMaxLevel!!, 0.01)
        assertEquals(0.0, week.meanMaxLevel!!, 0.01)
        val konzert = r.eventTypes.first { it.type == "Konzert" }
        assertEquals(4, konzert.events)
        assertEquals(1.0, konzert.shareWithUnrest, 0.01)
        assertEquals(listOf(0, 4, 0, 4, 4), r.levelCounts)
        assertTrue(r.insights.any { it.contains("Ereignissen") })
    }

    @Test
    fun drspPairsAcrossCycleBoundary() {
        // Einträge nur in der Woche vor der letzten Periode und an Tag 4–10 danach (laufender Zyklus)
        val p = start.plusDays(4 * 28L)
        val today = p.plusDays(12)
        val summary = CycleEngine.analyze(periods, emptyList(), today)
        val occ = (1..7).map { TermOccurrence(p.minusDays(it.toLong()).atTime(20, 0), "gereizt", Category.MIND, 3) } +
            (3..9).map { TermOccurrence(p.plusDays(it.toLong()).atTime(20, 0), ScaleTerms.SLEEP_QUALITY, Category.BODY, 3) }
        val r = Analytics.analyze(AnalysisInput(summary, occ, emptyList(), emptyList(), emptyList(), start, today))
        val pair = r.drspPairs.first { it.periodStart == p }
        assertTrue(pair.counted)
        assertEquals(7, pair.preDays)
        assertEquals(7, pair.postDays)
        val g = r.drsp.first { it.term == "gereizt" }
        assertEquals(1, g.cycles)
        assertEquals(7, g.preHits)
        assertEquals(0, g.postHits)
        assertTrue(g.verdict.contains("bestätigen"))
        // frühere Perioden ohne Einträge zählen nicht – mit Begründung
        assertTrue(r.drspPairs.filter { it.periodStart != p }.all { !it.counted && it.reason != null })
    }

    @Test
    fun drspVerdictForSlightlyLowerBefore() {
        val d = DrspResult("Unruhe-Episode", Category.MIND, 1.2, 1.4, 1)
        assertEquals(-14, d.changePercent!!.toInt())
        assertTrue(d.verdict.contains("ruhiger"))
    }

    @Test
    fun projectDayForFutureDate() {
        val s = build().summary
        val future = s.current!!.cycleStart.plusDays(40)
        val info = CycleEngine.projectDay(s, future)!!
        assertTrue(info.predicted)
        assertTrue(info.cycleDay in 1..28)
    }

    @Test
    fun slopeOfIncreasingSeries() {
        assertEquals(2.0, Analytics.slope(listOf(26.0, 28.0, 30.0, 32.0)), 0.001)
    }
}
