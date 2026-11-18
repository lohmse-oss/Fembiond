package de.fembiond.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.fembiond.app.domain.CycleEngine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Stündliche Prüfung; zur eingestellten Uhrzeit wird höchstens einmal täglich erinnert:
 * an fällige Einnahmen (zyklusbezogen) und an den GAD-7-Check.
 */
object Reminders {
    private const val CHANNEL = "reminders"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun notify(context: Context, id: Int, title: String, text: String, route: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FembiondApp
        val data = withTimeoutOrNull(20_000) { app.repository.data.filterNotNull().first() } ?: return Result.success()
        de.fembiond.app.widget.CycleWidget.refresh(applicationContext)
        val s = data.settings
        if (!s.reminderEnabled || !s.onboardingDone || LocalTime.now().hour != s.reminderHour) return Result.success()
        val prefs = applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (prefs.getLong("last", -1) == today.toEpochDay()) return Result.success()
        prefs.edit().putLong("last", today.toEpochDay()).apply()

        data.medications.filter { CycleEngine.intakeDue(it, data.summary, today) && (it.id to today) !in data.intakes }.forEach { m ->
            Reminders.notify(applicationContext, 1000 + m.id.toInt(), "Einnahme: ${m.name}", "Heute laut Schema fällig (${m.scheduleLabel()}). Zum Abhaken antippen.", Routes.HOME)
        }
        val risks = de.fembiond.app.domain.StressForecast.upcoming(data.summary, data.events, data.episodes, data.occurrences, today, 1)
        data.events.filter { it.date == today.plusDays(1) }.forEach { e ->
            val risk = risks.firstOrNull { it.event.id == e.id }
            val text = if (risk != null) "Gut vorbereiten: ${risk.factors.first()}. ${risk.tips.first()}."
            else "Plane Pausen ein und nimm dir mit, was dir sonst hilft. Unruhe kannst du direkt in der App festhalten."
            Reminders.notify(applicationContext, 3000 + e.id.toInt(), "Morgen: ${e.displayName}", text, Routes.EVENTS)
        }
        if (s.weeklyReview && de.fembiond.app.domain.WeeklyReviewBuilder.isReviewDay(today)) {
            val review = de.fembiond.app.domain.WeeklyReviewBuilder.build(data.summary, data.occurrences, data.episodes, data.events, today)
            val name = data.profile?.name?.let { "$it, dein" } ?: "Dein"
            Reminders.notify(applicationContext, 4000, "$name Wochenrückblick", de.fembiond.app.domain.WeeklyReviewBuilder.shortText(review), Routes.WEEKLY)
        }
        if (data.mrsDue && data.occurrences.size > 3) {
            Reminders.notify(applicationContext, 2001, "Menopause-Fragebogen", "Die MRS-Fragen sind wieder dran (11 kurze Fragen).", Routes.MRS)
        }
        if (data.gadDue && data.occurrences.size > 3) {
            Reminders.notify(applicationContext, 2000, "Kurzer Angst-Check", "Der GAD-7-Fragebogen ist wieder dran (7 Fragen).", Routes.GAD)
        }
        return Result.success()
    }
}
