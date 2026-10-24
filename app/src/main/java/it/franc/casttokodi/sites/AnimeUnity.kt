package it.franc.casttokodi.sites

import it.franc.casttokodi.domainOf

// Addon AnimeUnity: dopo l'invio si accodano da soli gli episodi successivi (#15).
// Il giro (pagina -> player VixCloud -> Kodi) lo fa MainActivity.queueNextEpisodes; qui ciò che dipende dal sito.

// Più di qualche episodio non serve: i link scadono dopo qualche ora
const val MAX_AUTO_NEXT = 3

// Spento finché non lo si accende in ⋮ → Impostazioni (scelta di Pippo, 2026-10-22)
const val DEFAULT_AUTO_NEXT = 0

// Come si legge la scelta nelle Impostazioni
fun autoNextLabel(count: Int): String = when {
    count <= 0 -> "Spento"
    count == 1 -> "Accoda da solo l'episodio dopo"
    else -> "Accoda da solo i prossimi $count episodi"
}

private val EPISODE_PATH = Regex("/anime/[^/]+/\\d+/?")

// true sulla pagina di un episodio (animeunity.<qualunque>/anime/<titolo>/<id>): solo lì si cercano i successivi
fun isAnimeUnityEpisode(host: String?, path: String?): Boolean =
    domainOf(host).startsWith("animeunity.") && EPISODE_PATH.matches(path.orEmpty())

// Eseguito nella pagina dell'episodio AnimeUnity: rilegge la pagina (stessi cookie, niente Cloudflare), prende
// l'elenco degli episodi dall'elemento <video-player> e, per ognuno dei COUNT successivi, l'embed_url del suo player.
const val NEXT_EPISODES_JS = """(function (n) {
  function player(url) {
    var x = new XMLHttpRequest();
    x.open('GET', url, false);
    x.send();
    if (x.status != 200) return null;
    return new DOMParser().parseFromString(x.responseText, 'text/html').querySelector('video-player');
  }
  try {
    var p = player(location.href);
    if (!p) return JSON.stringify({ error: 'player' });
    var episodes = JSON.parse(p.getAttribute('episodes') || '[]');
    var id = location.pathname.replace(/\/$/, '').split('/').pop();
    var i = episodes.findIndex(function (e) { return String(e.id) == id; });
    if (i < 0) return JSON.stringify({ error: 'episode' });
    var base = location.pathname.replace(/\/$/, '').replace(/\/[^\/]*$/, '');
    var next = episodes.slice(i + 1, i + 1 + n).map(function (e) {
      var q = player(location.origin + base + '/' + e.id);
      return { id: String(e.id), number: String(e.number), embed: q ? q.getAttribute('embed_url') : null };
    });
    return JSON.stringify({ next: next });
  } catch (e) {
    return JSON.stringify({ error: String(e) });
  }
})(COUNT)"""

// Nome in coda di un episodio accodato da solo, dal nome della pagina di quello mandato:
// "JoJo Episodio 3" -> "JoJo Episodio 4"; se la pagina non dice il numero, lo si aggiunge
fun nextEpisodeTitle(current: String, number: String): String {
    val episode = Regex("(?i)\\b(episodio|ep\\.?)\\s*\\d+")
    val last = episode.findAll(current).lastOrNull() ?: return "$current · Episodio $number".trimStart(' ', '·')
    return current.replaceRange(last.range, "Episodio $number")
}

private val SERIES_PATH = Regex("(/anime/[^/]+)(?:/\\d+)?/?")

// La serie di una pagina AnimeUnity, con o senza episodio: "/anime/1234-jojo/56789" -> "/anime/1234-jojo".
// Resta uguale quando il sito cambia dominio, quindi è lei a riconoscere la serie (vedi Watching.kt). null fuori da AnimeUnity
fun animeUnitySeries(host: String?, path: String?): String? {
    if (!domainOf(host).startsWith("animeunity.")) return null
    return SERIES_PATH.matchEntire(path.orEmpty())?.groupValues?.get(1)
}

// Nome della serie dall'indirizzo, quando la pagina non lo dice: "/anime/1234-jojo-no-kimyou" -> "Jojo No Kimyou"
fun animeUnitySlugTitle(series: String): String =
    series.substringAfterLast('/').replace(Regex("^\\d+-"), "").split('-').filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

// Eseguito nella pagina di un episodio AnimeUnity, come NEXT_EPISODES_JS: nome della serie, numero dell'episodio,
// quanti sono, quello dopo (id e numero) e il player di questo (embed_url), per "Continua a guardare".
const val EPISODE_INFO_JS = """(function () {
  try {
    var x = new XMLHttpRequest();
    x.open('GET', location.href, false);
    x.send();
    if (x.status != 200) return JSON.stringify({ error: 'http' });
    var p = new DOMParser().parseFromString(x.responseText, 'text/html').querySelector('video-player');
    if (!p) return JSON.stringify({ error: 'player' });
    var episodes = JSON.parse(p.getAttribute('episodes') || '[]');
    var anime = {};
    try { anime = JSON.parse(p.getAttribute('anime') || '{}'); } catch (e) {}
    var id = location.pathname.replace(/\/$/, '').split('/').pop();
    var i = episodes.findIndex(function (e) { return String(e.id) == id; });
    if (i < 0) {
      // Pagina della serie (/anime/1234-jojo), senza episodio nell'indirizzo: quello che il player ha aperto
      try {
        var current = JSON.parse(p.getAttribute('episode') || 'null');
        if (current) i = episodes.findIndex(function (e) { return String(e.id) == String(current.id); });
      } catch (e) {}
    }
    var next = i >= 0 && i + 1 < episodes.length ? episodes[i + 1] : null;
    return JSON.stringify({
      title: anime.title_eng || anime.title || anime.title_it || '',
      id: i >= 0 ? String(episodes[i].id) : '',
      number: i >= 0 ? String(episodes[i].number) : '',
      count: anime.episodes_count || episodes.length,
      next: next ? { id: String(next.id), number: String(next.number) } : null,
      embed: p.getAttribute('embed_url') || ''
    });
  } catch (e) {
    return JSON.stringify({ error: String(e) });
  }
})()"""
