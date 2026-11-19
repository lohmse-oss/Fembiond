package de.fembiond.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Freundlicher Planungshinweis für ein anstehendes Ereignis. */
data class EventRisk(
    val event: PlannedEvent,
    val factors: List<String>,
    val elevated: Boolean,
    val tips: List<String>,
)

/**
 * Belastungshinweis: kombiniert Zyklus-Zeitpunkt, bisherige Erfahrung mit der Ereignisart,
 * Schlaf und Unruhe der letzten Tage sowie gehäufte Termine.
 */
object StressForecast {

    private val GENERIC_TIPS = listOf(
        "Ohne Zeitdruck ankommen und einen ruhigen Rückzugsort einplanen",
        "Vorher etwas Bewegung oder eine Atemübung",
        "Einen Ausweg vorab festlegen – früher gehen ist in Ordnung",
    )

    fun upcoming(
        summary: CycleSummary,
        events: List<PlannedEvent>,
        episodes: List<Episode>,
        occurrences: List<TermOccurrence>,
        today: LocalDate,
        days: Int = 7,
    ): List<EventRisk> {
        val upcoming = events.filter { !it.date.isBefore(today) && daysBetween(today, it.date) <= days }.sortedBy { it.date }
        if (upcoming.isEmpty()) return emptyList()
        val past = events.filter { it.date.isBefore(today) }
        val typeHistory = past.groupBy { it.type }.mapValues { (_, l) -> l.map { Analytics.anticipatoryLevel(it, episodes) } }
        val lateLutealHistory = past.filter { e ->
            (CycleEngine.dayInfo(summary, e.date)?.daysBeforeNext ?: 99) in 1..7
        }.map { Analytics.anticipatoryLevel(it, episodes) }
        val otherHistory = past.filter { e ->
            (CycleEngine.dayInfo(summary, e.date)?.daysBeforeNext ?: 99) !in 1..7
        }.map { Analytics.anticipatoryLevel(it, episodes) }

        val recentSleep = occurrences.filter { it.term == ScaleTerms.SLEEP_QUALITY && daysBetween(it.date, today) in 0..2 }.mapNotNull { it.intensity }
        val recentUnrest = episodes.filter { daysBetween(it.date, today) in 0..2 }.maxOfOrNull { it.level } ?: 0
        val helped = episodes.filter { it.eventId != null || "Anstehendes Ereignis" in it.triggers }
            .flatMap { it.helped }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(3)

        return upcoming.mapNotNull { e ->
            val factors = mutableListOf<String>()
            val info = if (e.date.isAfter(today)) CycleEngine.projectDay(summary, e.date) else CycleEngine.dayInfo(summary, e.date)
            val beforeNext = info?.daysBeforeNext ?: 99
            if (beforeNext in 1..7) {
                val pre = lateLutealHistory.takeIf { it.size >= 2 }?.average()
                val other = otherHistory.takeIf { it.size >= 2 }?.average()
                factors += if (pre != null && other != null && pre - other >= 0.7)
                    "fällt voraussichtlich in die Tage vor der Periode – bisher waren Ereignisse in dieser Zeit belastender (Ø Stufe ${de1(pre)} statt ${de1(other)})"
                else "fällt voraussichtlich in die Tage vor der Periode"
            } else if (info?.phase == Phase.MENSTRUATION) {
                factors += "fällt voraussichtlich in die Periode"
            }
            typeHistory[e.type]?.takeIf { it.size >= 2 && it.average() >= 3.0 }?.let {
                factors += "„${e.type}“ war bisher meist mit starker Unruhe verbunden (Ø Stufe ${de1(it.average())})"
            }
            if (recentSleep.size >= 2 && recentSleep.average() <= 2.0) factors += "du hast in den letzten Nächten schlecht geschlafen"
            if (recentUnrest >= 3) factors += "die Unruhe war in den letzten Tagen bereits erhöht (Stufe $recentUnrest)"
            events.firstOrNull { it.id != e.id && kotlin.math.abs(daysBetween(e.date, it.date)) in 0..1 }?.let {
                factors += "direkt davor oder danach steht noch „${it.displayName}“ an"
            }
            if (factors.isEmpty()) return@mapNotNull null
            val tips = (helped.map { "Was dir bisher geholfen hat: $it" } + GENERIC_TIPS).take(3)
            EventRisk(e, factors, factors.size >= 2, tips)
        }
    }
}

data class WeeklyReview(
    val from: LocalDate,
    val to: LocalDate,
    val trackedDays: Int,
    val unrestCount: Int,
    val meanUnrestLevel: Double?,
    val panicCount: Int,
    val meanSleep: Double?,
    val hotFlashes: Int,
    val observations: List<String>,
    val nextWeek: List<Pair<PlannedEvent, DayInfo?>>,
)

/** Wochenrückblick: die letzten 7 Tage im Vergleich zu den 4 Wochen davor. */
object WeeklyReviewBuilder {

    fun isReviewDay(today: LocalDate) = today.dayOfWeek == DayOfWeek.SUNDAY

    fun build(
        summary: CycleSummary,
        occurrences: List<TermOccurrence>,
        episodes: List<Episode>,
        events: List<PlannedEvent>,
        today: LocalDate,
    ): WeeklyReview {
        val from = today.minusDays(6)
        val prevFrom = from.minusDays(28)
        fun inWeek(d: LocalDate) = !d.isBefore(from) && !d.isAfter(today)
        fun inPrev(d: LocalDate) = !d.isBefore(prevFrom) && d.isBefore(from)

        val occW = occurrences.filter { inWeek(it.date) }
        val occP = occurrences.filter { inPrev(it.date) }
        val epW = episodes.filter { inWeek(it.date) }
        val epP = episodes.filter { inPrev(it.date) }
        val tracked = (occW.map { it.date } + epW.map { it.date }).toSet().size
        val prevTrackedWeeks = ((occP.map { it.date } + epP.map { it.date }).toSet().size / 7.0).coerceAtLeast(0.0)
        val sleepW = occW.filter { it.term == ScaleTerms.SLEEP_QUALITY }.mapNotNull { it.intensity }
        val sleepP = occP.filter { it.term == ScaleTerms.SLEEP_QUALITY }.mapNotNull { it.intensity }
        val hotW = occW.count { it.term in Lexicon.PERI_TYPICAL }
        val hotP = occP.count { it.term in Lexicon.PERI_TYPICAL }

        val obs = mutableListOf<String>()
        if (tracked == 0) {
            obs += "Diese Woche gab es keine Einträge. Schon ein kurzer Eintrag am Tag macht die Auswertung aussagekräftiger."
        } else {
            val perWeekPrev = if (epP.isNotEmpty()) epP.size / 4.0 else null
            when {
                epW.isEmpty() && (perWeekPrev ?: 0.0) >= 1.0 -> obs += "Keine Unruhe-Episode diese Woche – in den Wochen davor waren es im Schnitt ${de1(perWeekPrev)}."
                epW.isEmpty() -> obs += "Diese Woche wurde keine Unruhe erfasst."
                perWeekPrev != null && epW.size >= perWeekPrev + 2 -> obs += "Mehr Unruhe als sonst: ${epW.size} Episoden (sonst etwa ${de1(perWeekPrev)} pro Woche)."
                perWeekPrev != null && epW.size <= perWeekPrev - 1.5 -> obs += "Ruhigere Woche: ${epW.size} Unruhe-Episoden (sonst etwa ${de1(perWeekPrev)} pro Woche)."
                else -> obs += "${epW.size} Unruhe-Episode${if (epW.size == 1) "" else "n"} diese Woche, Ø Stufe ${de1(epW.map { it.level }.average())}."
            }
            val panics = epW.count { it.isPanic }
            if (panics > 0) obs += "$panics Panikattacke${if (panics == 1) "" else "n"} – denk an deine Notfallstrategien und sprich es gern beim nächsten Termin an."
            val eventEp = epW.filter { it.eventId != null }
            if (eventEp.isNotEmpty()) {
                val e = events.firstOrNull { it.id == eventEp.maxBy { x -> x.level }.eventId }
                if (e != null) obs += "Die stärkste Unruhe hing mit „${e.displayName}“ zusammen (Stufe ${eventEp.maxOf { it.level }})."
            }
            if (sleepW.size >= 3) {
                val w = sleepW.average()
                val p = sleepP.takeIf { it.size >= 5 }?.average()
                obs += when {
                    p != null && w <= p - 0.5 -> "Der Schlaf war schlechter als sonst (Ø ${de1(w)} statt ${de1(p)})."
                    p != null && w >= p + 0.5 -> "Der Schlaf war besser als sonst (Ø ${de1(w)} statt ${de1(p)})."
                    else -> "Schlafqualität im Schnitt ${de1(w)} von 5."
                }
            }
            if (hotW > 0) {
                val p = if (hotP > 0 && prevTrackedWeeks > 0) hotP / 4.0 else null
                val top = occW.filter { it.term in Lexicon.PERI_TYPICAL }.groupingBy { it.term }.eachCount().maxByOrNull { it.value }?.key
                obs += "$hotW× typische Perimenopause-Beschwerden" + (top?.let { ", am häufigsten „$it“" } ?: "") + (p?.let { " (sonst etwa ${de1(it)} pro Woche)" } ?: "") + "."
            }
            val good = occW.filter { it.term in setOf("ausgeglichen", "fröhlich", "gut geschlafen", "energiegeladen") }.map { it.date }.toSet().size
            if (good >= 2) obs += "An $good Tagen ging es dir gut (ausgeglichen, fröhlich oder gut geschlafen)."
        }
        val next = events.filter { it.date.isAfter(today) && daysBetween(today, it.date) <= 7 }.sortedBy { it.date }
            .map { it to CycleEngine.projectDay(summary, it.date) }
        return WeeklyReview(
            from = from, to = today, trackedDays = tracked,
            unrestCount = epW.size,
            meanUnrestLevel = epW.takeIf { it.isNotEmpty() }?.map { it.level }?.average(),
            panicCount = epW.count { it.isPanic },
            meanSleep = sleepW.takeIf { it.isNotEmpty() }?.average(),
            hotFlashes = hotW,
            observations = obs.take(5),
            nextWeek = next,
        )
    }

    fun shortText(r: WeeklyReview): String =
        r.observations.firstOrNull() ?: "Dein Wochenrückblick ist da."

    @Suppress("unused")
    private fun pct(v: Double) = (v * 100).roundToInt()
}
