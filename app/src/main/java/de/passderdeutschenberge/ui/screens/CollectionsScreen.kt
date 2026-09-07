package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
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
import de.passderdeutschenberge.data.AreaProgress
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.ui.components.NavigationRow
import de.passderdeutschenberge.ui.components.SectionTitle
import de.passderdeutschenberge.ui.text.describeMacroRegion

/**
 * Die ueberregionalen Sammlungen des Passes. Ihre Ziele verweisen auf die
 * Quellseiten im Pass, deshalb stehen sie bewusst getrennt von den Gebieten.
 */
@Composable
fun CollectionsScreen(
    catalog: PassCatalog,
    progress: PassProgress,
    onOpenRegion: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        catalog.collectionMacroRegions.forEach { macro ->
            item(key = "head-" + macro.id) {
                SectionTitle(macro.displayName)
                Text(
                    text = describeMacroRegion(macro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(catalog.regionsOf(macro), key = { it.id }) { region ->
                val done = region.targets.count { progress.isTargetDone(it.id) }
                NavigationRow(
                    title = region.displayName,
                    subtitle = pluralStringResource(
                        R.plurals.targets_count,
                        region.targets.size,
                        region.targets.size,
                    ),
                    trailing = stringResource(R.string.page_reference, region.page),
                    progress = AreaProgress(done, region.targets.size),
                    onClick = { onOpenRegion(region.id) },
                )
            }
        }
    }
}
