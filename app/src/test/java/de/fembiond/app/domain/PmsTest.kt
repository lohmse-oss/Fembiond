package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PmsTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val periods = (0..4).map { PeriodRecord(start = start.plusDays(it * 28L), end = start.plusDays(it * 28L + 4)) }
    private val today = start.plusDays(4 * 28L + 14)
    private val summary = CycleEngine.analyze(periods, emptyList(), today)

    private fun occ(d: LocalDate, t: String, cat: Category) = TermOccurrence(d.atTime(20, 0), t, cat, 3)

    private fun build(withPms: Boolean, many: Boolean = false): List<TermOccurrence> {
        val out = mutableListOf<TermOccurrence>()
        for (k in 1..4) {
            val p = start.plusDays(k * 28L)
            for (i in 1..5) {
                val d = p.minusDays(i.toLong())
                out += occ(d, ScaleTerms.SLEEP_QUALITY, Category.SLEEP_ENERGY)
                if (withPms) {
                    out += occ(d, "gereizt", Category.MIND)
                    out += occ(d, "Brustspannen", Category.BODY)
                    if (many) listOf("traurig" to Category.MIND, "Blähungen" to Category.BODY, "erschöpft" to Category.SLEEP_ENERGY)
                        .forEach { (t, c) -> out += occ(d, t, c) }
                }
            }
            for (i in 3..9) out += occ(p.plusDays(i.toLong()), "ausgeglichen", Category.MIND)
        }
        return out
    }

    @Test
    fun pmsPatternInThreeConsecutiveCycles() {
        val r = Pms.analyze(summary, build(true), emptyList(), today)
        assertEquals(4, r.assessed)
        assertEquals(4, r.typical)
        assertTrue(r.verdict.startsWith("PMS-typisches Muster"))
        assertNull(r.pmddHint)
        assertTrue("gereizt" in r.typicalSymptoms)
    }

    @Test
    fun manySymptomsGivePmddHint() {
        val r = Pms.analyze(summary, build(true, many = true), emptyList(), today)
        assertNotNull(r.pmddHint)
    }

    @Test
    fun noPatternWithoutSymptoms() {
        val r = Pms.analyze(summary, build(false), emptyList(), today)
        assertEquals(0, r.typical)
        assertTrue(r.verdict.startsWith("Bisher kein"))
    }

    @Test
    fun symptomsPersistingAfterPeriodAreNotPms() {
        val o = build(true) + (1..4).flatMap { k -> (3..9).map { occ(start.plusDays(k * 28L + it), "gereizt", Category.MIND) } }
        val r = Pms.analyze(summary, o, emptyList(), today)
        assertEquals(0, r.typical)
    }

    @Test
    fun categoryFilterRemovesUnrest() {
        val ep = listOf(Episode(time = today.atTime(10, 0), durationMinutes = null, level = 3, symptoms = emptyList(), triggers = emptyList(), helped = emptyList()))
        val base = AnalysisInput(summary, build(true), ep, emptyList(), emptyList(), start, today)
        assertEquals(1, Analytics.analyze(base).unrestTotal)
        val body = Analytics.analyze(base.copy(categories = setOf(Category.BODY)))
        assertEquals(0, body.unrestTotal)
        assertTrue(body.heatmap.rows.all { it.category == Category.BODY })
    }
}
