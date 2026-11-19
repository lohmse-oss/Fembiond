package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleKnowledgeTest {

    private fun fact(list: List<CycleFact>, id: String) = list.first { it.id == id }

    @Test
    fun textbookCycleHasOvulationOnDay14() {
        val f = CycleKnowledge.facts(28, 5)
        assertEquals(14, fact(f, "ovulation").fromDay)
        assertEquals(9 to 14, fact(f, "fertile").let { it.fromDay to it.toDay })
        assertEquals(20 to 22, fact(f, "lab-progesterone").let { it.fromDay to it.toDay })
        assertEquals(24 to 28, fact(f, "pms").let { it.fromDay to it.toDay })
        assertEquals(28, fact(f, "next").fromDay)
    }

    @Test
    fun shortCycleShiftsOvulationEarlier() {
        val f = CycleKnowledge.facts(24, 5)
        assertEquals(10, fact(f, "ovulation").fromDay)
        assertEquals(6 to 8, fact(f, "follicular").let { it.fromDay to it.toDay })
        assertTrue(CycleKnowledge.factsFor(10, 24, 5).any { it.id == "ovulation" })
        assertFalse(CycleKnowledge.factsFor(15, 24, 5).any { it.id == "fertile" })
    }

    @Test
    fun rangesStayInsideCycle() {
        for (l in 18..45) for (p in 2..7) {
            CycleKnowledge.facts(l, p).forEach {
                assertTrue("${it.id} $l/$p", it.fromDay in 1..it.toDay && it.toDay <= l)
            }
        }
    }

    @Test
    fun cyclicProgesteroneAddsIntakeAndWithdrawal() {
        val med = MedicationPhase(name = "Progesteron", form = "oral", dose = "100 mg", schedule = ScheduleType.CYCLIC,
            cycleDayFrom = 14, cycleDayTo = 25, start = LocalDate.of(2026, 1, 1))
        val f = CycleKnowledge.facts(28, 5, med)
        assertEquals(14 to 25, fact(f, "intake").let { it.fromDay to it.toDay })
        assertEquals(27, fact(f, "withdrawal").fromDay)
        assertTrue(fact(f, "lab-progesterone").text.contains("nicht aussagekräftig"))
    }

    @Test
    fun overdueDayGetsLateFact() {
        val f = CycleKnowledge.factsFor(31, 26, 5)
        assertEquals(listOf("late"), f.map { it.id })
    }

    @Test
    fun forDateUsesRealLengthOfPastCycle() {
        val s = LocalDate.of(2026, 1, 1)
        val periods = listOf(0, 24, 48, 72).map { PeriodRecord(start = s.plusDays(it.toLong()), end = s.plusDays(it + 4L)) }
        val today = s.plusDays(80)
        val summary = CycleEngine.analyze(periods, emptyList(), today)
        val k = CycleKnowledge.forDate(summary, emptyList(), s.plusDays(9), today)
        assertNotNull(k)
        assertEquals(10, k!!.cycleDay)
        assertEquals(24, k.length)
        assertTrue(k.facts.any { it.id == "ovulation" })
    }
}
