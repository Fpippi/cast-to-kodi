package it.franc.casttokodi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

// Come l'app parla con Kodi: invio, coda e playlist. Niente android.*: lo prova KodiTest contro un Kodi finto.

// Kodi riproduce la coda dalla sua playlist video
const val VIDEO_PLAYLIST = 1

// Durata ipotizzata di un episodio quando Kodi non la sa ancora, per stimare quando partirà un elemento in coda
const val DEFAULT_EPISODE = 24 * 60L

// Un episodio va accodato solo se il suo link vale ancora almeno 10 minuti dopo l'inizio previsto
const val EXPIRY_MARGIN = 10 * 60L

class KodiError(message: String) : Exception(message)

// Kodi ha risposto 401: mancano utente e password o sono sbagliati, si chiedono
class Unauthorized : Exception()

// La coda non può proseguire con questo video: si avvisa con il motivo e non si accoda
class Refused(message: String) : Exception(message)

// position: dove è finito il video nella playlist di Kodi; null se è partito fuori dalla coda
class Sent(val message: String, val position: Int?)

class KodiState(
    val playerId: Int?,
    val fromQueue: Boolean,
    val position: Int,
    val remaining: Long,
    val episode: Long,
    val queueLength: Int,
) {
    // Secondi prima che parta l'elemento in posizione index, stimando gli episodi lunghi come quello in corso
    fun secondsUntil(index: Int): Long =
        if (playerId == null || !fromQueue) 0 else remaining + (index - position - 1).coerceAtLeast(0) * episode
}

// Una traccia audio o di sottotitoli del video in corso, come la descrive Kodi (lingua di 3 lettere, nome)
class Track(val index: Int, val language: String, val name: String)

// Quello che mostra il telecomando: il video in corso (playerId null se Kodi è fermo), a che punto è, le tracce e il volume.
// time e total in secondi; currentAudio e currentSubtitle sono indici di audio e subtitles, -1 se non ce n'è
class NowPlaying(
    val playerId: Int?,
    val title: String,
    val file: String,
    val time: Long,
    val total: Long,
    val paused: Boolean,
    val audio: List<Track>,
    val currentAudio: Int,
    val subtitles: List<Track>,
    val currentSubtitle: Int,
    val subtitlesOn: Boolean,
    val volume: Int,
    val muted: Boolean,
)

// Client JSON-RPC di Kodi, da usare su un thread di background. I problemi di rete restano IOException
// (Kodi forse ha cambiato IP), le risposte di errore diventano KodiError con un messaggio da mostrare.
class Kodi(val host: String, val auth: String) {
    fun call(method: String, params: JSONObject = JSONObject()): Any? {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", method)
            .put("params", params)
            .toString()
        val conn = URL("http://$host/jsonrpc").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.setRequestProperty("Content-Type", "application/json")
        if (auth.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.toByteArray()))
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        when (conn.responseCode) {
            200 -> {}
            401 -> throw Unauthorized()
            else -> throw KodiError("Kodi ha risposto HTTP ${conn.responseCode}.")
        }
        val reply = JSONObject(conn.inputStream.bufferedReader().readText())
        reply.optJSONObject("error")?.let { throw KodiError("$method: ${it.optString("message")}") }
        return reply.opt("result")
    }

    private fun playlist() = JSONObject().put("playlistid", VIDEO_PLAYLIST)

    // Cosa sta riproducendo Kodi e se viene dalla coda (la playlist video)
    fun state(): KodiState {
        val players = call("Player.GetActivePlayers") as? JSONArray ?: JSONArray()
        val items = call("Playlist.GetItems", playlist()) as? JSONObject
        val queueLength = items?.optJSONObject("limits")?.optInt("total") ?: 0
        val player = (0 until players.length()).map { players.getJSONObject(it) }.firstOrNull { it.optString("type") == "video" }
            ?: return KodiState(null, false, -1, 0, DEFAULT_EPISODE, queueLength)
        val id = player.getInt("playerid")
        val properties = JSONArray(listOf("playlistid", "position", "time", "totaltime"))
        val p = call("Player.GetProperties", JSONObject().put("playerid", id).put("properties", properties)) as JSONObject
        val total = seconds(p.optJSONObject("totaltime"))
        val position = p.optInt("position", -1)
        return KodiState(
            playerId = id,
            fromQueue = p.optInt("playlistid", -1) == VIDEO_PLAYLIST && position >= 0,
            position = position,
            remaining = (total - seconds(p.optJSONObject("time"))).coerceAtLeast(0),
            episode = if (total > 0) total else DEFAULT_EPISODE,
            queueLength = queueLength,
        )
    }

    private fun seconds(time: JSONObject?): Long =
        if (time == null) 0 else time.optLong("hours") * 3600 + time.optLong("minutes") * 60 + time.optLong("seconds")

    // "Manda a Kodi": subito dopo il video in corso se viene dalla coda, altrimenti in fondo, e parte.
    // La coda non si cancella mai.
    fun playNow(item: JSONObject): Sent {
        val state = state()
        return try {
            if (state.fromQueue) {
                val position = state.position + 1
                call("Playlist.Insert", playlist().put("position", position).put("item", item))
                call("Player.GoTo", JSONObject().put("playerid", state.playerId).put("to", position))
                Sent("Inviato a Kodi", position)
            } else {
                // Kodi fermo o con un video non partito dall'app: in fondo alla coda, e si parte da lì
                val position = state.queueLength
                call("Playlist.Add", playlist().put("item", item))
                call("Player.Open", JSONObject().put("item", playlist().put("position", position)))
                Sent("Inviato a Kodi", position)
            }
        } catch (e: KodiError) {
            openWithoutQueue(item)
        }
    }

    // Con alcuni link la playlist di Kodi è più severa di Player.Open {file}: il video parte da solo, senza coda dietro
    private fun openWithoutQueue(item: JSONObject): Sent {
        call("Player.Open", JSONObject().put("item", item))
        return Sent("Inviato a Kodi, ma senza coda: Kodi non riproduce questo video dalla playlist", null)
    }

    // "+ Coda": accoda solo se l'episodio partirà davvero, altrimenti Refused con il motivo, da mostrare.
    // item(viaPhone): il video da mandare (vedi Streams.kodiFile); problem(secondi all'inizio): link che scade prima
    fun enqueue(item: (viaPhone: Boolean) -> JSONObject, problem: (Long) -> String?): Sent {
        val state = state()
        if (state.playerId == null) return playNow(item(true))
        if (!state.fromQueue) {
            throw Refused(
                "Il video in corso su Kodi non è partito dall'app: quando finisce, Kodi non passa alla coda.\n\n" +
                    "Premi \"Manda a Kodi\" su questo episodio: parte subito e da lì puoi accodare gli altri."
            )
        }
        val position = state.queueLength
        problem(state.secondsUntil(position))?.let { throw Refused(it) }
        try {
            call("Playlist.Add", playlist().put("item", item(false)))
        } catch (e: KodiError) {
            throw Refused("Kodi non accetta questo video in coda (${e.message}).")
        }
        return Sent("Aggiunto alla coda (la vedi in ⋮ → Coda di Kodi)", position)
    }

    // Episodio successivo messo in coda da solo, in posizione position. Il motivo se non si può, altrimenti null
    fun insertAt(position: Int, item: () -> JSONObject, problem: (Long) -> String?): String? {
        problem(state().secondsUntil(position))?.let { return it }
        call("Playlist.Insert", playlist().put("position", position).put("item", item()))
        return null
    }

    // La coda come la mostra l'app: stato di Kodi e video in playlist (file, title, label)
    fun queue(): Pair<KodiState, List<JSONObject>> {
        val state = state()
        val reply = call("Playlist.GetItems", playlist().put("properties", JSONArray(listOf("title", "file")))) as? JSONObject
        val items = reply?.optJSONArray("items") ?: JSONArray()
        return state to List(items.length()) { items.getJSONObject(it) }
    }

    // Un video partito fuori dalla coda (dal telecomando, da un'altra app o senza coda) non è nella playlist:
    // lo si legge a parte, così la schermata dice comunque cosa sta andando. null se non ce n'è
    fun playingOutside(state: KodiState): JSONObject? {
        if (state.playerId == null || state.fromQueue) return null
        val properties = JSONArray(listOf("title", "file"))
        return (call("Player.GetItem", JSONObject().put("playerid", state.playerId).put("properties", properties)) as? JSONObject)
            ?.optJSONObject("item")
    }

    // Se la coda sta andando si salta lì, altrimenti si fa partire la coda da quel video
    fun playFrom(state: KodiState, index: Int) {
        if (state.fromQueue) call("Player.GoTo", JSONObject().put("playerid", state.playerId).put("to", index))
        else call("Player.Open", JSONObject().put("item", playlist().put("position", index)))
    }

    fun remove(index: Int) {
        call("Playlist.Remove", playlist().put("position", index))
    }

    // Toglie tutto tranne il video in corso (current, -1 se nessuno): niente Playlist.Clear, che lo staccherebbe dalla coda
    fun clearQueue(current: Int) {
        val total = (call("Playlist.GetItems", playlist()) as? JSONObject)?.optJSONObject("limits")?.optInt("total") ?: 0
        // Dal fondo, così le posizioni ancora da togliere non si spostano
        for (i in total - 1 downTo 0) if (i != current) remove(i)
    }

    // Telecomando: il video in corso in una volta sola (il telecomando lo rilegge ogni secondo) e i comandi.
    // I comandi sul video vogliono il playerId di NowPlaying; con Kodi fermo non si chiamano.
    fun nowPlaying(): NowPlaying {
        val app = call("Application.GetProperties", JSONObject().put("properties", JSONArray(listOf("volume", "muted")))) as? JSONObject
        val volume = app?.optInt("volume", 100) ?: 100
        val muted = app?.optBoolean("muted") ?: false
        val players = call("Player.GetActivePlayers") as? JSONArray ?: JSONArray()
        val player = (0 until players.length()).map { players.getJSONObject(it) }.firstOrNull { it.optString("type") == "video" }
            ?: return NowPlaying(null, "", "", 0, 0, false, emptyList(), -1, emptyList(), -1, false, volume, muted)
        val id = player.getInt("playerid")
        val properties = JSONArray(
            listOf("time", "totaltime", "speed", "audiostreams", "currentaudiostream", "subtitles", "currentsubtitle", "subtitleenabled")
        )
        val p = call("Player.GetProperties", JSONObject().put("playerid", id).put("properties", properties)) as JSONObject
        val item = (call("Player.GetItem", JSONObject().put("playerid", id).put("properties", JSONArray(listOf("title", "file")))) as? JSONObject)
            ?.optJSONObject("item") ?: JSONObject()
        val audio = tracks(p.optJSONArray("audiostreams"))
        val subtitles = tracks(p.optJSONArray("subtitles"))
        return NowPlaying(
            playerId = id,
            title = item.optString("title").ifBlank { item.optString("label") },
            file = item.optString("file"),
            time = seconds(p.optJSONObject("time")),
            total = seconds(p.optJSONObject("totaltime")),
            paused = p.optInt("speed", 1) == 0,
            audio = audio,
            currentAudio = audio.indexOfFirst { it.index == p.optJSONObject("currentaudiostream")?.optInt("index", -1) },
            subtitles = subtitles,
            currentSubtitle = subtitles.indexOfFirst { it.index == p.optJSONObject("currentsubtitle")?.optInt("index", -1) },
            subtitlesOn = p.optBoolean("subtitleenabled"),
            volume = volume,
            muted = muted,
        )
    }

    private fun tracks(array: JSONArray?): List<Track> = List(array?.length() ?: 0) {
        val t = array!!.getJSONObject(it)
        Track(t.optInt("index", it), t.optString("language"), t.optString("name"))
    }

    private fun player(id: Int) = JSONObject().put("playerid", id)

    fun playPause(id: Int) {
        call("Player.PlayPause", player(id))
    }

    fun stop(id: Int) {
        call("Player.Stop", player(id))
    }

    // Al secondo esatto (barra di avanzamento, "Vai al minuto")
    fun seekTo(id: Int, seconds: Long) {
        val s = seconds.coerceAtLeast(0)
        val time = JSONObject().put("hours", s / 3600).put("minutes", s / 60 % 60).put("seconds", s % 60).put("milliseconds", 0)
        call("Player.Seek", player(id).put("value", JSONObject().put("time", time)))
    }

    // Salto relativo, indietro se negativo: sempre di quanto dice il pulsante, non dei passi di Kodi (su/giù = 10 minuti)
    fun skip(id: Int, seconds: Int) {
        call("Player.Seek", player(id).put("value", JSONObject().put("seconds", seconds)))
    }

    // Video dopo o prima nella coda (la playlist video di Kodi)
    fun next(id: Int) {
        call("Player.GoTo", player(id).put("to", "next"))
    }

    fun previous(id: Int) {
        call("Player.GoTo", player(id).put("to", "previous"))
    }

    // 1.0 = normale. Kodi accetta solo da 0,8 a 1,5, e con alcuni video la rifiuta: lì arriva un KodiError
    fun setTempo(id: Int, tempo: Double) {
        call("Player.SetTempo", player(id).put("tempo", tempo))
    }

    fun volume(up: Boolean) {
        call("Application.SetVolume", JSONObject().put("volume", if (up) "increment" else "decrement"))
    }

    fun toggleMute() {
        call("Application.SetMute", JSONObject().put("mute", "toggle"))
    }

    fun setAudio(id: Int, track: Track) {
        call("Player.SetAudioStream", player(id).put("stream", track.index))
    }

    // track null: sottotitoli spenti
    fun setSubtitle(id: Int, track: Track?) {
        if (track == null) call("Player.SetSubtitle", player(id).put("subtitle", "off"))
        else call("Player.SetSubtitle", player(id).put("subtitle", track.index).put("enable", true))
    }

    // Tasti del telecomando di Kodi: Up, Down, Left, Right, Select, Back, Home, ContextMenu, Info
    fun press(key: String) {
        call("Input.$key")
    }

    // "Riprendi su Kodi" (Continua a guardare): appena il video file è partito ed è lungo quanto basta, si va al
    // secondo dove ci si era fermati. Si aspetta fino a tries volte, wait ms tra una e l'altra: sulla Fire TV un video
    // ci mette anche 20 secondi a partire. Non lo riapre mai (vedi la nota su Player.Open nel README). true se ha saltato
    fun resumeAt(file: String, seconds: Long, tries: Int = 60, wait: Long = 1000): Boolean {
        repeat(tries) {
            val playing = nowPlaying()
            val id = playing.playerId
            if (id != null && queueKey(playing.file) == queueKey(file) && playing.total > seconds) {
                seekTo(id, seconds)
                return true
            }
            Thread.sleep(wait)
        }
        return false
    }

    // Il punto che Kodi ha salvato per un video già riprodotto (posizione e durata in secondi) e se l'ha visto tutto.
    // Serve quando l'app era chiusa mentre l'episodio andava. null se Kodi non lo conosce
    fun savedPoint(file: String): Triple<Long, Long, Boolean>? {
        val params = JSONObject().put("file", file).put("media", "video").put("properties", JSONArray(listOf("resume", "playcount")))
        val details = (call("Files.GetFileDetails", params) as? JSONObject)?.optJSONObject("filedetails") ?: return null
        val resume = details.optJSONObject("resume")
        return Triple(resume?.optDouble("position", 0.0)?.toLong() ?: 0, resume?.optDouble("total", 0.0)?.toLong() ?: 0, details.optInt("playcount") > 0)
    }

    // Testo nel campo che Kodi ha aperto (la ricerca, un nome): con done Kodi lo conferma come con OK
    fun sendText(text: String) {
        call("Input.SendText", JSONObject().put("text", text).put("done", true))
    }
}
