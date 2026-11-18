package de.fembiond.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    @Test fun roundTrip() {
        val enc = Backup.encrypt("{\"a\":1}", "geheim".toCharArray())
        assertEquals("{\"a\":1}", Backup.decrypt(enc, "geheim".toCharArray()))
    }
    @Test fun wrongPasswordFails() {
        val enc = Backup.encrypt("x", "a".toCharArray())
        assertTrue(runCatching { Backup.decrypt(enc, "b".toCharArray()) }.isFailure)
    }
}
