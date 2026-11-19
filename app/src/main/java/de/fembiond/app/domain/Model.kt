package de.fembiond.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Zahl mit einer Nachkommastelle, deutsches Format (Komma). */
fun de1(v: Double?): String = String.format(Locale.GERMANY, "%.1f", v ?: 0.0)

/** Kategorien für Begriffe. [label] ist der Anzeigename. */
enum class Category(val label: String, val shortLabel: String) {
    MIND("Psyche", "Psyche"),
    BODY("Körper", "Körper"),
    LIFE("Lebensumstände", "Leben"),
    MEDICATION_EFFECT("Medikation & Nebenwirkungen", "Medikation"),
    PANIC_SYMPTOM("Unruhe: Körperliche Anzeichen", "Anzeichen"),
    PANIC_TRIGGER("Unruhe: Auslöser", "Auslöser"),
    PANIC_HELP("Unruhe: Was geholfen hat", "Hilfe"),
    EVENT_TYPE("Ereignisart", "Ereignis");

    /** Kategorien, die im normalen Tageseintrag erscheinen. */
    val isDaily: Boolean get() = this !in setOf(PANIC_SYMPTOM, PANIC_TRIGGER, PANIC_HELP, EVENT_TYPE)

    /** Beschwerden (Psyche/Körper) – Einordnung nach PMS, Periode, Perimenopause erst in der Auswertung. */
    val isSymptom: Boolean get() = this == MIND || this == BODY

    companion object {
        /** Frühere Kategorien (bis 0.6), die heute zu „Körper“ gehören. */
        val LEGACY_BODY = setOf("PERIMENOPAUSE", "SLEEP_ENERGY", "BLEEDING")

        /** Liest gespeicherte Namen – auch aus älteren Versionen und Sicherungen. */
        fun parse(name: String): Category? =
            if (name in LEGACY_BODY) BODY else entries.firstOrNull { it.name == name }
    }
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

/** Stufen der Unruhe – die höchste Stufe ist die Panikattacke. */
enum class UnrestLevel(val level: Int, val label: String, val description: String) {
    MILD(1, "leicht", "innerlich etwas angespannt, gut auszuhalten"),
    NOTICEABLE(2, "spürbar", "Gedanken kreisen, Ablenkung fällt schwer"),
    STRONG(3, "stark", "körperliche Anzeichen wie Herzklopfen, Zittern, Magen"),
    SEVERE(4, "sehr stark", "kurz vor der Panik, Drang abzusagen oder zu flüchten"),
    PANIC(5, "Panikattacke", "Kontrollverlust, Herzrasen, Atemnot");

    companion object {
        fun of(level: Int): UnrestLevel = entries.firstOrNull { it.level == level } ?: STRONG
    }
}

/** Eine Unruhe-Episode (Stufe 1–5, Stufe 5 = Panikattacke), optional mit Bezug zu einem Ereignis. */
data class Episode(
    val id: Long = 0,
    val time: LocalDateTime,
    val durationMinutes: Int?,
    val level: Int,
    val symptoms: List<String>,
    val triggers: List<String>,
    val helped: List<String>,
    val note: String = "",
    val backfilled: Boolean = false,
    val eventId: Long? = null,
) {
    val date: LocalDate get() = time.toLocalDate()
    val isPanic: Boolean get() = level >= 5
    val unrestLevel: UnrestLevel get() = UnrestLevel.of(level)
}

enum class EventOutcome(val label: String) {
    PLANNED("geplant"),
    ATTENDED("war dort"),
    LEFT_EARLY("früher gegangen"),
    CANCELLED("abgesagt");
}

/** Ein anstehendes oder vergangenes Ereignis (Feier, Essen gehen, Konzert …). */
data class PlannedEvent(
    val id: Long = 0,
    val date: LocalDate,
    val type: String,
    val title: String = "",
    val note: String = "",
    val outcome: EventOutcome = EventOutcome.PLANNED,
) {
    val displayName: String get() = title.ifBlank { type }
}

data class GadRecord(val date: LocalDate, val score: Int)

/** Spezielle Begriffe, deren Intensität ein Skalenwert ist. */
object ScaleTerms {
    const val SLEEP_QUALITY = "Schlafqualität"
    const val ENERGY = "Energie"
}
