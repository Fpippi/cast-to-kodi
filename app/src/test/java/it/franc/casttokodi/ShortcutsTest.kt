package it.franc.casttokodi

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Scorciatoie tenendo premuta l'icona: quali, in che ordine e con che nomi
class ShortcutsTest {
    @After
    fun italiano() {
        Lang.english = false
    }

    private val fireForce = Show(
        "animeunity:/anime/2-fire-force", "animeunity", "www.animeunity.to", "Fire Force",
        listOf(Episode(null, 7, "/anime/2-fire-force/107", time = 754, total = 1420, seen = 2)),
    )

    @Test
    fun conUnaSerieDaRiprendere() {
        val list = appShortcuts(kodiChosen = true, shows = listOf(fireForce))
        assertEquals(listOf(ShortcutKind.WAKE, ShortcutKind.REMOTE, ShortcutKind.RESUME, ShortcutKind.QUEUE), list.map { it.kind })
        val resume = list[2]
        assertEquals("Riprendi Fire Force", resume.long)
        assertEquals(fireForce.key, resume.show)
        assertEquals("resume", resume.id)
        assertTrue(list.all { it.short.length <= 12 && it.long.length <= 25 })
    }

    @Test
    fun senzaSerieOSenzaKodi() {
        assertEquals(listOf(ShortcutKind.WAKE, ShortcutKind.REMOTE, ShortcutKind.QUEUE), appShortcuts(true, emptyList()).map { it.kind })
        assertTrue(appShortcuts(false, listOf(fireForce)).isEmpty())
    }

    @Test
    fun nomeLungoTagliato() {
        val long = fireForce.copy(title = "JoJo's Bizarre Adventure: Stone Ocean")
        assertEquals(25, appShortcuts(true, listOf(long))[2].long.length)
        Lang.english = true
        assertEquals("Resume Fire Force", appShortcuts(true, listOf(fireForce))[2].long)
        assertEquals("TV on", appShortcuts(true, listOf(fireForce))[0].short)
    }
}
