package it.franc.casttokodi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

// Intent dal widget all'Activity: riprendere una serie (EXTRA_SHOW, EXTRA_AFTER = l'episodio dopo), accendere la TV,
// tornare alla pagina iniziale
const val ACTION_WIDGET_RESUME = "it.franc.casttokodi.WIDGET_RESUME"
const val ACTION_WIDGET_WAKE = "it.franc.casttokodi.WIDGET_WAKE"
const val ACTION_WIDGET_HOME = "it.franc.casttokodi.WIDGET_HOME"
// Dalla scorciatoia "Coda di Kodi" dell'icona: il telecomando sulla scheda Coda
const val ACTION_OPEN_QUEUE = "it.franc.casttokodi.QUEUE"
const val EXTRA_SHOW = "show"
const val EXTRA_AFTER = "after"

// Widget "Couch Cast" sulla schermata Home (2026-10-28, mockup approvato da Pippo). Con Kodi fermo: la serie da
// riprendere con Riprendi su Kodi, l'episodio dopo, Altre serie e ⏻; con un video in corso: titolo, tempo, Salta sigla,
// −10 s, play/pausa, +10 s e il telecomando. Cosa mostrare lo decide widgetContent (Widget.kt).
// Per la batteria non legge Kodi da solo, se non ogni 30 minuti: lo ridisegnano la barra di Kodi dell'app e il player
// della tendina a ogni loro lettura (refresh), quindi con lo schermo spento resta com'era.
class KodiWidget : AppWidgetProvider() {

    companion object {
        private const val PLAY = "it.franc.casttokodi.WIDGET_PLAY"
        private const val BACK = "it.franc.casttokodi.WIDGET_BACK"
        private const val FORWARD = "it.franc.casttokodi.WIDGET_FORWARD"
        private const val INTRO = "it.franc.casttokodi.WIDGET_INTRO"

        // L'ultima lettura di Kodi (null = non risponde), per ridisegnare quando cambiano solo le serie
        @Volatile private var last: NowPlaying? = null
        @Volatile private var drawnKey: String? = null
        @Volatile private var drawnAt = 0L

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, KodiWidget::class.java))

        // Una lettura nuova di Kodi: si ridisegna se è cambiato qualcosa, o ogni WIDGET_EVERY per il tempo che passa.
        // Da un thread qualsiasi; non fa niente se il widget non è sulla Home
        fun refresh(context: Context, playing: NowPlaying?, force: Boolean = false) {
            last = playing
            val ids = runCatching { ids(context) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val content = content(context, playing)
            val key = widgetKey(content)
            val now = System.currentTimeMillis()
            if (!force && key == drawnKey && now - drawnAt < WIDGET_EVERY) return
            drawnKey = key
            drawnAt = now
            val views = draw(context, content)
            runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
        }

        // Le serie sono cambiate (invio, punto salvato, serie tolta): si ridisegna con l'ultima lettura di Kodi
        fun showsChanged(context: Context) = thread { refresh(context, last, force = true) }

        private fun content(context: Context, playing: NowPlaying?): WidgetContent {
            val prefs = KodiAccess.prefs(context)
            val hosts = JSONObject(prefs.getString("siteHosts", "{}")!!).let { json -> json.keys().asSequence().associateWith { json.getString(it) } }
            return widgetContent(
                kodiChosen = !prefs.getString("host", "").isNullOrBlank(),
                playing = playing,
                shows = if (prefs.getBoolean("history", true)) KodiAccess.shows(prefs) else emptyList(),
                titles = KodiAccess.titles(prefs),
                kodiName = prefs.getString("name", "").orEmpty(),
                generalIntro = if (prefs.contains("intro")) prefs.getInt("intro", DEFAULT_INTRO) else null,
                hosts = hosts,
                nowSeconds = System.currentTimeMillis() / 1000,
                autoDefault = KodiAccess.aniSkipOn(prefs),
            )
        }

        // Quattro misure: stretta o larga, bassa o alta. Android sceglie la più grande che ci sta
        private fun draw(context: Context, content: WidgetContent): RemoteViews = RemoteViews(mapOf(
            SizeF(110f, 110f) to views(context, content, wide = false, tall = false),
            SizeF(250f, 110f) to views(context, content, wide = true, tall = false),
            SizeF(110f, 180f) to views(context, content, wide = false, tall = true),
            SizeF(250f, 180f) to views(context, content, wide = true, tall = true),
        ))

        private fun views(context: Context, content: WidgetContent, wide: Boolean, tall: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget)
            fun color(id: Int) = context.getColor(id)
            fun text(view: Int, day: Int, night: Int) = v.setColorInt(view, "setTextColor", color(day), color(night))
            fun fill(view: Int, day: Int, night: Int) =
                v.setColorStateList(view, "setBackgroundTintList", ColorStateList.valueOf(color(day)), ColorStateList.valueOf(color(night)))
            fun tint(view: Int, day: Int, night: Int) =
                v.setColorStateList(view, "setImageTintList", ColorStateList.valueOf(color(day)), ColorStateList.valueOf(color(night)))
            // Colori come il resto dell'app: superficie, testo, pulsante principale (accento) e secondari
            fun primary(view: Int) {
                fill(view, android.R.color.system_accent1_600, android.R.color.system_accent1_200)
                text(view, android.R.color.system_accent1_0, android.R.color.system_accent1_800)
            }
            fun secondary(view: Int) {
                fill(view, android.R.color.system_accent2_100, android.R.color.system_accent2_700)
                text(view, android.R.color.system_accent2_900, android.R.color.system_accent2_50)
            }
            fill(R.id.widget_root, android.R.color.system_neutral1_50, android.R.color.system_neutral1_900)
            listOf(R.id.title, R.id.playing_title).forEach { text(it, android.R.color.system_neutral1_900, android.R.color.system_neutral1_100) }
            listOf(R.id.note, R.id.playing_status).forEach { text(it, android.R.color.system_neutral2_700, android.R.color.system_neutral2_200) }

            val playing = content is WidgetContent.Playing
            v.setViewVisibility(R.id.resume, if (playing) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.playing, if (playing) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.widget_root, activity(context, 0, ACTION_WIDGET_HOME))

            when (content) {
                is WidgetContent.Playing -> {
                    v.setTextViewText(R.id.playing_title, content.title)
                    v.setTextViewText(R.id.playing_status, content.status)
                    v.setProgressBar(R.id.progress, 1000, content.progress, false)
                    v.setColorStateList(R.id.progress, "setProgressTintList",
                        ColorStateList.valueOf(color(android.R.color.system_accent1_600)), ColorStateList.valueOf(color(android.R.color.system_accent1_200)))
                    v.setColorStateList(R.id.progress, "setProgressBackgroundTintList",
                        ColorStateList.valueOf(color(android.R.color.system_neutral2_200)), ColorStateList.valueOf(color(android.R.color.system_neutral2_700)))
                    val picture = bitmap(context, content.picture, 160)
                    if (picture != null) {
                        v.setImageViewBitmap(R.id.thumb, picture)
                    } else {
                        // Senza immagine: ▶ su un quadrato colorato, come la barra di Kodi dell'app
                        val pad = (14 * context.resources.displayMetrics.density).toInt()
                        v.setImageViewResource(R.id.thumb, R.drawable.ic_play)
                        v.setViewPadding(R.id.thumb, pad, pad, pad, pad)
                        tint(R.id.thumb, android.R.color.system_accent1_50, android.R.color.system_accent1_100)
                    }
                    fill(R.id.thumb, android.R.color.system_accent1_400, android.R.color.system_accent1_600)
                    v.setViewVisibility(R.id.thumb, if (wide || tall) View.VISIBLE else View.GONE)

                    v.setImageViewResource(R.id.play, if (content.paused) R.drawable.ic_play else R.drawable.ic_pause)
                    v.setContentDescription(R.id.play, if (content.paused) tr("Riprendi", "Play") else tr("Pausa", "Pause"))
                    fill(R.id.play, android.R.color.system_accent1_600, android.R.color.system_accent1_200)
                    tint(R.id.play, android.R.color.system_accent1_0, android.R.color.system_accent1_800)
                    listOf(R.id.back, R.id.forward).forEach { tint(it, android.R.color.system_neutral1_900, android.R.color.system_neutral1_100) }
                    v.setContentDescription(R.id.back, tr("Indietro di $MEDIA_SKIP secondi", "Back $MEDIA_SKIP seconds"))
                    v.setContentDescription(R.id.forward, tr("Avanti di $MEDIA_SKIP secondi", "Forward $MEDIA_SKIP seconds"))
                    fill(R.id.remote, android.R.color.system_accent2_100, android.R.color.system_accent2_700)
                    tint(R.id.remote, android.R.color.system_accent2_900, android.R.color.system_accent2_50)
                    v.setContentDescription(R.id.remote, tr("Apri il telecomando", "Open the remote"))
                    // Salta sigla solo se il tempo è già scelto: la prima volta si sceglie dal telecomando, come nella tendina
                    v.setTextViewText(R.id.intro, content.intro ?: tr("Salta sigla", "Skip intro"))
                    secondary(R.id.intro)
                    v.setViewVisibility(R.id.intro, if (wide && content.intro != null) View.VISIBLE else View.GONE)
                    listOf(R.id.back, R.id.forward).forEach { v.setViewVisibility(it, if (wide) View.VISIBLE else View.GONE) }
                    v.setViewVisibility(R.id.remote, if (wide) View.VISIBLE else View.GONE)

                    v.setOnClickPendingIntent(R.id.play, broadcast(context, 1, PLAY))
                    v.setOnClickPendingIntent(R.id.back, broadcast(context, 2, BACK))
                    v.setOnClickPendingIntent(R.id.forward, broadcast(context, 3, FORWARD))
                    v.setOnClickPendingIntent(R.id.intro, broadcast(context, 4, INTRO))
                    v.setOnClickPendingIntent(R.id.remote, activity(context, 5, ACTION_OPEN_REMOTE))
                    v.setOnClickPendingIntent(R.id.widget_root, activity(context, 5, ACTION_OPEN_REMOTE))
                }
                is WidgetContent.Resume -> {
                    v.setTextViewText(R.id.title, content.title)
                    v.setTextViewText(R.id.note, content.note)
                    v.setTextViewText(R.id.action, content.action)
                    primary(R.id.action)
                    val picture = if (tall) bitmap(context, content.picture, 720) else null
                    if (picture != null) v.setImageViewBitmap(R.id.picture, picture)
                    v.setViewVisibility(R.id.picture, if (picture != null) View.VISIBLE else View.GONE)
                    v.setOnClickPendingIntent(R.id.action, activity(context, 6, ACTION_WIDGET_RESUME, content.key, after = false))
                    v.setTextViewText(R.id.after, content.after.orEmpty())
                    secondary(R.id.after)
                    v.setViewVisibility(R.id.after, if (content.after != null) View.VISIBLE else View.GONE)
                    v.setOnClickPendingIntent(R.id.after, activity(context, 7, ACTION_WIDGET_RESUME, content.key, after = true))
                    others(context, v, ::secondary, ::fill, ::tint)
                    v.setViewVisibility(R.id.resume_more, if (wide || tall) View.VISIBLE else View.GONE)
                    // Stretto: solo il nome e il pulsante
                    if (!wide) v.setViewVisibility(R.id.note, View.GONE)
                }
                WidgetContent.Idle, WidgetContent.NoKodi -> {
                    v.setTextViewText(R.id.title, "Couch Cast")
                    v.setTextViewText(R.id.note,
                        if (content == WidgetContent.NoKodi) tr("Scegli Kodi nell'app", "Choose Kodi in the app")
                        else tr("Niente da riprendere", "Nothing to resume"))
                    v.setViewVisibility(R.id.picture, View.GONE)
                    v.setTextViewText(R.id.action, tr("Apri", "Open"))
                    primary(R.id.action)
                    v.setOnClickPendingIntent(R.id.action, activity(context, 0, ACTION_WIDGET_HOME))
                    v.setViewVisibility(R.id.after, View.GONE)
                    others(context, v, ::secondary, ::fill, ::tint)
                    v.setViewVisibility(R.id.resume_more, if (content == WidgetContent.Idle && (wide || tall)) View.VISIBLE else View.GONE)
                }
            }
            return v
        }

        // Altre serie (la pagina iniziale) e ⏻
        private fun others(context: Context, v: RemoteViews, secondary: (Int) -> Unit, fill: (Int, Int, Int) -> Unit, tint: (Int, Int, Int) -> Unit) {
            v.setTextViewText(R.id.others, tr("Altre serie", "Other series"))
            secondary(R.id.others)
            v.setOnClickPendingIntent(R.id.others, activity(context, 0, ACTION_WIDGET_HOME))
            fill(R.id.power, android.R.color.system_accent2_100, android.R.color.system_accent2_700)
            tint(R.id.power, android.R.color.system_accent2_900, android.R.color.system_accent2_50)
            v.setContentDescription(R.id.power, tr("Accendi la TV e apri Kodi", "Turn on the TV and open Kodi"))
            v.setOnClickPendingIntent(R.id.power, activity(context, 8, ACTION_WIDGET_WAKE))
        }

        private fun activity(context: Context, code: Int, action: String, show: String? = null, after: Boolean = false): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (show != null) intent.putExtra(EXTRA_SHOW, show).putExtra(EXTRA_AFTER, after)
            return PendingIntent.getActivity(context, 100 + code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun broadcast(context: Context, code: Int, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, 100 + code, Intent(context, KodiWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // L'immagine della serie, rimpicciolita: il widget passa da un altro processo e le immagini grandi non ci stanno
        private fun bitmap(context: Context, name: String?, width: Int): Bitmap? {
            val file = name?.let { File(KodiAccess.framesDir(context), it) }?.takeIf { it.isFile } ?: return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= width) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }

        // Una lettura di Kodi senza chiedere niente; null se non risponde
        private fun read(context: Context): NowPlaying? = runCatching { KodiAccess.quietKodi(context)?.nowPlaying() }.getOrNull()
    }

    // Widget messo sulla Home, o il giro dei 30 minuti: una lettura di Kodi
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        KodiAccess.useLanguage(context)
        val pending = goAsync()
        thread {
            try {
                refresh(context, read(context), force = true)
            } finally {
                pending.finish()
            }
        }
    }

    // Tasti del piccolo telecomando: il comando a Kodi da qui, senza aprire l'app
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(PLAY, BACK, FORWARD, INTRO)) return
        KodiAccess.useLanguage(context)
        val pending = goAsync()
        thread {
            try {
                val kodi = KodiAccess.quietKodi(context)
                val playing = runCatching { kodi?.nowPlaying() }.getOrNull()
                val id = playing?.playerId
                if (kodi != null && id != null) {
                    runCatching {
                        when (action) {
                            PLAY -> kodi.playPause(id)
                            BACK -> kodi.skip(id, -MEDIA_SKIP)
                            FORWARD -> kodi.skip(id, MEDIA_SKIP)
                            INTRO -> {
                                val prefs = KodiAccess.prefs(context)
                                val general = if (prefs.contains("intro")) prefs.getInt("intro", DEFAULT_INTRO) else null
                                KodiAccess.fetchSkips(prefs, playing.file)
                                when (val press = skipPressOf(KodiAccess.shows(prefs), playing.file, playing.time, playing.total, general, KodiAccess.aniSkipOn(prefs))) {
                                    is SkipPress.To -> kodi.seekTo(id, press.seconds)
                                    is SkipPress.Forward -> kodi.skip(id, press.seconds)
                                    else -> Unit
                                }
                            }
                        }
                    }
                    Thread.sleep(400)
                }
                refresh(context, read(context), force = true)
                // Con un video in corso parte anche il player della tendina, che tiene aggiornato il widget
                if (id != null) KodiPlayer.startIfWanted(context)
            } finally {
                pending.finish()
            }
        }
    }
}
