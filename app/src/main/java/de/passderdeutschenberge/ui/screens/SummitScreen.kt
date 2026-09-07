package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
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
import de.passderdeutschenberge.data.Summit
import de.passderdeutschenberge.ui.PassViewModel
import de.passderdeutschenberge.ui.components.CheckableRow
import de.passderdeutschenberge.ui.components.CompactTextField
import de.passderdeutschenberge.ui.components.NavigationRow
import de.passderdeutschenberge.ui.components.RatingRow
import de.passderdeutschenberge.ui.components.SectionTitle
import de.passderdeutschenberge.ui.components.today
import de.passderdeutschenberge.ui.text.describeSummit
import de.passderdeutschenberge.ui.text.formatElevation

/**
 * Gipfelseite mit dem vollstaendigen Tourenformular des Passes. Die Ziele der
 * Umgebung sind dieselben Objekte wie auf der Regionsseite - ein Haken hier
 * wirkt auch dort.
 */
@Composable
fun SummitScreen(
    summit: Summit,
    catalog: PassCatalog,
    progress: PassProgress,
    viewModel: PassViewModel,
    onOpenRegion: (String) -> Unit,
) {
    val region = summit.regionId?.let { catalog.regionById[it] }
    val macro = summit.macroRegionId?.let { catalog.macroById[it] }
    val entry = progress.summit(summit.id)

    val nearbyNames = remember(summit) {
        (summit.text as? de.passderdeutschenberge.data.PassText.Summit)?.nearby.orEmpty()
    }
    // Die Umgebungsziele der Gipfelseite sind Teilmenge der Regionsziele -
    // ueber den Namen zuordnen, damit derselbe Haken gilt.
    val nearbyTargets = remember(summit, region) {
        val byName = region?.targets?.associateBy { it.name }.orEmpty()
        nearbyNames.mapNotNull { byName[it] }
    }

    val todayLabel = stringResource(R.string.set_today)
    val dateLabel = stringResource(R.string.visit_date)

    var notes by remember(summit.id) { mutableStateOf(entry.notes) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = entry.done,
                        onClick = { viewModel.toggleSummitDone(summit.id) },
                        label = {
                            Text(
                                stringResource(
                                    if (entry.done) R.string.cd_checked else R.string.cd_unchecked,
                                ),
                            )
                        },
                    )
                    formatElevation(summit.elevationM)?.let {
                        FilterChip(selected = false, onClick = {}, label = { Text(it) })
                    }
                }
            }
            SectionTitle(stringResource(R.string.section_about_summit))
            Text(
                text = describeSummit(summit, region, macro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionTitle(stringResource(R.string.section_tour))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactTextField(
                        value = entry.date,
                        onValueChange = { v ->
                            viewModel.updateSummit(summit.id) { it.copy(date = v) }
                        },
                        label = stringResource(R.string.form_date),
                        modifier = Modifier.weight(1f),
                    )
                    de.passderdeutschenberge.ui.components.TodayChip(
                        label = todayLabel,
                        onClick = {
                            viewModel.updateSummit(summit.id) { it.copy(date = today()) }
                        },
                    )
                }
                CompactTextField(
                    value = entry.startPoint,
                    onValueChange = { v ->
                        viewModel.updateSummit(summit.id) { it.copy(startPoint = v) }
                    },
                    label = stringResource(R.string.form_start),
                )
                CompactTextField(
                    value = entry.route,
                    onValueChange = { v ->
                        viewModel.updateSummit(summit.id) { it.copy(route = v) }
                    },
                    label = stringResource(R.string.form_route),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactTextField(
                        value = entry.distance,
                        onValueChange = { v ->
                            viewModel.updateSummit(summit.id) { it.copy(distance = v) }
                        },
                        label = stringResource(R.string.form_distance),
                        modifier = Modifier.weight(1f),
                    )
                    CompactTextField(
                        value = entry.duration,
                        onValueChange = { v ->
                            viewModel.updateSummit(summit.id) { it.copy(duration = v) }
                        },
                        label = stringResource(R.string.form_time),
                        modifier = Modifier.weight(1f),
                    )
                }
                CompactTextField(
                    value = entry.elevationGain,
                    onValueChange = { v ->
                        viewModel.updateSummit(summit.id) { it.copy(elevationGain = v) }
                    },
                    label = stringResource(R.string.form_elevation_gain),
                )
                Text(
                    stringResource(R.string.form_rating),
                    style = MaterialTheme.typography.labelLarge,
                )
                RatingRow(entry.rating) { value ->
                    viewModel.updateSummit(summit.id) { it.copy(rating = value) }
                }
                CompactTextField(
                    value = notes,
                    onValueChange = { v ->
                        notes = v
                        viewModel.updateSummit(summit.id) { it.copy(notes = v) }
                    },
                    label = stringResource(R.string.form_notes),
                    singleLine = false,
                    imeAction = ImeAction.Default,
                )
            }
        }

        if (nearbyTargets.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_nearby)) }
            items(nearbyTargets.size, key = { nearbyTargets[it].id }) { index ->
                val target = nearbyTargets[index]
                val targetEntry = progress.target(target.id)
                CheckableRow(
                    title = target.name,
                    type = target.type,
                    checked = targetEntry.done,
                    date = targetEntry.date,
                    onToggle = { viewModel.toggleTargetDone(target.id) },
                    onDateChange = { v ->
                        viewModel.updateTarget(target.id) { it.copy(date = v) }
                    },
                    todayLabel = todayLabel,
                    dateLabel = dateLabel,
                )
            }
        } else if (nearbyNames.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.section_nearby)) }
            items(nearbyNames.size, key = { "n-" + nearbyNames[it] }) { index ->
                Text(
                    text = nearbyNames[index],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        if (region != null) {
            item {
                SectionTitle(stringResource(R.string.section_about_region))
                NavigationRow(
                    title = region.displayName,
                    trailing = stringResource(R.string.page_reference, region.page),
                    leadingIcon = R.drawable.ic_tab_regions,
                    onClick = { onOpenRegion(region.id) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
