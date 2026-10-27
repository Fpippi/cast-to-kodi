package it.franc.casttokodi

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Backup e ripristino (2026-10-27, mockup approvato da Pippo): un file .zip con couchcast.json (i valori di prefs) e le
// immagini delle serie in serie/. Niente android.*: lo prova BackupTest. L'Activity legge e scrive prefs e i file.

const val BACKUP_FORMAT = 1
const val BACKUP_JSON = "couchcast.json"
const val BACKUP_IMAGES = "serie/"

// Il backup automatico riscrive lo stesso file una volta alla settimana, all'avvio dell'app
const val BACKUP_EVERY = 7L * 24 * 3600 * 1000

// Un ripristino non legge immagini oltre questa dimensione (un backup vero ne ha 12 serie x 7 immagini da ~200 KB)
private const val MAX_IMAGE = 8L * 1024 * 1024

enum class BackupPart(val id: String) { FAVORITES("favorites"), SHOWS("shows"), SETTINGS("settings") }

// Ogni chiave che l'app scrive in prefs sta qui o in NOT_BACKED_UP: BackupTest lo controlla nel codice, così un dato
// nuovo senza posto nel backup fa diventare rosso il controllo "test" della PR
val BACKED_UP: Map<String, BackupPart> = mapOf(
    "favorites" to BackupPart.FAVORITES,
    "shows" to BackupPart.SHOWS,
    "siteHosts" to BackupPart.SHOWS,
    "last" to BackupPart.SHOWS,
    "lastTitle" to BackupPart.SHOWS,
    "lastAt" to BackupPart.SHOWS,
    "host" to BackupPart.SETTINGS,
    "name" to BackupPart.SETTINGS,
    "manual" to BackupPart.SETTINGS,
    "autoNext" to BackupPart.SETTINGS,
    "autoNextMigrated" to BackupPart.SETTINGS,
    "intro" to BackupPart.SETTINGS,
    "history" to BackupPart.SETTINGS,
    "queueTitles" to BackupPart.SETTINGS,
    "adbHost" to BackupPart.SETTINGS,
    "mediaControls" to BackupPart.SETTINGS,
    "timerAction" to BackupPart.SETTINGS,
)

// Fuori dal backup, apposta: le chiavi del Keystore non escono dal telefono, quindi quello che cifrano non si
// decifrerebbe altrove (e il file può finire in giro)
val NOT_BACKED_UP: Map<String, String> = mapOf(
    "logins" to "password di Kodi cifrate con la chiave kodi-login del Keystore",
    "auth" to "utente e password delle versioni vecchie, spostati in logins all'avvio",
    "adbPrivate" to "chiave ADB cifrata con il Keystore: la Fire TV chiede di nuovo Consenti",
    "adbPublic" to "va in coppia con adbPrivate",
    "backupUri" to "il file del backup automatico vale solo su questo telefono",
    "backupAt" to "quando questo telefono ha salvato l'ultimo backup",
    "backupParts" to "le parti del backup automatico di questo telefono",
    "timerAt" to "il timer di spegnimento in corso vale per questa sera, non per un altro telefono",
    "timerToken" to "va con timerAt",
    "timerOnPhone" to "va con timerAt",
)

// Un backup letto: i valori di prefs (String, Int, Long o Boolean), le parti che contiene, le immagini per nome e quanti
// valori sono stati saltati perché di una versione più nuova che questa non conosce
class Backup(
    val version: String,
    val created: Long,
    val parts: Set<BackupPart>,
    val values: Map<String, Any>,
    val images: Map<String, ByteArray>,
    val skipped: Int,
)

// Quello che c'è in un insieme di valori, per il pannello del ripristino
data class BackupCount(val favorites: Int, val shows: Int, val settings: Boolean)

fun backupCount(values: Map<String, Any?>) = BackupCount(
    favorites = runCatching { JSONArray(values["favorites"] as? String ?: "[]").length() }.getOrDefault(0),
    shows = showsFromJson(values["shows"] as? String).size,
    settings = values.keys.any { BACKED_UP[it] == BackupPart.SETTINGS },
)

// Le immagini delle serie che il backup deve portarsi dietro
fun backupImages(values: Map<String, Any?>): Set<String> = usedImages(showsFromJson(values["shows"] as? String))

fun backupJson(values: Map<String, Any?>, parts: Set<BackupPart>, version: String, created: Long): String {
    val kept = JSONObject()
    values.forEach { (key, value) ->
        val part = BACKED_UP[key] ?: return@forEach
        if (part !in parts || value == null) return@forEach
        val typed = when (value) {
            is String -> JSONObject().put("string", value)
            is Int -> JSONObject().put("int", value)
            is Long -> JSONObject().put("long", value)
            is Boolean -> JSONObject().put("boolean", value)
            else -> return@forEach
        }
        kept.put(key, typed)
    }
    return JSONObject()
        .put("format", BACKUP_FORMAT)
        .put("version", version)
        .put("created", created)
        .put("parts", JSONArray(parts.map { it.id }))
        .put("values", kept)
        .toString(2)
}

// images: nome -> contenuto, letto solo quando serve
fun writeBackup(out: OutputStream, json: String, images: Map<String, () -> ByteArray?>) {
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(BACKUP_JSON))
        zip.write(json.toByteArray())
        zip.closeEntry()
        images.forEach { (name, read) ->
            val bytes = read() ?: return@forEach
            zip.putNextEntry(ZipEntry(BACKUP_IMAGES + name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

fun readBackup(input: InputStream): Backup {
    var json: String? = null
    val images = mutableMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            when {
                entry.name == BACKUP_JSON -> json = zip.readBytes().toString(Charsets.UTF_8)
                entry.name.startsWith(BACKUP_IMAGES) && !entry.isDirectory -> {
                    val name = entry.name.removePrefix(BACKUP_IMAGES)
                    // Solo nomi semplici: un "../" scriverebbe fuori dalla cartella delle immagini
                    if (name.isNotEmpty() && '/' !in name && '\\' !in name && !name.startsWith(".")) {
                        readLimited(zip)?.let { images[name] = it }
                    }
                }
            }
        }
    }
    val root = try {
        JSONObject(json ?: throw IOException("Non è un backup di Couch Cast"))
    } catch (e: org.json.JSONException) {
        throw IOException("Il backup è rovinato")
    }
    val parts = root.optJSONArray("parts")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
        .mapNotNull { id -> BackupPart.entries.firstOrNull { it.id == id } }.toSet()
    val values = mutableMapOf<String, Any>()
    var skipped = 0
    val saved = root.optJSONObject("values") ?: JSONObject()
    saved.keys().forEach { key ->
        val typed = saved.optJSONObject(key)
        val value: Any? = when {
            key !in BACKED_UP || typed == null -> null
            typed.has("string") -> typed.optString("string")
            typed.has("int") -> typed.optInt("int")
            typed.has("long") -> typed.optLong("long")
            typed.has("boolean") -> typed.optBoolean("boolean")
            else -> null
        }
        if (value == null) skipped++ else values[key] = value
    }
    val wanted = backupImages(values)
    return Backup(
        version = root.optString("version"),
        created = root.optLong("created"),
        parts = parts,
        values = values,
        images = images.filterKeys { it in wanted },
        skipped = skipped,
    )
}

private fun readLimited(input: InputStream): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > MAX_IMAGE) return null
        out.write(buffer, 0, n)
    }
}

// Cosa scrivere in prefs dopo un ripristino. Le chiavi di write vanno scritte, quelle di remove tolte; le altre restano
class Restore(val write: Map<String, Any>, val remove: Set<String>)

// "Sostituisci tutto": per le parti che il backup contiene, il telefono torna com'era nel backup
fun replaceWith(backup: Backup): Restore = Restore(
    write = backup.values,
    remove = BACKED_UP.filter { (key, part) -> part in backup.parts && key !in backup.values }.keys,
)

// "Unisci": si aggiunge quello che manca. Preferiti per indirizzo; serie per chiave, tenendo quella più avanti;
// l'ultima pagina più recente; per le impostazioni vince il telefono, il backup riempie solo quelle mai scelte
fun mergeWith(local: Map<String, Any?>, backup: Backup): Restore {
    val write = mutableMapOf<String, Any>()
    val theirs = backup.values

    (theirs["favorites"] as? String)?.let { write["favorites"] = mergeFavorites(local["favorites"] as? String, it) }
    (theirs["shows"] as? String)?.let { write["shows"] = showsToJson(mergeShows(showsFromJson(local["shows"] as? String), showsFromJson(it))) }
    (theirs["siteHosts"] as? String)?.let { write["siteHosts"] = mergeObjects(local["siteHosts"] as? String, it) }
    (theirs["queueTitles"] as? String)?.let { write["queueTitles"] = mergeObjects(local["queueTitles"] as? String, it) }

    val localAt = local["lastAt"] as? Long ?: 0L
    val theirAt = theirs["lastAt"] as? Long ?: 0L
    if (theirs["last"] != null && (local["last"] == null || theirAt > localAt)) {
        listOf("last", "lastTitle", "lastAt").forEach { key -> theirs[key]?.let { write[key] = it } }
    }

    BACKED_UP.filterValues { it == BackupPart.SETTINGS }.keys.forEach { key ->
        if (key == "queueTitles") return@forEach
        val value = theirs[key] ?: return@forEach
        if (local[key] == null || local[key] == "") write[key] = value
    }
    return Restore(write, emptySet())
}

private fun mergeFavorites(local: String?, theirs: String): String {
    val mine = runCatching { JSONArray(local ?: "[]") }.getOrDefault(JSONArray())
    val urls = (0 until mine.length()).map { mine.getJSONObject(it).optString("url") }.toMutableSet()
    val other = runCatching { JSONArray(theirs) }.getOrDefault(JSONArray())
    for (i in 0 until other.length()) {
        val favorite = other.optJSONObject(i) ?: continue
        if (urls.add(favorite.optString("url"))) mine.put(favorite)
    }
    return mine.toString()
}

// Oggetti JSON chiave -> testo (dominio di ogni sito, nomi dei video in coda): vince il telefono
private fun mergeObjects(local: String?, theirs: String): String {
    val merged = runCatching { JSONObject(theirs) }.getOrDefault(JSONObject())
    val mine = runCatching { JSONObject(local ?: "{}") }.getOrDefault(JSONObject())
    mine.keys().forEach { merged.put(it, mine.get(it)) }
    return merged.toString()
}

fun mergeShows(local: List<Show>, theirs: List<Show>): List<Show> {
    val byKey = LinkedHashMap<String, Show>()
    (local + theirs).forEach { show ->
        val known = byKey[show.key]
        byKey[show.key] = if (known == null || progress(show) > progress(known)) show else known
    }
    return byKey.values.sortedByDescending { it.updated }.take(MAX_SHOWS)
}

// Quanto si è avanti in una serie: stagione, episodio, poi visto o il punto, poi l'ultima volta che si è guardata
private fun progress(show: Show): List<Long> = show.current.let {
    listOf((it.season ?: 0).toLong(), (it.number ?: 0).toLong(), if (it.watched) Long.MAX_VALUE else it.time, show.updated)
}

private operator fun List<Long>.compareTo(other: List<Long>): Int {
    zip(other).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
    return size.compareTo(other.size)
}
