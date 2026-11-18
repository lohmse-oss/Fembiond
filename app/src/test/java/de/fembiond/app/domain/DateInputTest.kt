package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateInputTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun rangeWithoutYear() {
        assertEquals(LocalDate.of(2026, 9, 3) to LocalDate.of(2026, 9, 7), DateInput.parseRange("03.09. – 07.09.", today))
    }
    @Test fun futureDateMeansLastYear() {
        assertEquals(LocalDate.of(2025, 12, 20), DateInput.parseRange("20.12.", today)!!.first)
    }
    @Test fun rangeAcrossYearEnd() {
        val r = DateInput.parseRange("29.12. bis 2.1.26", today)!!
        assertEquals(LocalDate.of(2025, 12, 29), r.first)
        assertEquals(LocalDate.of(2026, 1, 2), r.second)
    }
    @Test fun fullDate() {
        assertEquals(LocalDate.of(2026, 3, 1), DateInput.parseRange("1.3.2026", today)!!.first)
    }
    @Test fun invalid() { assertNull(DateInput.parseRange("gestern", today)); assertNull(DateInput.parseRange("31.2.", today)) }
    @Test fun warnings() {
        val w = DateInput.warnings(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9), LocalDate.of(2026, 6, 1)))
        assertEquals(2, w.size)
        assertTrue(w[0].contains("Tippfehler"))
    }
}
