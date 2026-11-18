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

/**
 * Beschwerden der Perimenopause nach aktueller Studienlage (u. a. SWAN-Studie, Lancet-Serie zur Menopause 2024):
 * Neben Hitzewallungen stehen Schlafprobleme, Reizbarkeit und Stimmungsschwankungen, neue Ängste,
 * Konzentrations- und Gedächtnisprobleme („Brain Fog“), Erschöpfung, Gelenk- und Muskelschmerzen,
 * Herzklopfen, Veränderungen von Kopfschmerz/Migräne und der Blutung im Vordergrund.
 * Die Begriffe stammen aus allen Kategorien; Reihenfolge der Gruppen nach Häufigkeit in Studien.
 */
object PeriStats {
    val GROUPS: List<Pair<String, List<String>>> = listOf(
        "Schlaf" to listOf("Durchschlafprobleme", "Frühes Erwachen", "Einschlafprobleme", "schlecht geschlafen"),
        "Stimmung" to listOf("gereizt", "Wutausbrüche", "Stimmungsschwankungen", "ängstlich", "innere Unruhe", "traurig", "antriebslos", "überfordert"),
        "Konzentration & Gedächtnis" to listOf("konzentrationsschwach", "vergesslich", "Wortfindungsstörungen"),
        "Energie" to listOf("erschöpft", "müde"),
        "Schmerzen" to listOf("Gelenkschmerzen", "Kopfschmerzen", "Migräne", "Brustspannen"),
        "Herz & Kreislauf" to listOf("Herzklopfen", "Herzrasen", "Schwindel"),
        "Blutung" to listOf("Blutung stark", "Blutung sehr stark", "Zwischenblutung"),
        "Hitze" to listOf("Hitzewallung", "Nachtschweiß"),
        "Intimität & Blase" to listOf("Libido gering", "Scheidentrockenheit", "Harndrang"),
        "Weitere" to listOf("trockene Haut / Juckreiz", "Haarausfall", "Gewichtszunahme", "Tinnitus", "Kribbeln / Missempfindungen", "trockene Augen", "Blähungen", "Wassereinlagerungen"),
    )
    val SYMPTOMS: Set<String> = GROUPS.flatMap { it.second }.toSet()
    val HOT_TERMS = setOf("Hitzewallung", "Nachtschweiß")

    /** Vorschläge zur Schnellerfassung, solange noch nichts erfasst wurde. */
    val DEFAULT_QUICK = listOf("Durchschlafprobleme", "gereizt", "konzentrationsschwach", "Gelenkschmerzen", "erschöpft", "Herzklopfen")

    fun groupOf(term: String): String = GROUPS.firstOrNull { term in it.second }?.first ?: "Weitere"

    fun countOn(occ: List<TermOccurrence>, term: String, day: LocalDate): Int =
        occ.count { it.term == term && it.date == day }

    /** Anzahl je Tag für die letzten [days] Tage (ältester zuerst). */
    fun dailyCounts(occ: List<TermOccurrence>, terms: Set<String>, today: LocalDate, days: Int): List<Int> {
        val byDay = occ.filter { it.term in terms }.groupingBy { it.date }.eachCount()
        return (days - 1 downTo 0).map { byDay[today.minusDays(it.toLong())] ?: 0 }
    }

    data class SymptomStat(val term: String, val group: String, val days: Int, val count: Int, val lastDate: LocalDate)

    /** Nur Beschwerden, zu denen es Einträge gibt – nach Anzahl der Tage sortiert. */
    fun symptomStats(occ: List<TermOccurrence>, from: LocalDate, to: LocalDate): List<SymptomStat> =
        occ.filter { it.term in SYMPTOMS && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .groupBy { it.term }
            .map { (t, l) -> SymptomStat(t, groupOf(t), l.map { it.date }.toSet().size, l.size, l.maxOf { it.date }) }
            .sortedWith(compareByDescending<SymptomStat> { it.days }.thenByDescending { it.lastDate })

    /** Schnellerfassung: zuerst die eigenen häufigsten Beschwerden, sonst Vorschläge. */
    fun quickTerms(occ: List<TermOccurrence>, today: LocalDate, max: Int = 6): List<String> {
        val own = symptomStats(occ, today.minusDays(120), today).map { it.term }
        return (own + DEFAULT_QUICK).distinct().take(max)
    }
}
