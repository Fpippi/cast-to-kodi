package it.franc.casttokodi

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Anteprima di un video HLS: durata e un pezzo già decifrato, da cui il telefono estrae un fotogramma
// error: perché il pezzo non c'è, quando manca
// subtitles: le lingue dei sottotitoli della master (null = lingua non detta), per il "CC" nella scelta del video
class Preview(val seconds: Long?, val sample: ByteArray?, val error: String? = null, val subtitles: List<String?> = emptyList())

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
    // viaPhone: playlist e chiave le passa il telefono (Relay), per i video che Kodi apre subito, e con loro i
    // sottotitoli della master e quelli caricati dalla pagina (subtitles); onSubtitles riceve le tracce passate a Kodi.
    fun kodiFile(
        url: String, headers: List<Pair<String, String>>, viaPhone: Boolean,
        subtitles: List<SiteSubtitle> = emptyList(), onSubtitles: (List<SubtitleTrack>) -> Unit = {},
    ): String {
        val suffix = headers.joinToString("&") { (name, value) -> "$name=${enc(value)}" }
        if (url.substringBefore('?').lowercase().endsWith(".mp4")) return "$url|$suffix"
        val master = try { download(url, headers) } catch (e: Exception) { null }
        val text = master?.let { String(it.first) }
        val quality = text?.let { bestVariant(it, master.second) } ?: url
        if (!viaPhone) return "$quality|$suffix"
        val stream = relayed(quality, headers)?.let { (playlist, seconds) ->
            val tracks = pickSubtitles(
                (text?.let { masterSubtitles(it, master.second) }.orEmpty().mapNotNull { renditionTrack(it, headers) }) +
                    subtitles.mapNotNull { siteTrack(it) }
            )
            if (tracks.isEmpty()) playlist else withSubtitles(playlist, seconds, bandwidthOf(text, master?.second, quality), tracks)?.also { onSubtitles(tracks) } ?: playlist
        } ?: quality
        return "$stream|$suffix"
    }

    // Una traccia della master: la sua playlist e tutti i pezzi, uniti in un file. null se non si legge
    private fun renditionTrack(rendition: SubtitleRendition, headers: List<Pair<String, String>>): SubtitleTrack? = try {
        val (body, finalUrl) = download(rendition.url, headers)
        val parts = subtitleParts(String(body), finalUrl).takeIf { it.size in 1..MAX_SUBTITLE_PARTS }
        parts?.let { list ->
            val name = if (rendition.forced) listOf(rendition.name.ifBlank { languageName(rendition.language).orEmpty() }, "Forced").filter { it.isNotBlank() }.joinToString(" · ") else rendition.name
            SubtitleTrack(rendition.language, name, mergeVtt(list.map { String(fetch(it, headers, 2_000_000), Charsets.UTF_8) }), fromSite = false)
        }
    } catch (e: Exception) {
        null
    }

    private fun siteTrack(subtitle: SiteSubtitle): SubtitleTrack? = try {
        SubtitleTrack(subtitle.language, "", toVtt(String(fetch(subtitle.url, subtitle.headers, 2_000_000), Charsets.UTF_8)), fromSite = true)
    } catch (e: Exception) {
        null
    }

    // La master con la qualità passata dal telefono e i sottotitoli, servita anche lei dal telefono
    private fun withSubtitles(playlist: String, seconds: Double, bandwidth: Long, tracks: List<SubtitleTrack>): String? = try {
        val ip = localIp() ?: throw IOException("niente Wi-Fi")
        val base = "http://$ip:${relay.port()}"
        val id = System.currentTimeMillis()
        val entries = tracks.mapIndexed { i, track ->
            relay.put("s$id-$i.vtt", track.vtt.toByteArray(), "text/vtt; charset=utf-8")
            relay.put("s$id-$i.m3u8", subtitlePlaylist("$base/s$id-$i.vtt", seconds).toByteArray(), "application/vnd.apple.mpegurl")
            Triple(track.language, subtitleName(track.language, track.name, track.fromSite, i + 1), "$base/s$id-$i.m3u8")
        }
        relay.put("m$id.m3u8", subtitleMaster(playlist, bandwidth, entries).toByteArray(), "application/vnd.apple.mpegurl")
        "$base/m$id.m3u8"
    } catch (e: Exception) {
        null
    }

    // Durata e un pezzo della qualità più leggera, per riconoscere il video giusto nell'elenco. Da un thread di
    // background. Le playlist lette restano in copia: l'invio non rifà la richiesta (token monouso di vidxgo).
    fun preview(url: String, headers: List<Pair<String, String>>, limit: Int = 8_000_000, fraction: Double? = null): Preview {
        var (body, mediaUrl) = playlist(url, headers)
        val subtitles = masterSubtitles(String(body), mediaUrl).map { it.language }
        lowestVariant(String(body), mediaUrl)?.let { variant -> playlist(variant, headers).let { body = it.first; mediaUrl = it.second } }
        val media = String(body)
        val seconds = playlistSeconds(media)
        var error: String? = null
        val sample = try {
            val segment = segmentAt(media, mediaUrl, previewPoint(seconds ?: 0.0, fraction))
            if (segment == null) error = "nessun pezzo leggibile nella playlist (protetto?)"
            segment?.let { sample(it, headers, limit) }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            null
        }
        return Preview(seconds?.toLong(), sample, error, subtitles)
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

    // La playlist della qualità scelta e le sue chiavi, scaricate dal telefono e servite a Kodi in rete locale:
    // la Fire TV ci mette 10-20 s per ognuna e Kodi rinuncia dopo 30. I pezzi Kodi li prende dal sito.
    // null (si manda il link diretto) se non si riesce: niente IP sul Wi-Fi, playlist non HLS, errori di rete.
    // Con l'indirizzo locale la durata della playlist, per quella dei sottotitoli.
    private fun relayed(url: String, headers: List<Pair<String, String>>): Pair<String, Double>? = try {
        val ip = localIp()
        if (ip == null) null else {
            val base = "http://$ip:${relay.port()}"
            // Diverso a ogni avvio dell'app: un vecchio elemento nella coda di Kodi non riceve il video di un altro
            val id = System.currentTimeMillis()
            val keys = HashMap<String, String>()
            val (body, finalUrl) = download(url, headers)
            val media = String(body)
            relayPlaylist(media, finalUrl) { key ->
                keys.getOrPut(key) {
                    val path = "k$id-${keys.size}"
                    relay.put(path, download(key, headers).first, "application/octet-stream")
                    "$base/$path"
                }
            }?.let { playlist ->
                relay.put("v$id.m3u8", playlist.toByteArray(), "application/vnd.apple.mpegurl")
                "$base/v$id.m3u8" to (playlistSeconds(media) ?: 0.0)
            }
        }
    } catch (e: Exception) {
        null
    }
}
