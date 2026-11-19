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
        mrs: List<MrsRecord> = emptyList(),
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
                                        o.intensity?.let { put("intensity_1_5", it) }; if (o.sudden) put("sudden_onset", true)
                                        o.valence?.let { put("valence", it.name) }
                                        if (o.backfilled) put("backfilled", true)
                                    })
                                }
                            }
                        })
                    }
            }
            putJsonArray("unrest_episodes") {
                input.episodes.filter { !it.date.isBefore(input.from) && !it.date.isAfter(input.to) }.forEach { p ->
                    val info = CycleEngine.dayInfo(s, p.date)
                    add(buildJsonObject {
                        put("datetime", p.time.toString())
                        info?.let { put("cycle_day", it.cycleDay); put("phase", it.phase.name); it.daysBeforeNext?.let { n -> put("days_before_next_period", n) } }
                        put("level_1_5", p.level)
                        put("level_label", p.unrestLevel.label)
                        put("is_panic_attack", p.isPanic)
                        p.durationMinutes?.let { put("duration_min_approx", it) }
                        p.eventId?.let { id -> input.events.firstOrNull { it.id == id }?.let { e -> put("related_event", e.displayName); put("days_until_event", daysBetween(p.date, e.date)) } }
                        put("body_signs", JsonArray(p.symptoms.map(::JsonPrimitive)))
                        put("triggers", JsonArray(p.triggers.map(::JsonPrimitive)))
                        put("helped", JsonArray(p.helped.map(::JsonPrimitive)))
                        if (p.backfilled) put("backfilled", true)
                    })
                }
            }
            putJsonArray("events") {
                input.events.filter { !it.date.isBefore(input.from) && !it.date.isAfter(input.to.plusDays(60)) }.forEach { e ->
                    val info = CycleEngine.dayInfo(s, e.date) ?: CycleEngine.projectDay(s, e.date)
                    add(buildJsonObject {
                        put("date", e.date.toString())
                        put("type", e.type)
                        if (e.title.isNotBlank()) put("title", e.title)
                        put("outcome", e.outcome.name)
                        info?.let { put("cycle_day", it.cycleDay); put("phase", it.phase.name); put("phase_is_forecast", it.predicted && e.date.isAfter(today)) }
                        if (!e.date.isAfter(today)) put("max_unrest_level_3_days_before_to_event", Analytics.anticipatoryLevel(e, input.episodes))
                    })
                }
            }
            Pms.analyze(s, input.occurrences, input.episodes, today, input.from, input.to).let { pms ->
                putJsonObject("pms_assessment_acog") {
                    put("cycles_assessable", pms.assessed); put("cycles_pms_typical", pms.typical); put("cycles_pmdd_like", pms.pmddLike)
                    put("verdict", pms.verdict); pms.pmddHint?.let { put("pmdd_hint", it) }
                    putJsonArray("per_period") {
                        pms.cycles.forEach { c ->
                            add(buildJsonObject {
                                put("period_start", c.periodStart.toString()); put("assessable", c.assessable)
                                c.reason?.let { put("reason", it) }
                                put("affective_relieved", JsonArray(c.affective.map(::JsonPrimitive)))
                                put("somatic_relieved", JsonArray(c.somatic.map(::JsonPrimitive)))
                                put("pms_typical", c.pmsTypical)
                            })
                        }
                    }
                    putJsonArray("symptoms_pre5_vs_day4to10") {
                        pms.symptoms.take(12).forEach { sym ->
                            add(buildJsonObject { put("term", sym.term); put("group", sym.group); put("pre_rate", round1(sym.preRate)); put("post_rate", round1(sym.postRate)) })
                        }
                    }
                }
            }
            putJsonArray("mrs_menopause_rating_scale") {
                mrs.filter { !it.date.isBefore(input.from) }.forEach { m ->
                    add(buildJsonObject {
                        put("date", m.date.toString()); put("total_0_44", m.total)
                        put("somatic_0_16", m.somatic); put("psychological_0_16", m.psychological); put("urogenital_0_12", m.urogenital)
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
                putJsonArray("unrest_by_phase") {
                    result.unrestByPhase.forEach { p ->
                        add(buildJsonObject {
                            put("phase", p.phase.name); put("episodes", p.count); put("panic_attacks", p.panicCount)
                            p.meanLevel?.let { put("mean_level", round1(it)) }
                            p.relativeFrequency?.let { put("relative_frequency", round1(it)) }
                        })
                    }
                }
                put("episodes_per_level_1_to_5", JsonArray(result.levelCounts.map { JsonPrimitive(it) }))
                putJsonArray("unrest_around_events") {
                    result.anticipation.forEach { a ->
                        add(buildJsonObject {
                            put("day_offset_to_event", a.offset); put("events_with_data", a.events)
                            a.meanMaxLevel?.let { put("mean_max_level", round1(it)) }
                            a.share?.let { put("share_with_unrest", round1(it)) }
                        })
                    }
                }
                putJsonArray("event_types") {
                    result.eventTypes.forEach { t ->
                        add(buildJsonObject {
                            put("type", t.type); put("events", t.events); put("share_with_unrest", round1(t.shareWithUnrest))
                            put("mean_max_level", round1(t.meanMaxLevel)); put("panic_attacks", t.panics); put("cancelled_or_left_early", t.cancelled)
                        })
                    }
                }
                putJsonArray("events_by_cycle_timing") {
                    result.eventPhases.forEach { t ->
                        add(buildJsonObject { put("timing", t.label); put("events", t.events); put("mean_max_level", round1(t.meanMaxLevel)); put("cancelled_or_left_early", t.cancelled) })
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
                            c.unrestPer30?.let { put("unrest_episodes_per_30_days", round1(it)) }
                            c.meanUnrestLevel?.let { put("mean_unrest_level", round1(it)) }
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
            appendLine("- Selbst erfasste Daten einer Frau mit Fokus auf Zyklus, Perimenopause und Unruhezuständen.")
            appendLine("- Unruhe wird als Episode in Stufen erfasst: 1 leicht, 2 spürbar, 3 stark (körperliche Anzeichen), 4 sehr stark (kurz vor Panik), 5 Panikattacke. Unruhe tritt häufig vor anstehenden Ereignissen auf (Feiern, Essen gehen, Konzerte). Episoden können mit einem Ereignis verknüpft sein.")
            appendLine("- `events` enthält geplante und vergangene Ereignisse mit Ausgang (wahrgenommen, früher gegangen, abgesagt) und der Zyklusphase am Ereignistag (bei zukünftigen Ereignissen als Prognose).")
            appendLine("- Zyklustag 1 = erster Tag der Periode. Phasen: MENSTRUATION, FOLLICULAR, OVULATION (geschätzt 14 Tage vor der nächsten Periode), LUTEAL.")
            appendLine("- Intensitäten auf einer Skala von 1 (leicht) bis 5 (sehr stark). `Schlafqualität` und `Energie` sind Skalenwerte 1–5 (5 = sehr gut).")
            appendLine("- `backfilled` = nachträglich aus Notizen erfasst (geringer gewichtet), `approximate` = Datum ungefähr.")
            appendLine("- Zyklen mit `medicated: true` lagen unter Medikation (z. B. zyklisches Progesteron mit Abbruchblutung) und sind nicht in der natürlichen Grundlinie enthalten.")
            appendLine("- STRAW+10: EARLY = wiederholt ≥ 7 Tage Unterschied zwischen aufeinanderfolgenden Zyklen; LATE = Blutungspause ≥ 60 Tage.")
            appendLine("- PMS-Einschätzung nach ACOG: ≥1 affektives und ≥1 körperliches Symptom in den 5 Tagen vor der Periode, die an Tag 4–10 abklingen, in 3 aufeinanderfolgenden Zyklen; PMDS-Hinweis bei ≥5 Symptomen inkl. Kernsymptom in ≥2 Zyklen.")
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
            appendLine("1. Welche Muster zwischen Zyklusphase und Unruhe (inkl. Steigerung bis zur Panik) erkennst du, und wie robust sind sie angesichts der Datenmenge?")
            appendLine("2. Wie verläuft die Unruhe vor Ereignissen, und welche Ereignisarten oder Zyklusphasen sind besonders belastend? Welche Strategien haben geholfen?")
            appendLine("3. Passen die Beschwerden zu PMS oder PMDS, und passen die Zyklusverläufe zu einer beginnenden Perimenopause? Wie lassen sich beide auseinanderhalten?")
            appendLine("4. Was fällt im Vergleich vor und unter Medikation auf?")
            appendLine("5. Welche Punkte sollten beim nächsten Arzt- oder Therapietermin angesprochen werden?")
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
