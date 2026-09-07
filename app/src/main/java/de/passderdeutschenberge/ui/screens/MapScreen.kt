package de.passderdeutschenberge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.AreaProgress
import de.passderdeutschenberge.data.GermanyOutline
import de.passderdeutschenberge.data.MacroRegion
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.ui.components.ProgressBar
import de.passderdeutschenberge.ui.components.progressColor
import de.passderdeutschenberge.ui.map.GermanyMap
import de.passderdeutschenberge.ui.map.MapMarker
import de.passderdeutschenberge.ui.text.describeMacroRegion
import de.passderdeutschenberge.ui.theme.ProgressColors

/** Fortschritt einer Grossregion ueber alle enthaltenen Gipfel und Ziele. */
fun macroProgress(
    macro: MacroRegion,
    catalog: PassCatalog,
    progress: PassProgress,
): AreaProgress {
    val summits = catalog.summitsOf(macro)
    val targets = catalog.targetsOf(macro)
    return AreaProgress(
        done = summits.count { progress.isSummitDone(it.id) } +
            targets.count { progress.isTargetDone(it.id) },
        total = summits.size + targets.size,
    )
}

@Composable
fun MapScreen(
    catalog: PassCatalog,
    germany: GermanyOutline,
    progress: PassProgress,
    onOpenMacroRegion: (String) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var resetSignal by remember { mutableIntStateOf(0) }

    val doneColor = ProgressColors.done
    val startedColor = ProgressColors.started
    val openColor = ProgressColors.open

    val markers = remember(catalog, progress, doneColor, startedColor, openColor) {
        catalog.locatedMacroRegions.mapNotNull { macro ->
            val lat = macro.lat ?: return@mapNotNull null
            val lon = macro.lon ?: return@mapNotNull null
            val area = macroProgress(macro, catalog, progress)
            MapMarker(
                id = macro.id,
                lon = lon,
                lat = lat,
                color = when {
                    area.isComplete -> doneColor
                    area.isStarted -> startedColor
                    else -> openColor
                },
                fillFraction = area.fraction,
                weight = area.total,
            )
        }
    }

    val selected = selectedId?.let { catalog.macroById[it] }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            GermanyMap(
                outline = germany,
                markers = markers,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onOpen = onOpenMacroRegion,
                landColor = MaterialTheme.colorScheme.surfaceVariant,
                landStroke = MaterialTheme.colorScheme.outline,
                selectionColor = MaterialTheme.colorScheme.primary,
                mapDescription = stringResource(R.string.cd_map),
                resetSignal = resetSignal,
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    TextButton(onClick = { resetSignal++ }) {
                        Text(stringResource(R.string.map_reset))
                    }
                }
            }
            MapLegend(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }

        SelectionPanel(
            macro = selected,
            catalog = catalog,
            progress = progress,
            onOpen = onOpenMacroRegion,
        )
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            LegendRow(ProgressColors.done, stringResource(R.string.map_legend_done))
            LegendRow(ProgressColors.started, stringResource(R.string.map_legend_partial))
            LegendRow(ProgressColors.open, stringResource(R.string.map_legend_open))
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Uebersicht des gewaehlten Gebiets - bewusst zwischen Karte und Detailseite:
 * ein Tipp waehlt aus und zeigt hier die Zusammenfassung, erst der zweite oder
 * die Schaltflaeche oeffnet das Gebiet.
 */
@Composable
private fun SelectionPanel(
    macro: MacroRegion?,
    catalog: PassCatalog,
    progress: PassProgress,
    onOpen: (String) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (macro == null) {
                Text(
                    stringResource(R.string.map_selected_none),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.map_approximate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val area = macroProgress(macro, catalog, progress)
            Text(
                macro.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = progressColor(area),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                describeMacroRegion(macro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            ProgressBar(area, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(macro.id) }) {
                    Text(stringResource(R.string.cd_open_details))
                }
            }
        }
    }
}
