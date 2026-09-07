package de.passderdeutschenberge

import de.passderdeutschenberge.data.PassParser
import de.passderdeutschenberge.data.PassText
import de.passderdeutschenberge.data.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Liest die echten Assets und prueft, dass Struktur und Querverweise des
 * Passes vollstaendig aufloesen. Das ist der Pfad, der beim App-Start als
 * erstes laeuft - ein Fehler hier waere ein Absturz beim Oeffnen.
 */
class PassDataTest {

    private val assets = File("src/main/assets")
    private val catalog = PassParser.parseCatalog(File(assets, "pass_data.json").readText())

    @Test
    fun `Umfang entspricht dem Pass`() {
        assertEquals(57, catalog.macroRegions.size)
        assertEquals(219, catalog.regions.size)
        assertEquals(263, catalog.summits.size)
        assertEquals(1426, catalog.totalTargets)
    }

    @Test
    fun `Ziel-IDs sind eindeutig`() {
        val ids = catalog.regions.flatMap { it.targets }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `jede Region gehoert zu einer Grossregion`() {
        catalog.regions.forEach { region ->
            assertNotNull("ohne Grossregion: ${region.name}", region.macroRegionId)
            assertNotNull(
                "unbekannte Grossregion: ${region.macroRegionId}",
                catalog.macroById[region.macroRegionId],
            )
        }
    }

    @Test
    fun `jeder Gipfel haengt an Region und Grossregion`() {
        catalog.summits.forEach { summit ->
            assertNotNull("ohne Region: ${summit.name}", catalog.regionById[summit.regionId])
            assertNotNull("ohne Grossregion: ${summit.name}", catalog.macroById[summit.macroRegionId])
        }
    }

    @Test
    fun `Verweise der Grossregionen loesen auf`() {
        catalog.macroRegions.forEach { macro ->
            macro.regionIds.forEach { id ->
                assertNotNull("unbekannte Region $id in ${macro.name}", catalog.regionById[id])
            }
            macro.keySummits.forEach { ref ->
                assertNotNull("unbekannter Gipfel ${ref.name}", catalog.summitById[ref.id])
            }
        }
    }

    @Test
    fun `alle Beschreibungen sind aus Schablonen aufgebaut`() {
        // Kein Rueckfall auf unuebersetzbaren Rohtext - sonst waere die App
        // in Teilen einsprachig.
        assertTrue(catalog.summits.none { it.text is PassText.Raw })
        assertTrue(catalog.regions.none { it.text is PassText.Raw })
    }

    @Test
    fun `verortete Grossregionen liegen im deutschen Ausschnitt`() {
        assertEquals(56, catalog.locatedMacroRegions.size)
        assertEquals(1, catalog.collectionMacroRegions.size)
        catalog.locatedMacroRegions.forEach { macro ->
            val lat = macro.lat!!
            val lon = macro.lon!!
            assertTrue("Breite ausserhalb: ${macro.name} $lat", lat in 47.0..55.2)
            assertTrue("Laenge ausserhalb: ${macro.name} $lon", lon in 5.8..15.1)
        }
    }

    @Test
    fun `Regionen ohne Koordinate sind ausschliesslich Sammlungen`() {
        catalog.regions.filter { it.lat == null }.forEach { region ->
            val macro = catalog.macroById[region.macroRegionId]
            assertTrue("ohne Koordinate: ${region.name}", macro?.virtual == true)
        }
    }

    @Test
    fun `Sammlungsziele verweisen auf vorhandene Passseiten`() {
        val pages = catalog.summits.map { it.page }.toSet() +
            catalog.regions.flatMap { it.pages }.toSet()
        val collectionTargets = catalog.regions
            .flatMap { it.targets }
            .filter { it.category == TargetCategory.COLLECTION }
        assertTrue(collectionTargets.isNotEmpty())
        collectionTargets.forEach { target ->
            val page = target.sourcePage
            assertNotNull("ohne Quellseite: ${target.name}", page)
            assertTrue("unbekannte Quellseite $page bei ${target.name}", page in pages)
        }
    }

    @Test
    fun `Kartengeometrie enthaelt alle Bundeslaender`() {
        val germany = PassParser.parseGermany(File(assets, "germany.json").readText())
        assertEquals(16, germany.states.size)
        germany.states.forEach { state ->
            assertTrue("leerer Umriss: ${state.name}", state.rings.isNotEmpty())
            state.rings.forEach { ring ->
                assertTrue("ungerade Koordinatenzahl: ${state.name}", ring.size % 2 == 0)
                assertTrue("zu wenige Punkte: ${state.name}", ring.size >= 8)
            }
        }
    }
}
