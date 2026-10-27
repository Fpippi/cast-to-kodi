package it.franc.casttokodi

import java.net.URI
import java.util.Locale

// Sottotitoli (2026-10-27, mockup approvato da Pippo). Quando si manda un video passando dal telefono, l'app manda a
// Kodi una sola qualità (bestVariant) e così perdeva i sottotitoli della playlist; quelli che il sito carica a parte
// (.vtt, .srt) Kodi non li vedeva proprio. Ora il telefono li scarica tutti, li unisce in un file per lingua e li passa
// a Kodi con playlist e chiave (Relay), in una master fatta qui con la qualità scelta e le tracce dei sottotitoli.
// Kodi li legge all'avvio, poi il telefono può spegnersi. Niente android.*: lo provano SubtitlesTest e StreamsTest.

// Al massimo tante tracce per video, e tanti pezzi per traccia: un sito con 300 pezzi di sottotitoli farebbe aspettare troppo
const val MAX_SUBTITLE_TRACKS = 6
const val MAX_SUBTITLE_PARTS = 400

// Un file di sottotitoli caricato dalla pagina: headers come per il video (Referer, cookie del sito)
class SiteSubtitle(val url: String, val headers: List<Pair<String, String>>, val language: String? = subtitleLanguage(url))

// Una traccia della playlist principale (#EXT-X-MEDIA:TYPE=SUBTITLES): lingua, nome e playlist dei suoi pezzi
class SubtitleRendition(val language: String?, val name: String, val url: String, val forced: Boolean)

// Una traccia pronta da dare a Kodi: language come nelle playlist HLS ("it"), vtt il file intero
class SubtitleTrack(val language: String?, val name: String, val vtt: String, val fromSite: Boolean)

fun isSubtitlePath(path: String?): Boolean = path?.lowercase()?.let { it.endsWith(".vtt") || it.endsWith(".srt") } == true

// I file di sottotitoli della pagina da passare a Kodi: via quelli nella stessa cartella con troppi fratelli, che sono
// i pezzi di una traccia HLS letti dal player (la traccia intera arriva già dalla master)
fun siteSubtitleFiles(urls: List<String>): List<String> =
    urls.groupBy { it.substringBefore('?').substringBeforeLast('/') }.values.filter { it.size <= MAX_SUBTITLE_TRACKS }.flatten()

// Parole che nei nomi dei file dicono la lingua: "ep5_ita.vtt", "subs/English.srt", "?lang=it"
private val LANGUAGE_WORDS = mapOf(
    "it" to listOf("it", "ita", "italian", "italiano"),
    "en" to listOf("en", "eng", "english", "inglese"),
    "ja" to listOf("ja", "jp", "jpn", "jap", "japanese", "giapponese"),
    "es" to listOf("es", "spa", "esp", "spanish", "spagnolo", "espanol"),
    "fr" to listOf("fr", "fra", "fre", "french", "francese"),
    "de" to listOf("de", "deu", "ger", "german", "tedesco"),
    "pt" to listOf("pt", "por", "portuguese", "portoghese"),
)

private fun languageOfWords(text: String): String? {
    val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    return LANGUAGE_WORDS.entries.firstOrNull { (_, names) -> words.any { it in names } }?.key
}

// La lingua di un file di sottotitoli dal suo indirizzo (nome del file e parametri), null se non lo dice
fun subtitleLanguage(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val file = uri.rawPath.orEmpty().substringAfterLast('/').substringBeforeLast('.')
    return languageOfWords(file) ?: languageOfWords(uri.rawQuery.orEmpty().split('&').joinToString(" ") { it.substringAfter('=') })
}

// Codice ISO a 3 lettere ("ita") da quello che dicono Kodi o la playlist ("it", "ita", "Italiano"), per confrontarli
fun languageKey(language: String?): String? {
    val text = language?.trim()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
    if (text.length in 2..3 && text.all { it.isLetter() }) {
        runCatching { Locale.forLanguageTag(text.lowercase()).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return languageOfWords(text)?.let { Locale.forLanguageTag(it).isO3Language }
}

// "Italiano", "Inglese": il nome della lingua da mostrare, o null
fun languageName(language: String?): String? = languageKey(language)?.let { trackLabel(it, "", 0) }

// Le tracce di sottotitoli di una playlist principale, con indirizzi assoluti
fun masterSubtitles(master: String, masterUrl: String): List<SubtitleRendition> {
    val base = runCatching { URI(masterUrl) }.getOrNull() ?: return emptyList()
    return master.lines().map { it.trim() }.filter { it.startsWith("#EXT-X-MEDIA:") && "TYPE=SUBTITLES" in it }.mapNotNull { line ->
        fun attr(name: String) = Regex("(?<![-A-Z])$name=(\"([^\"]*)\"|[^,]*)").find(line)?.let { it.groupValues[2].ifEmpty { it.groupValues[1] } }
        val uri = attr("URI") ?: return@mapNotNull null
        SubtitleRendition(attr("LANGUAGE"), attr("NAME").orEmpty(), base.resolve(uri).toString(), attr("FORCED") == "YES")
    }
}

// I pezzi di una playlist di sottotitoli, con indirizzi assoluti
fun subtitleParts(media: String, mediaUrl: String): List<String> {
    val base = URI(mediaUrl)
    return media.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { base.resolve(it).toString() }
}

// Un file .srt in WebVTT, l'unico formato che le playlist HLS accettano. Un file già WebVTT resta com'è
fun toVtt(text: String): String {
    val clean = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    if (clean.trimStart().startsWith("WEBVTT")) return clean
    val times = Regex("(\\d{1,2}:\\d{2}:\\d{2}),(\\d{3})")
    return "WEBVTT\n\n" + clean.lines().joinToString("\n") { if ("-->" in it) times.replace(it) { m -> "${m.groupValues[1]}.${m.groupValues[2]}" } else it }
}

// I pezzi WebVTT di una traccia in un file solo: l'intestazione del primo (con X-TIMESTAMP-MAP, che dice come
// allineare i tempi al video) e le battute di tutti
fun mergeVtt(parts: List<String>): String {
    val texts = parts.map { toVtt(it) }
    val first = texts.firstOrNull() ?: return "WEBVTT\n"
    val header = first.substringBefore("\n\n").trimEnd()
    val cues = texts.map { it.substringAfter("\n\n", "").trim() }.filter { it.isNotEmpty() }
    return header + "\n\n" + cues.joinToString("\n\n") + "\n"
}

// Le tracce che vanno insieme al video: prima quelle della playlist, poi quelle del sito, una per lingua e nome
fun pickSubtitles(tracks: List<SubtitleTrack>): List<SubtitleTrack> =
    tracks.filter { "-->" in it.vtt }.distinctBy { (languageKey(it.language) ?: "") + "|" + it.name.lowercase() }.take(MAX_SUBTITLE_TRACKS)

// La playlist di una traccia: un pezzo solo, lungo quanto il video, così Kodi legge il file intero all'avvio
fun subtitlePlaylist(vttUrl: String, seconds: Double): String {
    val length = seconds.coerceAtLeast(1.0)
    return "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:${Math.ceil(length).toLong()}\n#EXT-X-MEDIA-SEQUENCE:0\n" +
        "#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:${"%.3f".format(Locale.ROOT, length)},\n$vttUrl\n#EXT-X-ENDLIST\n"
}

// La master per Kodi: la qualità scelta (videoUrl) e le tracce (lingua, nome, indirizzo della playlist della traccia).
// Nessuna accesa di default: le accende Kodi se lo chiede la sua impostazione, o la scelta della serie
fun subtitleMaster(videoUrl: String, bandwidth: Long, tracks: List<Triple<String?, String, String>>): String {
    fun quoted(s: String) = s.replace("\"", "'")
    val media = tracks.joinToString("") { (language, name, url) ->
        "#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"${quoted(name)}\"," +
            (language?.let { "LANGUAGE=\"${quoted(it)}\"," } ?: "") + "DEFAULT=NO,AUTOSELECT=NO,URI=\"$url\"\n"
    }
    return "#EXTM3U\n$media#EXT-X-STREAM-INF:BANDWIDTH=${bandwidth.coerceAtLeast(1)},SUBTITLES=\"subs\"\n$videoUrl\n"
}

// Il nome di una traccia per Kodi: quello della playlist, se no la lingua; "dal sito" per i file caricati dalla pagina
fun subtitleName(language: String?, name: String, fromSite: Boolean, number: Int): String {
    val base = name.trim().ifEmpty { languageName(language) ?: "Sottotitoli $number" }
    return if (fromSite) "$base · dal sito" else base
}

// "con sottotitoli in italiano e inglese": la fine dell'avviso dopo l'invio
fun subtitlesNote(tracks: List<SubtitleTrack>): String? {
    if (tracks.isEmpty()) return null
    val names = tracks.mapNotNull { languageName(it.language)?.lowercase(Locale.ITALIAN) }.distinct()
    if (names.isEmpty()) return "con sottotitoli"
    val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " e " + names.last()
    return "con sottotitoli in $list"
}

// "CC Italiano, English" sull'immagine del video nella scelta: le lingue di playlist e sito, senza ripetizioni
fun ccLabel(languages: List<String?>): String? {
    if (languages.isEmpty()) return null
    val names = languages.map { languageName(it) }.distinct()
    val known = names.filterNotNull()
    return "CC " + (if (known.isEmpty()) "sottotitoli" else known.joinToString(", "))
}

// La traccia di Kodi della lingua scelta per la serie (Show.subtitles), da accendere quando parte un suo episodio
fun subtitleFor(tracks: List<Track>, language: String?): Track? {
    val wanted = languageKey(language) ?: return null
    val same = tracks.filter { (languageKey(it.language) ?: languageKey(it.name)) == wanted }
    // Le tracce "Forced" traducono solo i cartelli: meglio quella completa, se c'è
    return same.firstOrNull { !it.name.contains("forced", ignoreCase = true) } ?: same.firstOrNull()
}

// La lingua di una traccia di Kodi, da ricordare per la serie
fun trackLanguage(track: Track): String? = languageKey(track.language) ?: languageKey(track.name)

// Riga sotto ogni traccia nel pannello: da dove arriva, quando si sa
fun subtitleSource(track: Track): String? = if (track.name.contains("dal sito", ignoreCase = true)) "dal sito" else null

// Nome di una traccia nel pannello, senza il "dal sito" che va nella riga sotto
fun subtitleTitle(track: Track, number: Int): String =
    trackLabel(track.language, track.name.replace(Regex("\\s*·?\\s*dal sito", RegexOption.IGNORE_CASE), "").trim(), number)
