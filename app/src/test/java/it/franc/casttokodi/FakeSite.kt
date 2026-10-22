package it.franc.casttokodi

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

// Un sito di streaming finto, come quelli col player VixCloud: playlist principale con due qualità, playlist dei
// pezzi con chiave AES e i pezzi del video. Conta le richieste e si ricorda gli header ricevuti.
// oneShot: la playlist principale vale una volta sola, poi 403 (come vidxgo su StreamingCommunity, 2026-10-21).
class FakeSite(private val oneShot: Boolean = false, private val broken: Boolean = false) : AutoCloseable {
    val hits = mutableMapOf<String, Int>()
    val referers = mutableMapOf<String, String?>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            exchange.use {
                val path = it.requestURI.path
                val count = synchronized(hits) {
                    referers[path] = it.requestHeaders.getFirst("Referer")
                    hits.merge(path, 1, Int::plus)!!
                }
                val body = when {
                    broken -> null
                    path == "/master.m3u8" && oneShot && count > 1 -> null
                    else -> files[path]
                }
                if (body == null) {
                    it.sendResponseHeaders(if (broken) 500 else 403, -1)
                } else {
                    it.sendResponseHeaders(200, body.size.toLong())
                    it.responseBody.write(body)
                }
            }
        }
        start()
    }

    val base = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)

    fun hits(path: String) = synchronized(hits) { hits[path] ?: 0 }

    companion object {
        val KEY = ByteArray(16) { it.toByte() }

        val MASTER = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
480p.m3u8?token=x
#EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
1080p.m3u8?token=x
"""

        fun media(quality: String) = """#EXTM3U
#EXT-X-TARGETDURATION:4
#EXT-X-KEY:METHOD=AES-128,URI="/storage/enc.key",IV=0x01
#EXTINF:4.000,
$quality/0000.ts
#EXTINF:4.000,
$quality/0001.ts
#EXT-X-ENDLIST
"""

        private val files = mapOf(
            "/master.m3u8" to MASTER.toByteArray(),
            "/480p.m3u8" to media("480p").toByteArray(),
            "/1080p.m3u8" to media("1080p").toByteArray(),
            "/storage/enc.key" to KEY,
            "/1080p/0000.ts" to "pezzo 0".toByteArray(),
            "/1080p/0001.ts" to "pezzo 1".toByteArray(),
        )
    }
}
