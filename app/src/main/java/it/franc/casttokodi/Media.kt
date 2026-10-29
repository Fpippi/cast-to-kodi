package it.franc.casttokodi

// Il player di Android (tendina e schermata di blocco) e il riquadro "Couch Cast" delle impostazioni rapide
// (2026-10-27, mockup approvato da Pippo): cosa mostrano del video che Kodi riproduce. Niente android.*: lo prova MediaTest.

// Il player rilegge Kodi ogni 3 secondi solo con lo schermo acceso; si chiude se Kodi non risponde per un minuto
const val MEDIA_EVERY = 3000L
const val MEDIA_GIVE_UP = 60_000L

// Salti dei tasti −10 s / +10 s, come nel telecomando
const val MEDIA_SKIP = 10

// title: la serie o il nome del video; subtitle: episodio e Kodi
class MediaText(val title: String, val subtitle: String)

fun mediaText(playing: NowPlaying, shows: List<Show>, titles: Map<String, String>, kodiName: String): MediaText {
    val show = showOfFile(shows, playing.file)
    val key = queueKey(playing.file)
    val episode = show?.episodes?.firstOrNull { it.file != null && queueKey(it.file) == key }
    val number = episode?.number?.let { n -> episode.season?.let { tr("Stagione $it · Episodio $n", "Season $it · Episode $n") } ?: tr("Episodio $n", "Episode $n") }
    return MediaText(
        title = show?.title ?: queueLabel(playing.file, playing.title, titles),
        subtitle = listOfNotNull(number, kodiName.ifBlank { "Kodi" }).joinToString(" · "),
    )
}

// I tasti del player oltre a play/pausa. Salta sigla solo se per quel video sa dove andare: i tempi di AniSkip o il salto
// già scelto (la prima scelta si fa dal telecomando); Prossimo solo se nella coda di Kodi c'è qualcosa dopo
data class MediaButtons(val intro: SkipPress?, val next: Boolean)

// In automatico il tasto c'è solo dentro un tratto (la tendina non ha tasti spenti)
fun mediaButtons(playing: NowPlaying, state: KodiState?, shows: List<Show>, generalIntro: Int?, autoDefault: Boolean = true) = MediaButtons(
    intro = skipPressOf(shows, playing.file, playing.time, playing.total, generalIntro, autoDefault).takeIf { it is SkipPress.To || it is SkipPress.Forward },
    next = state?.hasNext == true,
)

// Seconda riga del riquadro: cosa fa Kodi in breve
fun tileSubtitle(playing: NowPlaying?, text: MediaText?): String = when {
    playing == null -> tr("Kodi spento", "Kodi off")
    playing.playerId == null -> tr("Niente in riproduzione", "Nothing playing")
    text != null -> text.title
    else -> tr("In riproduzione", "Playing")
}
