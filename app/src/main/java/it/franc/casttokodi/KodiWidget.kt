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
// ⏭ del widget a coda finita: l'episodio dopo quello in corso, come ⏭ del telecomando
const val ACTION_WIDGET_NEXT = "it.franc.casttokodi.WIDGET_NEXT"
// Dalla scorciatoia "Coda di Kodi" dell'icona: il telecomando sulla scheda Coda
const val ACTION_OPEN_QUEUE = "it.franc.casttokodi.QUEUE"
const val EXTRA_SHOW = "show"
const val EXTRA_AFTER = "after"

// Widget "Couch Cast" sulla schermata Home (2026-10-28, rifatto il 2026-10-29 con i tasti del telecomando: mockup
// approvato da Pippo). Sempre la fila ⏮ −10 ⏯ +10 ⏭ ⏹ e Salta sigla; con un video in corso comandano Kodi, con Kodi
// fermo il tasto grande riprende la serie, ⏭ manda l'episodio dopo e gli altri sono sbiaditi. Cosa mostrare lo decide
// widgetContent (Widget.kt).
// Per la batteria non legge Kodi da solo, se non ogni 30 minuti: lo ridisegnano la barra di Kodi dell'app e il player
// della tendina a ogni loro lettura (refresh), quindi con lo schermo spento resta com'era.
class KodiWidget : AppWidgetProvider() {

    companion object {
        private const val PLAY = "it.franc.casttokodi.WIDGET_PLAY"
        private const val BACK = "it.franc.casttokodi.WIDGET_BACK"
        private const val FORWARD = "it.franc.casttokodi.WIDGET_FORWARD"
        private const val INTRO = "it.franc.casttokodi.WIDGET_INTRO"
        private const val PREVIOUS = "it.franc.casttokodi.WIDGET_PREVIOUS"
        private const val NEXT = "it.franc.casttokodi.WIDGET_NEXT_IN_QUEUE"
        private const val STOP = "it.franc.casttokodi.WIDGET_STOP"

        // L'ultima lettura di Kodi (null = non risponde), per ridisegnare quando cambiano solo le serie
        @Volatile private var last: NowPlaying? = null
        // Se c'è altro dopo nella coda, per il file che Kodi riproduceva quando l'ha letto (⏭ = episodio dopo se no)
        @Volatile private var hasNext: Pair<String, Boolean>? = null
        @Volatile private var drawnKey: String? = null
        @Volatile private var drawnAt = 0L

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, KodiWidget::class.java))

        // Una lettura nuova di Kodi: si ridisegna se è cambiato qualcosa, o ogni WIDGET_EVERY per il tempo che passa.
        // state: la coda, se chi chiama l'ha letta. Da un thread qualsiasi; non fa niente se il widget non è sulla Home
        fun refresh(context: Context, playing: NowPlaying?, force: Boolean = false, state: KodiState? = null) {
            last = playing
            if (state != null && playing?.playerId != null) hasNext = playing.file to state.hasNext
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
            val next = hasNext?.takeIf { it.first == playing?.file }?.second
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
                hasNext = next,
                autoDefault = KodiAccess.aniSkipOn(prefs),
            )
        }

        // Sei misure: stretta o larga, bassa (una riga, 4×1), media (4×2) o alta. Android sceglie la più grande che ci sta
        private fun draw(context: Context, content: WidgetContent): RemoteViews = RemoteViews(mapOf(
            SizeF(110f, 50f) to views(context, content, wide = false, size = LOW),
            SizeF(250f, 50f) to views(context, content, wide = true, size = LOW),
            SizeF(110f, 110f) to views(context, content, wide = false, size = MEDIUM),
            SizeF(250f, 110f) to views(context, content, wide = true, size = MEDIUM),
            SizeF(110f, 170f) to views(context, content, wide = false, size = TALL),
            SizeF(250f, 170f) to views(context, content, wide = true, size = TALL),
        ))

        private const val LOW = 0
        private const val MEDIUM = 1
        private const val TALL = 2

        private fun views(context: Context, content: WidgetContent, wide: Boolean, size: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget)
            fun color(id: Int) = context.getColor(id)
            fun text(view: Int, day: Int, night: Int) = v.setColorInt(view, "setTextColor", color(day), color(night))
            fun fill(view: Int, day: Int, night: Int) =
                v.setColorStateList(view, "setBackgroundTintList", ColorStateList.valueOf(color(day)), ColorStateList.valueOf(color(night)))
            fun tint(view: Int, day: Int, night: Int) =
                v.setColorStateList(view, "setImageTintList", ColorStateList.valueOf(color(day)), ColorStateList.valueOf(color(night)))
            fun show(view: Int, visible: Boolean) = v.setViewVisibility(view, if (visible) View.VISIBLE else View.GONE)
            // Colori come il resto dell'app: superficie, testo, tasto grande con l'accento, pillole secondarie. Spento =
            // un tasto che con Kodi fermo non fa niente (−10, +10, ⏹), sbiadito come nel mockup
            fun icon(view: Int, on: Boolean) =
                if (on) tint(view, android.R.color.system_neutral1_900, android.R.color.system_neutral1_100)
                else tint(view, android.R.color.system_neutral2_300, android.R.color.system_neutral2_600)
            fun label(view: Int, on: Boolean) =
                if (on) text(view, android.R.color.system_neutral1_900, android.R.color.system_neutral1_100)
                else text(view, android.R.color.system_neutral2_300, android.R.color.system_neutral2_600)
            fun pill(view: Int) = fill(view, android.R.color.system_accent2_100, android.R.color.system_accent2_700)
            fill(R.id.widget_root, android.R.color.system_neutral1_50, android.R.color.system_neutral1_900)
            listOf(R.id.title, R.id.line).forEach { text(it, android.R.color.system_neutral1_900, android.R.color.system_neutral1_100) }
            text(R.id.status, android.R.color.system_neutral2_700, android.R.color.system_neutral2_200)
            fill(R.id.play, android.R.color.system_accent1_600, android.R.color.system_accent1_200)
            tint(R.id.play, android.R.color.system_accent1_0, android.R.color.system_accent1_800)
            listOf(R.id.corner, R.id.stop, R.id.intro, R.id.intro_top).forEach(::pill)
            tint(R.id.corner, android.R.color.system_accent2_900, android.R.color.system_accent2_50)
            v.setTextViewText(R.id.back, "−$MEDIA_SKIP")
            v.setTextViewText(R.id.forward, "+$MEDIA_SKIP")
            v.setContentDescription(R.id.back, tr("Indietro di $MEDIA_SKIP secondi", "Back $MEDIA_SKIP seconds"))
            v.setContentDescription(R.id.forward, tr("Avanti di $MEDIA_SKIP secondi", "Forward $MEDIA_SKIP seconds"))
            v.setContentDescription(R.id.stop, tr("Ferma Kodi", "Stop Kodi"))

            // Basso: una riga sola sopra la fila; medio e alto: immagine, nome e stato. Stretto: solo ⏮ ⏯ ⏭
            show(R.id.header, size != LOW)
            show(R.id.line, size == LOW)
            listOf(R.id.back, R.id.forward, R.id.stop).forEach { show(it, wide) }
            show(R.id.thumb, wide)
            show(R.id.corner, wide)
            val playing = content as? WidgetContent.Playing
            show(R.id.progress, playing != null && size != LOW)

            // Salta sigla: in alto a destra nel widget medio, in fondo in quello alto; con Kodi fermo c'è ma sbiadito
            val intro = when (content) {
                is WidgetContent.Playing -> content.intro
                is WidgetContent.Resume -> tr("Salta sigla", "Skip intro")
                else -> null
            }
            val introView = if (size == TALL) R.id.intro else R.id.intro_top
            show(R.id.intro, size == TALL && intro != null)
            show(R.id.intro_top, size == MEDIUM && wide && intro != null)
            v.setTextViewText(introView, intro.orEmpty())
            if (playing != null) {
                text(introView, android.R.color.system_accent2_900, android.R.color.system_accent2_50)
                v.setOnClickPendingIntent(introView, broadcast(context, 4, INTRO))
            } else {
                label(introView, false)
            }

            when (content) {
                is WidgetContent.Playing -> {
                    v.setTextViewText(R.id.title, content.title)
                    v.setTextViewText(R.id.status, content.status)
                    v.setTextViewText(R.id.line, "${content.title} · ${content.clock}")
                    v.setProgressBar(R.id.progress, 1000, content.progress, false)
                    v.setColorStateList(R.id.progress, "setProgressTintList",
                        ColorStateList.valueOf(color(android.R.color.system_accent1_600)), ColorStateList.valueOf(color(android.R.color.system_accent1_200)))
                    v.setColorStateList(R.id.progress, "setProgressBackgroundTintList",
                        ColorStateList.valueOf(color(android.R.color.system_neutral2_200)), ColorStateList.valueOf(color(android.R.color.system_neutral2_700)))
                    picture(context, v, content.picture, ::fill, ::tint)

                    v.setImageViewResource(R.id.play, if (content.paused) R.drawable.ic_play else R.drawable.ic_pause)
                    v.setContentDescription(R.id.play, if (content.paused) tr("Riprendi", "Play") else tr("Pausa", "Pause"))
                    listOf(R.id.previous, R.id.next, R.id.stop).forEach { icon(it, true) }
                    listOf(R.id.back, R.id.forward).forEach { label(it, true) }
                    v.setContentDescription(R.id.previous, tr("Video precedente della coda", "Previous video in the queue"))
                    v.setContentDescription(R.id.next,
                        if (content.nextEpisode) tr("Episodio dopo", "Next episode") else tr("Video successivo della coda", "Next video in the queue"))
                    v.setImageViewResource(R.id.corner, R.drawable.ic_remote)
                    v.setContentDescription(R.id.corner, tr("Apri il telecomando", "Open the remote"))

                    v.setOnClickPendingIntent(R.id.play, broadcast(context, 1, PLAY))
                    v.setOnClickPendingIntent(R.id.back, broadcast(context, 2, BACK))
                    v.setOnClickPendingIntent(R.id.forward, broadcast(context, 3, FORWARD))
                    v.setOnClickPendingIntent(R.id.previous, broadcast(context, 9, PREVIOUS))
                    v.setOnClickPendingIntent(R.id.stop, broadcast(context, 10, STOP))
                    // A coda finita ⏭ manda l'episodio dopo: serve il WebView dell'app, quindi si apre lei
                    v.setOnClickPendingIntent(R.id.next,
                        if (content.nextEpisode) activity(context, 11, ACTION_WIDGET_NEXT) else broadcast(context, 12, NEXT))
                    v.setOnClickPendingIntent(R.id.corner, activity(context, 5, ACTION_OPEN_REMOTE))
                    v.setOnClickPendingIntent(R.id.widget_root, activity(context, 5, ACTION_OPEN_REMOTE))
                }
                is WidgetContent.Resume -> {
                    // Kodi fermo: stessi tasti, il grande riprende la serie e ⏭ manda l'episodio dopo
                    v.setTextViewText(R.id.title, content.title)
                    v.setTextViewText(R.id.status, content.note)
                    v.setTextViewText(R.id.line, content.title)
                    picture(context, v, content.picture, ::fill, ::tint)
                    v.setImageViewResource(R.id.play, R.drawable.ic_play)
                    v.setContentDescription(R.id.play, content.action)
                    v.setOnClickPendingIntent(R.id.play, activity(context, 6, ACTION_WIDGET_RESUME, content.key, after = false))
                    icon(R.id.previous, false)
                    icon(R.id.next, content.after != null)
                    if (content.after != null) {
                        v.setContentDescription(R.id.next, tr("Episodio dopo: ${content.after}", "Next episode: ${content.after}"))
                        v.setOnClickPendingIntent(R.id.next, activity(context, 7, ACTION_WIDGET_RESUME, content.key, after = true))
                    }
                    listOf(R.id.back, R.id.forward).forEach { label(it, false) }
                    icon(R.id.stop, false)
                    power(context, v)
                    v.setOnClickPendingIntent(R.id.widget_root, activity(context, 0, ACTION_WIDGET_HOME))
                }
                WidgetContent.Idle, WidgetContent.NoKodi -> {
                    val note = if (content == WidgetContent.NoKodi) tr("Scegli Kodi nell'app", "Choose Kodi in the app")
                        else tr("Niente da riprendere", "Nothing to resume")
                    v.setTextViewText(R.id.title, "Couch Cast")
                    v.setTextViewText(R.id.status, note)
                    v.setTextViewText(R.id.line, "Couch Cast · $note")
                    picture(context, v, null, ::fill, ::tint)
                    v.setImageViewResource(R.id.play, R.drawable.ic_play)
                    v.setContentDescription(R.id.play, tr("Apri Couch Cast", "Open Couch Cast"))
                    v.setOnClickPendingIntent(R.id.play, activity(context, 0, ACTION_WIDGET_HOME))
                    listOf(R.id.previous, R.id.next, R.id.stop).forEach { icon(it, false) }
                    listOf(R.id.back, R.id.forward).forEach { label(it, false) }
                    if (content == WidgetContent.Idle) power(context, v) else show(R.id.corner, false)
                    v.setOnClickPendingIntent(R.id.widget_root, activity(context, 0, ACTION_WIDGET_HOME))
                }
            }
            return v
        }

        // L'immagine del video o della serie; senza, ▶ su un quadrato colorato come la barra di Kodi dell'app
        private fun picture(context: Context, v: RemoteViews, name: String?, fill: (Int, Int, Int) -> Unit, tint: (Int, Int, Int) -> Unit) {
            val picture = bitmap(context, name, 160)
            if (picture != null) {
                v.setImageViewBitmap(R.id.thumb, picture)
            } else {
                val pad = (11 * context.resources.displayMetrics.density).toInt()
                v.setImageViewResource(R.id.thumb, R.drawable.ic_play)
                v.setViewPadding(R.id.thumb, pad, pad, pad, pad)
                tint(R.id.thumb, android.R.color.system_accent1_50, android.R.color.system_accent1_100)
            }
            fill(R.id.thumb, android.R.color.system_accent1_400, android.R.color.system_accent1_600)
        }

        // ⏻ nell'angolo, con Kodi fermo
        private fun power(context: Context, v: RemoteViews) {
            v.setImageViewResource(R.id.corner, R.drawable.ic_power)
            v.setContentDescription(R.id.corner, tr("Accendi la TV e apri Kodi", "Turn on the TV and open Kodi"))
            v.setOnClickPendingIntent(R.id.corner, activity(context, 8, ACTION_WIDGET_WAKE))
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

        // Una lettura di Kodi senza chiedere niente (con la coda, per ⏭) e il widget ridisegnato
        private fun readAndDraw(context: Context) {
            val kodi = KodiAccess.quietKodi(context)
            val playing = runCatching { kodi?.nowPlaying() }.getOrNull()
            val state = if (playing?.playerId != null) runCatching { kodi?.state() }.getOrNull() else null
            refresh(context, playing, force = true, state = state)
        }
    }

    // Widget messo sulla Home, o il giro dei 30 minuti: una lettura di Kodi
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        KodiAccess.useLanguage(context)
        val pending = goAsync()
        thread {
            try {
                readAndDraw(context)
            } finally {
                pending.finish()
            }
        }
    }

    // Tasti del piccolo telecomando: il comando a Kodi da qui, senza aprire l'app
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(PLAY, BACK, FORWARD, INTRO, PREVIOUS, NEXT, STOP)) return
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
                            PREVIOUS -> kodi.previous(id)
                            NEXT -> kodi.next(id)
                            STOP -> kodi.stop(id)
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
                readAndDraw(context)
                // Con un video in corso parte anche il player della tendina, che tiene aggiornato il widget
                if (id != null && action != STOP) KodiPlayer.startIfWanted(context)
            } finally {
                pending.finish()
            }
        }
    }
}
