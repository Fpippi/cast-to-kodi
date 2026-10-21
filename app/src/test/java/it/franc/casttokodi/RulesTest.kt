package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun barraInAlto() {
        assertEquals(GOOGLE, addressFor("   "))
        assertEquals("https://animeunity.so/anime/1", addressFor(" https://animeunity.so/anime/1 "))
        assertEquals("http://192.168.1.50:8080", addressFor("http://192.168.1.50:8080"))
        assertEquals("https://animeunity.so", addressFor("animeunity.so"))
        assertEquals("$GOOGLE/search?q=jojo%20animeunity", addressFor("jojo animeunity"))
        assertEquals("$GOOGLE/search?q=jojo", addressFor("jojo"))
    }

    @Test
    fun dominio() {
        assertEquals("animeunity.so", domainOf("www.animeunity.so"))
        assertEquals("vixcloud.co", domainOf("vixcloud.co"))
        assertEquals("", domainOf(null))
    }

    @Test
    fun bloccoDeiSalti() {
        fun blocks(scheme: String?, host: String?, allowed: String, mainFrame: Boolean = true, redirect: Boolean = false, gesture: Boolean = false) =
            blocksNavigation(scheme, host, allowed, mainFrame, redirect, gesture)

        // schemi strani (intent://, market://) sempre bloccati
        assertTrue(blocks("intent", "x", "animeunity.so"))
        // stesso dominio, anche su un altro sottodominio
        assertFalse(blocks("https", "www.animeunity.so", "animeunity.so"))
        // salto da JS verso un altro dominio: pubblicità
        assertTrue(blocks("https", "popads.net", "animeunity.so"))
        // redirect HTTP e iframe passano
        assertFalse(blocks("https", "animeunity.to", "animeunity.so", redirect = true))
        assertFalse(blocks("https", "vixcloud.co", "animeunity.so", mainFrame = false))
        // da Google si esce solo con un tocco
        assertFalse(blocks("https", "animeunity.so", "google.com", gesture = true))
        assertTrue(blocks("https", "animeunity.so", "google.com"))
    }

    @Test
    fun riconoscimentoVideo() {
        assertTrue(isVideoPath("/hls/MASTER.M3U8"))
        assertTrue(isVideoPath("/video/ep1.mp4"))
        assertTrue(isVideoPath("/playlist/12345"))
        assertFalse(isVideoPath("/player.js"))
        assertFalse(isVideoPath(null))
    }

    @Test
    fun pubblicita() {
        assertTrue(isAd("pubads.g.doubleclick.net", "/x.m3u8"))
        assertTrue(isAd("ads.example.com", "/x.mp4"))
        assertTrue(isAd("cdn.example.com", "/vast/x.mp4"))
        assertFalse(isAd("vixcloud.co", "/playlist/1"))
        // "ad" solo come etichetta intera, non dentro un nome
        assertFalse(isAd("cdn.adriatico.it", "/x.mp4"))
    }

    @Test
    fun videoDaProporre() {
        val master = videoFor("https://vixcloud.co/playlist/1?token=a&expires=1", "ref", "vixcloud.co", "/playlist/1", "token=a&expires=1")
        assertNotNull(master)
        assertTrue(master!!.main)
        assertEquals("Playlist principale\nvixcloud.co", master.label)
        assertEquals("ref", master.referer)

        // pezzi della playlist VixCloud scartati
        assertNull(videoFor("u", "r", "vixcloud.co", "/playlist/1", "type=video&rendition=720p"))
        assertNull(videoFor("u", "r", "vixcloud.co", "/playlist/1", "token=a&rendition=1080p"))
        // pubblicità scartata
        assertNull(videoFor("u", "r", "imasdk.googleapis.com", "/ad.mp4", null))

        assertEquals("Qualità 720p\ncdn.x", videoFor("https://cdn.x/720p/index.m3u8", "r", "cdn.x", "/720p/index.m3u8", null)!!.label)
        assertEquals("Video MP4\ncdn.x", videoFor("https://cdn.x/ep.mp4", "r", "cdn.x", "/ep.mp4", null)!!.label)
        assertEquals("Playlist\ncdn.x", videoFor("https://cdn.x/index.m3u8", "r", "cdn.x", "/index.m3u8", "")!!.label)
        assertTrue(videoFor("https://cdn.x/master.m3u8", "r", "cdn.x", "/master.m3u8", null)!!.main)
    }

    @Test
    fun headerPerKodi() {
        // Kodi vuole %20 per lo spazio, non '+'
        assertEquals("Mozilla%2F5.0%20%28Linux%29", enc("Mozilla/5.0 (Linux)"))
        assertEquals("https%3A%2F%2Fa.b%2F%3Fx%3D1%26y%3D2", enc("https://a.b/?x=1&y=2"))
    }
}
