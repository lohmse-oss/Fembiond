package de.fembiond.app.export

import de.fembiond.app.BuildConfig
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.AnalysisInput
import de.fembiond.app.domain.Analytics
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.ScaleTerms
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Rohdaten mit Rechenweg zur Prüfung der Auswertung: alle Eingaben plus die von der App
 * berechneten Zwischenwerte (Zyklen, Zuordnung jedes Tages, DRSP-Paare, Ergebnisse).
 * Enthält keinen Namen und kein Geburtsdatum; Notiztexte nur auf Wunsch.
 */
object DiagnosticExport {

    private val json = Json { prettyPrint = true }

    fun build(data: AppData, includeNotes: Boolean): String {
        val s = data.summary
        val from = listOfNotNull(
            data.periods.minOfOrNull { it.start }, data.occurrences.minOfOrNull { it.date }, data.episodes.minOfOrNull { it.date },
        ).minOrNull() ?: data.today
        val input = AnalysisInput(s, data.occurrences, data.episodes, data.medications, data.gad, from, data.today, events = data.events)
        val result = Analytics.analyze(input)
        fun r1(v: Double) = (v * 100).roundToInt() / 100.0

        val doc = buildJsonObject {
            put("schema", "fembiond-diagnose/1")
            put("app_version", BuildConfig.VERSION_NAME)
            put("created", data.today.toString())
            data.age?.let { put("age_years", it) }
            putJsonObject("settings") {
                put("typical_cycle", data.settings.typicalCycle)
                put("typical_period", data.settings.typicalPeriod)
                data.settings.phaseOverride?.let { put("phase_override", it.name); put("phase_override_cycle_start", data.settings.phaseOverrideCycleStart.toString()) }
            }

            // ---------- Eingaben ----------
            putJsonArray("input_periods") {
                data.periods.sortedBy { it.start }.forEach { p ->
                    add(buildJsonObject {
                        put("start", p.start.toString()); p.end?.let { put("end", it.toString()) }
                        put("approximate", p.approximate); put("backfilled", p.backfilled)
                    })
                }
            }
            putJsonArray("input_medications") {
                data.medications.forEach { m ->
                    add(buildJsonObject {
                        put("name", m.name); put("form", m.form); put("dose", m.dose); put("schedule", m.schedule.name)
                        m.cycleDayFrom?.let { put("cycle_day_from", it) }; m.cycleDayTo?.let { put("cycle_day_to", it) }
                        put("start", m.start.toString()); m.end?.let { put("end", it.toString()) }
                    })
                }
            }
            put("input_intake_days", JsonArray(data.intakes.map { JsonPrimitive(it.second.toString()) }.sortedBy { it.content }))
            putJsonArray("input_entries") {
                data.entries.sortedBy { it.time }.forEach { e ->
                    add(buildJsonObject {
                        put("datetime", e.time.toString()); put("source", e.source); put("backfilled", e.backfilled)
                        if (includeNotes && e.note.isNotBlank()) put("note", e.note)
                        putJsonArray("terms") {
                            e.terms.forEach { t ->
                                add(buildJsonObject {
                                    put("term", t.term); put("category", t.category.name)
                                    t.intensity?.let { put("intensity", it) }; t.valence?.let { put("valence", it.name) }
                                })
                            }
                        }
                    })
                }
            }
            putJsonArray("input_unrest_episodes") {
                data.episodes.sortedBy { it.time }.forEach { e ->
                    add(buildJsonObject {
                        put("datetime", e.time.toString()); put("level", e.level); put("backfilled", e.backfilled)
                        e.durationMinutes?.let { put("duration_min", it) }
                        e.eventId?.let { put("event_id", it) }
                        put("symptoms", JsonArray(e.symptoms.map(::JsonPrimitive)))
                        put("triggers", JsonArray(e.triggers.map(::JsonPrimitive)))
                        put("helped", JsonArray(e.helped.map(::JsonPrimitive)))
                        if (includeNotes && e.note.isNotBlank()) put("note", e.note)
                    })
                }
            }
            putJsonArray("input_events") {
                data.events.sortedBy { it.date }.forEach { e ->
                    add(buildJsonObject {
                        put("id", e.id); put("date", e.date.toString()); put("type", e.type); put("outcome", e.outcome.name)
                        if (includeNotes && e.title.isNotBlank()) put("title", e.title)
                    })
                }
            }
            putJsonArray("input_gad7") { data.gad.forEach { add(buildJsonObject { put("date", it.date.toString()); put("score", it.score) }) } }
            putJsonArray("input_mrs") { data.mrs.forEach { m -> add(buildJsonObject { put("date", m.date.toString()); put("answers", JsonArray(m.answers.map { JsonPrimitive(it) })); put("total", m.total) }) } }

            // ---------- Berechnet ----------
            putJsonObject("computed") {
                put("analysis_range", "$from bis ${data.today}")
                put("baseline_cycle_length", s.baselineLength)
                put("baseline_variability_sd", r1(s.baselineVariability))
                put("baseline_period_length", s.baselinePeriodLength)
                put("natural_complete_cycles", s.naturalCompleteCount)
                put("straw", s.straw.name)
                s.current?.let { c ->
                    putJsonObject("current") {
                        put("cycle_start", c.cycleStart.toString()); put("cycle_day", c.cycleDay); put("phase", c.phase.name)
                        put("expected_length", c.expectedLength)
                        put("next_period_from", c.prediction.from.toString()); put("next_period_to", c.prediction.to.toString())
                        put("withdrawal_bleed", c.prediction.withdrawalBleed); put("overdue_days", c.prediction.overdueDays)
                    }
                }
                putJsonArray("cycles") {
                    s.cycles.forEach { c ->
                        add(buildJsonObject {
                            put("index", c.index); put("start", c.start.toString())
                            c.nextStart?.let { put("next_start", it.toString()) }
                            c.length?.let { put("length", it) }; put("period_length_used", c.periodLength)
                            put("medicated", c.medicated); c.deviation?.let { put("deviation", it.name) }
                        })
                    }
                }
                val tracked = (data.occurrences.map { it.date } + data.episodes.map { it.date }).toSortedSet()
                val pairs = result.drspPairs
                putJsonArray("tracked_days") {
                    tracked.forEach { d ->
                        val info = CycleEngine.dayInfo(s, d)
                        val window = pairs.firstNotNullOfOrNull { p ->
                            when {
                                d in (1..7).map { p.periodStart.minusDays(it.toLong()) } -> "pre(${p.periodStart})"
                                d in (3..9).map { p.periodStart.plusDays(it.toLong()) } -> "post(${p.periodStart})"
                                else -> null
                            }
                        }
                        add(buildJsonObject {
                            put("date", d.toString())
                            info?.let {
                                put("cycle_index", it.cycleIndex); put("cycle_day", it.cycleDay); put("phase", it.phase.name)
                                it.daysBeforeNext?.let { n -> put("days_before_next", n) }
                            }
                            window?.let { put("drsp_window", it) }
                            data.episodes.filter { it.date == d }.maxOfOrNull { it.level }?.let { put("max_unrest_level", it) }
                            val terms = data.occurrences.filter { it.date == d && it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }.map { it.term }.distinct()
                            put("terms", JsonArray(terms.map(::JsonPrimitive)))
                            data.occurrences.firstOrNull { it.date == d && it.term == ScaleTerms.SLEEP_QUALITY }?.intensity?.let { put("sleep", it) }
                        })
                    }
                }
                putJsonArray("drsp_pairs") {
                    pairs.forEach { p ->
                        add(buildJsonObject {
                            put("period_start", p.periodStart.toString()); put("pre_tracked_days", p.preDays); put("post_tracked_days", p.postDays)
                            put("counted", p.counted); p.reason?.let { put("reason", it) }
                        })
                    }
                }
                putJsonArray("drsp_results") {
                    result.drsp.forEach { d ->
                        add(buildJsonObject {
                            put("term", d.term); put("pre_mean", r1(d.premenstrual)); put("post_mean", r1(d.postmenstrual))
                            d.changePercent?.let { put("change_percent", it.roundToInt()) }
                            put("pre_days", d.preDays); put("post_days", d.postDays); put("pre_hits", d.preHits); put("post_hits", d.postHits)
                            put("periods_counted", d.cycles); put("significant", d.significant); put("verdict", d.verdict)
                        })
                    }
                }
                putJsonArray("unrest_by_phase") {
                    result.unrestByPhase.forEach { p ->
                        add(buildJsonObject {
                            put("phase", p.phase.name); put("count", p.count); put("panic", p.panicCount)
                            put("days_tracked", p.daysTracked); put("days_with_unrest", p.daysWithUnrest)
                            p.meanLevel?.let { put("mean_level", r1(it)) }; p.relativeFrequency?.let { put("relative_frequency", r1(it)) }
                        })
                    }
                }
                putJsonArray("anticipation") {
                    result.anticipation.forEach { a ->
                        add(buildJsonObject { put("offset", a.offset); put("events", a.events); a.meanMaxLevel?.let { put("mean_max_level", r1(it)) } })
                    }
                }
                put("calm_days_tracked", result.calmDays)
                putJsonArray("weekly_trends") {
                    result.trends.forEach { w ->
                        add(buildJsonObject {
                            put("week", w.label); put("week_start", w.weekStart.toString()); put("tracked", w.tracked)
                            w.unrestCount?.let { put("unrest_count", it) }; w.meanUnrestLevel?.let { put("mean_unrest_level", r1(it)) }
                        })
                    }
                }
                put("insights", JsonArray(result.insights.map(::JsonPrimitive)))
            }
        }
        return json.encodeToString(JsonObject.serializer(), doc)
    }

    @Suppress("unused")
    private fun d(s: String) = LocalDate.parse(s)
}
