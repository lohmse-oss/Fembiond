package de.fembiond.app

import android.app.Application
import de.fembiond.app.data.FembiondDatabase
import de.fembiond.app.data.Repository
import de.fembiond.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class FembiondApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository: Repository by lazy {
        Repository(FembiondDatabase.create(this).dao(), SettingsStore(this), scope)
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { Reminders.schedule(this) }
        // Widget aktualisieren, sobald sich angezeigte Daten ändern
        scope.launch {
            repository.data.filterNotNull()
                .map { de.fembiond.app.widget.WidgetState.from(it) }
                .distinctUntilChanged()
                .debounce(800)
                .collect { de.fembiond.app.widget.CycleWidget.refresh(this@FembiondApp) }
        }
    }
}
