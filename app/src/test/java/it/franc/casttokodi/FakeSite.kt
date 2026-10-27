package it.franc.casttokodi

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Un sito di streaming finto, come quelli col player VixCloud: playlist principale con due qualità, playlist dei
// pezzi con chiave AES e i pezzi del video. Conta le richieste e si ricorda gli header ricevuti.
// oneShot: la playlist principale vale una volta sola, poi 403 (come vidxgo su StreamingCommunity, 2026-10-21).
class FakeSite(private val oneShot: Boolean = false, private val broken: Boolean = false) : AutoCloseable {
    val hits = mutableMapOf<String, Int>()
    val referers = mutableMapOf<String, String?>()

    private val server = TinyHttp {
        val path = it.path
        val count = synchronized(hits) {
            referers[path] = it.header("Referer")
            hits.merge(path, 1, Int::plus)!!
        }
        val body = when {
            broken -> null
            path == "/master.m3u8" && oneShot && count > 1 -> null
            else -> files[path]
        }
        if (body == null) TinyHttp.Response(if (broken) 500 else 403) else TinyHttp.Response(200, body)
    }

    val base = "http://127.0.0.1:${server.port}"

    override fun close() = server.close()

    fun hits(path: String) = synchronized(hits) { hits[path] ?: 0 }

    companion object {
        val KEY = ByteArray(16) { it.toByte() }

        val MASTER = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
480p.m3u8?token=x
#EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
1080p.m3u8?token=x
"""

        // La stessa master con due tracce di sottotitoli: l'italiano in due pezzi, l'inglese "Forced"
        val MASTER_SUBS = """#EXTM3U
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="s",NAME="Italiano",LANGUAGE="ita",URI="subs/ita.m3u8"
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="s",NAME="English",LANGUAGE="eng",FORCED=YES,URI="subs/eng.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480,SUBTITLES="s"
480p.m3u8?token=x
#EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,SUBTITLES="s"
1080p.m3u8?token=x
"""

        fun subs(vararg parts: String) = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n" + parts.joinToString("") { "#EXTINF:4.000,\n$it\n" } + "#EXT-X-ENDLIST\n"

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
            "/master-subs.m3u8" to MASTER_SUBS.toByteArray(),
            "/subs/ita.m3u8" to subs("ita/0.vtt", "ita/1.vtt").toByteArray(),
            "/subs/ita/0.vtt" to "WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:900000,LOCAL:00:00:00.000\n\n00:00:01.000 --> 00:00:02.000\nCiao\n".toByteArray(),
            "/subs/ita/1.vtt" to "WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:900000,LOCAL:00:00:00.000\n\n00:00:05.000 --> 00:00:06.000\nArrivederci\n".toByteArray(),
            "/subs/eng.m3u8" to subs("eng/0.vtt").toByteArray(),
            "/subs/eng/0.vtt" to "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n[SIGN]\n".toByteArray(),
            // Un .srt caricato dalla pagina, a parte
            "/page/episodio_ita.srt" to "1\r\n00:00:01,000 --> 00:00:02,500\r\nDal sito\r\n".toByteArray(),
            "/480p.m3u8" to media("480p").toByteArray(),
            "/1080p.m3u8" to media("1080p").toByteArray(),
            "/storage/enc.key" to KEY,
            "/1080p/0000.ts" to "pezzo 0".toByteArray(),
            "/1080p/0001.ts" to "pezzo 1".toByteArray(),
            // La qualità leggera è cifrata davvero, con la chiave e l'IV della playlist: la apre l'anteprima
            "/480p/0000.ts" to encrypt("anteprima 0"),
            "/480p/0001.ts" to encrypt("anteprima 1"),
        )

        private fun encrypt(text: String): ByteArray {
            val iv = ByteArray(16).also { it[15] = 1 }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(text.toByteArray())
        }
    }
}
