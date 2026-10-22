package it.franc.casttokodi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

// "Manda a Kodi", "+ Coda" e la coda di Kodi provati contro FakeKodi, come se ci fosse la Fire TV
class KodiTest {

    private fun item(file: String) = JSONObject().put("file", file)

    private fun noProblem(startsIn: Long): String? = null

    @Test
    fun mandaConKodiFermo() = FakeKodi().use { fake ->
        val sent = Kodi(fake.host, "").playNow(item("a"))
        assertEquals(listOf("a"), fake.playlist)
        assertEquals("a", fake.playing)
        assertEquals(0, fake.position)
        assertEquals(0, sent.position)
    }

    @Test
    fun mandaDuranteLaCodaEntraSubitoDopo() = FakeKodi().use { fake ->
        fake.playlist += listOf("a", "b")
        fake.playingFromQueue(0)
        val sent = Kodi(fake.host, "").playNow(item("c"))
        // c parte subito e b resta dopo: la coda non si perde
        assertEquals(listOf("a", "c", "b"), fake.playlist)
        assertEquals("c", fake.playing)
        assertEquals(1, sent.position)
        assertTrue("Playlist.Clear" !in fake.calls)
    }

    @Test
    fun mandaConVideoFuoriCodaVaInFondo() = FakeKodi().use { fake ->
        fake.playlist += "a"
        fake.playingOutside("x")
        val sent = Kodi(fake.host, "").playNow(item("c"))
        assertEquals(listOf("a", "c"), fake.playlist)
        assertEquals("c", fake.playing)
        assertEquals(1, fake.position)
        assertEquals(1, sent.position)
    }

    // #11: se Kodi rifiuta il link in playlist il video parte lo stesso, senza coda
    @Test
    fun ripiegoSenzaCoda() = FakeKodi().use { fake ->
        fake.rejectInPlaylist = true
        val sent = Kodi(fake.host, "").playNow(item("c"))
        assertEquals("c", fake.playing)
        assertEquals(-1, fake.position)
        assertTrue(fake.playlist.isEmpty())
        assertNull(sent.position)
    }

    @Test
    fun accodaInFondoSenzaInterrompere() = FakeKodi().use { fake ->
        fake.playlist += listOf("a", "b")
        fake.playingFromQueue(0, watched = 60, total = 24 * 60)
        val viaPhone = mutableListOf<Boolean>()
        val sent = Kodi(fake.host, "").enqueue({ viaPhone += it; item("c") }, ::noProblem)
        assertEquals(listOf("a", "b", "c"), fake.playlist)
        assertEquals("a", fake.playing)
        assertEquals(2, sent.position)
        // In coda il link va diretto: il telefono serve solo per i video che partono subito
        assertEquals(listOf(false), viaPhone)
    }

    @Test
    fun accodaConKodiFermoParteSubito() = FakeKodi().use { fake ->
        val viaPhone = mutableListOf<Boolean>()
        Kodi(fake.host, "").enqueue({ viaPhone += it; item("c") }, ::noProblem)
        assertEquals("c", fake.playing)
        assertEquals(listOf(true), viaPhone)
    }

    @Test
    fun nonAccodaSeIlVideoInCorsoNonEDellApp() = FakeKodi().use { fake ->
        fake.playlist += "a"
        fake.playingOutside("x")
        try {
            Kodi(fake.host, "").enqueue({ item("c") }, ::noProblem)
            fail("doveva rifiutare")
        } catch (e: Refused) {
            assertTrue(e.message!!.contains("non è partito dall'app"))
        }
        assertEquals(listOf("a"), fake.playlist)
    }

    // Il link scade prima del suo turno: si stima con episodi lunghi come quello in corso
    @Test
    fun nonAccodaSeIlLinkScadePrima() = FakeKodi().use { fake ->
        fake.playlist += listOf("a", "b")
        fake.playingFromQueue(0, watched = 4 * 60, total = 24 * 60)
        var startsIn = -1L
        try {
            Kodi(fake.host, "").enqueue({ item("c") }) { startsIn = it; "scade" }
            fail("doveva rifiutare")
        } catch (e: Refused) {
            assertEquals("scade", e.message)
        }
        // 20 minuti che restano di a, più tutto b
        assertEquals(20 * 60L + 24 * 60L, startsIn)
        assertEquals(listOf("a", "b"), fake.playlist)
    }

    @Test
    fun episodiSuccessiviDopoQuelloMandato() = FakeKodi().use { fake ->
        val kodi = Kodi(fake.host, "")
        val sent = kodi.playNow(item("ep1"))
        assertNull(kodi.insertAt(sent.position!! + 1, { item("ep2") }, ::noProblem))
        assertNull(kodi.insertAt(sent.position!! + 2, { item("ep3") }, ::noProblem))
        assertEquals(listOf("ep1", "ep2", "ep3"), fake.playlist)
        assertEquals("ep1", fake.playing)
        assertEquals("scade", kodi.insertAt(3, { item("ep4") }) { "scade" })
        assertEquals(3, fake.playlist.size)
    }

    @Test
    fun codaVistaDallApp() = FakeKodi().use { fake ->
        fake.playlist += listOf("a", "b", "c")
        fake.playingFromQueue(1, watched = 0, total = 20 * 60)
        val kodi = Kodi(fake.host, "")
        val (state, items) = kodi.queue()
        assertEquals(listOf("a", "b", "c"), items.map { it.getString("file") })
        assertTrue(state.fromQueue)
        assertEquals(1, state.position)
        assertEquals(20 * 60L, state.secondsUntil(2))

        // Tocco su un video: si salta lì
        kodi.playFrom(state, 2)
        assertEquals("c", fake.playing)

        // "Svuota": resta solo quello in corso
        kodi.clearQueue(2)
        assertEquals(listOf("c"), fake.playlist)
        assertEquals(0, fake.position)
    }

    @Test
    fun codaVuotaEKodiFermo() = FakeKodi().use { fake ->
        val kodi = Kodi(fake.host, "")
        val (state, items) = kodi.queue()
        assertTrue(items.isEmpty())
        assertNull(state.playerId)

        // Con Kodi fermo il tocco fa partire la coda da lì
        fake.playlist += listOf("a", "b")
        kodi.playFrom(kodi.queue().first, 1)
        assertEquals("b", fake.playing)
        assertEquals(1, fake.position)
        kodi.remove(0)
        assertEquals(listOf("b"), fake.playlist)
    }

    // Video partito fuori dalla coda: la coda lo mostra a parte, così non sembra vuota senza motivo
    @Test
    fun codaConVideoFuoriCoda() = FakeKodi().use { fake ->
        fake.playingOutside("https://x.com/film.m3u8")
        val kodi = Kodi(fake.host, "")
        val (state, items) = kodi.queue()
        assertTrue(items.isEmpty())
        assertEquals("https://x.com/film.m3u8", kodi.playingOutside(state)?.getString("file"))

        // Dalla coda invece niente: è già nell'elenco
        fake.playlist += "a"
        fake.playingFromQueue(0)
        assertNull(kodi.playingOutside(kodi.queue().first))
    }

    @Test
    fun password() = FakeKodi(login = "kodi:segreta").use { fake ->
        for (auth in listOf("", "kodi:sbagliata")) {
            try {
                Kodi(fake.host, auth).playNow(item("a"))
                fail("doveva chiedere la password")
            } catch (e: Unauthorized) {
            }
        }
        assertTrue(fake.playlist.isEmpty())
        Kodi(fake.host, "kodi:segreta").playNow(item("a"))
        assertEquals("a", fake.playing)
    }

    // Kodi spento o con un altro IP: errore di rete, e l'app lo cerca in rete
    @Test
    fun kodiCheNonRisponde() {
        val port = ServerSocket(0).use { it.localPort }
        try {
            Kodi("127.0.0.1:$port", "").state()
            fail("doveva fallire")
        } catch (e: IOException) {
        }
    }

    @Test
    fun metodoSconosciuto() = FakeKodi().use { fake ->
        try {
            Kodi(fake.host, "").call("Player.Volare")
            fail("doveva fallire")
        } catch (e: KodiError) {
            assertEquals("Player.Volare: Method not found.", e.message)
        }
    }
}
