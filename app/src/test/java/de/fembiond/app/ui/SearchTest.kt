package de.fembiond.app.ui

import de.fembiond.app.ui.screens.SearchHit
import de.fembiond.app.ui.screens.search
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val data = SampleData.build()

    @Test fun findsTermsUmlautInsensitive() {
        val hits = search(data, "kramp")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.kind == SearchHit.Kind.ENTRY })
    }

    @Test fun findsUnrestAndEvents() {
        assertTrue(search(data, "Herzklopfen").any { it.kind == SearchHit.Kind.UNREST })
        assertTrue(search(data, "konzert").any { it.kind == SearchHit.Kind.EVENT })
    }

    @Test fun shortQueryGivesNothing() { assertTrue(search(data, "a").isEmpty()) }
}
