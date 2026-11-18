package de.fembiond.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fembiond.app.FembiondApp
import de.fembiond.app.data.AppData
import de.fembiond.app.data.Repository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repository by lazy { (app as FembiondApp).repository }
    val data: StateFlow<AppData?> get() = repo.data

    fun run(block: suspend Repository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }
}
