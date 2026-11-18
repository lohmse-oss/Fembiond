package de.fembiond.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "periods", indices = [Index(value = ["start"], unique = true)])
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long?,
    val approximate: Boolean,
    val backfilled: Boolean,
)

@Serializable
@Entity(tableName = "terms", indices = [Index(value = ["label", "category"], unique = true)])
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val category: String,
    val valence: Int?,
    val builtIn: Boolean,
    val useCount: Int = 0,
    val lastUsed: Long? = null,
    val hidden: Boolean = false,
)

@Serializable
@Entity(tableName = "synonyms")
data class SynonymEntity(
    @PrimaryKey val phrase: String,
    val termId: Long,
)

@Serializable
@Entity(tableName = "entries", indices = [Index("day")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val minute: Int,
    val note: String,
    val source: String,
    val backfilled: Boolean,
)

@Serializable
@Entity(
    tableName = "entry_terms",
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TermEntity::class, ["id"], ["termId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("entryId"), Index("termId")],
)
data class EntryTermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val termId: Long,
    val intensity: Int?,
    val valence: Int?,
)

@Serializable
@Entity(tableName = "panics", indices = [Index("day")])
data class PanicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val minute: Int,
    val durationMinutes: Int?,
    val intensity: Int,
    val note: String,
    val backfilled: Boolean,
)

@Serializable
@Entity(
    tableName = "panic_terms",
    primaryKeys = ["panicId", "termId"],
    foreignKeys = [
        ForeignKey(PanicEntity::class, ["id"], ["panicId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TermEntity::class, ["id"], ["termId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("termId")],
)
data class PanicTermEntity(val panicId: Long, val termId: Long)

@Serializable
@Entity(tableName = "med_phases")
data class MedPhaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: String,
    val dose: String,
    val schedule: String,
    val dayFrom: Int?,
    val dayTo: Int?,
    val start: Long,
    val end: Long?,
    val review: Long?,
    val note: String,
)

@Serializable
@Entity(
    tableName = "intakes",
    primaryKeys = ["phaseId", "day"],
    foreignKeys = [ForeignKey(MedPhaseEntity::class, ["id"], ["phaseId"], onDelete = ForeignKey.CASCADE)],
)
data class IntakeEntity(val phaseId: Long, val day: Long, val taken: Boolean)

@Serializable
@Entity(tableName = "gad")
data class GadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val answers: String,
    val score: Int,
)

/** Zeile für die Statistik: Begriff eines Eintrags mit Zeitpunkt. */
data class OccurrenceRow(
    val entryId: Long,
    val day: Long,
    val minute: Int,
    val label: String,
    val category: String,
    val intensity: Int?,
    val valence: Int?,
    val backfilled: Boolean,
    val note: String,
)

data class PanicTermRow(val panicId: Long, val label: String, val category: String)
