package it.franc.casttokodi

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

// I servizi veri che l'app interroga, letti con le stesse funzioni dell'app: se uno cambia il formato della risposta
// qui si vede subito, invece di scoprirlo sul telefono. Gli altri test usano risposte scritte da noi, che non cambiano.
// Girano solo con SERVIZI_VERI=1: li lancia una volta a settimana il workflow "Servizi esterni" (servizi.yml), non le
// PR, perché un servizio giù per un'ora non deve bloccare il lavoro. Esempio: Fire Force (MyAnimeList 38671), episodio
// 1, letto da Pippo il 2026-10-28 (sigla 386–476 s, titoli di coda 1360–1437 s).
class ServiziVeriTest {

    private val fireForce = 38671

    @Before
    fun soloSeRichiesto() = assumeTrue("Solo con SERVIZI_VERI=1", System.getenv("SERVIZI_VERI") == "1")

    private fun get(url: String, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        // Gli stessi header di AniSkipClient.request: se un servizio comincia a chiederne altri, qui si vede
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            assertEquals("$url ha risposto ${conn.responseCode}", 200, conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun aniSkipDaIlTempiDellaSigla() {
        val parts = parseAniSkip(get(aniSkipUrl(fireForce, 1)))
        assertNotNull("La risposta di AniSkip non si legge più", parts)
        val intro = parts!!.firstOrNull { it.type == PartType.INTRO }
        assertNotNull("AniSkip non dà più la sigla di Fire Force 1: $parts", intro)
        assertTrue("Sigla in un punto strano: $intro", intro!!.from in 300.0..450.0 && intro.to > intro.from)
        assertTrue("Durata dell'episodio mancante: $intro", intro.length > 1000)
    }

    @Test
    fun aniListTrovaLaSeriePerNome() {
        val found = parseAniList(get(ANILIST_API, aniListSearchBody("Fire Force")))
        assertTrue("AniList non trova più Fire Force con il suo id MyAnimeList: $found", found.any { it.malId == fireForce })
        val match = found.first { it.malId == fireForce }
        assertTrue("Mancano episodi o anno: $match", (match.episodes ?: 0) > 0 && (match.year ?: 0) > 2000)
    }

    // Jikan da GitHub spesso non risponde (2026-11-09: due run su due in timeout, gli altri servizi sì). L'app in quel
    // caso usa la copertina di AniList, quindi qui un timeout salta la prova (si vede nel resoconto) invece di farla
    // fallire; fallisce invece se Jikan risponde con un formato che l'app non legge più. Che la copertina arrivi
    // comunque lo dice copertinaDaMyAnimeList
    @Test
    fun jikanDaLaCopertina() {
        val text = try {
            get(jikanAnimeUrl(fireForce))
        } catch (e: IOException) {
            assumeTrue("Jikan non risponde da GitHub ($e): l'app usa AniList", false)
            return
        }
        val cover = parseJikanCover(text)
        assertTrue("Jikan non dà più la copertina: $cover", cover?.startsWith("https://") == true)
    }

    // Il giro intero dell'app (Jikan, se no AniList), come lo fa coverFromMal
    @Test
    fun copertinaDaMyAnimeList() {
        val cover = AniSkipClient.malCover(fireForce)
        assertTrue("Né Jikan né AniList danno la copertina: $cover", cover?.startsWith("https://") == true)
    }

    @Test
    fun aniListDaLaCopertina() {
        val cover = parseAniListCover(get(ANILIST_API, aniListCoverBody(fireForce)))
        assertTrue("AniList non dà più la copertina: $cover", cover?.startsWith("https://") == true)
    }

    @Test
    fun githubDaLUltimaVersioneConLApk() {
        val release = parseRelease(get("https://api.github.com/repos/Fpippi/cast-to-kodi/releases/latest"))
        assertTrue("Versione strana: ${release.tag}", Regex("v\\d+\\.\\d+\\.\\d+").matches(release.tag))
        assertTrue("La release non ha $APK_NAME", release.apkUrl?.startsWith("https://") == true && release.apkSize > 0)
        assertTrue("La release non ha le novità", release.notes.isNotBlank())
    }
}
