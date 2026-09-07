package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.data.Region
import de.passderdeutschenberge.data.TargetCategory
import de.passderdeutschenberge.data.progressOf
import de.passderdeutschenberge.ui.PassViewModel
import de.passderdeutschenberge.ui.components.CheckableRow
import de.passderdeutschenberge.ui.components.CompactTextField
import de.passderdeutschenberge.ui.components.NavigationRow
import de.passderdeutschenberge.ui.components.ProgressBar
import de.passderdeutschenberge.ui.components.SectionTitle
import de.passderdeutschenberge.ui.components.categoryLabel
import de.passderdeutschenberge.ui.text.describeRegion
import de.passderdeutschenberge.ui.text.formatElevation

/** Reihenfolge der Zielabschnitte wie im Pass. */
private val CATEGORY_ORDER = listOf(
    TargetCategory.COLLECTION,
    TargetCategory.CASTLE,
    TargetCategory.NATURE,
    TargetCategory.ROCK,
    TargetCategory.WATER,
    TargetCategory.TRAIL,
)

@Composable
fun RegionScreen(
    region: Region,
    catalog: PassCatalog,
    progress: PassProgress,
    viewModel: PassViewModel,
    onOpenSummit: (String) -> Unit,
) {
    val summits = catalog.summitsByRegion[region.id].orEmpty()
    val macro = region.macroRegionId?.let { catalog.macroById[it] }
    val grouped = remember(region) { region.targets.groupBy { it.category } }

    val todayLabel = stringResource(R.string.set_today)
    val dateLabel = stringResource(R.string.visit_date)

    var note by remember(region.id) { mutableStateOf(progress.regionNote(region.id)) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ProgressBar(progress.progressOf(region, summits), Modifier.fillMaxWidth())
            }
            SectionTitle(stringResource(R.string.section_about_region))
            Text(
                text = describeRegion(region, macro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SectionTitle(stringResource(R.string.section_mountains_in_region))
        }

        if (summits.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_own_summit_page),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            items(summits.size, key = { summits[it].id }) { index ->
                val summit = summits[index]
                NavigationRow(
                    title = summit.displayName,
                    subtitle = formatElevation(summit.elevationM),
                    trailing = stringResource(R.string.page_reference, summit.page),
                    leadingIcon = R.drawable.ic_cat_summit,
                    onClick = { onOpenSummit(summit.id) },
                )
            }
        }

        CATEGORY_ORDER.forEach { category ->
            val targets = grouped[category].orEmpty()
            if (targets.isEmpty()) return@forEach
            item(key = "head-" + category.name) { SectionTitle(categoryLabel(category)) }
            items(targets.size, key = { targets[it].id }) { index ->
                val target = targets[index]
                val entry = progress.target(target.id)
                CheckableRow(
                    title = target.name,
                    type = target.type,
                    checked = entry.done,
                    date = entry.date,
                    onToggle = { viewModel.toggleTargetDone(target.id) },
                    onDateChange = { value ->
                        viewModel.updateTarget(target.id) { it.copy(date = value) }
                    },
                    todayLabel = todayLabel,
                    dateLabel = dateLabel,
                    trailing = target.sourcePage?.let {
                        stringResource(R.string.page_reference, it)
                    },
                    onClick = target.sourcePage?.let { page ->
                        catalog.summitByPage[page]?.let { summit ->
                            { onOpenSummit(summit.id) }
                        }
                    },
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.section_notes))
            CompactTextField(
                value = note,
                onValueChange = {
                    note = it
                    viewModel.setRegionNote(region.id, it)
                },
                label = stringResource(R.string.form_notes),
                singleLine = false,
                imeAction = ImeAction.Default,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(24.dp)) {}
        }
    }
}
