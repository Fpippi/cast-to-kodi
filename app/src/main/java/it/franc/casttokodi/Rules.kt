package it.franc.casttokodi

import it.franc.casttokodi.sites.GOOGLE
import it.franc.casttokodi.sites.googleSearch
import it.franc.casttokodi.sites.isGoogle
import it.franc.casttokodi.sites.isVixCloudPart
import it.franc.casttokodi.sites.isVixCloudPlaylist
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

// Regole dell'app scritte senza API Android (niente Uri, WebView, Context): così le provano i test
// in app/src/test, che girano sulla JVM a ogni PR. Quello che vale per un solo sito o player sta in sites/.

// ponytail: circuiti pubblicitari dei player e dei siti di streaming (video pre-roll e popunder); allungarlo quando
// tra i video trovati ne compare un altro. Si confronta come pezzo dell'host, quindi nomi abbastanza lunghi da non sbagliare
private val AD_HOSTS = listOf(
    "doubleclick", "googlesyndication", "imasdk", "adnxs", "adsrvr", "popads", "exoclick", "juicyads",
    "adsterra", "propellerads", "popcash", "hilltopads", "clickadu", "adcash", "trafficjunky", "adskeeper",
    "onclickads", "galaksion", "spotxchange", "springserve", "aniview", "vidoomy", "videoadex",
)

// host:porta (localhost:8080, 192.168.1.50:8080): roba di casa, che di solito non ha HTTPS
private val HOST_PORT = Regex("[\\w.-]+:\\d{1,5}(/.*)?")

class Video(val url: String, val referer: String, val label: String, val main: Boolean)

// Cosa aprire per il testo scritto nella barra in alto
fun addressFor(input: String): String {
    val text = input.trim()
    return when {
        text.isEmpty() -> GOOGLE
        text.startsWith("http://") || text.startsWith("https://") -> text
        HOST_PORT.matches(text) -> "http://$text"
        // "jojo animeunity" o "jojo" non sono indirizzi: si cercano su Google
        ' ' in text || '.' !in text -> googleSearch(text)
        else -> "https://$text"
    }
}

// ponytail: "ultime due etichette" sbaglia con suffissi tipo .co.uk, basta per i siti che usiamo
fun domainOf(host: String?) = host.orEmpty().split('.').takeLast(2).joinToString(".")

// true se la pagina principale non deve andare a url: popup e salti fatti da JS verso altri domini
fun blocksNavigation(scheme: String?, host: String?, allowedDomain: String, mainFrame: Boolean, redirect: Boolean): Boolean {
    if (scheme != "http" && scheme != "https") return true
    // I redirect HTTP (es. il sito che cambia dominio) sono ammessi, i salti fatti da JS no
    if (!mainFrame || redirect) return false
    if (isGoogle(allowedDomain)) return false
    return domainOf(host) != allowedDomain
}

// ponytail: riconosce per estensione più il caso VixCloud senza .m3u8; per altri siti così guardare il Content-Type
fun isVideoPath(path: String?): Boolean {
    val p = path?.lowercase() ?: return false
    return p.endsWith(".m3u8") || p.endsWith(".mp4") || isVixCloudPlaylist(p)
}

// Playlist HLS che l'app scarica al posto del WebView (vedi capturePlaylist in MainActivity)
fun isHlsPath(path: String?): Boolean = path?.lowercase()?.endsWith(".m3u8") == true

// Header della risposta scaricata dall'app da ridare al WebView: via quelli che non valgono più (il corpo è già
// decompresso e intero) e i cookie, che li tiene CookieManager. Se il server non ha mandato
// Access-Control-Allow-Origin (non ha visto l'Origin del player) lo si aggiunge, se no il player rifiuta la risposta.
fun forwardedHeaders(headers: Map<String?, List<String>>, origin: String?): Map<String, String> {
    val skip = setOf("content-encoding", "content-length", "transfer-encoding", "connection", "set-cookie", "set-cookie2")
    val out = headers.filterKeys { it != null && it.lowercase() !in skip }
        .map { (name, values) -> name!! to values.joinToString(", ") }.toMap().toMutableMap()
    if (out.keys.none { it.equals("Access-Control-Allow-Origin", true) }) out["Access-Control-Allow-Origin"] = origin ?: "*"
    return out
}

fun isAd(host: String?, path: String?): Boolean {
    val h = host.orEmpty().lowercase()
    val p = path.orEmpty().lowercase()
    return AD_HOSTS.any { it in h } || h.split('.').any { it == "ads" || it == "ad" } ||
        "/vast" in p || "/vpaid" in p || "/preroll" in p || "/ads/" in p
}

// Il video da proporre per un flusso intercettato, o null se è pubblicità o un pezzo della playlist VixCloud.
// host, path e query sono le parti già separate di url (query senza "?").
fun videoFor(url: String, referer: String, host: String?, path: String?, query: String?): Video? {
    val p = path.orEmpty().lowercase()
    val vixcloud = isVixCloudPlaylist(p)
    val params = query.orEmpty().split('&').map { it.substringBefore('=') }
    return when {
        isAd(host, path) -> null
        vixcloud && isVixCloudPart(params) -> null
        else -> {
            val main = vixcloud || "master" in p
            val quality = Regex("\\b(\\d{3,4})p\\b").find(url)?.groupValues?.get(1)
            // Parole semplici: chi sceglie non sa cos'è una playlist HLS
            val kind = when {
                main -> "Video completo, tutte le qualità (consigliato)"
                quality != null -> "Solo la qualità ${quality}p"
                p.endsWith(".mp4") -> "Video (file MP4)"
                else -> "Altro video trovato nella pagina"
            }
            Video(url, referer, "$kind\nda ${shortHost(host)}", main)
        }
    }
}

// Video da proporre, il completo per primo. Le singole qualità dello stesso sito del video completo sono pezzi di
// quello (es. 720p e 1080p di una master): mostrarle a parte faceva sembrare che ci fossero due video diversi.
fun videoChoices(found: List<Video>): List<Video> {
    val mainHosts = found.filter { it.main }.map { hostOf(it.url) }.toSet()
    return found.filter { it.main || hostOf(it.url) !in mainHosts }.sortedByDescending { it.main }
}

private fun hostOf(url: String): String? = try { URI(url).host } catch (e: Exception) { null }

// Preferito salvato. icon è la favicon in PNG Base64, assente per quelli salvati prima di #25
data class Favorite(val title: String, val url: String, val icon: String? = null)

// Nome proposto quando si aggiunge una pagina: il titolo senza il nome del sito in coda
// ("Guarda JoJo Episodio 3 - AnimeUnity" -> "Guarda JoJo Episodio 3"), o il dominio se non c'è titolo
fun suggestedName(pageTitle: String?, host: String?): String {
    val title = pageTitle.orEmpty().replace(Regex("\\s+"), " ").trim()
    val short = title.split(" - ", " | ", " – ", " — ").first().trim()
    return short.ifEmpty { title }.ifEmpty { shortHost(host) }
}

// Nome scritto nel campo di rinomina: vuoto vuol dire tenere quello vecchio
fun renamed(input: String, old: String): String = input.replace(Regex("\\s+"), " ").trim().ifEmpty { old }

// Dominio da mostrare sotto il nome, senza "www."
fun shortHost(host: String?): String = host.orEmpty().removePrefix("www.")

// Lettera sul cerchio quando il sito non ha favicon
fun initialOf(name: String, host: String?): String =
    (name.firstOrNull { it.isLetterOrDigit() } ?: shortHost(host).firstOrNull { it.isLetterOrDigit() } ?: '?')
        .uppercase()

// Da una playlist HLS principale (master) la qualità migliore, come URL assoluto. Kodi con la master apre tutte le
// qualità per scegliere (playlist, chiave e primo pezzo di ognuna): sulla Fire TV ci mette più dei 30 secondi che
// concede e il video non parte (VixCloud, 2026-10-21). null se non è una master o se l'audio è in una traccia a parte,
// che con una sola qualità si perderebbe: lì si manda la master com'è.
fun bestVariant(master: String, masterUrl: String): String? {
    if (master.lines().any { it.trim().startsWith("#EXT-X-MEDIA:") && "TYPE=AUDIO" in it && "URI=" in it }) return null
    return variants(master, masterUrl).maxByOrNull { it.first }?.second
}

// La qualità più leggera, per l'anteprima nell'elenco dei video: basta un'immagine piccola
fun lowestVariant(master: String, masterUrl: String): String? = variants(master, masterUrl).minByOrNull { it.first }?.second

// Le qualità di una master come (BANDWIDTH, URL assoluto), nell'ordine della playlist
private fun variants(master: String, masterUrl: String): List<Pair<Long, String>> {
    val lines = master.lines().map { it.trim() }
    return lines.mapIndexedNotNull { i, line ->
        if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
        val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@mapIndexedNotNull null
        val bandwidth = Regex("(?<![-A-Z])BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        bandwidth to URI(masterUrl).resolve(uri).toString()
    }
}

// Durata in secondi di una playlist dei pezzi (somma dei #EXTINF); null se non ne ha (una master, una diretta vuota)
fun playlistSeconds(media: String): Double? {
    val parts = media.lines().map { it.trim() }.filter { it.startsWith("#EXTINF:") }
    if (parts.isEmpty()) return null
    return parts.sumOf { it.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: 0.0 }
}

// "24 min", "1 h 32 min", "45 s": la durata accanto a ogni video, per riconoscere l'episodio da un trailer
fun durationLabel(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${(seconds + 30) / 60} min"
    else -> "${seconds / 3600} h ${(seconds % 3600 + 30) / 60} min"
}

// Il pezzo di una playlist da cui l'anteprima prende un fotogramma. key/iv solo se è cifrato AES-128;
// init è il pezzo d'inizio dei video fMP4 (#EXT-X-MAP), da mettere davanti.
class Segment(val url: String, val key: String?, val iv: ByteArray?, val init: String?)

// Il pezzo che contiene il secondo `at` (l'ultimo se il video è più corto): l'inizio spesso è nero.
// null se non si può decifrare (SAMPLE-AES, DRM) o se non ci sono pezzi.
fun segmentAt(media: String, mediaUrl: String, at: Double): Segment? {
    val base = URI(mediaUrl)
    val uri = Regex("URI=\"([^\"]+)\"")
    var key: String? = null
    var iv: ByteArray? = null
    var init: String? = null
    var number = 0L
    var start = 0.0
    var length = 0.0
    var found: Segment? = null
    for (raw in media.lines()) {
        val line = raw.trim()
        when {
            line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> number = line.substringAfter(':').trim().toLongOrNull() ?: 0L
            line.startsWith("#EXT-X-KEY:") -> when (Regex("METHOD=([A-Z0-9-]+)").find(line)?.groupValues?.get(1)) {
                "NONE" -> { key = null; iv = null }
                "AES-128" -> {
                    key = base.resolve(uri.find(line)?.groupValues?.get(1) ?: return null).toString()
                    iv = Regex("IV=0[xX]([0-9a-fA-F]+)").find(line)?.groupValues?.get(1)?.let { hexIv(it) }
                }
                else -> return null
            }
            line.startsWith("#EXT-X-MAP:") -> init = uri.find(line)?.groupValues?.get(1)?.let { base.resolve(it).toString() }
            line.startsWith("#EXTINF:") -> length = line.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: 0.0
            line.isEmpty() || line.startsWith("#") -> Unit
            else -> {
                // Senza IV nella playlist vale il numero del pezzo (RFC 8216, 5.2)
                found = Segment(base.resolve(line).toString(), key, if (key == null) null else iv ?: numberIv(number), init)
                start += length
                if (start > at) return found
                number++
            }
        }
    }
    return found
}

private fun hexIv(hex: String): ByteArray {
    val padded = hex.takeLast(32).padStart(32, '0')
    return ByteArray(16) { padded.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

private fun numberIv(n: Long): ByteArray = ByteArray(16) { if (it < 8) 0 else (n shr (8 * (15 - it))).toByte() }

// Playlist di una qualità da passare a Kodi dal telefono (vedi Relay): i pezzi diventano indirizzi assoluti del
// sito, le chiavi AES (#EXT-X-KEY) passano da keyUrl, che le scarica e dà l'indirizzo locale. null se non è una
// playlist dei pezzi (una master, o una pagina d'errore): lì si manda il link com'è.
fun relayPlaylist(media: String, mediaUrl: String, keyUrl: (String) -> String): String? {
    if (!media.trimStart().startsWith("#EXTM3U") || "#EXTINF" !in media) return null
    val base = URI(mediaUrl)
    val uri = Regex("URI=\"([^\"]+)\"")
    return media.lines().joinToString("\n") { raw ->
        val line = raw.trim()
        when {
            line.startsWith("#EXT-X-KEY:") -> uri.replace(line) { "URI=\"${keyUrl(base.resolve(it.groupValues[1]).toString())}\"" }
            line.startsWith("#EXT-X-MAP:") -> uri.replace(line) { "URI=\"${base.resolve(it.groupValues[1])}\"" }
            line.isEmpty() || line.startsWith("#") -> line
            else -> base.resolve(line).toString()
        }
    }
}

// "https://host[:porta]" di un indirizzo, come l'header Origin del browser; null se non è http(s)
fun originOf(url: String): String? {
    val uri = try { URI(url) } catch (e: Exception) { return null }
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
    val host = uri.host ?: return null
    return if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
}

// URLEncoder usa '+' per lo spazio, Kodi (e Google) vogliono %20
fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

// Credenziali di un Kodi, legate alla rete di casa in cui hanno funzionato. host è "IP:porta", name il nome
// annunciato via mDNS ("" se scritto a mano), network l'impronta di networkKey ("" per quelle salvate prima
// delle credenziali per dispositivo: valgono solo per il loro host). secret è la password già cifrata.
data class SavedLogin(val host: String, val name: String, val network: String, val user: String, val secret: String)

// Impronta della rete senza permessi di posizione (l'SSID li vorrebbe): sottorete IPv4 più gateway, es.
// "192.168.1.0/24 via 192.168.1.1". null se non si sa: niente credenziali salvate mandate alla cieca.
// ponytail: due router con la stessa sottorete e lo stesso gateway (capita) sembrano la stessa casa
fun networkKey(address: String?, prefixLength: Int, gateway: String?): String? {
    val ip = ipv4(address) ?: return null
    if (ipv4(gateway) == null || prefixLength !in 1..32) return null
    val mask = (-1L shl (32 - prefixLength)) and 0xFFFFFFFFL
    val net = ip and mask
    val subnet = (3 downTo 0).joinToString(".") { ((net shr (it * 8)) and 0xFF).toString() }
    return "$subnet/$prefixLength via $gateway"
}

private fun ipv4(text: String?): Long? {
    val parts = text?.split('.')?.takeIf { it.size == 4 } ?: return null
    return parts.fold(0L) { acc, p -> (acc shl 8) or (p.toIntOrNull()?.takeIf { it in 0..255 } ?: return null).toLong() }
}

// Le credenziali da provare con il Kodi a host (nome mDNS name, "" se non si sa) sulla rete network.
// Prima quelle dello stesso host, poi dello stesso nome; solo sulla stessa rete, tranne le vecchie senza rete.
fun loginFor(saved: List<SavedLogin>, host: String, name: String, network: String?): SavedLogin? {
    val here = saved.filter { (network != null && it.network == network) || (it.network.isEmpty() && it.host == host) }
    return here.firstOrNull { it.host == host } ?: here.firstOrNull { name.isNotEmpty() && it.name == name }
}

// L'elenco con login al posto delle credenziali di quel Kodi su quella rete (null: le toglie e basta)
fun replaceLogin(saved: List<SavedLogin>, host: String, name: String, network: String, login: SavedLogin?): List<SavedLogin> {
    val same = loginFor(saved, host, name, network)
    return saved.filter { it != same } + listOfNotNull(login)
}

// Kodi ha cambiato IP sulla stessa rete (l'ha ritrovato la ricerca): le sue credenziali lo seguono
fun moveLogin(saved: List<SavedLogin>, oldHost: String, newHost: String, name: String, network: String?): List<SavedLogin> {
    val login = loginFor(saved, oldHost, "", network) ?: return saved
    if (network == null) return saved
    val moved = login.copy(host = newHost, name = name.ifEmpty { login.name }, network = network)
    return replaceLogin(saved.filter { it != login }, newHost, moved.name, network, moved)
}

// Il vecchio campo unico "utente:password"; la password può contenere ':' ma l'utente di Kodi no
fun splitAuth(auth: String): Pair<String, String> = auth.substringBefore(':') to auth.substringAfter(':', "")

// "v1.2.0" o "1.2.0" → [1, 2, 0]; null per i tag che non sono versioni (i vecchi build-N)
fun versionParts(text: String): List<Int>? =
    Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").matchEntire(text.trim())?.groupValues?.drop(1)?.map { it.toInt() }

// Se il tag dell'ultima release (vX.Y.Z) è una versione più nuova di quella installata
fun isNewerVersion(latestTag: String, installed: String): Boolean {
    val latest = versionParts(latestTag) ?: return false
    val current = versionParts(installed) ?: return true // installata senza numero valido: meglio aggiornare
    return latest.zip(current).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } ?: false
}

// Nomi della coda: Kodi per un link mostra un pezzo dell'URL ("784674?type=video&token=…"), quindi l'app ricorda il
// nome della pagina da cui l'ha mandato. La chiave è il link senza gli header che Kodi accoda dopo '|'.
fun queueKey(file: String): String = file.substringBefore('|')

// I nomi ricordati con quello nuovo in fondo, tenendo solo gli ultimi max (la coda non ne ha mai così tanti)
fun withTitle(titles: Map<String, String>, file: String, title: String, max: Int = 50): Map<String, String> {
    val all = LinkedHashMap(titles)
    all.remove(queueKey(file))
    all[queueKey(file)] = title
    return all.entries.toList().takeLast(max).associateTo(LinkedHashMap()) { it.key to it.value }
}

// Nome di un elemento della coda: quello ricordato, altrimenti il titolo di Kodi se sembra un nome, altrimenti il sito
fun queueLabel(file: String, kodiTitle: String, titles: Map<String, String>): String {
    titles[queueKey(file)]?.let { return it }
    val host = Regex("^\\w+://([^/:?|]+)").find(file)?.groupValues?.get(1)
    if (kodiTitle.isNotBlank() && (host == null || ('?' !in kodiTitle && '/' !in kodiTitle))) return kodiTitle
    return if (host != null) "Video da ${shortHost(host)}" else file
}

// Telecomando: tempo scritto a mano per "Vai al minuto". "12:30" = 12 min 30 s, "1:02:30", "45" = 45 minuti.
// null se non è un tempo
fun parseClock(input: String): Long? {
    val parts = input.trim().replace('.', ':').replace(',', ':').split(':')
    if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 4 }) return null
    val n = parts.map { it.toLong() }
    return when (n.size) {
        1 -> n[0] * 60
        2 -> if (n[1] < 60) n[0] * 60 + n[1] else null
        else -> if (n[1] < 60 && n[2] < 60) n[0] * 3600 + n[1] * 60 + n[2] else null
    }
}

// "4:05", "1:02:03": il tempo accanto alla barra del telecomando
fun clockLabel(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// Nome di una traccia audio o di sottotitoli: la lingua in italiano ("ita" -> "Italiano"), più il nome se dice altro
// ("Inglese · Commento"); "Traccia N" se Kodi non sa niente
fun trackLabel(language: String, name: String, number: Int): String {
    val lang = language.trim().takeIf { it.isNotEmpty() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ITALIAN).takeIf { d -> d.isNotBlank() && d != it } ?: it }
        ?.replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    val extra = name.trim().takeIf { it.isNotEmpty() && !it.equals(lang, ignoreCase = true) && !it.equals(language, ignoreCase = true) }
    return listOfNotNull(lang, extra).joinToString(" · ").ifEmpty { "Traccia $number" }
}
