package de.passderdeutschenberge.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.passderdeutschenberge.data.GermanyOutline
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Ein anklickbarer Punkt auf der Karte. */
data class MapMarker(
    val id: String,
    val lon: Double,
    val lat: Double,
    val color: Color,
    /** 0..1 - Anteil der Fuellung, entspricht dem Fortschritt des Gebiets. */
    val fillFraction: Float,
    /** Umfang des Gebiets; steuert die Markergroesse. */
    val weight: Int,
)

/** Vorbereitete Geometrie: einmal gebaut, ueber alle Frames wiederverwendet. */
private class GermanyGeometry(outline: GermanyOutline) {
    val paths: List<Path> = outline.states.map { state ->
        Path().apply {
            fillType = PathFillType.EvenOdd // Enklaven (z. B. Berlin) bleiben Loecher
            state.rings.forEach { ring ->
                var i = 0
                while (i + 1 < ring.size) {
                    val point = Projection.toWorld(ring[i], ring[i + 1])
                    if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    i += 2
                }
                close()
            }
        }
    }

    val bounds: Rect = paths.map { it.getBounds() }.reduceOrNull { a, b ->
        Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
    } ?: Rect(0f, 0f, 1f, 1f)
}

/**
 * Deutschlandkarte mit den Gebieten des Passes.
 *
 * Zeichnet die Bundeslandumrisse als Orientierungsrahmen und darauf die Gebiete
 * des Passes als Marker, eingefaerbt nach Fortschritt. Vollstaendig offline:
 * keine Kacheln, kein API-Schluessel, keine Netzwerkberechtigung.
 *
 * Die Geometrie liegt in Weltkoordinaten und wird nie neu gebaut. Verschieben
 * und Zoomen aendert nur die Transformation der Zeichenflaeche; fuer den
 * Treffertest wird umgekehrt der Tippunkt in den Weltraum zurueckgerechnet.
 */
@Composable
fun GermanyMap(
    outline: GermanyOutline,
    markers: List<MapMarker>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpen: (String) -> Unit,
    landColor: Color,
    landStroke: Color,
    selectionColor: Color,
    mapDescription: String,
    modifier: Modifier = Modifier,
    resetSignal: Int = 0,
) {
    val geometry = remember(outline) { GermanyGeometry(outline) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }

    val currentMarkers by rememberUpdatedState(markers)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentSelected by rememberUpdatedState(selectedId)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = mapDescription }
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom)
                        .coerceIn(MapViewport.MIN_ZOOM, MapViewport.MAX_ZOOM)
                    panX += pan.x
                    panY += pan.y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val size = canvasSize
                    if (size.width <= 0f || size.height <= 0f) return@detectTapGestures
                    val view = MapViewport(geometry.bounds, size, zoom, Offset(panX, panY))
                    val hit = nearestMarker(currentMarkers, view, tap)
                    when {
                        hit == null -> currentOnSelect(null)
                        // Erster Tipp waehlt aus und zeigt die Uebersicht darunter,
                        // zweiter Tipp oeffnet das Gebiet.
                        hit.id == currentSelected -> currentOnOpen(hit.id)
                        else -> currentOnSelect(hit.id)
                    }
                }
            },
    ) {
        val view = MapViewport(geometry.bounds, size, zoom, Offset(panX, panY))

        withTransform({
            translate(view.canvasCenter.x + view.pan.x, view.canvasCenter.y + view.pan.y)
            scale(view.scale, view.scale, pivot = Offset.Zero)
            translate(-view.worldCenter.x, -view.worldCenter.y)
        }) {
            geometry.paths.forEach { path ->
                drawPath(path, color = landColor)
                drawPath(path, color = landStroke, style = Stroke(width = 1f / view.scale))
            }
        }

        currentMarkers.forEach { marker ->
            drawMarker(
                marker = marker,
                center = view.toScreen(Projection.toWorld(marker.lon, marker.lat)),
                radiusDp = markerRadius(marker.weight),
                selected = marker.id == currentSelected,
                selectionColor = selectionColor,
            )
        }
    }
}

private fun DrawScope.drawMarker(
    marker: MapMarker,
    center: Offset,
    radiusDp: Float,
    selected: Boolean,
    selectionColor: Color,
) {
    val radius = radiusDp * density
    drawCircle(color = marker.color.copy(alpha = 0.22f), radius = radius, center = center)
    drawCircle(
        color = marker.color,
        radius = radius,
        center = center,
        style = Stroke(width = 1.6f * density),
    )
    if (marker.fillFraction > 0f) {
        // Flaechentreue Fuellung: der Radius folgt der Wurzel des Anteils.
        drawCircle(
            color = marker.color,
            radius = radius * sqrt(marker.fillFraction),
            center = center,
        )
    }
    if (selected) {
        drawCircle(
            color = selectionColor,
            radius = radius + 5f * density,
            center = center,
            style = Stroke(width = 2.5f * density),
        )
    }
}

/** Marker-Groesse waechst mit dem Umfang des Gebiets, gedeckelt fuer Lesbarkeit. */
private fun markerRadius(weight: Int): Float = when {
    weight >= 60 -> 11f
    weight >= 35 -> 9.5f
    weight >= 18 -> 8f
    else -> 6.5f
}

/** Naechstgelegener Marker innerhalb der Tippflaeche, sonst null. */
private fun nearestMarker(
    markers: List<MapMarker>,
    view: MapViewport,
    tap: Offset,
): MapMarker? {
    val tolerance = TAP_TOLERANCE_PX * TAP_TOLERANCE_PX
    var best: MapMarker? = null
    var bestDistance = Float.MAX_VALUE
    for (marker in markers) {
        val screen = view.toScreen(Projection.toWorld(marker.lon, marker.lat))
        val distance = (screen - tap).getDistanceSquared()
        if (distance <= tolerance && distance < bestDistance) {
            best = marker
            bestDistance = distance
        }
    }
    return best
}

private const val TAP_TOLERANCE_PX = 64f
