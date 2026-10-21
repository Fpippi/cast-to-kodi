package it.franc.casttokodi.sites

// Addon player VixCloud (AnimeUnity, StreamingCommunity e altri siti che lo incorporano).

// La playlist arriva come /playlist/<id>?token=... senza .m3u8
fun isVixCloudPlaylist(path: String?): Boolean = path.orEmpty().lowercase().startsWith("/playlist/")

// type=video/audio e rendition=720p sono le parti della playlist principale, da non proporre.
// params sono i nomi dei parametri della query.
fun isVixCloudPart(params: List<String>): Boolean = "type" in params || "rendition" in params

// Scadenza del token scritta nel link (expires=<secondi unix>), null se non c'è. query senza "?".
fun linkExpiry(query: String?): Long? =
    query.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "expires" }?.substringAfter('=')?.toLongOrNull()

// Eseguito nella pagina del player VixCloud: la playlist principale con i parametri del token, come la chiede il player
const val MASTER_PLAYLIST_JS = """(function () {
  var m = window.masterPlaylist;
  if (!m || !m.url) return null;
  var u = new URL(m.url);
  var p = m.params || {};
  for (var k in p) if (p[k] !== undefined && p[k] !== null && p[k] !== '') u.searchParams.set(k, p[k]);
  if (window.canPlayFHD) u.searchParams.set('h', '1');
  return u.toString();
})()"""
