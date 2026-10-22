package it.franc.casttokodi

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
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

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/jsonrpc") { serve(it) }
        start()
    }

    val host = "127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)

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
    private fun serve(exchange: HttpExchange) = exchange.use {
        if (login != null) {
            val sent = it.requestHeaders.getFirst("Authorization")
            if (sent != "Basic " + Base64.getEncoder().encodeToString(login.toByteArray())) {
                it.sendResponseHeaders(401, -1)
                return
            }
        }
        val request = JSONObject(it.requestBody.bufferedReader().readText())
        val method = request.getString("method")
        calls += method
        val reply = JSONObject().put("jsonrpc", "2.0").put("id", request.opt("id"))
        try {
            reply.put("result", answer(method, request.optJSONObject("params") ?: JSONObject()))
        } catch (e: Refusal) {
            reply.put("error", JSONObject().put("code", e.code).put("message", e.message))
        }
        val body = reply.toString().toByteArray()
        it.responseHeaders.add("Content-Type", "application/json")
        it.sendResponseHeaders(200, body.size.toLong())
        it.responseBody.write(body)
    }

    private class Refusal(val code: Int, message: String) : Exception(message)

    private fun answer(method: String, params: JSONObject): Any = when (method) {
        "Player.GetActivePlayers" ->
            if (playing == null) JSONArray() else JSONArray().put(JSONObject().put("playerid", 1).put("type", "video"))
        "Player.GetProperties" -> {
            if (playing == null || params.optInt("playerid") != 1) throw Refusal(-32100, "Failed to execute method.")
            JSONObject()
                .put("playlistid", if (position >= 0) VIDEO_PLAYLIST else -1)
                .put("position", position)
                .put("time", clock(time))
                .put("totaltime", clock(totaltime))
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
            start(params.getInt("to"))
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
        else -> throw Refusal(-32601, "Method not found.")
    }

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
