package it.franc.casttokodi.sites

import it.franc.casttokodi.domainOf

// Addon AnimeSaturn: ⏭ con la coda finita, "Riprendi su Kodi" e il widget mandano l'episodio senza aprirlo.
// Il sito ha la scheda della serie (/anime/<Slug>), una pagina per episodio (/ep/<Slug>-ep-<N>) con il link alla pagina
// del player (/watch?file=<id>) e lì un player JW con il link del video (file: "...m3u8" o <source src>).
// L'episodio dopo si ricava dall'indirizzo: /ep/<Slug>-ep-<N+1>. Il giro (WebView invisibile -> Kodi) lo fa
// MainActivity.resumeOnKodi; qui ciò che dipende dal sito. Struttura presa dalle estensioni open source per il sito,
// non ancora provata sul sito vero.

private val EPISODE_PATH = Regex("/ep/(.+)-ep-(\\d+)/?")
private val SERIES_PATH = Regex("/anime/([^/]+)/?")

// animesaturn.<qualunque> e il dominio nuovo anisaturn.<qualunque>
fun isAnimeSaturn(host: String?): Boolean = domainOf(host?.lowercase()).let { it.startsWith("animesaturn.") || it.startsWith("anisaturn.") }

// true sulla pagina di un episodio (/ep/One-Piece-ep-1000): da lì l'app sa trovare il video senza aprirla
fun isAnimeSaturnEpisode(host: String?, path: String?): Boolean = isAnimeSaturn(host) && EPISODE_PATH.matches(path.orEmpty())

// La pagina del player (/watch?file=…), dove si guarda il video ma l'indirizzo non dice né serie né episodio
fun isAnimeSaturnWatch(host: String?, path: String?): Boolean = isAnimeSaturn(host) && path.orEmpty().trimEnd('/') == "/watch"

// La serie di una pagina, dalla scheda o da un episodio: "/ep/One-Piece-ep-1000" -> "/anime/One-Piece".
// Resta uguale quando il sito cambia dominio. null fuori da AnimeSaturn o su altre pagine
fun animeSaturnSeries(host: String?, path: String?): String? {
    if (!isAnimeSaturn(host)) return null
    val slug = EPISODE_PATH.matchEntire(path.orEmpty())?.groupValues?.get(1) ?: SERIES_PATH.matchEntire(path.orEmpty())?.groupValues?.get(1)
    return slug?.let { "/anime/$it" }
}

// Il numero dell'episodio dall'indirizzo: "/ep/One-Piece-ep-1000" -> 1000
fun animeSaturnNumber(path: String?): Int? = EPISODE_PATH.matchEntire(path.orEmpty())?.groupValues?.get(2)?.toIntOrNull()

// L'episodio dopo: "/ep/One-Piece-ep-1000" -> 1001 e "/ep/One-Piece-ep-1001"
fun animeSaturnNext(path: String?): Pair<Int, String>? {
    val match = EPISODE_PATH.matchEntire(path.orEmpty()) ?: return null
    val next = (match.groupValues[2].toIntOrNull() ?: return null) + 1
    return next to "/ep/${match.groupValues[1]}-ep-$next"
}

// Nome della serie dall'indirizzo, quando la pagina non lo dice: "/anime/Shingeki-no-Kyojin-ITA" -> "Shingeki no Kyojin ITA"
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

// Eseguito nella pagina di un episodio (o del player) di AnimeSaturn: il link del video. Dalla pagina dell'episodio
// rilegge quella del player (stessi cookie, niente Cloudflare); se non riesce (altro dominio) restituisce l'indirizzo
// del player, che l'app apre nel WebView invisibile per rieseguire questo script lì. Se la pagina non c'è (404:
// l'episodio dopo l'ultimo uscito) { missing }; senza link al player (la pagina di controllo di Cloudflare) { error }.
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
