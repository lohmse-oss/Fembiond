package de.fembiond.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ClassificationTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val periods = (0..5).map { PeriodRecord(start = start.plusDays(it * 28L), end = start.plusDays(it * 28L + 4)) }
    private val today = start.plusDays(5 * 28L + 10)
    private val summary = CycleEngine.analyze(periods, emptyList(), today)

    private fun o(d: LocalDate, t: String, c: Category = Category.BODY, i: Int? = null) = TermOccurrence(d.atTime(20, 0), t, c, i)

    /** Jeden Tag ein ruhiger Eintrag, damit alle Tage erfasst sind. */
    private fun baseline() = (0 until 5 * 28 + 10).map { o(start.plusDays(it.toLong()), "ausgeglichen", Category.MIND) }

    private fun run(occ: List<TermOccurrence>) =
        Classification.analyze(summary, baseline() + occ, emptyList(), today, start, today, age = 40)

    @Test
    fun crampsDuringPeriodAreUsual() {
        val occ = (0..4).flatMap { k -> (0..1).map { o(start.plusDays(k * 28L + it), "Krämpfe", i = 2) } }
        val s = run(occ).symptoms.first { it.term == "Krämpfe" }
        assertTrue(SymptomClass.PERIOD_USUAL in s.classes)
        assertFalse(SymptomClass.PERIOD_UNUSUAL in s.classes)
    }

    @Test
    fun severeCrampsAreUnusual() {
        val occ = (0..4).flatMap { k -> (0..1).map { o(start.plusDays(k * 28L + it), "Krämpfe", i = 5) } }
        assertTrue(SymptomClass.PERIOD_UNUSUAL in run(occ).symptoms.first { it.term == "Krämpfe" }.classes)
    }

    @Test
    fun premenstrualIrritabilityIsPms() {
        val occ = (1..5).flatMap { k -> (1..4).map { o(start.plusDays(k * 28L - it), "gereizt", Category.MIND) } }
        val s = run(occ).symptoms.first { it.term == "gereizt" }
        assertTrue(SymptomClass.PMS in s.classes)
        assertFalse(SymptomClass.PERI in s.classes)
    }

    @Test
    fun nightSweatsWithoutPatternPointToPerimenopause() {
        val occ = listOf(3, 10, 17, 40, 52, 66, 80, 95, 110, 120, 130).map { o(start.plusDays(it.toLong()), "Nachtschweiß") }
        val r = run(occ)
        assertTrue(SymptomClass.PERI in r.symptoms.first { it.term == "Nachtschweiß" }.classes)
        assertTrue(r.periSignals.any { it.contains("40 Jahren") })
    }

    @Test
    fun intermenstrualBleedingIsFlagged() {
        val r = run(listOf(o(start.plusDays(15), "Zwischenblutung")))
        assertTrue(r.redFlags.any { it.title.contains("zwischen") })
        assertFalse(r.symptoms.any { it.term == "Zwischenblutung" })
    }

    @Test
    fun calmTermsAreNoSymptoms() {
        assertFalse(run(emptyList()).symptoms.any { it.term == "ausgeglichen" })
    }
}
