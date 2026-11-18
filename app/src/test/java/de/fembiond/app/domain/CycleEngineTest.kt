package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleEngineTest {

    private fun periods(start: LocalDate, vararg lengths: Int): List<PeriodRecord> {
        var d = start
        val out = mutableListOf(PeriodRecord(start = d, end = d.plusDays(4)))
        for (l in lengths) {
            d = d.plusDays(l.toLong())
            out += PeriodRecord(start = d, end = d.plusDays(4))
        }
        return out
    }

    @Test
    fun regularCyclesGiveBaselineAndPrediction() {
        val start = LocalDate.of(2026, 1, 1)
        val p = periods(start, 28, 28, 29, 27, 28)
        val lastStart = p.last().start
        val today = lastStart.plusDays(9)
        val s = CycleEngine.analyze(p, emptyList(), today)
        assertEquals(28, s.baselineLength)
        assertEquals(5, s.baselinePeriodLength)
        val c = s.current!!
        assertEquals(10, c.cycleDay)
        assertEquals(Phase.FOLLICULAR, c.phase)
        assertTrue(!c.prediction.from.isAfter(lastStart.plusDays(28)))
        assertTrue(!c.prediction.to.isBefore(lastStart.plusDays(28)))
        assertEquals(StrawStage.NONE, s.straw)
        assertTrue(s.cycles.none { it.deviation != null })
    }

    @Test
    fun phasesForTypicalCycle() {
        assertEquals(Phase.MENSTRUATION, CycleEngine.phaseFor(3, 28, 5))
        assertEquals(Phase.FOLLICULAR, CycleEngine.phaseFor(8, 28, 5))
        assertEquals(Phase.OVULATION, CycleEngine.phaseFor(14, 28, 5))
        assertEquals(Phase.LUTEAL, CycleEngine.phaseFor(22, 28, 5))
    }

    @Test
    fun detectsDeviationAndEarlyTransition() {
        val p = periods(LocalDate.of(2025, 6, 1), 28, 28, 38, 27, 36, 26, 28)
        val s = CycleEngine.analyze(p, emptyList(), p.last().start.plusDays(3))
        assertEquals(StrawStage.EARLY, s.straw)
        assertTrue(s.cycles.any { it.deviation == Deviation.LONG })
    }

    @Test
    fun detectsLateTransitionOnLongGap() {
        val p = periods(LocalDate.of(2025, 1, 1), 28, 30, 65)
        val s = CycleEngine.analyze(p, emptyList(), p.last().start.plusDays(2))
        assertEquals(StrawStage.LATE, s.straw)
    }

    @Test
    fun cyclicProgesteronePredictsWithdrawalBleed() {
        val p = periods(LocalDate.of(2026, 1, 1), 28, 28, 28)
        val start = p.last().start
        val med = MedicationPhase(
            name = "Progesteron", form = "oral", dose = "", schedule = ScheduleType.CYCLIC,
            cycleDayFrom = 14, cycleDayTo = 25, start = start,
        )
        val s = CycleEngine.analyze(p, listOf(med), start.plusDays(15))
        val pred = s.current!!.prediction
        assertTrue(pred.withdrawalBleed)
        // letzte Einnahme an Zyklustag 25 → Blutung 2–7 Tage später
        assertEquals(start.plusDays(26), pred.from)
        assertEquals(start.plusDays(31), pred.to)
        assertTrue(s.cycles.last().medicated)
        assertTrue(CycleEngine.intakeDue(med, s, start.plusDays(13)))
        assertFalse(CycleEngine.intakeDue(med, s, start.plusDays(12)))
        assertFalse(CycleEngine.intakeDue(med, s, start.plusDays(25)))
    }

    @Test
    fun medicatedCyclesExcludedFromBaseline() {
        val p = periods(LocalDate.of(2026, 1, 1), 28, 28, 28, 40)
        val med = MedicationPhase(
            name = "P", form = "", dose = "", schedule = ScheduleType.CYCLIC, cycleDayFrom = 14, cycleDayTo = 25,
            start = p[3].start, end = p[3].start.plusDays(39),
        )
        val s = CycleEngine.analyze(p, listOf(med), p.last().start.plusDays(2))
        assertEquals(28, s.baselineLength)
        assertTrue(s.cycles[3].medicated)
    }

    @Test
    fun overdueShiftsPrediction() {
        val p = periods(LocalDate.of(2026, 1, 1), 28, 28, 28, 28)
        val today = p.last().start.plusDays(40)
        val s = CycleEngine.analyze(p, emptyList(), today)
        assertTrue(s.current!!.prediction.overdueDays > 0)
        assertEquals(today, s.current!!.prediction.from)
    }

    @Test
    fun dayInfoForCompletedCycle() {
        val p = periods(LocalDate.of(2026, 1, 1), 30)
        val s = CycleEngine.analyze(p, emptyList(), LocalDate.of(2026, 2, 5))
        val info = CycleEngine.dayInfo(s, LocalDate.of(2026, 1, 25))!!
        assertEquals(25, info.cycleDay)
        assertEquals(6, info.daysBeforeNext)
        assertEquals(Phase.LUTEAL, info.phase)
        assertNull(CycleEngine.dayInfo(s, LocalDate.of(2025, 12, 1)))
    }

    @Test
    fun emptyDataHasNoCurrentState() {
        val s = CycleEngine.analyze(emptyList(), emptyList(), LocalDate.now())
        assertNull(s.current)
        assertEquals(StrawStage.INSUFFICIENT, s.straw)
        assertNotNull(s)
    }
}
