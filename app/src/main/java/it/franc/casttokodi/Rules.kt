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

// URLEncoder usa '+' per lo spazio, Kodi (e Google) vogliono %20
fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
