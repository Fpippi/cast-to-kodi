package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Sottotitoli: lingue dai nomi dei file, tracce della master, .srt in WebVTT, pezzi uniti, scelta della serie
class SubtitlesTest {

    @Test
    fun linguaDalNomeDelFile() {
        assertEquals("it", subtitleLanguage("https://cdn.example/sub/episodio5_ita.vtt"))
        assertEquals("en", subtitleLanguage("https://cdn.example/sub/English.srt"))
        assertEquals("it", subtitleLanguage("https://cdn.example/sub/track.vtt?lang=it"))
        assertNull(subtitleLanguage("https://cdn.example/sub/track.vtt"))
        assertTrue(isSubtitlePath("/a/b.VTT"))
        assertFalse(isSubtitlePath("/a/b.m3u8"))
    }

    @Test
    fun lingueConfrontate() {
        assertEquals("ita", languageKey("it"))
        assertEquals("ita", languageKey("ita"))
        assertEquals("ita", languageKey("Italiano"))
        assertEquals("jpn", languageKey("jp"))
        assertNull(languageKey("und"))
        assertEquals("Italiano", languageName("ita"))
    }

    @Test
    fun tracceDellaMaster() {
        val list = masterSubtitles(FakeSite.MASTER_SUBS, "https://cdn.example/v/master.m3u8")
        assertEquals(listOf("ita", "eng"), list.map { it.language })
        assertEquals("https://cdn.example/v/subs/ita.m3u8", list[0].url)
        assertFalse(list[0].forced)
        assertTrue(list[1].forced)
        assertTrue(masterSubtitles(FakeSite.MASTER, "https://cdn.example/v/master.m3u8").isEmpty())
    }

    @Test
    fun srtInWebVtt() {
        val vtt = toVtt("﻿1\r\n00:00:01,000 --> 00:00:02,500\r\nCiao, come va\r\n")
        assertTrue(vtt, vtt.startsWith("WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.500\n"))
        // Le virgole del testo restano
        assertTrue(vtt, "Ciao, come va" in vtt)
    }

    @Test
    fun pezziUniti() {
        val merged = mergeVtt(listOf(
            "WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:900000,LOCAL:00:00:00.000\n\n00:00:01.000 --> 00:00:02.000\nUno\n",
            "WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:900000,LOCAL:00:00:00.000\n\n00:00:05.000 --> 00:00:06.000\nDue\n",
        ))
        assertEquals("WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:900000,LOCAL:00:00:00.000\n\n00:00:01.000 --> 00:00:02.000\nUno\n\n00:00:05.000 --> 00:00:06.000\nDue\n", merged)
    }

    @Test
    fun masterPerKodi() {
        val master = subtitleMaster("http://p/v.m3u8", 4500000, listOf(Triple("it", "Italiano · dal sito", "http://p/s0.m3u8")))
        assertTrue(master, "TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"Italiano · dal sito\",LANGUAGE=\"it\",DEFAULT=NO,AUTOSELECT=NO,URI=\"http://p/s0.m3u8\"" in master)
        assertTrue(master, master.endsWith("#EXT-X-STREAM-INF:BANDWIDTH=4500000,SUBTITLES=\"subs\"\nhttp://p/v.m3u8\n"))
        val playlist = subtitlePlaylist("http://p/s0.vtt", 1440.5)
        assertTrue(playlist, "#EXT-X-TARGETDURATION:1441" in playlist && "#EXTINF:1440.500,\nhttp://p/s0.vtt" in playlist)
    }

    @Test
    fun avvisoEdEtichette() {
        val ita = SubtitleTrack("it", "", "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nx", fromSite = true)
        val eng = SubtitleTrack("eng", "English", "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nx", fromSite = false)
        assertEquals("con sottotitoli in italiano e inglese", subtitlesNote(listOf(ita, eng)))
        assertNull(subtitlesNote(emptyList()))
        assertEquals("CC Italiano, Inglese", ccLabel(listOf("it", "eng", "ita")))
        assertEquals("CC sottotitoli", ccLabel(listOf(null)))
        // Un file senza battute non va
        assertEquals(listOf(eng), pickSubtitles(listOf(SubtitleTrack("it", "", "WEBVTT\n", true), eng, eng)))
        assertEquals("Italiano · dal sito", subtitleName("it", "", fromSite = true, number = 1))
    }

    @Test
    fun pezziDelPlayerScartati() {
        val pieces = (0..9).map { "https://cdn.example/subs/ita/$it.vtt" }
        val files = listOf("https://cdn.example/page/ita.vtt", "https://cdn.example/page/eng.vtt")
        assertEquals(files, siteSubtitleFiles(pieces + files))
    }

    @Test
    fun tracciaDellaSerie() {
        val tracks = listOf(Track(0, "eng", "English"), Track(1, "ita", "Italiano · Forced"), Track(2, "ita", "Italiano"))
        assertEquals(2, subtitleFor(tracks, "ita")?.index)
        assertEquals(0, subtitleFor(tracks, "eng")?.index)
        assertNull(subtitleFor(tracks, "jpn"))
        assertNull(subtitleFor(tracks, null))
        assertEquals("ita", trackLanguage(Track(3, "", "Italiano · dal sito")))
        assertEquals("dal sito", subtitleSource(Track(3, "it", "Italiano · dal sito")))
        assertEquals("Italiano", subtitleTitle(Track(3, "ita", "Italiano · dal sito"), 1))
    }

    @Test
    fun sceltaRicordataNellaSerie() {
        val show = Show("animeunity/anime/1-jojo", "animeunity", "animeunity.so", "JoJo",
            listOf(Episode(null, 1, "/anime/1-jojo/1", file = "http://cdn/1080p.m3u8")))
        val shows = withShowSubtitles(listOf(show), show.key, "ita")
        assertEquals("ita", subtitlesOf(shows, "http://cdn/1080p.m3u8|Referer=x"))
        assertEquals("ita", showsFromJson(showsToJson(shows)).single().subtitles)
        assertNull(subtitlesOf(withShowSubtitles(shows, show.key, null), "http://cdn/1080p.m3u8"))
    }
}
