package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class RelayTest {

    @Test
    fun serveIFile() {
        val relay = Relay(keep = 2)
        try {
            val base = "http://127.0.0.1:${relay.port()}"
            relay.put("v1.m3u8", "#EXTM3U\n".toByteArray(), "application/vnd.apple.mpegurl")
            relay.put("k1-0", byteArrayOf(1, 2, 3), "application/octet-stream")

            val conn = URL("$base/v1.m3u8").openConnection() as HttpURLConnection
            // Kodi accoda gli header all'URL con "|": qui arrivano solo come header veri, e la query si ignora
            conn.setRequestProperty("Referer", "https://vixcloud.cc/")
            assertEquals(200, conn.responseCode)
            assertEquals("application/vnd.apple.mpegurl", conn.contentType)
            assertEquals("#EXTM3U\n", conn.inputStream.use { String(it.readBytes()) })
            assertEquals(listOf<Byte>(1, 2, 3), URL("$base/k1-0?x=1").readBytes().toList())

            // HEAD (lo Stat di Kodi): stessa risposta senza corpo
            val head = URL("$base/k1-0").openConnection() as HttpURLConnection
            head.requestMethod = "HEAD"
            assertEquals(200, head.responseCode)
            assertEquals(3, head.contentLength)

            // Restano solo gli ultimi due: il primo se ne va
            relay.put("v2.m3u8", "#EXTM3U\n".toByteArray(), "application/vnd.apple.mpegurl")
            val gone = URL("$base/v1.m3u8").openConnection() as HttpURLConnection
            assertEquals(404, gone.responseCode)
            assertEquals(relay.port(), relay.port())
        } finally {
            relay.close()
        }
    }

    @Test
    fun playlistDalTelefono() {
        val media = """#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:4
#EXT-X-KEY:METHOD=AES-128,URI="https://vixcloud.cc/storage/enc.key",IV=0x01
#EXTINF:4.000,
https://au-u1-01.vix-content.net/hls/1/video/1080p/0000-0500.ts?token=a
#EXTINF:4.000,
0001.ts
#EXT-X-ENDLIST
"""
        val keys = mutableListOf<String>()
        val out = relayPlaylist(media, "https://vixcloud.cc/playlist/1?type=video&rendition=1080p") { keys += it; "http://192.168.1.5:4000/k-0" }
        assertEquals(listOf("https://vixcloud.cc/storage/enc.key"), keys)
        assertEquals(
            """#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:4
#EXT-X-KEY:METHOD=AES-128,URI="http://192.168.1.5:4000/k-0",IV=0x01
#EXTINF:4.000,
https://au-u1-01.vix-content.net/hls/1/video/1080p/0000-0500.ts?token=a
#EXTINF:4.000,
https://vixcloud.cc/playlist/0001.ts
#EXT-X-ENDLIST
""",
            out
        )
        // chiave relativa e init segment fMP4 (#EXT-X-MAP) resi assoluti
        assertEquals(
            "#EXTM3U\n#EXT-X-MAP:URI=\"https://cdn.x.com/a/init.mp4\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"L\"\n#EXTINF:4,\nhttps://cdn.x.com/a/1.m4s",
            relayPlaylist("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.key\"\n#EXTINF:4,\n1.m4s", "https://cdn.x.com/a/v.m3u8") {
                assertEquals("https://cdn.x.com/a/k.key", it); "L"
            }
        )
        // una master o una pagina d'errore: niente passaggio dal telefono
        assertNull(relayPlaylist("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8\n", "https://x.com/m.m3u8") { it })
        assertNull(relayPlaylist("<html>403</html>", "https://x.com/m.m3u8") { it })
    }

    @Test
    fun origine() {
        assertEquals("https://v.vidxgo.co", originOf("https://v.vidxgo.co/embed/1?x=2"))
        assertEquals("http://192.168.1.5:8080", originOf("http://192.168.1.5:8080/a"))
        assertNull(originOf("about:blank"))
        assertNull(originOf("non è un url"))
    }
}
