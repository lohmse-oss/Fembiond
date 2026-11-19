package de.fembiond.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

enum class SymptomClass(val label: String, val description: String) {
    PERIOD_UNUSUAL(
        "Auffällig – ärztlich abklären",
        "Beschwerden oder Blutungsmuster, die über übliche Periodenbeschwerden hinausgehen.",
    ),
    PERIOD_USUAL(
        "Übliche Periodenbeschwerden",
        "Treten gehäuft während der Blutung auf und sind meist leicht bis mittel – das ist häufig und in der Regel harmlos.",
    ),
    PMS(
        "PMS-Muster",
        "Gehäuft in den 5 Tagen vor der Periode und danach deutlich seltener (Kriterien nach ACOG).",
    ),
    PERI(
        "Spricht eher für Perimenopause",
        "Ohne Bezug zu einer Zyklusphase, über die Zeit zunehmend oder in der Forschung typisch für die Perimenopause.",
    ),
    UNCLEAR(
        "Noch ohne klares Muster",
        "Zu wenige Tage oder keine Häufung in einer Zyklusphase.",
    ),
}

/** Einordnung einer Beschwerde anhand ihres zeitlichen Musters im Zyklus. */
data class SymptomAssessment(
    val term: String,
    val category: Category,
    val days: Int,
    /** Anteil der Tage während der Blutung / in den 5 Tagen davor / übrige Tage (nur Tage mit Zyklusbezug). */
    val periodShare: Double,
    val preShare: Double,
    val otherShare: Double,
    val meanIntensity: Double?,
    val cycles: Int,
    val classes: List<SymptomClass>,
    val reasons: List<String>,
)

/** Warnzeichen auf Zyklus- und Blutungsebene. */
data class RedFlag(val title: String, val text: String, val source: String)

data class ClassificationResult(
    val symptoms: List<SymptomAssessment>,
    val redFlags: List<RedFlag>,
    /** Zyklus-Signale, die zur Perimenopause passen (STRAW+10, Alter). */
    val periSignals: List<String>,
    val assessedCycles: Int,
) {
    fun of(c: SymptomClass) = symptoms.filter { c in it.classes }
    val isEmpty: Boolean get() = symptoms.isEmpty() && redFlags.isEmpty() && periSignals.isEmpty()
}

/**
 * Ordnet Beschwerden erst in der Auswertung zu – beim Eintragen muss niemand wissen, ob etwas
 * PMS, Periode oder Perimenopause ist. Grundlage: zeitliches Muster im Zyklus (Blutung,
 * 5 Tage davor, übrige Tage), Stärke, Verlauf und Forschungsstand. Keine Diagnose.
 */
object Classification {

    /** Positive oder neutrale Begriffe sind keine Beschwerden. */
    val NOT_SYMPTOMS = setOf("ausgeglichen", "fröhlich", "gut geschlafen", "energiegeladen", "Libido hoch", ScaleTerms.SLEEP_QUALITY, ScaleTerms.ENERGY)

    /** Schmerzbegriffe rund um den Unterleib (Dysmenorrhoe). */
    val PELVIC_PAIN = setOf("Krämpfe", "Unterleibsschmerzen", "Regelschmerzen")

    private const val PRE_DAYS = 5
    const val MIN_DAYS = 3

    fun analyze(
        summary: CycleSummary,
        occurrences: List<TermOccurrence>,
        episodes: List<Episode>,
        today: LocalDate,
        from: LocalDate,
        to: LocalDate,
        age: Int? = null,
        pms: PmsResult = Pms.analyze(summary, occurrences, episodes, today, from, to),
    ): ClassificationResult {
        fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        val occ = occurrences.filter { inRange(it.date) && it.category.isSymptom && it.term !in NOT_SYMPTOMS }
        val unrest = episodes.filter { inRange(it.date) && it.level >= 2 }
            .map { TermOccurrence(it.time, Analytics.UNREST_LABEL, Category.MIND, it.level) }
        val symptomOcc = (occ + unrest).filter { it.term !in Lexicon.BLEEDING_TERMS }

        // Zyklusbezug je Datum: Blutung, 5 Tage davor oder übrige Tage
        val current = summary.current
        val cyclesById = summary.cycles.associateBy { it.index }
        fun bucketOf(d: LocalDate): Pair<Char, Int>? {
            val info = CycleEngine.dayInfo(summary, d) ?: return null
            val cycle = cyclesById[info.cycleIndex] ?: return null
            if (info.cycleDay <= cycle.periodLength) return 'P' to info.cycleIndex
            val before = info.daysBeforeNext ?: current?.takeIf { cycle.nextStart == null }?.let { it.expectedLength - info.cycleDay + 1 }
            return (if (before != null && before in 1..PRE_DAYS) 'V' else 'O') to info.cycleIndex
        }
        val bucketCache = HashMap<LocalDate, Pair<Char, Int>?>()
        fun bucket(d: LocalDate) = bucketCache.getOrPut(d) { bucketOf(d) }

        val length = (current?.expectedLength ?: summary.baselineLength).coerceAtLeast(15)
        val periodLen = summary.baselinePeriodLength.coerceIn(2, length - PRE_DAYS - 1)
        val expPeriod = periodLen.toDouble() / length
        val expPre = PRE_DAYS.toDouble() / length
        val mid = from.plusDays((to.toEpochDay() - from.toEpochDay()) / 2)
        val trackedDays = (occurrences.map { it.date } + episodes.map { it.date }).filter(::inRange).toSet()
        val trackedFirst = trackedDays.count { it.isBefore(mid) }
        val trackedSecond = trackedDays.size - trackedFirst

        val pmsTerms = pms.symptoms.filter { it.cycles >= 2 && it.preRate >= 0.3 && it.preRate >= it.postRate * 2 }.associateBy { it.term }

        val symptoms = symptomOcc.groupBy { it.term }.mapNotNull { (term, list) ->
            val dates = list.map { it.date }.toSortedSet()
            if (dates.size < 2) return@mapNotNull null
            val buckets = dates.mapNotNull { d -> bucket(d) }
            val withCycle = buckets.size.coerceAtLeast(1)
            val p = buckets.count { it.first == 'P' }.toDouble() / withCycle
            val v = buckets.count { it.first == 'V' }.toDouble() / withCycle
            val o = buckets.count { it.first == 'O' }.toDouble() / withCycle
            val cycles = buckets.map { it.second }.distinct().size
            val intensities = list.mapNotNull { it.intensity }
            val mean = intensities.takeIf { it.isNotEmpty() }?.average()
            val periodIntensities = list.filter { bucket(it.date)?.first == 'P' }.mapNotNull { it.intensity }
            val severePeriodCycles = list.filter { bucket(it.date)?.first == 'P' && (it.intensity ?: 0) >= 4 }.mapNotNull { bucket(it.date)?.second }.distinct().size
            val category = list.first().category

            val classes = mutableListOf<SymptomClass>()
            val reasons = mutableListOf<String>()

            val periodBound = buckets.size >= 2 && p >= 0.5 && p >= expPeriod * 2
            val pmsBound = term in pmsTerms || (buckets.size >= MIN_DAYS && v >= 0.4 && v >= expPre * 2 && cycles >= 2)
            val noPhase = buckets.size >= MIN_DAYS && p < expPeriod * 1.6 && v < expPre * 1.6
            val first = dates.count { it.isBefore(mid) }
            val second = dates.size - first
            val rising = dates.size >= 4 && trackedFirst > 0 && trackedSecond > 0 &&
                second.toDouble() / trackedSecond >= 1.5 * first.toDouble() / trackedFirst && second >= first + 2

            if (periodBound) {
                reasons += "${pct(p)} der Tage während der Blutung"
                if (severePeriodCycles >= 2 && (term in PELVIC_PAIN || term in Pms.SOMATIC)) {
                    classes += SymptomClass.PERIOD_UNUSUAL
                    reasons += "in $severePeriodCycles Zyklen Stärke 4–5 – starke Regelschmerzen sollten abgeklärt werden"
                } else {
                    classes += SymptomClass.PERIOD_USUAL
                    periodIntensities.takeIf { it.isNotEmpty() }?.let { reasons += "Stärke während der Blutung im Schnitt ${de1(it.average())}" }
                }
            }
            if (term in PELVIC_PAIN) {
                val outside = list.filter { bucket(it.date)?.first == 'O' }.map { it.date }.distinct().size
                if (outside >= MIN_DAYS && SymptomClass.PERIOD_UNUSUAL !in classes) {
                    classes += SymptomClass.PERIOD_UNUSUAL
                    reasons += "an $outside Tagen auch außerhalb der Periode und der Tage davor"
                }
            }
            if (pmsBound) {
                classes += SymptomClass.PMS
                val s = pmsTerms[term]
                reasons += if (s != null) "vor der Periode an ${pct(s.preRate)} der erfassten Tage, an Tag 4–10 an ${pct(s.postRate)} (${s.cycles} Zyklen)"
                else "${pct(v)} der Tage in den 5 Tagen vor der Periode"
            }
            val periTypical = term in Lexicon.PERI_TYPICAL
            val periPattern = !periodBound && !pmsBound && (rising || (noPhase && term in PeriStats.SYMPTOMS))
            if (periTypical || periPattern) {
                classes += SymptomClass.PERI
                if (periTypical) reasons += "in Studien vor allem in der Perimenopause beschrieben, kaum bei PMS"
                if (noPhase) reasons += "ohne Häufung in einer Zyklusphase"
                if (rising) reasons += "zuletzt häufiger ($first → $second Tage)"
            }
            if (classes.isEmpty()) {
                classes += SymptomClass.UNCLEAR
                reasons += when {
                    dates.size < MIN_DAYS -> "erst an ${dates.size} Tagen erfasst"
                    buckets.isEmpty() -> "für diese Tage ist kein Zyklus erfasst"
                    else -> "keine Häufung in einer Zyklusphase"
                }
            }
            SymptomAssessment(term, category, dates.size, p, v, o, mean, cycles, classes.distinct(), reasons)
        }.sortedWith(compareByDescending<SymptomAssessment> { it.days }.thenBy { it.term })

        return ClassificationResult(symptoms, redFlags(summary, occurrences, from, to, ::bucket), periSignals(summary, age), pms.assessed)
    }

    private fun redFlags(
        summary: CycleSummary,
        occurrences: List<TermOccurrence>,
        from: LocalDate,
        to: LocalDate,
        bucket: (LocalDate) -> Pair<Char, Int>?,
    ): List<RedFlag> {
        fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        val flags = mutableListOf<RedFlag>()
        val cycles = summary.cycles.filter { inRange(it.start) }

        val long = cycles.filter { !it.periodEstimated && it.periodLength > 8 }
        if (long.isNotEmpty()) flags += RedFlag(
            "Lange Blutung",
            "In ${long.size} ${if (long.size == 1) "Zyklus" else "Zyklen"} dauerte die Blutung länger als 8 Tage (bis ${long.maxOf { it.periodLength }} Tage).",
            "FIGO-Klassifikation abnormaler uteriner Blutungen (2018)",
        )
        val short = cycles.count { it.deviation == Deviation.SHORT }
        val longCycles = cycles.count { it.deviation == Deviation.LONG }
        if (short >= 2) flags += RedFlag("Häufige Blutungen", "$short Zyklen waren kürzer als 21 Tage.", "ACOG Committee Opinion 651; FIGO (2018)")
        if (longCycles >= 2) flags += RedFlag("Seltene Blutungen", "$longCycles Zyklen dauerten länger als 35 Tage.", "ACOG Committee Opinion 651; FIGO (2018)")

        val bleed = occurrences.filter { inRange(it.date) && it.term in Lexicon.BLEEDING_TERMS }
        val heavyCycles = bleed.filter { it.term == "Blutung sehr stark" }.mapNotNull { bucket(it.date)?.second }.distinct().size
        if (heavyCycles >= 2) flags += RedFlag(
            "Sehr starke Blutung",
            "In $heavyCycles Zyklen wurde eine sehr starke Blutung erfasst. Hinweise sind z. B. stündlicher Wechsel von Binde oder Tampon, Klumpen über 2,5 cm oder Müdigkeit durch Eisenmangel.",
            "NICE NG88 Heavy menstrual bleeding (2021)",
        )
        val between = bleed.filter { it.term in setOf("Zwischenblutung", "Schmierblutung") && bucket(it.date)?.first != 'P' }.map { it.date }.distinct()
        val explicit = bleed.filter { it.term == "Zwischenblutung" }.map { it.date }.distinct()
        if (explicit.isNotEmpty() || between.size >= 3) flags += RedFlag(
            "Blutungen zwischen den Perioden",
            "An ${maxOf(between.size, explicit.size)} Tagen Zwischen- oder Schmierblutung außerhalb der Periode. In der Perimenopause häufiger, sollte aber abgeklärt werden.",
            "FIGO (2018); S3-Leitlinie Peri- und Postmenopause (AWMF 2020)",
        )
        return flags
    }

    private fun periSignals(summary: CycleSummary, age: Int?): List<String> = buildList {
        when (summary.straw) {
            StrawStage.EARLY, StrawStage.LATE -> add("Zyklus: ${summary.straw.label} – ${summary.straw.description}")
            else -> {}
        }
        if (summary.baselineVariability >= 4) add("Die Zykluslängen schwanken deutlich (Standardabweichung ${de1(summary.baselineVariability)} Tage).")
        if (age != null && age >= 40) add("Mit $age Jahren liegt der Beginn der Perimenopause im üblichen Alter (meist Mitte 40, oft schon ab 40).")
    }

    fun pct(v: Double) = "${(v * 100).roundToInt()} %"
}
