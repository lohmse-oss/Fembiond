package de.fembiond.app

import android.app.Application
import de.fembiond.app.data.FembiondDatabase
import de.fembiond.app.data.Repository
import de.fembiond.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FembiondApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository: Repository by lazy {
        Repository(FembiondDatabase.create(this).dao(), SettingsStore(this), scope)
    }

    override fun onCreate() {
        super.onCreate()
        Reminders.schedule(this)
    }
}
