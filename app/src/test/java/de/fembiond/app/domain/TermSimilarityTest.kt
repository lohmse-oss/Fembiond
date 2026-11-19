package de.fembiond.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermSimilarityTest {
    @Test fun wordForms() = assertNotNull(TermSimilarity.compare("Beinschmerz", "Beinschmerzen"))
    @Test fun compound() = assertNotNull(TermSimilarity.compare("Schmerzen im Bein", "Beinschmerzen"))
    @Test fun typo() = assertNotNull(TermSimilarity.compare("Kopfschmrzen", "Kopfschmerzen"))
    @Test fun differentPain() = assertNull(TermSimilarity.compare("Kopfschmerzen", "Rückenschmerzen"))
    @Test fun bleedingLevels() = assertNull(TermSimilarity.compare("Blutung stark", "Blutung sehr stark"))
    @Test fun unrelated() = assertNull(TermSimilarity.compare("müde", "gereizt"))

    @Test fun findsViaSynonym() {
        val r = TermSimilarity.similar("Kopfweh", listOf("Kopfschmerzen" to Category.BODY), listOf(Triple("kopfweh", "Kopfschmerzen", Category.BODY)))
        assertEquals("Kopfschmerzen", r.first().label)
    }

    @Test fun lexiconHasNoDuplicates() {
        val terms = Lexicon.entries.filter { it.category.isSymptom }.map { it.label to it.category }
        assertTrue(TermSimilarity.duplicates(terms).toString(), TermSimilarity.duplicates(terms).isEmpty())
    }
}
