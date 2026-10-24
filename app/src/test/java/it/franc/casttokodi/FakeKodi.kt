package it.franc.casttokodi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

// Un Kodi finto in rete locale: risponde al JSON-RPC come Kodi (le chiamate che usa l'app) e tiene la sua playlist
// video, il video in corso e la posizione. Serve ai test di invio e coda senza una Fire TV.
// login: "utente:password" se Kodi la chiede, null se no.
class FakeKodi(private val login: String? = null) : AutoCloseable {
    // La playlist video (id 1): i link come li ha ricevuti, con gli header dopo "|"
    val playlist = mutableListOf<String>()
    // Il video in corso, null se Kodi è fermo
    var playing: String? = null
    // Posizione del video in corso nella playlist, -1 se è partito fuori dalla coda (Player.Open {file})
    var position = -1
    // Secondi già visti e durata del video in corso
    var time = 0L
    var totaltime = 0L
    // Come Kodi con alcuni link (#11): li rifiuta in playlist ma li apre con Player.Open {file}
    var rejectInPlaylist = false
    // I metodi chiamati, in ordine
    val calls = mutableListOf<String>()
    // Telecomando: pausa, volume, tracce del video in corso (indici di Kodi) e testo scritto
    var paused = false
    var volume = 50
    var muted = false
    var tempo = 1.0
    var audio = listOf("ita", "jpn")
    var currentAudio = 0
    var subtitles = listOf("ita")
    var currentSubtitle = 0
    var subtitlesOn = false
    var text: String? = null
    // Il punto salvato da Kodi per i video già riprodotti (posizione, durata) e quelli visti tutti
    val saved = mutableMapOf<String, Pair<Long, Long>>()
    val played = mutableSetOf<String>()
    // Le prossime n chiamate rispondono con un errore, come Kodi mentre apre il video sulla Fire TV
    var failing = 0
    // I prossimi n Player.Seek rispondono OK ma il video resta dov'è, come Kodi che salta mentre apre ancora il video
    var ignoredSeeks = 0

    private val server = TinyHttp { serve(it) }

    val host = "127.0.0.1:${server.port}"

    override fun close() = server.close()

    // Kodi in riproduzione dalla coda, alla posizione index
    fun playingFromQueue(index: Int, watched: Long = 0, total: Long = 0) {
        position = index
        playing = playlist[index]
        time = watched
        totaltime = total
    }

    // Kodi in riproduzione di un video aperto fuori dalla coda (da un altro telecomando o add-on)
    fun playingOutside(file: String) {
        position = -1
        playing = file
    }

    // Quello che farebbe Kodi per riprodurre: scarica il link in corso con gli header scritti dopo "|"
    fun fetchPlaying(): String = fetch(playing ?: error("Kodi è fermo"))

    @Synchronized
    private fun serve(http: TinyHttp.Request): TinyHttp.Response {
        if (http.path != "/jsonrpc" || http.method != "POST") return TinyHttp.Response(404)
        if (login != null && http.header("Authorization") != "Basic " + Base64.getEncoder().encodeToString(login.toByteArray())) {
            return TinyHttp.Response(401)
        }
        val request = JSONObject(String(http.body))
        val method = request.getString("method")
        calls += method
        if (failing > 0) {
            failing--
            return TinyHttp.Response(500)
        }
        val reply = JSONObject().put("jsonrpc", "2.0").put("id", request.opt("id"))
        try {
            reply.put("result", answer(method, request.optJSONObject("params") ?: JSONObject()))
        } catch (e: Refusal) {
            reply.put("error", JSONObject().put("code", e.code).put("message", e.message))
        }
        return TinyHttp.Response(200, reply.toString().toByteArray(), "application/json")
    }

    private class Refusal(val code: Int, message: String) : Exception(message)

    private fun answer(method: String, params: JSONObject): Any = when (method) {
        "Player.GetActivePlayers" ->
            if (playing == null) JSONArray() else JSONArray().put(JSONObject().put("playerid", 1).put("type", "video"))
        "Player.GetProperties" -> {
            player(params)
            JSONObject()
                .put("playlistid", if (position >= 0) VIDEO_PLAYLIST else -1)
                .put("position", position)
                .put("time", clock(time))
                .put("totaltime", clock(totaltime))
                .put("speed", if (paused) 0 else 1)
                .put("audiostreams", streams(audio))
                .put("currentaudiostream", streams(audio).optJSONObject(currentAudio) ?: JSONObject())
                .put("subtitles", streams(subtitles))
                .put("currentsubtitle", streams(subtitles).optJSONObject(currentSubtitle) ?: JSONObject())
                .put("subtitleenabled", subtitlesOn)
        }
        "Player.PlayPause" -> {
            player(params)
            paused = !paused
            JSONObject().put("speed", if (paused) 0 else 1)
        }
        "Player.Stop" -> {
            player(params)
            playing = null
            position = -1
            "OK"
        }
        // Come Kodi 19+: value {time} va al punto preciso, value {seconds} salta da dove si è; mai fuori dal video
        "Player.Seek" -> {
            player(params)
            val value = params.getJSONObject("value")
            val to = value.optJSONObject("time")?.let { it.getLong("hours") * 3600 + it.getLong("minutes") * 60 + it.getLong("seconds") }
                ?: (time + value.getLong("seconds"))
            if (ignoredSeeks > 0) ignoredSeeks-- else time = to.coerceIn(0, totaltime)
            JSONObject().put("time", clock(time)).put("totaltime", clock(totaltime))
        }
        "Player.SetTempo" -> {
            player(params)
            val wanted = params.getDouble("tempo")
            if (wanted < 0.8 || wanted > 1.5) throw Refusal(-32602, "Invalid params.")
            tempo = wanted
            "OK"
        }
        "Player.SetAudioStream" -> {
            player(params)
            currentAudio = params.getInt("stream").also { if (it !in audio.indices) throw Refusal(-32602, "Invalid params.") }
            "OK"
        }
        "Player.SetSubtitle" -> {
            player(params)
            when (val wanted = params.get("subtitle")) {
                "off" -> subtitlesOn = false
                "on" -> subtitlesOn = true
                is Int -> {
                    if (wanted !in subtitles.indices) throw Refusal(-32602, "Invalid params.")
                    currentSubtitle = wanted
                    if (params.optBoolean("enable")) subtitlesOn = true
                }
            }
            "OK"
        }
        "Application.GetProperties" -> JSONObject().put("volume", volume).put("muted", muted)
        "Application.SetVolume" -> {
            volume = when (val wanted = params.get("volume")) {
                "increment" -> volume + 10
                "decrement" -> volume - 10
                else -> (wanted as Int)
            }.coerceIn(0, 100)
            volume
        }
        "Application.SetMute" -> {
            muted = if (params.get("mute") == "toggle") !muted else params.getBoolean("mute")
            muted
        }
        "Input.SendText" -> {
            text = params.getString("text")
            "OK"
        }
        "Input.Up", "Input.Down", "Input.Left", "Input.Right", "Input.Select", "Input.Back", "Input.Home",
        "Input.ContextMenu", "Input.Info" -> "OK"
        "Player.GetItem" -> {
            player(params)
            JSONObject().put("item", JSONObject().put("file", playing).put("label", playing!!.substringAfterLast('/')).put("title", ""))
        }
        "Playlist.GetItems" -> {
            videoPlaylist(params)
            // Come Kodi: con la playlist vuota "items" non c'è
            val result = JSONObject().put("limits", JSONObject().put("start", 0).put("end", playlist.size).put("total", playlist.size))
            if (playlist.isNotEmpty()) {
                result.put("items", JSONArray(playlist.map { JSONObject().put("file", it).put("label", it.substringAfterLast('/')).put("title", "") }))
            }
            result
        }
        "Playlist.Add" -> {
            videoPlaylist(params)
            playlist += fileOf(params.getJSONObject("item"))
            "OK"
        }
        "Playlist.Insert" -> {
            videoPlaylist(params)
            val at = params.getInt("position")
            if (at < 0 || at > playlist.size) throw Refusal(-32602, "Invalid params.")
            playlist.add(at, fileOf(params.getJSONObject("item")))
            if (position >= 0 && at <= position) position++
            "OK"
        }
        "Playlist.Remove" -> {
            videoPlaylist(params)
            val at = params.getInt("position")
            // Kodi non toglie il video in corso
            if (at < 0 || at >= playlist.size || at == position) throw Refusal(-32602, "Invalid params.")
            playlist.removeAt(at)
            if (at < position) position--
            "OK"
        }
        "Player.GoTo" -> {
            if (playing == null || position < 0) throw Refusal(-32100, "Failed to execute method.")
            start(when (val to = params.get("to")) {
                "next" -> position + 1
                "previous" -> position - 1
                else -> to as Int
            })
            "OK"
        }
        "Player.Open" -> {
            val item = params.getJSONObject("item")
            if (item.has("playlistid")) {
                videoPlaylist(item)
                start(item.optInt("position", 0))
            } else {
                playingOutside(item.getString("file"))
                time = 0
            }
            "OK"
        }
        // Come Kodi: un file mai riprodotto non c'è nel suo database
        "Files.GetFileDetails" -> {
            val file = params.getString("file")
            val point = saved[file] ?: if (file in played) 0L to 0L else throw Refusal(-32602, "Invalid params.")
            JSONObject().put("filedetails", JSONObject()
                .put("file", file)
                .put("playcount", if (file in played) 1 else 0)
                .put("resume", JSONObject().put("position", point.first.toDouble()).put("total", point.second.toDouble())))
        }
        else -> throw Refusal(-32601, "Method not found.")
    }

    // Le chiamate sul video in corso: con Kodi fermo (o un altro player) Kodi risponde con un errore
    private fun player(params: JSONObject) {
        if (playing == null || params.optInt("playerid") != 1) throw Refusal(-32100, "Failed to execute method.")
    }

    private fun streams(languages: List<String>) =
        JSONArray(languages.mapIndexed { i, lang -> JSONObject().put("index", i).put("language", lang).put("name", "") })

    private fun videoPlaylist(params: JSONObject) {
        if (params.optInt("playlistid", -1) != VIDEO_PLAYLIST) throw Refusal(-32602, "Invalid params.")
    }

    private fun fileOf(item: JSONObject): String {
        if (rejectInPlaylist) throw Refusal(-32602, "Invalid params.")
        return item.getString("file")
    }

    private fun start(index: Int) {
        if (index < 0 || index >= playlist.size) throw Refusal(-32602, "Invalid params.")
        playingFromQueue(index)
        paused = false
    }

    private fun clock(seconds: Long) =
        JSONObject().put("hours", seconds / 3600).put("minutes", seconds / 60 % 60).put("seconds", seconds % 60).put("milliseconds", 0)

    companion object {
        // Un link come lo legge Kodi: "url|Header=valore&Header2=valore", valori codificati come in un URL
        fun fetch(file: String): String {
            val conn = URL(file.substringBefore('|')).openConnection() as HttpURLConnection
            file.substringAfter('|', "").split('&').filter { '=' in it }.forEach {
                conn.setRequestProperty(it.substringBefore('='), URLDecoder.decode(it.substringAfter('='), "UTF-8"))
            }
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode} da ${conn.url}")
            return conn.inputStream.use { String(it.readBytes()) }
        }
    }
}
