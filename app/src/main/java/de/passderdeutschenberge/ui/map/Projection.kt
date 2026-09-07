package de.passderdeutschenberge.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.cos

/**
 * Aequidistante Zylinderprojektion mit Breitengradkorrektur.
 *
 * Fuer den Ausschnitt Deutschlands (47..55 Grad Nord) ist der Unterschied zu
 * Web-Mercator optisch nicht relevant, die Rechnung dafuer trivial und ohne
 * Singularitaeten. Bezugsbreite ist die Mitte Deutschlands.
 */
object Projection {
    private const val REFERENCE_LATITUDE = 51.2
    private val kx = cos(Math.toRadians(REFERENCE_LATITUDE)).toFloat()

    fun x(lon: Double): Float = (lon * kx).toFloat()

    /** Y waechst nach unten, Breitengrade nach oben - daher negiert. */
    fun y(lat: Double): Float = (-lat).toFloat()

    fun toWorld(lon: Double, lat: Double) = Offset(x(lon), y(lat))
}

/**
 * Abbildung Weltkoordinaten <-> Bildschirm.
 *
 * Bewusst als reine Rechnung statt ueber eine Grafik-Matrix: fuer den Hit-Test
 * wird der Tippunkt invers in den Weltraum zurueckgerechnet, statt alle
 * Geometrien zu transformieren. Damit bleiben die vorbereiteten Pfade ueber
 * alle Frames unveraendert gueltig.
 */
data class MapViewport(
    val world: Rect,
    val canvas: Size,
    val zoom: Float,
    val pan: Offset,
) {
    private val baseScale: Float =
        if (world.width <= 0f || world.height <= 0f || canvas.minDimension <= 0f) 1f
        else minOf(canvas.width / world.width, canvas.height / world.height) * FIT_PADDING

    val scale: Float get() = baseScale * zoom

    val worldCenter: Offset = Offset(world.center.x, world.center.y)
    val canvasCenter: Offset = Offset(canvas.width / 2f, canvas.height / 2f)

    fun toScreen(worldPoint: Offset): Offset = Offset(
        (worldPoint.x - worldCenter.x) * scale + canvasCenter.x + pan.x,
        (worldPoint.y - worldCenter.y) * scale + canvasCenter.y + pan.y,
    )

    fun toScreen(x: Float, y: Float): Offset = toScreen(Offset(x, y))

    fun toWorld(screenPoint: Offset): Offset = Offset(
        (screenPoint.x - canvasCenter.x - pan.x) / scale + worldCenter.x,
        (screenPoint.y - canvasCenter.y - pan.y) / scale + worldCenter.y,
    )

    companion object {
        const val FIT_PADDING = 0.92f
        const val MIN_ZOOM = 0.8f
        const val MAX_ZOOM = 14f
    }
}
