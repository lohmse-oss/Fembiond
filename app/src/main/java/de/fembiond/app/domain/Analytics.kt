package de.fembiond.app.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

data class AnalysisInput(
    val summary: CycleSummary,
    val occurrences: List<TermOccurrence>,
    val episodes: List<Episode>,
    val medications: List<MedicationPhase>,
    val gad: List<GadRecord>,
    val from: LocalDate,
    val to: LocalDate,
    val categories: Set<Category> = Category.entries.toSet(),
    val terms: Set<String> = emptySet(),
    val events: List<PlannedEvent> = emptyList(),
)

data class CycleBar(
    val start: LocalDate,
    val length: Int?,
    val periodLength: Int,
    val deviation: Deviation?,
    val medicated: Boolean,
    val approximate: Boolean,
    val periodEstimated: Boolean = false,
)

data class CycleStats(
    val bars: List<CycleBar>,
    val median: Double?,
    val stdDev: Double?,
    val min: Int?,
    val max: Int?,
    /** Veränderung der Zykluslänge in Tagen pro Zyklus (lineare Regression). */
    val trendPerCycle: Double?,
)

data class HeatRow(val term: String, val category: Category, val cells: List<Double>, val totalDays: Double)
data class Heatmap(val maxDay: Int, val rows: List<HeatRow>, val trackedPerDay: List<Double>)

data class PhaseRate(val term: String, val category: Category, val rates: Map<Phase, Double>)

data class DrspResult(
    val term: String,
    val category: Category,
    val premenstrual: Double,
    val postmenstrual: Double,
    /** Anzahl ausgewerteter Perioden (Paare „Woche davor“ / „Tag 4–10 danach“). */
    val cycles: Int,
    val preDays: Int = 0,
    val postDays: Int = 0,
    /** Tage, an denen der Begriff im jeweiligen Fenster auftrat. */
    val preHits: Int = 0,
    val postHits: Int = 0,
) {
    val changePercent: Double? get() = if (postmenstrual <= 0.0) null else (premenstrual - postmenstrual) / postmenstrual * 100
    /** DRSP-Kriterium: prämenstruell mindestens 30 % stärker (bzw. nur prämenstruell vorhanden). */
    val significant: Boolean get() = cycles >= 2 && premenstrual >= 0.5 && (postmenstrual == 0.0 || (changePercent ?: 0.0) >= 30.0)

    /** Einordnung in Alltagssprache. */
    val verdict: String
        get() {
            val c = changePercent
            return when {
                cycles == 0 -> "Noch keine Periode mit genug Einträgen davor und danach – noch keine Aussage möglich."
                significant && c == null -> "Tritt fast nur vor der Periode auf – deutliches Muster."
                significant -> "Vor der Periode deutlich stärker (${if (c!! >= 0) "+" else ""}${c.roundToInt()} %) – deutliches Muster."
                cycles < 2 && (c == null || c >= 30) && premenstrual > postmenstrual -> "Vor der Periode stärker – erst $cycles Periode ausgewertet, das Muster muss sich noch bestätigen."
                c == null -> "Nur vor der Periode beobachtet, aber selten – noch kein Muster."
                c >= 10 -> "Vor der Periode etwas stärker – noch kein deutliches Muster (ab +30 % über 2 Perioden)."
                c <= -10 -> "Vor der Periode etwas ruhiger als danach – kein Hinweis auf eine Verstärkung vor der Periode."
                else -> "Vorher und danach etwa gleich – kein Zusammenhang mit der Periode erkennbar."
            }
        }
}

/** Ein Paar aus „7 Tage vor der Periode“ und „Tag 4–10 danach“ für eine Periode. */
data class DrspPair(
    val periodStart: LocalDate,
    val preDays: Int,
    val postDays: Int,
    val counted: Boolean,
    val reason: String?,
)

data class UnrestPhaseStat(
    val phase: Phase,
    /** Anzahl Unruhe-Episoden (alle Stufen). */
    val count: Int,
    val panicCount: Int,
    /** Mittlere Stufe – nur über erfasste Episoden; Tage ohne Einträge fließen nicht ein. */
    val meanLevel: Double?,
    /**
     * Anteil der erfassten Tage dieser Phase mit Unruhe im Verhältnis zum Durchschnitt (1× = Durchschnitt).
     * Nur berechnet, wenn es auch erfasste Tage ohne Unruhe gibt – sonst null.
     */
    val relativeFrequency: Double?,
    val daysTracked: Int = 0,
    val daysWithUnrest: Int = 0,
)

/** Unruhe in den Tagen rund um Ereignisse (Tag 0 = Ereignistag). */
data class AnticipationPoint(val offset: Int, val meanMaxLevel: Double?, val share: Double?, val events: Int)

data class EventTypeStat(
    val type: String,
    val events: Int,
    /** Anteil der Ereignisse mit Unruhe in den 3 Tagen davor bis zum Ereignistag. */
    val shareWithUnrest: Double,
    val meanMaxLevel: Double,
    val panics: Int,
    val cancelled: Int,
)

data class EventPhaseStat(
    val label: String,
    val events: Int,
    val meanMaxLevel: Double,
    val cancelled: Int,
)

data class PairStat(val a: String, val b: String, val days: Double, val lift: Double)

data class EventEffect(
    val event: String,
    val target: String,
    val eventDays: Int,
    val rateWith: Double,
    val rateWithout: Double,
)

data class PeriodCompare(
    val label: String,
    val trackedDays: Int,
    val unrestPer30: Double?,
    val meanUnrestLevel: Double?,
    val panicPer30: Double?,
    val meanSleep: Double?,
    val meanEnergy: Double?,
    val medianCycle: Double?,
    val topTerms: List<Pair<String, Double>>,
)

/** Verlauf je Kalenderwoche (ISO, Montag–Sonntag). Wochen ohne Einträge haben null-Werte. */
data class WeekTrend(
    val weekStart: LocalDate,
    val label: String,
    val tracked: Boolean,
    val meanSleep: Double?,
    val meanEnergy: Double?,
    /** Anzahl Unruhe-Episoden; null, wenn in der Woche nichts erfasst wurde. */
    val unrestCount: Int?,
    val panicCount: Int?,
    /** Mittlere Stufe nur über erfasste Episoden (Tage ohne Einträge zählen nicht). */
    val meanUnrestLevel: Double?,
    val gad: Double?,
)

data class AnalysisResult(
    val trackedDays: Int,
    val cycles: CycleStats,
    val heatmap: Heatmap,
    val phaseRates: List<PhaseRate>,
    val drsp: List<DrspResult>,
    val drspPairs: List<DrspPair>,
    val unrestByPhase: List<UnrestPhaseStat>,
    /** Erfasste Tage ohne Unruhe – nötig für Häufigkeitsvergleiche. */
    val calmDays: Int,
    val unrestTotal: Int,
    val panicTotal: Int,
    /** Anzahl Episoden je Stufe 1–5. */
    val levelCounts: List<Int>,
    val anticipation: List<AnticipationPoint>,
    val eventTypes: List<EventTypeStat>,
    val eventPhases: List<EventPhaseStat>,
    val coOccurrence: List<PairStat>,
    val nextDay: List<PairStat>,
    val events: List<EventEffect>,
    val compare: List<PeriodCompare>,
    val trends: List<WeekTrend>,
    val insights: List<String>,
)

object Analytics {

    const val PANIC_LABEL = "Panikattacke"
    const val UNREST_LABEL = "Unruhe-Episode"
    private const val BACKFILL_WEIGHT = 0.5

    fun analyze(input: AnalysisInput): AnalysisResult {
        val s = input.summary
        val inRange: (LocalDate) -> Boolean = { !it.isBefore(input.from) && !it.isAfter(input.to) }
        val allOcc = input.occurrences.filter { inRange(it.date) }
        val scaleOcc = allOcc.filter { it.term == ScaleTerms.SLEEP_QUALITY || it.term == ScaleTerms.ENERGY }
        val occ = allOcc.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
            .filter { it.category in input.categories && (input.terms.isEmpty() || it.term in input.terms) }
        // Unruhe-Episoden gehören zur Kategorie „Stimmung & Geist“ und folgen deren Filter
        val panics = if (Category.MIND in input.categories) input.episodes.filter { inRange(it.date) } else emptyList()

        // Tage mit Einträgen und deren Gewicht (nachgetragen = halbes Gewicht)
        val dayWeight = HashMap<LocalDate, Double>()
        fun mark(date: LocalDate, backfilled: Boolean) {
            val w = if (backfilled) BACKFILL_WEIGHT else 1.0
            dayWeight[date] = maxOf(dayWeight[date] ?: 0.0, w)
        }
        allOcc.forEach { mark(it.date, it.backfilled) }
        panics.forEach { mark(it.date, it.backfilled) }

        // Begriff → Datum → (Gewicht, Score)
        val termDays = HashMap<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>()
        for (o in occ) {
            val map = termDays.getOrPut(o.term to o.category) { HashMap() }
            val w = if (o.backfilled) BACKFILL_WEIGHT else 1.0
            val score = (o.intensity ?: 3).toDouble()
            val prev = map[o.date]
            map[o.date] = maxOf(prev?.first ?: 0.0, w) to maxOf(prev?.second ?: 0.0, score)
        }
        if (Category.MIND in input.categories) {
            fun series(list: List<Episode>): HashMap<LocalDate, Pair<Double, Double>> {
                val map = HashMap<LocalDate, Pair<Double, Double>>()
                for (p in list) {
                    val w = if (p.backfilled) BACKFILL_WEIGHT else 1.0
                    val prev = map[p.date]
                    map[p.date] = maxOf(prev?.first ?: 0.0, w) to maxOf(prev?.second ?: 0.0, p.level.toDouble())
                }
                return map
            }
            series(panics).takeIf { it.isNotEmpty() }?.let { termDays[UNREST_LABEL to Category.MIND] = it }
            series(panics.filter { it.isPanic }).takeIf { it.isNotEmpty() }?.let { termDays[PANIC_LABEL to Category.MIND] = it }
        }

        val dayInfo = dayWeight.keys.associateWith { CycleEngine.dayInfo(s, it) }
        val topTerms = termDays.entries.sortedByDescending { e -> e.value.values.sumOf { it.first } }

        val cycles = cycleStats(s, input.from, input.to)
        val heatmap = heatmap(topTerms.take(15), dayWeight, dayInfo)
        val phaseRates = phaseRates(topTerms.take(20), dayWeight, dayInfo)
        val drspPairs = drspPairs(s, dayWeight, input.from, input.to)
        val drsp = drsp(topTerms, dayWeight, drspPairs)
        val unrestByPhase = unrestByPhase(panics, dayWeight, dayInfo, s)
        val events = input.events.filter { inRange(it.date) }
        val anticipation = anticipation(events, panics, dayWeight)
        val eventTypes = eventTypes(events, panics)
        val eventPhases = eventPhases(events, panics, s)
        val (co, next) = pairs(topTerms.take(25), dayWeight)
        val lifeEffects = events(termDays, dayWeight)
        val compare = compare(input, dayWeight, allOcc, panics)
        val trends = trends(scaleOcc, panics, input.gad.filter { inRange(it.date) }, dayWeight.keys)

        val result = AnalysisResult(
            trackedDays = dayWeight.size,
            cycles = cycles,
            heatmap = heatmap,
            phaseRates = phaseRates,
            drsp = drsp,
            drspPairs = drspPairs,
            unrestByPhase = unrestByPhase,
            calmDays = dayWeight.keys.count { d -> panics.none { it.date == d } },
            unrestTotal = panics.size,
            panicTotal = panics.count { it.isPanic },
            levelCounts = (1..5).map { l -> panics.count { it.level == l } },
            anticipation = anticipation,
            eventTypes = eventTypes,
            eventPhases = eventPhases,
            coOccurrence = co,
            nextDay = next,
            events = lifeEffects,
            compare = compare,
            trends = trends,
            insights = emptyList(),
        )
        return result.copy(insights = Insights.build(result, s))
    }

    private fun cycleStats(s: CycleSummary, from: LocalDate, to: LocalDate): CycleStats {
        val inRange = s.cycles.filter { c -> !c.start.isAfter(to) && (c.nextStart == null || c.nextStart.isAfter(from)) }
        val bars = inRange.map { CycleBar(it.start, it.length, it.periodLength, it.deviation, it.medicated, it.approximate, it.periodEstimated) }
        val lengths = inRange.mapNotNull { it.length }
        val natural = inRange.filter { it.isComplete && !it.medicated }.map { it.length!! }
        val basis = natural.ifEmpty { lengths }
        val trend = if (basis.size >= 4) slope(basis.map { it.toDouble() }) else null
        return CycleStats(
            bars = bars,
            median = basis.takeIf { it.isNotEmpty() }?.let { median(it) },
            stdDev = basis.takeIf { it.size >= 2 }?.let { stdDev(it.map(Int::toDouble)) },
            min = lengths.minOrNull(),
            max = lengths.maxOrNull(),
            trendPerCycle = trend,
        )
    }

    fun slope(y: List<Double>): Double {
        val n = y.size
        val xMean = (n - 1) / 2.0
        val yMean = y.average()
        var num = 0.0
        var den = 0.0
        y.forEachIndexed { i, v -> num += (i - xMean) * (v - yMean); den += (i - xMean) * (i - xMean) }
        return if (den == 0.0) 0.0 else num / den
    }

    private fun heatmap(
        terms: List<Map.Entry<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>>,
        dayWeight: Map<LocalDate, Double>,
        dayInfo: Map<LocalDate, DayInfo?>,
    ): Heatmap {
        val maxDay = (dayInfo.values.mapNotNull { it?.cycleDay }.maxOrNull() ?: 28).coerceIn(28, 40)
        val tracked = DoubleArray(maxDay)
        dayWeight.forEach { (d, w) -> dayInfo[d]?.let { tracked[minOf(it.cycleDay, maxDay) - 1] += w } }
        val rows = terms.map { (key, days) ->
            val sum = DoubleArray(maxDay)
            days.forEach { (d, v) -> dayInfo[d]?.let { sum[minOf(it.cycleDay, maxDay) - 1] += v.first } }
            HeatRow(key.first, key.second, sum.mapIndexed { i, v -> if (tracked[i] > 0) v / tracked[i] else 0.0 }, days.values.sumOf { it.first })
        }
        return Heatmap(maxDay, rows, tracked.toList())
    }

    private fun phaseRates(
        terms: List<Map.Entry<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>>,
        dayWeight: Map<LocalDate, Double>,
        dayInfo: Map<LocalDate, DayInfo?>,
    ): List<PhaseRate> {
        val trackedPhase = HashMap<Phase, Double>()
        dayWeight.forEach { (d, w) -> dayInfo[d]?.let { trackedPhase.merge(it.phase, w, Double::plus) } }
        return terms.map { (key, days) ->
            val counts = HashMap<Phase, Double>()
            days.forEach { (d, v) -> dayInfo[d]?.let { counts.merge(it.phase, v.first, Double::plus) } }
            PhaseRate(key.first, key.second, Phase.entries.associateWith { p ->
                val t = trackedPhase[p] ?: 0.0
                if (t > 0) (counts[p] ?: 0.0) / t else 0.0
            })
        }
    }

    /** Mindestanzahl erfasster Tage je Fenster, damit eine Periode zählt. */
    const val DRSP_MIN_DAYS = 2

    /**
     * Paare je Periode (DRSP): Woche vor der Periode (Tag −7…−1) und Zyklustag 4–10 nach derselben Periode.
     * Der laufende Zyklus zählt mit, sobald Tag 4–10 erreicht ist.
     */
    fun drspPairs(s: CycleSummary, dayWeight: Map<LocalDate, Double>, from: LocalDate, to: LocalDate, today: LocalDate = to): List<DrspPair> =
        s.cycles.filter { it.index > 0 }.mapNotNull { c ->
            val p = c.start
            if (p.plusDays(9).isBefore(from)) return@mapNotNull null
            if (p.isAfter(to)) return@mapNotNull null
            val pre = (1..7).count { dayWeight.containsKey(p.minusDays(it.toLong())) }
            val post = (3..9).count { dayWeight.containsKey(p.plusDays(it.toLong())) }
            val postFuture = p.plusDays(3).isAfter(today)
            val reason = when {
                postFuture -> "Tag 4–10 nach dieser Periode liegt noch in der Zukunft"
                pre < DRSP_MIN_DAYS && post < DRSP_MIN_DAYS -> "zu wenige Einträge davor ($pre Tage) und danach ($post Tage)"
                pre < DRSP_MIN_DAYS -> "nur $pre erfasste${if (pre == 1) "r" else ""} Tag${if (pre == 1) "" else "e"} in der Woche davor (mind. $DRSP_MIN_DAYS)"
                post < DRSP_MIN_DAYS -> "nur $post erfasste${if (post == 1) "r" else ""} Tag${if (post == 1) "" else "e"} an Tag 4–10 (mind. $DRSP_MIN_DAYS)"
                else -> null
            }
            DrspPair(p, pre, post, reason == null, reason)
        }

    private fun drsp(
        terms: List<Map.Entry<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>>,
        dayWeight: Map<LocalDate, Double>,
        pairs: List<DrspPair>,
    ): List<DrspResult> {
        val counted = pairs.filter { it.counted }
        val pre = counted.flatMap { pr -> (1..7).map { pr.periodStart.minusDays(it.toLong()) } }.filter { dayWeight.containsKey(it) }.distinct()
        val post = counted.flatMap { pr -> (3..9).map { pr.periodStart.plusDays(it.toLong()) } }.filter { dayWeight.containsKey(it) }.distinct()
        if (pre.isEmpty() || post.isEmpty()) return emptyList()
        fun mean(days: List<LocalDate>, map: Map<LocalDate, Pair<Double, Double>>): Double {
            var sum = 0.0
            var w = 0.0
            days.forEach { d -> val dw = dayWeight[d]!!; w += dw; sum += dw * (map[d]?.second ?: 0.0) }
            return if (w == 0.0) 0.0 else sum / w
        }
        return terms.filter { it.key.second != Category.LIFE }.map { (key, map) ->
            DrspResult(
                key.first, key.second, mean(pre, map), mean(post, map), counted.size,
                preDays = pre.size, postDays = post.size,
                preHits = pre.count { map.containsKey(it) }, postHits = post.count { map.containsKey(it) },
            )
        }.filter { it.premenstrual > 0 || it.postmenstrual > 0 }
            .sortedByDescending { it.premenstrual - it.postmenstrual }
    }

    /** Mindestanzahl erfasster ruhiger Tage, damit Häufigkeiten nach Phase verglichen werden. */
    const val MIN_CALM_DAYS = 3

    private fun unrestByPhase(
        episodes: List<Episode>,
        dayWeight: Map<LocalDate, Double>,
        dayInfo: Map<LocalDate, DayInfo?>,
        s: CycleSummary,
    ): List<UnrestPhaseStat> {
        val unrestDays = episodes.map { it.date }.toSet()
        val tracked = dayWeight.keys.filter { dayInfo[it] != null }
        val calm = tracked.count { it !in unrestDays }
        val overallRate = if (tracked.isNotEmpty()) tracked.count { it in unrestDays }.toDouble() / tracked.size else 0.0
        val byPhase = episodes.groupBy { CycleEngine.dayInfo(s, it.date)?.phase }
        return Phase.entries.map { p ->
            val list = byPhase[p].orEmpty()
            val days = tracked.filter { dayInfo[it]?.phase == p }
            val withUnrest = days.count { it in unrestDays }
            val rate = if (days.isNotEmpty()) withUnrest.toDouble() / days.size else 0.0
            UnrestPhaseStat(
                phase = p,
                count = list.size,
                panicCount = list.count { it.isPanic },
                meanLevel = list.takeIf { it.isNotEmpty() }?.map { it.level }?.average(),
                relativeFrequency = if (calm >= MIN_CALM_DAYS && overallRate > 0 && days.size >= 3) rate / overallRate else null,
                daysTracked = days.size,
                daysWithUnrest = withUnrest,
            )
        }
    }

    /** Höchste Unruhe-Stufe an einem Tag (0 = erfasst, aber keine Unruhe; null = nicht erfasst). */
    private fun maxLevel(day: LocalDate, episodes: Map<LocalDate, List<Episode>>, dayWeight: Map<LocalDate, Double>): Int? {
        val list = episodes[day]
        return when {
            !list.isNullOrEmpty() -> list.maxOf { it.level }
            dayWeight.containsKey(day) -> 0
            else -> null
        }
    }

    private fun anticipation(events: List<PlannedEvent>, episodes: List<Episode>, dayWeight: Map<LocalDate, Double>): List<AnticipationPoint> {
        if (events.isEmpty()) return emptyList()
        val byDay = episodes.groupBy { it.date }
        return (-7..1).map { off ->
            val values = events.mapNotNull { maxLevel(it.date.plusDays(off.toLong()), byDay, dayWeight) }
            AnticipationPoint(
                offset = off,
                meanMaxLevel = values.takeIf { it.isNotEmpty() }?.average(),
                share = values.takeIf { it.isNotEmpty() }?.let { v -> v.count { it > 0 }.toDouble() / v.size },
                events = values.size,
            )
        }
    }

    /** Höchste Stufe in den 3 Tagen vor bis zum Ereignistag, inkl. direkt verknüpfter Episoden. */
    fun anticipatoryLevel(event: PlannedEvent, episodes: List<Episode>): Int =
        episodes.filter { e ->
            e.eventId == event.id && event.id != 0L ||
                (!e.date.isBefore(event.date.minusDays(3)) && !e.date.isAfter(event.date))
        }.maxOfOrNull { it.level } ?: 0

    private fun eventTypes(events: List<PlannedEvent>, episodes: List<Episode>): List<EventTypeStat> =
        events.groupBy { it.type }.map { (type, list) ->
            val levels = list.map { anticipatoryLevel(it, episodes) }
            EventTypeStat(
                type = type,
                events = list.size,
                shareWithUnrest = levels.count { it > 0 }.toDouble() / list.size,
                meanMaxLevel = levels.average(),
                panics = levels.count { it >= 5 },
                cancelled = list.count { it.outcome == EventOutcome.CANCELLED || it.outcome == EventOutcome.LEFT_EARLY },
            )
        }.sortedByDescending { it.meanMaxLevel * it.events }

    private fun eventPhases(events: List<PlannedEvent>, episodes: List<Episode>, s: CycleSummary): List<EventPhaseStat> {
        if (events.isEmpty()) return emptyList()
        val groups = events.groupBy { e ->
            val info = CycleEngine.dayInfo(s, e.date) ?: CycleEngine.projectDay(s, e.date)
            when {
                info == null -> null
                (info.daysBeforeNext ?: 99) in 1..7 -> "In den 7 Tagen vor der Periode"
                info.phase == Phase.MENSTRUATION -> "Während der Periode"
                else -> "Übrige Zykluszeit"
            }
        }.filterKeys { it != null }
        return groups.map { (label, list) ->
            EventPhaseStat(label!!, list.size, list.map { anticipatoryLevel(it, episodes) }.average(),
                list.count { it.outcome == EventOutcome.CANCELLED || it.outcome == EventOutcome.LEFT_EARLY })
        }.sortedBy { it.label }
    }

    private fun pairs(
        terms: List<Map.Entry<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>>,
        dayWeight: Map<LocalDate, Double>,
    ): Pair<List<PairStat>, List<PairStat>> {
        val n = dayWeight.size.toDouble()
        if (n < 7) return emptyList<PairStat>() to emptyList()
        val sets = terms.map { it.key.first to it.value.keys }
        val same = mutableListOf<PairStat>()
        val next = mutableListOf<PairStat>()
        for (i in sets.indices) for (j in sets.indices) {
            if (i == j) continue
            val (a, da) = sets[i]
            val (b, db) = sets[j]
            if (i < j) {
                val both = da.count { it in db }
                if (both >= 3) {
                    val lift = (both / n) / ((da.size / n) * (db.size / n))
                    if (lift > 1.2) same += PairStat(a, b, both.toDouble(), lift)
                }
            }
            val follow = da.count { it.plusDays(1) in db }
            val trackedNext = da.count { dayWeight.containsKey(it.plusDays(1)) }
            if (follow >= 3 && trackedNext > 0) {
                val rate = follow.toDouble() / trackedNext
                val base = db.size / n
                if (base > 0 && rate / base > 1.3) next += PairStat(a, b, follow.toDouble(), rate / base)
            }
        }
        return same.sortedByDescending { it.lift * it.days }.take(10) to next.sortedByDescending { it.lift * it.days }.take(10)
    }

    private fun events(
        termDays: Map<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>,
        dayWeight: Map<LocalDate, Double>,
    ): List<EventEffect> {
        val events = termDays.filterKeys { it.second == Category.LIFE }.filterValues { it.size >= 3 }
        val targets = termDays.filterKeys { it.second in setOf(Category.MIND, Category.SLEEP_ENERGY, Category.BODY) || it.first == PANIC_LABEL }
            .filterValues { it.size >= 3 }
        val out = mutableListOf<EventEffect>()
        for ((ek, edays) in events) {
            val affected = edays.keys.flatMap { listOf(it, it.plusDays(1)) }.filter { dayWeight.containsKey(it) }.toSet()
            val others = dayWeight.keys - affected
            if (affected.size < 3 || others.size < 3) continue
            for ((tk, tdays) in targets) {
                val with = affected.count { it in tdays }.toDouble() / affected.size
                val without = others.count { it in tdays }.toDouble() / others.size
                if (with - without >= 0.15 && with >= 0.3) out += EventEffect(ek.first, tk.first, edays.size, with, without)
            }
        }
        return out.sortedByDescending { it.rateWith - it.rateWithout }.take(10)
    }

    private fun compare(
        input: AnalysisInput,
        dayWeight: Map<LocalDate, Double>,
        occ: List<TermOccurrence>,
        panics: List<Episode>,
    ): List<PeriodCompare> {
        val relevant = input.medications.filter { m -> dayWeight.keys.any { m.activeOn(it) } }
        if (relevant.isEmpty()) return emptyList()
        val groups = mutableListOf<Pair<String, (LocalDate) -> Boolean>>()
        groups += "Ohne Medikation" to { d -> input.medications.none { it.activeOn(d) } }
        relevant.forEach { m -> groups += "${m.name} (${m.scheduleLabel()})" to { d -> m.activeOn(d) } }
        return groups.map { (label, pred) ->
            val days = dayWeight.keys.filter(pred)
            val dset = days.toSet()
            val o = occ.filter { it.date in dset }
            val sleep = o.filter { it.term == ScaleTerms.SLEEP_QUALITY }.mapNotNull { it.intensity }
            val energy = o.filter { it.term == ScaleTerms.ENERGY }.mapNotNull { it.intensity }
            val eps = panics.filter { it.date in dset }
            val p = eps.count { it.isPanic }
            val cyc = input.summary.cycles.filter { c -> c.isComplete && pred(c.start) }.map { it.length!! }
            val termRates = o.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
                .groupBy { it.term }
                .mapValues { (_, v) -> v.map { it.date }.toSet().size.toDouble() / maxOf(1, days.size) }
                .entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
            PeriodCompare(
                label = label,
                trackedDays = days.size,
                unrestPer30 = if (days.size >= 7) eps.size * 30.0 / days.size else null,
                meanUnrestLevel = eps.takeIf { it.isNotEmpty() }?.map { it.level }?.average(),
                panicPer30 = if (days.size >= 7) p * 30.0 / days.size else null,
                meanSleep = sleep.takeIf { it.isNotEmpty() }?.average(),
                meanEnergy = energy.takeIf { it.isNotEmpty() }?.average(),
                medianCycle = cyc.takeIf { it.isNotEmpty() }?.let { median(it) },
                topTerms = termRates,
            )
        }
    }

    private fun trends(
        scale: List<TermOccurrence>, panics: List<Episode>, gad: List<GadRecord>, trackedDays: Set<LocalDate>,
    ): List<WeekTrend> {
        val dates = scale.map { it.date } + panics.map { it.date } + gad.map { it.date } + trackedDays
        if (dates.isEmpty()) return emptyList()
        fun monday(d: LocalDate) = d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val first = monday(dates.min())
        val last = monday(dates.max())
        val weekFields = java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(last) }.map { w ->
            val end = w.plusDays(6)
            fun inW(d: LocalDate) = !d.isBefore(w) && !d.isAfter(end)
            val tracked = trackedDays.any { inW(it) }
            val sc = scale.filter { inW(it.date) }
            val ep = panics.filter { inW(it.date) }
            WeekTrend(
                weekStart = w,
                label = "KW ${w.get(weekFields)}",
                tracked = tracked,
                meanSleep = sc.filter { it.term == ScaleTerms.SLEEP_QUALITY }.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average(),
                meanEnergy = sc.filter { it.term == ScaleTerms.ENERGY }.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average(),
                unrestCount = if (tracked) ep.size else null,
                panicCount = if (tracked) ep.count { it.isPanic } else null,
                meanUnrestLevel = ep.takeIf { it.isNotEmpty() }?.map { it.level }?.average(),
                gad = gad.filter { inW(it.date) }.map { it.score }.takeIf { it.isNotEmpty() }?.average(),
            )
        }.toList()
    }
}

/** Formuliert Ergebnisse in Alltagssprache – vorsichtig und mit Hinweis auf die Datenmenge. */
object Insights {
    fun build(r: AnalysisResult, s: CycleSummary): List<String> {
        val out = mutableListOf<String>()
        if (r.trackedDays < 14) {
            out += "Bisher gibt es Einträge an ${r.trackedDays} Tagen. Aussagekräftige Muster zeigen sich meist erst nach 2–3 Zyklen mit regelmäßigen Einträgen."
        }
        r.cycles.median?.let { m ->
            val sd = r.cycles.stdDev
            out += "Der übliche Zyklus dauert ${m.roundToInt()} Tage" + (sd?.let { " (Schwankung ± ${de1(it)} Tage)" } ?: "") + "."
        }
        val deviations = r.cycles.bars.count { it.deviation != null }
        if (deviations > 0) out += "$deviations ${if (deviations == 1) "Zyklus weicht" else "Zyklen weichen"} deutlich vom Üblichen ab."
        r.cycles.trendPerCycle?.let { t ->
            if (t >= 0.8) out += "Die Zyklen werden tendenziell länger (etwa +${de1(t)} Tage pro Zyklus)."
            if (t <= -0.8) out += "Die Zyklen werden tendenziell kürzer (etwa ${de1(t)} Tage pro Zyklus)."
        }
        if (s.straw == StrawStage.EARLY || s.straw == StrawStage.LATE) out += s.straw.description

        r.drsp.filter { it.significant }.take(3).forEach { d ->
            val change = d.changePercent
            out += if (change == null) {
                "„${d.term}“ tritt fast nur in den 7 Tagen vor der Periode auf (${d.cycles} Perioden ausgewertet)."
            } else {
                "„${d.term}“ ist in den 7 Tagen vor der Periode etwa ${change.roundToInt()} % stärker als an Tag 4–10 danach (${d.cycles} Perioden ausgewertet)."
            }
        }
        r.unrestByPhase.filter { (it.relativeFrequency ?: 0.0) >= 1.5 && it.count >= 2 }.maxByOrNull { it.relativeFrequency!! }?.let {
            out += "Unruhe häuft sich in der ${it.phase.label}: ${it.count} von ${r.unrestTotal} Episoden, etwa ${de1(it.relativeFrequency)}-mal so häufig wie im Durchschnitt" +
                (if (it.panicCount > 0) ", davon ${it.panicCount} Panikattacke${if (it.panicCount == 1) "" else "n"}." else ".")
        }
        if (r.unrestTotal >= 5 && r.panicTotal > 0) {
            out += "${r.panicTotal} von ${r.unrestTotal} Unruhe-Episoden (${(r.panicTotal * 100.0 / r.unrestTotal).roundToInt()} %) haben sich bis zur Panikattacke gesteigert."
        }
        val ant = r.anticipation.filter { it.events >= 3 && it.meanMaxLevel != null }
        if (ant.isNotEmpty()) {
            val peak = ant.maxBy { it.meanMaxLevel!! }
            val base = ant.filter { it.offset <= -5 }.mapNotNull { it.meanMaxLevel }.takeIf { it.isNotEmpty() }?.average()
            if (peak.meanMaxLevel!! >= 1.0 && (base == null || peak.meanMaxLevel >= base + 0.7)) {
                val day = when (peak.offset) { 0 -> "am Tag des Ereignisses"; 1 -> "am Tag danach"; -1 -> "am Vortag"; else -> "${-peak.offset} Tage vorher" }
                out += "Vor Ereignissen steigt die Unruhe an und ist $day am stärksten (Ø Stufe ${de1(peak.meanMaxLevel)}" + (base?.let { ", eine Woche vorher Ø ${de1(it)}" } ?: "") + ")."
            }
        }
        r.eventTypes.filter { it.events >= 2 && it.meanMaxLevel >= 2.0 }.take(2).forEach {
            out += "„${it.type}“ ging in ${(it.shareWithUnrest * 100).roundToInt()} % der Fälle Unruhe voraus (Ø Stufe ${de1(it.meanMaxLevel)}, ${it.events} Ereignisse" +
                (if (it.cancelled > 0) ", ${it.cancelled}× abgesagt oder früher gegangen" else "") + ")."
        }
        val pre = r.eventPhases.firstOrNull { it.label.startsWith("In den 7 Tagen") }
        val other = r.eventPhases.firstOrNull { it.label.startsWith("Übrige") }
        if (pre != null && other != null && pre.events >= 2 && other.events >= 2 && pre.meanMaxLevel - other.meanMaxLevel >= 0.7) {
            out += "Ereignisse in den 7 Tagen vor der Periode gingen mit stärkerer Unruhe einher (Ø ${de1(pre.meanMaxLevel)}) als zu anderen Zeiten (Ø ${de1(other.meanMaxLevel)}). Bei der Planung kann das hilfreich sein."
        }
        r.events.firstOrNull()?.let {
            out += "An Tagen mit bzw. nach „${it.event}“ trat „${it.target}“ in ${(it.rateWith * 100).roundToInt()} % der Fälle auf, sonst in ${(it.rateWithout * 100).roundToInt()} %."
        }
        r.nextDay.firstOrNull()?.let {
            out += "Auf „${it.a}“ folgt am nächsten Tag häufig „${it.b}“ (${it.days.roundToInt()}-mal beobachtet)."
        }
        if (r.compare.size >= 2) {
            val base = r.compare.first()
            r.compare.drop(1).forEach { c ->
                if (c.trackedDays < 14 || base.trackedDays < 14) {
                    out += "Für einen Vergleich „${c.label}“ gegenüber der Zeit ohne Medikation braucht es noch mehr Einträge."
                } else {
                    val parts = mutableListOf<String>()
                    if (base.unrestPer30 != null && c.unrestPer30 != null) parts += "Unruhe-Episoden pro 30 Tage ${de1(base.unrestPer30)} → ${de1(c.unrestPer30)}"
                    if (base.meanUnrestLevel != null && c.meanUnrestLevel != null) parts += "Ø Stufe ${de1(base.meanUnrestLevel)} → ${de1(c.meanUnrestLevel)}"
                    if (base.panicPer30 != null && c.panicPer30 != null) parts += "Panikattacken pro 30 Tage ${de1(base.panicPer30)} → ${de1(c.panicPer30)}"
                    if (base.meanSleep != null && c.meanSleep != null) parts += "Schlafqualität ${de1(base.meanSleep)} → ${de1(c.meanSleep)}"
                    if (parts.isNotEmpty()) out += "Vergleich ohne Medikation → ${c.label}: ${parts.joinToString(", ")}."
                }
            }
        }
        return out
    }
}
