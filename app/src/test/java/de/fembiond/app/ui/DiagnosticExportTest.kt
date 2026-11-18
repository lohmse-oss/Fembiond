package de.fembiond.app.ui

import de.fembiond.app.export.DiagnosticExport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportTest {
    @Test
    fun exportIsValidJsonWithoutPersonalData() {
        val data = SampleData.build()
        val text = DiagnosticExport.build(data, includeNotes = false)
        val root = Json.parseToJsonElement(text).jsonObject
        assertTrue(root["input_periods"]!!.jsonArray.isNotEmpty())
        val computed = root["computed"]!!.jsonObject
        assertTrue(computed["drsp_pairs"]!!.jsonArray.isNotEmpty())
        assertTrue(computed["tracked_days"]!!.jsonArray.isNotEmpty())
        assertFalse(text.contains(data.profile!!.name))
        assertFalse(text.contains("Schlecht geschlafen, aber okay"))
        assertTrue(DiagnosticExport.build(data, includeNotes = true).contains("Schlecht geschlafen, aber okay"))
    }
}
