package it.franc.casttokodi

import it.franc.casttokodi.sites.animeUnitySeries
import it.franc.casttokodi.sites.animeUnitySlugTitle
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

// "Continua a guardare" nella pagina iniziale (2026-10-24): le serie mandate a Kodi, ognuna con gli ultimi episodi e
// a che punto sono. Niente android.*: lo prova WatchingTest. L'Activity lo salva in prefs["shows"] (showsToJson).

// Le 12 serie più recenti e, per ognuna, gli ultimi 6 episodi toccati
const val MAX_SHOWS = 12
const val MAX_EPISODES = 6

// Sotto i 30 secondi non si riprende dal punto: si riparte dall'inizio
const val RESUME_MIN = 30L

// "Salta sigla" del telecomando: va avanti di tanto da dove si è, perché la sigla non parte sempre allo stesso punto
// (scelta di Pippo, 2026-10-24). Le sigle degli anime durano quasi sempre 1:30 e il tasto si preme a sigla già partita,
// quindi un po' meno: è il primo proposto quando si scrive il tempo a mano. Il tempo vale per la serie, o per tutti i
// video se non è di una serie nota, e si sceglie al primo tocco
const val DEFAULT_INTRO = 85
val INTRO_CHOICES = listOf(30, 45, 60, 75, 80, 85, 90, 95, 100, 110, 120)
const val MAX_INTRO = 600

// Il tempo scritto a mano per "Salta sigla": "1:25" o solo i secondi ("85", non minuti come in "Vai al minuto").
// null se non è un tempo o se è fuori da 1 secondo – 10 minuti
fun parseIntro(input: String): Int? {
    val text = input.trim().replace('.', ':').replace(',', ':')
    val seconds = text.toIntOrNull() ?: parseClock(text)?.takeIf { ':' in text }?.toInt() ?: return null
    return seconds.takeIf { it in 1..MAX_INTRO }
}

// "45 secondi", "1:25" nell'elenco di Salta sigla
fun introTime(seconds: Int): String = if (seconds < 60) "$seconds secondi" else clockLabel(seconds.toLong())

// Eseguito nella pagina di un episodio (non AnimeUnity, che la dà con EPISODE_INFO_JS): l'immagine di anteprima che il
// sito dichiara per la condivisione (og:image), di solito la copertina della serie. Stringa vuota se non c'è
const val PAGE_IMAGE_JS = """(function () {
  var m = document.querySelector('meta[property="og:image"], meta[name="og:image"], meta[name="twitter:image"]');
  return m ? m.content : '';
})()"""

// Un episodio come lo dice la pagina. key è la serie: il nome del sito senza dominio (i siti pirata lo cambiano spesso)
// più, su AnimeUnity, la parte dell'indirizzo che resta uguale ("/anime/1234-jojo"), sugli altri siti il nome della
// serie letto dal titolo. path è l'indirizzo senza dominio, con la query (StreamingCommunity: /watch/12?e=34).
class EpisodePage(val key: String, val site: String, val series: String, val season: Int?, val number: Int?, val path: String)

// seen: quando è stato mandato a Kodi o visto in riproduzione (ms); 0 se è solo in coda, che non lo rende quello in corso.
// file: il link mandato a Kodi, per riconoscerlo nel mini-player. time e total in secondi
data class Episode(
    val season: Int?,
    val number: Int?,
    val path: String,
    val file: String? = null,
    val time: Long = 0,
    val total: Long = 0,
    val watched: Boolean = false,
    val seen: Long = 0,
    // Fotogramma di questo episodio (file in filesDir/serie), se si è riusciti a prenderlo dal video
    val image: String? = null,
)

// host: il dominio dell'ultimo invio, per riaprirla se il sito non si è più visto. episodes: dal meno recente.
// count: episodi in tutto e next: quello dopo, solo se il sito lo dice (AnimeUnity). image: fotogramma JPEG in base64
data class Show(
    val key: String,
    val site: String,
    val host: String,
    val title: String,
    val episodes: List<Episode>,
    val count: Int? = null,
    val next: Episode? = null,
    val image: String? = null,
    val updated: Long = 0,
    val intro: Int? = null,
) {
    // L'ultimo mandato o visto su Kodi
    val current: Episode get() = episodes.filter { it.seen > 0 }.maxByOrNull { it.seen } ?: episodes.last()

    // Immagine della scheda: il fotogramma dell'episodio a cui si è (scelta di Pippo, 2026-10-24), se no quella della serie
    val picture: String? get() = current.image ?: image

    // Visto l'ultimo episodio: resta in fila con "Finita" finché non la si toglie (scelta di Pippo)
    val finished: Boolean get() = current.let { it.watched && it.season == null && it.number != null && count != null && it.number >= count }

    // Da guardare adesso: quello in corso se non è finito, se no il successivo; null se non si sa dove sia
    val upNext: Episode? get() {
        val c = current
        if (!c.watched) return c
        if (finished || c.number == null) return null
        return episodes.firstOrNull { it.season == c.season && it.number == c.number + 1 }
            ?: next?.takeIf { it.season == c.season && (it.number == null || it.number > c.number) }
    }

    // Gli episodi del pannello: quello prima, quello in corso e il prossimo, in ordine
    val nearby: List<Episode> get() {
        val c = current
        val before = episodes.filter { it !== c && it.season == c.season && it.number != null && c.number != null && it.number < c.number }
            .maxByOrNull { it.number!! }
        val after = episodes.firstOrNull { c.number != null && it.season == c.season && it.number == c.number + 1 }
            ?: upNext?.takeIf { it !== c } ?: next?.takeIf { n -> c.number != null && n.number != null && n.number > c.number }
        return listOfNotNull(before, c, after)
    }
}

// "animeunity.so" e "www.animeunity.to" -> "animeunity"
fun siteOf(host: String?): String = domainOf(host?.lowercase()).substringBefore('.')

private val SEASON_WORD = Regex("(?i)\\bstagione\\s*(\\d{1,2})\\D{0,12}?\\b(?:episodio|ep\\.?)\\s*(\\d{1,4})\\b")
private val SEASON_EPISODE = Regex("(?i)\\bS(\\d{1,2})\\s*[ ·.:x-]?\\s*E(?:p\\.?\\s*)?(\\d{1,4})\\b")
private val EPISODE_WORD = Regex("(?i)\\b(?:episodio|episode|ep\\.?)\\s*(\\d{1,4})\\b")
private const val SEPARATORS = " -|–—:·,()[]"

// L'episodio di una pagina: su AnimeUnity dall'indirizzo, sugli altri siti dal titolo ("Episodio 5", "S2E5",
// "Stagione 2 Episodio 5"). null se la pagina non sembra un episodio. Su AnimeUnity vale anche la pagina della serie
// (/anime/1234-jojo), che ha già il player: il numero lo dice poi il player (EPISODE_INFO_JS)
fun episodeFromPage(host: String?, path: String?, query: String?, title: String?): EpisodePage? {
    val site = siteOf(host).ifBlank { return null }
    // AnimeUnity mette il suo nome davanti: "AnimeUnity ~ Le Bizzarre Avventure di JoJo"
    val text = title.orEmpty().replace(Regex("\\s+"), " ").trim().replace(Regex("(?i)^animeunity\\s*~\\s*"), "")
    val fullPath = path.orEmpty().ifEmpty { "/" } + (query?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: "")
    val season = SEASON_WORD.find(text) ?: SEASON_EPISODE.find(text)
    val match = season ?: EPISODE_WORD.find(text)
    val seasonNumber = season?.groupValues?.get(1)?.toIntOrNull()
    val number = match?.groupValues?.last()?.toIntOrNull()
    val before = match?.let { text.substring(0, it.range.first).trim { c -> c in SEPARATORS } }.orEmpty()

    val series = animeUnitySeries(host, path)
    if (series != null) {
        val name = before.ifBlank { suggestedName(text.takeIf { match == null }, null).takeIf { it.isNotBlank() } ?: animeUnitySlugTitle(series) }
        return EpisodePage("$site:$series", site, name, seasonNumber, number, fullPath)
    }
    if (match == null || before.isBlank()) return null
    return EpisodePage("$site:${seriesKey(before)}", site, before, seasonNumber, number, fullPath)
}

// "Breaking Bad" e "breaking  bad!" sono la stessa serie
private fun seriesKey(name: String) = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

// La serie di una pagina già nota, anche se non è un episodio (la scheda della serie su AnimeUnity): per l'avviso
// "Eri all'episodio 12" quando ci si arriva dal sito o da Google
fun showOfPage(shows: List<Show>, host: String?, path: String?, query: String?, title: String?): Show? {
    val key = animeUnitySeries(host, path)?.let { "${siteOf(host)}:$it" } ?: episodeFromPage(host, path, query, title)?.key ?: return null
    return shows.firstOrNull { it.key == key }
}

// Visto: passato il 95%, oppure a meno di 2 minuti dalla fine (la sigla di un anime da 24 minuti dura di più dei 72
// secondi che lascia il 95%). I 2 minuti solo dai 10 minuti in su, se no una clip sarebbe vista appena partita
fun isWatched(time: Long, total: Long): Boolean =
    total > 0 && (time * 100 >= total * 95 || (total >= 600 && total - time <= 120))

// Un episodio appena mandato a Kodi. playing: è partito ("Manda a Kodi") e diventa quello in corso; false se è solo
// in coda. Rimandare un episodio già visto lo fa ricominciare; uno a metà tiene il punto, per riprenderlo.
fun withSent(
    shows: List<Show>, page: EpisodePage, host: String, file: String, now: Long, playing: Boolean,
    count: Int? = null, next: EpisodePage? = null, image: String? = null,
): List<Show> {
    val old = shows.firstOrNull { it.key == page.key }
    val oldEpisodes = old?.episodes.orEmpty()
    val existing = oldEpisodes.firstOrNull { same(it, page) }
    val restart = existing?.watched == true && playing
    val episode = (existing ?: Episode(page.season, page.number, page.path)).copy(
        path = page.path,
        file = file,
        time = if (restart) 0 else existing?.time ?: 0,
        watched = if (restart) false else existing?.watched ?: false,
        seen = if (playing) now else existing?.seen ?: 0,
    )
    val episodes = (oldEpisodes.filter { it !== existing } + episode).takeLast(MAX_EPISODES)
    val show = Show(
        key = page.key,
        site = page.site,
        host = host,
        title = old?.title ?: page.series,
        episodes = episodes,
        count = count ?: old?.count,
        next = if (playing && next != null) Episode(next.season, next.number, next.path) else old?.next,
        image = image ?: old?.image,
        updated = now,
        intro = old?.intro,
    )
    return (listOf(show) + shows.filter { it.key != page.key }).take(MAX_SHOWS)
}

private fun same(e: Episode, page: EpisodePage) =
    if (e.number != null && page.number != null) e.number == page.number && e.season == page.season else e.path == page.path

// Quello che arriva dopo l'invio: dal player di AnimeUnity il nome della serie, quanti episodi sono e quello dopo;
// il fotogramma dal video. null lascia com'era
// path e number: il numero dell'episodio a quell'indirizzo, se il titolo della pagina non lo diceva; at: il suo
// indirizzo vero, se è stato mandato dalla pagina della serie di AnimeUnity
fun withDetails(
    shows: List<Show>, key: String, title: String? = null, image: String? = null, count: Int? = null, next: Episode? = null,
    path: String? = null, number: Int? = null, at: String? = null,
): List<Show> = shows.map { show ->
    if (show.key != key) show
    else show.copy(
        title = title?.takeIf { it.isNotBlank() } ?: show.title,
        image = image ?: show.image,
        count = count ?: show.count,
        next = next ?: show.next,
        episodes = show.episodes.map { if (number != null && it.number == null && it.path == path) it.copy(number = number, path = at ?: it.path) else it },
    )
}

// Il fotogramma preso dal video mandato (file): va all'episodio di quel video. null se nessuna serie lo ha
fun withEpisodeImage(shows: List<Show>, file: String, image: String): List<Show>? {
    val key = queueKey(file)
    if (shows.none { s -> s.episodes.any { it.file != null && queueKey(it.file) == key } }) return null
    return shows.map { show ->
        show.copy(episodes = show.episodes.map { if (it.file != null && queueKey(it.file) == key) it.copy(image = image) else it })
    }
}

// I file delle immagini ancora usati (serie ed episodi): gli altri si possono cancellare
fun usedImages(shows: List<Show>): Set<String> =
    (shows.mapNotNull { it.image } + shows.flatMap { s -> s.episodes.mapNotNull { it.image } }).toSet()

// Kodi sta riproducendo file a time su total (lettura del mini-player): l'episodio diventa quello in corso e prende il
// punto. Se Kodi è passato da solo a quello dopo, quello prima conta come visto. null se il video non è di una
// serie nota o se non è cambiato abbastanza da salvarlo (meno di 10 secondi)
fun withProgress(shows: List<Show>, file: String, time: Long, total: Long, now: Long): List<Show>? {
    val key = queueKey(file)
    val show = shows.firstOrNull { s -> s.episodes.any { it.file != null && queueKey(it.file) == key } } ?: return null
    val episode = show.episodes.first { it.file != null && queueKey(it.file) == key }
    val previous = show.current
    val watched = episode.watched || isWatched(time, total)
    val moved = previous !== episode
    if (!moved && watched == episode.watched && kotlin.math.abs(time - episode.time) < 10 && total == episode.total) return null
    val updated = episode.copy(time = time, total = total, watched = watched, seen = now)
    val episodes = show.episodes.map {
        when {
            it === episode -> updated
            // Passato da solo al successivo: quello prima è finito
            it === previous && moved && previous.season == episode.season && previous.number != null &&
                episode.number != null && previous.number < episode.number -> it.copy(watched = true)
            else -> it
        }
    }
    val changed = show.copy(episodes = episodes, updated = now)
    return listOf(changed) + shows.filter { it.key != show.key }
}

// Il punto salvato da Kodi per un episodio (quando l'app era chiusa): senza cambiare quello in corso
fun withSaved(shows: List<Show>, file: String, time: Long, total: Long, played: Boolean): List<Show>? {
    val key = queueKey(file)
    var changed = false
    val result = shows.map { show ->
        show.copy(episodes = show.episodes.map {
            if (it.file == null || queueKey(it.file) != key || it.watched) it
            else {
                val watched = played || isWatched(time, total)
                if (!watched && (time <= it.time || total <= 0)) it
                else it.copy(time = if (watched) it.time else time, total = if (total > 0) total else it.total, watched = watched)
                    .also { changed = true }
            }
        })
    }
    return if (changed) result else null
}

// La serie del video che Kodi sta riproducendo, se è stato mandato dall'app
fun showOfFile(shows: List<Show>, file: String?): Show? {
    val key = file?.let { queueKey(it) } ?: return null
    return shows.firstOrNull { s -> s.episodes.any { it.file != null && queueKey(it.file) == key } }
}

// Di quanto salta "Salta sigla" con questo video: quello della serie, o general (per tutti) se il video non è di una
// serie nota. null se non è ancora stato scelto: il tasto lo chiede
fun introOf(shows: List<Show>, file: String?, general: Int?): Int? {
    val show = showOfFile(shows, file) ?: return general
    return show.intro
}

fun withIntro(shows: List<Show>, key: String, seconds: Int): List<Show> = shows.map { if (it.key == key) it.copy(intro = seconds) else it }

// ⏭ del telecomando con la coda finita: l'episodio dopo quello che Kodi sta riproducendo (file), se l'app lo conosce
fun episodeAfter(show: Show, file: String?): Episode? {
    val key = file?.let { queueKey(it) } ?: return null
    val playing = show.episodes.firstOrNull { it.file != null && queueKey(it.file) == key } ?: return null
    val number = playing.number ?: return null
    if (show.count != null && playing.season == null && number >= show.count) return null
    return show.episodes.firstOrNull { it.season == playing.season && it.number == number + 1 }
        ?: show.next?.takeIf { it.season == playing.season && it.number == number + 1 }
}

fun introLabel(seconds: Int?): String = if (seconds == null) "Salta sigla" else "Salta sigla (+${clockLabel(seconds.toLong())})"

fun withoutShow(shows: List<Show>, key: String): List<Show> = shows.filter { it.key != key }

// "Segna come visto": l'episodio in corso, così la scheda passa al prossimo
fun withCurrentWatched(shows: List<Show>, key: String): List<Show> = shows.map { show ->
    if (show.key != key) show else { val c = show.current; show.copy(episodes = show.episodes.map { if (it === c) it.copy(watched = true) else it }) }
}

// Da dove riprendere un episodio che si sta per mandare: il punto se era a metà, altrimenti null (dall'inizio)
fun resumePoint(shows: List<Show>, site: String, path: String): Long? {
    val episode = shows.filter { it.site == site }.flatMap { it.episodes }.firstOrNull { it.path == path } ?: return null
    return episode.time.takeIf { !episode.watched && it >= RESUME_MIN && (episode.total == 0L || it < episode.total) }
}

// L'indirizzo per riaprire un episodio: con l'ultimo dominio del sito che ha funzionato (hosts: sito -> dominio,
// aggiornato a ogni pagina), così un cambio di dominio non perde la serie
fun episodeUrl(show: Show, episode: Episode, hosts: Map<String, String>): String =
    "https://${hosts[show.site] ?: show.host}${episode.path}"

// "Ep. 12", "S2 · Ep. 5", "Episodio" se il numero non si sa
fun episodeLabel(e: Episode): String = when {
    e.number == null -> "Episodio"
    e.season != null -> "S${e.season} · Ep. ${e.number}"
    else -> "Ep. ${e.number}"
}

// "al 9", "all'8", "all'11"
fun toEpisode(n: Int): String = if (n == 1 || n == 11 || n.toString().startsWith("8")) "all'$n" else "al $n"

// Cosa dice la scheda: la riga sotto il nome, l'etichetta sull'immagine (null se niente) e quanto è visto (0..1)
class ShowCard(val note: String, val badge: String?, val progress: Float)

fun showCard(show: Show): ShowCard {
    val c = show.current
    return when {
        show.finished -> ShowCard("${c.number} di ${show.count}", null, 1f)
        !c.watched && c.time >= RESUME_MIN ->
            ShowCard("${episodeLabel(c)} · fermo a ${clockLabel(c.time)}",
                if (c.total > 0) "${clockLabel(c.time)} / ${clockLabel(c.total)}" else null,
                if (c.total > 0) (c.time.toFloat() / c.total).coerceIn(0f, 1f) else 0f)
        !c.watched -> ShowCard(episodeLabel(c), null, 0f)
        c.number != null -> ShowCard(
            (if (c.season != null) "Visto S${c.season} · Ep. ${c.number}" else "Visto il ${c.number}") + " · tocca ${toEpisode(c.number + 1)}",
            "Prossimo", 0f)
        else -> ShowCard("Visto", null, 0f)
    }
}

// Avviso sotto la barra sul sito: titolo e dettaglio, null se non serve (serie finita, prossimo episodio sconosciuto,
// o la pagina è già quello da guardare)
fun resumeBanner(show: Show, pagePath: String): Pair<String, String>? {
    val target = show.upNext ?: return null
    if (target.path == pagePath || target.number == null) return null
    return if (!target.watched && target === show.current && target.time >= RESUME_MIN) {
        "Eri all'episodio ${target.number}" to "fermo a ${clockLabel(target.time)}"
    } else if (target === show.current) {
        "Eri all'episodio ${target.number}" to show.title
    } else {
        "Tocca all'episodio ${target.number}" to "hai visto il ${show.current.number}"
    }
}

// L'ultima pagina si mostra solo se non è già un episodio della fila delle serie
fun lastPageShown(url: String?, shows: List<Show>): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return true
    val site = siteOf(uri.host)
    val path = uri.rawPath.orEmpty().ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
    return shows.none { show -> show.site == site && show.episodes.any { it.path == path } }
}

fun showsToJson(shows: List<Show>): String = JSONArray(shows.map { s ->
    JSONObject().put("key", s.key).put("site", s.site).put("host", s.host).put("title", s.title)
        .put("episodes", JSONArray(s.episodes.map { episodeJson(it) }))
        .putOpt("count", s.count).putOpt("next", s.next?.let { episodeJson(it) })
        .putOpt("image", s.image).put("updated", s.updated).putOpt("intro", s.intro)
}).toString()

private fun episodeJson(e: Episode) = JSONObject().putOpt("season", e.season).putOpt("number", e.number).put("path", e.path)
    .putOpt("file", e.file).put("time", e.time).put("total", e.total).put("watched", e.watched).put("seen", e.seen)
    .putOpt("image", e.image)

fun showsFromJson(text: String?): List<Show> = try {
    val array = JSONArray(text ?: "[]")
    List(array.length()) { array.getJSONObject(it) }.mapNotNull { o ->
        val episodes = o.optJSONArray("episodes")?.let { a -> List(a.length()) { episodeOf(a.getJSONObject(it)) } }.orEmpty()
        if (episodes.isEmpty()) return@mapNotNull null
        Show(
            key = o.getString("key"), site = o.getString("site"), host = o.getString("host"), title = o.getString("title"),
            episodes = episodes,
            count = if (o.has("count")) o.getInt("count") else null,
            next = o.optJSONObject("next")?.let { episodeOf(it) },
            image = o.optString("image").ifEmpty { null },
            updated = o.optLong("updated"),
            intro = if (o.has("intro")) o.getInt("intro") else null,
        )
    }
} catch (e: Exception) {
    emptyList()
}

private fun episodeOf(o: JSONObject) = Episode(
    season = if (o.has("season")) o.getInt("season") else null,
    number = if (o.has("number")) o.getInt("number") else null,
    path = o.getString("path"),
    file = o.optString("file").ifEmpty { null },
    time = o.optLong("time"),
    total = o.optLong("total"),
    watched = o.optBoolean("watched"),
    seen = o.optLong("seen"),
    image = o.optString("image").ifEmpty { null },
)
