package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermParserTest {

    private val vocab = Lexicon.entries.flatMap { e ->
        (listOf(e.label) + e.synonyms).map { VocabItem(it, e.label, e.category, e.valence) }
    }
    private val parser = TermParser(vocab)

    private fun ParseResult.find(label: String) = terms.firstOrNull { it.label == label }

    @Test
    fun spokenSentenceIsRecognized() {
        val r = parser.parse("Bin heute ziemlich müde, hab seit Mittag Kopfweh und so eine innere Unruhe, so Stärke drei.")
        assertEquals(4, r.find("müde")?.intensity)
        assertEquals(Category.BODY, r.find("müde")?.category)
        assertEquals(Category.BODY, r.find("Kopfschmerzen")?.category)
        assertEquals(3, r.find("innere Unruhe")?.intensity)
        assertEquals(Category.MIND, r.find("innere Unruhe")?.category)
    }

    @Test
    fun negationIsRespected() {
        val r = parser.parse("Heute keine Kopfschmerzen, aber gereizt")
        assertTrue(r.find("Kopfschmerzen") == null)
        assertTrue(r.find("gereizt") != null)
    }

    @Test
    fun typosAndInflectionsAreTolerated() {
        val r = parser.parse("Krämpfe, gereizter als sonst, Hitzewalung in der Nacht")
        assertTrue(r.find("Krämpfe") != null)
        assertTrue(r.find("gereizt") != null)
        assertTrue(r.find("Hitzewallung") != null)
    }

    @Test
    fun longerPhrasesWin() {
        val r = parser.parse("nicht gut geschlafen")
        assertTrue(r.find("schlecht geschlafen") != null)
        assertTrue(r.find("gut geschlafen") == null)
    }

    @Test
    fun lifeEventsWithValence() {
        val r = parser.parse("Heute war mein letzter Arbeitstag, abends Streit mit meiner Schwester, kaum geschlafen.")
        assertEquals(Category.LIFE, r.find("letzter Arbeitstag")?.category)
        assertEquals(Valence.BURDEN, r.find("Streit")?.valence)
        assertTrue(r.find("schlecht geschlafen") != null)
    }

    @Test
    fun unknownWordsAreOffered() {
        val r = parser.parse("Heute Zahnfleischbluten und gereizt")
        assertTrue(r.unknown.contains("zahnfleischbluten"))
        assertFalse(r.unknown.contains("heute"))
    }

    @Test
    fun weinenIsNotAlcohol() {
        val r = parser.parse("musste weinen")
        assertTrue(r.find("Alkohol") == null)
    }

    @Test
    fun intensityModifiers() {
        val r = parser.parse("leichte Krämpfe; extrem erschöpft")
        assertEquals(2, r.find("Krämpfe")?.intensity)
        assertEquals(5, r.find("erschöpft")?.intensity)
    }

    @Test
    fun learnedSynonymIsUsed() {
        val p = TermParser(vocab + VocabItem("matschig im kopf", "konzentrationsschwach", Category.MIND))
        assertTrue(p.parse("total matschig im Kopf").find("konzentrationsschwach") != null)
    }

    @Test
    fun rankingPrefersRecentFrequentTerms() {
        assertTrue(TermRanking.score(10, 1, false) > TermRanking.score(10, 120, false))
        assertTrue(TermRanking.score(5, 1, false) > TermRanking.score(0, null, true))
    }
}
