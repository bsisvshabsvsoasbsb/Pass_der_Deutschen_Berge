package de.passderdeutschenberge

import de.passderdeutschenberge.data.PassParser
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.data.ProgressTransfer
import de.passderdeutschenberge.data.SummitEntry
import de.passderdeutschenberge.data.TargetEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Datenaustausch ist der Weg, auf dem der Fortschritt einen Signatur- oder
 * Geraetewechsel ueberlebt. Faellt er aus, sind die Eintraege verloren -
 * entsprechend eng wird hier geprueft.
 */
class ProgressTransferTest {

    private val catalog =
        PassParser.parseCatalog(File("src/main/assets/pass_data.json").readText())

    private val summit = catalog.summits.first { it.regionId != null }
    private val target = catalog.regions.first { it.targets.isNotEmpty() }.targets.first()
    private val region = catalog.regions.first()

    private val sample = PassProgress(
        summits = mapOf(
            summit.id to SummitEntry(
                done = true,
                date = "2026-05-01",
                startPoint = "Wanderparkplatz",
                route = "Rundweg A1",
                distance = "8,4 km",
                duration = "2:30",
                elevationGain = "310",
                rating = 4,
                notes = "Nebel am Gipfel",
            ),
        ),
        targets = mapOf(target.id to TargetEntry(done = true, date = "2026-05-02")),
        regionNotes = mapOf(region.id to "Im Herbst wiederkommen"),
    )

    @Test
    fun `Export und Import ergeben denselben Stand`() {
        val json = ProgressTransfer.export(sample, catalog, "1.0")
        val (restored, report) = ProgressTransfer.import(json, catalog, PassProgress(), replace = true)
        assertEquals(sample.summits, restored.summits)
        assertEquals(sample.targets, restored.targets)
        assertEquals(sample.regionNotes, restored.regionNotes)
        assertEquals(0, report.unresolved)
        assertEquals(3, report.total)
    }

    @Test
    fun `Export enthaelt beide Schluessel`() {
        val root = JSONObject(ProgressTransfer.export(sample, catalog, "1.0"))
        val s = root.getJSONArray("summits").getJSONObject(0)
        assertEquals(summit.id, s.getString("id"))
        assertEquals(catalog.keyOfSummit(summit), s.getString("key"))
        val t = root.getJSONArray("targets").getJSONObject(0)
        assertEquals(target.id, t.getString("id"))
        assertEquals(catalog.keyOfTarget(target.id), t.getString("key"))
    }

    @Test
    fun `Zuordnung gelingt auch wenn sich die technischen IDs verschieben`() {
        // Simuliert eine neue PDF-Fassung: die Seitenzahlen und damit die IDs
        // sind andere, die fachlichen Schluessel bleiben gleich.
        val json = ProgressTransfer.export(sample, catalog, "1.0")
        val broken = JSONObject(json).apply {
            getJSONArray("summits").getJSONObject(0).put("id", "summit-99999")
            getJSONArray("targets").getJSONObject(0).put("id", "region-99999--nichts")
            getJSONArray("regionNotes").getJSONObject(0).put("id", "region-99999")
        }.toString()
        assertNotEquals(json, broken)

        val (restored, report) = ProgressTransfer.import(broken, catalog, PassProgress(), replace = true)
        assertEquals(0, report.unresolved)
        assertEquals(sample.summits, restored.summits)
        assertEquals(sample.targets, restored.targets)
        assertEquals(sample.regionNotes, restored.regionNotes)
    }

    @Test
    fun `unbekannte Eintraege werden gezaehlt statt verschluckt`() {
        val json = JSONObject(ProgressTransfer.export(sample, catalog, "1.0")).apply {
            getJSONArray("summits").getJSONObject(0).put("id", "summit-99999").put("key", "gibt/es-nicht")
        }.toString()
        val (restored, report) = ProgressTransfer.import(json, catalog, PassProgress(), replace = true)
        assertEquals(1, report.unresolved)
        assertEquals(0, restored.summits.size)
        assertEquals(1, restored.targets.size)
    }

    @Test
    fun `Zusammenfuehren behaelt bestehende Eintraege`() {
        val other = catalog.summits.first { it.id != summit.id && it.regionId != null }
        val existing = PassProgress(summits = mapOf(other.id to SummitEntry(done = true)))
        val json = ProgressTransfer.export(sample, catalog, "1.0")

        val (merged, _) = ProgressTransfer.import(json, catalog, existing, replace = false)
        assertTrue(merged.summits.containsKey(other.id))
        assertTrue(merged.summits.containsKey(summit.id))

        val (replaced, _) = ProgressTransfer.import(json, catalog, existing, replace = true)
        assertTrue(!replaced.summits.containsKey(other.id))
    }

    @Test
    fun `fremde und beschaedigte Dateien werden abgewiesen`() {
        val kaputt = assertThrows(ProgressTransfer.TransferException::class.java) {
            ProgressTransfer.import("kein json", catalog, PassProgress(), replace = true)
        }
        assertEquals(ProgressTransfer.Reason.INVALID_JSON, kaputt.reason)

        val fremd = assertThrows(ProgressTransfer.TransferException::class.java) {
            ProgressTransfer.import("""{"format":"etwas anderes"}""", catalog, PassProgress(), true)
        }
        assertEquals(ProgressTransfer.Reason.WRONG_FORMAT, fremd.reason)

        val neuer = assertThrows(ProgressTransfer.TransferException::class.java) {
            ProgressTransfer.import(
                """{"format":"${ProgressTransfer.FORMAT}","version":99}""",
                catalog, PassProgress(), true,
            )
        }
        assertEquals(ProgressTransfer.Reason.NEWER_VERSION, neuer.reason)
    }

    @Test
    fun `Schluessel decken den gesamten Pass ab`() {
        assertEquals(catalog.summits.size, catalog.summitByKey.size)
        assertEquals(catalog.totalTargets, catalog.targetByKey.size)
        assertEquals(catalog.regions.size, catalog.regionBySlug.size)
    }
}
