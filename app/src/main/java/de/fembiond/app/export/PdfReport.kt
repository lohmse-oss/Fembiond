package de.fembiond.app.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.fembiond.app.domain.AnalysisInput
import de.fembiond.app.domain.AnalysisResult
import de.fembiond.app.domain.Phase
import de.fembiond.app.domain.de1
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Arztbericht als PDF (A4), komplett lokal erzeugt. */
class PdfReport(private val input: AnalysisInput, private val r: AnalysisResult, private val age: Int?, private val mrs: List<de.fembiond.app.domain.MrsRecord> = emptyList()) {

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private var pageNo = 0
    private val w = 595f
    private val h = 842f
    private val margin = 44f
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val berry = Color.rgb(160, 41, 91)
    private val plum = Color.rgb(123, 63, 115)
    private val apricot = Color.rgb(217, 120, 58)
    private val lavender = Color.rgb(126, 107, 192)
    private val grey = Color.rgb(81, 67, 71)

    private val title = Paint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = berry; isAntiAlias = true }
    private val h2 = Paint().apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK; isAntiAlias = true }
    private val body = Paint().apply { textSize = 9.5f; color = Color.BLACK; isAntiAlias = true }
    private val small = Paint().apply { textSize = 8f; color = grey; isAntiAlias = true }
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    private fun phaseColor(p: Phase) = when (p) {
        Phase.MENSTRUATION -> Color.rgb(194, 24, 91)
        Phase.FOLLICULAR -> apricot
        Phase.OVULATION -> lavender
        Phase.LUTEAL -> plum
    }

    private fun newPage() {
        page?.let { finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), pageNo).create())
        y = margin
    }

    private fun finishPage(p: PdfDocument.Page) {
        p.canvas.drawText("Fembiond · Selbst erfasste Daten, keine Diagnose · Seite $pageNo", margin, h - 24f, small)
        doc.finishPage(p)
    }

    private fun ensure(space: Float) { if (y + space > h - margin - 20) newPage() }
    private val c get() = page!!.canvas

    private fun heading(t: String) { ensure(40f); y += 10f; c.drawText(t, margin, y + 12f, h2); y += 22f }

    private fun text(t: String, paint: Paint = body, indent: Float = 0f) {
        val maxW = w - 2 * margin - indent
        val words = t.split(" ")
        var line = ""
        for (word in words) {
            val cand = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(cand) > maxW) { ensure(14f); c.drawText(line, margin + indent, y + 10f, paint); y += 13f; line = word } else line = cand
        }
        if (line.isNotEmpty()) { ensure(14f); c.drawText(line, margin + indent, y + 10f, paint); y += 13f }
    }

    fun write(out: OutputStream) {
        newPage()
        c.drawText("Zyklus- & Befindlichkeitsbericht", margin, y + 18f, title); y += 30f
        text("Zeitraum ${input.from.format(fmt)} – ${input.to.format(fmt)}" + (age?.let { " · Alter $it Jahre" } ?: "") + " · erstellt am ${LocalDate.now().format(fmt)}", small)
        text("${r.trackedDays} Tage mit Einträgen · ${r.unrestTotal} Unruhe-Episoden, davon ${r.panicTotal} Panikattacken", small)

        val s = input.summary
        heading("Zyklus")
        text("Üblicher Zyklus ${s.baselineLength} Tage (Schwankung ± ${de1(s.baselineVariability)}), Periode ca. ${s.baselinePeriodLength} Tage, ${s.naturalCompleteCount} vollständige Zyklen ohne Medikation.")
        text("Einordnung nach STRAW+10: ${s.straw.label}. ${s.straw.description}")
        s.current?.let { text("Aktuell Zyklustag ${it.cycleDay} (${it.phase.label}). Nächste Periode erwartet ${it.prediction.from.format(fmt)} – ${it.prediction.to.format(fmt)}" + if (it.prediction.withdrawalBleed) " (Abbruchblutung nach Schema)." else ".") }
        val bars = r.cycles.bars.takeLast(14)
        if (bars.isNotEmpty()) {
            y += 4f
            val maxLen = (bars.mapNotNull { it.length }.maxOrNull() ?: 35).coerceAtLeast(35)
            val barW = w - 2 * margin - 150f
            bars.forEach { b ->
                ensure(14f)
                c.drawText(b.start.format(fmt), margin, y + 9f, small)
                val len = b.length ?: 0
                val x0 = margin + 60f
                fill.color = Color.rgb(194, 24, 91)
                c.drawRect(x0, y + 2f, x0 + barW * b.periodLength / maxLen, y + 10f, fill)
                fill.color = Color.argb(160, 123, 63, 115)
                if (len > b.periodLength) c.drawRect(x0 + barW * b.periodLength / maxLen, y + 2f, x0 + barW * len / maxLen, y + 10f, fill)
                val tag = listOfNotNull(b.length?.let { "$it T." } ?: "läuft", "Medikation".takeIf { b.medicated }, "Abweichung".takeIf { b.deviation != null }, "ca.".takeIf { b.approximate })
                c.drawText(tag.joinToString(" · "), x0 + barW + 6f, y + 9f, small)
                y += 13f
            }
        }

        if (input.medications.isNotEmpty()) {
            heading("Medikation")
            input.medications.forEach { m ->
                text("• ${m.displayName()} – ${m.scheduleLabel()}, ${m.start.format(fmt)} bis ${m.end?.format(fmt) ?: "laufend"}" + (m.reviewDate?.let { ", Neubefund ${it.format(fmt)}" } ?: ""))
            }
            if (r.compare.size >= 2) {
                y += 4f
                r.compare.forEach { cmp ->
                    text("${cmp.label}: " + listOfNotNull(
                        "${cmp.trackedDays} Tage",
                        cmp.unrestPer30?.let { "Unruhe ${de1(it)}/30 Tage" },
                        cmp.meanUnrestLevel?.let { "Ø Stufe ${de1(it)}" },
                        cmp.panicPer30?.let { "Panik ${de1(it)}/30 Tage" },
                        cmp.meanSleep?.let { "Schlaf Ø ${de1(it)}/5" },
                        cmp.meanEnergy?.let { "Energie Ø ${de1(it)}/5" },
                        cmp.medianCycle?.let { "Zyklus ${it.roundToInt()} T." },
                    ).joinToString(", "), indent = 8f)
                }
            }
        }

        val hot = input.occurrences.filter { it.term in de.fembiond.app.domain.PeriStats.HOT_TERMS && !it.date.isBefore(input.from) && !it.date.isAfter(input.to) }
        if (hot.isNotEmpty() || mrs.isNotEmpty()) {
            heading("Perimenopause")
            if (hot.isNotEmpty()) text("Hitzewallungen/Nachtschweiß im Zeitraum: ${hot.size}× an ${hot.map { it.date }.toSet().size} Tagen.")
            mrs.sortedBy { it.date }.takeLast(6).forEach { m ->
                text("MRS ${m.date.format(fmt)}: ${m.total}/44 (körperlich ${m.somatic}, psychisch ${m.psychological}, urogenital ${m.urogenital}) – ${de.fembiond.app.domain.Mrs.severity(m.total)}")
            }
        }

        val pms = de.fembiond.app.domain.Pms.analyze(input.summary, input.occurrences, input.episodes, input.to, input.from, input.to)
        heading("PMS-Einschätzung (ACOG-Kriterien)")
        text(pms.verdict)
        pms.pmddHint?.let { text(it) }
        pms.cycles.filter { it.assessable }.takeLast(6).forEach { c ->
            text("• Periode ab ${c.periodStart.format(fmt)}: Stimmung ${c.affective.joinToString().ifEmpty { "–" }}; Körper ${c.somatic.joinToString().ifEmpty { "–" }}" + if (c.pmsTypical) " → PMS-typisch" else "", small)
        }

        heading("Wichtigste Beobachtungen")
        if (r.insights.isEmpty()) text("Noch zu wenige Daten für automatische Aussagen.")
        r.insights.forEach { text("• $it") }

        heading("Unruhe nach Zyklusphase")
        if (r.unrestTotal == 0) text("Keine Unruhe-Episoden im Zeitraum.") else {
            text("Stufen: 1 leicht · 2 spürbar · 3 stark · 4 sehr stark · 5 Panikattacke. Verteilung: " +
                r.levelCounts.mapIndexed { i, n -> "Stufe ${i + 1}: $n" }.joinToString(", "), small)
            val max = r.unrestByPhase.maxOf { it.count }.coerceAtLeast(1)
            r.unrestByPhase.forEach { p ->
                ensure(16f)
                c.drawText(p.phase.label, margin, y + 10f, body)
                fill.color = phaseColor(p.phase)
                c.drawRect(margin + 100f, y + 2f, margin + 100f + 200f * p.count / max, y + 11f, fill)
                c.drawText("${p.count}" + (p.meanLevel?.let { " · Ø Stufe ${de1(it)}" } ?: "") +
                    (if (p.panicCount > 0) " · ${p.panicCount}× Panik" else "") +
                    (p.relativeFrequency?.let { " · ${de1(it)}×" } ?: ""), margin + 310f, y + 10f, small)
                y += 15f
            }
            val symptoms = input.episodes.filter { !it.date.isBefore(input.from) && !it.date.isAfter(input.to) }.flatMap { it.symptoms }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
            if (symptoms.isNotEmpty()) text("Häufigste körperliche Anzeichen: " + symptoms.joinToString { "${it.key} (${it.value})" }, small)
            val helped = input.episodes.filter { !it.date.isBefore(input.from) && !it.date.isAfter(input.to) }.flatMap { it.helped }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
            if (helped.isNotEmpty()) text("Was geholfen hat: " + helped.joinToString { "${it.key} (${it.value})" }, small)
        }

        val ant = r.anticipation.filter { it.events > 0 }
        if (ant.isNotEmpty()) {
            heading("Unruhe rund um Ereignisse")
            text("Ø höchste Unruhe-Stufe je Tag relativ zum Ereignis (0 = Ereignistag), über ${ant.maxOf { it.events }} Ereignisse.", small)
            val bw = (w - 2 * margin) / ant.size
            ensure(70f)
            ant.forEachIndexed { i, a ->
                val v = (a.meanMaxLevel ?: 0.0) / 5.0
                fill.color = if (a.offset == 0) berry else Color.argb(170, 123, 63, 115)
                c.drawRect(margin + i * bw + 4, y + 50f - (50f * v).toFloat(), margin + (i + 1) * bw - 4, y + 50f, fill)
                c.drawText(if (a.offset == 0) "Tag 0" else "%+d".format(a.offset), margin + i * bw + 6, y + 62f, small)
            }
            y += 70f
            r.eventTypes.take(8).forEach { t ->
                text("• ${t.type}: ${t.events} Ereignisse, Unruhe vorher in ${(t.shareWithUnrest * 100).roundToInt()} %, Ø Stufe ${de1(t.meanMaxLevel)}" +
                    (if (t.panics > 0) ", ${t.panics}× Panik" else "") + (if (t.cancelled > 0) ", ${t.cancelled}× abgesagt oder früher gegangen" else ""))
            }
            r.eventPhases.forEach { p -> text("${p.label}: ${p.events} Ereignisse, Ø Stufe ${de1(p.meanMaxLevel)}", small, 8f) }
        }

        val drsp = r.drsp.take(10)
        if (drsp.isNotEmpty()) {
            heading("Vor der Periode vs. danach (DRSP-Methode)")
            text("Mittlere Stärke 0–5: 7 Tage vor jeder Periode gegenüber Tag 4–10 danach. * = mind. 30 % stärker bei 2+ Perioden.", small)
            drsp.forEach { d ->
                text("${if (d.significant) "* " else "  "}${d.term}: ${de1(d.premenstrual)} vs. ${de1(d.postmenstrual)}" +
                    (d.changePercent?.let { " (${if (it >= 0) "+" else ""}${it.roundToInt()} %)" } ?: " (nur prämenstruell)"))
            }
        }

        val hm = r.heatmap
        if (hm.rows.isNotEmpty()) {
            heading("Häufigkeit nach Zyklustag")
            val rows = hm.rows.take(12)
            val labelW = 120f
            val cellW = (w - 2 * margin - labelW) / hm.maxDay
            ensure(14f + rows.size * 12f)
            for (d in 1..hm.maxDay) if (d == 1 || d % 7 == 0) c.drawText("$d", margin + labelW + (d - 1) * cellW, y + 8f, small)
            y += 11f
            rows.forEach { row ->
                c.drawText(row.term.take(24), margin, y + 8f, small)
                row.cells.forEachIndexed { i, v ->
                    val a = (v.coerceIn(0.0, 1.0) * 230 + 15).toInt()
                    fill.color = if (hm.trackedPerDay[i] == 0.0) Color.rgb(240, 240, 240) else Color.argb(a, 156, 30, 85)
                    c.drawRect(margin + labelW + i * cellW + 0.5f, y, margin + labelW + (i + 1) * cellW - 0.5f, y + 10f, fill)
                }
                y += 12f
            }
            text("Dunkler = an mehr erfassten Tagen aufgetreten.", small)
        }

        heading("Hinweis")
        text("Selbst erfasste Daten aus der App Fembiond. Die Auswertung zeigt statistische Muster und stellt keine Diagnose dar. Nachgetragene Daten sind geringer gewichtet.", small)

        page?.let { finishPage(it) }
        page = null
        doc.writeTo(out)
        doc.close()
    }
}
