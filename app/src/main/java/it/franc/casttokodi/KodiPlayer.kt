package it.franc.casttokodi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import java.io.File
import kotlin.concurrent.thread

// Intent che apre l'app sul telecomando (tocco sul player, pressione lunga sul riquadro)
const val ACTION_OPEN_REMOTE = "it.franc.casttokodi.REMOTE"

// Player di Android per Kodi (2026-10-27): il riquadro con immagine, tempo e tasti nella tendina e sulla schermata di
// blocco, quello delle app di musica. È un servizio in primo piano con una MediaSession: Android 13 mostra i suoi tasti
// (Salta sigla, −10 s, pausa, +10 s, Prossimo) e non chiede il permesso delle notifiche per questo tipo di notifica.
// Per la batteria: con lo schermo spento non chiede niente a Kodi; si chiude da solo quando Kodi si ferma o non risponde
// per un minuto. Lo avvia l'Activity quando vede un video in corso (startIfWanted).
class KodiPlayer : Service() {

    companion object {
        private const val CHANNEL = "kodi"
        private const val NOTIFICATION = 1
        private const val INTRO = "intro"
        private const val BACK = "back"
        private const val FORWARD = "forward"

        @Volatile
        var running = false
            private set

        // "Controlli nella tendina" delle Impostazioni, acceso di default
        fun enabled(context: Context) = KodiAccess.prefs(context).getBoolean("mediaControls", true)

        fun startIfWanted(context: Context) {
            if (running || !enabled(context)) return
            running = true
            runCatching { context.startForegroundService(Intent(context, KodiPlayer::class.java)) }.onFailure { running = false }
        }

        fun stop(context: Context) = context.stopService(Intent(context, KodiPlayer::class.java))
    }

    private lateinit var session: MediaSession
    private val power by lazy { getSystemService(PowerManager::class.java) }
    @Volatile private var alive = true
    @Volatile private var now: NowPlaying? = null
    @Volatile private var buttons = MediaButtons(null, false)
    private var lastImage: String? = null
    private var lastPicture: android.graphics.Bitmap? = null

    // Allo schermo acceso si rilegge subito, senza aspettare il giro
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) thread { read() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        KodiAccess.useLanguage(this)
        running = true
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Kodi in riproduzione", "Kodi playing"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Tasti di Kodi nella tendina e sulla schermata di blocco", "Kodi buttons in the notification shade and on the lock screen")
                setShowBadge(false)
            }
        )
        session = MediaSession(this, "Couch Cast").apply {
            setSessionActivity(openApp())
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = command { kodi, id -> kodi.playPause(id) }
                override fun onPause() = command { kodi, id -> kodi.playPause(id) }
                override fun onSkipToNext() = command { kodi, id -> kodi.next(id) }
                override fun onSeekTo(position: Long) = command { kodi, id -> kodi.seekTo(id, position / 1000) }
                override fun onCustomAction(action: String, extras: android.os.Bundle?) = when (action) {
                    INTRO -> buttons.intro?.let { seconds -> command { kodi, id -> kodi.skip(id, seconds) } } ?: Unit
                    BACK -> command { kodi, id -> kodi.skip(id, -MEDIA_SKIP) }
                    FORWARD -> command { kodi, id -> kodi.skip(id, MEDIA_SKIP) }
                    else -> Unit
                }
            })
            isActive = true
        }
        startForeground(NOTIFICATION, notification(null, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        registerReceiver(screen, IntentFilter(Intent.ACTION_SCREEN_ON))
        thread {
            var lastAnswer = System.currentTimeMillis()
            while (alive) {
                if (power?.isInteractive != false) {
                    val answered = read()
                    if (answered) lastAnswer = System.currentTimeMillis()
                    else if (System.currentTimeMillis() - lastAnswer > MEDIA_GIVE_UP) break
                    if (answered && now?.playerId == null) break
                } else {
                    // Schermo spento: niente richieste, e il minuto senza risposta riparte quando si riaccende
                    lastAnswer = System.currentTimeMillis()
                }
                Thread.sleep(MEDIA_EVERY)
            }
            if (alive) stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        alive = false
        running = false
        runCatching { unregisterReceiver(screen) }
        session.release()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_REMOTE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Una lettura di Kodi; false se non risponde
    private fun read(): Boolean {
        val kodi = KodiAccess.quietKodi(this) ?: return false
        val playing = try {
            kodi.nowPlaying()
        } catch (e: Exception) {
            return false
        }
        now = playing
        KodiWidget.refresh(this, playing)
        if (playing.playerId == null) return true
        val state = runCatching { kodi.state() }.getOrNull()
        val prefs = KodiAccess.prefs(this)
        val shows = KodiAccess.shows(prefs)
        KodiAccess.autoSubtitles(prefs, kodi, playing)
        val general = if (prefs.contains("intro")) prefs.getInt("intro", DEFAULT_INTRO) else null
        buttons = mediaButtons(playing, state, shows, general)
        val text = mediaText(playing, shows, KodiAccess.titles(prefs), prefs.getString("name", "").orEmpty())
        // Con il timer di spegnimento attivo, la sua ora (anche sulla schermata di blocco)
        val timer = prefs.getLong("timerAt", 0).takeIf { it > System.currentTimeMillis() / 1000 }
        show(playing, timer?.let { MediaText(text.title, "${text.subtitle} · timer ${clockAt(it)}") } ?: text, pictureOfFile(shows, playing.file))
        return true
    }

    private fun show(playing: NowPlaying, text: MediaText, image: String?) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, text.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, text.subtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, playing.total * 1000)
        // L'immagine si rilegge dal disco solo quando cambia
        if (image != lastImage) {
            lastImage = image
            lastPicture = image?.let { File(KodiAccess.framesDir(this), it) }?.takeIf { it.isFile }
                ?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
        }
        lastPicture?.let { metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
        session.setMetadata(metadata.build())

        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO
        if (buttons.next) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        val state = PlaybackState.Builder()
            .setActions(actions)
            .setState(if (playing.paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, playing.time * 1000, if (playing.paused) 0f else 1f)
        if (buttons.intro != null) state.addCustomAction(INTRO, tr("Salta sigla", "Skip intro"), R.drawable.ic_skip_intro)
        state.addCustomAction(BACK, tr("Indietro di $MEDIA_SKIP secondi", "Back $MEDIA_SKIP seconds"), R.drawable.ic_replay)
        state.addCustomAction(FORWARD, tr("Avanti di $MEDIA_SKIP secondi", "Forward $MEDIA_SKIP seconds"), R.drawable.ic_forward)
        session.setPlaybackState(state.build())
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION, notification(text, playing))
    }

    private fun notification(text: MediaText?, playing: NowPlaying?): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_cast)
            .setContentTitle(text?.title ?: "Kodi")
            .setContentText(text?.subtitle ?: tr("Collegamento a Kodi…", "Connecting to Kodi…"))
            .setContentIntent(openApp())
            .setOngoing(playing?.paused != true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()

    // Un comando dai tasti: subito a Kodi, poi una lettura per aggiornare il player
    private fun command(action: (Kodi, Int) -> Unit) {
        thread {
            val id = now?.playerId ?: return@thread
            val kodi = KodiAccess.quietKodi(this) ?: return@thread
            runCatching { action(kodi, id) }
            Thread.sleep(400)
            read()
        }
    }
}
