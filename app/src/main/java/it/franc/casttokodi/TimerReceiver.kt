package it.franc.casttokodi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

// Timer di spegnimento tenuto dal telefono, quando la Fire TV non risponde in ADB (vedi SleepTimer.kt): allo scadere
// Kodi va in pausa o si ferma via JSON-RPC. Senza ADB la TV non si può spegnere, quindi "Spegni la TV" ferma soltanto.
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = KodiAccess.prefs(context)
        KodiAccess.useLanguage(context)
        // Un timer annullato o sostituito non fa niente
        if (intent.getLongExtra("token", 0) != prefs.getLong("timerToken", -1)) return
        prefs.edit().remove("timerAt").remove("timerToken").remove("timerOnPhone").apply()
        val action = timerAction(intent.getStringExtra("action"))
        val pending = goAsync()
        thread {
            try {
                val kodi = KodiAccess.quietKodi(context) ?: return@thread
                val playing = kodi.nowPlaying()
                val id = playing.playerId ?: return@thread
                if (action == TimerAction.PAUSE) {
                    if (!playing.paused) kodi.playPause(id)
                } else {
                    kodi.stop(id)
                }
            } catch (e: Exception) {
                // Kodi spento o fuori rete: niente da fermare
            } finally {
                pending.finish()
            }
        }
    }
}
