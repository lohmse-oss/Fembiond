package de.fembiond.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Export für einen KI-Agenten: Markdown mit Erklärung der Daten plus eingebettetes JSON.
 * Enthält bewusst keine Namen oder Kontaktdaten.
 */
object AgentExport {

    private val json = Json { prettyPrint = true }

    fun build(
        input: AnalysisInput,
        result: AnalysisResult,
        age: Int?,
        intakes: Set<Pair<Long, LocalDate>>,
        notes: List<Pair<LocalDate, String>>,
        today: LocalDate,
    ): String {
        val s = input.summary
        val doc = buildJsonObject {
            put("schema", "fembiond-export/1")
            put("created", today.toString())
            putJsonObject("range") { put("from", input.from.toString()); put("to", input.to.toString()) }
            age?.let { put("age_years", it) }
            putJsonObject("baseline") {
                put("cycle_length_median_days", s.baselineLength)
                put("cycle_length_sd_days", round1(s.baselineVariability))
                put("period_length_days", s.baselinePeriodLength)
                put("natural_complete_cycles", s.naturalCompleteCount)
                put("straw10_stage", s.straw.name)
                put("straw10_note", s.straw.description)
            }
            s.current?.let { c ->
                putJsonObject("current") {
                    put("cycle_day", c.cycleDay)
                    put("phase", c.phase.name)
                    put("next_period_from", c.prediction.from.toString())
                    put("next_period_to", c.prediction.to.toString())
                    put("prediction_based_on_withdrawal_bleed", c.prediction.withdrawalBleed)
                    put("overdue_days", c.prediction.overdueDays)
                }
            }
            putJsonArray("cycles") {
                s.cycles.filter { !it.start.isAfter(input.to) && (it.nextStart == null || !it.nextStart.isBefore(input.from)) }.forEach { c ->
                    add(buildJsonObject {
                        put("start", c.start.toString())
                        c.length?.let { put("length_days", it) } ?: put("ongoing", true)
                        put("period_days", c.periodLength)
                        put("medicated", c.medicated)
                        put("approximate", c.approximate)
                        put("backfilled", c.backfilled)
                        c.deviation?.let { put("deviation", it.name) }
                    })
                }
            }
            putJsonArray("medication_phases") {
                input.medications.forEach { m ->
                    add(buildJsonObject {
                        put("name", m.name); put("form", m.form); put("dose", m.dose)
                        put("schedule", m.schedule.name)
                        m.cycleDayFrom?.let { put("cycle_day_from", it) }
                        m.cycleDayTo?.let { put("cycle_day_to", it) }
                        put("start", m.start.toString())
                        m.end?.let { put("end", it.toString()) }
                        m.reviewDate?.let { put("review_date", it.toString()) }
                        put("intake_days_logged", intakes.count { it.first == m.id })
                    })
                }
            }
            putJsonArray("daily_entries") {
                input.occurrences.filter { !it.date.isBefore(input.from) && !it.date.isAfter(input.to) && it.category in input.categories }
                    .groupBy { it.date }.toSortedMap().forEach { (d, list) ->
                        val info = CycleEngine.dayInfo(s, d)
                        add(buildJsonObject {
                            put("date", d.toString())
                            info?.let { put("cycle_day", it.cycleDay); put("phase", it.phase.name); it.daysBeforeNext?.let { n -> put("days_before_next_period", n) } }
                            putJsonArray("terms") {
                                list.forEach { o ->
                                    add(buildJsonObject {
                                        put("term", o.term); put("category", o.category.name)
                                        o.intensity?.let { put("intensity_1_5", it) }
                                        o.valence?.let { put("valence", it.name) }
                                        if (o.backfilled) put("backfilled", true)
                                    })
                                }
                            }
                        })
                    }
            }
            putJsonArray("panic_attacks") {
                input.panics.filter { !it.date.isBefore(input.from) && !it.date.isAfter(input.to) }.forEach { p ->
                    val info = CycleEngine.dayInfo(s, p.date)
                    add(buildJsonObject {
                        put("datetime", p.time.toString())
                        info?.let { put("cycle_day", it.cycleDay); put("phase", it.phase.name) }
                        put("intensity_1_5", p.intensity)
                        p.durationMinutes?.let { put("duration_min_approx", it) }
                        put("symptoms", JsonArray(p.symptoms.map(::JsonPrimitive)))
                        put("triggers", JsonArray(p.triggers.map(::JsonPrimitive)))
                        put("helped", JsonArray(p.helped.map(::JsonPrimitive)))
                    })
                }
            }
            putJsonArray("gad7") { input.gad.filter { !it.date.isBefore(input.from) }.forEach { add(buildJsonObject { put("date", it.date.toString()); put("score_0_21", it.score) }) } }
            putJsonObject("analysis") {
                putJsonArray("premenstrual_vs_postmenstrual") {
                    result.drsp.take(15).forEach { d ->
                        add(buildJsonObject {
                            put("term", d.term); put("pre_mean", round1(d.premenstrual)); put("post_mean", round1(d.postmenstrual))
                            d.changePercent?.let { put("change_percent", it.roundToInt()) }
                            put("cycles", d.cycles); put("meets_drsp_30pct", d.significant)
                        })
                    }
                }
                putJsonArray("panic_by_phase") {
                    result.panicByPhase.forEach { p ->
                        add(buildJsonObject {
                            put("phase", p.phase.name); put("count", p.count)
                            p.meanIntensity?.let { put("mean_intensity", round1(it)) }
                            p.relativeFrequency?.let { put("relative_frequency", round1(it)) }
                        })
                    }
                }
                putJsonArray("event_effects") {
                    result.events.forEach { e ->
                        add(buildJsonObject { put("event", e.event); put("target", e.target); put("rate_with", round1(e.rateWith)); put("rate_without", round1(e.rateWithout)) })
                    }
                }
                putJsonArray("medication_comparison") {
                    result.compare.forEach { c ->
                        add(buildJsonObject {
                            put("group", c.label); put("tracked_days", c.trackedDays)
                            c.panicPer30?.let { put("panic_per_30_days", round1(it)) }
                            c.meanSleep?.let { put("mean_sleep_quality", round1(it)) }
                            c.meanEnergy?.let { put("mean_energy", round1(it)) }
                            c.medianCycle?.let { put("median_cycle_days", it) }
                        })
                    }
                }
            }
        }

        return buildString {
            appendLine("# Zyklus- und Befindlichkeitsdaten (Export aus Fembiond)")
            appendLine()
            appendLine("Zeitraum: ${input.from} bis ${input.to}. " + (age?.let { "Alter: $it Jahre. " } ?: "") + "Erstellt am $today.")
            appendLine()
            appendLine("## Kontext für die Auswertung")
            appendLine("- Selbst erfasste Daten einer Frau mit Fokus auf Zyklus, Perimenopause, innere Unruhe und Panikattacken.")
            appendLine("- Zyklustag 1 = erster Tag der Periode. Phasen: MENSTRUATION, FOLLICULAR, OVULATION (geschätzt 14 Tage vor der nächsten Periode), LUTEAL.")
            appendLine("- Intensitäten auf einer Skala von 1 (leicht) bis 5 (sehr stark). `Schlafqualität` und `Energie` sind Skalenwerte 1–5 (5 = sehr gut).")
            appendLine("- `backfilled` = nachträglich aus Notizen erfasst (geringer gewichtet), `approximate` = Datum ungefähr.")
            appendLine("- Zyklen mit `medicated: true` lagen unter Medikation (z. B. zyklisches Progesteron mit Abbruchblutung) und sind nicht in der natürlichen Grundlinie enthalten.")
            appendLine("- STRAW+10: EARLY = wiederholt ≥ 7 Tage Unterschied zwischen aufeinanderfolgenden Zyklen; LATE = Blutungspause ≥ 60 Tage.")
            appendLine("- Vergleich vor/nach der Periode nach DRSP-Methode: 7 Tage vor der Periode gegenüber Zyklustag 4–10; ≥ 30 % Zunahme gilt als relevant.")
            appendLine("- Die App stellt keine Diagnosen. Bitte Einschätzungen vorsichtig formulieren und auf ärztliche Abklärung verweisen.")
            appendLine()
            appendLine("## Zusammenfassung")
            result.insights.forEach { appendLine("- $it") }
            if (result.insights.isEmpty()) appendLine("- Noch zu wenige Daten für automatische Erkenntnisse.")
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("## Freitext-Notizen")
                notes.forEach { (d, n) -> appendLine("- $d: $n") }
            }
            appendLine()
            appendLine("## Mögliche Fragen an dich")
            appendLine("1. Welche Muster zwischen Zyklusphase und Unruhe/Panik erkennst du, und wie robust sind sie angesichts der Datenmenge?")
            appendLine("2. Passen die Zyklusverläufe zu einer beginnenden Perimenopause?")
            appendLine("3. Was fällt im Vergleich vor und unter Medikation auf?")
            appendLine("4. Welche Punkte sollten beim nächsten Arzttermin angesprochen werden?")
            appendLine()
            appendLine("## Daten (JSON)")
            appendLine("```json")
            appendLine(json.encodeToString(JsonObject.serializer(), doc))
            appendLine("```")
        }
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    @Suppress("unused")
    private fun empty() = buildJsonArray { }
}
