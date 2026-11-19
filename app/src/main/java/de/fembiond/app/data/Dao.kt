package de.fembiond.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FembiondDao {
    @Transaction
    suspend fun mergeTerm(from: Long, into: Long, fromLabel: String, intoLabel: String, eventType: Boolean, uses: Int, keepHidden: Boolean) {
        dropDuplicateEntryTerms(from, into); moveEntryTerms(from, into)
        dropDuplicatePanicTerms(from, into); movePanicTerms(from, into)
        moveSynonyms(from, into)
        if (eventType) renameEventType(fromLabel, intoLabel)
        addUse(into, uses)
        // Startbegriffe würden beim nächsten Start neu angelegt – daher ausgeblendet behalten
        if (keepHidden) hideTerm(from) else deleteTermById(from)
    }

    // Perioden
    @Query("SELECT * FROM periods ORDER BY start")
    fun periods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY start")
    suspend fun periodsOnce(): List<PeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(p: PeriodEntity): Long

    @Update
    suspend fun updatePeriod(p: PeriodEntity)

    @Query("DELETE FROM periods WHERE id = :id")
    suspend fun deletePeriod(id: Long)

    // Begriffe
    @Query("SELECT * FROM terms ORDER BY label")
    fun terms(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms")
    suspend fun termsOnce(): List<TermEntity>

    // Begriffe zusammenführen / löschen
    @Query("DELETE FROM entry_terms WHERE termId = :from AND entryId IN (SELECT entryId FROM entry_terms WHERE termId = :into)")
    suspend fun dropDuplicateEntryTerms(from: Long, into: Long)
    @Query("UPDATE entry_terms SET termId = :into WHERE termId = :from")
    suspend fun moveEntryTerms(from: Long, into: Long)
    @Query("DELETE FROM panic_terms WHERE termId = :from AND panicId IN (SELECT panicId FROM panic_terms WHERE termId = :into)")
    suspend fun dropDuplicatePanicTerms(from: Long, into: Long)
    @Query("UPDATE panic_terms SET termId = :into WHERE termId = :from")
    suspend fun movePanicTerms(from: Long, into: Long)
    @Query("UPDATE synonyms SET termId = :into WHERE termId = :from")
    suspend fun moveSynonyms(from: Long, into: Long)
    @Query("UPDATE events SET type = :into WHERE type = :from")
    suspend fun renameEventType(from: String, into: String)
    @Query("UPDATE terms SET useCount = useCount + :add WHERE id = :id")
    suspend fun addUse(id: Long, add: Int)
    @Query("UPDATE terms SET hidden = 1, useCount = 0 WHERE id = :id")
    suspend fun hideTerm(id: Long)
    @Query("DELETE FROM terms WHERE id = :id")
    suspend fun deleteTermById(id: Long)
    @Query("SELECT (SELECT COUNT(*) FROM entry_terms WHERE termId = :id) + (SELECT COUNT(*) FROM panic_terms WHERE termId = :id)")
    suspend fun termUsage(id: Long): Int

    @Query("SELECT * FROM terms WHERE label = :label AND category = :category LIMIT 1")
    suspend fun term(label: String, category: String): TermEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTerm(t: TermEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTerms(t: List<TermEntity>)

    @Update
    suspend fun updateTerm(t: TermEntity)

    @Query("UPDATE terms SET useCount = useCount + 1, lastUsed = :now WHERE id = :id")
    suspend fun touchTerm(id: Long, now: Long)

    @Query("SELECT * FROM synonyms")
    fun synonyms(): Flow<List<SynonymEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonym(s: SynonymEntity)

    // Einträge
    @Insert
    suspend fun insertEntry(e: EntryEntity): Long

    @Insert
    suspend fun insertEntryTerms(t: List<EntryTermEntity>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query(
        """SELECT e.id AS entryId, e.day, e.minute, t.label, t.category, et.intensity, et.valence, e.backfilled, e.note
           FROM entries e JOIN entry_terms et ON et.entryId = e.id JOIN terms t ON t.id = et.termId
           ORDER BY e.day, e.minute"""
    )
    fun occurrences(): Flow<List<OccurrenceRow>>

    @Query("SELECT * FROM entries ORDER BY day DESC, minute DESC")
    fun entries(): Flow<List<EntryEntity>>

    // Panik
    @Insert
    suspend fun insertPanic(p: PanicEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPanicTerms(t: List<PanicTermEntity>)

    @Query("DELETE FROM panics WHERE id = :id")
    suspend fun deletePanic(id: Long)

    @Query("SELECT * FROM panics ORDER BY day, minute")
    fun panics(): Flow<List<PanicEntity>>

    @Query("SELECT pt.panicId, t.label, t.category FROM panic_terms pt JOIN terms t ON t.id = pt.termId")
    fun panicTerms(): Flow<List<PanicTermRow>>

    // Ereignisse
    @Query("SELECT * FROM events ORDER BY day")
    fun events(): Flow<List<EventEntity>>

    @Upsert
    suspend fun upsertEvent(e: EventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("UPDATE panics SET eventId = NULL WHERE eventId = :id")
    suspend fun unlinkEvent(id: Long)

    @Query("SELECT * FROM events") suspend fun allEvents(): List<EventEntity>
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Insert suspend fun restoreEvents(l: List<EventEntity>)

    // MRS
    @Query("SELECT * FROM mrs ORDER BY day")
    fun mrs(): Flow<List<MrsEntity>>

    @Insert
    suspend fun insertMrs(m: MrsEntity)

    @Query("SELECT * FROM mrs") suspend fun allMrs(): List<MrsEntity>
    @Query("DELETE FROM mrs") suspend fun clearMrs()
    @Insert suspend fun restoreMrs(l: List<MrsEntity>)

    // Profil
    @Query("SELECT * FROM profile WHERE id = 1")
    fun profile(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsertProfile(p: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = 1") suspend fun profileOnce(): ProfileEntity?

    // Medikation
    @Query("SELECT * FROM med_phases ORDER BY start")
    fun medPhases(): Flow<List<MedPhaseEntity>>

    @Upsert
    suspend fun upsertMedPhase(m: MedPhaseEntity): Long

    @Query("DELETE FROM med_phases WHERE id = :id")
    suspend fun deleteMedPhase(id: Long)

    @Query("SELECT * FROM intakes")
    fun intakes(): Flow<List<IntakeEntity>>

    @Upsert
    suspend fun upsertIntake(i: IntakeEntity)

    // GAD-7
    @Query("SELECT * FROM gad ORDER BY day")
    fun gad(): Flow<List<GadEntity>>

    @Insert
    suspend fun insertGad(g: GadEntity)

    // Backup
    @Query("SELECT * FROM entries") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM entry_terms") suspend fun allEntryTerms(): List<EntryTermEntity>
    @Query("SELECT * FROM panics") suspend fun allPanics(): List<PanicEntity>
    @Query("SELECT * FROM panic_terms") suspend fun allPanicTerms(): List<PanicTermEntity>
    @Query("SELECT * FROM med_phases") suspend fun allMedPhases(): List<MedPhaseEntity>
    @Query("SELECT * FROM intakes") suspend fun allIntakes(): List<IntakeEntity>
    @Query("SELECT * FROM gad") suspend fun allGad(): List<GadEntity>
    @Query("SELECT * FROM synonyms") suspend fun allSynonyms(): List<SynonymEntity>

    @Query("DELETE FROM periods") suspend fun clearPeriods()
    @Query("DELETE FROM terms") suspend fun clearTerms()
    @Query("DELETE FROM synonyms") suspend fun clearSynonyms()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM panics") suspend fun clearPanics()
    @Query("DELETE FROM med_phases") suspend fun clearMeds()
    @Query("DELETE FROM gad") suspend fun clearGad()

    @Insert suspend fun restorePeriods(l: List<PeriodEntity>)
    @Insert suspend fun restoreTerms(l: List<TermEntity>)
    @Insert suspend fun restoreSynonyms(l: List<SynonymEntity>)
    @Insert suspend fun restoreEntries(l: List<EntryEntity>)
    @Insert suspend fun restoreEntryTerms(l: List<EntryTermEntity>)
    @Insert suspend fun restorePanics(l: List<PanicEntity>)
    @Insert suspend fun restorePanicTerms(l: List<PanicTermEntity>)
    @Insert suspend fun restoreMeds(l: List<MedPhaseEntity>)
    @Insert suspend fun restoreIntakes(l: List<IntakeEntity>)
    @Insert suspend fun restoreGad(l: List<GadEntity>)

    @Transaction
    suspend fun clearAll() {
        clearEntries(); clearPanics(); clearEvents(); clearMrs(); clearMeds(); clearGad(); clearSynonyms(); clearTerms(); clearPeriods()
    }
}
