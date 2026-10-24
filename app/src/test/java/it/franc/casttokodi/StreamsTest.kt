package it.franc.casttokodi

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

// Il giro intero senza Fire TV: il telefono legge il sito finto (FakeSite), sceglie la qualità, passa playlist e
// chiave, e il Kodi finto (FakeKodi) riproduce scaricando tutto come farebbe quello vero.
class StreamsTest {

    private val headers = listOf("Referer" to "https://animeunity.so/anime/1", "User-Agent" to "Telefono")

    private fun withRelay(test: (Streams) -> Unit) {
        val relay = Relay()
        try {
            test(Streams(relay) { "127.0.0.1" })
        } finally {
            relay.close()
        }
    }

    @Test
    fun giroCompletoDalTelefonoAKodi() = withRelay { streams ->
        FakeSite().use { site ->
            FakeKodi().use { kodi ->
                val file = streams.kodiFile("${site.base}/master.m3u8", headers, viaPhone = true)
                // La playlist la serve il telefono, gli header restano per i pezzi
                assertTrue(file, file.startsWith("http://127.0.0.1:"))
                assertTrue(file, file.endsWith("|Referer=https%3A%2F%2Fanimeunity.so%2Fanime%2F1&User-Agent=Telefono"))
                Kodi(kodi.host, "").playNow(JSONObject().put("file", file))

                // Kodi apre la playlist dal telefono: solo la qualità migliore, chiave sul telefono, pezzi sul sito
                val playlist = kodi.fetchPlaying()
                assertTrue(playlist, "${site.base}/1080p/0000.ts" in playlist)
                assertTrue(playlist, "480p" !in playlist)
                val key = Regex("URI=\"([^\"]+)\"").find(playlist)!!.groupValues[1]
                assertTrue(key, key.startsWith("http://127.0.0.1:"))
                assertArrayEquals(FakeSite.KEY, URL(key).readBytes())
                assertEquals("pezzo 0", FakeKodi.fetch("${site.base}/1080p/0000.ts|${file.substringAfter('|')}"))

                // Il telefono ha chiesto al sito una volta sola master, qualità e chiave, con il Referer della pagina
                assertEquals(1, site.hits("/master.m3u8"))
                assertEquals(1, site.hits("/1080p.m3u8"))
                assertEquals(1, site.hits("/storage/enc.key"))
                assertEquals(0, site.hits("/480p.m3u8"))
                assertEquals("https://animeunity.so/anime/1", site.referers["/master.m3u8"])
                assertEquals("https://animeunity.so/anime/1", site.referers["/1080p/0000.ts"])
            }
        }
    }

    // In coda il link va diretto alla qualità migliore: quando tocca a lui il telefono può essere spento
    @Test
    fun inCodaSenzaTelefono() = withRelay { streams ->
        FakeSite().use { site ->
            val file = streams.kodiFile("${site.base}/master.m3u8", headers, viaPhone = false)
            assertEquals("${site.base}/1080p.m3u8?token=x", queueKey(file))
            assertEquals(FakeSite.media("1080p"), FakeKodi.fetch(file))
            assertEquals(0, site.hits("/storage/enc.key"))
        }
    }

    // vidxgo: la master vale una volta sola. Quella scaricata per il WebView si riusa, senza un'altra richiesta.
    @Test
    fun playlistMonousoRiusata() = withRelay { streams ->
        FakeSite(oneShot = true).use { site ->
            val master = "${site.base}/master.m3u8"
            streams.capture(master, URL(master).readBytes(), master)
            val file = streams.kodiFile(master, headers, viaPhone = true)
            assertEquals(1, site.hits("/master.m3u8"))
            assertTrue(file, file.startsWith("http://127.0.0.1:"))
            assertTrue(FakeKodi.fetch(file).contains("${site.base}/1080p/0001.ts"))
        }
    }

    // Senza la copia la seconda richiesta prende 403: si manda il link com'era, e Kodi avrà lo stesso problema
    @Test
    fun playlistMonousoSenzaCopia() = withRelay { streams ->
        FakeSite(oneShot = true).use { site ->
            val master = "${site.base}/master.m3u8"
            URL(master).readBytes()
            assertEquals(master, queueKey(streams.kodiFile(master, headers, viaPhone = true)))
        }
    }

    @Test
    fun sitoRottoSiMandaIlLinkCosiCome() = withRelay { streams ->
        FakeSite(broken = true).use { site ->
            val master = "${site.base}/master.m3u8"
            assertEquals(master, queueKey(streams.kodiFile(master, headers, viaPhone = true)))
        }
    }

    // Senza Wi-Fi (nessun IP locale) Kodi riceve il link diretto alla qualità
    @Test
    fun senzaWifiLinkDiretto() {
        val relay = Relay()
        try {
            FakeSite().use { site ->
                val file = Streams(relay) { null }.kodiFile("${site.base}/master.m3u8", headers, viaPhone = true)
                assertEquals("${site.base}/1080p.m3u8?token=x", queueKey(file))
            }
        } finally {
            relay.close()
        }
    }

    @Test
    fun mp4NonSiTocca() = withRelay { streams ->
        FakeSite().use { site ->
            val file = streams.kodiFile("${site.base}/film.mp4?t=1", headers, viaPhone = true)
            assertEquals("${site.base}/film.mp4?t=1", queueKey(file))
            assertEquals(0, site.hits("/film.mp4"))
        }
    }

    // Anteprima: durata e un pezzo decifrato della qualità più leggera, senza toccare quella buona
    @Test
    fun anteprimaDallaQualitaLeggera() = withRelay { streams ->
        FakeSite().use { site ->
            val preview = streams.preview("${site.base}/master.m3u8", headers)
            assertEquals(8L, preview.seconds)
            assertEquals("anteprima 0", String(preview.sample!!))
            assertEquals(1, site.hits("/480p.m3u8"))
            assertEquals(0, site.hits("/1080p.m3u8"))
            assertEquals("https://animeunity.so/anime/1", site.referers["/480p/0000.ts"])
        }
    }

    // Dopo l'anteprima la master monouso non si richiede: l'invio usa la copia
    @Test
    fun anteprimaNonConsumaLaPlaylistMonouso() = withRelay { streams ->
        FakeSite(oneShot = true).use { site ->
            val master = "${site.base}/master.m3u8"
            streams.preview(master, headers)
            val file = streams.kodiFile(master, headers, viaPhone = true)
            assertEquals(1, site.hits("/master.m3u8"))
            assertTrue(FakeKodi.fetch(file).contains("${site.base}/1080p/0001.ts"))
        }
    }

    // Pezzi troppo grossi: niente immagine, la durata resta
    @Test
    fun anteprimaSenzaPezzoTroppoGrosso() = withRelay { streams ->
        FakeSite().use { site ->
            val preview = streams.preview("${site.base}/master.m3u8", headers, limit = 4)
            assertEquals(8L, preview.seconds)
            assertEquals(null, preview.sample)
        }
    }
}
