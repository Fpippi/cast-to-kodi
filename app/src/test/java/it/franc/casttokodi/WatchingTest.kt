package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// "Continua a guardare": come si riconosce una serie, quando un episodio è visto, il punto da riprendere e il cambio
// di dominio dei siti. Più la ripresa dal minuto e il punto salvato contro FakeKodi.
class WatchingTest {

    private fun jojo(number: Int, id: Int = 56780 + number) =
        episodeFromPage("www.animeunity.so", "/anime/1234-jojo/$id", null, "JoJo's Bizarre Adventure Episodio $number - AnimeUnity")!!

    @Test
    fun animeUnityDallaPaginaDellaSerie() {
        // Mandato dalla pagina della serie: il player dice poi numero e indirizzo vero dell'episodio
        val serie = episodeFromPage("www.animeunity.so", "/anime/1234-jojo", null, "JoJo - AnimeUnity")!!
        var shows = withSent(emptyList(), serie, "www.animeunity.so", "f1", 1000, playing = true)
        assertEquals(1, shows.size)
        shows = withDetails(shows, serie.key, path = serie.path, number = 1, at = "/anime/1234-jojo/56781")
        assertEquals(1, shows[0].current.number)
        assertEquals("/anime/1234-jojo/56781", shows[0].current.path)
        // Poi l'episodio 1 dalla sua pagina è lo stesso
        shows = withSent(shows, jojo(1), "www.animeunity.so", "f1b", 2000, playing = true)
        assertEquals(1, shows[0].episodes.size)
    }

    @Test
    fun animeUnityDallIndirizzo() {
        val page = jojo(12)
        assertEquals("animeunity:/anime/1234-jojo", page.key)
        assertEquals("JoJo's Bizarre Adventure", page.series)
        assertEquals(12, page.number)
        assertEquals("/anime/1234-jojo/56792", page.path)
        // Stessa serie con un altro dominio
        assertEquals(page.key, episodeFromPage("animeunity.to", "/anime/1234-jojo/56793", null, "JoJo Episodio 13")!!.key)
        // Senza numero nel titolo: il nome dall'indirizzo
        val bare = episodeFromPage("animeunity.so", "/anime/77-frieren-beyond/9", null, null)!!
        assertEquals("Frieren Beyond", bare.series)
        assertNull(bare.number)
        // La pagina della serie ha già il player: stessa serie, numero ancora da sapere
        val serie = episodeFromPage("animeunity.so", "/anime/1234-jojo", null, "JoJo - AnimeUnity")!!
        assertEquals(page.key, serie.key)
        assertEquals("/anime/1234-jojo", serie.path)
        assertNull(serie.number)
        // Il titolo vero di AnimeUnity ha il nome del sito davanti
        val vero = episodeFromPage("www.animeunity.co", "/anime/627-le-bizzarre-avventure-di-jojo-diamond-is-unbreakable/14883",
            null, "AnimeUnity ~ Le Bizzarre Avventure di JoJo: Diamond is Unbreakable")!!
        assertEquals("Le Bizzarre Avventure di JoJo: Diamond is Unbreakable", vero.series)
    }

    @Test
    fun altriSitiDalTitolo() {
        val bb = episodeFromPage("streamingcommunity.computer", "/watch/12", "e=34", "Breaking Bad - S2E5 - StreamingCommunity")!!
        assertEquals("streamingcommunity:breaking bad", bb.key)
        assertEquals("Breaking Bad", bb.series)
        assertEquals(2, bb.season)
        assertEquals(5, bb.number)
        assertEquals("/watch/12?e=34", bb.path)
        val word = episodeFromPage("www.sito.tv", "/x", null, "Dark Stagione 3 Episodio 2")!!
        assertEquals(3, word.season)
        assertEquals(2, word.number)
        assertEquals("Dark", word.series)
        // Il titolo cambia dominio ma non nome: stessa serie
        assertEquals(bb.key, episodeFromPage("streamingcommunity.so", "/watch/12", "e=35", "Breaking Bad S2 E6")!!.key)
        // Una pagina che non è un episodio
        assertNull(episodeFromPage("www.google.com", "/search", "q=jojo", "jojo - Cerca con Google"))
        assertNull(episodeFromPage("sito.tv", "/x", null, "Episodio 3"))
    }

    @Test
    fun visto() {
        val anime = 24 * 60L
        assertFalse(isWatched(20 * 60, anime))
        // La sigla finale: mancano meno di 2 minuti
        assertTrue(isWatched(anime - 110, anime))
        assertTrue(isWatched(anime, anime))
        // Un film di 2 ore: vale il 95%
        assertFalse(isWatched(7200 - 500, 7200))
        assertTrue(isWatched(7200 * 95 / 100, 7200))
        // Una clip di 3 minuti non è vista appena partita
        assertFalse(isWatched(70, 180))
        assertFalse(isWatched(0, 0))
    }

    @Test
    fun saltaSigla() {
        var shows = withSent(emptyList(), jojo(12), "www.animeunity.so", "http://cdn/12.m3u8|Referer=x", 1000, playing = true)
        // Finché la serie non ha il suo, vale quello per tutti
        assertEquals(DEFAULT_INTRO, introOf(shows, "http://cdn/12.m3u8|Referer=y", DEFAULT_INTRO))
        assertEquals(60, introOf(shows, "http://altro/video.mp4", 60))
        shows = withIntro(shows, shows[0].key, 55)
        assertEquals(55, introOf(shows, "http://cdn/12.m3u8", DEFAULT_INTRO))
        // Resta dopo il salvataggio e con l'episodio dopo
        shows = showsFromJson(showsToJson(shows))
        shows = withSent(shows, jojo(13), "www.animeunity.so", "http://cdn/13.m3u8", 2000, playing = true)
        assertEquals(55, introOf(shows, "http://cdn/13.m3u8", DEFAULT_INTRO))
        assertEquals("Salta sigla (+1:25)", introLabel(85))
        // Tempo scritto a mano: un numero solo sono secondi
        assertEquals(85, parseIntro("85"))
        assertEquals(72, parseIntro(" 1:12 "))
        assertEquals(72, parseIntro("1.12"))
        assertNull(parseIntro("0"))
        assertNull(parseIntro("11:00"))
        assertNull(parseIntro("sigla"))
        assertEquals("45 secondi", introTime(45))
    }

    @Test
    fun episodioDopo() {
        val next = EpisodePage("x", "animeunity", "JoJo", null, 13, "/anime/1234-jojo/56793")
        var shows = withSent(emptyList(), jojo(12), "animeunity.so", "f12", 1000, playing = true, count = 39, next = next)
        assertEquals("/anime/1234-jojo/56793", episodeAfter(shows[0], "f12")?.path)
        // Un video che non è della serie, o l'ultimo episodio: niente
        assertNull(episodeAfter(shows[0], "altro"))
        shows = withSent(shows, jojo(39), "animeunity.so", "f39", 2000, playing = true)
        assertNull(episodeAfter(shows[0], "f39"))
        // Senza il prossimo dal sito ma con l'episodio dopo già mandato una volta
        shows = withSent(emptyList(), jojo(4), "animeunity.so", "f4", 1000, playing = true)
        shows = withSent(shows, jojo(5), "animeunity.so", "f5", 1100, playing = false)
        assertEquals(5, episodeAfter(shows[0], "f4")?.number)
    }

    @Test
    fun invioERiproduzione() {
        var shows = withSent(emptyList(), jojo(12), "www.animeunity.so", "http://cdn/12.m3u8|Referer=x", 1000, playing = true, count = 39)
        assertEquals(1, shows.size)
        assertEquals(12, shows[0].current.number)
        assertEquals(ShowCard("Ep. 12", null, 0f).note, showCard(shows[0]).note)

        // Il mini-player legge Kodi a 12:40 di 23:50
        shows = withProgress(shows, "http://cdn/12.m3u8|Referer=y", 760, 1430, 2000)!!
        assertEquals(760, shows[0].current.time)
        val card = showCard(shows[0])
        assertEquals("Ep. 12 · fermo a 12:40", card.note)
        assertEquals("12:40 / 23:50", card.badge)
        // Pochi secondi dopo: niente da salvare
        assertNull(withProgress(shows, "http://cdn/12.m3u8", 765, 1430, 2100))
        // Un video che non è di una serie
        assertNull(withProgress(shows, "http://altro/video.mp4", 10, 100, 2100))

        // Rimandato a metà: tiene il punto, per riprenderlo
        val again = withSent(shows, jojo(12), "www.animeunity.so", "http://cdn/12b.m3u8", 3000, playing = true)
        assertEquals(760L, resumePoint(again, "animeunity", "/anime/1234-jojo/56792"))
        assertEquals(760, again[0].current.time)

        // Finito: la scheda passa al prossimo
        shows = withProgress(again, "http://cdn/12b.m3u8", 1400, 1430, 4000)!!
        assertTrue(shows[0].current.watched)
        assertEquals("Visto il 12 · tocca al 13", showCard(shows[0]).note)
        assertEquals("Prossimo", showCard(shows[0]).badge)
        assertNull(resumePoint(shows, "animeunity", "/anime/1234-jojo/56792"))
        // Rimandato dopo averlo visto: ricomincia
        val rewatch = withSent(shows, jojo(12), "www.animeunity.so", "http://cdn/12c.m3u8", 5000, playing = true)
        assertFalse(rewatch[0].current.watched)
        assertEquals(0, rewatch[0].current.time)
    }

    @Test
    fun codaEPassaggioAlSuccessivo() {
        var shows = withSent(emptyList(), jojo(7), "animeunity.so", "f7", 1000, playing = true)
        // In coda: non diventa quello in corso
        shows = withSent(shows, jojo(8), "animeunity.so", "f8", 1100, playing = false)
        assertEquals(7, shows[0].current.number)
        assertEquals(listOf(7, 8), shows[0].nearby.map { it.number })
        // Kodi passa da solo all'8 (l'app era chiusa sul finale del 7): il 7 è visto
        shows = withProgress(shows, "f8", 20, 1430, 3000)!!
        assertEquals(8, shows[0].current.number)
        assertTrue(shows[0].episodes.first { it.number == 7 }.watched)
        assertEquals(listOf(7, 8), shows[0].nearby.map { it.number })
    }

    @Test
    fun prossimoDalSito() {
        val next = jojo(13)
        var shows = withSent(emptyList(), jojo(12), "animeunity.so", "f12", 1000, playing = true, next = next)
        assertEquals(12, shows[0].upNext!!.number)
        shows = withCurrentWatched(shows, shows[0].key)
        assertEquals(13, shows[0].upNext!!.number)
        assertEquals("/anime/1234-jojo/56793", shows[0].upNext!!.path)
        assertEquals(listOf(12, 13), shows[0].nearby.map { it.number })
    }

    @Test
    fun serieFinita() {
        var shows = withSent(emptyList(), jojo(37), "animeunity.so", "f37", 1000, playing = true, count = 37)
        assertFalse(shows[0].finished)
        shows = withProgress(shows, "f37", 1420, 1430, 2000)!!
        assertTrue(shows[0].finished)
        assertNull(shows[0].upNext)
        assertEquals("37 di 37", showCard(shows[0]).note)
        // Resta finché non la si toglie
        assertEquals(1, shows.size)
        assertTrue(withoutShow(shows, shows[0].key).isEmpty())
    }

    @Test
    fun cambioDiDominio() {
        val shows = withSent(emptyList(), jojo(12), "www.animeunity.so", "f12", 1000, playing = true)
        val show = shows[0]
        assertEquals("https://www.animeunity.so/anime/1234-jojo/56792", episodeUrl(show, show.current, emptyMap()))
        // Il sito si è visto su un dominio nuovo: si riapre lì
        assertEquals("https://animeunity.to/anime/1234-jojo/56792", episodeUrl(show, show.current, mapOf("animeunity" to "animeunity.to")))
        // Sul dominio nuovo la serie si riconosce, anche dalla sua scheda
        assertEquals(show.key, showOfPage(shows, "animeunity.to", "/anime/1234-jojo", null, "JoJo")?.key)
        assertEquals(760L, resumePoint(withProgress(shows, "f12", 760, 1430, 2000)!!, "animeunity", "/anime/1234-jojo/56792"))
    }

    @Test
    fun avvisoSulSito() {
        var shows = withSent(emptyList(), jojo(12), "animeunity.so", "f12", 1000, playing = true, next = jojo(13))
        shows = withProgress(shows, "f12", 760, 1430, 2000)!!
        val show = shows[0]
        assertEquals("Eri all'episodio 12" to "fermo a 12:40", resumeBanner(show, "/anime/1234-jojo"))
        // Già sulla pagina giusta: niente avviso
        assertNull(resumeBanner(show, "/anime/1234-jojo/56792"))
        val watched = withCurrentWatched(shows, show.key)[0]
        assertEquals("Tocca all'episodio 13" to "hai visto il 12", resumeBanner(watched, "/anime/1234-jojo/56781"))
    }

    @Test
    fun ultimaPagina() {
        val shows = withSent(emptyList(), jojo(12), "animeunity.so", "f12", 1000, playing = true)
        // Già nella fila delle serie: non si ripete
        assertFalse(lastPageShown("https://animeunity.to/anime/1234-jojo/56792", shows))
        assertTrue(lastPageShown("https://myanimelist.net/topanime.php", shows))
        assertFalse(lastPageShown(null, shows))
    }

    @Test
    fun parole() {
        assertEquals("all'8", toEpisode(8))
        assertEquals("all'11", toEpisode(11))
        assertEquals("all'1", toEpisode(1))
        assertEquals("al 13", toEpisode(13))
        assertEquals("all'80", toEpisode(80))
        assertEquals("S2 · Ep. 5", episodeLabel(Episode(2, 5, "/")))
        assertEquals("Episodio", episodeLabel(Episode(null, null, "/")))
    }

    @Test
    fun massimoDodiciSerie() {
        var shows = emptyList<Show>()
        for (i in 1..15) {
            val page = episodeFromPage("sito.tv", "/s$i/1", null, "Serie $i Episodio 1")!!
            shows = withSent(shows, page, "sito.tv", "f$i", i.toLong(), playing = true)
        }
        assertEquals(MAX_SHOWS, shows.size)
        assertEquals("Serie 15", shows[0].title)
    }

    @Test
    fun salvataggio() {
        var shows = withSent(emptyList(), jojo(12), "animeunity.so", "f12", 1000, playing = true, count = 39, next = jojo(13), image = "abc")
        shows = withProgress(shows, "f12", 760, 1430, 2000)!!
        shows = withSent(shows, episodeFromPage("sc.so", "/w/1", null, "Dark S1E1")!!, "sc.so", "d1", 3000, playing = true)
        assertEquals(shows, showsFromJson(showsToJson(shows)))
        assertTrue(showsFromJson("non è json").isEmpty())
        assertTrue(showsFromJson(null).isEmpty())
    }

    @Test
    fun riprendeDalMinuto() = FakeKodi().use { fake ->
        val kodi = Kodi(fake.host, "")
        kodi.playNow(org.json.JSONObject().put("file", "http://cdn/12.m3u8|Referer=x"))
        fake.totaltime = 1430
        // Finché il tempo è fermo a 0 il video si sta ancora aprendo: non si salta
        assertEquals("il video su Kodi non è partito in tempo", kodi.resumeAt("http://cdn/12.m3u8|Referer=x", 760, tries = 3, wait = 10, seekPause = 1))
        assertEquals(0, fake.time)
        fake.time = 2
        assertNull(kodi.resumeAt("http://cdn/12.m3u8|Referer=x", 760, tries = 3, wait = 10, seekPause = 1))
        assertEquals(760, fake.time)
        // Kodi che non risponde bene mentre apre il video: si riprova, non si rinuncia
        fake.time = 2
        fake.failing = 2
        assertNull(kodi.resumeAt("http://cdn/12.m3u8|Referer=x", 700, tries = 6, wait = 10, seekPause = 1))
        assertEquals(700, fake.time)
        // Kodi che accetta il salto ma resta dov'era: si risalta finché non arriva
        fake.time = 2
        fake.ignoredSeeks = 2
        assertNull(kodi.resumeAt("http://cdn/12.m3u8|Referer=x", 700, tries = 10, wait = 10, seekPause = 1))
        assertEquals(700, fake.time)
        // ...ma non all'infinito
        fake.time = 2
        fake.ignoredSeeks = 10
        assertNotNull(kodi.resumeAt("http://cdn/12.m3u8|Referer=x", 700, tries = 20, wait = 10, seekPause = 1, seeks = 3))
        assertEquals(2, fake.time)
        fake.ignoredSeeks = 0
        fake.time = 760
        // Se parte un altro video non salta
        assertEquals("Kodi non ha mostrato il video in riproduzione", kodi.resumeAt("http://cdn/13.m3u8", 500, tries = 2, wait = 10))
        assertEquals(760, fake.time)
    }

    @Test
    fun puntoSalvatoDaKodi() = FakeKodi().use { fake ->
        val kodi = Kodi(fake.host, "")
        fake.saved["f12"] = 760L to 1430L
        fake.played += "f7"
        assertEquals(Triple(760L, 1430L, false), kodi.savedPoint("f12"))
        assertEquals(true, kodi.savedPoint("f7")?.third)
        var shows = withSent(emptyList(), jojo(12), "animeunity.so", "f12", 1000, playing = true)
        shows = withSaved(shows, "f12", 760, 1430, false)!!
        assertEquals(760, shows[0].current.time)
        assertNull(withSaved(shows, "f12", 760, 1430, false))
        assertTrue(withSaved(shows, "f12", 0, 0, true)!![0].current.watched)
        assertNotNull(runCatching { kodi.savedPoint("mai visto") }.exceptionOrNull())
    }
}
