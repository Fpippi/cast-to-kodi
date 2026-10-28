package it.franc.casttokodi

import it.franc.casttokodi.sites.isAnimeUnityEpisode
import java.time.ZoneId

// Widget sulla schermata Home (2026-10-28, mockup approvato da Pippo): con Kodi fermo la serie da riprendere, con un
// video in corso un piccolo telecomando. Cosa mostra lo decide widgetContent, senza android.*: lo prova WidgetTest.
// Il widget lo disegna KodiWidget.

// Con lo stesso video e lo stesso stato, il widget si ridisegna al massimo ogni 15 secondi (solo il tempo cambia)
const val WIDGET_EVERY = 15_000L

sealed class WidgetContent {
    // Kodi non ancora scelto: il tocco apre l'app
    object NoKodi : WidgetContent()

    // Kodi fermo o spento e nessuna serie da riprendere
    object Idle : WidgetContent()

    // La serie da riprendere. action: il testo del pulsante (su Kodi solo dove l'app sa leggere il player senza aprire
    // la pagina, se no apre l'episodio nell'app); after: l'episodio dopo ("Ep. 8"), null se non si sa o non si manda
    class Resume(val key: String, val title: String, val note: String, val picture: String?, val action: String, val after: String?) : WidgetContent()

    // Kodi riproduce: progress da 0 a 1000; intro = secondi di Salta sigla, null se per quel video non è ancora scelto
    class Playing(val title: String, val status: String, val progress: Int, val paused: Boolean, val intro: String?, val picture: String?) : WidgetContent()
}

// playing: l'ultima lettura di Kodi, null se non risponde. hosts: sito -> ultimo dominio visto (prefs["siteHosts"])
fun widgetContent(
    kodiChosen: Boolean, playing: NowPlaying?, shows: List<Show>, titles: Map<String, String>, kodiName: String,
    generalIntro: Int?, hosts: Map<String, String>, nowSeconds: Long, zone: ZoneId = ZoneId.systemDefault(),
): WidgetContent {
    if (!kodiChosen) return WidgetContent.NoKodi
    if (playing?.playerId != null) {
        val text = mediaText(playing, shows, titles, kodiName)
        val key = queueKey(playing.file)
        val episode = showOfFile(shows, playing.file)?.episodes?.firstOrNull { it.file != null && queueKey(it.file) == key }
        val time = "${clockLabel(playing.time)} / ${clockLabel(playing.total)}"
        val status = when {
            playing.total <= 0 -> miniPlayerStatus(playing.paused, playing.time, playing.total)
            playing.paused -> tr("In pausa · $time", "Paused · $time")
            else -> tr("$time · finisce alle ${clockAt(nowSeconds + playing.total - playing.time, zone)}", "$time · ends at ${clockAt(nowSeconds + playing.total - playing.time, zone)}")
        }
        return WidgetContent.Playing(
            title = listOfNotNull(text.title, episode?.takeIf { it.number != null }?.let(::episodeLabel)).joinToString(" · "),
            status = status,
            progress = if (playing.total > 0) (playing.time * 1000 / playing.total).toInt().coerceIn(0, 1000) else 0,
            paused = playing.paused,
            // Il nome del tasto Salta sigla (sigla, riassunto o titoli di coda), null se non sa dove andare
            intro = skipPressOf(shows, playing.file, playing.time, playing.total, generalIntro).takeIf { it != SkipPress.Choose }?.let(::skipShortLabel),
            picture = pictureOfFile(shows, playing.file),
        )
    }
    // La più recente con un episodio da guardare (le serie finite restano nella pagina iniziale, qui no)
    val show = shows.firstOrNull { it.upNext != null } ?: return WidgetContent.Idle
    val target = show.upNext!!
    val toKodi = widgetSendsToKodi(show, target, hosts)
    return WidgetContent.Resume(
        key = show.key,
        title = show.title,
        note = showCard(show).note,
        picture = show.picture,
        action = when {
            !toKodi -> tr("Apri l'episodio", "Open the episode")
            !target.watched && target.time >= RESUME_MIN -> tr("Riprendi su Kodi", "Resume on Kodi")
            else -> tr("Guarda su Kodi", "Watch on Kodi")
        },
        after = widgetAfter(show)?.takeIf { toKodi && widgetSendsToKodi(show, it, hosts) }?.let(::episodeLabel),
    )
}

// L'episodio dopo quello da guardare, se l'app lo conosce
fun widgetAfter(show: Show): Episode? {
    val target = show.upNext ?: return null
    val number = target.number ?: return null
    if (show.count != null && target.season == null && number >= show.count) return null
    return show.episodes.firstOrNull { it.season == target.season && it.number == number + 1 }
        ?: show.next?.takeIf { it.season == target.season && it.number == number + 1 }
}

// Come "Riprendi su Kodi" del pannello della serie: solo dove l'app legge il player senza aprire la pagina
fun widgetSendsToKodi(show: Show, episode: Episode, hosts: Map<String, String>): Boolean =
    isAnimeUnityEpisode(hosts[show.site] ?: show.host, episode.path)

// Cosa conta per ridisegnare subito: tutto tranne il tempo che passa
fun widgetKey(content: WidgetContent): String = when (content) {
    WidgetContent.NoKodi -> "nokodi"
    WidgetContent.Idle -> "idle"
    is WidgetContent.Resume -> listOf("resume", content.key, content.title, content.note, content.picture, content.action, content.after).joinToString("|")
    is WidgetContent.Playing -> listOf("playing", content.title, content.paused, content.intro, content.picture).joinToString("|")
}
