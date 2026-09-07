package de.passderdeutschenberge.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.MacroRegion
import de.passderdeutschenberge.data.PassText
import de.passderdeutschenberge.data.Region
import de.passderdeutschenberge.data.Summit
import java.text.NumberFormat
import java.util.Locale

/**
 * Setzt die Beschreibungen aus lokalisierten Schablonen zusammen.
 *
 * Die Texte im Quell-PDF sind selbst schablonenhaft erzeugt. Deshalb werden nur
 * die variablen Bestandteile gespeichert und der Satz hier sprachrichtig
 * gebaut - inklusive Aufzaehlungszeichen und Zahlformat der aktiven Sprache.
 */

@Composable
fun currentLocale(): Locale {
    // Ueber die Resources statt ueber LocalConfiguration: dieser Weg ist ueber
    // alle Compose-Versionen stabil und folgt der per-App gesetzten Sprache.
    val configuration = LocalContext.current.resources.configuration
    return remember(configuration) {
        configuration.locales.get(0) ?: Locale.getDefault()
    }
}

/** "A, B und C" bzw. "A, B i C". */
@Composable
fun joinNatural(items: List<String>): String {
    val and = stringResource(R.string.list_and)
    return when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " " + and + " " + items.last()
    }
}

@Composable
fun formatElevation(meters: Double?): String? {
    val locale = currentLocale()
    if (meters == null) return null
    val format = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
    }
    return stringResource(R.string.elevation_format, format.format(meters))
}

@Composable
private fun characterText(macro: MacroRegion?): String {
    if (macro == null) return ""
    val res = characterTextRes(macro.characterResName)
    return if (res != 0) stringResource(res) else ""
}

@Composable
fun describeSummit(summit: Summit, region: Region?, macro: MacroRegion?): String {
    val text = summit.text
    if (text is PassText.Raw) return text.de

    val parts = mutableListOf<String>()
    parts += stringResource(
        R.string.tpl_summit_intro,
        summit.displayName,
        region?.displayName ?: "",
    )
    formatElevation(summit.elevationM)?.let {
        parts += stringResource(R.string.tpl_summit_elevation, it)
    }
    characterText(macro).takeIf { it.isNotEmpty() }?.let { parts += it }
    val nearby = (text as? PassText.Summit)?.nearby.orEmpty()
    if (nearby.isNotEmpty()) {
        parts += stringResource(R.string.tpl_summit_nearby, joinNatural(nearby))
    }
    parts += stringResource(R.string.tpl_summit_outro)
    return parts.joinToString(" ")
}

@Composable
fun describeRegion(region: Region, macro: MacroRegion?): String {
    when (val text = region.text) {
        is PassText.Raw -> return text.de

        is PassText.Collection -> {
            val parts = mutableListOf(stringResource(R.string.tpl_collection))
            if (text.top.isNotEmpty()) {
                parts += stringResource(R.string.tpl_collection_top, joinNatural(text.top))
            }
            return parts.joinToString(" ")
        }

        is PassText.Region -> {
            val parts = mutableListOf<String>()
            characterText(macro).takeIf { it.isNotEmpty() }?.let { parts += it }
            val focus = text.focus.map { stringResource(focusRes(it)) }
            if (focus.isNotEmpty()) {
                parts += stringResource(
                    R.string.tpl_region_focus,
                    region.displayName,
                    joinNatural(focus),
                )
            }
            if (text.top.isNotEmpty()) {
                parts += stringResource(R.string.tpl_region_top, joinNatural(text.top))
            }
            if (text.peaks.isNotEmpty()) {
                parts += stringResource(R.string.tpl_region_peaks, joinNatural(text.peaks))
            }
            return parts.joinToString(" ")
        }

        else -> return ""
    }
}

@Composable
fun describeMacroRegion(macro: MacroRegion): String = characterText(macro)

@StringRes
private fun focusRes(key: String): Int = when (key) {
    "castle" -> R.string.focus_castle
    "water" -> R.string.focus_water
    "nature" -> R.string.focus_nature
    "rock" -> R.string.focus_rock
    "trail" -> R.string.focus_trail
    else -> R.string.focus_mixed
}
