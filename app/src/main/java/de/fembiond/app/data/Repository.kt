package de.fembiond.app.data

import de.fembiond.app.domain.Category
import de.fembiond.app.domain.CycleEngine
import de.fembiond.app.domain.CycleSummary
import de.fembiond.app.domain.GadRecord
import de.fembiond.app.domain.Lexicon
import de.fembiond.app.domain.MedicationPhase
import de.fembiond.app.domain.Episode
import de.fembiond.app.domain.MrsRecord
import de.fembiond.app.domain.Profile
import de.fembiond.app.domain.EventOutcome
import de.fembiond.app.domain.PlannedEvent
import de.fembiond.app.domain.PeriodRecord
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.domain.ScheduleType
import de.fembiond.app.domain.TermOccurrence
import de.fembiond.app.domain.TermParser
import de.fembiond.app.domain.TermRanking
import de.fembiond.app.domain.Valence
import de.fembiond.app.domain.VocabItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class TermInfo(
    val id: Long,
    val label: String,
    val category: Category,
    val valence: Valence?,
    val builtIn: Boolean,
    val useCount: Int,
    val hidden: Boolean,
    val score: Double,
)

data class EntryView(
    val id: Long,
    val time: LocalDateTime,
    val note: String,
    val source: String,
    val backfilled: Boolean,
    val terms: List<TermOccurrence>,
)

/** Ein ausgewählter Begriff vor dem Speichern. */
data class Selection(val label: String, val category: Category, val intensity: Int? = null, val valence: Valence? = null)

data class AppData(
    val today: LocalDate,
    val settings: Settings,
    val periods: List<PeriodRecord>,
    val medications: List<MedicationPhase>,
    val intakes: Set<Pair<Long, LocalDate>>,
    val occurrences: List<TermOccurrence>,
    val entries: List<EntryView>,
    val episodes: List<Episode>,
    val events: List<PlannedEvent>,
    val gad: List<GadRecord>,
    val mrs: List<MrsRecord>,
    val profile: Profile?,
    val terms: List<TermInfo>,
    val summary: CycleSummary,
    val parser: TermParser,
) {
    fun suggestions(category: Category): List<TermInfo> =
        terms.filter { it.category == category && !it.hidden }.sortedByDescending { it.score }

    /** Alter aus dem Geburtsdatum (Profil), sonst aus dem Geburtsjahr. */
    val age: Int? get() = profile?.age(today) ?: settings.birthYear?.let { today.year - it }

    val mrsDue: Boolean
        get() {
            val last = mrs.maxOfOrNull { it.date } ?: return occurrences.isNotEmpty()
            return ChronoUnit.DAYS.between(last, today) >= settings.mrsIntervalDays
        }

    val gadDue: Boolean
        get() {
            val last = gad.maxOfOrNull { it.date } ?: return occurrences.isNotEmpty()
            return ChronoUnit.DAYS.between(last, today) >= settings.gadIntervalDays
        }
}

@Serializable
data class BackupData(
    val version: Int = 2,
    val periods: List<PeriodEntity>,
    val terms: List<TermEntity>,
    val synonyms: List<SynonymEntity>,
    val entries: List<EntryEntity>,
    val entryTerms: List<EntryTermEntity>,
    val panics: List<PanicEntity>,
    val panicTerms: List<PanicTermEntity>,
    val meds: List<MedPhaseEntity>,
    val intakes: List<IntakeEntity>,
    val gad: List<GadEntity>,
    val events: List<EventEntity> = emptyList(),
    val mrs: List<MrsEntity> = emptyList(),
    val profile: ProfileEntity? = null,
)

class Repository(
    private val dao: FembiondDao,
    val settingsStore: SettingsStore,
    scope: CoroutineScope,
) {
    init {
        scope.launch { seedIfEmpty() }
    }

    /** Fügt fehlende Startbegriffe hinzu (auch nach Updates); vorhandene bleiben unverändert. */
    private suspend fun seedIfEmpty() {
        val seeds = Lexicon.entries.filter { it.suggested }.map {
            TermEntity(label = it.label, category = it.category.name, valence = it.valence?.value, builtIn = true)
        } + listOf(
            TermEntity(label = ScaleTerms.SLEEP_QUALITY, category = Category.SLEEP_ENERGY.name, valence = null, builtIn = true, hidden = true),
            TermEntity(label = ScaleTerms.ENERGY, category = Category.SLEEP_ENERGY.name, valence = null, builtIn = true, hidden = true),
        )
        dao.insertTerms(seeds)
    }

    private val base: Flow<Triple<Settings, List<PeriodEntity>, Triple<List<MedPhaseEntity>, List<MrsEntity>, ProfileEntity?>>> =
        combine(settingsStore.settings, dao.periods(), dao.medPhases(), dao.mrs(), dao.profile()) { s, p, m, r, pr -> Triple(s, p, Triple(m, r, pr)) }

    private val records: Flow<Triple<List<OccurrenceRow>, List<EntryEntity>, Pair<List<PanicEntity>, List<PanicTermRow>>>> =
        combine(dao.occurrences(), dao.entries(), dao.panics(), dao.panicTerms()) { o, e, p, pt -> Triple(o, e, p to pt) }

    private val vocab: Flow<Triple<List<TermEntity>, List<SynonymEntity>, Triple<List<IntakeEntity>, List<GadEntity>, List<EventEntity>>>> =
        combine(dao.terms(), dao.synonyms(), dao.intakes(), dao.gad(), dao.events()) { t, s, i, g, e -> Triple(t, s, Triple(i, g, e)) }

    val data: StateFlow<AppData?> = combine(base, records, vocab) { b, r, v -> build(b, r, v) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private var cachedParserKey: Int = 0
    private var cachedParser: TermParser? = null

    private fun build(
        b: Triple<Settings, List<PeriodEntity>, Triple<List<MedPhaseEntity>, List<MrsEntity>, ProfileEntity?>>,
        r: Triple<List<OccurrenceRow>, List<EntryEntity>, Pair<List<PanicEntity>, List<PanicTermRow>>>,
        v: Triple<List<TermEntity>, List<SynonymEntity>, Triple<List<IntakeEntity>, List<GadEntity>, List<EventEntity>>>,
    ): AppData {
        val today = LocalDate.now()
        val (settings, periodE, extra) = b
        val (medE, mrsE, profileE) = extra
        val periods = periodE.map { it.toDomain() }
        val meds = medE.map { it.toDomain() }
        val summary = CycleEngine.analyze(periods, meds, today, settings.typicalCycle, settings.typicalPeriod)

        val occ = r.first.map { it.toOccurrence() }
        val byEntry = r.first.groupBy { it.entryId }
        val entries = r.second.map { e ->
            EntryView(e.id, time(e.day, e.minute), e.note, e.source, e.backfilled, byEntry[e.id].orEmpty().map { it.toOccurrence() })
        }
        val ptByPanic = r.third.second.groupBy { it.panicId }
        val panics = r.third.first.map { p ->
            val t = ptByPanic[p.id].orEmpty()
            Episode(
                id = p.id,
                time = time(p.day, p.minute),
                durationMinutes = p.durationMinutes,
                level = p.level,
                eventId = p.eventId,
                symptoms = t.filter { it.category == Category.PANIC_SYMPTOM.name }.map { it.label },
                triggers = t.filter { it.category == Category.PANIC_TRIGGER.name }.map { it.label },
                helped = t.filter { it.category == Category.PANIC_HELP.name }.map { it.label },
                note = p.note,
                backfilled = p.backfilled,
            )
        }

        val (termE, synE, rest) = v
        val now = System.currentTimeMillis()
        val terms = termE.mapNotNull { t ->
            val cat = runCatching { Category.valueOf(t.category) }.getOrNull() ?: return@mapNotNull null
            val days = t.lastUsed?.let { (now - it) / 86_400_000L }
            TermInfo(t.id, t.label, cat, Valence.of(t.valence), t.builtIn, t.useCount, t.hidden, TermRanking.score(t.useCount, days, t.builtIn))
        }

        val key = termE.hashCode() * 31 + synE.hashCode()
        val parser = cachedParser.takeIf { key == cachedParserKey } ?: run {
            val byId = termE.associateBy { it.id }
            val items = Lexicon.entries.flatMap { e -> (listOf(e.label) + e.synonyms).map { VocabItem(it, e.label, e.category, e.valence) } } +
                terms.filter { !it.hidden }
                    .map { VocabItem(it.label, it.label, it.category, it.valence) } +
                synE.mapNotNull { s ->
                    val t = byId[s.termId] ?: return@mapNotNull null
                    val cat = runCatching { Category.valueOf(t.category) }.getOrNull() ?: return@mapNotNull null
                    VocabItem(s.phrase, t.label, cat, Valence.of(t.valence))
                }
            TermParser(items).also { cachedParser = it; cachedParserKey = key }
        }

        return AppData(
            today = today,
            settings = settings,
            periods = periods,
            medications = meds,
            intakes = rest.first.filter { it.taken }.map { it.phaseId to LocalDate.ofEpochDay(it.day) }.toSet(),
            occurrences = occ,
            entries = entries,
            episodes = panics,
            events = rest.third.map { e ->
                PlannedEvent(e.id, LocalDate.ofEpochDay(e.day), e.type, e.title, e.note,
                    runCatching { EventOutcome.valueOf(e.outcome) }.getOrDefault(EventOutcome.PLANNED))
            },
            gad = rest.second.map { GadRecord(LocalDate.ofEpochDay(it.day), it.score) },
            mrs = mrsE.map { m -> MrsRecord(LocalDate.ofEpochDay(m.day), m.answers.split(",").mapNotNull { it.toIntOrNull() }) },
            profile = profileE?.let { Profile(it.name, it.birthDate?.let(LocalDate::ofEpochDay)) },
            terms = terms,
            summary = summary,
            parser = parser,
        )
    }

    // ---------- Einträge ----------

    private suspend fun termId(sel: Selection, now: Long): Long {
        val existing = dao.term(sel.label, sel.category.name)
        val id = existing?.id ?: dao.insertTerm(
            TermEntity(label = sel.label, category = sel.category.name, valence = sel.valence?.value, builtIn = false)
        ).let { if (it > 0) it else dao.term(sel.label, sel.category.name)!!.id }
        if (existing?.hidden == true && sel.label != ScaleTerms.SLEEP_QUALITY && sel.label != ScaleTerms.ENERGY) {
            dao.updateTerm(existing.copy(hidden = false))
        }
        dao.touchTerm(id, now)
        return id
    }

    suspend fun saveEntry(time: LocalDateTime, note: String, selections: List<Selection>, source: String, backfilled: Boolean = false) {
        if (selections.isEmpty() && note.isBlank()) return
        val now = System.currentTimeMillis()
        val entryId = dao.insertEntry(
            EntryEntity(day = time.toLocalDate().toEpochDay(), minute = time.hour * 60 + time.minute, note = note.trim(), source = source, backfilled = backfilled)
        )
        dao.insertEntryTerms(selections.map { EntryTermEntity(entryId = entryId, termId = termId(it, now), intensity = it.intensity, valence = it.valence?.value) })
    }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    suspend fun learnSynonym(phrase: String, label: String, category: Category) {
        val clean = phrase.trim().lowercase()
        if (clean.isEmpty() || clean == label.lowercase()) return
        val id = termId(Selection(label, category), System.currentTimeMillis())
        dao.insertSynonym(SynonymEntity(clean, id))
    }

    suspend fun updateTerm(info: TermInfo, label: String = info.label, category: Category = info.category, hidden: Boolean = info.hidden) {
        val t = dao.termsOnce().firstOrNull { it.id == info.id } ?: return
        dao.updateTerm(t.copy(label = label.trim(), category = category.name, hidden = hidden))
    }

    // ---------- Panik ----------

    suspend fun saveEpisode(
        time: LocalDateTime, duration: Int?, level: Int,
        symptoms: List<String>, triggers: List<String>, helped: List<String>, note: String,
        backfilled: Boolean = false, eventId: Long? = null,
    ) {
        val now = System.currentTimeMillis()
        val id = dao.insertPanic(
            PanicEntity(
                day = time.toLocalDate().toEpochDay(), minute = time.hour * 60 + time.minute, durationMinutes = duration,
                intensity = level, note = note.trim(), backfilled = backfilled, level = level, eventId = eventId,
            )
        )
        val terms = symptoms.map { Selection(it, Category.PANIC_SYMPTOM) } + triggers.map { Selection(it, Category.PANIC_TRIGGER) } + helped.map { Selection(it, Category.PANIC_HELP) }
        dao.insertPanicTerms(terms.map { PanicTermEntity(id, termId(it, now)) })
    }

    // ---------- Ereignisse ----------

    suspend fun saveEvent(e: PlannedEvent): Long {
        termId(Selection(e.type, Category.EVENT_TYPE), System.currentTimeMillis())
        return dao.upsertEvent(EventEntity(e.id, e.date.toEpochDay(), e.type, e.title.trim(), e.note.trim(), e.outcome.name))
    }

    suspend fun deleteEvent(id: Long) {
        dao.unlinkEvent(id)
        dao.deleteEvent(id)
    }

    suspend fun deletePanic(id: Long) = dao.deletePanic(id)

    // ---------- Perioden ----------

    suspend fun addPeriod(start: LocalDate, end: LocalDate?, approximate: Boolean = false, backfilled: Boolean = false) {
        dao.insertPeriod(PeriodEntity(start = start.toEpochDay(), end = end?.toEpochDay(), approximate = approximate, backfilled = backfilled))
    }

    suspend fun updatePeriod(p: PeriodRecord) {
        dao.updatePeriod(PeriodEntity(p.id, p.start.toEpochDay(), p.end?.toEpochDay(), p.approximate, p.backfilled))
    }

    suspend fun deletePeriod(id: Long) = dao.deletePeriod(id)

    /** Periode beginnt an [date]. Liegt ein Beginn sehr nahe, wird er verschoben statt dupliziert. */
    suspend fun startPeriod(date: LocalDate) {
        val near = dao.periodsOnce().firstOrNull { kotlin.math.abs(it.start - date.toEpochDay()) <= 3 }
        if (near != null) dao.updatePeriod(near.copy(start = date.toEpochDay(), end = near.end?.takeIf { it >= date.toEpochDay() }))
        else addPeriod(date, null)
    }

    suspend fun endPeriod(date: LocalDate) {
        val p = dao.periodsOnce().lastOrNull { it.start <= date.toEpochDay() } ?: return
        dao.updatePeriod(p.copy(end = date.toEpochDay()))
    }

    // ---------- Medikation ----------

    suspend fun saveMedication(m: MedicationPhase): Long = dao.upsertMedPhase(
        MedPhaseEntity(
            id = m.id, name = m.name, form = m.form, dose = m.dose, schedule = m.schedule.name,
            dayFrom = m.cycleDayFrom, dayTo = m.cycleDayTo, start = m.start.toEpochDay(), end = m.end?.toEpochDay(),
            review = m.reviewDate?.toEpochDay(), note = m.note,
        )
    )

    suspend fun deleteMedication(id: Long) = dao.deleteMedPhase(id)

    suspend fun setIntake(phaseId: Long, date: LocalDate, taken: Boolean) =
        dao.upsertIntake(IntakeEntity(phaseId, date.toEpochDay(), taken))

    // ---------- GAD-7 ----------

    suspend fun saveGad(date: LocalDate, answers: List<Int>) =
        dao.insertGad(GadEntity(day = date.toEpochDay(), answers = answers.joinToString(","), score = answers.sum()))

    // ---------- MRS & Profil ----------

    suspend fun saveMrs(date: LocalDate, answers: List<Int>) =
        dao.insertMrs(MrsEntity(day = date.toEpochDay(), answers = answers.joinToString(","), total = answers.sum()))

    suspend fun saveProfile(name: String, birthDate: LocalDate?) =
        dao.upsertProfile(ProfileEntity(1, name.trim(), birthDate?.toEpochDay()))

    // ---------- Einstellungen ----------

    suspend fun updateSettings(block: (Settings) -> Settings) {
        settingsStore.update(block, settingsStore.settings.first())
    }

    // ---------- Backup ----------

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun backupJson(): String = json.encodeToString(
        BackupData.serializer(),
        BackupData(
            periods = dao.periodsOnce(), terms = dao.termsOnce(), synonyms = dao.allSynonyms(), entries = dao.allEntries(),
            entryTerms = dao.allEntryTerms(), panics = dao.allPanics(), panicTerms = dao.allPanicTerms(),
            meds = dao.allMedPhases(), intakes = dao.allIntakes(), gad = dao.allGad(), events = dao.allEvents(),
            mrs = dao.allMrs(), profile = dao.profileOnce(),
        ),
    )

    suspend fun restoreJson(text: String) {
        val b = json.decodeFromString(BackupData.serializer(), text)
        dao.clearAll()
        dao.restorePeriods(b.periods); dao.restoreTerms(b.terms); dao.restoreSynonyms(b.synonyms)
        dao.restoreEntries(b.entries); dao.restoreEntryTerms(b.entryTerms); dao.restorePanics(b.panics)
        dao.restorePanicTerms(b.panicTerms); dao.restoreMeds(b.meds); dao.restoreIntakes(b.intakes); dao.restoreGad(b.gad); dao.restoreEvents(b.events); dao.restoreMrs(b.mrs)
        b.profile?.let { dao.upsertProfile(it) }
    }

    companion object {
        fun time(day: Long, minute: Int): LocalDateTime = LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60)
    }
}

private fun PeriodEntity.toDomain() = PeriodRecord(id, LocalDate.ofEpochDay(start), end?.let(LocalDate::ofEpochDay), approximate, backfilled)

private fun MedPhaseEntity.toDomain() = MedicationPhase(
    id = id, name = name, form = form, dose = dose,
    schedule = runCatching { ScheduleType.valueOf(schedule) }.getOrDefault(ScheduleType.CONTINUOUS),
    cycleDayFrom = dayFrom, cycleDayTo = dayTo, start = LocalDate.ofEpochDay(start), end = end?.let(LocalDate::ofEpochDay),
    reviewDate = review?.let(LocalDate::ofEpochDay), note = note,
)

private fun OccurrenceRow.toOccurrence() = TermOccurrence(
    time = Repository.time(day, minute),
    term = label,
    category = runCatching { Category.valueOf(category) }.getOrDefault(Category.MIND),
    intensity = intensity,
    valence = Valence.of(valence),
    backfilled = backfilled,
)
