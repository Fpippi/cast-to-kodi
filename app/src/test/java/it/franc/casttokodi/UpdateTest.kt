package it.franc.casttokodi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Aggiornamento con un tocco: cosa si legge dalla release e come si mostrano le novità
class UpdateTest {

    @Test
    fun releaseConApk() {
        val release = parseRelease("""{"tag_name":"v2.3.0","body":"- Timer","assets":[
            {"name":"altro.zip","browser_download_url":"https://x/altro.zip","size":10},
            {"name":"CastToKodi.apk","browser_download_url":"https://x/CastToKodi.apk","size":12400000}]}""")
        assertEquals("v2.3.0", release.tag)
        assertEquals("https://x/CastToKodi.apk", release.apkUrl)
        assertEquals(12_400_000L, release.apkSize)
    }

    @Test
    fun releaseSenzaApk() {
        val release = parseRelease("""{"tag_name":"v2.3.0","body":""}""")
        assertNull(release.apkUrl)
        assertEquals(0L, release.apkSize)
    }

    @Test
    fun novitaSenzaMarkdown() {
        val notes = releaseNotes("## 2.3.0\r\n\r\n- **Timer** di spegnimento nel `telecomando`\r\n- Vedi la [guida](docs/TUTORIAL.md), *Backup* compreso\r\nRiga semplice")
        assertEquals("• Timer di spegnimento nel telecomando\n• Vedi la guida, Backup compreso\nRiga semplice", notes)
        assertEquals("• a\n• b\n…", releaseNotes("- a\n- b\n- c", max = 2))
    }

    @Test
    fun pesi() {
        assertEquals("12,4 MB", sizeLabel(12_400_000))
        assertEquals("800 kB", sizeLabel(800_000))
        assertEquals("Scarico… 3,1 MB di 12,4 MB", downloadLabel(3_100_000, 12_400_000))
        assertEquals("Scarico… 3,1 MB", downloadLabel(3_100_000, 0))
    }
}
