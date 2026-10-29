package it.franc.casttokodi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.ceil

// Salta sigla al punto giusto (#150, 2026-10-28): i tempi di sigla, riassunto e titoli di coda degli anime vengono da
// AniSkip, un archivio pubblico e gratuito dove li segnano gli utenti, cercati per id MyAnimeList + numero
// dell'episodio, senza chiave. L'id lo dà la pagina della serie (ogni stagione e ogni parte ha il suo); se manca si
// cerca la serie su AniList, anche lei senza chiave. Niente android.*: lo prova AniSkipTest.
// Spento da ⋮ → Impostazioni (prefs["aniskip"]): allora né AniSkip né AniList sentono niente dall'app.

const val ANISKIP_API = "https://api.aniskip.com/v2"
const val ANILIST_API = "https://graphql.anilist.co"

// Di quanto può differire la durata del video su Kodi da quella dell'episodio su cui sono stati segnati i tempi:
// oltre, il sito ha un'altra versione (un logo all'inizio, un taglio) e i tempi non valgono
const val SKIP_LENGTH_SLACK = 10.0

// In automatico il tasto si accende da 5 secondi prima di un tratto (riassunto, sigla, titoli di coda) fino alla sua
// fine, e solo lì: un tocco prima non fa perdere il pezzo di episodio che c'è prima (Pippo, 2026-10-29)
const val SKIP_LEAD = 5

// Show.malId quando Pippo ha scelto "Nessuna" nel pannello della serie: AniSkip non si chiede più
const val NO_MAL = 0

// Rilettura dei tempi: se AniSkip non ha risposto (rete) dopo 10 minuti, se non li aveva dopo 3 giorni (qualcuno può
// segnarli intanto)
private const val RETRY_FAILED_MS = 10 * 60 * 1000L
private const val RETRY_MISSING_MS = 3 * 24 * 3600 * 1000L

enum class PartType(val api: String) { INTRO("op"), RECAP("recap"), CREDITS("ed") }

// Un tratto da saltare, in secondi; length: la durata dell'episodio su cui è stato segnato
data class SkipPart(val type: PartType, val from: Double, val to: Double, val length: Double)

// Una serie trovata su AniList
data class AnimeMatch(val malId: Int, val title: String, val other: String?, val year: Int?, val episodes: Int?, val cover: String?)

fun aniSkipUrl(malId: Int, episode: Int, base: String = ANISKIP_API): String {
    val types = listOf("op", "ed", "mixed-op", "mixed-ed", "recap").joinToString("&") { "types%5B%5D=$it" }
    return "$base/skip-times/$malId/$episode?$types&episodeLength=0"
}

// La risposta di AniSkip: i tratti, uno per tipo; vuota se l'episodio non c'è. null se non è una risposta di AniSkip
fun parseAniSkip(text: String): List<SkipPart>? = try {
    val json = JSONObject(text)
    if (!json.optBoolean("found")) emptyList()
    else {
        val results = json.optJSONArray("results") ?: JSONArray()
        List(results.length()) { results.getJSONObject(it) }.mapNotNull { r ->
            val type = when (r.optString("skipType")) {
                "op", "mixed-op" -> PartType.INTRO
                "ed", "mixed-ed" -> PartType.CREDITS
                "recap" -> PartType.RECAP
                else -> return@mapNotNull null
            }
            val interval = r.optJSONObject("interval") ?: return@mapNotNull null
            val from = interval.optDouble("startTime", Double.NaN)
            val to = interval.optDouble("endTime", Double.NaN)
            if (from.isNaN() || to.isNaN() || to <= from) null else SkipPart(type, from, to, r.optDouble("episodeLength", 0.0))
        }.distinctBy { it.type }
    }
} catch (e: Exception) {
    null
}

// I tratti che valgono per il video su Kodi, lungo total secondi: senza durata (diretta) nessuno
fun partsFor(parts: List<SkipPart>, total: Long): List<SkipPart> =
    if (total <= 0) emptyList() else parts.filter { it.length <= 0 || abs(it.length - total) <= SKIP_LENGTH_SLACK }

// Cosa fa "Salta sigla" adesso
sealed class SkipPress {
    // Vai a quel punto: la fine della sigla, del riassunto o dei titoli di coda
    data class To(val type: PartType, val seconds: Long) : SkipPress()

    // Avanti di tanto da dove si è (il salto di prima, quando i tempi non ci sono)
    data class Forward(val seconds: Int) : SkipPress()

    // Né tempi né salto scelto: il tasto chiede di quanto
    object Choose : SkipPress()

    // Automatico, fuori dai tratti: il tasto è spento (la pressione lunga dice dove funziona). next: il prossimo tratto
    data class Off(val next: SkipPart?) : SkipPress()
}

// Dove porta il tasto a time su total. Automatico (auto, coi tempi di AniSkip): dentro un tratto, da SKIP_LEAD secondi
// prima, alla sua fine (i titoli di coda che arrivano alla fine: all'ultimo secondo, così Kodi passa al video dopo in
// coda); fuori, spento. Manuale, o automatico senza tempi buoni per questo video: il salto fisso (forward)
fun skipPress(parts: List<SkipPart>, time: Long, total: Long, forward: Int?, auto: Boolean = true): SkipPress {
    val fit = if (auto) partsFor(parts, total) else emptyList()
    if (fit.isEmpty()) return if (forward != null) SkipPress.Forward(forward) else SkipPress.Choose
    val part = fit.filter { time >= it.from - SKIP_LEAD && time < it.to - 1 }.minByOrNull { it.from }
    if (part != null) return SkipPress.To(part.type, ceil(part.to).toLong().coerceAtMost(total - 1))
    return SkipPress.Off(fit.filter { it.from > time }.minByOrNull { it.from })
}

fun partName(type: PartType): String = when (type) {
    PartType.INTRO -> tr("Sigla", "Intro")
    PartType.RECAP -> tr("Riassunto", "Recap")
    PartType.CREDITS -> tr("Titoli di coda", "Credits")
}

// La riga sotto il tasto del telecomando
fun skipHint(press: SkipPress): String = when (press) {
    is SkipPress.To -> tr("Porta a ${clockLabel(press.seconds)}", "Jumps to ${clockLabel(press.seconds)}")
    is SkipPress.Off -> when (press.next?.type) {
        PartType.INTRO -> tr("La sigla parte alle ${clockLabel(press.next.from.toLong())} · tieni premuto", "The intro starts at ${clockLabel(press.next.from.toLong())} · hold")
        PartType.RECAP -> tr("Il riassunto parte alle ${clockLabel(press.next.from.toLong())} · tieni premuto", "The recap starts at ${clockLabel(press.next.from.toLong())} · hold")
        PartType.CREDITS -> tr("I titoli di coda partono alle ${clockLabel(press.next.from.toLong())} · tieni premuto", "The credits start at ${clockLabel(press.next.from.toLong())} · hold")
        null -> tr("Niente da saltare fino alla fine · tieni premuto", "Nothing to skip until the end · hold")
    }
    else -> ""
}

// I tratti da segnare sulla barra del tempo, come frazioni del video (da, a)
fun partMarks(parts: List<SkipPart>, total: Long, auto: Boolean): List<Pair<Float, Float>> =
    if (!auto || total <= 0) emptyList()
    else partsFor(parts, total).map { (it.from / total).toFloat().coerceIn(0f, 1f) to (it.to / total).toFloat().coerceIn(0f, 1f) }

fun skipLabel(press: SkipPress): String = when (press) {
    is SkipPress.To -> when (press.type) {
        PartType.INTRO -> tr("Salta sigla", "Skip intro")
        PartType.RECAP -> tr("Salta riassunto", "Skip recap")
        PartType.CREDITS -> tr("Salta titoli di coda", "Skip credits")
    }
    is SkipPress.Forward -> introLabel(press.seconds)
    SkipPress.Choose, is SkipPress.Off -> introLabel(null)
}

// Lo stesso nome senza il tempo, per la tendina e il widget, dove c'è poco posto
fun skipShortLabel(press: SkipPress): String = if (press is SkipPress.To) skipLabel(press) else tr("Salta sigla", "Skip intro")

// Modalità e salto manuale, per le righe del pannello della serie e di Impostazioni
fun skipModeLabel(auto: Boolean, manual: Int?): String =
    if (auto) tr("Automatico (AniSkip)", "Automatic (AniSkip)")
    else manual?.let { tr("Manuale · avanti di ${introTime(it)}", "Manual · forward ${introTime(it)}") } ?: tr("Manuale", "Manual")

// "L'episodio 13 del sito è il 1 su MyAnimeList"
fun shiftLabel(number: Int, shift: Int): String =
    tr("L'episodio $number del sito è il ${number - shift} su MyAnimeList", "Episode $number on the site is ${number - shift} on MyAnimeList")

// Il numero dell'episodio per AniSkip, che conta come MyAnimeList: da 1 per ogni stagione e parte. Un sito può
// continuare la numerazione (la parte 2 da 13): se il numero supera gli episodi della serie, si conta dal primo
// dell'elenco della pagina. first: il numero del primo episodio elencato; count: gli episodi della serie
fun aniSkipShift(first: Int?, number: Int?, count: Int?): Int =
    if (first != null && first > 1 && number != null && count != null && count > 0 && number > count) first - 1 else 0

private val SECOND_PART = Regex("(?i)\\b(part|parte|cour)\\s*(2|ii)\\b")

// Lo spostamento proposto quando si sa quanti episodi ha la serie su MyAnimeList (malEpisodes): i siti a volte
// dividono una stagione in due ("part 1" e "part 2") che su MyAnimeList è una sola. first..first+count-1: gli
// episodi della pagina. Numeri già dentro la serie di MyAnimeList: nessuno spostamento; parte 2 numerata da 1: va
// in fondo alla stagione (24 episodi, 12 sulla pagina: l'1 è il 13, spostamento -12); numeri oltre la fine della
// serie (il sito continua da 13 e MyAnimeList ha la parte a sé): si conta dal primo
fun suggestShift(title: String, first: Int?, count: Int?, malEpisodes: Int?): Int {
    if (malEpisodes == null || count == null || count <= 0) return aniSkipShift(first, first, count)
    val start = first ?: 1
    val last = start + count - 1
    return when {
        start == 1 && SECOND_PART.containsMatchIn(title) && count < malEpisodes -> count - malEpisodes
        last <= malEpisodes -> 0
        start > 1 -> start - 1
        else -> 0
    }
}

fun isSecondPart(title: String) = SECOND_PART.containsMatchIn(title)

// Lo stesso titolo, scritto in modi diversi: "Fire Force: Season 3" e "fire force season 3"
fun sameTitle(a: String, b: String): Boolean {
    fun clean(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    return clean(a).isNotEmpty() && clean(a) == clean(b)
}

// La serie giusta tra quelle trovate per titolo, solo se è certa: un solo risultato con lo stesso titolo (e, se si
// sanno, lo stesso numero di episodi). Se no si sceglie a mano dal pannello della serie
fun sureMatch(found: List<AnimeMatch>, title: String, count: Int?): AnimeMatch? {
    val same = found.filter { m ->
        (sameTitle(m.title, title) || m.other?.let { sameTitle(it, title) } == true) &&
            (count == null || m.episodes == null || m.episodes == count)
    }
    return same.singleOrNull()
}

private const val ANILIST_SEARCH = "query(\$s:String){Page(perPage:10){media(search:\$s,type:ANIME){idMal title{romaji english} " +
    "episodes seasonYear coverImage{medium}}}}"
private const val ANILIST_BY_ID = "query(\$id:Int){Media(id:\$id){idMal title{romaji english} episodes seasonYear coverImage{medium}}}"

fun aniListSearchBody(title: String): String =
    JSONObject().put("query", ANILIST_SEARCH).put("variables", JSONObject().put("s", title)).toString()

fun aniListByIdBody(id: Int): String =
    JSONObject().put("query", ANILIST_BY_ID).put("variables", JSONObject().put("id", id)).toString()

// Le serie della risposta di AniList (ricerca o per id), solo quelle che hanno un id MyAnimeList
fun parseAniList(text: String): List<AnimeMatch> = try {
    val data = JSONObject(text).getJSONObject("data")
    val media = data.optJSONObject("Page")?.optJSONArray("media")?.let { a -> List(a.length()) { a.getJSONObject(it) } }
        ?: listOfNotNull(data.optJSONObject("Media"))
    media.mapNotNull { m ->
        val mal = m.optInt("idMal", 0).takeIf { it > 0 } ?: return@mapNotNull null
        val names = m.optJSONObject("title")
        val romaji = names?.optString("romaji").orEmpty().takeIf { it.isNotBlank() && it != "null" }
        val english = names?.optString("english").orEmpty().takeIf { it.isNotBlank() && it != "null" }
        AnimeMatch(
            malId = mal,
            title = english ?: romaji ?: return@mapNotNull null,
            other = romaji?.takeIf { english != null && it != english },
            year = m.optInt("seasonYear", 0).takeIf { it > 0 },
            episodes = m.optInt("episodes", 0).takeIf { it > 0 },
            cover = m.optJSONObject("coverImage")?.optString("medium")?.takeIf { it.startsWith("http") },
        )
    }
} catch (e: Exception) {
    emptyList()
}

// Le chiamate in rete (in un thread). null se il servizio non risponde
object AniSkipClient {
    fun skips(malId: Int, episode: Int, base: String = ANISKIP_API): List<SkipPart>? =
        request(aniSkipUrl(malId, episode, base))?.let { parseAniSkip(it) }

    fun search(title: String, base: String = ANILIST_API): List<AnimeMatch>? =
        request(base, aniListSearchBody(title))?.let { parseAniList(it) }

    fun byAniList(id: Int, base: String = ANILIST_API): AnimeMatch? =
        request(base, aniListByIdBody(id))?.let { parseAniList(it).firstOrNull() }

    private fun request(url: String, body: String? = null): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            // AniSkip risponde 404 quando l'episodio non c'è, con lo stesso JSON ("found": false)
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }?.takeIf { conn.responseCode in 200..299 || conn.responseCode == 404 }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

// --- Le serie salvate (prefs["shows"]) ---

// I tratti dell'episodio che Kodi sta riproducendo (file), se si sanno
fun skipPartsOf(shows: List<Show>, file: String?): List<SkipPart> = episodeOfFile(shows, file)?.skips.orEmpty()

fun episodeOfFile(shows: List<Show>, file: String?): Episode? {
    val key = file?.let { queueKey(it) } ?: return null
    return showOfFile(shows, file)?.episodes?.firstOrNull { it.file != null && queueKey(it.file) == key }
}

// Cosa fa il tasto per il video file a time su total, con la serie del video (o general se il video non è di una serie)
// autoDefault: la modalità di Impostazioni (prefs["aniskip"]), per le serie che non hanno scelto la loro
fun skipPressOf(shows: List<Show>, file: String?, time: Long, total: Long, general: Int?, autoDefault: Boolean = true): SkipPress =
    skipPress(skipPartsOf(shows, file), time, total, introOf(shows, file, general), skipAutoOf(shows, file, autoDefault))

// Automatico (AniSkip) o manuale per il video file: quello della serie, se no quello di Impostazioni
fun skipAutoOf(shows: List<Show>, file: String?, autoDefault: Boolean): Boolean = showOfFile(shows, file)?.skipAuto ?: autoDefault

fun withSkipAuto(shows: List<Show>, key: String, auto: Boolean?): List<Show> = shows.map { if (it.key == key) it.copy(skipAuto = auto) else it }

// Lo spostamento scelto (o confermato) a mano: i tempi già chiesti non valgono più
fun withShift(shows: List<Show>, key: String, shift: Int): List<Show> = shows.map { show ->
    if (show.key != key || show.episodeShift == shift) show
    else show.copy(episodeShift = shift, episodes = show.episodes.map { it.copy(skips = null, skipsAt = 0) })
}

fun withSiteFirst(shows: List<Show>, key: String, first: Int?): List<Show> =
    if (first == null) shows else shows.map { if (it.key == key) it.copy(siteFirst = first) else it }

// Il numero dell'episodio da chiedere ad AniSkip per il video file, o null se non si può chiedere (serie senza id
// MyAnimeList, episodio senza numero) o se i tempi sono già stati chiesti da poco
fun aniSkipRequest(shows: List<Show>, file: String?, now: Long, autoDefault: Boolean = true): Pair<Int, Int>? {
    val show = showOfFile(shows, file) ?: return null
    if (!(show.skipAuto ?: autoDefault)) return null
    val mal = show.malId?.takeIf { it != NO_MAL } ?: return null
    val episode = episodeOfFile(shows, file) ?: return null
    val number = episode.number?.minus(show.episodeShift)?.takeIf { it > 0 } ?: return null
    val wait = when {
        episode.skips == null -> RETRY_FAILED_MS
        episode.skips.isEmpty() -> RETRY_MISSING_MS
        else -> return null
    }
    if (episode.skipsAt > 0 && now - episode.skipsAt < wait) return null
    return mal to number
}

// I tratti chiesti per il video file: parts null = AniSkip non ha risposto (si richiede più tardi)
fun withSkips(shows: List<Show>, file: String, parts: List<SkipPart>?, now: Long): List<Show> {
    val key = queueKey(file)
    return shows.map { show ->
        show.copy(episodes = show.episodes.map {
            if (it.file == null || queueKey(it.file) != key) it else it.copy(skips = parts ?: it.skips, skipsAt = now)
        })
    }
}

// La serie su MyAnimeList (dalla pagina, da AniList o scelta a mano; NO_MAL = nessuna). Cambiando serie, i tempi già
// chiesti non valgono più
fun withMal(shows: List<Show>, key: String, malId: Int, title: String?, shift: Int? = null): List<Show> = shows.map { show ->
    if (show.key != key) show
    else show.copy(
        malId = malId,
        malTitle = title,
        episodeShift = shift ?: show.episodeShift,
        episodes = if (show.malId == malId) show.episodes else show.episodes.map { it.copy(skips = null, skipsAt = 0) },
    )
}

fun skipsToJson(parts: List<SkipPart>): JSONArray = JSONArray(parts.map {
    JSONObject().put("type", it.type.api).put("from", it.from).put("to", it.to).put("length", it.length)
})

fun skipsFromJson(array: JSONArray): List<SkipPart> = List(array.length()) { array.getJSONObject(it) }.mapNotNull { o ->
    val type = PartType.values().firstOrNull { it.api == o.optString("type") } ?: return@mapNotNull null
    SkipPart(type, o.optDouble("from"), o.optDouble("to"), o.optDouble("length", 0.0))
}

// Riga del pannello della serie
fun aniSkipLabel(show: Show, enabled: Boolean): String = when {
    !enabled -> tr("Serve la modalità Automatico", "Needs Automatic mode")
    show.malId == NO_MAL -> tr("Nessuna serie: solo il salto fisso", "No series: fixed skip only")
    show.malId == null -> tr("Serie non trovata: tocca per cercarla", "Series not found: tap to search")
    else -> (show.malTitle ?: "MyAnimeList ${show.malId}") +
        (show.current.skips?.let { parts -> if (parts.isEmpty()) tr(" · episodio senza tempi", " · no times for this episode") else tr(" · tempi trovati", " · times found") } ?: "")
}
