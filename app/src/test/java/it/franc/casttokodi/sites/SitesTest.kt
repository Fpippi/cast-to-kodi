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
}
