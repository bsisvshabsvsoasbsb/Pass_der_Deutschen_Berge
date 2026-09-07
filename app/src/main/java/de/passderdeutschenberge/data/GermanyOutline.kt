package de.passderdeutschenberge.data

/**
 * Umrisse der Bundeslaender als flache Koordinatenliste (lon, lat, lon, lat, ...).
 * Quelle: Natural Earth, gemeinfrei. Vorverarbeitet und vereinfacht in
 * tools/build_assets.py - zur Laufzeit wird nichts geladen oder generalisiert.
 */
data class FederalState(
    val code: String,
    val name: String,
    val rings: List<DoubleArray>,
)

data class GermanyOutline(
    val attribution: String,
    val states: List<FederalState>,
)
