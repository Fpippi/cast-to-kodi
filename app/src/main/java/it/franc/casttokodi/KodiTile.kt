package it.franc.casttokodi

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlin.concurrent.thread

// Riquadro "Couch Cast" delle impostazioni rapide (2026-10-27): mostra cosa fa Kodi e al tocco mette in pausa o fa
// ripartire. Con Kodi fermo o spento il tocco apre l'app. La pressione lunga apre il telecomando (intent
// QS_TILE_PREFERENCES dell'Activity). Android lo tiene sveglio solo mentre la tendina è aperta: a tendina chiusa non
// costa niente.
class KodiTile : TileService() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var playing: NowPlaying? = null
    @Volatile private var listening = false

    override fun onStartListening() {
        super.onStartListening()
        KodiAccess.useLanguage(this)
        listening = true
        refresh()
    }

    override fun onStopListening() {
        listening = false
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val id = playing?.playerId
        if (id == null) return openApp()
        thread {
            KodiAccess.quietKodi(this)?.let { kodi -> runCatching { kodi.playPause(id) } }
            Thread.sleep(400)
            refresh()
        }
    }

    private fun refresh() {
        thread {
            val read = runCatching { KodiAccess.quietKodi(this)?.nowPlaying() }.getOrNull()
            playing = read
            val text = read?.takeIf { it.playerId != null }?.let {
                val prefs = KodiAccess.prefs(this)
                mediaText(it, KodiAccess.shows(prefs), KodiAccess.titles(prefs), prefs.getString("name", "").orEmpty())
            }
            main.post { draw(read, text) }
        }
    }

    private fun draw(read: NowPlaying?, text: MediaText?) {
        val tile = qsTile ?: return
        if (!listening) return
        val video = read?.playerId != null
        tile.label = "Couch Cast"
        tile.subtitle = tileSubtitle(read, text)
        tile.state = if (video) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, when {
            read == null -> R.drawable.ic_power
            !video -> R.drawable.ic_cast
            read.paused -> R.drawable.ic_play
            else -> R.drawable.ic_pause
        })
        tile.contentDescription = if (video) tr("Couch Cast: pausa o riprendi su Kodi", "Couch Cast: pause or resume on Kodi") else tr("Couch Cast: apri l'app", "Couch Cast: open the app")
        tile.updateTile()
    }

    // Kodi fermo o spento: l'app, dove c'è ⏻ e il resto
    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_REMOTE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                startActivityAndCollapse(intent)
            }
        }
    }
}
