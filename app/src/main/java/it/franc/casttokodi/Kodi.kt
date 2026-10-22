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
        return Sent("Aggiunto alla coda (la vedi in Kodi → Coda di Kodi)", position)
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
}
