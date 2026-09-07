package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.ui.components.EmptyState
import de.passderdeutschenberge.ui.components.NavigationRow
import de.passderdeutschenberge.ui.components.SearchField
import de.passderdeutschenberge.ui.text.formatElevation
import java.text.Collator

private enum class SummitFilter { ALL, OPEN, DONE }
private enum class SummitSort { PASS_ORDER, NAME, ELEVATION }

/**
 * Alle 263 Gipfel mit Suche, Filter und Sortierung. Die Namenssortierung nutzt
 * einen Collator der aktiven Sprache - Umlaute und polnische Diakritika werden
 * sonst falsch einsortiert.
 */
@Composable
fun SummitsScreen(
    catalog: PassCatalog,
    progress: PassProgress,
    onOpenSummit: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SummitFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(SummitSort.PASS_ORDER) }

    val locale = de.passderdeutschenberge.ui.text.currentLocale()
    val collator = remember(locale) { Collator.getInstance(locale) }

    val summits = remember(catalog, progress, query, filter, sort, collator) {
        val needle = query.trim().lowercase()
        catalog.summits
            .asSequence()
            .filter { needle.isEmpty() || it.displayName.lowercase().contains(needle) }
            .filter { summit ->
                when (filter) {
                    SummitFilter.ALL -> true
                    SummitFilter.OPEN -> !progress.isSummitDone(summit.id)
                    SummitFilter.DONE -> progress.isSummitDone(summit.id)
                }
            }
            .toList()
            .let { list ->
                when (sort) {
                    SummitSort.PASS_ORDER -> list.sortedBy { it.page }
                    SummitSort.NAME -> list.sortedWith { a, b ->
                        collator.compare(a.displayName, b.displayName)
                    }
                    SummitSort.ELEVATION -> list.sortedByDescending { it.elevationM ?: -1.0 }
                }
            }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SearchField(query, { query = it })
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                FilterChip(
                    selected = filter == SummitFilter.ALL,
                    onClick = { filter = SummitFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                FilterChip(
                    selected = filter == SummitFilter.OPEN,
                    onClick = { filter = SummitFilter.OPEN },
                    label = { Text(stringResource(R.string.filter_open)) },
                )
                FilterChip(
                    selected = filter == SummitFilter.DONE,
                    onClick = { filter = SummitFilter.DONE },
                    label = { Text(stringResource(R.string.filter_done)) },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                FilterChip(
                    selected = sort == SummitSort.PASS_ORDER,
                    onClick = { sort = SummitSort.PASS_ORDER },
                    label = { Text(stringResource(R.string.sort_pass_order)) },
                )
                FilterChip(
                    selected = sort == SummitSort.NAME,
                    onClick = { sort = SummitSort.NAME },
                    label = { Text(stringResource(R.string.sort_name)) },
                )
                FilterChip(
                    selected = sort == SummitSort.ELEVATION,
                    onClick = { sort = SummitSort.ELEVATION },
                    label = { Text(stringResource(R.string.sort_elevation)) },
                )
            }
        }

        if (summits.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_results)) }
        }

        items(summits, key = { it.id }) { summit ->
            val region = summit.regionId?.let { catalog.regionById[it] }
            NavigationRow(
                title = summit.displayName,
                subtitle = region?.displayName,
                trailing = formatElevation(summit.elevationM),
                leadingIcon = if (progress.isSummitDone(summit.id)) {
                    R.drawable.ic_cat_summit
                } else {
                    R.drawable.ic_cat_pass
                },
                onClick = { onOpenSummit(summit.id) },
            )
        }
    }
}
