package de.passderdeutschenberge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.passderdeutschenberge.AppContainer
import de.passderdeutschenberge.data.GermanyOutline
import de.passderdeutschenberge.data.PassCatalog
import de.passderdeutschenberge.data.PassProgress
import de.passderdeutschenberge.data.ProgressStore
import de.passderdeutschenberge.data.SummitEntry
import de.passderdeutschenberge.data.TargetEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogState(
    val catalog: PassCatalog? = null,
    val germany: GermanyOutline? = null,
) {
    val isReady: Boolean get() = catalog != null && germany != null
}

class PassViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _catalog = MutableStateFlow(CatalogState())
    val catalogState: StateFlow<CatalogState> = _catalog.asStateFlow()

    val progress: StateFlow<PassProgress> = container.progressStore.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PassProgress())

    val languageTag: StateFlow<String> = container.progressStore.languageTag
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val progressStore: ProgressStore get() = container.progressStore

    init {
        viewModelScope.launch {
            val catalog = container.repository.loadCatalog()
            val germany = container.repository.loadGermany()
            _catalog.value = CatalogState(catalog, germany)
        }
    }

    fun toggleSummitDone(id: String) = viewModelScope.launch {
        container.progressStore.updateSummit(id) { it.copy(done = !it.done) }
    }

    fun updateSummit(id: String, transform: (SummitEntry) -> SummitEntry) = viewModelScope.launch {
        container.progressStore.updateSummit(id, transform)
    }

    fun toggleTargetDone(id: String) = viewModelScope.launch {
        container.progressStore.updateTarget(id) { it.copy(done = !it.done) }
    }

    fun updateTarget(id: String, transform: (TargetEntry) -> TargetEntry) = viewModelScope.launch {
        container.progressStore.updateTarget(id, transform)
    }

    fun setRegionNote(id: String, note: String) = viewModelScope.launch {
        container.progressStore.setRegionNote(id, note)
    }

    fun setLanguage(tag: String) = viewModelScope.launch {
        container.progressStore.setLanguageTag(tag)
    }

    fun clearProgress() = viewModelScope.launch {
        container.progressStore.clearAll()
    }

    companion object {
        val CONTAINER_KEY = object : CreationExtras.Key<AppContainer> {}

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PassViewModel(this[CONTAINER_KEY]!!) }
        }
    }
}
