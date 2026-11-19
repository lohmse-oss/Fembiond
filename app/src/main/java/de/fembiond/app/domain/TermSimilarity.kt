package de.fembiond.app.domain

/** Ein vorhandener Begriff, der einer Eingabe ähnelt. */
data class SimilarTerm(val label: String, val category: Category, val score: Double, val reason: String)

/**
 * Prüft auf Doppelte: Tippfehler, Wortformen („Beinschmerz“/„Beinschmerzen“), Zusammensetzungen
 * („Schmerzen im Bein“/„Beinschmerzen“) und gelernte Synonyme.
 */
object TermSimilarity {

    private val FILLER = setOf("im", "in", "am", "an", "der", "die", "das", "den", "dem", "des", "und", "mit", "beim", "vom", "von", "zu", "zum", "zur", "ein", "eine")
    private val ENDINGS = listOf("ungen", "ung", "en", "er", "es", "em", "e", "n", "s")

    fun normalize(s: String): String = TermParser.fold(s).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun stem(w: String): String {
        if (w.length < 5) return w
        val e = ENDINGS.firstOrNull { w.endsWith(it) && w.length - it.length >= 4 } ?: return w
        return w.dropLast(e.length)
    }

    private fun tokens(s: String) = normalize(s).split(" ").filter { it.isNotBlank() && it !in FILLER }.map(::stem)

    /** Ähnlichkeit 0–1 mit kurzer Begründung, oder null. */
    fun compare(a: String, b: String): Pair<Double, String>? {
        val na = normalize(a); val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return null
        if (na == nb) return 1.0 to "gleicher Begriff"
        val ta = tokens(a); val tb = tokens(b)
        val ja = ta.joinToString(""); val jb = tb.joinToString("")
        if (ja == jb) return 0.95 to "andere Wortform"
        if (ta.toSet() == tb.toSet()) return 0.9 to "gleiche Wörter"
        // Zusammensetzungen: alle Wortteile des einen stecken im anderen („schmerz bein“ ↔ „beinschmerz“)
        val single = listOf(ta, tb).firstOrNull { it.size == 1 }?.first()
        val multi = listOf(ta, tb).firstOrNull { it.size >= 2 }
        if (single != null && multi != null && multi.all { it.length >= 3 && single.contains(it) } && multi.sumOf { it.length } >= single.length * 0.7) {
            return 0.85 to "gleiche Wortteile"
        }
        val allowed = when { minOf(ja.length, jb.length) <= 4 -> 0; minOf(ja.length, jb.length) <= 8 -> 1; else -> 2 }
        if (allowed > 0 && kotlin.math.abs(ja.length - jb.length) <= allowed && TermParser.damerauLevenshtein(ja, jb) <= allowed) return 0.8 to "fast gleich geschrieben"
        if (minOf(ja.length, jb.length) >= 5 && (ja.startsWith(jb) || jb.startsWith(ja))) return 0.7 to "gleicher Wortanfang"
        return null
    }

    /**
     * Vorhandene Begriffe, die [input] ähneln – inklusive gelernter Synonyme ([synonyms]: Phrase → Begriff).
     */
    fun similar(
        input: String,
        terms: List<Pair<String, Category>>,
        synonyms: List<Triple<String, String, Category>> = emptyList(),
        max: Int = 5,
    ): List<SimilarTerm> {
        if (normalize(input).length < 3) return emptyList()
        val direct = terms.mapNotNull { (l, c) -> compare(input, l)?.let { SimilarTerm(l, c, it.first, it.second) } }
        val viaSyn = synonyms.mapNotNull { (phrase, l, c) -> compare(input, phrase)?.let { SimilarTerm(l, c, it.first - 0.05, "bekanntes anderes Wort „$phrase“") } }
        return (direct + viaSyn).groupBy { it.label to it.category }.map { (_, l) -> l.maxBy { it.score } }
            .sortedByDescending { it.score }.take(max)
    }

    /** Mögliche Doppelte innerhalb einer Liste (je Paar einmal). */
    fun duplicates(terms: List<Pair<String, Category>>, minScore: Double = 0.8): List<Pair<Pair<String, Category>, Pair<String, Category>>> {
        val out = mutableListOf<Pair<Pair<String, Category>, Pair<String, Category>>>()
        for (i in terms.indices) for (j in i + 1 until terms.size) {
            val s = compare(terms[i].first, terms[j].first) ?: continue
            if (s.first >= minScore) out += terms[i] to terms[j]
        }
        return out
    }
}
