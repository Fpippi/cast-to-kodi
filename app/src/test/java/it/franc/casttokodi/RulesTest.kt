package it.franc.casttokodi

import it.franc.casttokodi.sites.GOOGLE
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
        // host:porta in HTTP, non su Google né in HTTPS
        assertEquals("http://localhost:8080", addressFor("localhost:8080"))
        assertEquals("http://192.168.1.50:8080/jsonrpc", addressFor("192.168.1.50:8080/jsonrpc"))
    }

    @Test
    fun dominio() {
        assertEquals("animeunity.so", domainOf("www.animeunity.so"))
        assertEquals("vixcloud.co", domainOf("vixcloud.co"))
        assertEquals("", domainOf(null))
    }

    @Test
    fun bloccoDeiSalti() {
        fun blocks(scheme: String?, host: String?, allowed: String, mainFrame: Boolean = true, redirect: Boolean = false) =
            blocksNavigation(scheme, host, allowed, mainFrame, redirect)

        // schemi strani (intent://, market://) sempre bloccati
        assertTrue(blocks("intent", "x", "animeunity.so"))
        // stesso dominio, anche su un altro sottodominio
        assertFalse(blocks("https", "www.animeunity.so", "animeunity.so"))
        // salto da JS verso un altro dominio: pubblicità
        assertTrue(blocks("https", "popads.net", "animeunity.so"))
        // redirect HTTP e iframe passano
        assertFalse(blocks("https", "animeunity.to", "animeunity.so", redirect = true))
        assertFalse(blocks("https", "vixcloud.co", "animeunity.so", mainFrame = false))
        // da Google si esce sempre, anche da JS: alcuni risultati si aprono così
        assertFalse(blocks("https", "animeunity.so", "google.com"))
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
        // circuiti dei siti di streaming e video pre-roll
        assertTrue(isAd("s.adsterra.com", "/x.mp4"))
        assertTrue(isAd("cdn.vidoomy.com", "/x.m3u8"))
        assertTrue(isAd("cdn.example.com", "/preroll/x.mp4"))
        assertFalse(isAd("vixcloud.co", "/playlist/1"))
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

    private val casa = "192.168.1.0/24 via 192.168.1.1"
    private val fireTv = SavedLogin("192.168.1.50:8080", "Kodi (Fire TV)", casa, "kodi", "cifrata")

    @Test
    fun improntaDellaRete() {
        assertEquals(casa, networkKey("192.168.1.23", 24, "192.168.1.1"))
        assertEquals("10.0.0.0/8 via 10.0.0.1", networkKey("10.20.30.40", 8, "10.0.0.1"))
        assertNull(networkKey("192.168.1.23", 24, null))
        assertNull(networkKey(null, 24, "192.168.1.1"))
        assertNull(networkKey("fe80::1", 64, "192.168.1.1"))
        assertNull(networkKey("192.168.1.300", 24, "192.168.1.1"))
    }

    @Test
    fun credenzialiDaProvare() {
        val saved = listOf(fireTv)
        assertEquals(fireTv, loginFor(saved, "192.168.1.50:8080", "", casa))
        // Stesso Kodi a un IP nuovo, riconosciuto dal nome annunciato
        assertEquals(fireTv, loginFor(saved, "192.168.1.77:8080", "Kodi (Fire TV)", casa))
        // Un altro Kodi sulla stessa rete, o un'altra rete con lo stesso IP: si chiedono
        assertNull(loginFor(saved, "192.168.1.77:8080", "", casa))
        assertNull(loginFor(saved, "192.168.1.50:8080", "Kodi (Fire TV)", "192.168.0.0/24 via 192.168.0.1"))
        assertNull(loginFor(saved, "192.168.1.50:8080", "", null))
        // Quelle di prima dell'aggiornamento valgono per il loro host su qualunque rete
        val old = SavedLogin("192.168.1.50:8080", "", "", "kodi", "cifrata")
        assertEquals(old, loginFor(listOf(old), "192.168.1.50:8080", "", null))
        assertNull(loginFor(listOf(old), "192.168.1.51:8080", "", casa))
    }

    @Test
    fun salvareCredenziali() {
        val nuove = fireTv.copy(user = "pippo")
        assertEquals(listOf(nuove), replaceLogin(listOf(fireTv), fireTv.host, fireTv.name, casa, nuove))
        assertEquals(emptyList<SavedLogin>(), replaceLogin(listOf(fireTv), fireTv.host, "", casa, null))
        // Una vecchia senza rete viene sostituita da quella legata alla rete
        val old = SavedLogin(fireTv.host, "", "", "kodi", "vecchia")
        assertEquals(listOf(fireTv), replaceLogin(listOf(old), fireTv.host, fireTv.name, casa, fireTv))
        // Le credenziali di un'altra rete restano
        val altrove = fireTv.copy(network = "10.0.0.0/8 via 10.0.0.1")
        assertEquals(listOf(altrove, nuove), replaceLogin(listOf(altrove), fireTv.host, fireTv.name, casa, nuove))
    }

    @Test
    fun credenzialiSeguonoKodi() {
        val moved = moveLogin(listOf(fireTv), fireTv.host, "192.168.1.77:8080", "", casa)
        assertEquals(listOf(fireTv.copy(host = "192.168.1.77:8080")), moved)
        assertEquals(listOf(fireTv), moveLogin(listOf(fireTv), "192.168.1.60:8080", "192.168.1.77:8080", "", casa))
        assertEquals(listOf(fireTv), moveLogin(listOf(fireTv), fireTv.host, "192.168.1.77:8080", "", null))
    }

    @Test
    fun vecchioCampoAccesso() {
        assertEquals("kodi" to "a:b", splitAuth("kodi:a:b"))
        assertEquals("kodi" to "", splitAuth("kodi"))
    }

    @Test
    fun preferiti() {
        assertEquals("Guarda JoJo Episodio 3 Streaming SUB ITA",
            suggestedName("Guarda JoJo Episodio 3 Streaming SUB ITA - AnimeUnity", "www.animeunity.so"))
        assertEquals("Film", suggestedName("  Film  |  Sito ", "sito.it"))
        assertEquals("animeunity.so", suggestedName("   ", "www.animeunity.so"))
        assertEquals("animeunity.so", suggestedName(null, "www.animeunity.so"))
        assertEquals("- Solo trattino", suggestedName("- Solo trattino", "x.it"))

        // Nome vuoto = si tiene quello vecchio; spazi ripuliti
        assertEquals("Vecchio", renamed("   ", "Vecchio"))
        assertEquals("JoJo stagione 2", renamed("  JoJo   stagione 2 ", "Vecchio"))

        assertEquals("J", initialOf("jojo", "animeunity.so"))
        assertEquals("3", initialOf("★ 3 metri", null))
        assertEquals("A", initialOf("★", "www.animeunity.so"))
        assertEquals("?", initialOf("", null))
    }

    @Test
    fun versioni() {
        assertEquals(listOf(1, 10, 0), versionParts("v1.10.0"))
        assertEquals(listOf(2, 0, 3), versionParts(" 2.0.3 "))
        assertNull(versionParts("build-18"))
        assertNull(versionParts("1.0"))

        assertTrue(isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(isNewerVersion("v1.10.0", "1.9.9")) // si confrontano i numeri, non il testo
        assertTrue(isNewerVersion("v2.0.0", "1.12.4"))
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("v1.0.0", "1.1.0"))
        assertFalse(isNewerVersion("build-18", "1.0.0"))
        assertTrue(isNewerVersion("v1.0.0", "0.1"))
    }

    @Test
    fun qualitaMigliore() {
        val master = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1200000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=854x480
https://vixcloud.cc/playlist/1?type=video&rendition=480p&token=a
#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=4000000,BANDWIDTH=4500000,RESOLUTION=1920x1080
https://vixcloud.cc/playlist/1?type=video&rendition=1080p&token=c
#EXT-X-STREAM-INF:BANDWIDTH=2150000,RESOLUTION=1280x720
https://vixcloud.cc/playlist/1?type=video&rendition=720p&token=b
"""
        assertEquals("https://vixcloud.cc/playlist/1?type=video&rendition=1080p&token=c", bestVariant(master, "https://vixcloud.cc/playlist/1?token=x"))
        // percorsi relativi alla master
        assertEquals("https://cdn.example.com/hls/720/index.m3u8", bestVariant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n720/index.m3u8\n", "https://cdn.example.com/hls/master.m3u8"))
        // audio in una traccia a parte: resta la master
        assertNull(bestVariant("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",URI=\"audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO=\"a\"\nv.m3u8\n", "https://x.com/m.m3u8"))
        // non è una master: una playlist dei pezzi
        assertNull(bestVariant("#EXTM3U\n#EXTINF:4,\n0001.ts\n", "https://x.com/v.m3u8"))
    }

    @Test
    fun nomiDellaCoda() {
        val file = "https://vixcloud.cc/playlist/1?type=video&rendition=1080p&token=t|Referer=x&User-Agent=y"
        val titles = withTitle(emptyMap(), file, "JoJo Episodio 3")
        // Kodi può restituire il link con o senza gli header
        assertEquals("JoJo Episodio 3", queueLabel(file, "1?type=video", titles))
        assertEquals("JoJo Episodio 3", queueLabel(queueKey(file), "", titles))
        // non ricordato: il titolo di Kodi se è un nome, altrimenti il sito
        assertEquals("Video da vixcloud.cc", queueLabel(file, "1?type=video&rendition=1080p", emptyMap()))
        assertEquals("Video da vixcloud.cc", queueLabel(file, "", emptyMap()))
        assertEquals("Trailer", queueLabel("https://www.x.com/a.mp4", "Trailer", emptyMap()))
        assertEquals("Film.mkv", queueLabel("/sdcard/Film.mkv", "Film.mkv", emptyMap()))
        // il nome nuovo sostituisce il vecchio e i più vecchi escono
        val many = (1..5).fold(emptyMap<String, String>()) { acc, i -> withTitle(acc, "https://x.com/$i.m3u8", "Ep $i", max = 3) }
        assertEquals(listOf("Ep 3", "Ep 4", "Ep 5"), many.values.toList())
        assertEquals("Nuovo", withTitle(many, "https://x.com/3.m3u8|R=1", "Nuovo", max = 3)["https://x.com/3.m3u8"])
    }
}
