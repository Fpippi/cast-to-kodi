package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Il telecomando di Kodi provato contro FakeKodi: cosa legge dal video in corso e cosa fanno i pulsanti
class RemoteTest {

    // Kodi che riproduce il secondo di tre episodi in coda, a 10 minuti su 24
    private fun playing(fake: FakeKodi) {
        fake.playlist += listOf("ep1", "ep2", "ep3")
        fake.playingFromQueue(1, watched = 600, total = 24 * 60)
    }

    @Test
    fun kodiFermo() = FakeKodi().use { fake ->
        val now = Kodi(fake.host, "").nowPlaying()
        assertNull(now.playerId)
        assertEquals(50, now.volume)
    }

    @Test
    fun leggeIlVideoInCorso() = FakeKodi().use { fake ->
        playing(fake)
        val now = Kodi(fake.host, "").nowPlaying()
        assertEquals(1, now.playerId)
        assertEquals("ep2", now.file)
        assertEquals("ep2", now.title)
        assertEquals(600, now.time)
        assertEquals(24 * 60L, now.total)
        assertFalse(now.paused)
        assertEquals(listOf("ita", "jpn"), now.audio.map { it.language })
        assertEquals(0, now.currentAudio)
        assertFalse(now.subtitlesOn)
    }

    @Test
    fun playPausaEStop() = FakeKodi().use { fake ->
        playing(fake)
        val kodi = Kodi(fake.host, "")
        kodi.playPause(1)
        assertTrue(kodi.nowPlaying().paused)
        kodi.playPause(1)
        assertFalse(kodi.nowPlaying().paused)
        kodi.stop(1)
        assertNull(kodi.nowPlaying().playerId)
        // La coda resta: stop non la cancella
        assertEquals(3, fake.playlist.size)
    }

    @Test
    fun salti() = FakeKodi().use { fake ->
        playing(fake)
        val kodi = Kodi(fake.host, "")
        kodi.skip(1, 30)
        assertEquals(630, fake.time)
        kodi.skip(1, -10)
        assertEquals(620, fake.time)
        kodi.seekTo(1, 12 * 60 + 30)
        assertEquals(750, fake.time)
        kodi.seekTo(1, 3 * 3600)
        assertEquals(24 * 60L, fake.time)
    }

    @Test
    fun episodioSuccessivoEPrecedente() = FakeKodi().use { fake ->
        playing(fake)
        val kodi = Kodi(fake.host, "")
        kodi.next(1)
        assertEquals("ep3", fake.playing)
        kodi.previous(1)
        kodi.previous(1)
        assertEquals("ep1", fake.playing)
    }

    @Test
    fun velocita() = FakeKodi().use { fake ->
        playing(fake)
        val kodi = Kodi(fake.host, "")
        kodi.setTempo(1, 1.25)
        assertEquals(1.25, fake.tempo, 0.0)
        try {
            kodi.setTempo(1, 2.0)
            fail("Kodi non va oltre 1,5x")
        } catch (e: KodiError) {
            assertTrue(e.message!!.startsWith("Player.SetTempo"))
        }
    }

    @Test
    fun volume() = FakeKodi().use { fake ->
        val kodi = Kodi(fake.host, "")
        kodi.volume(up = true)
        assertEquals(60, kodi.nowPlaying().volume)
        kodi.volume(up = false)
        kodi.volume(up = false)
        assertEquals(40, fake.volume)
        kodi.toggleMute()
        assertTrue(kodi.nowPlaying().muted)
        kodi.toggleMute()
        assertFalse(fake.muted)
    }

    @Test
    fun audioESottotitoli() = FakeKodi().use { fake ->
        playing(fake)
        val kodi = Kodi(fake.host, "")
        var now = kodi.nowPlaying()
        kodi.setAudio(1, now.audio[1])
        kodi.setSubtitle(1, now.subtitles[0])
        now = kodi.nowPlaying()
        assertEquals(1, now.currentAudio)
        assertTrue(now.subtitlesOn)
        assertEquals(0, now.currentSubtitle)
        kodi.setSubtitle(1, null)
        assertFalse(kodi.nowPlaying().subtitlesOn)
    }

    @Test
    fun tastiETesto() = FakeKodi().use { fake ->
        val kodi = Kodi(fake.host, "")
        listOf("Up", "Down", "Left", "Right", "Select", "Back", "Home", "ContextMenu").forEach { kodi.press(it) }
        kodi.sendText("jojo")
        assertEquals("jojo", fake.text)
        assertEquals(
            listOf("Input.Up", "Input.Down", "Input.Left", "Input.Right", "Input.Select", "Input.Back", "Input.Home", "Input.ContextMenu", "Input.SendText"),
            fake.calls,
        )
    }
}
