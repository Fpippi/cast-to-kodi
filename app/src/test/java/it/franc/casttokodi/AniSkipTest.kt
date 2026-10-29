package it.franc.casttokodi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Salta sigla con i tempi di AniSkip (#150): risposta, scelta del punto, serie su MyAnimeList, rete contro un finto
class AniSkipTest {

    // La risposta vera di AniSkip per Fire Force episodio 1 (letta da Pippo il 2026-10-28)
    private val fireForce1 = """{"found":true,"results":[{"interval":{"startTime":386.418,"endTime":476.418},"skipType":"op",""" +
        """"skipId":"5fe2718a-082d-436b-b705-32d22d256bd4","episodeLength":1437},{"interval":{"startTime":1359.767,"endTime":1437},""" +
        """"skipType":"ed","skipId":"00649630-d2d4-48e9-adcb-3b4328cdabff","episodeLength":1437.0189}],""" +
        """"message":"Successfully found skip times","statusCode":200}"""

    private val parts = parseAniSkip(fireForce1)!!

    @Test
    fun rispostaDiAniSkip() {
        assertEquals(2, parts.size)
        assertEquals(SkipPart(PartType.INTRO, 386.418, 476.418, 1437.0), parts[0])
        assertEquals(PartType.CREDITS, parts[1].type)
        assertEquals(emptyList<SkipPart>(), parseAniSkip("""{"found":false,"results":[],"message":"No skip times found","statusCode":404}"""))
        assertNull(parseAniSkip("<html>"))
        val mixed = parseAniSkip("""{"found":true,"results":[{"interval":{"startTime":0,"endTime":40},"skipType":"recap","episodeLength":1400},""" +
            """{"interval":{"startTime":60,"endTime":150},"skipType":"mixed-op","episodeLength":1400}]}""")!!
        assertEquals(listOf(PartType.RECAP, PartType.INTRO), mixed.map { it.type })
    }

    @Test
    fun indirizzo() {
        assertEquals(
            "https://api.aniskip.com/v2/skip-times/38671/1?types%5B%5D=op&types%5B%5D=ed&types%5B%5D=mixed-op&types%5B%5D=mixed-ed&types%5B%5D=recap&episodeLength=0",
            aniSkipUrl(38671, 1),
        )
    }

    @Test
    fun soloDentroISegmenti() {
        // Dentro la sigla, da 5 secondi prima: alla sua fine (7:57)
        for (time in listOf(382L, 386L, 400L, 470L)) assertEquals(SkipPress.To(PartType.INTRO, 477), skipPress(parts, time, 1437, 85))
        // Prima della sigla il tasto è spento: un tocco a 2:00 non fa perdere i minuti prima della sigla (Pippo, 2026-10-29)
        assertEquals(SkipPress.Off(parts[0]), skipPress(parts, 120, 1437, 85))
        assertEquals(SkipPress.Off(parts[0]), skipPress(parts, 0, 1437, null))
        // Tra sigla e titoli: spento, il prossimo sono i titoli
        assertEquals(SkipPress.Off(parts[1]), skipPress(parts, 600, 1437, 85))
        // Titoli di coda: alla fine dell'episodio (l'ultimo secondo, così Kodi passa al video dopo)
        assertEquals(SkipPress.To(PartType.CREDITS, 1436), skipPress(parts, 1356, 1437, 85))
        assertEquals(SkipPress.To(PartType.CREDITS, 1436), skipPress(parts, 1400, 1437, 85))
        assertEquals(SkipPress.Off(parts[1]), skipPress(parts, 1340, 1437, 85))
        assertEquals("Salta sigla", skipLabel(skipPress(parts, 400, 1437, 85)))
        assertEquals("Salta titoli di coda", skipLabel(skipPress(parts, 1400, 1437, 85)))
        assertEquals("Salta sigla", skipLabel(skipPress(parts, 120, 1437, 85)))
        assertEquals("Porta a 7:57", skipHint(skipPress(parts, 400, 1437, 85)))
        assertEquals("La sigla parte alle 6:26 · tieni premuto", skipHint(skipPress(parts, 120, 1437, 85)))
        assertEquals("I titoli di coda partono alle 22:39 · tieni premuto", skipHint(skipPress(parts, 600, 1437, 85)))
    }

    @Test
    fun manuale() {
        // In manuale i tempi di AniSkip non contano: il salto fisso, sempre acceso
        assertEquals(SkipPress.Forward(85), skipPress(parts, 120, 1437, 85, auto = false))
        assertEquals(SkipPress.Choose, skipPress(parts, 400, 1437, null, auto = false))
        assertEquals("Salta sigla (+1:25)", skipLabel(skipPress(parts, 120, 1437, 85, auto = false)))
        assertEquals("", skipHint(skipPress(parts, 120, 1437, 85, auto = false)))
        assertEquals("Salta sigla", skipShortLabel(skipPress(parts, 120, 1437, 85, auto = false)))
        // Automatico senza tempi: come il manuale
        assertEquals(SkipPress.Forward(85), skipPress(emptyList(), 120, 1437, 85))
    }

    @Test
    fun tratti() {
        // La barra del telecomando: frazioni del video
        val marks = partMarks(parts, 1437, auto = true)
        assertEquals(2, marks.size)
        assertEquals(386.418f / 1437, marks[0].first, 0.0001f)
        assertEquals(1f, marks[1].second, 0.0001f)
        assertEquals(emptyList<Pair<Float, Float>>(), partMarks(parts, 1437, auto = false))
        assertEquals(emptyList<Pair<Float, Float>>(), partMarks(parts, 1500, auto = true))
    }

    @Test
    fun riassuntoPrimaDellaSigla() {
        val withRecap = parts + SkipPart(PartType.RECAP, 0.0, 30.0, 1437.0)
        assertEquals(SkipPress.To(PartType.RECAP, 30), skipPress(withRecap, 10, 1437, null))
        assertEquals("Salta riassunto", skipLabel(skipPress(withRecap, 10, 1437, null)))
        // Dopo il riassunto non porta alla fine della sigla
        assertEquals(SkipPress.Off(parts[0]), skipPress(withRecap, 30, 1437, null))
    }

    @Test
    fun altraVersioneDelVideo() {
        // Il sito ha un video più lungo di 30 secondi (un logo all'inizio): i tempi non valgono, salto fisso
        assertEquals(SkipPress.Forward(85), skipPress(parts, 400, 1467, 85))
        // Qualche secondo di differenza va bene
        assertEquals(SkipPress.To(PartType.INTRO, 477), skipPress(parts, 400, 1442, 85))
        // In diretta (senza durata) nessun tempo
        assertEquals(SkipPress.Forward(85), skipPress(parts, 0, 0, 85))
    }

    @Test
    fun numeroDellEpisodio() {
        // Parte 2 numerata dal sito da 13, con 12 episodi: per AniSkip il 13 è l'1
        assertEquals(12, aniSkipShift(first = 13, number = 13, count = 12))
        // Serie lunga che il sito elenca a pezzi (dal 121): i numeri sono già quelli giusti
        assertEquals(0, aniSkipShift(first = 121, number = 130, count = 500))
        assertEquals(0, aniSkipShift(first = 1, number = 5, count = 12))
        assertEquals(0, aniSkipShift(first = null, number = 13, count = 12))
    }

    @Test
    fun parteDue() {
        // Sito: "Fire Force 3 part 2" con 12 episodi da 1; MyAnimeList: la stagione 3 intera, 24 episodi. L'1 è il 13
        assertEquals(-12, suggestShift("Fire Force 3 Part 2", first = 1, count = 12, malEpisodes = 24))
        assertEquals(-12, suggestShift("Fire Force Stagione 3 Parte 2", first = 1, count = 12, malEpisodes = 24))
        // La parte 1 sulla stessa stagione: niente
        assertEquals(0, suggestShift("Fire Force 3 Part 1", first = 1, count = 12, malEpisodes = 24))
        // Il sito continua da 13 e la stagione su MyAnimeList è intera: i numeri vanno già bene
        assertEquals(0, suggestShift("Fire Force 3 Part 2", first = 13, count = 12, malEpisodes = 24))
        // Il sito continua da 13 e MyAnimeList ha la parte a sé (12 episodi): si conta dal primo
        assertEquals(12, suggestShift("Fire Force 3 Part 2", first = 13, count = 12, malEpisodes = 12))
        // Episodi su MyAnimeList non noti: come prima
        assertEquals(0, suggestShift("Fire Force", first = 1, count = 12, malEpisodes = null))
        assertTrue(isSecondPart("Fire Force 3 part 2"))
        assertTrue(!isSecondPart("Fire Force 3"))
        // Con lo spostamento -12 l'episodio 1 del sito si chiede ad AniSkip come 13
        val part2 = withShift(listOf(show.copy(episodeShift = 0, episodes = listOf(Episode(null, 1, "/p2/1", file, seen = 1)))), show.key, -12)
        assertEquals(38671 to 13, aniSkipRequest(part2, file, now = 1000))
        assertEquals("L'episodio 1 del sito è il 13 su MyAnimeList", shiftLabel(1, -12))
        // Spostamento cambiato: i tempi già presi si richiedono
        val found = withSkips(part2, file, parts, now = 1000)
        assertNull(withShift(found, show.key, 0).single().episodes.single().skips)
        assertEquals(parts, withShift(found, show.key, -12).single().episodes.single().skips)
    }

    private val search = """{"data":{"Page":{"media":[
        {"idMal":38671,"title":{"romaji":"Enen no Shouboutai","english":"Fire Force"},"episodes":24,"seasonYear":2019,"coverImage":{"medium":"https://img.example/1.jpg"}},
        {"idMal":40956,"title":{"romaji":"Enen no Shouboutai: Ni no Shou","english":"Fire Force Season 2"},"episodes":24,"seasonYear":2020,"coverImage":{"medium":"https://img.example/2.jpg"}},
        {"idMal":null,"title":{"romaji":"Senza MAL","english":null},"episodes":1,"seasonYear":2024,"coverImage":{"medium":null}}
    ]}}}"""

    @Test
    fun ricercaSuAniList() {
        val found = parseAniList(search)
        assertEquals(listOf(38671, 40956), found.map { it.malId })
        assertEquals(AnimeMatch(38671, "Fire Force", "Enen no Shouboutai", 2019, 24, "https://img.example/1.jpg"), found[0])
        // Solo se è certa: stesso titolo (scritto in un altro modo) e stessi episodi
        assertEquals(38671, sureMatch(found, "fire force", 24)?.malId)
        assertEquals(40956, sureMatch(found, "Fire Force: Season 2", null)?.malId)
        assertEquals(38671, sureMatch(found, "Enen no Shouboutai", null)?.malId)
        assertNull(sureMatch(found, "Fire Force", 12))
        assertNull(sureMatch(found, "Fire", null))
        assertEquals(40956, parseAniList("""{"data":{"Media":{"idMal":40956,"title":{"romaji":"Enen","english":null}}}}""").single().malId)
        assertEquals(emptyList<AnimeMatch>(), parseAniList("""{"errors":[{"message":"Not Found."}],"data":{"Media":null}}"""))
        val body = JSONObject(aniListSearchBody("Fire Force"))
        assertEquals("Fire Force", body.getJSONObject("variables").getString("s"))
        assertTrue(body.getString("query").contains("idMal"))
    }

    private val file = "https://cdn.example/1.m3u8|Referer=x"

    private val show = Show(
        "animeunity:/anime/2-fire-force", "animeunity", "www.animeunity.to", "Fire Force",
        listOf(Episode(null, 13, "/anime/2-fire-force/13", file, seen = 1)),
        malId = 38671, malTitle = "Fire Force", episodeShift = 12,
    )

    @Test
    fun quandoChiedere() {
        // Il numero per AniSkip tolto lo spostamento della parte
        assertEquals(38671 to 1, aniSkipRequest(listOf(show), file, now = 1000))
        val failed = withSkips(listOf(show), file, null, now = 1000)
        assertNull(aniSkipRequest(failed, file, now = 1000 + 60_000))
        assertNotNull(aniSkipRequest(failed, file, now = 1000 + 11 * 60_000))
        val missing = withSkips(listOf(show), file, emptyList(), now = 1000)
        assertNull(aniSkipRequest(missing, file, now = 1000 + 24 * 3600_000L))
        assertNotNull(aniSkipRequest(missing, file, now = 1000 + 4 * 24 * 3600_000L))
        val found = withSkips(listOf(show), file, parts, now = 1000)
        assertNull(aniSkipRequest(found, file, now = Long.MAX_VALUE))
        assertEquals(parts, skipPartsOf(found, "https://cdn.example/1.m3u8"))
        // Una risposta mancata non cancella i tempi già presi
        assertEquals(parts, skipPartsOf(withSkips(found, file, null, now = 5000), file))
        // Senza id MyAnimeList, o con "Nessuna", non si chiede
        assertNull(aniSkipRequest(listOf(show.copy(malId = null)), file, now = 1000))
        assertNull(aniSkipRequest(listOf(show.copy(malId = NO_MAL)), file, now = 1000))
        assertNull(aniSkipRequest(listOf(show), "https://altro.example/x.mp4", now = 1000))
    }

    @Test
    fun serieCambiataETempiSalvati() {
        val found = withSkips(listOf(show), file, parts, now = 1000)
        // Stessa serie: i tempi restano; serie diversa: si richiedono
        assertEquals(parts, withMal(found, show.key, 38671, "Fire Force").single().episodes.single().skips)
        assertNull(withMal(found, show.key, 40956, "Fire Force Season 2").single().episodes.single().skips)
        // Salvati e riletti come le altre impostazioni della serie
        val back = showsFromJson(showsToJson(found)).single()
        assertEquals(38671, back.malId)
        assertEquals("Fire Force", back.malTitle)
        assertEquals(12, back.episodeShift)
        assertEquals(parts, back.episodes.single().skips)
        assertEquals(1000, back.episodes.single().skipsAt)
        assertNull(showsFromJson(showsToJson(listOf(show.copy(malId = null)))).single().malId)
        val manual = showsFromJson(showsToJson(withSiteFirst(withSkipAuto(found, show.key, false), show.key, 13))).single()
        assertEquals(false, manual.skipAuto)
        assertEquals(13, manual.siteFirst)
        assertNull(back.skipAuto)
        // Un nuovo invio dello stesso episodio non perde serie e tempi
        val page = EpisodePage(show.key, "animeunity", "Fire Force", null, 13, "/anime/2-fire-force/13")
        val sent = withSent(found, page, "www.animeunity.to", file, now = 2000, playing = true).single()
        assertEquals(38671, sent.malId)
        assertEquals(false, withSent(withSkipAuto(found, show.key, false), page, "www.animeunity.to", file, now = 2000, playing = true).single().skipAuto)
        assertEquals(parts, sent.episodes.single().skips)
    }

    @Test
    fun pressioneDelTastoConLaSerie() {
        val found = withSkips(listOf(show.copy(intro = 80)), file, parts, now = 1000)
        assertEquals(SkipPress.To(PartType.INTRO, 477), skipPressOf(found, file, 400, 1437, null))
        assertEquals(SkipPress.Off(parts[1]), skipPressOf(found, file, 600, 1437, null))
        // Impostazioni in manuale, serie senza scelta: manuale; serie in automatico: automatico lo stesso
        assertEquals(SkipPress.Forward(80), skipPressOf(found, file, 400, 1437, null, autoDefault = false))
        val auto = withSkipAuto(found, show.key, true)
        assertEquals(SkipPress.To(PartType.INTRO, 477), skipPressOf(auto, file, 400, 1437, null, autoDefault = false))
        assertEquals(SkipPress.Forward(80), skipPressOf(withSkipAuto(found, show.key, false), file, 400, 1437, null))
        // In manuale non si chiede niente ad AniSkip
        assertNull(aniSkipRequest(listOf(show.copy(skipAuto = false)), file, now = 1000))
        assertNull(aniSkipRequest(listOf(show), file, now = 1000, autoDefault = false))
        assertNotNull(aniSkipRequest(listOf(show.copy(skipAuto = true)), file, now = 1000, autoDefault = false))
        // Video fuori dalle serie: il tempo generale
        assertEquals(SkipPress.Forward(90), skipPressOf(found, "https://altro.example/x.mp4", 100, 1437, 90))
    }

    @Test
    fun tendinaEWidget() {
        val found = withSkips(listOf(show.copy(intro = 80)), file, parts, now = 1000)
        fun at(time: Long) = NowPlaying(1, "", file, time, 1437, false, emptyList(), -1, emptyList(), -1, false, 50, false)
        // Fuori dai tratti la tendina non ha il tasto (non ce n'è uno spento) e il widget nemmeno
        assertNull(mediaButtons(at(120), null, found, null).intro)
        assertEquals(SkipPress.To(PartType.INTRO, 477), mediaButtons(at(400), null, found, null).intro)
        assertEquals(SkipPress.Forward(80), mediaButtons(at(120), null, found, null, autoDefault = false).intro)
        val widget = widgetContent(true, at(400), found, emptyMap(), "", null, emptyMap(), nowSeconds = 0) as WidgetContent.Playing
        assertEquals("Salta sigla", widget.intro)
        assertNull((widgetContent(true, at(120), found, emptyMap(), "", null, emptyMap(), nowSeconds = 0) as WidgetContent.Playing).intro)
    }

    @Test
    fun aniSkipFinto() {
        TinyHttp { request ->
            when (request.path) {
                "/v2/skip-times/38671/1" -> TinyHttp.Response(200, fireForce1.toByteArray(), "application/json")
                "/v2/skip-times/38671/99" -> TinyHttp.Response(404, """{"found":false,"results":[],"statusCode":404}""".toByteArray(), "application/json")
                "/graphql" -> {
                    val variables = JSONObject(String(request.body)).getJSONObject("variables")
                    if (request.method == "POST" && variables.optString("s") == "Fire Force") TinyHttp.Response(200, search.toByteArray(), "application/json")
                    else TinyHttp.Response(400)
                }
                else -> TinyHttp.Response(500)
            }
        }.use { server ->
            val base = "http://127.0.0.1:${server.port}"
            assertEquals(parts, AniSkipClient.skips(38671, 1, "$base/v2"))
            assertEquals(emptyList<SkipPart>(), AniSkipClient.skips(38671, 99, "$base/v2"))
            assertNull(AniSkipClient.skips(38671, 2, "$base/v2"))
            assertEquals(2, AniSkipClient.search("Fire Force", "$base/graphql")?.size)
            assertNull(AniSkipClient.search("Altro", "$base/graphql"))
        }
        // Nessuno in ascolto: null, non un errore
        assertNull(AniSkipClient.skips(38671, 1, "http://127.0.0.1:1"))
    }
}
