package de.passderdeutschenberge.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.passderdeutschenberge.BuildConfig
import de.passderdeutschenberge.R
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.ui.screens.CollectionsScreen
import de.passderdeutschenberge.ui.screens.MacroRegionScreen
import de.passderdeutschenberge.ui.screens.MapScreen
import de.passderdeutschenberge.ui.screens.ProgressScreen
import de.passderdeutschenberge.ui.screens.RegionScreen
import de.passderdeutschenberge.ui.screens.RegionsOverviewScreen
import de.passderdeutschenberge.ui.screens.SummitScreen
import de.passderdeutschenberge.ui.screens.SummitsScreen

private data class TabSpec(
    val tab: Tab,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
)

private val TABS = listOf(
    TabSpec(Tab.MAP, R.string.tab_map, R.drawable.ic_tab_map),
    TabSpec(Tab.REGIONS, R.string.tab_regions, R.drawable.ic_tab_regions),
    TabSpec(Tab.SUMMITS, R.string.tab_summits, R.drawable.ic_tab_summits),
    TabSpec(Tab.COLLECTIONS, R.string.tab_collections, R.drawable.ic_tab_collections),
    TabSpec(Tab.PROGRESS, R.string.tab_progress, R.drawable.ic_tab_progress),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassApp(
    viewModel: PassViewModel,
    onLanguageSelected: (String) -> Unit,
    onApplyLocale: (String) -> Unit,
) {
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val nav = rememberNavigationState()

    // Gespeicherte Sprache auch beim Start anwenden, nicht nur beim Umschalten.
    LaunchedEffect(languageTag) { onApplyLocale(languageTag) }

    val catalog = catalogState.catalog
    val germany = catalogState.germany
    if (catalog == null || germany == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    BackHandler(enabled = nav.currentStack.canGoBack) { nav.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle(catalog, nav),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (nav.currentStack.canGoBack) {
                        IconButton(onClick = { nav.back() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { spec ->
                    NavigationBarItem(
                        selected = nav.currentTab == spec.tab,
                        onClick = { nav.selectTab(spec.tab) },
                        icon = {
                            Icon(
                                painter = painterResource(spec.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(spec.labelRes)) },
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            TabContent(
                nav = nav,
                catalog = catalog,
                germany = germany,
                progress = progress,
                viewModel = viewModel,
                languageTag = languageTag,
                onLanguageSelected = onLanguageSelected,
            )
        }
    }
}

@Composable
private fun TabContent(
    nav: NavigationState,
    catalog: PassCatalog,
    germany: de.passderdeutschenberge.data.GermanyOutline,
    progress: PassProgress,
    viewModel: PassViewModel,
    languageTag: String,
    onLanguageSelected: (String) -> Unit,
) {
    when (val destination = nav.currentStack.current) {
        is Destination.MacroRegion -> catalog.macroById[destination.id]?.let { macro ->
            MacroRegionScreen(
                macro = macro,
                catalog = catalog,
                progress = progress,
                onOpenRegion = { nav.navigate(Destination.Region(it)) },
                onOpenSummit = { nav.navigate(Destination.Summit(it)) },
            )
        }

        is Destination.Region -> catalog.regionById[destination.id]?.let { region ->
            RegionScreen(
                region = region,
                catalog = catalog,
                progress = progress,
                viewModel = viewModel,
                onOpenSummit = { nav.navigate(Destination.Summit(it)) },
            )
        }

        is Destination.Summit -> catalog.summitById[destination.id]?.let { summit ->
            SummitScreen(
                summit = summit,
                catalog = catalog,
                progress = progress,
                viewModel = viewModel,
                onOpenRegion = { nav.navigate(Destination.Region(it)) },
            )
        }

        Destination.Overview -> when (nav.currentTab) {
            Tab.MAP -> MapScreen(
                catalog = catalog,
                germany = germany,
                progress = progress,
                onOpenMacroRegion = { nav.navigate(Destination.MacroRegion(it)) },
            )

            Tab.REGIONS -> RegionsOverviewScreen(
                catalog = catalog,
                progress = progress,
                onOpenMacroRegion = { nav.navigate(Destination.MacroRegion(it)) },
            )

            Tab.SUMMITS -> SummitsScreen(
                catalog = catalog,
                progress = progress,
                onOpenSummit = { nav.navigate(Destination.Summit(it)) },
            )

            Tab.COLLECTIONS -> CollectionsScreen(
                catalog = catalog,
                progress = progress,
                onOpenRegion = { nav.navigate(Destination.Region(it)) },
            )

            Tab.PROGRESS -> ProgressScreen(
                catalog = catalog,
                progress = progress,
                languageTag = languageTag,
                viewModel = viewModel,
                appVersion = BuildConfig.VERSION_NAME,
                onLanguageSelected = onLanguageSelected,
                onClearProgress = { viewModel.clearProgress() },
            )
        }
    }
}

@Composable
private fun screenTitle(catalog: PassCatalog, nav: NavigationState): String =
    when (val destination = nav.currentStack.current) {
        is Destination.MacroRegion ->
            catalog.macroById[destination.id]?.displayName.orEmpty()

        is Destination.Region ->
            catalog.regionById[destination.id]?.displayName.orEmpty()

        is Destination.Summit ->
            catalog.summitById[destination.id]?.displayName.orEmpty()

        Destination.Overview -> stringResource(
            when (nav.currentTab) {
                Tab.MAP -> R.string.tab_map
                Tab.REGIONS -> R.string.tab_regions
                Tab.SUMMITS -> R.string.tab_summits
                Tab.COLLECTIONS -> R.string.tab_collections
                Tab.PROGRESS -> R.string.tab_progress
            },
        )
    }
