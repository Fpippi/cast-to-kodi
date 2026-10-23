package it.franc.casttokodi

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Anteprima di un video HLS: durata e un pezzo già decifrato, da cui il telefono estrae un fotogramma
class Preview(val seconds: Long?, val sample: ByteArray?)

// Il link da mandare a Kodi: una sola qualità della playlist HLS e, se serve, playlist e chiavi passate dal telefono.
// Niente android.*: lo prova StreamsTest con un sito finto e un Kodi finto. localIp: IPv4 del telefono sul Wi-Fi.
class Streams(private val relay: Relay, private val localIp: () -> String?) {
    // Playlist .m3u8 scaricate per il WebView: indirizzo → corpo e indirizzo finale (capturePlaylist)
    private val captured = LinkedHashMap<String, Pair<ByteArray, String>>()

    // Alcuni player (StreamingCommunity con vidxgo, 2026-10-21) danno playlist con un token che vale una volta sola:
    // la copia scaricata per il WebView si riusa all'invio, senza rifare la richiesta
    fun capture(url: String, body: ByteArray, finalUrl: String) = synchronized(captured) {
        captured.remove(url)
        captured[url] = body to finalUrl
        while (captured.size > 30) captured.remove(captured.keys.first())
    }

    // Kodi accetta gli header HTTP accodati all'URL: url|Header=valore&Header2=valore.
    // Da un thread di background: per le playlist HLS si legge la master e si manda una sola qualità (bestVariant).
    // viaPhone: playlist e chiave le passa il telefono (Relay), per i video che Kodi apre subito.
    fun kodiFile(url: String, headers: List<Pair<String, String>>, viaPhone: Boolean): String {
        val suffix = headers.joinToString("&") { (name, value) -> "$name=${enc(value)}" }
        if (url.substringBefore('?').lowercase().endsWith(".mp4")) return "$url|$suffix"
        val quality = singleQuality(url, headers)
        val stream = if (viaPhone) relayed(quality, headers) ?: quality else quality
        return "$stream|$suffix"
    }

    // Durata e un pezzo della qualità più leggera, per riconoscere il video giusto nell'elenco. Da un thread di
    // background. Le playlist lette restano in copia: l'invio non rifà la richiesta (token monouso di vidxgo).
    fun preview(url: String, headers: List<Pair<String, String>>, limit: Int = 8_000_000): Preview {
        var (body, mediaUrl) = playlist(url, headers)
        lowestVariant(String(body), mediaUrl)?.let { variant -> playlist(variant, headers).let { body = it.first; mediaUrl = it.second } }
        val media = String(body)
        val seconds = playlistSeconds(media)
        val sample = try {
            // Al 10% del video, al massimo a un minuto e mezzo: l'inizio spesso è nero
            segmentAt(media, mediaUrl, minOf((seconds ?: 0.0) * 0.1, 90.0))?.let { sample(it, headers, limit) }
        } catch (e: Exception) {
            null
        }
        return Preview(seconds?.toLong(), sample)
    }

    private fun playlist(url: String, headers: List<Pair<String, String>>) =
        download(url, headers).also { (body, finalUrl) -> capture(url, body, finalUrl) }

    private fun sample(segment: Segment, headers: List<Pair<String, String>>, limit: Int): ByteArray {
        var data = fetch(segment.url, headers, limit)
        if (segment.key != null && segment.iv != null) {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(fetch(segment.key, headers, 64), "AES"), IvParameterSpec(segment.iv))
            data = cipher.doFinal(data)
        }
        return segment.init?.let { fetch(it, headers, limit) + data } ?: data
    }

    // Come download ma senza copie e con un limite: un pezzo troppo grosso non vale l'attesa
    private fun fetch(url: String, headers: List<Pair<String, String>>, limit: Int): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
        val body = conn.inputStream.use { it.readNBytes(limit + 1) }
        if (body.size > limit) throw IOException("$url: più di $limit byte")
        return body
    }

    // Corpo e indirizzo finale (dopo i redirect), letti con gli stessi header che userà Kodi; le playlist già
    // scaricate per il WebView (capturePlaylist) si prendono dalla copia
    private fun download(url: String, headers: List<Pair<String, String>>): Pair<ByteArray, String> {
        synchronized(captured) { captured[url] }?.let { return it }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
        val body = conn.inputStream.use { it.readBytes() }
        return body to conn.url.toString()
    }

    // La qualità migliore della playlist HLS; se qualcosa va storto si manda il link com'era
    private fun singleQuality(url: String, headers: List<Pair<String, String>>): String = try {
        val (body, finalUrl) = download(url, headers)
        bestVariant(String(body), finalUrl) ?: url
    } catch (e: Exception) {
        url
    }

    // La playlist della qualità scelta e le sue chiavi, scaricate dal telefono e servite a Kodi in rete locale:
    // la Fire TV ci mette 10-20 s per ognuna e Kodi rinuncia dopo 30. I pezzi Kodi li prende dal sito.
    // null (si manda il link diretto) se non si riesce: niente IP sul Wi-Fi, playlist non HLS, errori di rete.
    private fun relayed(url: String, headers: List<Pair<String, String>>): String? = try {
        val ip = localIp()
        if (ip == null) null else {
            val base = "http://$ip:${relay.port()}"
            // Diverso a ogni avvio dell'app: un vecchio elemento nella coda di Kodi non riceve il video di un altro
            val id = System.currentTimeMillis()
            val keys = HashMap<String, String>()
            val (body, finalUrl) = download(url, headers)
            relayPlaylist(String(body), finalUrl) { key ->
                keys.getOrPut(key) {
                    val path = "k$id-${keys.size}"
                    relay.put(path, download(key, headers).first, "application/octet-stream")
                    "$base/$path"
                }
            }?.let { playlist ->
                relay.put("v$id.m3u8", playlist.toByteArray(), "application/vnd.apple.mpegurl")
                "$base/v$id.m3u8"
            }
        }
    } catch (e: Exception) {
        null
    }
}
