package de.fembiond.app.domain

import java.time.LocalDate

/** Liest handschriftlich übliche Datumsangaben: „3.9.“, „03.09.2026“, „3.9.–7.9.“, „3.9. bis 7.9.25“. */
object DateInput {
    private val single = Regex("""(\d{1,2})\.(\d{1,2})\.?(\d{2,4})?""")

    fun parseRange(text: String, today: LocalDate): Pair<LocalDate, LocalDate?>? {
        val matches = single.findAll(text).toList()
        if (matches.isEmpty()) return null
        val second = matches.getOrNull(1)
        val endHasYear = second?.groupValues?.get(3)?.isNotEmpty() == true
        val startHasYear = matches[0].groupValues[3].isNotEmpty()
        val start: LocalDate
        val end: LocalDate?
        if (second != null && endHasYear && !startHasYear) {
            end = toDate(second, today, null) ?: return null
            var s = toDate(matches[0], today, end.year.toString()) ?: return null
            if (s.isAfter(end)) s = s.minusYears(1)
            start = s
        } else {
            start = toDate(matches[0], today, null) ?: return null
            end = second?.let { toDate(it, today, if (startHasYear) start.year.toString() else null) }?.let { e ->
                var x = e
                while (x.isBefore(start)) x = x.plusYears(1)
                x
            }
        }
        if (end != null && daysBetween(start, end) > 14) return start to null
        return start to end
    }

    private fun toDate(m: MatchResult, today: LocalDate, fallbackYear: String?): LocalDate? {
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val yearText = m.groupValues[3].ifEmpty { fallbackYear.orEmpty() }
        return runCatching {
            if (yearText.isNotEmpty()) {
                val y = yearText.toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(y, month, day)
            } else {
                val d = LocalDate.of(today.year, month, day)
                if (d.isAfter(today)) d.minusYears(1) else d
            }
        }.getOrNull()
    }

    /** Plausibilitätsprüfung für eine Liste von Periodenbeginnen. */
    fun warnings(starts: List<LocalDate>): List<String> {
        val sorted = starts.sorted()
        return sorted.zipWithNext().mapNotNull { (a, b) ->
            val d = daysBetween(a, b)
            when {
                d < 15 -> "Zwischen ${a.dayOfMonth}.${a.monthValue}. und ${b.dayOfMonth}.${b.monthValue}. liegen nur $d Tage – Tippfehler?"
                d > 90 -> "Zwischen ${a.dayOfMonth}.${a.monthValue}. und ${b.dayOfMonth}.${b.monthValue}. liegen $d Tage – fehlt eine Periode?"
                else -> null
            }
        }
    }
}
