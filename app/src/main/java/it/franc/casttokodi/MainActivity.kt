package it.franc.casttokodi

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Message
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.widget.CheckBox
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import it.franc.casttokodi.sites.GOOGLE
import it.franc.casttokodi.sites.MASTER_PLAYLIST_JS
import it.franc.casttokodi.sites.MAX_AUTO_NEXT
import it.franc.casttokodi.sites.NEXT_EPISODES_JS
import it.franc.casttokodi.sites.isAnimeUnityEpisode
import it.franc.casttokodi.sites.isGoogleConsent
import it.franc.casttokodi.sites.linkExpiry
import kotlin.concurrent.thread

private const val REPO = "Fpippi/cast-to-kodi"

private const val NOT_FOUND_HELP = """Nessun Kodi trovato. Controlla che:

1. Kodi sia aperto sulla Fire TV;
2. in Kodi sia attivo Impostazioni → Servizi → Generale → "Annuncia servizi ad altri sistemi";
3. telefono e Fire TV siano sullo stesso Wi-Fi.

Oppure passa a "Manuale" e scrivi IP:porta."""

// Kodi riproduce la coda dalla sua playlist video
private const val VIDEO_PLAYLIST = 1

// Durata ipotizzata di un episodio quando Kodi non la sa ancora, per stimare quando partirà un elemento in coda
private const val DEFAULT_EPISODE = 24 * 60L

// Un episodio va accodato solo se il suo link vale ancora almeno 10 minuti dopo l'inizio previsto
private const val EXPIRY_MARGIN = 10 * 60L

// Quanto aspettare che Kodi faccia partire il video dalla playlist prima di darlo per partito
private const val START_TIMEOUT_MS = 15_000L

// Dopo quanto chiudere la finestra finta data ai popup
private const val POPUP_SINK_MS = 10_000L

// Alias nell'Android Keystore della chiave che cifra le password di Kodi
private const val LOGIN_KEY = "kodi-login"

// Nome della chiave ADB del telefono, salvato dalla Fire TV con la chiave
private const val ADB_KEY_NAME = "Couch Cast"

private const val NO_ADB_HELP = """Controlla che:

1. sulla Fire TV sia attivo Impostazioni → La mia Fire TV → Opzioni sviluppatore → Debug ADB;
2. telefono e Fire TV siano sullo stesso Wi-Fi;
3. Kodi non serve: l'app cerca da sola la Fire TV in rete sulla porta 5555.

Se la Fire TV è in sonno profondo il suo Wi-Fi è spento: accendila con Alexa o con il telecomando."""

// Le ultime righe utili del log di Kodi sulla Fire TV, lette via ADB: senza add-on da installare.
// Con il debug di Kodi attivo il log è pieno di rumore: si tengono errori e righe su apertura e riproduzione.
private const val KODI_LOG = "tail -n 3000 /sdcard/Android/data/org.xbmc.kodi/files/.kodi/temp/kodi.log 2>&1 | " +
    "grep -iE 'error|warning|fail|curl|playlist|playfile|videoplayer|inputstream|ffmpeg|http' | tail -n 120"

private class KodiError(message: String) : Exception(message)

// Kodi ha risposto 401: mancano utente e password o sono sbagliati, si chiedono
private class Unauthorized : Exception()

// La coda non può proseguire con questo video: si avvisa con il motivo e non si accoda
private class Refused(message: String) : Exception(message)

// position: dove è finito il video nella playlist di Kodi; null se è partito fuori dalla coda
private class Sent(val message: String, val position: Int?)

private class KodiState(
    val playerId: Int?,
    val fromQueue: Boolean,
    val position: Int,
    val remaining: Long,
    val episode: Long,
    val queueLength: Int,
) {
    // Secondi prima che parta l'elemento in posizione index, stimando gli episodi lunghi come quello in corso
    fun secondsUntil(index: Int): Long =
        if (playerId == null || !fromQueue) 0 else remaining + (index - position - 1).coerceAtLeast(0) * episode
}

// Client JSON-RPC di Kodi, da usare su un thread di background. I problemi di rete restano IOException
// (Kodi forse ha cambiato IP), le risposte di errore diventano KodiError con un messaggio da mostrare.
private class Kodi(val host: String, val auth: String) {
    fun call(method: String, params: JSONObject = JSONObject()): Any? {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", method)
            .put("params", params)
            .toString()
        val conn = URL("http://$host/jsonrpc").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.setRequestProperty("Content-Type", "application/json")
        if (auth.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP))
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        when (conn.responseCode) {
            200 -> {}
            401 -> throw Unauthorized()
            else -> throw KodiError("Kodi ha risposto HTTP ${conn.responseCode}.")
        }
        val reply = JSONObject(conn.inputStream.bufferedReader().readText())
        reply.optJSONObject("error")?.let { throw KodiError("$method: ${it.optString("message")}") }
        return reply.opt("result")
    }
}

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var cast: Button
    private lateinit var queue: Button
    private lateinit var progress: ProgressBar
    private val prefs by lazy { getSharedPreferences("kodi", MODE_PRIVATE) }

    // url del video -> Referer con cui la pagina l'ha richiesto (molti host rifiutano richieste senza)
    private val videos = LinkedHashMap<String, String>()

    // Dominio da cui la pagina principale può navigare: tutto il resto è considerato redirect pubblicitario
    private var allowedDomain = ""
    @Volatile private var pageUrl = ""

    // "IP:porta" -> nome annunciato via mDNS, per riconoscere lo stesso Kodi quando cambia IP
    private val kodiNames = HashMap<String, String>()

    // Favicon della pagina aperta, salvata con il preferito
    private var pageIcon: Bitmap? = null

    // Mentre si cerca Kodi in rete lo dice il pulsante in basso: un toast sparisce prima di averlo letto
    private var searchingKodi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = EditText(this).apply {
            hint = "Cerca o scrivi un indirizzo"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> open(text.toString()); true }
        }
        val favorites = Button(this).apply {
            text = "★"
            setOnClickListener { showFavorites() }
        }
        val settings = Button(this).apply {
            text = "Kodi"
            setOnClickListener { askKodiAddress() }
        }
        val power = Button(this).apply {
            contentDescription = "Accendi la TV e apri Kodi"
            tooltipText = contentDescription
            setOnClickListener { wakeKodi() }
        }
        paint(favorites, active = true, primary = false)
        paint(power, active = true, primary = false)
        // Il carattere ⏻ manca nel font del Pixel (si vedeva un quadratino): icona vettoriale nel testo, del colore del testo
        power.text = SpannableString(" ").apply {
            val icon = getDrawable(it.franc.casttokodi.R.drawable.ic_power)!!.mutate().apply {
                val size = dp(22)
                setBounds(0, 0, size, size)
                setTint(power.currentTextColor)
            }
            setSpan(ImageSpan(icon, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        paint(settings, active = true, primary = false)
        cast = Button(this).apply {
            setOnClickListener { pickVideo(queued = false) }
        }
        queue = Button(this).apply {
            text = "+ Coda"
            setOnClickListener { pickVideo(queued = true) }
        }
        progress = ProgressBar(this, null, R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(sys(R.color.system_accent1_600, R.color.system_accent1_200))
        }
        web = WebView(this)
        setupWebView()

        val topBar = LinearLayout(this).apply {
            setPadding(dp(8), dp(4), dp(8), 0)
            addView(address, LinearLayout.LayoutParams(0, -2, 1f))
            addView(favorites, LinearLayout.LayoutParams(dp(56), -2).apply { marginStart = dp(4) })
            addView(power, LinearLayout.LayoutParams(dp(56), -2).apply { marginStart = dp(4) })
            addView(settings, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        }
        val bottomBar = LinearLayout(this).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(cast, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(queue, LinearLayout.LayoutParams(-2, dp(56)).apply { marginStart = dp(8) })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar)
            addView(progress, LinearLayout.LayoutParams(-1, -2))
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(bottomBar, LinearLayout.LayoutParams(-1, -2))
            // targetSdk 35 forza l'edge-to-edge: senza padding la barra finisce sotto la status bar
            setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                WindowInsets.CONSUMED
            }
        }
        setContentView(root)
        refreshCastButton()

        migrateAuth()
        open(prefs.getString("last", null) ?: GOOGLE)
        if (prefs.getString("host", "").isNullOrBlank()) askKodiAddress()
        checkUpdate()
    }

    // L'ultima release ha come tag vX.Y.Z, con X.Y.Z = versionName dell'APK (da CHANGELOG.md, vedi .github/workflows/apk.yml).
    // Il repo è pubblico: l'API non vuole token (limite 60 richieste l'ora per IP, ne basta una per avvio).
    private fun checkUpdate() {
        val installed = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        thread {
            val latest = try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) return@thread
                JSONObject(conn.inputStream.bufferedReader().readText()).optString("tag_name")
            } catch (e: Exception) {
                return@thread
            }
            if (!isNewerVersion(latest, installed)) return@thread
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Nuova versione disponibile")
                    .setMessage("Installata: $installed\nUltima: ${latest.removePrefix("v")}")
                    .setPositiveButton("Scarica") { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$REPO/releases/latest")))
                    }
                    .setNegativeButton("Dopo", null)
                    .show()
            }
        }
    }

    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Con il supporto multi-finestra attivo, window.open passa da onCreateWindow e lo possiamo rifiutare;
            // senza, il popup verrebbe caricato al posto della pagina.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
        }
        web.webChromeClient = object : WebChromeClient() {
            // Molti player aprono un popup al primo tocco e partono solo se window.open riesce: se lo si rifiuta,
            // ogni tocco su "avvia" riapre il popup e il video non parte mai. Gli si dà una finestra finta,
            // mai mostrata, che non scarica niente e si chiude da sola.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                toast("Popup bloccato")
                val sink = WebView(this@MainActivity)
                sink.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = sink
                resultMsg.sendToTarget()
                web.postDelayed({ sink.destroy() }, POPUP_SINK_MS)
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                pageIcon = icon
                fillMissingIcon(view.url, icon)
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val blocked = blocksNavigation(
                    url.scheme, url.host, allowedDomain, request.isForMainFrame, request.isRedirect
                )
                return if (blocked) block(url) else false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageUrl = url
                pageIcon = favicon
                allowedDomain = domainOf(Uri.parse(url))
                address.setText(url)
                videos.clear()
                refreshCastButton()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!isGoogleConsent(Uri.parse(url).host)) prefs.edit().putString("last", url).apply()
            }

            // Chiamato su un thread di background per ogni risorsa, anche dentro gli iframe e per le XHR dei player
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (isVideoPath(request.url.path)) {
                    val referer = request.requestHeaders["Referer"] ?: pageUrl
                    val video = request.url.toString()
                    runOnUiThread {
                        if (videos.putIfAbsent(video, referer) == null) refreshCastButton()
                    }
                }
                return null
            }
        }
    }

    private fun open(input: String) {
        val url = addressFor(input)
        allowedDomain = domainOf(Uri.parse(url))
        web.loadUrl(url)
        web.requestFocus()
    }

    private fun block(url: Uri): Boolean {
        toast("Bloccato: ${url.host ?: url.scheme}")
        return true
    }

    private fun domainOf(url: Uri) = domainOf(url.host)

    // Video trovati senza pubblicità e senza i pezzi delle playlist VixCloud, con la playlist principale per prima
    private fun playable(): List<Video> = videos.mapNotNull { (url, referer) ->
        val uri = Uri.parse(url)
        videoFor(url, referer, uri.host, uri.path, uri.encodedQuery)
    }.sortedByDescending { it.main }

    private fun refreshCastButton() {
        val count = playable().size
        val ready = count > 0 && !searchingKodi
        cast.isEnabled = ready
        queue.isEnabled = ready
        cast.text = when {
            searchingKodi -> "Cerco Kodi in rete…"
            count == 0 -> "Nessun video: avvia il player"
            count == 1 -> "Manda a Kodi"
            else -> "Manda a Kodi ($count)"
        }
        paint(cast, ready, primary = true)
        paint(queue, ready, primary = false)
    }

    // Colori di sistema (Material You, presi dallo sfondo del Pixel): accento per l'azione principale,
    // tonale per le secondarie, neutro quando non si può premere. Chiaro e scuro seguono il tema del telefono.
    private fun paint(button: Button, active: Boolean, primary: Boolean) {
        val (background, text) = when {
            // Il testo da spento è un'istruzione ("Nessun video: avvia il player"): grigio ma leggibile in tutti e due i temi
            !active -> sys(R.color.system_neutral2_100, R.color.system_neutral2_800) to
                sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
            primary -> sys(R.color.system_accent1_600, R.color.system_accent1_200) to
                sys(R.color.system_accent1_0, R.color.system_accent1_800)
            else -> sys(R.color.system_accent2_100, R.color.system_accent2_700) to
                sys(R.color.system_accent2_900, R.color.system_accent2_100)
        }
        button.backgroundTintList = ColorStateList.valueOf(background)
        button.setTextColor(text)
        button.isAllCaps = false
    }

    private fun sys(light: Int, dark: Int): Int {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return getColor(if (night) dark else light)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun pickVideo(queued: Boolean) {
        val list = playable()
        if (list.isEmpty()) return
        if (list.size == 1) return sendToKodi(list[0], queued)
        AlertDialog.Builder(this)
            .setTitle(if (queued) "Quale video mettere in coda?" else "Quale video?")
            .setItems(list.map { it.label }.toTypedArray()) { _, i -> sendToKodi(list[i], queued) }
            .show()
    }

    // Tutto passa dalla playlist video di Kodi, così "+ Coda" e gli episodi successivi seguono quello in corso.
    // La coda non si cancella mai: un video nuovo si inserisce dopo quello in corso o in fondo.
    // typed: utente e password appena scritti nel dialogo dopo un 401, al posto di quelli salvati
    private fun sendToKodi(video: Video, queued: Boolean, typed: Pair<String, String>? = null) {
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val network = currentNetwork()
        val login = typed ?: savedLogin(host, network)
        val kodi = Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
        val userAgent = web.settings.userAgentString
        val page = pageUrl

        thread {
            val sent = try {
                if (queued) enqueue(kodi, video, userAgent) else playNow(kodi, video, userAgent)
            } catch (e: IOException) {
                runOnUiThread { relocateKodi(host) { sendToKodi(video, queued, typed) } }
                return@thread
            } catch (e: Unauthorized) {
                runOnUiThread { askLogin(host, login, wrong = login != null) { sendToKodi(video, queued, it) } }
                return@thread
            } catch (e: Refused) {
                runOnUiThread { notice("Non messo in coda", e.message.orEmpty()) }
                return@thread
            } catch (e: KodiError) {
                runOnUiThread { notice("Kodi non ha accettato il video", e.message.orEmpty()) }
                return@thread
            } catch (e: Exception) {
                runOnUiThread { notice("Errore", e.message.orEmpty()) }
                return@thread
            }
            runOnUiThread {
                // Hanno funzionato: valgono per questo Kodi su questa rete (anche le vecchie senza rete, o dopo un cambio di IP)
                if (login != null && network != null) storeLogin(host, network, login)
                toast(sent.message)
                sent.position?.let { queueNextEpisodes(kodi, page, it, userAgent) }
            }
        }
    }

    private fun playNow(kodi: Kodi, video: Video, userAgent: String): Sent {
        val item = kodiItem(video.url, video.referer, userAgent)
        val state = kodiState(kodi)
        return try {
            if (state.fromQueue) {
                val position = state.position + 1
                kodi.call("Playlist.Insert", playlist().put("position", position).put("item", item))
                kodi.call("Player.GoTo", JSONObject().put("playerid", state.playerId).put("to", position))
                Sent("Inviato a Kodi", position)
            } else {
                // Kodi fermo o con un video non partito dall'app: in fondo alla coda, e si parte da lì
                val position = state.queueLength
                kodi.call("Playlist.Add", playlist().put("item", item))
                kodi.call("Player.Open", JSONObject().put("item", playlist().put("position", position)))
                // Player.Open risponde OK subito anche se poi il video non parte ("Playback failed" sulla TV)
                if (!startedFromQueue(kodi)) return openWithoutQueue(kodi, item)
                Sent("Inviato a Kodi", position)
            }
        } catch (e: KodiError) {
            openWithoutQueue(kodi, item)
        }
    }

    // Con alcuni link la playlist di Kodi è più severa di Player.Open {file}: il video parte da solo, senza coda dietro
    private fun openWithoutQueue(kodi: Kodi, item: JSONObject): Sent {
        kodi.call("Player.Open", JSONObject().put("item", item))
        return Sent("Inviato a Kodi, ma senza coda: Kodi non riproduce questo video dalla playlist", null)
    }

    // true quando il video parte dalla playlist, false se Kodi l'ha scartato: dopo il fallimento svuota la playlist.
    // Se dopo START_TIMEOUT_MS non è ancora deciso lo si dà per partito, per non aprirlo due volte.
    private fun startedFromQueue(kodi: Kodi): Boolean {
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            val state = kodiState(kodi)
            if (state.fromQueue) return true
            if (state.queueLength == 0) return false
        }
        return true
    }

    // Accoda solo se l'episodio partirà davvero: altrimenti Refused con il motivo, da mostrare
    private fun enqueue(kodi: Kodi, video: Video, userAgent: String): Sent {
        val state = kodiState(kodi)
        if (state.playerId == null) return playNow(kodi, video, userAgent)
        if (!state.fromQueue) {
            throw Refused(
                "Il video in corso su Kodi non è partito dall'app: quando finisce, Kodi non passa alla coda.\n\n" +
                    "Premi \"Manda a Kodi\" su questo episodio: parte subito e da lì puoi accodare gli altri."
            )
        }
        val position = state.queueLength
        expiryProblem(video.url, state.secondsUntil(position))?.let { throw Refused(it) }
        try {
            kodi.call("Playlist.Add", playlist().put("item", kodiItem(video.url, video.referer, userAgent)))
        } catch (e: KodiError) {
            throw Refused("Kodi non accetta questo video in coda (${e.message}).")
        }
        return Sent("Aggiunto alla coda", position)
    }

    private fun kodiState(kodi: Kodi): KodiState {
        val players = kodi.call("Player.GetActivePlayers") as? JSONArray ?: JSONArray()
        val items = kodi.call("Playlist.GetItems", playlist()) as? JSONObject
        val queueLength = items?.optJSONObject("limits")?.optInt("total") ?: 0
        val player = (0 until players.length()).map { players.getJSONObject(it) }.firstOrNull { it.optString("type") == "video" }
            ?: return KodiState(null, false, -1, 0, DEFAULT_EPISODE, queueLength)
        val id = player.getInt("playerid")
        val properties = JSONArray(listOf("playlistid", "position", "time", "totaltime"))
        val p = kodi.call("Player.GetProperties", JSONObject().put("playerid", id).put("properties", properties)) as JSONObject
        val total = seconds(p.optJSONObject("totaltime"))
        val position = p.optInt("position", -1)
        return KodiState(
            playerId = id,
            fromQueue = p.optInt("playlistid", -1) == VIDEO_PLAYLIST && position >= 0,
            position = position,
            remaining = (total - seconds(p.optJSONObject("time"))).coerceAtLeast(0),
            episode = if (total > 0) total else DEFAULT_EPISODE,
            queueLength = queueLength,
        )
    }

    private fun seconds(time: JSONObject?): Long =
        if (time == null) 0 else time.optLong("hours") * 3600 + time.optLong("minutes") * 60 + time.optLong("seconds")

    // VixCloud scrive nel link la scadenza del token (expires=<secondi unix>): un episodio che parte dopo non si vede
    private fun expiryProblem(url: String, startsIn: Long): String? {
        val expires = linkExpiry(Uri.parse(url).encodedQuery) ?: return null
        val startsAt = System.currentTimeMillis() / 1000 + startsIn
        if (startsAt + EXPIRY_MARGIN < expires) return null
        val time = DateFormat.getTimeFormat(this).format(Date(expires * 1000))
        return "Il link di questo episodio scade alle $time, prima che tocchi a lui: mettilo in coda più tardi."
    }

    private fun playlist() = JSONObject().put("playlistid", VIDEO_PLAYLIST)

    // Kodi accetta gli header HTTP accodati all'URL: url|Header=valore&Header2=valore
    private fun kodiItem(url: String, referer: String, userAgent: String) =
        JSONObject().put("file", "$url|Referer=${enc(referer)}&User-Agent=${enc(userAgent)}")

    // AnimeUnity: dopo l'invio si accodano da soli gli N episodi successivi, subito dopo quello mandato.
    // L'elenco degli episodi e il player di ognuno si leggono dalla pagina del sito, il link video dal player.
    private fun queueNextEpisodes(kodi: Kodi, page: String, after: Int, userAgent: String) {
        val count = prefs.getInt("autoNext", 1)
        val uri = Uri.parse(page)
        if (count <= 0 || !isAnimeUnityEpisode(uri.host, uri.path)) return
        if (web.url != page) return
        web.evaluateJavascript(NEXT_EPISODES_JS.replace("COUNT", count.toString())) { result ->
            val reply = (JSONTokener(result).nextValue() as? String)?.let { JSONObject(it) }
            val next = reply?.optJSONArray("next")
            if (next == null || next.length() == 0) {
                toast(if (reply?.has("next") == true) "Era l'ultimo episodio" else "Episodio successivo non trovato: usa + Coda")
                return@evaluateJavascript
            }
            val queuedNumbers = mutableListOf<String>()
            fun queueAt(i: Int) {
                if (i >= next.length()) {
                    if (queuedNumbers.isNotEmpty()) toast("In coda anche: episodio ${queuedNumbers.joinToString(", ")}")
                    return
                }
                val episode = next.getJSONObject(i)
                val number = episode.optString("number")
                val embed = episode.optString("embed").takeIf { it.startsWith("http") }
                    ?: return notice("Episodio $number non messo in coda", "Non ho trovato il suo player sulla pagina. Aprilo e usa + Coda.")
                resolveVixCloud(embed, userAgent) { stream ->
                    if (stream == null) {
                        notice("Episodio $number non messo in coda", "Non ho trovato il link del video. Aprilo e usa + Coda.")
                        return@resolveVixCloud
                    }
                    thread {
                        val problem = try {
                            val position = after + 1 + i
                            expiryProblem(stream, kodiState(kodi).secondsUntil(position))
                                ?: run {
                                    kodi.call("Playlist.Insert", playlist().put("position", position).put("item", kodiItem(stream, embed, userAgent)))
                                    null
                                }
                        } catch (e: Exception) {
                            "Kodi: ${e.message}"
                        }
                        runOnUiThread {
                            if (problem != null) return@runOnUiThread notice("Episodio $number non messo in coda", problem)
                            queuedNumbers += number
                            queueAt(i + 1)
                        }
                    }
                }
            }
            queueAt(0)
        }
    }

    // Il link video (playlist VixCloud con token) esiste solo dentro la pagina del player: la si carica in un
    // WebView invisibile e si legge window.masterPlaylist, senza far partire il video
    private fun resolveVixCloud(embed: String, userAgent: String, onResult: (String?) -> Unit) {
        val hidden = WebView(this)
        hidden.settings.javaScriptEnabled = true
        hidden.settings.userAgentString = userAgent
        var done = false
        val finish = { stream: String? ->
            if (!done) {
                done = true
                web.post { hidden.destroy() }
                onResult(stream)
            }
        }
        hidden.webViewClient = object : WebViewClient() {
            // Solo i redirect HTTP: i salti fatti da JS (pubblicità) non portano via dal player
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !request.isRedirect

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(MASTER_PLAYLIST_JS) { result ->
                    // Un redirect può arrivare qui prima della pagina vera: si aspetta quella o il timeout
                    (JSONTokener(result).nextValue() as? String)?.let(finish)
                }
            }
        }
        hidden.loadUrl(embed, mapOf("Referer" to pageUrl))
        web.postDelayed({ finish(null) }, 15000)
    }

    // Accende la TV e apre Kodi via ADB in rete (#31): la Fire TV si sveglia, con HDMI-CEC accende la TV e la
    // porta sul suo ingresso, poi Kodi va in primo piano. Si avvisa quando Kodi risponde, così si può mandare il video.
    // La Fire TV si prova prima all'ultimo IP che ha funzionato (o a quello di Kodi), poi si cerca in tutta la rete.
    private fun wakeKodi() {
        val kodiHost = prefs.getString("host", "").orEmpty()
        val known = listOfNotNull(prefs.getString("adbHost", null), adbHost(kodiHost).ifBlank { null }).distinct()
        val local = localAddress()
        toast("Accendo la TV…")
        thread {
            val reachable = findAdb(known, timeout = 1500)
            val found = reachable.ifEmpty {
                runOnUiThread { toast("Cerco la Fire TV in rete…") }
                local?.let { (address, prefix) -> findAdb(subnetHosts(address, prefix)) }.orEmpty()
            }
            runOnUiThread {
                when {
                    found.isEmpty() -> notice("Fire TV non trovata", "Nessun dispositivo risponde sulla porta 5555.\n\n$NO_ADB_HELP")
                    found.size == 1 -> wakeAt(found[0])
                    else -> AlertDialog.Builder(this)
                        .setTitle("Quale Fire TV?")
                        .setItems(found.toTypedArray()) { _, i -> wakeAt(found[i]) }
                        .show()
                }
            }
        }
    }

    private fun wakeAt(ip: String) {
        val savedHost = prefs.getString("host", "").orEmpty()
        // Kodi è sulla Fire TV appena trovata, sulla porta salvata (8080 se non c'è ancora un Kodi salvato)
        val host = "$ip:" + savedHost.substringAfter(':', "8080")
        val network = currentNetwork()
        val login = savedLogin(savedHost.ifBlank { host }, network)
        thread {
            var confirming = false
            val problem = try {
                adbShell(ip, adbKeys(), ADB_KEY_NAME, WAKE_AND_OPEN_KODI, onConfirm = {
                    confirming = true
                    runOnUiThread {
                        notice(
                            "Conferma sulla TV",
                            "La Fire TV chiede se consentire il debug da questo telefono: spunta \"Consenti sempre da questo computer\" e premi OK. Serve solo la prima volta."
                        )
                    }
                })
                null
            } catch (e: SocketTimeoutException) {
                if (confirming) "La TV non ha confermato in tempo. Premi di nuovo il pulsante di accensione e accetta la richiesta sullo schermo." else "Non risponde a $ip.\n\n$NO_ADB_HELP"
            } catch (e: IOException) {
                if (confirming) "La TV ha rifiutato il telefono. Premi di nuovo il pulsante di accensione e accetta la richiesta sullo schermo." else "Non risponde a $ip.\n\n$NO_ADB_HELP"
            } catch (e: Exception) {
                e.message.orEmpty()
            }
            if (problem != null) {
                runOnUiThread { notice("Fire TV non raggiungibile", problem) }
                return@thread
            }
            prefs.edit().putString("adbHost", ip).apply()
            val ready = waitForKodi(Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty()))
            runOnUiThread {
                if (ready && host != savedHost) {
                    // La Fire TV ha cambiato IP (o Kodi non era ancora salvato): Kodi e le sue credenziali la seguono
                    if (savedHost.isNotBlank()) saveLogins(moveLogin(loadLogins(), savedHost, host, prefs.getString("name", "").orEmpty(), network))
                    prefs.edit().putString("host", host).putBoolean("manual", false).apply()
                    toast("Kodi è pronto a $host")
                } else {
                    toast(if (ready) "Kodi è pronto" else "Kodi è aperto, ma non risponde ancora")
                }
            }
        }
    }

    // Kodi appena aperto ci mette qualche secondo ad accendere il server JSON-RPC; un 401 vuol dire che risponde
    private fun waitForKodi(kodi: Kodi): Boolean {
        repeat(20) {
            try {
                kodi.call("JSONRPC.Ping")
                return true
            } catch (e: Unauthorized) {
                return true
            } catch (e: Exception) {
                Thread.sleep(1500)
            }
        }
        return false
    }

    // Coppia di chiavi RSA del telefono per ADB, creata al primo uso; la privata è cifrata come le password di Kodi
    private fun adbKeys(): KeyPair {
        val public = prefs.getString("adbPublic", null)
        val private = prefs.getString("adbPrivate", null)?.let { unseal(it) }
        if (public != null && private != null) {
            val factory = KeyFactory.getInstance("RSA")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(Base64.decode(public, Base64.NO_WRAP))),
                factory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(private, Base64.NO_WRAP))),
            )
        }
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        prefs.edit()
            .putString("adbPublic", Base64.encodeToString(keys.public.encoded, Base64.NO_WRAP))
            .putString("adbPrivate", seal(Base64.encodeToString(keys.private.encoded, Base64.NO_WRAP)))
            .apply()
        return keys
    }

    // Il router può aver dato un altro IP alla Fire TV: si cerca Kodi in rete, ci si sposta lì e si riprova.
    // Se lo trova allo stesso indirizzo il retry non scatta, quindi niente giri a vuoto.
    private fun relocateKodi(oldHost: String, retry: () -> Unit) {
        searchingKodi = true
        refreshCastButton()
        discoverKodi { found ->
            searchingKodi = false
            refreshCastButton()
            val moveTo = { host: String ->
                val name = kodiNames[host].orEmpty()
                saveLogins(moveLogin(loadLogins(), oldHost, host, name, currentNetwork()))
                prefs.edit().putString("host", host).putString("name", name).putBoolean("manual", false).apply()
                toast("Kodi trovato a $host")
                retry()
            }
            when {
                found.size == 1 && found[0] != oldHost -> moveTo(found[0])
                found.size > 1 -> chooseKodi(found, moveTo)
                else -> notice("Kodi non risponde", "Non risponde a $oldHost e non l'ho trovato in rete.\n\n$NOT_FOUND_HELP")
            }
        }
    }

    // Kodi si annuncia via mDNS solo se in Impostazioni → Servizi → Generale è attivo "Annuncia servizi ad altri sistemi".
    // Restituisce sul thread UI gli "IP:porta" trovati in qualche secondo.
    private fun discoverKodi(onResult: (List<String>) -> Unit) {
        val nsd = getSystemService(NsdManager::class.java) ?: return onResult(emptyList())
        // Lo stato si tocca solo dal thread UI: i callback di NsdManager arrivano su un thread suo
        val found = LinkedHashSet<String>()
        val queue = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        // resolveService accetta una risoluzione alla volta: le altre aspettano in coda
        fun resolveNext() {
            if (resolving) return
            val service = queue.removeFirstOrNull() ?: return
            resolving = true
            @Suppress("DEPRECATION") // il sostituto registerServiceInfoCallback esiste solo da API 34
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) = runOnUiThread {
                    // Solo IPv4: un indirizzo IPv6 con i ":" non si può scrivere come "host:porta"
                    @Suppress("DEPRECATION") val address = info.host
                    if (address is Inet4Address) {
                        val hostPort = "${address.hostAddress}:${info.port}"
                        found.add(hostPort)
                        kodiNames[hostPort] = info.serviceName.orEmpty()
                    }
                    resolving = false
                    resolveNext()
                }

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = runOnUiThread {
                    resolving = false
                    resolveNext()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) = runOnUiThread {
                queue.addLast(service)
                resolveNext()
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsd.discoverServices("_xbmc-jsonrpc-h._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        web.postDelayed({
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                // la ricerca non era partita
            }
            onResult(found.toList())
        }, 3000)
    }

    private fun chooseKodi(found: List<String>, onPick: (String) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Quale Kodi?")
            .setItems(found.toTypedArray()) { _, i -> onPick(found[i]) }
            .show()
    }

    // Due modi alternativi per dire dov'è Kodi (ricerca in rete o IP scritto a mano) più l'accesso, comune a entrambi
    private fun askKodiAddress() {
        val saved = prefs.getString("host", "").orEmpty()
        val startManual = saved.isNotBlank() && prefs.getBoolean("manual", false)
        var chosen = if (startManual) "" else saved
        var searched = false

        val autoTab = RadioButton(this).apply { id = View.generateViewId(); text = "Automatico" }
        val manualTab = RadioButton(this).apply { id = View.generateViewId(); text = "Manuale" }
        val tabs = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(autoTab)
            addView(manualTab)
        }

        val spinner = ProgressBar(this).apply { isIndeterminate = true }
        val status = TextView(this).apply { textSize = 15f }
        val statusRow = LinearLayout(this).apply {
            setPadding(0, dp(8), 0, dp(8))
            addView(spinner, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val found = RadioGroup(this)
        val search = Button(this).apply { text = "Cerca di nuovo" }
        val autoPanel = section(statusRow, found, search)

        val host = EditText(this).apply {
            hint = "IP:porta, es. 192.168.1.50:8080"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(if (startManual) saved else "")
        }
        val manualHelp = label("L'IP è in Fire TV → Impostazioni → La mia Fire TV → Info → Rete. La porta è quella di Kodi, di solito 8080.")
        val manualPanel = section(host, manualHelp)

        val network = currentNetwork()
        val login = savedLogin(saved, network)
        val (user, password, showPassword) = loginFields(login)
        val authPanel = section(label("Accesso, se impostato in Kodi"), user, password, showPassword).apply { setPadding(0, dp(16), 0, 0) }
        val autoNext = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("autoNext", 1).toString())
        }
        val autoNextPanel = section(
            label("AnimeUnity: episodi successivi da accodare da soli (0 = mai, massimo $MAX_AUTO_NEXT)"),
            autoNext,
        ).apply { setPadding(0, dp(16), 0, 0) }

        fun runSearch() {
            searched = true
            found.removeAllViews()
            spinner.visibility = View.VISIBLE
            status.text = "Cerco Kodi sul Wi-Fi…"
            search.isEnabled = false
            discoverKodi { list ->
                spinner.visibility = View.GONE
                search.isEnabled = true
                status.text = when (list.size) {
                    0 -> NOT_FOUND_HELP
                    1 -> "Trovato:"
                    else -> "Trovati ${list.size} Kodi, scegli quale:"
                }
                if (list.size == 1) chosen = list[0]
                for (address in list) {
                    val option = RadioButton(this).apply {
                        id = View.generateViewId()
                        text = address
                        setOnClickListener { chosen = address }
                    }
                    found.addView(option)
                    if (address == chosen) found.check(option.id)
                }
            }
        }
        search.setOnClickListener { runSearch() }

        tabs.setOnCheckedChangeListener { _, checked ->
            val manual = checked == manualTab.id
            autoPanel.visibility = if (manual) View.GONE else View.VISIBLE
            manualPanel.visibility = if (manual) View.VISIBLE else View.GONE
            if (!manual && !searched) runSearch()
        }
        tabs.check(if (startManual) manualTab.id else autoTab.id)

        val form = section(tabs, autoPanel, manualPanel, authPanel, autoNextPanel).apply { setPadding(dp(24), dp(8), dp(24), 0) }
        AlertDialog.Builder(this)
            .setTitle("Dov'è Kodi?")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Salva") { _, _ ->
                val manual = tabs.checkedRadioButtonId == manualTab.id
                val newHost = if (manual) host.text.toString().trim() else chosen.ifBlank { saved }
                prefs.edit()
                    .putString("host", newHost)
                    .putString("name", if (manual) "" else kodiNames[newHost] ?: prefs.getString("name", ""))
                    .putBoolean("manual", manual)
                    .putInt("autoNext", autoNext.text.toString().toIntOrNull()?.coerceIn(0, MAX_AUTO_NEXT) ?: 1)
                    .apply()
                val typed = user.text.toString().trim() to password.text.toString()
                when {
                    // Fuori casa (o senza Wi-Fi) non si sa a che rete legarle: si richiedono al primo 401
                    network == null -> {}
                    typed.first.isEmpty() -> saveLogins(replaceLogin(loadLogins(), newHost, prefs.getString("name", "").orEmpty(), network, null))
                    typed != login || newHost != saved -> storeLogin(newHost, network, typed)
                }
            }
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Log di Kodi") { _, _ -> showKodiLog() }
            .show()
    }

    // Quando Kodi rifiuta un video il motivo è solo nel suo log: lo si legge via ADB, come per il pulsante di
    // accensione, e lo si può condividere. La Fire TV è all'ultimo IP ADB che ha funzionato o a quello di Kodi.
    private fun showKodiLog() {
        val ip = prefs.getString("adbHost", null) ?: adbHost(prefs.getString("host", "").orEmpty())
        if (ip.isBlank()) return notice("Log di Kodi", "Prima scegli Kodi o accendi la TV con il pulsante di accensione.")
        toast("Leggo il log di Kodi…")
        thread {
            val log = try {
                adbShell(ip, adbKeys(), ADB_KEY_NAME, KODI_LOG, onConfirm = {
                    runOnUiThread {
                        notice("Conferma sulla TV", "La Fire TV chiede se consentire il debug da questo telefono: spunta \"Consenti sempre da questo computer\" e premi OK.")
                    }
                })
            } catch (e: Exception) {
                runOnUiThread { notice("Log di Kodi non leggibile", "Non risponde a $ip (${e.message}).\n\n$NO_ADB_HELP") }
                return@thread
            }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Log di Kodi")
                    .setMessage(log.ifBlank { "Il log è vuoto." })
                    .setPositiveButton("Condividi") { _, _ ->
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
                        startActivity(Intent.createChooser(send, "Condividi il log di Kodi"))
                    }
                    .setNegativeButton("Chiudi", null)
                    .show()
            }
        }
    }

    // Utente, password nascosta e "Mostra password", già compilati con login se c'è
    private fun loginFields(login: Pair<String, String>?): Triple<EditText, EditText, CheckBox> {
        val user = EditText(this).apply {
            hint = "Utente"
            isSingleLine = true
            setText(login?.first.orEmpty())
        }
        val hidden = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val password = EditText(this).apply {
            hint = "Password"
            isSingleLine = true
            inputType = hidden
            setText(login?.second.orEmpty())
        }
        val show = CheckBox(this).apply {
            text = "Mostra password"
            setOnCheckedChangeListener { _, checked ->
                password.inputType = if (checked) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else hidden
                password.setSelection(password.text.length)
            }
        }
        return Triple(user, password, show)
    }

    // Kodi ha risposto 401: si chiedono utente e password e con OK l'invio riparte da solo
    private fun askLogin(host: String, tried: Pair<String, String>?, wrong: Boolean, retry: (Pair<String, String>) -> Unit) {
        val (user, password, show) = loginFields(tried?.copy(second = ""))
        val message = if (wrong) "Kodi a $host non accetta utente e password." else "Kodi a $host chiede utente e password."
        val form = section(label(message), user, password, show).apply { setPadding(dp(24), dp(8), dp(24), 0) }
        AlertDialog.Builder(this)
            .setTitle("Accesso a Kodi")
            .setView(form)
            .setPositiveButton("OK") { _, _ -> retry(user.text.toString().trim() to password.text.toString()) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // IPv4 del telefono e lunghezza del prefisso della sua rete, per cercarci la Fire TV
    private fun localAddress(): Pair<String, Int>? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val link = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val address = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return (address.address.hostAddress ?: return null) to address.prefixLength
    }

    // Impronta della rete attuale (vedi networkKey), null senza rete o senza un gateway IPv4
    private fun currentNetwork(): String? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val link = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val address = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gateway = link.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway
        return networkKey(address.address.hostAddress, address.prefixLength, gateway?.hostAddress)
    }

    // Utente e password salvati per il Kodi a host sulla rete network, già decifrati
    private fun savedLogin(host: String, network: String?): Pair<String, String>? {
        val name = if (host == prefs.getString("host", "")) prefs.getString("name", "").orEmpty() else kodiNames[host].orEmpty()
        val login = loginFor(loadLogins(), host, name, network) ?: return null
        return login.user to (unseal(login.secret) ?: return null)
    }

    private fun storeLogin(host: String, network: String, login: Pair<String, String>) {
        val name = if (host == prefs.getString("host", "")) prefs.getString("name", "").orEmpty() else kodiNames[host].orEmpty()
        val entry = SavedLogin(host, name, network, login.first, seal(login.second))
        saveLogins(replaceLogin(loadLogins(), host, name, network, entry))
    }

    // Credenziali in SharedPreferences come JSON, con la password cifrata da seal
    private fun loadLogins(): List<SavedLogin> {
        val array = JSONArray(prefs.getString("logins", "[]"))
        return List(array.length()) { i ->
            array.getJSONObject(i).let {
                SavedLogin(it.getString("host"), it.getString("name"), it.getString("network"), it.getString("user"), it.getString("secret"))
            }
        }
    }

    private fun saveLogins(list: List<SavedLogin>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("host", it.host).put("name", it.name).put("network", it.network).put("user", it.user).put("secret", it.secret))
        }
        prefs.edit().putString("logins", array.toString()).apply()
    }

    // Il vecchio "utente:password" unico vale per il Kodi salvato, su qualunque rete finché non funziona di nuovo
    private fun migrateAuth() {
        val auth = prefs.getString("auth", "").orEmpty()
        val host = prefs.getString("host", "").orEmpty()
        if (auth.isNotBlank() && host.isNotBlank()) {
            val (user, password) = splitAuth(auth)
            saveLogins(loadLogins() + SavedLogin(host, "", "", user, seal(password)))
        }
        prefs.edit().remove("auth").apply()
    }

    // Password cifrate con una chiave AES dell'Android Keystore, che non esce dal telefono: "iv:testo" in Base64
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

    private fun seal(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, loginKey()) }
        val data = cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    // null se non si decifra (chiave persa, es. backup ripristinato su un altro telefono): si richiedono
    private fun unseal(secret: String): String? = try {
        val (iv, data) = secret.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, loginKey(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(data))
    } catch (e: Exception) {
        null
    }

    private fun section(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, dp(4), 0, dp(4))
    }

    // Preferiti in SharedPreferences come JSON: [{"title": ..., "url": ..., "icon": PNG in Base64, se c'è}]
    private fun loadFavorites(): MutableList<Favorite> {
        val array = JSONArray(prefs.getString("favorites", "[]"))
        return MutableList(array.length()) { i ->
            array.getJSONObject(i).let { Favorite(it.getString("title"), it.getString("url"), it.optString("icon").ifEmpty { null }) }
        }
    }

    private fun saveFavorites(list: List<Favorite>) {
        val array = JSONArray(list.map { JSONObject().put("title", it.title).put("url", it.url).putOpt("icon", it.icon) })
        prefs.edit().putString("favorites", array.toString()).apply()
    }

    private fun encodeIcon(icon: Bitmap): String {
        val small = if (icon.width > 64 || icon.height > 64) Bitmap.createScaledBitmap(icon, 64, 64, true) else icon
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // I preferiti salvati senza icona (prima di #25, o pagina non ancora caricata) la prendono alla prossima visita
    private fun fillMissingIcon(url: String?, icon: Bitmap) {
        val list = loadFavorites()
        val i = list.indexOfFirst { it.url == url && it.icon == null }
        if (i < 0) return
        list[i] = list[i].copy(icon = encodeIcon(icon))
        saveFavorites(list)
    }

    // Lista dei preferiti: in cima il pulsante per aggiungere la pagina aperta, poi una riga per sito.
    // Tocco apre, ⋮ (o pressione lunga) per rinominare o togliere
    private fun showFavorites() {
        val list = loadFavorites()
        val current = web.url?.takeIf { it.startsWith("http") }
        val canAdd = current != null && list.none { it.url == current }
        if (list.isEmpty() && !canAdd) return toast("Apri un sito per aggiungerlo ai preferiti")

        lateinit var dialog: AlertDialog
        val content = section().apply { setPadding(dp(16), dp(8), dp(8), 0) }
        if (canAdd) {
            val add = Button(this).apply {
                text = "＋ Aggiungi questa pagina"
                setOnClickListener { dialog.dismiss(); addFavorite(current!!) }
            }
            paint(add, active = true, primary = true)
            content.addView(add, LinearLayout.LayoutParams(-1, dp(48)).apply { marginEnd = dp(8); bottomMargin = dp(8) })
        }
        if (list.isEmpty()) content.addView(label("Nessun preferito: aggiungi la pagina che hai aperto."))
        list.forEachIndexed { i, favorite ->
            content.addView(favoriteRow(favorite, onOpen = { dialog.dismiss(); open(favorite.url) }) { anchor ->
                favoriteMenu(anchor, favorite) { changed ->
                    if (changed == null) list.removeAt(i) else list[i] = changed
                    saveFavorites(list)
                    dialog.dismiss()
                    showFavorites()
                }
            })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Preferiti")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun addFavorite(url: String) {
        val icon = pageIcon?.let { encodeIcon(it) }
        askFavoriteName("Aggiungi ai preferiti", suggestedName(web.title, Uri.parse(url).host)) { name ->
            saveFavorites(loadFavorites() + Favorite(name, url, icon))
            toast("Aggiunto ai preferiti")
        }
    }

    // Riga di un preferito: icona del sito (o iniziale su un cerchio), nome, dominio più tenue e ⋮
    private fun favoriteRow(favorite: Favorite, onOpen: () -> Unit, onMenu: (View) -> Unit): View {
        val host = Uri.parse(favorite.url).host
        val bitmap = favorite.icon
            ?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val icon: View = if (bitmap != null) {
            ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = circle(sys(R.color.system_neutral2_100, R.color.system_neutral2_800))
                clipToOutline = true
            }
        } else {
            TextView(this).apply {
                text = initialOf(favorite.title, host)
                gravity = Gravity.CENTER
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(sys(R.color.system_accent1_900, R.color.system_accent1_100))
                background = circle(sys(R.color.system_accent1_100, R.color.system_accent1_700))
            }
        }
        val name = TextView(this).apply {
            text = favorite.title
            textSize = 16f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
        }
        val domain = TextView(this).apply {
            text = shortHost(host)
            textSize = 13f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(sys(R.color.system_neutral2_600, R.color.system_neutral2_400))
        }
        val more = Button(this, null, R.attr.borderlessButtonStyle).apply {
            text = "⋮"
            textSize = 20f
            contentDescription = "Rinomina o togli"
            minWidth = dp(48)
            minimumWidth = dp(48)
            setTextColor(sys(R.color.system_neutral2_700, R.color.system_neutral2_200))
        }
        more.setOnClickListener { onMenu(more) }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(4), dp(4), 0, dp(4))
            setBackgroundResource(attr(R.attr.selectableItemBackground))
            setOnClickListener { onOpen() }
            setOnLongClickListener { onMenu(more); true }
            addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(section(name, domain), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
            addView(more, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    // done riceve il preferito rinominato, o null se va tolto
    private fun favoriteMenu(anchor: View, favorite: Favorite, done: (Favorite?) -> Unit) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Rinomina")
            menu.add(0, 2, 1, "Togli")
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    askFavoriteName("Rinomina", favorite.title) { done(favorite.copy(title = it)) }
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Togliere \"${favorite.title}\" dai preferiti?")
                        .setPositiveButton("Togli") { _, _ -> done(null) }
                        .setNegativeButton("Annulla", null)
                        .show()
                }
                true
            }
            show()
        }
    }

    // Campo già compilato con initial; lasciato vuoto si tiene initial
    private fun askFavoriteName(title: String, initial: String, done: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(initial)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSelectAllOnFocus(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(section(field).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Salva") { _, _ -> done(renamed(field.text.toString(), initial)) }
            .setNegativeButton("Annulla", null)
            .create()
        field.setOnEditorActionListener { _, _, _ ->
            done(renamed(field.text.toString(), initial))
            dialog.dismiss()
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        field.requestFocus()
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun attr(id: Int): Int = TypedValue().also { theme.resolveAttribute(id, it, true) }.resourceId

    // Per i messaggi lunghi o importanti: un toast sparisce prima di averlo letto
    private fun notice(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Il WebView salva i cookie su disco ogni tanto: se l'app viene chiusa prima si perde la scelta sulla pagina
    // dei cookie di Google (e i login dei siti), che si ripresenterebbe a ogni avvio
    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
