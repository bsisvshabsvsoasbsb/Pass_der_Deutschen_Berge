package de.passderdeutschenberge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.AreaProgress
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.data.ProgressTransfer
import de.passderdeutschenberge.data.progressOf
import de.passderdeutschenberge.ui.PassViewModel
import de.passderdeutschenberge.ui.TransferState
import de.passderdeutschenberge.ui.components.ProgressBar
import de.passderdeutschenberge.ui.components.SectionTitle
import de.passderdeutschenberge.ui.components.StatCard

@Composable
fun ProgressScreen(
    catalog: PassCatalog,
    progress: PassProgress,
    languageTag: String,
    viewModel: PassViewModel,
    appVersion: String,
    onLanguageSelected: (String) -> Unit,
    onClearProgress: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var askImportMode by remember { mutableStateOf(false) }
    // Wird zwischen Modusauswahl und Dateiauswahl gehalten.
    var importReplaces by remember { mutableStateOf(true) }
    var exportFileName by remember { mutableStateOf(ProgressTransfer.defaultFileName()) }

    val resolver = LocalContext.current.contentResolver
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportProgress(resolver, uri, appVersion, exportFileName)
    }
    // Bewusst */*: manche Dateiverwaltungen melden fuer .json einen anderen
    // MIME-Typ, die Datei waere sonst nicht auswaehlbar. Geprueft wird der
    // Inhalt, nicht der Typ.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importProgress(resolver, uri, importReplaces)
    }

    val summitProgress = AreaProgress(progress.doneSummitCount, catalog.totalSummits)
    val targetProgress = AreaProgress(progress.doneTargetCount, catalog.totalTargets)
    val completedRegions = remember(catalog, progress) {
        catalog.regions.count { region ->
            progress.progressOf(region, catalog.summitsByRegion[region.id].orEmpty()).isComplete
        }
    }
    val completedMacros = remember(catalog, progress) {
        catalog.locatedMacroRegions.count { macroProgress(it, catalog, progress).isComplete }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.progress_title))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            StatCard(
                label = stringResource(R.string.progress_summits),
                value = stringResource(R.string.progress_percent, summitProgress.percent),
                detail = pluralStringResource(
                    R.plurals.summits_climbed,
                    summitProgress.done,
                    summitProgress.done,
                ),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.progress_targets),
                value = stringResource(R.string.progress_percent, targetProgress.percent),
                detail = pluralStringResource(
                    R.plurals.targets_visited,
                    targetProgress.done,
                    targetProgress.done,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.progress_summits), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ProgressBar(summitProgress, Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.progress_targets), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ProgressBar(targetProgress, Modifier.fillMaxWidth())
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            StatCard(
                label = stringResource(R.string.progress_regions),
                value = stringResource(
                    R.string.progress_of,
                    completedRegions,
                    catalog.regions.size,
                ),
                detail = null,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.progress_macro_regions),
                value = stringResource(
                    R.string.progress_of,
                    completedMacros,
                    catalog.locatedMacroRegions.size,
                ),
                detail = null,
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle(stringResource(R.string.settings_language))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            FilterChip(
                selected = languageTag.isEmpty(),
                onClick = { onLanguageSelected("") },
                label = { Text(stringResource(R.string.language_system)) },
            )
            FilterChip(
                selected = languageTag == "de",
                onClick = { onLanguageSelected("de") },
                label = { Text(stringResource(R.string.language_de)) },
            )
            FilterChip(
                selected = languageTag == "pl",
                onClick = { onLanguageSelected("pl") },
                label = { Text(stringResource(R.string.language_pl)) },
            )
        }

        SectionTitle(stringResource(R.string.about_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(
                    R.string.about_stats,
                    catalog.totalTargets,
                    catalog.totalSummits,
                    catalog.regions.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.about_sources_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(R.string.about_sources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.about_map_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle(stringResource(R.string.transfer_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.transfer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    exportFileName = ProgressTransfer.defaultFileName()
                    exportLauncher.launch(exportFileName)
                }) {
                    Text(stringResource(R.string.action_export))
                }
                OutlinedButton(onClick = { askImportMode = true }) {
                    Text(stringResource(R.string.action_import))
                }
            }
        }

        Column(Modifier.padding(16.dp)) {
            OutlinedButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.settings_reset))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearProgress()
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (askImportMode) {
        AlertDialog(
            onDismissRequest = { askImportMode = false },
            title = { Text(stringResource(R.string.import_mode_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_mode_question))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.import_mode_replace) + " – " +
                            stringResource(R.string.import_mode_replace_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.import_mode_merge) + " – " +
                            stringResource(R.string.import_mode_merge_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    importReplaces = true
                    askImportMode = false
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.import_mode_replace)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    importReplaces = false
                    askImportMode = false
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.import_mode_merge)) }
            },
        )
    }

    transfer?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTransfer() },
            title = { Text(stringResource(R.string.transfer_result_title)) },
            text = { Text(transferMessage(state)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissTransfer() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun transferMessage(state: TransferState): String = when (state) {
    is TransferState.Exported -> stringResource(R.string.export_done, state.fileName)

    is TransferState.Imported -> {
        val report = state.report
        val head = if (report.total == 0) {
            stringResource(R.string.import_nothing)
        } else {
            stringResource(
                R.string.import_done,
                report.summits,
                report.targets,
                report.notes,
            )
        }
        if (report.unresolved > 0) {
            head + "\n\n" + stringResource(R.string.import_unresolved, report.unresolved)
        } else {
            head
        }
    }

    is TransferState.Failed -> stringResource(R.string.transfer_failed) + " " + when (state.reason) {
        ProgressTransfer.Reason.INVALID_JSON -> stringResource(R.string.transfer_error_invalid_json)
        ProgressTransfer.Reason.WRONG_FORMAT -> stringResource(R.string.transfer_error_wrong_format)
        ProgressTransfer.Reason.NEWER_VERSION ->
            stringResource(R.string.transfer_error_newer_version, state.detail)
        ProgressTransfer.Reason.IO -> stringResource(R.string.transfer_error_io)
    }
}
