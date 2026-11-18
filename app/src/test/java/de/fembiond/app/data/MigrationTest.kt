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
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FembiondDatabase::class.java)

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
    }
}
