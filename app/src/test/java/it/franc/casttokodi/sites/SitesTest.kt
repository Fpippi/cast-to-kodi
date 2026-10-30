package it.franc.casttokodi.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitesTest {

    @Test
    fun animeUnity() {
        assertTrue(isAnimeUnityEpisode("www.animeunity.so", "/anime/1234-jojo/56789"))
        assertTrue(isAnimeUnityEpisode("animeunity.to", "/anime/1234-jojo/56789/"))
        // la scheda dell'anime e gli altri siti no
        assertFalse(isAnimeUnityEpisode("www.animeunity.so", "/anime/1234-jojo"))
        assertFalse(isAnimeUnityEpisode("streamingcommunity.so", "/anime/1234-jojo/56789"))
        assertFalse(isAnimeUnityEpisode(null, null))
    }

    @Test
    fun animeUnitySerie() {
        assertEquals("/anime/1234-jojo", animeUnitySeries("www.animeunity.so", "/anime/1234-jojo/56789"))
        assertEquals("/anime/1234-jojo", animeUnitySeries("animeunity.to", "/anime/1234-jojo"))
        assertNull(animeUnitySeries("streamingcommunity.so", "/anime/1234-jojo/56789"))
        assertNull(animeUnitySeries("animeunity.so", "/top-anime"))
        assertEquals("Jojo No Kimyou", animeUnitySlugTitle("/anime/1234-jojo-no-kimyou"))
    }

    @Test
    fun autoNextLabels() {
        assertEquals("Spento", autoNextLabel(0))
        assertEquals("Accoda da solo l'episodio dopo", autoNextLabel(1))
        assertEquals("Accoda da solo i prossimi 3 episodi", autoNextLabel(3))
    }

    @Test
    fun vixCloud() {
        assertTrue(isVixCloudPlaylist("/playlist/12345"))
        assertFalse(isVixCloudPlaylist("/hls/master.m3u8"))
        assertTrue(isVixCloudPart(listOf("type", "rendition")))
        assertFalse(isVixCloudPart(listOf("token", "expires")))
        assertEquals(1761040000L, linkExpiry("token=a&expires=1761040000&h=1"))
        assertNull(linkExpiry("token=a"))
        assertNull(linkExpiry(null))
    }

    @Test
    fun google() {
        assertEquals("$GOOGLE/search?q=jojo%20animeunity", googleSearch("jojo animeunity"))
        assertTrue(isGoogle("google.com"))
        assertTrue(isGoogle("google.it"))
        assertFalse(isGoogle("animeunity.so"))
        assertTrue(isGoogleConsent("consent.google.com"))
        assertTrue(isGoogleConsent("consent.google.it"))
        assertFalse(isGoogleConsent("www.google.com"))
        assertFalse(isGoogleConsent(null))
    }

    @Test
    fun nomeEpisodioSuccessivo() {
        assertEquals("JoJo Episodio 4", nextEpisodeTitle("JoJo Episodio 3", "4"))
        assertEquals("JoJo Ep. 2 Episodio 12", nextEpisodeTitle("JoJo Ep. 2 episodio 11", "12"))
        assertEquals("JoJo · Episodio 4", nextEpisodeTitle("JoJo", "4"))
        assertEquals("Episodio 4", nextEpisodeTitle("", "4"))
    }

    @Test
    fun animeSaturn() {
        assertTrue(isAnimeSaturnEpisode("www.animesaturn.cx", "/ep/One-Piece-ep-1000"))
        assertTrue(isAnimeSaturnEpisode("anisaturn.com", "/ep/Shingeki-no-Kyojin-ITA-ep-3/"))
        assertFalse(isAnimeSaturnEpisode("www.animesaturn.cx", "/anime/One-Piece"))
        assertFalse(isAnimeSaturnEpisode("www.animeunity.so", "/ep/One-Piece-ep-1000"))
        assertTrue(isAnimeSaturnWatch("www.animesaturn.cx", "/watch"))
        assertEquals("/anime/One-Piece", animeSaturnSeries("www.animesaturn.cx", "/ep/One-Piece-ep-1000"))
        assertEquals("/anime/One-Piece", animeSaturnSeries("www.animesaturn.tv", "/anime/One-Piece"))
        assertNull(animeSaturnSeries("www.animesaturn.cx", "/watch"))
        assertEquals(1000, animeSaturnNumber("/ep/One-Piece-ep-1000"))
        // il nome della serie può contenere "ep": conta l'ultimo "-ep-"
        assertEquals(13 to "/ep/Step-by-Step-ep-13", animeSaturnNext("/ep/Step-by-Step-ep-12"))
        assertNull(animeSaturnNext("/anime/One-Piece"))
        assertEquals("Shingeki no Kyojin ITA", animeSaturnSlugTitle("/anime/Shingeki-no-Kyojin-ITA"))
    }

    @Test
    fun animeSaturnEpisodioDallaPaginaDelPlayer() {
        val history = listOf("/ep/One-Piece-ep-1000", "/anime/One-Piece", "/ep/One-Piece-ep-999")
        assertEquals("/ep/One-Piece-ep-1000", animeSaturnEpisodePath("www.animesaturn.cx", "/watch", history))
        assertEquals("/ep/One-Piece-ep-7", animeSaturnEpisodePath("www.animesaturn.cx", "/ep/One-Piece-ep-7", history))
        assertNull(animeSaturnEpisodePath("www.animesaturn.cx", "/watch", listOf("/anime/One-Piece")))
        assertNull(animeSaturnEpisodePath("www.animesaturn.cx", "/anime/One-Piece", history))
        assertNull(animeSaturnEpisodePath("www.animeunity.so", "/watch", history))
    }
}
