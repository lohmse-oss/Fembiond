package de.fembiond.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** Kategorien für Begriffe. [label] ist der Anzeigename. */
enum class Category(val label: String, val shortLabel: String) {
    MIND("Stimmung & Geist", "Geist"),
    BODY("Körper", "Körper"),
    PERIMENOPAUSE("Perimenopause", "Perimeno."),
    SLEEP_ENERGY("Schlaf & Energie", "Schlaf"),
    BLEEDING("Blutung", "Blutung"),
    LIFE("Lebensumstände", "Leben"),
    MEDICATION_EFFECT("Medikation & Nebenwirkungen", "Medikation"),
    PANIC_SYMPTOM("Panik: Symptome", "Symptome"),
    PANIC_TRIGGER("Panik: Auslöser", "Auslöser"),
    PANIC_HELP("Panik: Was geholfen hat", "Hilfe");

    /** Kategorien, die im normalen Tageseintrag erscheinen. */
    val isDaily: Boolean get() = this !in setOf(PANIC_SYMPTOM, PANIC_TRIGGER, PANIC_HELP)
}

/** Bewertung eines Ereignisses (Lebensumstände). */
enum class Valence(val label: String, val value: Int) {
    BURDEN("belastend", -1), NEUTRAL("neutral", 0), POSITIVE("positiv", 1);

    companion object {
        fun of(value: Int?): Valence? = entries.firstOrNull { it.value == value }
    }
}

enum class Phase(val label: String) {
    MENSTRUATION("Menstruation"),
    FOLLICULAR("Follikelphase"),
    OVULATION("Ovulation"),
    LUTEAL("Lutealphase"),
}

/** Blutungsstärke; [level] 0..6 */
enum class Flow(val level: Int, val label: String) {
    NONE(0, "keine"),
    SPOTTING(1, "Schmierblutung"),
    LIGHT(2, "leicht"),
    MEDIUM(3, "mittel"),
    HEAVY(4, "stark"),
    VERY_HEAVY(5, "sehr stark"),
    INTERMENSTRUAL(6, "Zwischenblutung");

    companion object {
        fun of(level: Int): Flow = entries.firstOrNull { it.level == level } ?: NONE
    }
}

data class PeriodRecord(
    val id: Long = 0,
    val start: LocalDate,
    val end: LocalDate? = null,
    val approximate: Boolean = false,
    val backfilled: Boolean = false,
)

enum class ScheduleType { CYCLIC, CONTINUOUS }

data class MedicationPhase(
    val id: Long = 0,
    val name: String,
    val form: String,
    val dose: String,
    val schedule: ScheduleType,
    val cycleDayFrom: Int? = null,
    val cycleDayTo: Int? = null,
    val start: LocalDate,
    val end: LocalDate? = null,
    val reviewDate: LocalDate? = null,
    val note: String = "",
) {
    fun activeOn(date: LocalDate): Boolean = !date.isBefore(start) && (end == null || !date.isAfter(end))

    fun scheduleLabel(): String = when (schedule) {
        ScheduleType.CONTINUOUS -> "durchgehend"
        ScheduleType.CYCLIC -> "Zyklustag $cycleDayFrom–$cycleDayTo"
    }

    fun displayName(): String = listOf(name, form, dose).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Ein Begriff mit Intensität an einem Zeitpunkt – Grundeinheit für Statistik. */
data class TermOccurrence(
    val time: LocalDateTime,
    val term: String,
    val category: Category,
    val intensity: Int? = null,
    val valence: Valence? = null,
    val backfilled: Boolean = false,
) {
    val date: LocalDate get() = time.toLocalDate()
}

data class PanicRecord(
    val id: Long = 0,
    val time: LocalDateTime,
    val durationMinutes: Int?,
    val intensity: Int,
    val symptoms: List<String>,
    val triggers: List<String>,
    val helped: List<String>,
    val note: String = "",
    val backfilled: Boolean = false,
) {
    val date: LocalDate get() = time.toLocalDate()
}

data class GadRecord(val date: LocalDate, val score: Int)

/** Spezielle Begriffe, deren Intensität ein Skalenwert ist. */
object ScaleTerms {
    const val SLEEP_QUALITY = "Schlafqualität"
    const val ENERGY = "Energie"
}
