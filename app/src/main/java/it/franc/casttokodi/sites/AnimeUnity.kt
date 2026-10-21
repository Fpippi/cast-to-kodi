package it.franc.casttokodi.sites

import it.franc.casttokodi.domainOf

// Addon AnimeUnity: dopo l'invio si accodano da soli gli episodi successivi (#15).
// Il giro (pagina -> player VixCloud -> Kodi) lo fa MainActivity.queueNextEpisodes; qui ciò che dipende dal sito.

// Più di qualche episodio non serve: i link scadono dopo qualche ora
const val MAX_AUTO_NEXT = 3

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
      return { number: String(e.number), embed: q ? q.getAttribute('embed_url') : null };
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
