package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Backup e ripristino: il file va e torna uguale, unire e sostituire, e soprattutto che ogni dato salvato dall'app
// abbia il suo posto nel backup (regola scritta in CLAUDE.md)
class BackupTest {

    private fun episode(number: Int, time: Long = 0, watched: Boolean = false, image: String? = null) =
        Episode(null, number, "/anime/1-jojo/$number", "f$number", time, 1400, watched, 1000L + number, image)

    private fun show(key: String, vararg episodes: Episode, updated: Long = 1000, image: String? = null) =
        Show(key, "animeunity", "www.animeunity.so", key, episodes.toList(), updated = updated, image = image)

    private fun roundTrip(values: Map<String, Any?>, parts: Set<BackupPart> = BackupPart.entries.toSet(),
                          images: Map<String, ByteArray> = emptyMap()): Backup {
        val out = ByteArrayOutputStream()
        writeBackup(out, backupJson(values, parts, "2.3.0", 42), images.mapValues { (_, bytes) -> { bytes } })
        return readBackup(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun ogniDatoSalvatoHaIlSuoPostoNelBackup() {
        val dir = listOf("src/main/java/it/franc/casttokodi", "app/src/main/java/it/franc/casttokodi")
            .map(::File).first { it.isDirectory }
        // putString("x", …), putInt, putLong, putBoolean…: le scritture in SharedPreferences (JSONObject usa put/putOpt)
        val written = dir.walk().filter { it.extension == "kt" }
            .flatMap { Regex("""\.put(?:String|Int|Long|Boolean|Float|StringSet)\(\s*"(\w+)"""").findAll(it.readText()) }
            .map { it.groupValues[1] }.toSet()
        assertTrue("nessuna scrittura trovata in $dir", written.isNotEmpty())
        val missing = written - BACKED_UP.keys - NOT_BACKED_UP.keys
        assertTrue("dati salvati senza posto nel backup (aggiungili a BACKED_UP o NOT_BACKED_UP): $missing", missing.isEmpty())
        assertTrue(BACKED_UP.keys.intersect(NOT_BACKED_UP.keys).isEmpty())
    }

    @Test
    fun andataERitorno() {
        val shows = showsToJson(listOf(show("a", episode(3, image = "a3.jpg"), image = "copertina-a.jpg")))
        val values = mapOf<String, Any?>(
            "favorites" to """[{"title":"Anime","url":"https://animetv.example/"}]""",
            "shows" to shows, "lastAt" to 123L, "autoNext" to 2, "manual" to true, "host" to "192.168.1.50:8080",
            "logins" to "segreto", "adbPrivate" to "chiave", "backupUri" to "content://x",
        )
        val images = mapOf("a3.jpg" to byteArrayOf(1, 2, 3), "copertina-a.jpg" to byteArrayOf(4), "vecchia.jpg" to byteArrayOf(5))
        val backup = roundTrip(values, images = images)
        assertEquals("2.3.0", backup.version)
        assertEquals(42L, backup.created)
        assertEquals(shows, backup.values["shows"])
        assertEquals(123L, backup.values["lastAt"])
        assertEquals(2, backup.values["autoNext"])
        assertEquals(true, backup.values["manual"])
        // Password, chiave ADB e file del backup automatico non escono dal telefono
        assertNull(backup.values["logins"])
        assertNull(backup.values["adbPrivate"])
        assertNull(backup.values["backupUri"])
        // Solo le immagini che le serie usano
        assertEquals(setOf("a3.jpg", "copertina-a.jpg"), backup.images.keys)
        assertEquals(BackupCount(favorites = 1, shows = 1, settings = true), backupCount(backup.values))
    }

    @Test
    fun soloLePartiScelte() {
        val backup = roundTrip(mapOf("favorites" to "[]", "shows" to "[]", "host" to "x:8080"), setOf(BackupPart.FAVORITES))
        assertEquals(setOf("favorites"), backup.values.keys)
        assertEquals(setOf(BackupPart.FAVORITES), backup.parts)
        // Sostituire tocca solo la parte che c'è: le serie del telefono restano
        val restore = replaceWith(backup)
        assertFalse("shows" in restore.remove)
        assertFalse("host" in restore.remove)
    }

    @Test
    fun sostituireTogliequelloCheNelBackupNonCera() {
        val backup = roundTrip(mapOf("favorites" to "[]", "host" to "x:8080"))
        val restore = replaceWith(backup)
        assertEquals("x:8080", restore.write["host"])
        assertTrue("shows" in restore.remove)
        assertTrue("last" in restore.remove)
        assertFalse("host" in restore.remove)
    }

    @Test
    fun unireTieneLaSerieAvanti() {
        val local = mapOf<String, Any?>(
            "favorites" to """[{"title":"Mio","url":"https://a.example/"}]""",
            "shows" to showsToJson(listOf(show("a", episode(5, time = 300)), show("b", episode(2)))),
            "host" to "10.0.0.2:8080",
            "lastAt" to 500L, "last" to "https://a.example/nuova",
        )
        val backup = roundTrip(mapOf(
            "favorites" to """[{"title":"Suo","url":"https://a.example/"},{"title":"Altro","url":"https://b.example/"}]""",
            "shows" to showsToJson(listOf(show("a", episode(4, watched = true)), show("b", episode(3)), show("c", episode(1)))),
            "host" to "10.0.0.9:8080", "intro" to 90,
            "lastAt" to 100L, "last" to "https://a.example/vecchia",
        ))
        val restore = mergeWith(local, backup)
        val favorites = org.json.JSONArray(restore.write["favorites"] as String)
        assertEquals(2, favorites.length())
        assertEquals("Mio", favorites.getJSONObject(0).getString("title"))
        val shows = showsFromJson(restore.write["shows"] as String).associateBy { it.key }
        assertEquals(setOf("a", "b", "c"), shows.keys)
        assertEquals(5, shows["a"]!!.current.number)
        assertEquals(3, shows["b"]!!.current.number)
        // Impostazioni: vince il telefono, il backup riempie solo quelle mai scelte
        assertNull(restore.write["host"])
        assertEquals(90, restore.write["intro"])
        // Ultima pagina: la più recente, qui quella del telefono
        assertNull(restore.write["last"])
        assertTrue(restore.remove.isEmpty())
    }

    @Test
    fun unireNonSuperaLeDodiciSerie() {
        val mine = (1..8).map { show("m$it", episode(1), updated = it.toLong()) }
        val theirs = (1..8).map { show("t$it", episode(1), updated = 100L + it) }
        val merged = mergeShows(mine, theirs)
        assertEquals(MAX_SHOWS, merged.size)
        assertEquals("t8", merged.first().key)
    }

    @Test
    fun datiDiUnaVersionePiuNuovaSiSaltano() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON))
            zip.write("""{"format":2,"version":"9.0.0","parts":["favorites","nuova"],"values":{"favorites":{"string":"[]"},"sconosciuto":{"string":"x"},"host":{"double":1.5}}}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("serie/../../fuori.jpg"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        val backup = readBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals(setOf("favorites"), backup.values.keys)
        assertEquals(2, backup.skipped)
        assertEquals(setOf(BackupPart.FAVORITES), backup.parts)
        assertTrue(backup.images.isEmpty())
    }

    @Test(expected = IOException::class)
    fun unFileQualsiasiNonEUnBackup() {
        readBackup(ByteArrayInputStream("ciao".toByteArray()))
    }
}
