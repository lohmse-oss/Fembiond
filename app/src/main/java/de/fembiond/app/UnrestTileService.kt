package de.fembiond.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Kachel in den Schnelleinstellungen: öffnet direkt „Unruhe erfassen“. */
class UnrestTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Unruhe erfassen"
            if (Build.VERSION.SDK_INT >= 29) subtitle = "Fembiond"
            updateTile()
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // nur unter Android 14 genutzt
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, Routes.unrest())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 77, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
