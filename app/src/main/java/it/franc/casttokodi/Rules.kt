package it.franc.casttokodi

import java.net.URLEncoder

// Regole dell'app scritte senza API Android (niente Uri, WebView, Context): così le provano i test
// in app/src/test, che girano sulla JVM a ogni PR verso main.

const val GOOGLE = "https://www.google.com"

// ponytail: elenco corto dei circuiti pubblicitari visti nei player; allungarlo quando se ne incontrano altri
private val AD_HOSTS = listOf("doubleclick", "googlesyndication", "imasdk", "adnxs", "adsrvr", "popads", "exoclick", "juicyads")

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
        ' ' in text || '.' !in text -> "$GOOGLE/search?q=${enc(text)}"
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
    // Dai risultati di Google si va su altri siti per forza. Non serve il tocco (hasGesture): Google apre
    // alcuni risultati da JS e il WebView non lo segna come gesto; su Google non ci sono salti pubblicitari
    if (allowedDomain.startsWith("google.")) return false
    return domainOf(host) != allowedDomain
}

// ponytail: riconosce per estensione più il caso VixCloud (AnimeUnity, StreamingCommunity), che serve
// la playlist come /playlist/<id>?token=... senza .m3u8; per altri siti così guardare il Content-Type
fun isVideoPath(path: String?): Boolean {
    val p = path?.lowercase() ?: return false
    return p.endsWith(".m3u8") || p.endsWith(".mp4") || p.startsWith("/playlist/")
}

fun isAd(host: String?, path: String?): Boolean {
    val h = host.orEmpty().lowercase()
    val p = path.orEmpty().lowercase()
    return AD_HOSTS.any { it in h } || h.split('.').any { it == "ads" || it == "ad" } ||
        "/vast" in p || "/ads/" in p
}

// Il video da proporre per un flusso intercettato, o null se è pubblicità o un pezzo della playlist VixCloud.
// host, path e query sono le parti già separate di url (query senza "?").
fun videoFor(url: String, referer: String, host: String?, path: String?, query: String?): Video? {
    val p = path.orEmpty().lowercase()
    val vixcloud = p.startsWith("/playlist/")
    val params = query.orEmpty().split('&').map { it.substringBefore('=') }
    return when {
        isAd(host, path) -> null
        // VixCloud: type=video/audio e rendition=720p sono le parti della playlist principale
        vixcloud && ("type" in params || "rendition" in params) -> null
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
