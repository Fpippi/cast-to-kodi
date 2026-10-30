package it.franc.casttokodi.sites

import it.franc.casttokodi.domainOf

// Addon AnimeSaturn: ⏭ con la coda finita, "Riprendi su Kodi" e il widget mandano l'episodio senza aprirlo.
// Il sito ha la scheda della serie (/anime/<slug>/) e una pagina per episodio (/<slug>-ep-<N>/, letta il 2026-10-30
// su animesaturn.ro: un WordPress) con il player in un iframe (/wp-content/themes/…/player/?source=…&url=…) che a sua
// volta apre in un iframe il player vero, su un altro dominio (cdn2.megaplay.su/e/<id>, non letto: bloccato dalla
// sessione di Claude). Il link del video quindi non si legge dalla pagina: lo prende il WebView invisibile di
// resumeOnKodi quando il player lo chiede. La struttura di prima (/ep/<Slug>-ep-<N> e il player JW in
// /watch?file=<id>, dalle estensioni open source per il sito) resta riconosciuta per i domini che la usano ancora.
// L'episodio dopo si ricava dall'indirizzo: -ep-<N+1>. Qui ciò che dipende dal sito.

private val EPISODE_PATH = Regex("/(ep/)?([^/]+)-ep-(\\d+)(/?)")
private val SERIES_PATH = Regex("/anime/([^/]+)/?")

// animesaturn.<qualunque> e il dominio nuovo anisaturn.<qualunque>
fun isAnimeSaturn(host: String?): Boolean = domainOf(host?.lowercase()).let { it.startsWith("animesaturn.") || it.startsWith("anisaturn.") }

// true sulla pagina di un episodio (/chainsaw-man-ita-ep-1/, o /ep/One-Piece-ep-1000): da lì l'app sa trovare il video senza aprirla
fun isAnimeSaturnEpisode(host: String?, path: String?): Boolean = isAnimeSaturn(host) && EPISODE_PATH.matches(path.orEmpty())

// La pagina del player (/watch?file=…), dove si guarda il video ma l'indirizzo non dice né serie né episodio
fun isAnimeSaturnWatch(host: String?, path: String?): Boolean = isAnimeSaturn(host) && path.orEmpty().trimEnd('/') == "/watch"

// La serie di una pagina, dalla scheda o da un episodio: "/chainsaw-man-ita-ep-1/" -> "/anime/chainsaw-man-ita".
// Resta uguale quando il sito cambia dominio. null fuori da AnimeSaturn o su altre pagine
fun animeSaturnSeries(host: String?, path: String?): String? {
    if (!isAnimeSaturn(host)) return null
    val slug = EPISODE_PATH.matchEntire(path.orEmpty())?.groupValues?.get(2) ?: SERIES_PATH.matchEntire(path.orEmpty())?.groupValues?.get(1)
    return slug?.let { "/anime/$it" }
}

// Il numero dell'episodio dall'indirizzo: "/chainsaw-man-ita-ep-12/" -> 12
fun animeSaturnNumber(path: String?): Int? = EPISODE_PATH.matchEntire(path.orEmpty())?.groupValues?.get(3)?.toIntOrNull()

// L'episodio dopo, nella stessa forma: "/chainsaw-man-ita-ep-1/" -> 2 e "/chainsaw-man-ita-ep-2/"
fun animeSaturnNext(path: String?): Pair<Int, String>? {
    val match = EPISODE_PATH.matchEntire(path.orEmpty()) ?: return null
    val (ep, slug, number, slash) = match.destructured
    val next = (number.toIntOrNull() ?: return null) + 1
    return next to "/$ep$slug-ep-$next$slash"
}

// Nome della serie dall'indirizzo, quando la pagina non lo dice: "/anime/chainsaw-man-ita" -> "chainsaw man ita"
fun animeSaturnSlugTitle(series: String): String =
    series.substringAfterLast('/').split('-').filter { it.isNotBlank() }.joinToString(" ")

// L'episodio a cui appartiene la pagina aperta: lei stessa se è un episodio, se è la pagina del player l'ultimo
// episodio aperto prima (history: gli indirizzi senza dominio della cronologia del WebView, dal più recente), perché
// ci si arriva da lì. null se non si sa
fun animeSaturnEpisodePath(host: String?, path: String?, history: List<String>): String? = when {
    isAnimeSaturnEpisode(host, path) -> path
    isAnimeSaturnWatch(host, path) -> history.firstOrNull { EPISODE_PATH.matches(it) }
    else -> null
}

// Eseguito nella pagina di un episodio (o del player /watch) di AnimeSaturn: il link del video. Con il player in un
// iframe (/player/?…, il sito di adesso) { frame: true }: il link lo chiede il player dentro l'iframe e lo prende il
// WebView invisibile. Con /watch?file= rilegge quella pagina (stessi cookie, niente Cloudflare); se non riesce
// restituisce il suo indirizzo, che l'app apre nel WebView invisibile per rieseguire questo script lì. Se la pagina
// non c'è (404: l'episodio dopo l'ultimo uscito) { missing }; senza player (la pagina di controllo di Cloudflare) { error }.
const val SATURN_STREAM_JS = """(function () {
  function stream(html) {
    var m = html.match(/file\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']/);
    if (m) return m[1];
    var s = new DOMParser().parseFromString(html, 'text/html').querySelector('video source[src], source[src]');
    return s ? s.getAttribute('src') : '';
  }
  function get(url) {
    var x = new XMLHttpRequest();
    x.open('GET', url, false);
    x.send();
    return x.status == 200 ? x.responseText : '';
  }
  try {
    if (location.pathname.replace(/\/$/, '') == '/watch') {
      var here = stream(document.documentElement.outerHTML);
      if (!here && location.search.indexOf('s=alt') < 0) return JSON.stringify({ watch: location.href + '&s=alt' });
      return JSON.stringify(here ? { stream: new URL(here, location.href).href, watch: location.href } : { error: 'stream' });
    }
    var a = document.querySelector('a[href*="/watch?file="], iframe[src*="/watch?file="]');
    if (!a && document.querySelector('iframe[src*="/player/"]')) return JSON.stringify({ frame: true });
    if (!a) {
      var status = 0;
      try { var x = new XMLHttpRequest(); x.open('HEAD', location.href, false); x.send(); status = x.status; } catch (e) {}
      return JSON.stringify(status == 404 ? { missing: true } : { error: 'watch' });
    }
    var first = a.href || a.src;
    var watch = first;
    var found = '';
    try {
      found = stream(get(watch));
      if (!found) { watch = watch + '&s=alt'; found = stream(get(watch)); }
    } catch (e) {}
    return JSON.stringify(found ? { stream: new URL(found, watch).href, watch: watch } : { watch: first });
  } catch (e) {
    return JSON.stringify({ error: String(e) });
  }
})()"""
