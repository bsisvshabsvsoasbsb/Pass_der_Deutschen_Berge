package de.passderdeutschenberge

import android.app.Application
import de.passderdeutschenberge.data.PassRepository
import de.passderdeutschenberge.data.ProgressStore

/**
 * Sehr kleiner Service-Container. Ein DI-Framework waere bei zwei Abhaengigkeiten
 * mehr Buildzeit und Boilerplate als Gewinn.
 */
class PassApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            repository = PassRepository(this),
            progressStore = ProgressStore(this),
        )
    }
}

class AppContainer(
    val repository: PassRepository,
    val progressStore: ProgressStore,
)
