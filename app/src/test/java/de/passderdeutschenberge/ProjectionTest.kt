package de.passderdeutschenberge

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import de.passderdeutschenberge.ui.map.MapViewport
import de.passderdeutschenberge.ui.map.Projection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {

    private val germanyWorld = Rect(
        Projection.x(5.87),
        Projection.y(55.06),
        Projection.x(15.04),
        Projection.y(47.27),
    )

    @Test
    fun `Breitengrad waechst nach oben`() {
        // Y ist negiert: der noerdlichere Punkt muss den kleineren Y-Wert haben.
        assertTrue(Projection.y(55.0) < Projection.y(47.0))
    }

    @Test
    fun `Hin- und Ruecktransformation sind invers`() {
        val view = MapViewport(
            world = germanyWorld,
            canvas = Size(1080f, 1920f),
            zoom = 2.5f,
            pan = Offset(-120f, 64f),
        )
        val world = Projection.toWorld(10.0, 51.0)
        val roundTrip = view.toWorld(view.toScreen(world))
        assertEquals(world.x, roundTrip.x, 1e-3f)
        assertEquals(world.y, roundTrip.y, 1e-3f)
    }

    @Test
    fun `Ausschnitt passt ohne Zoom in die Flaeche`() {
        val canvas = Size(1000f, 1000f)
        val view = MapViewport(germanyWorld, canvas, zoom = 1f, pan = Offset.Zero)
        val topLeft = view.toScreen(Offset(germanyWorld.left, germanyWorld.top))
        val bottomRight = view.toScreen(Offset(germanyWorld.right, germanyWorld.bottom))
        assertTrue(topLeft.x >= 0f && topLeft.y >= 0f)
        assertTrue(bottomRight.x <= canvas.width && bottomRight.y <= canvas.height)
    }

    @Test
    fun `Zoom skaliert um die Bildmitte`() {
        val canvas = Size(800f, 600f)
        val base = MapViewport(germanyWorld, canvas, zoom = 1f, pan = Offset.Zero)
        val zoomed = MapViewport(germanyWorld, canvas, zoom = 3f, pan = Offset.Zero)
        val center = Offset(germanyWorld.center.x, germanyWorld.center.y)
        assertEquals(base.toScreen(center).x, zoomed.toScreen(center).x, 1e-3f)
        assertEquals(base.toScreen(center).y, zoomed.toScreen(center).y, 1e-3f)
    }
}
