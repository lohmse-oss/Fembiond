package de.fembiond.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Deviation(val label: String) {
    SHORT("kurzer Zyklus (unter 21 Tage)"),
    LONG("langer Zyklus (über 35 Tage)"),
    DIFFERS("deutliche Abweichung vom üblichen Zyklus"),
}

data class Cycle(
    val index: Int,
    val start: LocalDate,
    /** Start der folgenden Periode, null beim aktuellen Zyklus. */
    val nextStart: LocalDate?,
    val periodLength: Int,
    val medicated: Boolean,
    val approximate: Boolean,
    val backfilled: Boolean,
    val deviation: Deviation? = null,
) {
    val length: Int? get() = nextStart?.let { daysBetween(start, it) }
    val isComplete: Boolean get() = nextStart != null
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && (nextStart == null || date.isBefore(nextStart))
}

enum class StrawStage(val label: String, val description: String) {
    INSUFFICIENT(
        "Noch zu wenige Daten",
        "Für eine Einordnung werden mindestens 4 vollständige Zyklen ohne Medikation benötigt.",
    ),
    NONE(
        "Keine Auffälligkeit",
        "Die Zykluslängen sind bisher ohne Muster, das auf einen Übergang hindeutet.",
    ),
    EARLY(
        "Mögliche frühe Übergangsphase",
        "Aufeinanderfolgende Zyklen unterscheiden sich wiederholt um 7 Tage oder mehr. Nach STRAW+10 kann das auf eine frühe Perimenopause hindeuten – bitte ärztlich abklären.",
    ),
    LATE(
        "Mögliche späte Übergangsphase",
        "Es gab eine Blutungspause von 60 Tagen oder mehr. Nach STRAW+10 kann das auf eine späte Perimenopause hindeuten – bitte ärztlich abklären.",
    ),
}

data class DayInfo(
    val cycleIndex: Int,
    val cycleDay: Int,
    val phase: Phase,
    /** Tage bis zur nächsten Periode (1 = Vortag), nur bei abgeschlossenen Zyklen. */
    val daysBeforeNext: Int?,
    val medicated: Boolean,
    val predicted: Boolean,
)

data class Prediction(
    val from: LocalDate,
    val to: LocalDate,
    /** Prognose stützt sich auf ein zyklisches Medikationsschema (Abbruchblutung). */
    val withdrawalBleed: Boolean,
    val overdueDays: Int,
)

data class CurrentState(
    val cycleDay: Int,
    val phase: Phase,
    val cycleStart: LocalDate,
    val expectedLength: Int,
    val ovulation: LocalDate?,
    val fertileFrom: LocalDate?,
    val fertileTo: LocalDate?,
    val prediction: Prediction,
    val medicationToday: List<MedicationPhase>,
    val inPeriod: Boolean,
)

data class CycleSummary(
    val cycles: List<Cycle>,
    val baselineLength: Int,
    val baselineVariability: Double,
    val baselinePeriodLength: Int,
    val naturalCompleteCount: Int,
    val straw: StrawStage,
    val current: CurrentState?,
) {
    val hasData: Boolean get() = cycles.isNotEmpty()
}

fun daysBetween(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()

object CycleEngine {

    const val SHORT_LIMIT = 21
    const val LONG_LIMIT = 35
    const val DEVIATION_DAYS = 7
    const val LATE_GAP_DAYS = 60

    fun analyze(
        periods: List<PeriodRecord>,
        medications: List<MedicationPhase>,
        today: LocalDate,
        typicalCycle: Int = 28,
        typicalPeriod: Int = 5,
    ): CycleSummary {
        val sorted = periods.sortedBy { it.start }.distinctBy { it.start }
        if (sorted.isEmpty()) {
            return CycleSummary(emptyList(), typicalCycle, 0.0, typicalPeriod, 0, StrawStage.INSUFFICIENT, null)
        }

        var cycles = sorted.mapIndexed { i, p ->
            val next = sorted.getOrNull(i + 1)?.start
            val lastDay = next?.minusDays(1) ?: today
            val periodLength = p.end?.let { daysBetween(p.start, it) + 1 } ?: typicalPeriod
            Cycle(
                index = i,
                start = p.start,
                nextStart = next,
                periodLength = periodLength.coerceIn(1, 15),
                medicated = medications.any { m -> m.overlaps(p.start, lastDay) },
                approximate = p.approximate,
                backfilled = p.backfilled,
            )
        }

        val natural = cycles.filter { it.isComplete && !it.medicated }
        val recent = natural.takeLast(12)
        val weightedLengths = recent.flatMap { c -> List(if (c.approximate) 1 else 2) { c.length!! } }
        val baseline = if (weightedLengths.isEmpty()) typicalCycle else median(weightedLengths).roundToInt()
        val variability = if (recent.size < 2) 0.0 else stdDev(recent.map { it.length!!.toDouble() })
        val periodLengths = cycles.filter { sorted[it.index].end != null }.takeLast(12).map { it.periodLength }
        val basePeriod = if (periodLengths.isEmpty()) typicalPeriod else median(periodLengths).roundToInt()

        cycles = cycles.map { c ->
            val len = c.length ?: return@map c
            val dev = when {
                len < SHORT_LIMIT -> Deviation.SHORT
                len > LONG_LIMIT -> Deviation.LONG
                recent.size >= 3 && abs(len - baseline) >= DEVIATION_DAYS -> Deviation.DIFFERS
                else -> null
            }
            c.copy(deviation = dev)
        }

        val currentCycle = cycles.last()
        val straw = straw(natural, currentCycle, today)
        val current = currentState(currentCycle, medications, today, baseline, variability, recent.size)

        return CycleSummary(cycles, baseline, variability, basePeriod, natural.size, straw, current)
    }

    private fun MedicationPhase.overlaps(from: LocalDate, to: LocalDate): Boolean =
        !start.isAfter(to) && (end == null || !end.isBefore(from))

    fun straw(natural: List<Cycle>, current: Cycle, today: LocalDate): StrawStage {
        val lastTen = natural.takeLast(10)
        val currentDays = daysBetween(current.start, today) + 1
        if (lastTen.any { it.length!! >= LATE_GAP_DAYS } || (!current.medicated && currentDays >= LATE_GAP_DAYS)) {
            return StrawStage.LATE
        }
        if (lastTen.size < 4) return StrawStage.INSUFFICIENT
        val jumps = lastTen.zipWithNext().count { (a, b) -> abs(a.length!! - b.length!!) >= DEVIATION_DAYS }
        return if (jumps >= 2) StrawStage.EARLY else StrawStage.NONE
    }

    private fun currentState(
        cycle: Cycle,
        medications: List<MedicationPhase>,
        today: LocalDate,
        baseline: Int,
        variability: Double,
        sampleSize: Int,
    ): CurrentState? {
        if (today.isBefore(cycle.start)) return null
        val day = daysBetween(cycle.start, today) + 1
        val activeMeds = medications.filter { it.activeOn(today) }
        val cyclic = activeMeds.firstOrNull { it.schedule == ScheduleType.CYCLIC && it.cycleDayTo != null }

        val prediction: Prediction
        val expectedLength: Int
        if (cyclic != null) {
            // Abbruchblutung etwa 2–7 Tage nach der letzten Einnahme.
            val lastDose = cycle.start.plusDays(cyclic.cycleDayTo!!.toLong() - 1)
            var from = lastDose.plusDays(2)
            var to = lastDose.plusDays(7)
            val overdue = if (today.isAfter(to)) daysBetween(to, today) else 0
            if (overdue > 0) { from = today; to = today.plusDays(3) }
            prediction = Prediction(from, to, true, overdue)
            expectedLength = cyclic.cycleDayTo + 4
        } else {
            var spread = if (variability == 0.0) 1 else variability.roundToInt().coerceIn(1, 7)
            if (sampleSize < 3) spread = maxOf(spread, 3)
            expectedLength = baseline
            var from = cycle.start.plusDays((baseline - spread).toLong())
            var to = cycle.start.plusDays((baseline + spread).toLong())
            val overdue = if (today.isAfter(to)) daysBetween(to, today) else 0
            if (overdue > 0) { from = today; to = today.plusDays(spread.toLong()) }
            prediction = Prediction(from, to, false, overdue)
        }

        val ovDay = ovulationDay(expectedLength, cycle.periodLength)
        val ovulation = cycle.start.plusDays(ovDay.toLong() - 1)
        val showOvulation = cyclic == null || ovulation.isBefore(cycle.start.plusDays(cyclic.cycleDayFrom!!.toLong() - 1))
        return CurrentState(
            cycleDay = day,
            phase = phaseFor(day, expectedLength, cycle.periodLength),
            cycleStart = cycle.start,
            expectedLength = expectedLength,
            ovulation = if (showOvulation) ovulation else null,
            fertileFrom = if (showOvulation) ovulation.minusDays(5) else null,
            fertileTo = if (showOvulation) ovulation.plusDays(1) else null,
            prediction = prediction,
            medicationToday = activeMeds,
            inPeriod = day <= cycle.periodLength,
        )
    }

    fun ovulationDay(length: Int, periodLength: Int): Int = (length - 14).coerceAtLeast(periodLength + 2)

    fun phaseFor(cycleDay: Int, length: Int, periodLength: Int): Phase {
        val ov = ovulationDay(length, periodLength)
        return when {
            cycleDay <= periodLength -> Phase.MENSTRUATION
            abs(cycleDay - ov) <= 1 -> Phase.OVULATION
            cycleDay < ov -> Phase.FOLLICULAR
            else -> Phase.LUTEAL
        }
    }

    /** Ordnet ein Datum seinem Zyklus und seiner Phase zu. */
    fun dayInfo(summary: CycleSummary, date: LocalDate): DayInfo? {
        val cycle = summary.cycles.lastOrNull { it.contains(date) } ?: return null
        val day = daysBetween(cycle.start, date) + 1
        val length = cycle.length ?: summary.current?.expectedLength ?: summary.baselineLength
        return DayInfo(
            cycleIndex = cycle.index,
            cycleDay = day,
            phase = phaseFor(day, maxOf(length, day), cycle.periodLength),
            daysBeforeNext = cycle.nextStart?.let { daysBetween(date, it) },
            medicated = cycle.medicated,
            predicted = !cycle.isComplete,
        )
    }

    /**
     * Schätzt Zyklustag und Phase für ein zukünftiges Datum anhand der Prognose
     * (z. B. für geplante Ereignisse). Liefert null ohne aktuellen Zyklus.
     */
    fun projectDay(summary: CycleSummary, date: LocalDate): DayInfo? {
        val c = summary.current ?: return null
        val cycle = summary.cycles.last()
        val p = c.prediction
        val nextStart = p.from.plusDays((daysBetween(p.from, p.to) / 2).toLong())
        if (date.isBefore(nextStart)) {
            val day = daysBetween(c.cycleStart, date) + 1
            if (day < 1) return null
            val length = maxOf(daysBetween(c.cycleStart, nextStart), day)
            return DayInfo(cycle.index, day, phaseFor(day, length, cycle.periodLength), daysBetween(date, nextStart), cycle.medicated, true)
        }
        var start = nextStart
        var index = cycle.index + 1
        val len = summary.baselineLength.coerceAtLeast(15)
        while (!date.isBefore(start.plusDays(len.toLong()))) { start = start.plusDays(len.toLong()); index++ }
        val day = daysBetween(start, date) + 1
        return DayInfo(index, day, phaseFor(day, len, summary.baselinePeriodLength), len - day + 1, false, true)
    }

    /** Ist an diesem Datum laut Schema eine Einnahme vorgesehen? */
    fun intakeDue(phase: MedicationPhase, summary: CycleSummary, date: LocalDate): Boolean {
        if (!phase.activeOn(date)) return false
        if (phase.schedule == ScheduleType.CONTINUOUS) return true
        val info = dayInfo(summary, date) ?: return false
        return info.cycleDay in (phase.cycleDayFrom ?: 0)..(phase.cycleDayTo ?: 0)
    }
}

fun median(values: List<Int>): Double {
    if (values.isEmpty()) return 0.0
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
}

fun stdDev(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
}
