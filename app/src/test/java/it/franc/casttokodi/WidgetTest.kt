package it.franc.casttokodi

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

// Widget sulla Home: cosa mostra con Kodi fermo (la serie da riprendere) e con un video in corso (il telecomando)
class WidgetTest {
    @After
    fun italiano() {
        Lang.english = false
    }

    private val utc = ZoneId.of("UTC")
    private val hosts = mapOf("animeunity" to "www.animeunity.so")

    private fun playing(file: String, time: Long = 600, total: Long = 1400, paused: Boolean = false, playerId: Int? = 1) =
        NowPlaying(playerId, "", file, time, total, paused, emptyList(), -1, emptyList(), -1, false, 50, false)

    private val fireForce = Show(
        "animeunity:/anime/2-fire-force", "animeunity", "www.animeunity.to", "Fire Force",
        listOf(
            Episode(null, 6, "/anime/2-fire-force/106", "https://cdn.example/6.m3u8", watched = true, seen = 1),
            Episode(null, 7, "/anime/2-fire-force/107", "https://cdn.example/7.m3u8|Referer=x", time = 754, total = 1420, seen = 2, image = "ep7.jpg"),
        ),
        count = 24,
        next = Episode(null, 8, "/anime/2-fire-force/108"),
        intro = 90,
    )

    private val other = Show("serietv:serie", "serietv", "serietv.example", "Una serie", listOf(Episode(null, 3, "/serie/3", seen = 1)))

    private fun content(playing: NowPlaying?, shows: List<Show>, kodi: Boolean = true) =
        widgetContent(kodi, playing, shows, emptyMap(), "", null, hosts, nowSeconds = 21 * 3600L, zone = utc)

    @Test
    fun serieDaRiprendere() {
        val c = content(playing("", playerId = null), listOf(fireForce, other)) as WidgetContent.Resume
        assertEquals("Fire Force", c.title)
        assertEquals("Ep. 7 · fermo a 12:34", c.note)
        assertEquals("Riprendi su Kodi", c.action)
        assertEquals("Ep. 8", c.after)
        assertEquals("ep7.jpg", c.picture)
        assertEquals(fireForce.key, c.key)
    }

    @Test
    fun kodiSpentoMostraLoStesso() {
        assertTrue(content(null, listOf(fireForce)) is WidgetContent.Resume)
    }

    @Test
    fun sugliAltriSitiApreLaPagina() {
        val c = content(null, listOf(other)) as WidgetContent.Resume
        assertEquals("Apri l'episodio", c.action)
        assertNull(c.after)
    }

    @Test
    fun episodioVistoPassaAlProssimo() {
        val watched = fireForce.copy(episodes = fireForce.episodes.map { if (it.number == 7) it.copy(watched = true) else it })
        val c = content(null, listOf(watched)) as WidgetContent.Resume
        assertEquals("Guarda su Kodi", c.action)
        assertEquals("Visto il 7 · tocca all'8", c.note)
        // Dopo l'8 l'app non sa niente
        assertNull(c.after)
    }

    @Test
    fun serieFinitaSiSalta() {
        val finished = fireForce.copy(count = 7, episodes = fireForce.episodes.map { if (it.number == 7) it.copy(watched = true) else it })
        assertTrue(content(null, listOf(finished, other)).let { it is WidgetContent.Resume && it.title == "Una serie" })
        assertSame(WidgetContent.Idle, content(null, listOf(finished)))
        assertNull(widgetAfter(fireForce.copy(count = 7)))
    }

    @Test
    fun senzaKodiOSenzaSerie() {
        assertSame(WidgetContent.NoKodi, content(null, listOf(fireForce), kodi = false))
        assertSame(WidgetContent.Idle, content(null, emptyList()))
    }

    @Test
    fun telecomando() {
        val c = content(playing("https://cdn.example/7.m3u8|Referer=y", time = 754, total = 1420), listOf(fireForce)) as WidgetContent.Playing
        assertEquals("Fire Force · Ep. 7", c.title)
        assertEquals("12:34 / 23:40 · finisce alle 21:11", c.status)
        assertEquals(530, c.progress)
        assertEquals(90, c.intro)
        assertEquals("ep7.jpg", c.picture)
        val paused = content(playing("https://cdn.example/7.m3u8", time = 754, total = 1420, paused = true), listOf(fireForce)) as WidgetContent.Playing
        assertEquals("In pausa · 12:34 / 23:40", paused.status)
        Lang.english = true
        val english = content(playing("https://cdn.example/7.m3u8", time = 754, total = 1420), listOf(fireForce)) as WidgetContent.Playing
        assertEquals("12:34 / 23:40 · ends at 21:11", english.status)
    }

    @Test
    fun videoQualsiasiSenzaSigla() {
        val c = content(playing("https://cdn.example/film.mp4"), listOf(fireForce)) as WidgetContent.Playing
        assertEquals("Video da cdn.example", c.title)
        assertNull(c.intro)
        assertNull(c.picture)
    }

    @Test
    fun siRidisegnaSoloSeCambiaQualcosa() {
        val a = content(playing("https://cdn.example/7.m3u8", time = 754), listOf(fireForce))
        val b = content(playing("https://cdn.example/7.m3u8", time = 760), listOf(fireForce))
        val paused = content(playing("https://cdn.example/7.m3u8", time = 760, paused = true), listOf(fireForce))
        assertEquals(widgetKey(a), widgetKey(b))
        assertNotEquals(widgetKey(a), widgetKey(paused))
    }
}
