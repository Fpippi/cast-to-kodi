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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Message
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
import it.franc.casttokodi.sites.DEFAULT_AUTO_NEXT
import it.franc.casttokodi.sites.MAX_AUTO_NEXT
import it.franc.casttokodi.sites.NEXT_EPISODES_JS
import it.franc.casttokodi.sites.isAnimeUnityEpisode
import it.franc.casttokodi.sites.isGoogleConsent
import it.franc.casttokodi.sites.nextEpisodeTitle
import it.franc.casttokodi.sites.linkExpiry
import kotlin.concurrent.thread

private const val REPO = "Fpippi/cast-to-kodi"

private const val NOT_FOUND_HELP = """Nessun Kodi trovato. Controlla che:

1. Kodi sia aperto sulla Fire TV;
2. in Kodi sia attivo Impostazioni → Servizi → Generale → "Annuncia servizi ad altri sistemi";
3. telefono e Fire TV siano sullo stesso Wi-Fi.

Oppure passa a "Manuale" e scrivi IP:porta."""

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

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var cast: Button
    private lateinit var queue: Button
    private lateinit var progress: ProgressBar
    private val prefs by lazy { getSharedPreferences("kodi", MODE_PRIVATE) }

    // url del video -> Referer con cui la pagina l'ha richiesto (molti host rifiutano richieste senza)
    private val videos = LinkedHashMap<String, String>()
    // Link per Kodi, con playlist e chiavi passate dal telefono (kodiItem con viaPhone)
    private val streams = Streams(Relay()) { localAddress()?.first }

    // Dominio da cui la pagina principale può navigare: tutto il resto è considerato redirect pubblicitario
    private var allowedDomain = ""
    @Volatile private var pageUrl = ""

    // "IP:porta" -> nome annunciato via mDNS, per riconoscere lo stesso Kodi quando cambia IP
    private val kodiNames = HashMap<String, String>()

    // Favicon della pagina aperta, salvata con il preferito
    private var pageIcon: Bitmap? = null

    // Mentre si cerca Kodi in rete lo dice il pulsante in basso: un toast sparisce prima di averlo letto
    private var searchingKodi = false
    // Un invio alla volta: i tocchi ripetuti su "Manda a Kodi" mentre Kodi risponde non mandano altri video
    private var sending = false
    // App in primo piano: con il telefono bloccato il telecomando smette di chiedere a Kodi a che punto è
    @Volatile private var resumed = false

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
            setOnClickListener { kodiMenu(this) }
        }
        val power = Button(this).apply {
            contentDescription = "Accendi la TV e apri Kodi"
            tooltipText = contentDescription
            setOnClickListener { wakeKodi() }
        }
        paint(favorites, active = true, primary = false)
        paint(power, active = true, primary = false)
        // Il carattere ⏻ manca nel font del Pixel (si vedeva un quadratino): icona vettoriale disegnata sopra, al centro,
        // del colore del testo. Il testo resta uno spazio, così altezza e linea di base sono quelle di ★ e Kodi
        // (con l'icona dentro il testo, in un ImageSpan, il pulsante usciva più alto e spostato)
        power.text = " "
        power.foreground = getDrawable(it.franc.casttokodi.R.drawable.ic_power)!!.mutate().apply { setTint(power.currentTextColor) }
        power.foregroundGravity = Gravity.CENTER
        paint(settings, active = true, primary = false)
        cast = Button(this).apply {
            setOnClickListener { pickVideo(queued = false) }
        }
        queue = Button(this).apply {
            text = "+ Coda"
            setOnClickListener { pickVideo(queued = true) }
            // Pressione lunga: apre la coda di Kodi
            setOnLongClickListener { showQueue(); true }
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
        migrateAutoNext()
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
                // Niente avviso: compariva sopra a "Manda a Kodi" a ogni tocco sul player
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
                if (request.method == "GET" && isHlsPath(request.url.path)) return capturePlaylist(request)
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
    private fun playable(): List<Video> = videoChoices(videos.mapNotNull { (url, referer) ->
        val uri = Uri.parse(url)
        videoFor(url, referer, uri.host, uri.path, uri.encodedQuery)
    })

    private fun refreshCastButton() {
        val count = playable().size
        val ready = count > 0 && !searchingKodi && !sending
        cast.isEnabled = ready
        queue.isEnabled = ready
        cast.text = when {
            searchingKodi -> "Cerco Kodi in rete…"
            sending -> "Invio a Kodi…"
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
        lateinit var dialog: AlertDialog
        val content = section().apply { setPadding(dp(16), dp(8), dp(16), 0) }
        val userAgent = web.settings.userAgentString
        list.forEach { video ->
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(sys(R.color.system_neutral1_100, R.color.system_neutral1_800))
                }
                clipToOutline = true
            }
            val detail = TextView(this).apply {
                text = "Anteprima in caricamento…"
                textSize = 12f
            }
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = video.label; textSize = 15f })
                addView(detail)
            }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                setOnClickListener { dialog.dismiss(); sendToKodi(video, queued) }
                addView(image, LinearLayout.LayoutParams(dp(112), dp(63)).apply { marginEnd = dp(12) })
                addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            }
            content.addView(row)
            thread {
                val (seconds, frame) = videoPreview(video, userAgent)
                runOnUiThread {
                    frame?.let { image.setImageBitmap(it) }
                    detail.text = when {
                        seconds != null && frame == null -> "Durata ${durationLabel(seconds)}, nessuna immagine"
                        seconds != null -> "Durata ${durationLabel(seconds)}"
                        frame != null -> "Durata sconosciuta"
                        else -> "Anteprima non disponibile"
                    }
                }
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(if (queued) "Quale video mettere in coda? Di solito è il primo" else "Quale video? Di solito è il primo")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Durata in secondi e un fotogramma piccolo, per riconoscere l'episodio da un trailer o da una pubblicità.
    // Da un thread di background. MP4: li legge MediaMetadataRetriever dal sito. HLS: Streams.preview scarica un pezzo
    // della qualità più leggera (già decifrato) e il fotogramma si prende da lì. Video protetti: niente immagine.
    private fun videoPreview(video: Video, userAgent: String): Pair<Long?, Bitmap?> {
        val headers = streamHeaders(video.url, video.referer, userAgent)
        val retriever = MediaMetadataRetriever()
        var file: File? = null
        var seconds: Long? = null
        return try {
            if (Uri.parse(video.url).path.orEmpty().lowercase().endsWith(".mp4")) {
                retriever.setDataSource(video.url, headers.toMap())
            } else {
                val preview = streams.preview(video.url, headers)
                seconds = preview.seconds
                val sample = preview.sample ?: return seconds to null
                file = File.createTempFile("anteprima", ".ts", cacheDir).apply { writeBytes(sample) }
                retriever.setDataSource(file.path)
            }
            val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (seconds == null) seconds = length?.let { it / 1000 }
            // Per un MP4 al 10% (l'inizio spesso è nero), per un pezzo HLS a metà
            val at = if (file == null) minOf((length ?: 0) / 10, 90_000) else (length ?: 0) / 2
            seconds to retriever.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dp(224), dp(126))
        } catch (e: Exception) {
            seconds to null
        } finally {
            retriever.release()
            file?.delete()
        }
    }

    // Tutto passa dalla playlist video di Kodi, così "+ Coda" e gli episodi successivi seguono quello in corso.
    // La coda non si cancella mai: un video nuovo si inserisce dopo quello in corso o in fondo.
    // typed: utente e password appena scritti nel dialogo dopo un 401, al posto di quelli salvati
    private fun sendToKodi(video: Video, queued: Boolean, typed: Pair<String, String>? = null) {
        if (sending) return
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val network = currentNetwork()
        val login = typed ?: savedLogin(host, network)
        val kodi = Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
        val userAgent = web.settings.userAgentString
        val page = pageUrl
        val title = suggestedName(web.title, Uri.parse(page).host)
        sending = true
        refreshCastButton()
        // Il pulsante torna attivo prima di qualunque dialogo, così il nuovo tentativo che ne parte non viene scartato
        val done = { then: () -> Unit ->
            runOnUiThread {
                sending = false
                refreshCastButton()
                then()
            }
        }

        thread {
            val sent = try {
                if (queued) kodi.enqueue({ kodiItem(video.url, video.referer, userAgent, title, viaPhone = it) }) { expiryProblem(video.url, it) }
                else kodi.playNow(kodiItem(video.url, video.referer, userAgent, title, viaPhone = true))
            } catch (e: IOException) {
                done { relocateKodi(host) { sendToKodi(video, queued, typed) } }
                return@thread
            } catch (e: Unauthorized) {
                done { askLogin(host, login, wrong = login != null) { sendToKodi(video, queued, it) } }
                return@thread
            } catch (e: Refused) {
                done { notice("Non messo in coda", e.message.orEmpty()) }
                return@thread
            } catch (e: KodiError) {
                done { notice("Kodi non ha accettato il video", e.message.orEmpty()) }
                return@thread
            } catch (e: Exception) {
                done { notice("Errore", e.message.orEmpty()) }
                return@thread
            }
            done {
                // Hanno funzionato: valgono per questo Kodi su questa rete (anche le vecchie senza rete, o dopo un cambio di IP)
                if (login != null && network != null) storeLogin(host, network, login)
                toast(sent.message)
                sent.position?.let { queueNextEpisodes(kodi, page, it, userAgent, title) }
            }
        }
    }

    // VixCloud scrive nel link la scadenza del token (expires=<secondi unix>): un episodio che parte dopo non si vede
    private fun expiryProblem(url: String, startsIn: Long): String? {
        val expires = linkExpiry(Uri.parse(url).encodedQuery) ?: return null
        val startsAt = System.currentTimeMillis() / 1000 + startsIn
        if (startsAt + EXPIRY_MARGIN < expires) return null
        val time = DateFormat.getTimeFormat(this).format(Date(expires * 1000))
        return "Il link di questo episodio scade alle $time, prima che tocchi a lui: mettilo in coda più tardi."
    }

    // Il video per Kodi (vedi Streams.kodiFile), da un thread di background. title è il nome da mostrare nella coda (vedi queueLabel)
    private fun kodiItem(url: String, referer: String, userAgent: String, title: String, viaPhone: Boolean = false): JSONObject {
        val file = streams.kodiFile(url, streamHeaders(url, referer, userAgent), viaPhone)
        rememberTitle(queueKey(file), title)
        return JSONObject().put("file", file)
    }

    // Gli header che manderebbe il browser: oltre a Referer e User-Agent, l'Origin della pagina del player quando il
    // video sta su un altro dominio e i cookie del sito del video. Senza, alcuni CDN rispondono 403
    // (StreamingCommunity con il player vidxgo, 2026-10-21).
    private fun streamHeaders(url: String, referer: String, userAgent: String): List<Pair<String, String>> {
        val headers = mutableListOf("Referer" to referer, "User-Agent" to userAgent)
        val origin = originOf(referer)
        if (origin != null && origin != originOf(url)) headers += "Origin" to origin
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { headers += "Cookie" to it }
        return headers
    }

    // Alcuni player (StreamingCommunity con vidxgo, 2026-10-21) danno playlist con un token che vale una volta sola:
    // dopo il browser, il telefono e Kodi ricevono 403. Le playlist .m3u8 le scarica l'app al posto del WebView, con i
    // suoi header e cookie, e se ne tiene una copia per l'invio a Kodi. null (il WebView fa da sé) se qualcosa va storto.
    private fun capturePlaylist(request: WebResourceRequest): WebResourceResponse? = try {
        val url = request.url.toString()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        // Accept-Encoding no: così il corpo arriva già decompresso
        request.requestHeaders.forEach { (name, value) -> if (!name.equals("Accept-Encoding", true)) conn.setRequestProperty(name, value) }
        val referer = request.requestHeaders["Referer"] ?: pageUrl
        val origin = originOf(referer)
        if (origin != null && origin != originOf(url) && request.requestHeaders.keys.none { it.equals("Origin", true) }) {
            conn.setRequestProperty("Origin", origin)
        }
        val cookies = CookieManager.getInstance()
        cookies.getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
        val code = conn.responseCode
        val body = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        conn.headerFields["Set-Cookie"]?.forEach { cookies.setCookie(url, it) }
        if (code in 200..299) streams.capture(url, body, conn.url.toString())
        WebResourceResponse(
            conn.contentType?.substringBefore(';')?.trim() ?: "application/vnd.apple.mpegurl", "utf-8", code,
            conn.responseMessage?.takeIf { it.isNotBlank() } ?: "OK", forwardedHeaders(conn.headerFields, origin),
            ByteArrayInputStream(body)
        )
    } catch (e: Exception) {
        null
    }

    // AnimeUnity: dopo l'invio si accodano da soli gli N episodi successivi, subito dopo quello mandato.
    // L'elenco degli episodi e il player di ognuno si leggono dalla pagina del sito, il link video dal player.
    private fun queueNextEpisodes(kodi: Kodi, page: String, after: Int, userAgent: String, title: String) {
        val count = prefs.getInt("autoNext", DEFAULT_AUTO_NEXT)
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
                            kodi.insertAt(after + 1 + i, { kodiItem(stream, embed, userAgent, nextEpisodeTitle(title, number)) }) { expiryProblem(stream, it) }
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
            setText(prefs.getInt("autoNext", DEFAULT_AUTO_NEXT).toString())
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
                    .putInt("autoNext", autoNext.text.toString().toIntOrNull()?.coerceIn(0, MAX_AUTO_NEXT) ?: DEFAULT_AUTO_NEXT)
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
            .show()
    }

    // Il pulsante Kodi apre un menu: la coda si guarda spesso, le impostazioni e il log ogni tanto
    private fun kodiMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 4, 0, "Telecomando")
            menu.add(0, 1, 1, "Coda di Kodi")
            menu.add(0, 2, 2, "Dov'è Kodi?")
            menu.add(0, 3, 3, "Log di Kodi")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showQueue()
                    2 -> askKodiAddress()
                    4 -> showRemote()
                    else -> showKodiLog()
                }
                true
            }
            show()
        }
    }

    // Chiamate a Kodi fuori dall'invio (la coda), con gli stessi ripieghi di sendToKodi: Kodi ritrovato in rete se
    // non risponde, credenziali chieste al 401. work gira in background, done sul thread UI.
    private fun <T> onKodi(work: (Kodi) -> T, done: (T) -> Unit, typed: Pair<String, String>? = null) {
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val network = currentNetwork()
        val login = typed ?: savedLogin(host, network)
        val kodi = Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
        thread {
            val result = try {
                work(kodi)
            } catch (e: IOException) {
                runOnUiThread { relocateKodi(host) { onKodi(work, done) } }
                return@thread
            } catch (e: Unauthorized) {
                runOnUiThread { askLogin(host, login, wrong = login != null) { onKodi(work, done, it) } }
                return@thread
            } catch (e: Exception) {
                runOnUiThread { notice("Kodi non ha risposto", e.message.orEmpty()) }
                return@thread
            }
            runOnUiThread {
                if (login != null && network != null) storeLogin(host, network, login)
                done(result)
            }
        }
    }

    // La playlist video di Kodi, cioè la coda: cosa c'è, cosa sta andando e quando parte il resto.
    // Tocco su un video: riproduci ora o togli. Quello in corso non si tocca (Kodi non lo toglie).
    private fun showQueue() {
        onKodi({ kodi ->
            val (state, items) = kodi.queue()
            Triple(state, items, kodi.playingOutside(state))
        }, { (state, items, outside) ->
            val titles = loadTitles()
            val current = if (state.fromQueue) state.position else -1
            lateinit var dialog: AlertDialog
            val content = section().apply { setPadding(dp(16), dp(8), dp(8), 0) }
            content.addView(label(when {
                outside != null && items.isEmpty() ->
                    "Kodi sta riproducendo un video partito fuori dalla coda, e la coda è vuota. \"+ Coda\" non può accodare dopo " +
                        "questo video: usa \"Manda a Kodi\" sull'episodio e da lì accoda gli altri."
                outside != null -> "Kodi sta riproducendo un video partito fuori dalla coda: quando finisce non passa a questi. Tocca un video per farlo partire."
                items.isEmpty() -> "La coda è vuota. \"Manda a Kodi\" fa partire un video, \"+ Coda\" lo mette in fondo."
                state.playerId == null -> "Kodi è fermo: tocca un video per farlo partire da lì."
                else -> "Quando un video finisce Kodi passa al successivo."
            }).apply { setPadding(0, 0, dp(8), dp(8)) })
            outside?.let { item ->
                val name = queueLabel(item.optString("file"), item.optString("title").ifBlank { item.optString("label") }, titles)
                content.addView(queueRow(0, name, "▶ In riproduzione, fuori dalla coda", playing = true, done = false) {})
            }
            items.forEachIndexed { i, item ->
                val file = item.optString("file")
                val name = queueLabel(file, item.optString("title").ifBlank { item.optString("label") }, titles)
                val playing = i == current
                content.addView(queueRow(i + 1, name, queueNote(state, i, file), playing, done = current >= 0 && i < current) { anchor ->
                    queueMenu(anchor, name, state, i) { dialog.dismiss() }
                })
            }
            val builder = AlertDialog.Builder(this)
                .setTitle("Coda di Kodi")
                .setView(ScrollView(this).apply { addView(content) })
                .setPositiveButton("Aggiorna") { _, _ -> showQueue() }
                .setNegativeButton("Chiudi", null)
            if (items.size > (if (current >= 0) 1 else 0)) builder.setNeutralButton("Svuota") { _, _ -> clearQueue(current) }
            dialog = builder.show()
        })
    }

    // Sotto il nome: a che punto è, quando parte (stima con episodi lunghi come quello in corso) o se il link è scaduto
    private fun queueNote(state: KodiState, index: Int, file: String): String {
        val now = System.currentTimeMillis() / 1000
        val expires = linkExpiry(Uri.parse(queueKey(file)).encodedQuery)
        val playing = state.fromQueue && index == state.position
        return when {
            playing && state.playerId != null -> "▶ In riproduzione"
            state.fromQueue && index < state.position -> "Già visto"
            expires != null && expires < now -> "Link scaduto: rimandalo dalla pagina dell'episodio"
            !state.fromQueue -> "In coda"
            else -> {
                val minutes = (state.secondsUntil(index) + 59) / 60
                val warning = if (expires != null && now + state.secondsUntil(index) + EXPIRY_MARGIN >= expires) " · il link scade prima" else ""
                "Parte tra circa $minutes min$warning"
            }
        }
    }

    // Riga della coda: numero (▶ per quello in corso), nome e nota; i già visti più tenui
    private fun queueRow(number: Int, name: String, note: String, playing: Boolean, done: Boolean, onTap: (View) -> Unit): View {
        val badge = TextView(this).apply {
            text = if (playing) "▶" else number.toString()
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (playing) sys(R.color.system_accent1_0, R.color.system_accent1_800) else sys(R.color.system_accent1_900, R.color.system_accent1_100))
            background = circle(if (playing) sys(R.color.system_accent1_600, R.color.system_accent1_200) else sys(R.color.system_accent1_100, R.color.system_accent1_700))
        }
        val title = TextView(this).apply {
            text = name
            textSize = 16f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            if (playing) typeface = Typeface.DEFAULT_BOLD
            setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
        }
        val detail = TextView(this).apply {
            text = note
            textSize = 13f
            setTextColor(sys(R.color.system_neutral2_600, R.color.system_neutral2_400))
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(4), dp(4), dp(8), dp(4))
            if (done) alpha = 0.6f
            if (!playing) {
                setBackgroundResource(attr(R.attr.selectableItemBackground))
                setOnClickListener { onTap(this) }
            }
            addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(section(title, detail), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        }
    }

    private fun queueMenu(anchor: View, name: String, state: KodiState, index: Int, close: () -> Unit) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Riproduci ora")
            menu.add(0, 2, 1, "Togli dalla coda")
            setOnMenuItemClickListener { item ->
                close()
                if (item.itemId == 1) {
                    onKodi({ kodi -> kodi.playFrom(state, index) }, { toast("Parte: $name") })
                } else {
                    onKodi({ kodi -> kodi.remove(index) }, { showQueue() })
                }
                true
            }
            show()
        }
    }

    // Telecomando di Kodi in un dialogo (Kodi → Telecomando)
    private fun showRemote() {
        if (prefs.getString("host", "").isNullOrBlank()) return askKodiAddress()
        AlertDialog.Builder(this)
            .setTitle("Telecomando")
            .setView(ScrollView(this).apply { addView(remoteView()) })
            .setNegativeButton("Chiudi", null)
            .show()
    }

    // Il telecomando, da solo: chi lo mostra (il dialogo qui sopra o un pannello) mette solo la View.
    // Finché la View è attaccata alla finestra e l'app è in primo piano, rilegge da Kodi il video in corso ogni secondo.
    // Il telecomando della TV salta dei passi di Kodi (su/giù 10 minuti): qui i salti sono quelli scritti sul pulsante.
    private fun remoteView(): View {
        var now: NowPlaying? = null
        var dragging = false
        var generation = 0
        val title = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
        }
        val status = TextView(this).apply {
            textSize = 13f
            text = "Chiedo a Kodi…"
            setTextColor(sys(R.color.system_neutral2_600, R.color.system_neutral2_400))
        }
        val elapsed = TextView(this).apply { textSize = 13f }
        val total = TextView(this).apply { textSize = 13f; gravity = Gravity.END }
        val volume = TextView(this).apply { textSize = 13f; gravity = Gravity.CENTER }

        // Comando sul video in corso; poi si rilegge subito, così il telecomando non resta indietro di un secondo
        lateinit var refresh: () -> Unit
        fun video(action: (Kodi, Int) -> Unit) {
            val id = now?.playerId ?: return toast("Kodi non sta riproducendo niente")
            onKodi({ action(it, id) }, { refresh() })
        }
        fun kodi(action: (Kodi) -> Unit) = onKodi({ action(it) }, { refresh() })

        val bar = SeekBar(this).apply {
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) elapsed.text = clockLabel(progress.toLong())
                }
                override fun onStartTrackingTouch(bar: SeekBar) {
                    dragging = true
                }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    dragging = false
                    video { kodi, id -> kodi.seekTo(id, bar.progress.toLong()) }
                }
            })
        }
        val playPause = remoteIcon(it.franc.casttokodi.R.drawable.ic_pause, "Play o pausa", primary = true) {
            video { kodi, id -> kodi.playPause(id) }
        }
        val mute = remoteIcon(it.franc.casttokodi.R.drawable.ic_volume_off, "Muto") { kodi { it.toggleMute() } }

        fun show(playing: NowPlaying?, problem: String?) {
            now = playing
            if (playing == null) {
                status.text = problem
                return
            }
            volume.text = if (playing.muted) "Muto" else "Volume ${playing.volume}%"
            mute.alpha = if (playing.muted) 1f else 0.6f
            if (playing.playerId == null) {
                title.text = "Niente in riproduzione"
                status.text = "Kodi è fermo"
                bar.isEnabled = false
                bar.progress = 0
                elapsed.text = clockLabel(0)
                total.text = clockLabel(0)
                return
            }
            title.text = queueLabel(playing.file, playing.title, loadTitles())
            status.text = if (playing.paused) "In pausa" else "In riproduzione"
            setRemoteIcon(playPause, if (playing.paused) it.franc.casttokodi.R.drawable.ic_play else it.franc.casttokodi.R.drawable.ic_pause)
            bar.isEnabled = playing.total > 0
            bar.max = playing.total.toInt().coerceAtLeast(1)
            if (!dragging) {
                bar.progress = playing.time.toInt()
                elapsed.text = clockLabel(playing.time)
            }
            total.text = clockLabel(playing.total)
        }

        // In background: il video in corso, o perché non si sa
        fun read(): Pair<NowPlaying?, String?> {
            val host = prefs.getString("host", "").orEmpty()
            val login = savedLogin(host, currentNetwork())
            return try {
                Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty()).nowPlaying() to null
            } catch (e: Unauthorized) {
                null to "Kodi chiede utente e password: premi un pulsante per inserirli"
            } catch (e: Exception) {
                null to "Kodi non risponde. Se la TV è spenta, accendila con ⏻ qui sotto"
            }
        }
        refresh = { thread { read().let { (playing, problem) -> runOnUiThread { show(playing, problem) } } } }

        val skips = listOf(-30, -10, 10, 30).map { seconds ->
            remoteText(if (seconds < 0) "−${-seconds} s" else "+$seconds s") { video { kodi, id -> kodi.skip(id, seconds) } }
        }
        val transport = listOf(
            remoteIcon(it.franc.casttokodi.R.drawable.ic_skip_previous, "Video precedente della coda") { video { kodi, id -> kodi.previous(id) } },
            playPause,
            remoteIcon(it.franc.casttokodi.R.drawable.ic_stop, "Ferma il video") {
                if (now?.playerId == null) return@remoteIcon toast("Kodi non sta riproducendo niente")
                AlertDialog.Builder(this)
                    .setMessage("Fermare il video su Kodi? La coda resta.")
                    .setPositiveButton("Ferma") { _, _ -> video { kodi, id -> kodi.stop(id) } }
                    .setNegativeButton("Annulla", null)
                    .show()
            },
            remoteIcon(it.franc.casttokodi.R.drawable.ic_skip_next, "Video successivo della coda") { video { kodi, id -> kodi.next(id) } },
        )
        val options = listOf(
            remoteText("Vai al minuto") { now?.let { askSeek(it) { seconds -> video { kodi, id -> kodi.seekTo(id, seconds) } } } },
            remoteText("Velocità") {
                val id = now?.playerId ?: return@remoteText toast("Kodi non sta riproducendo niente")
                chooseTempo { tempo -> tempoAt(tempo, id) }
            },
        )
        val tracks = listOf(
            remoteText("Audio") { now?.takeIf { it.playerId != null }?.let { chooseAudio(it) { track -> video { kodi, id -> kodi.setAudio(id, track) } } } },
            remoteText("Sottotitoli") { now?.takeIf { it.playerId != null }?.let { chooseSubtitle(it) { track -> video { kodi, id -> kodi.setSubtitle(id, track) } } } },
        )
        fun key(icon: Int, description: String, name: String) = remoteIcon(icon, description) { kodi { it.press(name) } }
        val ok = remoteText("OK") { kodi { it.press("Select") } }
        val pad = section(
            padRow(key(it.franc.casttokodi.R.drawable.ic_arrow_up, "Su", "Up")),
            padRow(key(it.franc.casttokodi.R.drawable.ic_arrow_left, "Sinistra", "Left"), ok, key(it.franc.casttokodi.R.drawable.ic_arrow_right, "Destra", "Right")),
            padRow(key(it.franc.casttokodi.R.drawable.ic_arrow_down, "Giù", "Down")),
        )
        val menus = listOf(
            remoteText("Indietro") { kodi { it.press("Back") } },
            remoteText("Home") { kodi { it.press("Home") } },
            remoteText("Menu") { kodi { it.press("ContextMenu") } },
            remoteText("Scrivi") { askText { text -> kodi { it.sendText(text) } } },
        )
        val sound = listOf(
            remoteIcon(it.franc.casttokodi.R.drawable.ic_volume_down, "Volume giù") { kodi { it.volume(up = false) } },
            mute,
            remoteIcon(it.franc.casttokodi.R.drawable.ic_volume_up, "Volume su") { kodi { it.volume(up = true) } },
        )
        val power = remoteText("Accendi la TV e Kodi") { wakeKodi() }

        val times = LinearLayout(this).apply {
            addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
            addView(total, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val hint = label("Durante il video le frecce di Kodi saltano avanti e indietro (su e giù di 10 minuti): per spostarti usa la barra o i salti.").apply {
            textSize = 12f
            setTextColor(sys(R.color.system_neutral2_600, R.color.system_neutral2_400))
        }
        return section(
            title, status, bar, times,
            remoteRow(skips), remoteRow(transport), remoteRow(options), remoteRow(tracks),
            pad, hint, remoteRow(menus), remoteRow(sound), volume, remoteRow(listOf(power)),
        ).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val mine = ++generation
                    thread {
                        while (mine == generation) {
                            if (resumed) read().let { (playing, problem) -> runOnUiThread { if (mine == generation) show(playing, problem) } }
                            Thread.sleep(1000)
                        }
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {
                    generation++
                }
            })
        }
    }

    // Pulsante del telecomando con un'icona al centro, del colore del testo (come ⏻)
    private fun remoteIcon(icon: Int, description: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = " "
        contentDescription = description
        tooltipText = description
        paint(this, active = true, primary = primary)
        setRemoteIcon(this, icon)
        foregroundGravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun setRemoteIcon(button: Button, icon: Int) {
        button.foreground = getDrawable(icon)!!.mutate().apply { setTint(button.currentTextColor) }
    }

    private fun remoteText(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        paint(this, active = true, primary = false)
        setOnClickListener { onClick() }
    }

    // Pulsanti in fila, larghi uguali
    private fun remoteRow(buttons: List<View>) = LinearLayout(this).apply {
        buttons.forEach { addView(it, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(2); marginEnd = dp(2) }) }
    }

    // Una fila della croce: pulsanti quadrati al centro
    private fun padRow(vararg buttons: View) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        buttons.forEach { addView(it, LinearLayout.LayoutParams(dp(72), dp(64)).apply { marginStart = dp(4); marginEnd = dp(4) }) }
    }

    // "Vai al minuto": 12:30, 1:02:30 o solo i minuti
    private fun askSeek(playing: NowPlaying, done: (Long) -> Unit) {
        if (playing.playerId == null) return toast("Kodi non sta riproducendo niente")
        val field = EditText(this).apply {
            hint = "es. 12:30"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        lateinit var dialog: AlertDialog
        val go = {
            val seconds = parseClock(field.text.toString())
            if (seconds == null) toast("Scrivi il tempo come 12:30 (minuti:secondi) o 1:02:30")
            else {
                done(seconds)
                dialog.dismiss()
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Vai al minuto")
            .setMessage(if (playing.total > 0) "Il video dura ${clockLabel(playing.total)}." else null)
            .setView(section(field).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Vai", null)
            .setNegativeButton("Annulla", null)
            .create()
        field.setOnEditorActionListener { _, _, _ -> go(); true }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        // Con un tempo sbagliato il dialogo resta aperto
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { go() }
        field.requestFocus()
    }

    private fun chooseTempo(done: (Double) -> Unit) {
        val tempos = listOf(1.0, 1.25, 1.5)
        AlertDialog.Builder(this)
            .setTitle("Velocità")
            .setItems(arrayOf("Normale", "1,25x", "1,5x")) { _, i -> done(tempos[i]) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Kodi rifiuta la velocità con alcuni video: lo si spiega invece del messaggio tecnico
    private fun tempoAt(tempo: Double, id: Int) {
        onKodi({ kodi ->
            try {
                kodi.setTempo(id, tempo)
                null
            } catch (e: KodiError) {
                e.message
            }
        }, { problem ->
            if (problem == null) toast(if (tempo == 1.0) "Velocità normale" else "Velocità ${tempo.toString().replace('.', ',')}x")
            else notice("Velocità non cambiata", "Kodi non cambia la velocità di questo video ($problem). Serve Kodi 19 o più recente, e con alcuni video o impostazioni audio Kodi non lo permette.")
        })
    }

    private fun chooseAudio(playing: NowPlaying, done: (Track) -> Unit) {
        val labels = playing.audio.mapIndexed { i, t -> trackLabel(t.language, t.name, i + 1) }
        if (labels.size < 2) {
            return notice("Audio", if (labels.isEmpty()) "Kodi non vede tracce audio in questo video." else "Questo video ha una sola traccia audio: ${labels[0]}.")
        }
        AlertDialog.Builder(this)
            .setTitle("Lingua dell'audio")
            .setSingleChoiceItems(labels.toTypedArray(), playing.currentAudio) { dialog, i ->
                dialog.dismiss()
                done(playing.audio[i])
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // done(null): sottotitoli spenti
    private fun chooseSubtitle(playing: NowPlaying, done: (Track?) -> Unit) {
        if (playing.subtitles.isEmpty()) return notice("Sottotitoli", "Questo video non ha sottotitoli.")
        val labels = listOf("Spenti") + playing.subtitles.mapIndexed { i, t -> trackLabel(t.language, t.name, i + 1) }
        AlertDialog.Builder(this)
            .setTitle("Sottotitoli")
            .setSingleChoiceItems(labels.toTypedArray(), if (playing.subtitlesOn) playing.currentSubtitle + 1 else 0) { dialog, i ->
                dialog.dismiss()
                done(if (i == 0) null else playing.subtitles[i - 1])
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Testo per il campo che Kodi ha aperto (la ricerca, un nome): si scrive con la tastiera del telefono
    private fun askText(done: (String) -> Unit) {
        val field = EditText(this).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Scrivi su Kodi")
            .setMessage("Prima apri in Kodi il campo dove scrivere, per esempio la ricerca.")
            .setView(section(field).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Manda") { _, _ -> done(field.text.toString()) }
            .setNegativeButton("Annulla", null)
            .create()
        field.setOnEditorActionListener { _, _, _ ->
            done(field.text.toString())
            dialog.dismiss()
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        field.requestFocus()
    }

    // Toglie tutto tranne il video in corso (vedi Kodi.clearQueue)
    private fun clearQueue(current: Int) {
        AlertDialog.Builder(this)
            .setMessage(if (current >= 0) "Togliere dalla coda tutti i video tranne quello in corso?" else "Togliere tutti i video dalla coda?")
            .setPositiveButton("Svuota") { _, _ ->
                onKodi({ kodi -> kodi.clearQueue(current) }, { showQueue() })
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Nomi dei video mandati a Kodi (link senza header -> nome della pagina), per mostrarli nella coda
    private fun loadTitles(): Map<String, String> {
        val json = JSONObject(prefs.getString("queueTitles", "{}")!!)
        return json.keys().asSequence().associateWithTo(LinkedHashMap()) { json.getString(it) }
    }

    // Da più thread (invio e accodamento automatico): uno alla volta
    @Synchronized
    private fun rememberTitle(file: String, title: String) {
        prefs.edit().putString("queueTitles", JSONObject(withTitle(loadTitles(), file, title)).toString()).apply()
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

    // Fino alla 1.2.1 gli episodi automatici partivano da 1 e il dialogo "Dov'è Kodi?" salvava quel valore anche senza
    // toccarlo: una volta sola si riportano a spenti, poi resta quello che si sceglie
    private fun migrateAutoNext() {
        if (prefs.getBoolean("autoNextMigrated", false)) return
        prefs.edit().putInt("autoNext", DEFAULT_AUTO_NEXT).putBoolean("autoNextMigrated", true).apply()
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
        resumed = false
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
