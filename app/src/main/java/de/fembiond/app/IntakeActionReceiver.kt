package de.fembiond.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Aktion „Eingenommen“ direkt aus der Erinnerung. */
class IntakeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phaseId = intent.getLongExtra(EXTRA_PHASE, -1)
        val day = intent.getLongExtra(EXTRA_DAY, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        if (phaseId < 0 || day < 0) return
        val app = context.applicationContext as FembiondApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.setIntake(phaseId, LocalDate.ofEpochDay(day), true)
                NotificationManagerCompat.from(context).cancel(notificationId)
                de.fembiond.app.widget.CycleWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PHASE = "phaseId"
        const val EXTRA_DAY = "day"
        const val EXTRA_NOTIFICATION = "notificationId"
    }
}
