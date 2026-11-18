package de.fembiond.app.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

data class AnalysisInput(
    val summary: CycleSummary,
    val occurrences: List<TermOccurrence>,
    val panics: List<PanicRecord>,
    val medications: List<MedicationPhase>,
    val gad: List<GadRecord>,
    val from: LocalDate,
    val to: LocalDate,
    val categories: Set<Category> = Category.entries.toSet(),
    val terms: Set<String> = emptySet(),
)

data class CycleBar(
    val start: LocalDate,
    val length: Int?,
    val periodLength: Int,
    val deviation: Deviation?,
    val medicated: Boolean,
    val approximate: Boolean,
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
    val cycles: Int,
) {
    val changePercent: Double? get() = if (postmenstrual <= 0.0) null else (premenstrual - postmenstrual) / postmenstrual * 100
    /** DRSP-Kriterium: prämenstruell mindestens 30 % stärker (bzw. nur prämenstruell vorhanden). */
    val significant: Boolean get() = cycles >= 2 && premenstrual >= 0.5 && (postmenstrual == 0.0 || (changePercent ?: 0.0) >= 30.0)
}

data class PanicPhaseStat(
    val phase: Phase,
    val count: Int,
    val meanIntensity: Double?,
    /** Verhältnis Anteil Attacken / Anteil Tage. >1 = gehäuft. */
    val relativeFrequency: Double?,
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
    val panicPer30: Double?,
    val meanSleep: Double?,
    val meanEnergy: Double?,
    val medianCycle: Double?,
    val topTerms: List<Pair<String, Double>>,
)

data class MonthTrend(
    val month: YearMonth,
    val meanSleep: Double?,
    val meanEnergy: Double?,
    val panicCount: Int,
    val gad: Double?,
)

data class AnalysisResult(
    val trackedDays: Int,
    val cycles: CycleStats,
    val heatmap: Heatmap,
    val phaseRates: List<PhaseRate>,
    val drsp: List<DrspResult>,
    val panicByPhase: List<PanicPhaseStat>,
    val panicTotal: Int,
    val coOccurrence: List<PairStat>,
    val nextDay: List<PairStat>,
    val events: List<EventEffect>,
    val compare: List<PeriodCompare>,
    val trends: List<MonthTrend>,
    val insights: List<String>,
)

object Analytics {

    const val PANIC_LABEL = "Panikattacke"
    private const val BACKFILL_WEIGHT = 0.5

    fun analyze(input: AnalysisInput): AnalysisResult {
        val s = input.summary
        val inRange: (LocalDate) -> Boolean = { !it.isBefore(input.from) && !it.isAfter(input.to) }
        val allOcc = input.occurrences.filter { inRange(it.date) }
        val scaleOcc = allOcc.filter { it.term == ScaleTerms.SLEEP_QUALITY || it.term == ScaleTerms.ENERGY }
        val occ = allOcc.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
            .filter { it.category in input.categories && (input.terms.isEmpty() || it.term in input.terms) }
        val panics = input.panics.filter { inRange(it.date) }

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
        if (input.categories.contains(Category.PANIC_SYMPTOM) || input.terms.isEmpty()) {
            val map = HashMap<LocalDate, Pair<Double, Double>>()
            for (p in panics) {
                val w = if (p.backfilled) BACKFILL_WEIGHT else 1.0
                val prev = map[p.date]
                map[p.date] = maxOf(prev?.first ?: 0.0, w) to maxOf(prev?.second ?: 0.0, p.intensity.toDouble())
            }
            if (map.isNotEmpty()) termDays[PANIC_LABEL to Category.MIND] = map
        }

        val dayInfo = dayWeight.keys.associateWith { CycleEngine.dayInfo(s, it) }
        val topTerms = termDays.entries.sortedByDescending { e -> e.value.values.sumOf { it.first } }

        val cycles = cycleStats(s, input.from, input.to)
        val heatmap = heatmap(topTerms.take(15), dayWeight, dayInfo)
        val phaseRates = phaseRates(topTerms.take(20), dayWeight, dayInfo)
        val drsp = drsp(topTerms, dayWeight, dayInfo, s)
        val panicByPhase = panicByPhase(panics, dayWeight, dayInfo, s)
        val (co, next) = pairs(topTerms.take(25), dayWeight)
        val events = events(termDays, dayWeight)
        val compare = compare(input, dayWeight, allOcc, panics)
        val trends = trends(scaleOcc, panics, input.gad.filter { inRange(it.date) })

        val result = AnalysisResult(
            trackedDays = dayWeight.size,
            cycles = cycles,
            heatmap = heatmap,
            phaseRates = phaseRates,
            drsp = drsp,
            panicByPhase = panicByPhase,
            panicTotal = panics.size,
            coOccurrence = co,
            nextDay = next,
            events = events,
            compare = compare,
            trends = trends,
            insights = emptyList(),
        )
        return result.copy(insights = Insights.build(result, s))
    }

    private fun cycleStats(s: CycleSummary, from: LocalDate, to: LocalDate): CycleStats {
        val inRange = s.cycles.filter { c -> !c.start.isAfter(to) && (c.nextStart == null || c.nextStart.isAfter(from)) }
        val bars = inRange.map { CycleBar(it.start, it.length, it.periodLength, it.deviation, it.medicated, it.approximate) }
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

    private fun drsp(
        terms: List<Map.Entry<Pair<String, Category>, HashMap<LocalDate, Pair<Double, Double>>>>,
        dayWeight: Map<LocalDate, Double>,
        dayInfo: Map<LocalDate, DayInfo?>,
        s: CycleSummary,
    ): List<DrspResult> {
        val pre = dayWeight.keys.filter { dayInfo[it]?.daysBeforeNext in 1..7 }
        val post = dayWeight.keys.filter { val i = dayInfo[it]; i != null && i.cycleDay in 4..10 && i.daysBeforeNext != null }
        val cyclesBoth = s.cycles.count { c ->
            val nextCycle = s.cycles.getOrNull(c.index + 1)
            pre.count { d -> dayInfo[d]?.cycleIndex == c.index } >= 2 &&
                post.count { d -> dayInfo[d]?.cycleIndex == c.index } >= 2 && nextCycle != null
        }
        if (pre.isEmpty() || post.isEmpty()) return emptyList()
        fun mean(days: List<LocalDate>, map: Map<LocalDate, Pair<Double, Double>>): Double {
            var sum = 0.0
            var w = 0.0
            days.forEach { d -> val dw = dayWeight[d]!!; w += dw; sum += dw * (map[d]?.second ?: 0.0) }
            return if (w == 0.0) 0.0 else sum / w
        }
        return terms.map { (key, map) ->
            DrspResult(key.first, key.second, mean(pre, map), mean(post, map), cyclesBoth)
        }.filter { it.premenstrual > 0 || it.postmenstrual > 0 }
            .sortedByDescending { it.premenstrual - it.postmenstrual }
    }

    private fun panicByPhase(
        panics: List<PanicRecord>,
        dayWeight: Map<LocalDate, Double>,
        dayInfo: Map<LocalDate, DayInfo?>,
        s: CycleSummary,
    ): List<PanicPhaseStat> {
        val trackedPhase = HashMap<Phase, Double>()
        dayWeight.forEach { (d, w) -> dayInfo[d]?.let { trackedPhase.merge(it.phase, w, Double::plus) } }
        val totalTracked = trackedPhase.values.sum()
        val byPhase = panics.groupBy { CycleEngine.dayInfo(s, it.date)?.phase }
        val total = panics.count { CycleEngine.dayInfo(s, it.date) != null }
        return Phase.entries.map { p ->
            val list = byPhase[p].orEmpty()
            val dayShare = if (totalTracked > 0) (trackedPhase[p] ?: 0.0) / totalTracked else 0.0
            val attackShare = if (total > 0) list.size.toDouble() / total else 0.0
            PanicPhaseStat(
                phase = p,
                count = list.size,
                meanIntensity = list.takeIf { it.isNotEmpty() }?.map { it.intensity }?.average(),
                relativeFrequency = if (dayShare > 0 && total >= 3) attackShare / dayShare else null,
            )
        }
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
        panics: List<PanicRecord>,
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
            val p = panics.count { it.date in dset }
            val cyc = input.summary.cycles.filter { c -> c.isComplete && pred(c.start) }.map { it.length!! }
            val termRates = o.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
                .groupBy { it.term }
                .mapValues { (_, v) -> v.map { it.date }.toSet().size.toDouble() / maxOf(1, days.size) }
                .entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
            PeriodCompare(
                label = label,
                trackedDays = days.size,
                panicPer30 = if (days.size >= 7) p * 30.0 / days.size else null,
                meanSleep = sleep.takeIf { it.isNotEmpty() }?.average(),
                meanEnergy = energy.takeIf { it.isNotEmpty() }?.average(),
                medianCycle = cyc.takeIf { it.isNotEmpty() }?.let { median(it) },
                topTerms = termRates,
            )
        }
    }

    private fun trends(scale: List<TermOccurrence>, panics: List<PanicRecord>, gad: List<GadRecord>): List<MonthTrend> {
        val months = (scale.map { YearMonth.from(it.date) } + panics.map { YearMonth.from(it.date) } + gad.map { YearMonth.from(it.date) })
            .toSortedSet()
        return months.map { m ->
            val sc = scale.filter { YearMonth.from(it.date) == m }
            MonthTrend(
                month = m,
                meanSleep = sc.filter { it.term == ScaleTerms.SLEEP_QUALITY }.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average(),
                meanEnergy = sc.filter { it.term == ScaleTerms.ENERGY }.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average(),
                panicCount = panics.count { YearMonth.from(it.date) == m },
                gad = gad.filter { YearMonth.from(it.date) == m }.map { it.score }.takeIf { it.isNotEmpty() }?.average(),
            )
        }
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
            out += "Der übliche Zyklus dauert ${m.roundToInt()} Tage" + (sd?.let { " (Schwankung ± ${"%.1f".format(it)} Tage)" } ?: "") + "."
        }
        val deviations = r.cycles.bars.count { it.deviation != null }
        if (deviations > 0) out += "$deviations ${if (deviations == 1) "Zyklus weicht" else "Zyklen weichen"} deutlich vom Üblichen ab."
        r.cycles.trendPerCycle?.let { t ->
            if (t >= 0.8) out += "Die Zyklen werden tendenziell länger (etwa +${"%.1f".format(t)} Tage pro Zyklus)."
            if (t <= -0.8) out += "Die Zyklen werden tendenziell kürzer (etwa ${"%.1f".format(t)} Tage pro Zyklus)."
        }
        if (s.straw == StrawStage.EARLY || s.straw == StrawStage.LATE) out += s.straw.description

        r.drsp.filter { it.significant }.take(3).forEach { d ->
            val change = d.changePercent
            out += if (change == null) {
                "„${d.term}“ tritt fast nur in den 7 Tagen vor der Periode auf (${d.cycles} Zyklen ausgewertet)."
            } else {
                "„${d.term}“ ist in den 7 Tagen vor der Periode etwa ${change.roundToInt()} % stärker als in der Woche nach der Periode (${d.cycles} Zyklen ausgewertet)."
            }
        }
        r.panicByPhase.filter { (it.relativeFrequency ?: 0.0) >= 1.5 && it.count >= 2 }.maxByOrNull { it.relativeFrequency!! }?.let {
            out += "Panikattacken häufen sich in der ${it.phase.label}: ${it.count} von ${r.panicTotal} Attacken, etwa ${"%.1f".format(it.relativeFrequency)}-mal so häufig wie im Durchschnitt."
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
                    if (base.panicPer30 != null && c.panicPer30 != null) parts += "Panikattacken pro 30 Tage ${"%.1f".format(base.panicPer30)} → ${"%.1f".format(c.panicPer30)}"
                    if (base.meanSleep != null && c.meanSleep != null) parts += "Schlafqualität ${"%.1f".format(base.meanSleep)} → ${"%.1f".format(c.meanSleep)}"
                    if (parts.isNotEmpty()) out += "Vergleich ohne Medikation → ${c.label}: ${parts.joinToString(", ")}."
                }
            }
        }
        return out
    }
}
