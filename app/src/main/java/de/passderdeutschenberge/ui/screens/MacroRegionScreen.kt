package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.MacroRegion
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.data.progressOf
import de.passderdeutschenberge.ui.components.NavigationRow
import de.passderdeutschenberge.ui.components.ProgressBar
import de.passderdeutschenberge.ui.components.SectionTitle
import de.passderdeutschenberge.ui.text.describeMacroRegion
import de.passderdeutschenberge.ui.text.formatElevation

/**
 * Uebersicht einer Grossregion - entspricht der Regionsuebersichtsseite des
 * Passes: Charaktertext, wichtige Gipfel, Entdeckungsregionen.
 */
@Composable
fun MacroRegionScreen(
    macro: MacroRegion,
    catalog: PassCatalog,
    progress: PassProgress,
    onOpenRegion: (String) -> Unit,
    onOpenSummit: (String) -> Unit,
) {
    val regions = catalog.regionsOf(macro)
    val keySummits = macro.keySummits.mapNotNull { ref -> ref.id?.let { catalog.summitById[it] } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ProgressBar(
                    macroProgress(macro, catalog, progress),
                    Modifier.fillMaxWidth(),
                )
            }
            SectionTitle(stringResource(R.string.section_landscape))
            Text(
                text = describeMacroRegion(macro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (keySummits.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_key_summits)) }
            items(keySummits, key = { "key-" + it.id }) { summit ->
                NavigationRow(
                    title = summit.displayName,
                    subtitle = formatElevation(summit.elevationM),
                    trailing = stringResource(R.string.page_reference, summit.page),
                    leadingIcon = R.drawable.ic_cat_summit,
                    onClick = { onOpenSummit(summit.id) },
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_discovery_regions)) }
        items(regions, key = { it.id }) { region ->
            val summits = catalog.summitsByRegion[region.id].orEmpty()
            NavigationRow(
                title = region.displayName,
                subtitle = pluralStringResource(
                    R.plurals.targets_count,
                    region.targets.size,
                    region.targets.size,
                ),
                trailing = stringResource(R.string.page_reference, region.page),
                progress = progress.progressOf(region, summits),
                onClick = { onOpenRegion(region.id) },
            )
        }
    }
}
