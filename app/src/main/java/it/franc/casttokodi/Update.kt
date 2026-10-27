package it.franc.casttokodi

import org.json.JSONObject

// Aggiornamento con un tocco (2026-10-27, mockup approvato da Pippo): l'avviso c'era già, ma "Scarica" apriva GitHub
// nel browser. Ora l'app legge da releases/latest le novità (il testo della release, cioè la sezione del CHANGELOG) e
// l'APK, lo scarica e lo passa all'installatore di Android. Niente android.*: lo prova UpdateTest.

// Nome dell'APK nelle release: resta quello vecchio, così Obtainium e i link continuano a funzionare
const val APK_NAME = "CastToKodi.apk"

// Dove l'installatore di Android manda l'esito (MainActivity.installStatus)
const val ACTION_INSTALL = "it.franc.casttokodi.INSTALL"

// tag: "v2.3.0"; notes: il testo della release; apk: indirizzo e peso dell'APK (null se la release non lo ha ancora)
class Release(val tag: String, val notes: String, val apkUrl: String?, val apkSize: Long)

fun parseRelease(json: String): Release {
    val o = JSONObject(json)
    val assets = o.optJSONArray("assets")
    val apk = assets?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name") == APK_NAME } }
    return Release(
        tag = o.optString("tag_name"),
        notes = o.optString("body"),
        apkUrl = apk?.optString("browser_download_url")?.ifEmpty { null },
        apkSize = apk?.optLong("size") ?: 0,
    )
}

// Le novità da mostrare nel pannello: il Markdown del CHANGELOG senza i segni (titoli, grassetti, codice, link) e con i
// punti al posto dei trattini. Al massimo max righe, per non riempire lo schermo
fun releaseNotes(markdown: String, max: Int = 12): String {
    val lines = markdown.replace("\r\n", "\n").lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .map { line ->
            val bullet = Regex("^\\s*[-*]\\s+").find(line)
            val text = (if (bullet != null) "• " + line.substring(bullet.range.last + 1) else line.trim())
            text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .replace(Regex("(?<![*\\w])\\*([^*]+)\\*(?![*\\w])"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
                .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        }
    if (lines.size <= max) return lines.joinToString("\n")
    return (lines.take(max) + "…").joinToString("\n")
}

// "12,4 MB" accanto al pulsante e durante lo scaricamento
fun sizeLabel(bytes: Long): String = when {
    bytes < 1_000_000 -> "${(bytes + 500) / 1000} kB"
    else -> "%.1f MB".format(Lang.locale, bytes / 1_000_000.0)
}

// "Scarico… 3,1 MB di 12,4 MB"
fun downloadLabel(done: Long, total: Long): String =
    if (total > 0) tr("Scarico… ${sizeLabel(done)} di ${sizeLabel(total)}", "Downloading… ${sizeLabel(done)} of ${sizeLabel(total)}") else tr("Scarico… ${sizeLabel(done)}", "Downloading… ${sizeLabel(done)}")
