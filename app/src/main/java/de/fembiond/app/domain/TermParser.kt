package de.fembiond.app.domain

import kotlin.math.min
import kotlin.math.pow

/** Eine erkennbare Formulierung, die auf einen Begriff zeigt. */
data class VocabItem(
    val phrase: String,
    val label: String,
    val category: Category,
    val valence: Valence? = null,
)

data class ParsedTerm(
    val label: String,
    val category: Category,
    val intensity: Int?,
    val valence: Valence?,
    val matchedText: String,
)

data class ParseResult(
    val terms: List<ParsedTerm>,
    /** Nicht erkannte Wörter/Wortgruppen, die als neue Begriffe angeboten werden. */
    val unknown: List<String>,
)

/**
 * Regelbasierte Begriffserkennung (Stufe 1): Wörterbuch, Synonyme, Tippfehlertoleranz,
 * Intensitätswörter und Verneinungen. Läuft vollständig lokal.
 */
class TermParser(vocabulary: List<VocabItem>) {

    private data class Phrase(val tokens: List<String>, val item: VocabItem)

    private val phrases: List<Phrase> = vocabulary
        .map { Phrase(tokenize(it.phrase).map(::fold), it) }
        .filter { it.tokens.isNotEmpty() }
        .distinctBy { it.tokens to it.item.label }
        .sortedByDescending { it.tokens.size }

    private val maxLen = phrases.maxOfOrNull { it.tokens.size } ?: 1

    fun parse(text: String, categories: Set<Category> = Category.entries.filter { it.isDaily }.toSet()): ParseResult {
        val words = mutableListOf<String>()
        val segOf = mutableListOf<Int>()
        var seg = 0
        Regex("[\\p{L}\\p{N}.]+|[,;!?\\n]").findAll(text).forEach { m ->
            val raw = m.value.trimEnd('.')
            val endsSentence = m.value.endsWith(".")
            if (raw.isEmpty() || raw.matches(Regex("[,;!?\\n]"))) { seg++; return@forEach }
            val lower = raw.lowercase()
            if (lower in SEGMENT_WORDS) { seg++; return@forEach }
            words += lower
            segOf += seg
            if (endsSentence) seg++
        }
        val folded = words.map(::fold)
        val consumed = BooleanArray(words.size)
        val usable = phrases.filter { it.item.category in categories }

        data class Hit(val start: Int, val end: Int, val item: VocabItem)
        val hits = mutableListOf<Hit>()

        var i = 0
        while (i < words.size) {
            val hit = findAt(folded, i, usable, exact = true) ?: findAt(folded, i, usable, exact = false)
            if (hit != null) {
                val (len, item) = hit
                for (k in i until i + len) consumed[k] = true
                hits += Hit(i, i + len, item)
                i += len
            } else {
                i++
            }
        }

        val result = mutableListOf<ParsedTerm>()
        for (h in hits) {
            val segment = segOf[h.start]
            if (isNegated(folded, segOf, consumed, h.start, segment)) continue
            val nextSegHasHits = hits.any { segOf[it.start] == segment + 1 }
            val intensity = modifierBefore(folded, segOf, h.start, segment)
                ?: explicitNumber(folded, segOf, segment)
                ?: if (!nextSegHasHits) explicitNumber(folded, segOf, segment + 1) else null
            val valence = if (h.item.category == Category.LIFE) {
                valenceHint(folded, segOf, segment) ?: h.item.valence
            } else null
            val text = words.subList(h.start, h.end).joinToString(" ")
            val existing = result.indexOfFirst { it.label == h.item.label && it.category == h.item.category }
            if (existing >= 0) {
                val e = result[existing]
                result[existing] = e.copy(intensity = maxOfNullable(e.intensity, intensity))
            } else {
                result += ParsedTerm(h.item.label, h.item.category, intensity, valence, text)
            }
        }

        val unknown = mutableListOf<String>()
        var run = mutableListOf<String>()
        var runSeg = -1
        fun flush() {
            if (run.isNotEmpty()) unknown += run.take(3).joinToString(" ")
            run = mutableListOf()
        }
        for (k in words.indices) {
            val w = folded[k]
            val isContent = !consumed[k] && w.length >= 4 && w !in STOP && w !in INTENSITY_WORDS && w !in NUMBER_WORDS &&
                w !in NEGATIONS && w.none { it.isDigit() }
            if (isContent && (run.isEmpty() || runSeg == segOf[k])) {
                run += words[k]; runSeg = segOf[k]
            } else {
                flush()
                if (isContent) { run += words[k]; runSeg = segOf[k] }
            }
        }
        flush()

        return ParseResult(result, unknown.distinct())
    }

    private fun findAt(folded: List<String>, i: Int, usable: List<Phrase>, exact: Boolean): Pair<Int, VocabItem>? {
        for (len in min(maxLen, folded.size - i) downTo 1) {
            for (p in usable) {
                if (p.tokens.size != len) continue
                var ok = true
                for (k in 0 until len) {
                    val w = folded[i + k]
                    val t = p.tokens[k]
                    val match = if (exact) w == t else fuzzyEquals(w, t)
                    if (!match) { ok = false; break }
                }
                if (ok) return len to p.item
            }
        }
        return null
    }

    private fun isNegated(folded: List<String>, segOf: List<Int>, consumed: BooleanArray, start: Int, seg: Int): Boolean {
        for (k in maxOf(0, start - 3) until start) {
            if (segOf[k] == seg && !consumed[k] && folded[k] in NEGATIONS) return true
        }
        return false
    }

    private fun modifierBefore(folded: List<String>, segOf: List<Int>, start: Int, seg: Int): Int? {
        var best: Int? = null
        for (k in maxOf(0, start - 3) until start) {
            if (segOf[k] != seg) continue
            val w = folded[k]
            val v = INTENSITY_WORDS[w] ?: INTENSITY_WORDS.entries.firstOrNull { (key, _) ->
                key.length >= 5 && w.startsWith(key) && w.length - key.length <= 2
            }?.value
            if (v != null) {
                // "sehr stark" → höchster Wert der Kombination
                best = if (best == null) v else minOf(5, maxOf(best, v) + if (w == "sehr" || folded.getOrNull(k - 1) == "sehr") 1 else 0)
            }
        }
        return best
    }

    private fun explicitNumber(folded: List<String>, segOf: List<Int>, seg: Int): Int? {
        val idx = folded.indices.filter { segOf[it] == seg }
        for ((n, k) in idx.withIndex()) {
            val w = folded[k]
            val value = NUMBER_WORDS[w] ?: w.toIntOrNull()
            if (value == null || value !in 1..5) continue
            val prev = idx.getOrNull(n - 1)?.let { folded[it] }
            val next = idx.getOrNull(n + 1)?.let { folded[it] }
            val next2 = idx.getOrNull(n + 2)?.let { folded[it] }
            if (prev in SCALE_WORDS || (next == "von" && (next2 == "5" || next2 == "funf"))) return value
        }
        return null
    }

    private fun valenceHint(folded: List<String>, segOf: List<Int>, seg: Int): Valence? {
        val words = folded.indices.filter { segOf[it] == seg }.map { folded[it] }
        return when {
            words.any { it in NEGATIVE_HINTS } -> Valence.BURDEN
            words.any { it in POSITIVE_HINTS } -> Valence.POSITIVE
            else -> null
        }
    }

    companion object {
        fun tokenize(s: String): List<String> =
            Regex("[\\p{L}\\p{N}]+").findAll(s.lowercase()).map { it.value }.toList()

        /** Kleinschreibung, Umlaute vereinfachen – macht Tippfehler und Spracheingabe robuster. */
        fun fold(s: String): String = s.lowercase()
            .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")

        private val INFLECTIONS = listOf("e", "r", "s", "n", "m", "en", "er", "es", "em", "st", "te", "ten")

        fun fuzzyEquals(word: String, target: String): Boolean {
            if (word == target) return true
            if (target.length >= 4 && word.startsWith(target) && word.removePrefix(target) in INFLECTIONS) return true
            if (word.length >= 4 && target.startsWith(word) && target.removePrefix(word) in INFLECTIONS) return true
            val allowed = when {
                target.length <= 4 -> 0
                target.length <= 8 -> 1
                else -> 2
            }
            if (allowed == 0 || kotlin.math.abs(word.length - target.length) > allowed) return false
            return damerauLevenshtein(word, target) <= allowed
        }

        fun damerauLevenshtein(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
            return d[a.length][b.length]
        }

        private fun maxOfNullable(a: Int?, b: Int?): Int? = when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }

        val SEGMENT_WORDS = setOf("und", "aber", "sowie", "dann", "außerdem", "ausserdem", "danach", "später", "trotzdem")
        val NEGATIONS = setOf("kein", "keine", "keinen", "keiner", "keinem", "nicht", "ohne", "nie")
        val SCALE_WORDS = setOf("starke", "stufe", "level", "intensitat", "wert", "grad")
        val NUMBER_WORDS = mapOf("eins" to 1, "ein" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "funf" to 5)
        val INTENSITY_WORDS: Map<String, Int> = mapOf(
            "kaum" to 1, "minimal" to 1,
            "leicht" to 2, "leichte" to 2, "leichter" to 2, "etwas" to 2, "bisschen" to 2, "wenig" to 2, "schwach" to 2,
            "mittel" to 3, "massig" to 3, "einigermassen" to 3, "spurbar" to 3,
            "ziemlich" to 4, "stark" to 4, "starke" to 4, "starker" to 4, "starken" to 4, "richtig" to 4, "sehr" to 4, "deutlich" to 4, "ordentlich" to 4,
            "extrem" to 5, "total" to 5, "unertraglich" to 5, "heftig" to 5, "heftige" to 5, "massiv" to 5, "massive" to 5, "brutal" to 5, "krass" to 5,
        )
        val NEGATIVE_HINTS = setOf("stressig", "schlimm", "blod", "anstrengend", "belastend", "nervig", "furchtbar", "schrecklich", "traurig", "doof", "mies")
        val POSITIVE_HINTS = setOf("schon", "toll", "super", "klasse", "wunderbar", "herrlich", "entspannt", "gut", "lustig")
        val STOP: Set<String> = setOf(
            "heute", "gestern", "morgen", "abend", "abends", "mittag", "mittags", "nachmittag", "vormittag", "nacht", "nachts", "fruh",
            "habe", "hatte", "haben", "hab", "bin", "war", "waren", "sein", "ist", "sind", "wird", "wurde", "worden", "fuhle", "fuhlte",
            "gefuhlt", "mich", "mir", "mein", "meine", "meinen", "meiner", "meinem", "sich", "ganz", "auch", "noch", "schon", "immer",
            "wieder", "einen", "einem", "einer", "eine", "dass", "weil", "wenn", "seit", "fast", "mehr", "viel", "viele", "sehr",
            "irgendwie", "eigentlich", "einfach", "gerade", "jetzt", "also", "dann", "denn", "doch", "halt", "eher", "bzw", "oder",
            "nach", "beim", "über", "uber", "unter", "durch", "gegen", "zwischen", "wegen", "dabei", "dazu", "davon", "darauf",
            "diese", "dieser", "dieses", "diesem", "welche", "etwa", "gegen", "ungefahr", "wirklich", "tag", "tage", "tagen", "woche",
            "stunde", "stunden", "minuten", "zeit", "ganzen", "ganze", "insgesamt", "allem", "alles", "nichts", "etwas", "gemacht",
            "machen", "gehabt", "gewesen", "geht", "ging", "gegangen", "kann", "konnte", "muss", "musste", "soll", "will", "wollte",
            "bisschen", "starke", "stufe", "level", "intensitat", "gefuhl", "zustand", "irgendwas", "jemand", "leute", "beiden",
            "danach", "vorher", "spater", "kurz", "lang", "lange", "gleich", "endlich", "zusammen", "allein", "alleine", "sowas",
        ).map(::fold).toSet()
    }
}

/** Sortierung der Vorschläge: Häufigkeit mit zeitlichem Abklingen (Halbwertszeit 30 Tage). */
object TermRanking {
    fun score(useCount: Int, daysSinceLastUse: Long?, builtIn: Boolean): Double {
        val decay = if (daysSinceLastUse == null) 0.0 else 0.5.pow(daysSinceLastUse / 30.0)
        return useCount * (0.3 + 0.7 * decay) + if (builtIn) 0.1 else 0.0
    }
}
