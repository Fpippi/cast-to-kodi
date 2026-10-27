package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Player di Android e riquadro della tendina: testi e tasti dal video che Kodi riproduce
class MediaTest {

    private fun playing(file: String, title: String = "", playerId: Int? = 1) =
        NowPlaying(playerId, title, file, 300, 1400, false, emptyList(), -1, emptyList(), -1, false, 50, false)

    private val jojo = Show(
        "animeunity:/anime/1-jojo", "animeunity", "www.animeunity.so", "JoJo's Bizarre Adventure",
        listOf(Episode(null, 12, "/anime/1-jojo/12", "https://cdn.example/12.m3u8|Referer=x", seen = 1), Episode(null, 13, "/anime/1-jojo/13", "https://cdn.example/13.m3u8")),
        intro = 85,
    )

    @Test
    fun episodioDiUnaSerie() {
        val text = mediaText(playing("https://cdn.example/12.m3u8|Referer=y"), listOf(jojo), emptyMap(), "Kodi in salotto")
        assertEquals("JoJo's Bizarre Adventure", text.title)
        assertEquals("Episodio 12 · Kodi in salotto", text.subtitle)
        assertEquals("JoJo's Bizarre Adventure", tileSubtitle(playing("x"), text))
    }

    @Test
    fun videoQualsiasi() {
        val text = mediaText(playing("https://cdn.example/film.mp4", title = "film.mp4"), listOf(jojo),
            mapOf("https://cdn.example/film.mp4" to "Un film"), "")
        assertEquals("Un film", text.title)
        assertEquals("Kodi", text.subtitle)
    }

    @Test
    fun tasti() {
        val state = KodiState(playerId = 1, fromQueue = true, position = 0, remaining = 600, episode = 1400, queueLength = 2)
        val buttons = mediaButtons(playing("https://cdn.example/12.m3u8"), state, listOf(jojo), generalIntro = null)
        assertEquals(85, buttons.intro)
        assertTrue(buttons.next)
        // Coda finita, video fuori dalle serie e senza tempo generale: niente Salta sigla né Prossimo
        val last = KodiState(playerId = 1, fromQueue = true, position = 1, remaining = 600, episode = 1400, queueLength = 2)
        val other = mediaButtons(playing("https://cdn.example/film.mp4"), last, listOf(jojo), generalIntro = null)
        assertNull(other.intro)
        assertFalse(other.next)
        assertEquals(90, mediaButtons(playing("https://cdn.example/film.mp4"), null, listOf(jojo), generalIntro = 90).intro)
    }

    @Test
    fun riquadroConKodiFermoOSpento() {
        assertEquals("Kodi spento", tileSubtitle(null, null))
        assertEquals("Niente in riproduzione", tileSubtitle(playing("", playerId = null), null))
    }
}
