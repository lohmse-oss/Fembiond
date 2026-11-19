package de.fembiond.app.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Update von v0.1.0 (DB 1) auf v0.2.0 (DB 2): bestehende Panikattacken bleiben als Stufe 5 erhalten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FembiondDatabase::class.java, listOf(Migration4To5()))

    @Test
    fun migrate1To2KeepsData() {
        helper.createDatabase("test.db", 1).apply {
            execSQL("INSERT INTO panics (id, day, minute, durationMinutes, intensity, note, backfilled) VALUES (1, 20000, 600, 10, 4, 'alt', 0)")
            execSQL("INSERT INTO periods (id, start, `end`, approximate, backfilled) VALUES (1, 19990, 19994, 0, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("test.db", 2, true)
        db.query("SELECT level, eventId, note FROM panics WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(5, c.getInt(0))
            assertTrue(c.isNull(1))
            assertEquals("alt", c.getString(2))
        }
        db.query("SELECT COUNT(*) FROM periods").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.execSQL("INSERT INTO events (day, type, title, note, outcome) VALUES (20010, 'Konzert', '', '', 'PLANNED')")
        db.close()
        val v3 = helper.runMigrationsAndValidate("test.db", 3, true)
        v3.query("SELECT COUNT(*) FROM events").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        v3.execSQL("INSERT INTO mrs (day, answers, total) VALUES (20020, '1,1,1,1,1,1,1,1,1,1,1', 11)")
        v3.execSQL("INSERT INTO profile (id, name, birthDate) VALUES (1, 'Test', 6000)")
        v3.execSQL("INSERT INTO terms (id, label, category, valence, builtIn, useCount, lastUsed, hidden) VALUES (1, 'Beinschmerzen', 'BODY', NULL, 0, 1, 0, 0)")
        v3.execSQL("INSERT INTO entries (id, day, minute, note, source, backfilled) VALUES (1, 20030, 600, '', 'text', 0)")
        v3.execSQL("INSERT INTO entry_terms (entryId, termId, intensity, valence) VALUES (1, 1, 3, NULL)")
        v3.close()
        val v4 = helper.runMigrationsAndValidate("test.db", 4, true)
        v4.query("SELECT intensity, sudden FROM entry_terms").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0)); assertEquals(0, c.getInt(1))
        }
        // Frühere Kategorien: Zwilling in BODY vorhanden (Hitzewallung) bzw. nicht (Tinnitus)
        v4.execSQL("INSERT INTO terms (id, label, category, valence, builtIn, useCount, lastUsed, hidden) VALUES (2, 'Hitzewallung', 'PERIMENOPAUSE', NULL, 1, 3, 0, 0)")
        v4.execSQL("INSERT INTO terms (id, label, category, valence, builtIn, useCount, lastUsed, hidden) VALUES (3, 'Hitzewallung', 'BODY', NULL, 1, 0, 0, 0)")
        v4.execSQL("INSERT INTO terms (id, label, category, valence, builtIn, useCount, lastUsed, hidden) VALUES (4, 'Tinnitus', 'PERIMENOPAUSE', NULL, 1, 1, 0, 0)")
        v4.execSQL("INSERT INTO entry_terms (entryId, termId, intensity, valence, sudden) VALUES (1, 2, 4, NULL, 0)")
        v4.execSQL("INSERT INTO synonyms (phrase, termId) VALUES ('wallung', 2)")
        v4.close()
        val v5 = helper.runMigrationsAndValidate("test.db", 5, true)
        v5.query("SELECT COUNT(*) FROM terms WHERE category IN ('PERIMENOPAUSE','SLEEP_ENERGY','BLEEDING')").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        v5.query("SELECT COUNT(*) FROM entry_terms WHERE termId = 3").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        v5.query("SELECT termId FROM synonyms WHERE phrase = 'wallung'").use { c -> c.moveToFirst(); assertEquals(3, c.getInt(0)) }
        v5.query("SELECT category FROM terms WHERE id = 4").use { c -> c.moveToFirst(); assertEquals("BODY", c.getString(0)) }
    }
}
