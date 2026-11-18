package de.fembiond.app.domain

import java.time.LocalDate
import java.time.Period

data class Profile(val name: String, val birthDate: LocalDate?) {
    fun age(today: LocalDate): Int? = birthDate?.let { Period.between(it, today).years }
    fun isBirthday(today: LocalDate): Boolean =
        birthDate != null && birthDate.month == today.month && birthDate.dayOfMonth == today.dayOfMonth
    fun daysUntilBirthday(today: LocalDate): Int? {
        val b = birthDate ?: return null
        var next = b.withYear(today.year)
        if (next.isBefore(today)) next = next.plusYears(1)
        return daysBetween(today, next)
    }
}

data class MrsRecord(val date: LocalDate, val answers: List<Int>) {
    val total: Int get() = answers.sum()
    val somatic: Int get() = Mrs.SOMATIC.sumOf { answers.getOrElse(it) { 0 } }
    val psychological: Int get() = Mrs.PSYCHOLOGICAL.sumOf { answers.getOrElse(it) { 0 } }
    val urogenital: Int get() = Mrs.UROGENITAL.sumOf { answers.getOrElse(it) { 0 } }
}

/**
 * Menopause Rating Scale (MRS): 11 Beschwerden, je 0 (keine) bis 4 (sehr stark).
 * Subskalen: somatisch (1–3, 11), psychisch (4–7), urogenital (8–10).
 */
object Mrs {
    val ITEMS = listOf(
        "Wallungen, Schwitzen" to "aufsteigende Hitze, Schweißausbrüche",
        "Herzbeschwerden" to "Herzklopfen, Herzrasen, Herzstolpern, Herzbeklemmung",
        "Schlafstörungen" to "Einschlafstörungen, Durchschlafstörungen, zu frühes Erwachen",
        "Depressive Verstimmung" to "Mutlosigkeit, Traurigkeit, Weinerlichkeit, Antriebslosigkeit, Stimmungsschwankungen",
        "Reizbarkeit" to "Nervosität, innere Anspannung, Aggressivität",
        "Ängstlichkeit" to "innere Unruhe, Panik",
        "Körperliche und geistige Erschöpfung" to "allgemeine Leistungsminderung, Gedächtnis- und Konzentrationsschwäche, Vergesslichkeit",
        "Sexualprobleme" to "Veränderung des sexuellen Verlangens, der Betätigung und Befriedigung",
        "Harnwegsbeschwerden" to "Beschwerden beim Wasserlassen, häufiger Harndrang, unwillkürlicher Harnabgang",
        "Trockenheit der Scheide" to "Gefühl der Trockenheit oder des Brennens, Beschwerden beim Geschlechtsverkehr",
        "Gelenk- und Muskelbeschwerden" to "Schmerzen im Bereich der Gelenke, rheumaähnliche Beschwerden",
    )
    val ANSWERS = listOf("keine", "leicht", "mittel", "stark", "sehr stark")
    val SOMATIC = listOf(0, 1, 2, 10)
    val PSYCHOLOGICAL = listOf(3, 4, 5, 6)
    val UROGENITAL = listOf(7, 8, 9)

    fun severity(total: Int): String = when {
        total <= 4 -> "keine bis geringe Beschwerden"
        total <= 8 -> "leichte Beschwerden"
        total <= 15 -> "mittelstarke Beschwerden"
        else -> "starke Beschwerden"
    }
}

/** Hinweise zur Perimenopause aus den Tageseinträgen. */
object PeriStats {
    val HOT_TERMS = setOf("Hitzewallung", "Nachtschweiß")

    fun countOn(occ: List<TermOccurrence>, term: String, day: LocalDate): Int =
        occ.count { it.term == term && it.date == day }

    /** Anzahl je Tag für die letzten [days] Tage (ältester zuerst). */
    fun dailyCounts(occ: List<TermOccurrence>, terms: Set<String>, today: LocalDate, days: Int): List<Int> {
        val byDay = occ.filter { it.term in terms }.groupingBy { it.date }.eachCount()
        return (days - 1 downTo 0).map { byDay[today.minusDays(it.toLong())] ?: 0 }
    }
}
