package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

// Timer di spegnimento: le scelte con l'ora, il comando lasciato alla Fire TV e la riga del timer attivo
class SleepTimerTest {
    private val utc = ZoneId.of("UTC")
    private val now = 22 * 3600L + 50 * 60 // 22:50

    private fun playing(time: Long, total: Long) =
        NowPlaying(1, "", "f", time, total, false, emptyList(), -1, emptyList(), -1, false, 50, false)

    @Test
    fun sceltaConLaCoda() {
        val state = KodiState(playerId = 1, fromQueue = true, position = 0, remaining = 23 * 60L, episode = 24 * 60L, queueLength = 3)
        val choices = timerChoices(playing(60, 24 * 60L), state, now, utc)
        assertEquals(listOf("Fine di questo episodio", "Fine della coda", "Tra 30 minuti", "Tra 1 ora", "Altro tempo…"), choices.map { it.title })
        assertEquals(23 * 60L - TIMER_END_MARGIN, choices[0].seconds)
        assertEquals("alle 23:12", choices[0].detail)
        assertEquals("2 episodi dopo questo, alle 00:00", choices[1].detail)
        assertEquals(30 * 60L, choices[2].seconds)
        assertNull(choices[4].seconds)
    }

    @Test
    fun kodiFermoSoloMinuti() {
        val choices = timerChoices(null, null, now, utc)
        assertEquals(listOf("Tra 30 minuti", "Tra 1 ora", "Altro tempo…"), choices.map { it.title })
    }

    @Test
    fun comandoPerLaFireTv() {
        val command = timerCommand(600, TimerAction.TV_OFF, 1234)
        assertTrue(command.startsWith("nohup sh -c '"))
        assertTrue("sleep 600" in command)
        assertTrue("echo 1234 > $TIMER_FILE" in command)
        assertTrue("= \"1234\" ] || exit 0" in command)
        assertTrue("input keyevent KEYCODE_MEDIA_STOP; sleep 1; input keyevent KEYCODE_SLEEP" in command)
        assertTrue(command.endsWith("</dev/null >/dev/null 2>&1 &"))
        // Mai meno di un secondo
        assertTrue("sleep 1;" in timerCommand(-5, TimerAction.PAUSE, 1))
    }

    @Test
    fun sceltaRicordata() {
        assertEquals(TimerAction.STOP, timerAction("stop"))
        assertEquals(DEFAULT_TIMER_ACTION, timerAction(null))
        assertEquals(DEFAULT_TIMER_ACTION, timerAction("altro"))
    }

    @Test
    fun minutiAMano() {
        assertEquals(45, parseTimerMinutes(" 45 "))
        assertNull(parseTimerMinutes("0"))
        assertNull(parseTimerMinutes("1:30"))
        assertNull(parseTimerMinutes("800"))
    }

    @Test
    fun rigaDelTimer() {
        assertEquals("Si spegne alle 23:13", timerLabel(TimerAction.TV_OFF, 23 * 3600L + 13 * 60, utc))
        assertEquals("Pausa alle 23:13", timerLabel(TimerAction.PAUSE, 23 * 3600L + 13 * 60, utc))
    }
}
