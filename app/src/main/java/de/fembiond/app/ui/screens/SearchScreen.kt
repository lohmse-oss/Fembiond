package de.fembiond.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.fembiond.app.Routes
import de.fembiond.app.data.AppData
import de.fembiond.app.domain.ScaleTerms
import de.fembiond.app.domain.TermParser
import de.fembiond.app.ui.ChipFlow
import de.fembiond.app.ui.HintText
import de.fembiond.app.ui.LevelBadge
import de.fembiond.app.ui.SectionCard
import de.fembiond.app.ui.hm
import de.fembiond.app.ui.monthYear
import de.fembiond.app.ui.weekdayLong
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Ein Treffer der Suche. */
data class SearchHit(val time: LocalDateTime, val kind: Kind, val title: String, val detail: String, val level: Int? = null) {
    enum class Kind { ENTRY, UNREST, EVENT }
    val date: LocalDate get() = time.toLocalDate()
}

/** Durchsucht Einträge, Notizen, Unruhe-Episoden und Ereignisse (Groß/Klein und Umlaute egal). */
fun search(data: AppData, query: String): List<SearchHit> {
    val q = TermParser.fold(query.trim())
    if (q.length < 2) return emptyList()
    fun m(vararg texts: String) = texts.any { TermParser.fold(it).contains(q) }
    val hits = mutableListOf<SearchHit>()
    data.entries.forEach { e ->
        val terms = e.terms.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
        if (m(e.note, *terms.map { it.term }.toTypedArray(), *terms.map { it.category.label }.toTypedArray())) {
            hits += SearchHit(e.time, SearchHit.Kind.ENTRY,
                terms.joinToString { t -> t.term + (t.intensity?.let { " ($it)" } ?: "") }.ifBlank { "Notiz" },
                e.note)
        }
    }
    data.episodes.forEach { p ->
        val event = p.eventId?.let { id -> data.events.firstOrNull { it.id == id } }
        if (m("unruhe", "panik".takeIf { p.isPanic } ?: "", p.note, event?.displayName ?: "", *p.symptoms.toTypedArray(), *p.triggers.toTypedArray(), *p.helped.toTypedArray())) {
            hits += SearchHit(p.time, SearchHit.Kind.UNREST,
                if (p.isPanic) "Panikattacke" else "Unruhe ${p.unrestLevel.label}",
                listOfNotNull(event?.let { "Bezug: ${it.displayName}" }, (p.symptoms + p.triggers).takeIf { it.isNotEmpty() }?.joinToString(), p.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                p.level)
        }
    }
    data.events.forEach { e ->
        if (m(e.type, e.title, e.note)) hits += SearchHit(e.date.atTime(12, 0), SearchHit.Kind.EVENT, e.displayName, listOf(e.type.takeIf { e.title.isNotBlank() }, e.outcome.label).filterNotNull().joinToString(" · "))
    }
    return hits.sortedByDescending { it.time }
}

@Composable
fun SearchScreen(data: AppData, nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hits = remember(data, query) { search(data, query) }
    val suggestions = remember(data) {
        data.occurrences.filter { it.term != ScaleTerms.SLEEP_QUALITY && it.term != ScaleTerms.ENERGY }
            .groupingBy { it.term }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
    }

    Screen(title = "Suchen", nav = nav) {
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("z. B. Migräne, Konzert, gereizt …") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Leeren") } },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        if (query.trim().length < 2) {
            if (suggestions.isNotEmpty()) {
                Text("Häufig gesucht", style = MaterialTheme.typography.labelLarge)
                ChipFlow { suggestions.forEach { s -> FilterChip(false, { query = s }, { Text(s) }) } }
            }
            HintText("Durchsucht Begriffe, Notizen, Unruhe-Episoden (Anzeichen, Auslöser, Hilfen) und Ereignisse.")
        } else if (hits.isEmpty()) {
            EmptyState(Icons.Rounded.SearchOff, "Nichts gefunden", "Zu „${query.trim()}“ gibt es keine Einträge. Versuch es mit einem anderen Begriff.")
        } else {
            HintText("${hits.size} Treffer · zuletzt am ${hits.first().date.weekdayLong()}")
            hits.groupBy { YearMonth.from(it.date) }.forEach { (month, list) ->
                SectionCard(title = month.atDay(1).monthYear()) {
                    list.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.day(h.date)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            when {
                                h.level != null -> LevelBadge(h.level, 24.dp)
                                h.kind == SearchHit.Kind.EVENT -> Icon(Icons.Rounded.Event, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.tertiary)
                                else -> Icon(Icons.Rounded.EditNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(h.date.weekdayLong() + if (h.kind != SearchHit.Kind.EVENT) " · ${h.time.hm()}" else "",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (h.detail.isNotBlank()) Text(h.detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
