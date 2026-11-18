package de.fembiond.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** Bewertung eines Zyklus (Periode) hinsichtlich PMS. */
data class PmsCycle(
    val periodStart: LocalDate,
    val preDays: Int,
    val postDays: Int,
    val assessable: Boolean,
    val affective: List<String>,
    val somatic: List<String>,
    val other: List<String>,
    /** Symptome vor der Periode, die danach (Tag 4–10) abgeklungen sind. */
    val relieved: Boolean,
    val pmsTypical: Boolean,
    val pmddLike: Boolean,
    val medicated: Boolean,
    val reason: String?,
)

data class PmsSymptom(val term: String, val group: String, val preRate: Double, val postRate: Double, val cycles: Int)

data class PmsResult(
    val cycles: List<PmsCycle>,
    val symptoms: List<PmsSymptom>,
    val assessed: Int,
    val typical: Int,
    val pmddLike: Int,
    val verdict: String,
    val pmddHint: String?,
) {
    /** Typische Beschwerden vor der Periode (für Hinweise auf dem Startbildschirm). */
    val typicalSymptoms: List<String> get() = symptoms.filter { it.preRate >= 0.3 && it.preRate >= it.postRate * 2 }.take(4).map { it.term }
}

/**
 * PMS-Einschätzung nach den Kriterien des American College of Obstetricians and Gynecologists (ACOG):
 * mindestens ein affektives und ein körperliches Symptom in den 5 Tagen vor der Periode, die nach Beginn
 * der Periode abklingen (hier: Tag 4–10 deutlich weniger), in 3 aufeinanderfolgenden Zyklen.
 * Hinweis auf PMDS (DSM-5) bei ≥ 5 Symptomen inkl. eines affektiven Kernsymptoms in ≥ 2 Zyklen.
 * Keine Diagnose.
 */
object Pms {
    // DSM-5 (PMDS) / ACOG: affektive Kernsymptome und weitere
    val AFFECTIVE = setOf(
        "gereizt", "Wutausbrüche", "traurig", "ängstlich", "angespannt", "innere Unruhe", "Stimmungsschwankungen", "überfordert",
        "sensibel", "Rückzug", Analytics.UNREST_LABEL,
    )
    val CORE_AFFECTIVE = setOf("gereizt", "Wutausbrüche", "traurig", "ängstlich", "angespannt", "innere Unruhe", "Stimmungsschwankungen", Analytics.UNREST_LABEL)
    val SOMATIC = setOf(
        "Brustspannen", "Blähungen", "Kopfschmerzen", "Migräne", "Wassereinlagerungen", "Gelenkschmerzen",
        "Rückenschmerzen", "Hautunreinheiten", "Gewichtszunahme",
    )
    val OTHER = setOf(
        "müde", "erschöpft", "antriebslos", "konzentrationsschwach", "vergesslich", "Heißhunger",
        "Einschlafprobleme", "Durchschlafprobleme", "Frühes Erwachen", "schlecht geschlafen", "Libido gering",
    )
    const val MIN_DAYS = 2

    fun group(term: String) = when (term) {
        in AFFECTIVE -> "Stimmung"
        in SOMATIC -> "Körper"
        else -> "Weitere"
    }

    fun analyze(
        summary: CycleSummary,
        occurrences: List<TermOccurrence>,
        episodes: List<Episode>,
        today: LocalDate,
        from: LocalDate = LocalDate.MIN,
        to: LocalDate = today,
    ): PmsResult {
        val all = AFFECTIVE + SOMATIC + OTHER
        // Tag → vorhandene PMS-Begriffe; Unruhe ab Stufe 2 zählt als affektives Symptom
        val byDay = HashMap<LocalDate, MutableSet<String>>()
        occurrences.filter { it.term in all }.forEach { byDay.getOrPut(it.date) { mutableSetOf() }.add(it.term) }
        episodes.filter { it.level >= 2 }.forEach { byDay.getOrPut(it.date) { mutableSetOf() }.add(Analytics.UNREST_LABEL) }
        val tracked = (occurrences.map { it.date } + episodes.map { it.date }).toSet()

        val cycles = summary.cycles.filter { it.index > 0 && !it.start.plusDays(9).isBefore(from) && !it.start.isAfter(to) }.map { c ->
            val p = c.start
            val pre = (1..5).map { p.minusDays(it.toLong()) }.filter { it in tracked }
            val post = (3..9).map { p.plusDays(it.toLong()) }.filter { it in tracked }
            val prevMedicated = summary.cycles.getOrNull(c.index - 1)?.medicated == true
            val reason = when {
                p.plusDays(3).isAfter(today) -> "Tag 4–10 nach dieser Periode liegt noch in der Zukunft"
                pre.size < MIN_DAYS -> "nur ${pre.size} erfasste Tage in den 5 Tagen davor (mind. $MIN_DAYS)"
                post.size < MIN_DAYS -> "nur ${post.size} erfasste Tage an Tag 4–10 (mind. $MIN_DAYS)"
                else -> null
            }
            if (reason != null) return@map PmsCycle(p, pre.size, post.size, false, emptyList(), emptyList(), emptyList(), false, false, false, prevMedicated, reason)
            fun rate(days: List<LocalDate>, t: String) = days.count { byDay[it]?.contains(t) == true }.toDouble() / days.size
            val present = all.filter { t -> rate(pre, t) > 0 }
            // „abgeklungen“: danach höchstens halb so häufig und an höchstens 30 % der Tage
            val relievedSet = present.filter { t -> val after = rate(post, t); after <= 0.3 && after <= rate(pre, t) / 2 }
            val aff = relievedSet.filter { it in AFFECTIVE }
            val som = relievedSet.filter { it in SOMATIC }
            val oth = relievedSet.filter { it in OTHER }
            val typical = aff.isNotEmpty() && som.isNotEmpty()
            val pmdd = relievedSet.size >= 5 && relievedSet.any { it in CORE_AFFECTIVE }
            PmsCycle(p, pre.size, post.size, true, aff, som, oth, relievedSet.isNotEmpty() && relievedSet.size >= present.size / 2, typical, pmdd, prevMedicated, null)
        }

        val assessed = cycles.filter { it.assessable }
        val typical = assessed.count { it.pmsTypical }
        val pmdd = assessed.count { it.pmddLike }
        // längste Serie aufeinanderfolgender PMS-typischer Zyklen (unter den auswertbaren)
        var run = 0
        var best = 0
        assessed.forEach { if (it.pmsTypical) { run++; best = maxOf(best, run) } else run = 0 }

        val symptoms = if (assessed.isEmpty()) emptyList() else all.mapNotNull { t ->
            var preHits = 0; var preN = 0; var postHits = 0; var postN = 0; var cyc = 0
            assessed.forEach { c ->
                val pre = (1..5).map { c.periodStart.minusDays(it.toLong()) }.filter { it in tracked }
                val post = (3..9).map { c.periodStart.plusDays(it.toLong()) }.filter { it in tracked }
                val ph = pre.count { byDay[it]?.contains(t) == true }
                preHits += ph; preN += pre.size
                postHits += post.count { byDay[it]?.contains(t) == true }; postN += post.size
                if (ph > 0) cyc++
            }
            if (preHits == 0 && postHits == 0) null
            else PmsSymptom(t, group(t), preHits.toDouble() / preN, postHits.toDouble() / postN, cyc)
        }.sortedByDescending { it.preRate - it.postRate }

        val verdict = when {
            assessed.isEmpty() -> "Noch keine Periode mit genug Einträgen in den 5 Tagen davor und an Tag 4–10 danach – noch keine Aussage möglich."
            best >= 3 -> "PMS-typisches Muster in $typical von ${assessed.size} ausgewerteten Zyklen, davon $best in Folge: Beschwerden der Stimmung und des Körpers vor der Periode, die danach abklingen. Das entspricht den üblichen Kriterien für PMS – sprich es gern ärztlich an."
            typical > 0 -> "Hinweise auf PMS in $typical von ${assessed.size} ausgewerteten Zyklen. Für eine Einordnung sollte sich das Muster in 3 Zyklen hintereinander zeigen."
            assessed.any { it.affective.isNotEmpty() || it.somatic.isNotEmpty() } -> "Einzelne Beschwerden vor der Periode, aber bisher kein typisches PMS-Muster (dafür braucht es Stimmungs- und Körperbeschwerden zusammen, die nach der Periode abklingen)."
            else -> "Bisher kein PMS-typisches Muster in ${assessed.size} ausgewerteten Zyklen."
        }
        val pmddHint = if (pmdd >= 2) "In $pmdd Zyklen traten vor der Periode 5 oder mehr Beschwerden auf (darunter Stimmung), die danach abklangen. Das kann auf eine prämenstruelle dysphorische Störung (PMDS) hindeuten – bitte ärztlich abklären." else null
        return PmsResult(cycles, symptoms, assessed.size, typical, pmdd, verdict, pmddHint)
    }

    fun pct(v: Double) = "${(v * 100).roundToInt()} %"
}
