package it.franc.casttokodi

import it.franc.casttokodi.sites.GOOGLE
import it.franc.casttokodi.sites.googleSearch
import it.franc.casttokodi.sites.isGoogle
import it.franc.casttokodi.sites.isVixCloudPart
import it.franc.casttokodi.sites.isVixCloudPlaylist
import java.net.URI
import java.net.URLEncoder

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
            val kind = when {
                main -> "Playlist principale"
                quality != null -> "Qualità ${quality}p"
                p.endsWith(".mp4") -> "Video MP4"
                else -> "Playlist"
            }
            Video(url, referer, "$kind\n${host.orEmpty()}", main)
        }
    }
}

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
    val lines = master.lines().map { it.trim() }
    if (lines.any { it.startsWith("#EXT-X-MEDIA:") && "TYPE=AUDIO" in it && "URI=" in it }) return null
    var best: String? = null
    var bestBandwidth = -1L
    lines.forEachIndexed { i, line ->
        if (!line.startsWith("#EXT-X-STREAM-INF:")) return@forEachIndexed
        val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
        val bandwidth = Regex("(?<![-A-Z])BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        if (bandwidth > bestBandwidth) {
            bestBandwidth = bandwidth
            best = uri
        }
    }
    return best?.let { URI(masterUrl).resolve(it).toString() }
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
