package de.passderdeutschenberge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Sehr flacher Navigationsgraph: fuenf Tabs, je Tab ein eigener Rueckstapel.
 * Bei dieser Groesse ist eine eigene Zustandsmaschine schlanker und besser
 * kontrollierbar als eine Navigationsbibliothek - und spart eine Abhaengigkeit.
 */
enum class Tab { MAP, REGIONS, SUMMITS, COLLECTIONS, PROGRESS }

sealed interface Destination {
    data object Overview : Destination
    data class MacroRegion(val id: String) : Destination
    data class Region(val id: String) : Destination
    data class Summit(val id: String) : Destination

    /** Kompakte Form fuer rememberSaveable; ueberlebt Prozesstod. */
    fun encode(): String = when (this) {
        Overview -> "o"
        is MacroRegion -> "m:$id"
        is Region -> "r:$id"
        is Summit -> "s:$id"
    }

    companion object {
        fun decode(raw: String): Destination {
            val id = raw.substringAfter(':', "")
            return when (raw.substringBefore(':')) {
                "m" -> MacroRegion(id)
                "r" -> Region(id)
                "s" -> Summit(id)
                else -> Overview
            }
        }
    }
}

/** Rueckstapel eines Tabs. Index 0 ist immer die Uebersicht des Tabs. */
class TabBackStack(initial: List<Destination>) {
    val entries: SnapshotStateList<Destination> = mutableStateListOf<Destination>().apply {
        addAll(initial.ifEmpty { listOf(Destination.Overview) })
    }

    val current: Destination get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

    fun push(destination: Destination) {
        if (entries.lastOrNull() != destination) entries.add(destination)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    fun resetToRoot() {
        while (entries.size > 1) entries.removeAt(entries.lastIndex)
    }
}

/** Navigationszustand aller Tabs, inklusive aktivem Tab. */
class NavigationState(
    initialTab: Tab,
    initialStacks: Map<Tab, List<Destination>>,
) {
    var currentTab by mutableStateOf(initialTab)
        private set

    private val stacks: Map<Tab, TabBackStack> =
        Tab.entries.associateWith { TabBackStack(initialStacks[it].orEmpty()) }

    fun stack(tab: Tab): TabBackStack = stacks.getValue(tab)

    val currentStack: TabBackStack get() = stack(currentTab)

    fun selectTab(tab: Tab) {
        // Erneutes Tippen auf den aktiven Tab fuehrt zurueck zur Uebersicht -
        // dem Wunsch "erst die Uebersicht, dann hineingehen" entsprechend.
        if (tab == currentTab) currentStack.resetToRoot() else currentTab = tab
    }

    fun navigate(destination: Destination) = currentStack.push(destination)

    fun back(): Boolean = currentStack.pop()

    fun snapshot(): List<String> = buildList {
        add(currentTab.name)
        Tab.entries.forEach { tab ->
            add(tab.name + "=" + stack(tab).entries.joinToString("|") { it.encode() })
        }
    }
}

@Composable
fun rememberNavigationState(): NavigationState = rememberSaveable(
    saver = listSaver(
        save = { it.snapshot() },
        restore = { saved ->
            val tab = runCatching { Tab.valueOf(saved.first()) }.getOrDefault(Tab.MAP)
            val stacks = saved.drop(1).mapNotNull { row ->
                val name = row.substringBefore('=')
                val value = row.substringAfter('=', "")
                runCatching { Tab.valueOf(name) }.getOrNull()?.let { key ->
                    key to value.split('|').filter { it.isNotEmpty() }.map(Destination::decode)
                }
            }.toMap()
            NavigationState(tab, stacks)
        },
    ),
) { NavigationState(Tab.MAP, emptyMap()) }
