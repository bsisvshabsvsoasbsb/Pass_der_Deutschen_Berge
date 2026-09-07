package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.ui.components.EmptyState
import de.passderdeutschenberge.ui.components.NavigationRow
import de.passderdeutschenberge.ui.components.SearchField
import de.passderdeutschenberge.ui.components.SectionTitle

/**
 * Einstieg in die Regionen: erst die Uebersicht aller Grossregionen in der
 * Reihenfolge des Passes, dann hinein. Sammlungen stehen in einem eigenen Tab
 * und tauchen hier nicht auf.
 */
@Composable
fun RegionsOverviewScreen(
    catalog: PassCatalog,
    progress: PassProgress,
    onOpenMacroRegion: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val entries = remember(catalog, query) {
        val needle = query.trim().lowercase()
        catalog.locatedMacroRegions.filter { macro ->
            needle.isEmpty() ||
                macro.displayName.lowercase().contains(needle) ||
                catalog.regionsOf(macro).any { it.displayName.lowercase().contains(needle) }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.overview_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                SearchField(query, { query = it })
                SectionTitle(stringResource(R.string.overview_macro_regions))
            }
        }

        if (entries.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_results)) }
        }

        items(entries, key = { it.id }) { macro ->
            val regions = catalog.regionsOf(macro)
            val summits = catalog.summitsOf(macro)
            NavigationRow(
                title = macro.displayName,
                subtitle = pluralStringResource(
                    R.plurals.regions_count,
                    regions.size,
                    regions.size,
                ) + " · " + pluralStringResource(
                    R.plurals.summits_count,
                    summits.size,
                    summits.size,
                ),
                progress = macroProgress(macro, catalog, progress),
                onClick = { onOpenMacroRegion(macro.id) },
            )
        }
    }
}
