package it.franc.casttokodi

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.Locale

// Due lingue: la scelta (automatica o dalle Impostazioni), i testi in inglese e il controllo che nel codice nessun
// testo per l'utente resti solo in italiano
class I18nTest {
    @After
    fun italiano() {
        Lang.english = false
    }

    @Test
    fun sceltaDellaLingua() {
        assertFalse(Lang.isEnglish(Lang.AUTO, Locale.ITALY))
        assertFalse(Lang.isEnglish(null, Locale("it", "CH")))
        assertTrue(Lang.isEnglish(Lang.AUTO, Locale.US))
        assertTrue(Lang.isEnglish(Lang.AUTO, Locale.GERMANY))
        assertTrue(Lang.isEnglish(Lang.ENGLISH, Locale.ITALY))
        assertFalse(Lang.isEnglish(Lang.ITALIAN, Locale.US))
        Lang.choose(Lang.ENGLISH)
        assertEquals("Send", tr("Manda", "Send"))
        Lang.choose(Lang.ITALIAN)
        assertEquals("Manda", tr("Manda", "Send"))
    }

    @Test
    fun testiInInglese() {
        Lang.english = true
        val utc = ZoneId.of("UTC")
        val state = KodiState(playerId = 1, fromQueue = true, position = 0, remaining = 1800L, episode = 1800L, queueLength = 3)
        assertEquals("Starts at 21:30", queueNote(state, 1, null, 21 * 3600L, utc).text)
        assertEquals("Paused · 1:00 of 24:00", miniPlayerStatus(true, 60, 24 * 60))
        assertEquals("English", trackLabel("eng", "", 1))
        assertEquals("with Italian and English subtitles",
            subtitlesNote(listOf(SubtitleTrack("ita", "", "", false), SubtitleTrack("eng", "", "", false))))
        assertEquals("Switch off the TV", TimerAction.TV_OFF.label)
        assertEquals("1.5 MB", sizeLabel(1_500_000))
        Lang.english = false
        assertEquals("con sottotitoli in italiano e inglese",
            subtitlesNote(listOf(SubtitleTrack("ita", "", "", false), SubtitleTrack("eng", "", "", false))))
        assertEquals("1,5 MB", sizeLabel(1_500_000))
    }

    // Ogni testo che sembra italiano sta in tr("…", "…"), con l'inglese diverso dall'italiano. Fuori apposta: le righe
    // con "// non tradurre" e i pezzi tra "// non tradurre: inizio" e "// non tradurre: fine" (nomi di cartelle, testi
    // per chi legge il codice, nomi presi dai siti); i Regex e le stringhe """…""" (codice JavaScript) non contano.
    @Test
    fun nessunTestoSoloInItaliano() {
        val dir = listOf("src/main/java/it/franc/casttokodi", "app/src/main/java/it/franc/casttokodi")
            .map(::File).first { it.isDirectory }
        val problems = mutableListOf<String>()
        dir.walk().filter { it.extension == "kt" }.forEach { file ->
            val source = file.readText()
            val lines = source.lines()
            var skipping = false
            val skipped = lines.map { line ->
                if (line.contains("// non tradurre: inizio")) skipping = true
                val skip = skipping || line.contains("// non tradurre")
                if (line.contains("// non tradurre: fine")) skipping = false
                skip
            }
            val literals = stringLiterals(source)
            var firstArgEnd = -1
            literals.forEachIndexed { i, literal ->
                val before = source.substring(0, literal.start).trimEnd()
                val second = firstArgEnd >= 0 && source.substring(firstArgEnd, literal.start).trim() == ","
                val first = before.endsWith("tr(")
                if (second) {
                    val italian = literals[i - 1].text
                    if (italian == literal.text) problems += "${file.name}:${literal.line}: inglese uguale all'italiano ${literal.text}"
                }
                firstArgEnd = if (first) literal.end else -1
                if (first || second || before.endsWith("Regex(") || skipped[literal.line - 1]) return@forEachIndexed
                if (ITALIAN.containsMatchIn(literal.text.replace(TEMPLATE, ""))) problems += "${file.name}:${literal.line}: ${literal.text}"
            }
        }
        assertTrue("testi solo in italiano (mettili in tr(\"…\", \"…\")):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private class Literal(val start: Int, val end: Int, val line: Int, val text: String)

    // Le stringhe "…" del sorgente, fuori dai commenti e dalle """…""", con i ${…} dentro (che possono contenere altre stringhe)
    private fun stringLiterals(source: String): List<Literal> {
        val found = mutableListOf<Literal>()
        var i = 0
        var line = 1
        fun skipString(from: Int): Int {
            var j = from + 1
            while (source[j] != '"') {
                when {
                    source[j] == '\\' -> j += 2
                    source.startsWith("\${", j) -> {
                        var depth = 1
                        j += 2
                        while (depth > 0) {
                            when (source[j]) {
                                '{' -> depth++
                                '}' -> depth--
                                '"' -> j = skipString(j)
                            }
                            j++
                        }
                    }
                    else -> j++
                }
            }
            return j
        }
        while (i < source.length) {
            when {
                source[i] == '\n' -> { line++; i++ }
                source.startsWith("//", i) -> i = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i)
                    line += source.substring(i, end).count { it == '\n' }
                    i = end + 2
                }
                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3)
                    line += source.substring(i, end).count { it == '\n' }
                    i = end + 3
                }
                source[i] == '\'' -> {
                    var j = i + 1
                    while (source[j] != '\'') j += if (source[j] == '\\') 2 else 1
                    i = j + 1
                }
                source[i] == '"' -> {
                    val end = skipString(i)
                    found += Literal(i, end + 1, line, source.substring(i, end + 1))
                    i = end + 1
                }
                else -> i++
            }
        }
        return found
    }

    private companion object {
        val TEMPLATE = Regex("""\$\{[^}]*}""")
        val ITALIAN = Regex(
            "[àèéìòù]|\\b(il|lo|la|le|gli|di|del|della|dei|delle|da|dal|dalla|nel|nella|con|per|sul|sulla|non|che|una|alle|" +
                "alla|ai|questo|questa|ancora|niente|nessun|nessuna|tocca|premi|apri|scegli|episodio|episodi|serie|coda|" +
                "sottotitoli|spento|acceso|annulla|chiudi|salva|togli|impostazioni|preferiti|telecomando)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}
