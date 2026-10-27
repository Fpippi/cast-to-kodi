package it.franc.casttokodi

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Alias nell'Android Keystore della chiave che cifra le password di Kodi
private const val LOGIN_KEY = "kodi-login"

// Kodi e i dati dell'app visti da fuori dell'Activity (riquadro della tendina, player di Android): le stesse prefs,
// le credenziali salvate e la chiave che le cifra. L'Activity usa queste funzioni invece di averne una copia.
object KodiAccess {
    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("kodi", Context.MODE_PRIVATE)

    // Il Kodi scelto con le credenziali salvate per questa rete, senza chiedere niente; null se Kodi non è scelto
    fun quietKodi(context: Context): Kodi? {
        val prefs = prefs(context)
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return null
        val login = loginFor(loadLogins(prefs), host, prefs.getString("name", "").orEmpty(), currentNetwork(context))
        val password = login?.let { unseal(it.secret) }
        return Kodi(host, if (login != null && password != null) "${login.user}:$password" else "")
    }

    // Impronta della rete attuale (sottorete IPv4 e gateway), senza SSID per non chiedere la posizione
    fun currentNetwork(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val link = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val address = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gateway = link.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway
        return networkKey(address.address.hostAddress, address.prefixLength, gateway?.hostAddress)
    }

    // Credenziali in SharedPreferences come JSON, con la password cifrata da seal
    fun loadLogins(prefs: SharedPreferences): List<SavedLogin> {
        val array = JSONArray(prefs.getString("logins", "[]"))
        return List(array.length()) { i ->
            array.getJSONObject(i).let {
                SavedLogin(it.getString("host"), it.getString("name"), it.getString("network"), it.getString("user"), it.getString("secret"))
            }
        }
    }

    private fun loginKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(LOGIN_KEY, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(LOGIN_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun seal(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, loginKey()) }
        val data = cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    // null se non si decifra (chiave persa, es. backup ripristinato su un altro telefono): si richiedono
    fun unseal(secret: String): String? = try {
        val (iv, data) = secret.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, loginKey(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(data))
    } catch (e: Exception) {
        null
    }

    // Nomi dei video mandati a Kodi (link senza header -> nome della pagina), per mostrarli nella coda
    fun titles(prefs: SharedPreferences): Map<String, String> {
        val json = JSONObject(prefs.getString("queueTitles", "{}")!!)
        return json.keys().asSequence().associateWithTo(LinkedHashMap()) { json.getString(it) }
    }

    fun shows(prefs: SharedPreferences): List<Show> = showsFromJson(prefs.getString("shows", null))

    // Cartella delle immagini delle serie (vedi saveFrame nell'Activity)
    fun framesDir(context: Context) = File(context.filesDir, "serie").apply { mkdirs() }
}
