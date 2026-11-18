package de.fembiond.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val DE: Locale = Locale.GERMANY
private val dayMonth = DateTimeFormatter.ofPattern("d. MMM", DE)
private val full = DateTimeFormatter.ofPattern("d. MMMM yyyy", DE)
private val short = DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)
private val weekday = DateTimeFormatter.ofPattern("EEEE, d. MMMM", DE)
private val time = DateTimeFormatter.ofPattern("HH:mm", DE)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", DE)

fun LocalDate.dm(): String = format(dayMonth)
fun LocalDate.full(): String = format(full)
fun LocalDate.short(): String = format(short)
fun LocalDate.weekdayLong(): String = format(weekday)
fun LocalDate.monthYear(): String = format(monthYear)
fun LocalDateTime.hm(): String = format(time)

fun relativeDays(today: LocalDate, date: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, date)
    return when {
        d == 0L -> "heute"
        d == 1L -> "morgen"
        d == -1L -> "gestern"
        d > 0 -> "in $d Tagen"
        else -> "vor ${-d} Tagen"
    }
}

fun Double.fmt1(): String = String.format(DE, "%.1f", this)
