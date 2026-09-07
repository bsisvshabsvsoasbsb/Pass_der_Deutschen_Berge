package de.passderdeutschenberge

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import de.passderdeutschenberge.ui.PassApp
import de.passderdeutschenberge.ui.PassViewModel
import de.passderdeutschenberge.ui.theme.PassTheme

/**
 * AppCompatActivity ist Voraussetzung fuer die Per-App-Sprachumschaltung ueber
 * AppCompatDelegate; die Oberflaeche selbst ist vollstaendig Compose.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as PassApplication).container
        val extras = MutableCreationExtras().apply {
            set(PassViewModel.CONTAINER_KEY, container)
        }
        val viewModel = ViewModelProvider(
            viewModelStore,
            PassViewModel.Factory,
            extras,
        )[PassViewModel::class.java]

        setContent {
            PassTheme {
                PassApp(
                    viewModel = viewModel,
                    onLanguageSelected = viewModel::setLanguage,
                    onApplyLocale = ::applyLocale,
                )
            }
        }
    }

    /**
     * Leerer Tag = Systemsprache. Wird nur aufgerufen, wenn sich die Sprache
     * tatsaechlich unterscheidet: die API erneuert die Activity selbst, ein
     * unbedingter Aufruf erzeugt sonst eine Neustartschleife.
     */
    private fun applyLocale(tag: String) {
        val current = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore(',')
        if (current == tag) return
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
