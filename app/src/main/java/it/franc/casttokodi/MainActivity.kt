package it.franc.casttokodi

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.service.quicksettings.TileService
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.Window
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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
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
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import it.franc.casttokodi.sites.MASTER_PLAYLIST_JS
import it.franc.casttokodi.sites.DEFAULT_AUTO_NEXT
import it.franc.casttokodi.sites.MAX_AUTO_NEXT
import it.franc.casttokodi.sites.autoNextLabel
import it.franc.casttokodi.sites.NEXT_EPISODES_JS
import it.franc.casttokodi.sites.EPISODE_INFO_JS
import it.franc.casttokodi.sites.isAnimeUnityEpisode
import it.franc.casttokodi.sites.animeUnitySeries
import it.franc.casttokodi.sites.isGoogleConsent
import it.franc.casttokodi.sites.nextEpisodeTitle
import it.franc.casttokodi.sites.linkExpiry
import kotlin.concurrent.thread

private const val REPO = "Fpippi/cast-to-kodi"
private const val TUTORIAL = "https://github.com/$REPO/blob/main/docs/TUTORIAL.md"

private const val NOT_FOUND_HELP = """Nessun Kodi trovato. Controlla che:

1. Kodi sia aperto sulla Fire TV;
2. in Kodi sia attivo Impostazioni → Servizi → Generale → "Annuncia servizi ad altri sistemi";
3. telefono e Fire TV siano sullo stesso Wi-Fi.

Oppure passa a "Manuale" e scrivi IP:porta."""

// Dopo quanto chiudere la finestra finta data ai popup
private const val POPUP_SINK_MS = 10_000L

// Inizio del nome dei file delle copertine prese dal sito, che valgono più dei fotogrammi del video
// "copertina-" dalla 2.2.4: le copertine "cover-" della 2.2.3 erano salvate troppo piccole e così si riscaricano
private const val COVER_PREFIX = "copertina-"

// Scelta del file per Salva un backup e Ripristina (onActivityResult)
private const val REQUEST_BACKUP = 41
private const val REQUEST_RESTORE = 42

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
    private lateinit var queue: ImageButton
    private lateinit var castBar: View
    private lateinit var favorites: ImageButton
    private lateinit var siteIcon: ImageView
    private lateinit var shield: TextView
    private lateinit var snack: TextView
    // L'avviso del telecomando a schermo intero, mentre è aperto
    private var playerSnack: TextView? = null
    private lateinit var mini: View
    private lateinit var home: ScrollView
    private lateinit var homeContent: LinearLayout
    private lateinit var homeKodiTitle: TextView
    private lateinit var homeKodiStatus: TextView
    private lateinit var homeKodiButton: Button
    // Kodi ha risposto all'ultima lettura del mini-player? null = non ancora chiesto
    private var kodiReachable: Boolean? = null
    private lateinit var miniTitle: TextView
    private lateinit var miniStatus: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var miniThumb: ImageView
    private lateinit var miniProgress: ProgressBar
    // Video che Kodi sta riproducendo, letto dal mini-player; null = Kodi fermo o non raggiungibile
    private var miniNow: NowPlaying? = null
    // Ogni avvio del ciclo di lettura del mini-player ha il suo numero: in onPause si cambia e il ciclo vecchio si ferma
    @Volatile private var miniGeneration = 0
    private lateinit var progress: ProgressBar
    private val prefs by lazy { KodiAccess.prefs(this) }

    // url del video -> Referer con cui la pagina l'ha richiesto (molti host rifiutano richieste senza)
    private val videos = LinkedHashMap<String, String>()
    // Link per Kodi, con playlist e chiavi passate dal telefono (kodiItem con viaPhone)
    private val streams = Streams(Relay()) { localAddress()?.first }

    // Dominio da cui la pagina principale può navigare: tutto il resto è considerato redirect pubblicitario
    private var allowedDomain = ""
    @Volatile private var pageUrl = ""
    private var pageVisible = false

    // "IP:porta" -> nome annunciato via mDNS, per riconoscere lo stesso Kodi quando cambia IP
    private val kodiNames = HashMap<String, String>()

    // Favicon della pagina aperta, salvata con il preferito
    private var pageIcon: Bitmap? = null
    // Popup e salti bloccati sulla pagina aperta: si contano nello scudo della barra invece di un avviso a ogni blocco
    private val blocked = mutableListOf<Uri>()
    // Continua a guardare: le immagini già lette nella scelta del video (diventano quella della serie), gli avvisi
    // "Eri all'episodio" chiusi in questa sessione e, per ogni scheda della pagina iniziale, come aggiornarla in silenzio
    private val previews = HashMap<String, Bitmap>()

    // File di sottotitoli (.vtt, .srt) caricati dalla pagina: indirizzo → Referer, passati a Kodi con il video
    private val subtitleFiles = LinkedHashMap<String, String>()
    private val bannerClosed = mutableSetOf<String>()
    private val tileUpdaters = HashMap<String, (Show) -> Unit>()
    private lateinit var banner: View
    private lateinit var bannerTitle: TextView
    private lateinit var bannerDetail: TextView
    private lateinit var bannerGo: Button

    // Mentre si cerca Kodi in rete lo dice il pulsante in basso: un toast sparisce prima di averlo letto
    private var searchingKodi = false
    // Un invio alla volta: i tocchi ripetuti su "Manda a Kodi" mentre Kodi risponde non mandano altri video
    private var sending = false
    // App in primo piano: con il telefono bloccato il telecomando smette di chiedere a Kodi a che punto è
    @Volatile private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_100)
        val onSurfaceVariant = sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
        address = EditText(this).apply {
            hint = "Cerca o scrivi un indirizzo"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = null
            textSize = 15f
            setTextColor(onSurface)
            setHintTextColor(onSurfaceVariant)
            setPadding(0, 0, 0, 0)
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, _, _ -> open(text.toString()); true }
            // Come Chrome: si vede solo il sito, toccando torna l'indirizzo intero da modificare
            setOnFocusChangeListener { _, focused ->
                if (home.visibility == View.VISIBLE || pageUrl.isEmpty()) return@setOnFocusChangeListener
                setText(if (focused) pageUrl else addressLabel(pageUrl))
                if (focused) post { selectAll() }
            }
        }
        siteIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        shield = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(onSurfaceVariant)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(it.franc.casttokodi.R.drawable.ic_shield, onSurfaceVariant, 14), null, null, null)
            compoundDrawablePadding = dp(4)
            setPadding(dp(8), 0, dp(10), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), sys(R.color.system_neutral2_400, R.color.system_neutral2_600))
            }
            visibility = View.GONE
            setOnClickListener { showBlocked() }
        }
        // Barra a pillola: icona del sito, indirizzo e, se ha bloccato qualcosa, lo scudo con il conto
        val pill = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(6), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(sys(R.color.system_neutral1_100, R.color.system_neutral1_800))
            }
            addView(siteIcon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) })
            addView(address, LinearLayout.LayoutParams(0, -2, 1f))
            addView(shield, LinearLayout.LayoutParams(-2, dp(28)).apply { marginStart = dp(4) })
        }
        favorites = iconButton(it.franc.casttokodi.R.drawable.ic_star, "Preferiti") { showFavorites() }
        val settings = iconButton(it.franc.casttokodi.R.drawable.ic_more, "Kodi: coda, impostazioni e log") { kodiMenu(it) }

        // "Manda a Kodi" e "+ Coda" galleggiano sul sito e compaiono solo quando c'è un video da mandare
        cast = Button(this).apply {
            textSize = 15f
            isAllCaps = false
            stateListAnimator = null
            elevation = dp(6).toFloat()
            compoundDrawablePadding = dp(10)
            setPadding(dp(16), 0, dp(20), 0)
            setOnClickListener { pickVideo(queued = false) }
        }
        queue = ImageButton(this).apply {
            contentDescription = "Metti in coda su Kodi (tieni premuto per vedere la coda)"
            tooltipText = "Metti in coda"
            stateListAnimator = null
            elevation = dp(6).toFloat()
            setOnClickListener { pickVideo(queued = true) }
            // Pressione lunga: apre la coda di Kodi
            setOnLongClickListener { showPlayer(tab = 1); true }
        }
        castBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(queue, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            addView(cast, LinearLayout.LayoutParams(-2, dp(56)))
        }
        snack = snackView()
        progress = ProgressBar(this, null, R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(sys(R.color.system_accent1_600, R.color.system_accent1_200))
        }
        web = WebView(this)
        setupWebView()

        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
            addView(pill, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(favorites, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4) })
            addView(settings, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(dp(16), 0, dp(16), dp(16))
            addView(castBar, LinearLayout.LayoutParams(-2, -2))
            addView(snack, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        homeContent = section().apply { setPadding(dp(16), dp(8), dp(16), dp(24)) }
        home = ScrollView(this).apply {
            setBackgroundColor(sys(R.color.system_neutral1_10, R.color.system_neutral1_900))
            addView(homeContent)
            visibility = View.GONE
        }
        val page = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(-1, -1))
            addView(home, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        mini = miniPlayer()
        banner = resumeBannerView()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(sys(R.color.system_neutral1_10, R.color.system_neutral1_900))
            addView(topBar)
            addView(progress, LinearLayout.LayoutParams(-1, -2))
            addView(banner, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), 0, dp(8), dp(6)) })
            addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(mini, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(4), dp(8), dp(8)) })
            // targetSdk 35 forza l'edge-to-edge: senza padding la barra finisce sotto la status bar
            setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                WindowInsets.CONSUMED
            }
        }
        setContentView(root)
        showSiteIcon(null)
        refreshCastButton()

        migrateAuth()
        migrateAutoNext()
        cleanFrames()
        autoBackup()
        showHome()
        if (prefs.getString("host", "").isNullOrBlank()) askKodiAddress()
        openShared(intent)
        openRemote(intent)
        checkUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openShared(intent)
        openRemote(intent)
    }

    // Tocco sul player di Android o pressione lunga sul riquadro della tendina: il telecomando
    private fun openRemote(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_REMOTE && intent?.action != TileService.ACTION_QS_TILE_PREFERENCES) return
        intent.action = null
        if (!prefs.getString("host", "").isNullOrBlank()) showPlayer()
    }

    // Condividi → Couch Cast da Chrome o da un'altra app: la pagina si apre qui, dove il video si trova come al solito.
    // Se il link è già il video (.m3u8, .mp4) si può mandare subito a Kodi, senza pagina.
    private fun openShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        // Consumato: se Android ricrea l'Activity con lo stesso intent, il link non si riapre
        setIntent(Intent())
        val url = sharedAddress(intent.getStringExtra(Intent.EXTRA_TEXT)) ?: return toast("Nel testo condiviso non c'è un link")
        if (!isVideoLink(url)) return open(url)
        val text = TextView(this).apply {
            this.text = "Il link condiviso è già un video:\n$url"
            textSize = 14f
            setTextColor(sys(R.color.system_neutral2_700, R.color.system_neutral2_200))
            setPadding(dp(24), 0, dp(24), dp(8))
        }
        sheet("Video condiviso", text,
            "Apri qui" to { open(url) },
            "Manda a Kodi" to { sendToKodi(Video(url, "", "", true), queued = false, page = url) })
    }

    // L'ultima release ha come tag vX.Y.Z, con X.Y.Z = versionName dell'APK (da CHANGELOG.md, vedi .github/workflows/apk.yml).
    // Il repo è pubblico: l'API non vuole token (limite 60 richieste l'ora per IP, ne basta una per avvio).
    // asked: dalle Impostazioni, dove si dice anche quando non c'è niente di nuovo o GitHub non risponde
    private fun checkUpdate(asked: Boolean = false) {
        val installed = installedVersion()
        thread {
            val latest = try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                JSONObject(conn.inputStream.bufferedReader().readText()).optString("tag_name")
            } catch (e: Exception) {
                if (asked) toast("Non riesco a controllare gli aggiornamenti: c'è la rete?")
                return@thread
            }
            if (!isNewerVersion(latest, installed)) {
                if (asked) toast("Hai già l'ultima versione ($installed)")
                return@thread
            }
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

    private fun installedVersion() = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

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
                // Passando da Google a un sito, l'icona di Google può arrivare quando il sito è già partito: finché
                // il sito non si vede, l'icona è ancora della pagina di prima
                if (!pageVisible) return
                pageIcon = icon
                showSiteIcon(icon)
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

            override fun onPageCommitVisible(view: WebView, url: String) {
                pageVisible = true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageVisible = false
                pageUrl = url
                pageIcon = favicon
                showSiteIcon(favicon)
                home.visibility = View.GONE
                banner.visibility = View.GONE
                allowedDomain = domainOf(Uri.parse(url))
                if (!address.hasFocus()) address.setText(addressLabel(url))
                videos.clear()
                previews.clear()
                subtitleFiles.clear()
                blocked.clear()
                refreshShield()
                refreshStar()
                refreshCastButton()
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageVisible = true
                // onReceivedIcon spesso arriva prima che il sito si veda e viene scartato: a pagina finita l'icona del
                // WebView è quella del sito
                view.favicon?.let { icon ->
                    pageIcon = icon
                    showSiteIcon(icon)
                    fillMissingIcon(url, icon)
                }
                pageShown(url, view.title)
            }

            // AnimeUnity cambia episodio senza caricare una pagina nuova (history.pushState): onPageStarted non arriva,
            // ma pageUrl deve seguire l'indirizzo, se no l'invio vale per la pagina di prima e la serie non si riconosce.
            // I video trovati restano: è lo stesso player
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url == pageUrl || !url.startsWith("http")) return
                pageUrl = url
                if (!address.hasFocus()) address.setText(addressLabel(url))
                refreshStar()
                pageShown(url, view.title)
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
                if (isSubtitlePath(request.url.path)) {
                    val referer = request.requestHeaders["Referer"] ?: pageUrl
                    val file = request.url.toString()
                    runOnUiThread { subtitleFiles.putIfAbsent(file, referer) }
                }
                if (request.method == "GET" && isHlsPath(request.url.path)) return capturePlaylist(request)
                return null
            }
        }
    }

    // Pagina finita di caricare, o indirizzo cambiato da JS: ultima pagina, dominio del sito, avviso "Eri all'episodio"
    private fun pageShown(url: String, title: String?) {
        val uri = Uri.parse(url)
        if (!url.startsWith("http") || isGoogleConsent(uri.host)) return
        rememberSite(uri.host)
        if (historyOn()) {
            prefs.edit().putString("last", url).putString("lastTitle", title).putLong("lastAt", System.currentTimeMillis()).apply()
        }
        refreshBanner(uri, title)
    }

    private fun open(input: String) {
        val url = addressFor(input)
        allowedDomain = domainOf(Uri.parse(url))
        web.loadUrl(url)
        web.requestFocus()
    }

    private fun block(url: Uri): Boolean {
        if (blocked.none { it.host == url.host }) blocked += url
        refreshShield()
        return true
    }

    private fun refreshShield() {
        shield.text = blocked.size.toString()
        shield.contentDescription = "${blocked.size} popup o salti bloccati"
        shield.visibility = if (blocked.isEmpty()) View.GONE else View.VISIBLE
    }

    // Lo scudo mostra cosa è stato bloccato: se serviva (un login, un player su un altro dominio) lo si apre da qui
    private fun showBlocked() {
        val list = blocked.toList()
        AlertDialog.Builder(this)
            .setTitle("Bloccati su questa pagina")
            .setItems(list.map { it.host ?: it.scheme ?: it.toString() }.toTypedArray()) { _, i ->
                allowedDomain = domainOf(list[i])
                web.loadUrl(list[i].toString())
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showSiteIcon(icon: Bitmap?) {
        if (icon != null) {
            siteIcon.setImageBitmap(icon)
            siteIcon.imageTintList = null
        } else {
            siteIcon.setImageResource(it.franc.casttokodi.R.drawable.ic_public)
            siteIcon.imageTintList = ColorStateList.valueOf(sys(R.color.system_neutral2_700, R.color.system_neutral2_200))
        }
    }

    // Stella piena se la pagina aperta è già nei preferiti
    private fun refreshStar() {
        val url = web.url
        val saved = url != null && loadFavorites().any { it.url == url }
        favorites.setImageDrawable(icon(
            if (saved) it.franc.casttokodi.R.drawable.ic_star_filled else it.franc.casttokodi.R.drawable.ic_star,
            if (saved) sys(R.color.system_accent1_600, R.color.system_accent1_200) else sys(R.color.system_neutral2_700, R.color.system_neutral2_200),
        ))
    }

    private fun domainOf(url: Uri) = domainOf(url.host)

    // Video trovati senza pubblicità e senza i pezzi delle playlist VixCloud, con la playlist principale per prima
    private fun playable(): List<Video> = videoChoices(videos.mapNotNull { (url, referer) ->
        val uri = Uri.parse(url)
        videoFor(url, referer, uri.host, uri.path, uri.encodedQuery)
    })

    // Senza video i pulsanti non ci sono: il sito resta tutto visibile. Mentre si cerca Kodi o si manda il video
    // restano, spenti, con quello che sta succedendo.
    private fun refreshCastButton() {
        val count = playable().size
        val busy = searchingKodi || sending
        val ready = count > 0 && !busy
        castBar.visibility = if ((count > 0 && home.visibility != View.VISIBLE) || busy) View.VISIBLE else View.GONE
        cast.isEnabled = ready
        queue.isEnabled = ready
        queue.visibility = if (busy) View.GONE else View.VISIBLE
        cast.text = when {
            searchingKodi -> "Cerco Kodi in rete…"
            sending -> "Invio a Kodi…"
            count == 1 -> "Manda a Kodi"
            else -> "Manda a Kodi · $count"
        }
        // Pulsanti galleggianti di Material You: contenitore dell'accento per l'azione principale, tonale per la coda
        val (castBg, castFg) = sys(R.color.system_accent1_100, R.color.system_accent1_700) to
            sys(R.color.system_accent1_900, R.color.system_accent1_100)
        val (queueBg, queueFg) = sys(R.color.system_accent2_100, R.color.system_accent2_700) to
            sys(R.color.system_accent2_900, R.color.system_accent2_100)
        cast.background = rounded(castBg, 16)
        cast.setTextColor(castFg)
        cast.alpha = if (ready) 1f else 0.7f
        cast.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(it.franc.casttokodi.R.drawable.ic_cast, castFg), null, null, null)
        queue.background = rounded(queueBg, 24)
        queue.setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_queue, queueFg))
    }

    // Sfondo arrotondato con l'onda al tocco
    private fun rounded(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x33808080),
        GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(color) },
        null,
    )

    private fun icon(id: Int, color: Int, size: Int = 24) = getDrawable(id)!!.mutate().apply {
        setTint(color)
        setBounds(0, 0, dp(size), dp(size))
    }

    // Pulsante tondo con solo l'icona, come quelli delle barre di Android
    private fun iconButton(id: Int, description: String, onClick: (View) -> Unit) = ImageButton(this).apply {
        contentDescription = description
        tooltipText = description
        setImageDrawable(icon(id, sys(R.color.system_neutral2_700, R.color.system_neutral2_200)))
        setBackgroundResource(attr(R.attr.selectableItemBackgroundBorderless))
        setOnClickListener(onClick)
    }

    // Colori di sistema (Material You, presi dallo sfondo del telefono): accento per l'azione principale,
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

    // Più video sulla pagina: immagini grandi con la durata sopra, come su YouTube. Il primo (di solito l'episodio) è
    // già scelto con "Manda" e "In coda" sotto; toccandone un altro i pulsanti passano lì. Sotto il minuto: sbiaditi.
    private fun pickVideo(queued: Boolean) {
        val list = playable()
        if (list.isEmpty()) return
        if (list.size == 1) return sendToKodi(list[0], queued)
        lateinit var dialog: Dialog
        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_50)
        val onSurfaceVariant = sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
        val selected = sys(R.color.system_neutral2_100, R.color.system_neutral2_800)
        val content = section().apply { setPadding(dp(16), 0, dp(16), dp(16)) }
        var chosen = list[0]
        fun action(text: String, iconId: Int, primary: Boolean, queue: Boolean) = Button(this).apply {
            val (bg, fg) = if (primary) sys(R.color.system_accent1_600, R.color.system_accent1_200) to sys(R.color.system_accent1_0, R.color.system_accent1_800)
                else sys(R.color.system_accent2_100, R.color.system_accent2_700) to sys(R.color.system_accent2_900, R.color.system_accent2_100)
            this.text = text
            textSize = 15f
            isAllCaps = false
            stateListAnimator = null
            background = rounded(bg, 24)
            setTextColor(fg)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconId, fg, 20), null, null, null)
            compoundDrawablePadding = dp(8)
            setPadding(dp(20), 0, dp(20), 0)
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss(); sendToKodi(chosen, queue) }
        }
        val actions = LinearLayout(this).apply {
            setPadding(0, dp(8), 0, dp(8))
            addView(action("Manda", it.franc.casttokodi.R.drawable.ic_cast, primary = !queued, queue = false), LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(action("In coda", it.franc.casttokodi.R.drawable.ic_queue, primary = queued, queue = true),
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        }
        val userAgent = web.settings.userAgentString
        val cards = mutableListOf<View>()
        // Sottotitoli caricati dalla pagina: valgono per qualunque video si mandi da qui
        val siteLanguages = siteSubtitleFiles(subtitleFiles.keys.toList()).map { subtitleLanguage(it) }
        list.forEachIndexed { i, video ->
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_play, onSurfaceVariant, 28))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(sys(R.color.system_neutral2_200, R.color.system_neutral2_700))
                }
                clipToOutline = true
            }
            val duration = TextView(this).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(sys(R.color.system_neutral1_10, R.color.system_neutral1_10))
                setPadding(dp(6), dp(1), dp(6), dp(1))
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_1000))
                    alpha = 210
                }
                visibility = View.GONE
            }
            // "CC Italiano, Inglese": sottotitoli nella playlist o sulla pagina
            val cc = TextView(this).apply {
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_900))
                setPadding(dp(5), dp(1), dp(5), dp(1))
                background = rounded(sys(R.color.system_neutral1_10, R.color.system_neutral1_10), 4)
                text = ccLabel(siteLanguages)
                visibility = if (siteLanguages.isEmpty()) View.GONE else View.VISIBLE
            }
            val frame = FrameLayout(this).apply {
                addView(image, FrameLayout.LayoutParams(-1, -1))
                addView(duration, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(6), dp(6)) })
                addView(cc, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), 0, dp(52), dp(6)) })
            }
            val host = "da ${shortHost(Uri.parse(video.url).host)}" + if (siteLanguages.isNotEmpty()) " · sottotitoli dal sito" else ""
            val detail = TextView(this).apply {
                text = host
                textSize = 13f
                setTextColor(onSurfaceVariant)
            }
            val text = section(TextView(this).apply {
                this.text = video.label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(onSurface)
            }, detail)
            if (i == 0) text.addView(TextView(this).apply {
                this.text = "Consigliato"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(sys(R.color.system_accent2_900, R.color.system_accent2_100))
                setPadding(dp(10), dp(3), dp(10), dp(3))
                background = rounded(sys(R.color.system_accent2_100, R.color.system_accent2_700), 8)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
            val card = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(frame, LinearLayout.LayoutParams(dp(144), dp(81)))
                addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
            }
            card.setOnClickListener {
                chosen = video
                cards.forEach { other -> other.background = if (other === card) rounded(selected, 16) else null }
                content.removeView(actions)
                content.addView(actions, content.indexOfChild(card) + 1)
            }
            cards += card
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            if (i == 0) content.addView(actions)
            thread {
                var inPlaylist: List<String?> = emptyList()
                val (seconds, picture) = videoPreview(video, userAgent, subtitles = { inPlaylist = it })
                runOnUiThread {
                    if (inPlaylist.isNotEmpty()) {
                        cc.text = ccLabel(inPlaylist + siteLanguages)
                        cc.visibility = View.VISIBLE
                    }
                    picture?.let {
                        previews[video.url] = it
                        image.setImageBitmap(it)
                    }
                    seconds?.let {
                        duration.text = clockLabel(it)
                        duration.visibility = View.VISIBLE
                    }
                    when {
                        seconds != null && seconds < 60 -> {
                            detail.text = "Probabile pubblicità, $seconds s"
                            if (chosen !== video) card.alpha = 0.55f
                        }
                        seconds != null && picture == null -> detail.text = "$host · nessuna immagine"
                        seconds == null && picture == null -> detail.text = "$host · anteprima non disponibile"
                    }
                }
            }
        }
        cards[0].background = rounded(selected, 16)
        dialog = sheet("${list.size} video su questa pagina", content)
    }

    // Durata in secondi e un fotogramma piccolo, per riconoscere l'episodio da un trailer o da una pubblicità.
    // Da un thread di background. MP4: li legge MediaMetadataRetriever dal sito. HLS: Streams.preview scarica un pezzo
    // della qualità più leggera (già decifrato) e il fotogramma si prende da lì. Video protetti: niente immagine.
    // width: larghezza massima del fotogramma in pixel (quella delle anteprime nell'elenco dei video, se non detta).
    // failed: perché il fotogramma non c'è, se manca
    // subtitles: le lingue dei sottotitoli nella playlist, quando ci sono
    private fun videoPreview(
        video: Video, userAgent: String, width: Int = dp(224), fraction: Double? = null, failed: (String) -> Unit = {},
        subtitles: (List<String?>) -> Unit = {},
    ): Pair<Long?, Bitmap?> {
        val headers = streamHeaders(video.url, video.referer, userAgent)
        val retriever = MediaMetadataRetriever()
        var file: File? = null
        var seconds: Long? = null
        return try {
            if (Uri.parse(video.url).path.orEmpty().lowercase().endsWith(".mp4")) {
                retriever.setDataSource(video.url, headers.toMap())
            } else {
                val preview = streams.preview(video.url, headers, fraction = fraction)
                seconds = preview.seconds
                if (preview.subtitles.isNotEmpty()) subtitles(preview.subtitles)
                val sample = preview.sample ?: return (seconds to null).also { failed(preview.error ?: "pezzo del video non scaricato") }
                // Senza gli eventuali byte finti davanti al pezzo (2.2.9: con la 2.2.8 il telefono non leggeva il fotogramma)
                val start = tsStart(sample)
                file = File.createTempFile("anteprima", ".ts", cacheDir).apply { writeBytes(if (start > 0) sample.copyOfRange(start, sample.size) else sample) }
                retriever.setDataSource(file.path)
            }
            val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (seconds == null) seconds = length?.let { it / 1000 }
            // Per un MP4 il punto scelto (previewPoint), per un pezzo HLS a metà del pezzo
            val at = if (file == null) (previewPoint((length ?: 0) / 1000.0, fraction) * 1000).toLong() else (length ?: 0) / 2
            // Nei pezzi HLS il tempo non parte da 0 e cercare un punto può fallire: allora un fotogramma qualunque
            // (tempo negativo), e se no si decodifica il primo fotogramma del pezzo a mano
            val frame = retriever.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, width * 9 / 16)
                ?: retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, width * 9 / 16)
                ?: file?.let { firstFrame(it.path, width) }
            if (frame == null) failed("il telefono non legge il fotogramma dal pezzo")
            seconds to frame
        } catch (e: Exception) {
            failed(e.message ?: e.javaClass.simpleName)
            seconds to null
        } finally {
            retriever.release()
            file?.delete()
        }
    }

    // Luminosità di un fotogramma, da una copia piccola
    private fun frameBrightness(picture: Bitmap): Double {
        val small = Bitmap.createScaledBitmap(picture, 32, 18, true)
        val pixels = IntArray(32 * 18)
        small.getPixels(pixels, 0, 32, 0, 0, 32, 18)
        return brightness(pixels)
    }

    // Il primo fotogramma di un pezzo di video, decodificato con MediaCodec senza cercare un punto: un pezzo HLS
    // inizia sempre con un fotogramma completo. null se il telefono non ha il decoder o il pezzo non si legge
    private fun firstFrame(path: String, width: Int): Bitmap? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(input)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 10_000)
                if (output < 0) continue
                val picture = if (info.size > 0) codec.getOutputImage(output)?.use { yuvBitmap(it) } else null
                codec.releaseOutputBuffer(output, false)
                if (picture != null) {
                    val height = (width.toLong() * picture.height / picture.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    return if (picture.width > width) Bitmap.createScaledBitmap(picture, width, height, true) else picture
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return null
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }

    // Un fotogramma YUV del decoder in Bitmap, passando da NV21 e JPEG (YuvImage)
    private fun yuvBitmap(image: Image): Bitmap? {
        val crop = image.cropRect
        val w = crop.width() and 1.inv()
        val h = crop.height() and 1.inv()
        if (w <= 0 || h <= 0) return null
        val nv21 = ByteArray(w * h * 3 / 2)
        val (y, u, v) = image.planes
        for (row in 0 until h) for (col in 0 until w) {
            nv21[row * w + col] = y.buffer.get((crop.top + row) * y.rowStride + (crop.left + col) * y.pixelStride)
        }
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            val at = w * h + row * w + col * 2
            nv21[at] = v.buffer.get((crop.top / 2 + row) * v.rowStride + (crop.left / 2 + col) * v.pixelStride)
            nv21[at + 1] = u.buffer.get((crop.top / 2 + row) * u.rowStride + (crop.left / 2 + col) * u.pixelStride)
        }
        val out = java.io.ByteArrayOutputStream()
        android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null).compressToJpeg(android.graphics.Rect(0, 0, w, h), 90, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    // L'immagine dell'episodio in riproduzione (o della serie) in thumb, al posto del ▶; false se non c'è. Le
    // immagini lette restano in memoria: il mini-player e il telecomando le chiedono ogni pochi secondi
    private val thumbCache = HashMap<String, Bitmap?>()

    private fun showPicture(thumb: ImageView, file: String?): Boolean {
        val name = pictureOfFile(loadShows(), file) ?: return false
        val picture = thumbCache.getOrPut(name) { loadFrame(name) } ?: return false
        thumb.setImageBitmap(picture)
        thumb.imageTintList = null
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        thumb.clipToOutline = true
        return true
    }

    // Tutto passa dalla playlist video di Kodi, così "+ Coda" e gli episodi successivi seguono quello in corso.
    // La coda non si cancella mai: un video nuovo si inserisce dopo quello in corso o in fondo.
    // typed: utente e password appena scritti nel dialogo dopo un 401, al posto di quelli salvati.
    // page: la pagina del video, o il video stesso quando è arrivato da Condividi senza pagina.
    // name: il nome da mostrare, quando la pagina non è quella aperta ("Riprendi su Kodi" dalla pagina iniziale)
    private fun sendToKodi(video: Video, queued: Boolean, typed: Pair<String, String>? = null, page: String = pageUrl, name: String? = null) {
        if (sending) return
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val network = currentNetwork()
        val login = typed ?: savedLogin(host, network)
        val kodi = Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
        val userAgent = web.settings.userAgentString
        val pageTitle = name ?: web.title.takeIf { page == pageUrl }
        val title = suggestedName(pageTitle, Uri.parse(page).host)
        // Continua a guardare: l'episodio di questa pagina e, se era a metà, da dove riprenderlo
        val pageUri = Uri.parse(page)
        val episode = if (historyOn()) episodeFromPage(pageUri.host, pageUri.path, pageUri.encodedQuery, pageTitle) else null
        val resume = if (!queued && episode != null) resumePoint(loadShows(), episode.site, episode.path) else null
        var sentFile: String? = null
        // Sottotitoli della pagina (solo se il video è di questa pagina) e quelli passati davvero a Kodi
        val siteSubtitles = if (page == pageUrl) pageSubtitles(userAgent) else emptyList()
        var subtitlesSent: List<SubtitleTrack> = emptyList()
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
                if (queued) {
                    kodi.enqueue({ kodiItem(video.url, video.referer, userAgent, title, viaPhone = it, siteSubtitles) { tracks -> subtitlesSent = tracks }.also { item -> sentFile = item.getString("file") } }) {
                        expiryProblem(video.url, it)
                    }
                } else {
                    kodi.playNow(kodiItem(video.url, video.referer, userAgent, title, viaPhone = true, siteSubtitles) { subtitlesSent = it }.also { sentFile = it.getString("file") })
                }
            } catch (e: IOException) {
                done { relocateKodi(host) { sendToKodi(video, queued, typed, page, name) } }
                return@thread
            } catch (e: Unauthorized) {
                done { askLogin(host, login, wrong = login != null) { sendToKodi(video, queued, it, page, name) } }
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
                // Con Kodi fermo "+ Coda" fa partire il video: anche lui diventa quello in corso
                val playing = !queued || sent.message.startsWith("Inviato")
                val file = sentFile
                // "Inviato a Kodi, con sottotitoli in italiano"
                val message = subtitlesNote(subtitlesSent)?.let { "${sent.message}, $it" } ?: sent.message
                // 2.2.1: su AnimeUnity la serie non compariva e dal codice non si capisce perché. Finché non è chiaro,
                // l'app dice cosa è andato storto invece di tacere
                if (episode != null && file != null) {
                    try {
                        recordSent(episode, pageUri.host.orEmpty(), file, playing, video, page)
                        if (loadShows().none { it.key == episode.key }) notice("Continua a guardare non ha salvato", "Serie non ritrovata dopo il salvataggio\n\n$page")
                    } catch (e: Exception) {
                        notice("Continua a guardare non ha salvato", "$e\n\n$page")
                    }
                } else if (historyOn() && domainOf(pageUri.host).startsWith("animeunity.")) {
                    notice("Continua a guardare non ha salvato", "Episodio: ${episode != null}, video: ${file != null}\n\n$page")
                }
                if (resume != null && playing && file != null) {
                    toast("$message: riparte da ${clockLabel(resume)}")
                    thread {
                        val failed = runCatching { kodi.resumeAt(file, resume) }.getOrElse { it.message ?: it.javaClass.simpleName }
                        // 2.2.5: la ripresa non funzionava sulla Fire TV vera; finché non è provata, l'app dice perché
                        if (failed != null) runOnUiThread { notice("Non riparte da ${clockLabel(resume)}", "$failed.\n\nPuoi andare al minuto dal telecomando.") }
                    }
                } else {
                    toast(message)
                }
                readMini()
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
    // subtitles: i file di sottotitoli della pagina, che vanno con il video solo se passa dal telefono
    private fun kodiItem(
        url: String, referer: String, userAgent: String, title: String, viaPhone: Boolean = false,
        subtitles: List<SiteSubtitle> = emptyList(), onSubtitles: (List<SubtitleTrack>) -> Unit = {},
    ): JSONObject {
        val file = streams.kodiFile(url, streamHeaders(url, referer, userAgent), viaPhone, subtitles, onSubtitles)
        rememberTitle(queueKey(file), title)
        return JSONObject().put("file", file)
    }

    // I sottotitoli caricati dalla pagina aperta, con gli header del browser (da leggere sul thread principale)
    private fun pageSubtitles(userAgent: String): List<SiteSubtitle> = siteSubtitleFiles(subtitleFiles.keys.toList()).map {
        SiteSubtitle(it, streamHeaders(it, subtitleFiles[it].orEmpty(), userAgent))
    }

    // Gli header che manderebbe il browser: oltre a Referer e User-Agent, l'Origin della pagina del player quando il
    // video sta su un altro dominio e i cookie del sito del video. Senza, alcuni CDN rispondono 403
    // (StreamingCommunity con il player vidxgo, 2026-10-21).
    private fun streamHeaders(url: String, referer: String, userAgent: String): List<Pair<String, String>> {
        // Referer vuoto (video arrivato da Condividi, senza pagina): non lo si manda, come il browser
        val headers = mutableListOf("User-Agent" to userAgent)
        if (referer.isNotBlank()) headers.add(0, "Referer" to referer)
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
        val uri = Uri.parse(page)
        val sentEpisode = if (historyOn()) episodeFromPage(uri.host, uri.path, uri.encodedQuery, title) else null
        // Quanti: la scelta della serie (pannello della serie), se no quella di ⋮ → Impostazioni
        val count = autoNextOf(loadShows(), sentEpisode?.key, prefs.getInt("autoNext", DEFAULT_AUTO_NEXT))
        if (count <= 0 || !isAnimeUnityEpisode(uri.host, uri.path)) return
        if (web.url != page) return
        val base = uri.path.orEmpty().trimEnd('/').substringBeforeLast('/')
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
                        var file: String? = null
                        val problem = try {
                            kodi.insertAt(after + 1 + i, {
                                kodiItem(stream, embed, userAgent, nextEpisodeTitle(title, number)).also { file = it.getString("file") }
                            }) { expiryProblem(stream, it) }
                        } catch (e: Exception) {
                            "Kodi: ${e.message}"
                        }
                        runOnUiThread {
                            if (problem != null) return@runOnUiThread notice("Episodio $number non messo in coda", problem)
                            val id = episode.optString("id")
                            if (sentEpisode != null && file != null && id.isNotBlank()) {
                                val next = EpisodePage(sentEpisode.key, sentEpisode.site, sentEpisode.series, null, number.toIntOrNull(), "$base/$id")
                                recordSent(next, uri.host.orEmpty(), file!!, playing = false, video = null)
                            }
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
    private fun resolveVixCloud(embed: String, userAgent: String, referer: String = pageUrl, onResult: (String?) -> Unit) {
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
        hidden.loadUrl(embed, mapOf("Referer" to referer))
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

        val form = section(tabs, autoPanel, manualPanel, authPanel).apply { setPadding(dp(24), dp(8), dp(24), 0) }
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
            menu.add(0, 5, 2, "Pagina iniziale")
            menu.add(0, 6, 3, "Impostazioni")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showPlayer(tab = 1)
                    4 -> showPlayer()
                    5 -> showHome()
                    else -> showSettings()
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

    // Telecomando e coda a schermo intero, con le due schede in alto: si apre dalla barra di Kodi in fondo, dalla pagina
    // iniziale o da ⋮, e si chiude con ⌄, con indietro o trascinando giù l'intestazione.
    private fun showPlayer(tab: Int = 0) {
        if (prefs.getString("host", "").isNullOrBlank()) return askKodiAddress()
        val dialog = Dialog(this, R.style.Theme_DeviceDefault_DayNight)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val surface = sys(R.color.system_neutral1_10, R.color.system_neutral1_900)
        val onSurfaceVariant = sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
        val tonal = sys(R.color.system_accent2_100, R.color.system_accent2_700)
        val onTonal = sys(R.color.system_accent2_900, R.color.system_accent2_100)

        val close = iconButton(it.franc.casttokodi.R.drawable.ic_arrow_down, "Chiudi") { dialog.dismiss() }
        val chip = TextView(this).apply {
            text = prefs.getString("name", "").orEmpty().ifBlank { "Kodi" }
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(onTonal)
            compoundDrawablePadding = dp(8)
            setPadding(dp(12), 0, dp(14), 0)
            background = rounded(tonal, 16)
        }
        // Pallino acceso se Kodi risponde, spento se no
        fun showReachable(reachable: Boolean) {
            val color = if (reachable) sys(R.color.system_accent1_500, R.color.system_accent1_200) else sys(R.color.system_neutral2_400, R.color.system_neutral2_500)
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(circle(color).apply { setSize(dp(8), dp(8)) }, null, null, null)
        }
        showReachable(kodiReachable == true)
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(chip, LinearLayout.LayoutParams(-2, dp(32)))
        }

        val tabs = listOf("Telecomando", "Coda").map { name ->
            TextView(this).apply {
                text = name
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
        }
        val segmented = LinearLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), sys(R.color.system_neutral2_400, R.color.system_neutral2_600))
            }
            tabs.forEach { addView(it, LinearLayout.LayoutParams(0, -1, 1f)) }
        }
        val remote = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(remoteView { playing -> showReachable(playing != null) })
        }
        val (queuePage, reloadQueue) = queueView { waiting -> tabs[1].text = if (waiting > 0) "Coda · $waiting" else "Coda" }
        fun select(index: Int) {
            remote.visibility = if (index == 0) View.VISIBLE else View.GONE
            queuePage.visibility = if (index == 1) View.VISIBLE else View.GONE
            tabs.forEachIndexed { i, view ->
                view.background = if (i == index) rounded(tonal, 22) else null
                view.setTextColor(if (i == index) onTonal else onSurfaceVariant)
            }
            if (index == 1) reloadQueue()
        }
        tabs.forEachIndexed { i, view -> view.setOnClickListener { select(i) } }

        val pages = FrameLayout(this).apply {
            addView(remote, FrameLayout.LayoutParams(-1, -1))
            addView(queuePage, FrameLayout.LayoutParams(-1, -1))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), 0)
            addView(header, LinearLayout.LayoutParams(-1, dp(52)))
            addView(segmented, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
            addView(pages, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        }
        val note = snackView()
        val root = FrameLayout(this).apply {
            setBackgroundColor(surface)
            addView(body, FrameLayout.LayoutParams(-1, -1))
            addView(note, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(16), 0, dp(16), dp(16)) })
            setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                WindowInsets.CONSUMED
            }
        }
        // Trascinando giù l'intestazione il telecomando scende con il dito; oltre un certo punto si chiude
        var startY = 0f
        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startY = event.rawY
                MotionEvent.ACTION_MOVE -> root.translationY = (event.rawY - startY).coerceAtLeast(0f)
                else -> if (root.translationY > dp(96)) dialog.dismiss() else root.animate().translationY(0f)
            }
            true
        }
        select(tab)
        if (tab == 0) reloadQueue()
        dialog.setContentView(root)
        dialog.window?.apply {
            setDecorFitsSystemWindows(false)
            setBackgroundDrawable(ColorDrawable(surface))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setWindowAnimations(R.style.Animation_InputMethod)
        }
        playerSnack = note
        dialog.setOnDismissListener {
            if (playerSnack === note) playerSnack = null
            readMini()
        }
        dialog.show()
    }

    // Scheda Coda: la playlist video di Kodi, cosa sta andando e a che ora parte il resto. Restituisce la View e la
    // funzione che la rilegge; waiting riceve quanti video aspettano dopo quello in corso (il numero sulla scheda).
    // La lettura è in silenzio, se Kodi non risponde lo dice qui; ↻ la rifà chiedendo, se serve, utente e password.
    private fun queueView(waiting: (Int) -> Unit): Pair<View, () -> Unit> {
        val tonal = sys(R.color.system_accent2_100, R.color.system_accent2_700)
        val onTonal = sys(R.color.system_accent2_900, R.color.system_accent2_100)
        val list = section()
        val info = label("").apply {
            textSize = 13f
            setTextColor(sys(R.color.system_neutral2_700, R.color.system_neutral2_200))
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        val clear = Button(this).apply {
            text = "Svuota la coda"
            isAllCaps = false
            stateListAnimator = null
            background = rounded(tonal, 24)
            setTextColor(onTonal)
        }
        val refresh = ImageButton(this).apply {
            contentDescription = "Aggiorna la coda"
            tooltipText = contentDescription
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_refresh, onTonal))
            background = RippleDrawable(ColorStateList.valueOf(0x33808080), circle(tonal), null)
        }
        lateinit var load: (Boolean) -> Unit
        val fetch = { kodi: Kodi ->
            val (state, items) = kodi.queue()
            Triple(state, items, kodi.playingOutside(state))
        }
        fun show(state: KodiState, items: List<JSONObject>, outside: JSONObject?) {
            val titles = loadTitles()
            val current = if (state.fromQueue) state.position else -1
            val now = System.currentTimeMillis() / 1000
            list.removeAllViews()
            info.text = when {
                outside != null && items.isEmpty() ->
                    "Kodi sta riproducendo un video partito fuori dalla coda, e la coda è vuota. \"+ Coda\" non può accodare dopo " +
                        "questo video: usa \"Manda a Kodi\" sull'episodio e da lì accoda gli altri."
                outside != null -> "Kodi sta riproducendo un video partito fuori dalla coda: quando finisce non passa a questi."
                items.isEmpty() -> "La coda è vuota. \"Manda a Kodi\" fa partire un video, \"+ Coda\" lo mette in fondo."
                state.playerId == null -> "Kodi è fermo: con ⋮ fai partire un video da lì."
                else -> ""
            }
            info.visibility = if (info.text.isEmpty()) View.GONE else View.VISIBLE
            outside?.let { item ->
                val name = queueLabel(item.optString("file"), item.optString("title").ifBlank { item.optString("label") }, titles)
                list.addView(queueRow(0, name, QueueNote("In riproduzione, fuori dalla coda"), playing = true, done = false, menu = null))
            }
            items.forEachIndexed { i, item ->
                val file = item.optString("file")
                val name = queueLabel(file, item.optString("title").ifBlank { item.optString("label") }, titles)
                val note = queueNote(state, i, linkExpiry(Uri.parse(queueKey(file)).encodedQuery), now)
                val playing = i == current
                list.addView(queueRow(i + 1, name, note, playing, done = current >= 0 && i < current,
                    menu = if (playing) null else { anchor -> queueMenu(anchor, name, state, i) { load(false) } }))
            }
            clear.visibility = if (items.size > (if (current >= 0) 1 else 0)) View.VISIBLE else View.INVISIBLE
            clear.setOnClickListener { clearQueue(current) { load(false) } }
            waiting(if (current >= 0) items.size - current - 1 else items.size)
        }
        load = { loud ->
            if (loud) {
                onKodi(fetch, { (state, items, outside) -> show(state, items, outside) })
            } else {
                thread {
                    val (result, problem) = try {
                        quietKodi()?.let(fetch) to null
                    } catch (e: Unauthorized) {
                        null to "Kodi chiede utente e password: tocca ↻ per inserirli."
                    } catch (e: Exception) {
                        null to "Kodi non risponde. Se la TV è spenta accendila dal telecomando, poi tocca ↻."
                    }
                    runOnUiThread {
                        if (result != null) return@runOnUiThread show(result.first, result.second, result.third)
                        list.removeAllViews()
                        info.text = problem ?: "Kodi non ancora scelto."
                        info.visibility = View.VISIBLE
                        clear.visibility = View.INVISIBLE
                        waiting(0)
                    }
                }
            }
        }
        refresh.setOnClickListener { load(true) }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
            addView(clear, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(refresh, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(context).apply { addView(section(info, list)) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(bar)
        }
        return page to { load(false) }
    }

    // Riga della coda: tondino con il numero (▶ pieno per quello in corso), nome, quando parte e ⋮; i già visti più tenui
    private fun queueRow(number: Int, name: String, note: QueueNote, playing: Boolean, done: Boolean, menu: ((View) -> Unit)?): View {
        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_50)
        val onSurfaceVariant = sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
        val badge = if (playing) {
            ImageView(this).apply {
                setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_play, sys(R.color.system_accent1_0, R.color.system_accent1_800), 18))
                scaleType = ImageView.ScaleType.CENTER
                background = circle(sys(R.color.system_accent1_600, R.color.system_accent1_200))
            }
        } else {
            TextView(this).apply {
                text = number.toString()
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(onSurfaceVariant)
                background = circle(sys(R.color.system_neutral2_100, R.color.system_neutral2_800))
            }
        }
        val title = TextView(this).apply {
            text = name
            textSize = 15f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            if (playing) typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
        }
        val detail = TextView(this).apply {
            text = note.text
            textSize = 13f
            if (note.warning) {
                val red = errorColor()
                setTextColor(red)
                compoundDrawablePadding = dp(4)
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon(it.franc.casttokodi.R.drawable.ic_schedule, red, 14), null, null, null)
            } else {
                setTextColor(if (playing) sys(R.color.system_accent1_700, R.color.system_accent1_200) else onSurfaceVariant)
            }
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(4), dp(6), 0, dp(6))
            if (done) alpha = 0.55f
            addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(section(title, detail), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
            if (menu != null) {
                val more = iconButton(it.franc.casttokodi.R.drawable.ic_more, "Riproduci o togli $name") { menu(it) }
                addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
                setBackgroundResource(attr(R.attr.selectableItemBackground))
                setOnClickListener { menu(more) }
            }
        }
    }

    // Rosso per i link che scadono: quello di sistema da Android 14, prima il terzo accento di Material You
    private fun errorColor() =
        if (Build.VERSION.SDK_INT >= 34) sys(R.color.system_error_600, R.color.system_error_200)
        else sys(R.color.system_accent3_600, R.color.system_accent3_200)

    private fun queueMenu(anchor: View, name: String, state: KodiState, index: Int, changed: () -> Unit) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Riproduci ora")
            menu.add(0, 2, 1, "Togli dalla coda")
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1) {
                    onKodi({ kodi -> kodi.playFrom(state, index) }, { toast("Parte: $name"); changed() })
                } else {
                    onKodi({ kodi -> kodi.remove(index) }, { changed() })
                }
                true
            }
            show()
        }
    }

    // Kodi con le credenziali salvate per questa rete, per le letture in silenzio; null se Kodi non è ancora scelto
    private fun quietKodi(): Kodi? {
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return null
        val login = savedLogin(host, currentNetwork())
        return Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
    }

    // Il telecomando, da solo (la scheda di showPlayer). Finché la View è attaccata alla finestra e l'app è in primo
    // piano, rilegge da Kodi il video in corso ogni secondo; onRead riceve ogni lettura (null se Kodi non risponde).
    // In alto il video con la barra, poi play grande, la croce tonda con OK, i tasti tondi e, in una fila che scorre,
    // il resto. Il telecomando della TV salta dei passi di Kodi (su/giù 10 minuti): qui i salti sono quelli scritti.
    private fun remoteView(onRead: (NowPlaying?) -> Unit = {}): View {
        var now: NowPlaying? = null
        var dragging = false
        var generation = 0
        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_50)
        val onSurfaceVariant = sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
        val accent = sys(R.color.system_accent1_600, R.color.system_accent1_200)
        val onAccent = sys(R.color.system_accent1_0, R.color.system_accent1_800)
        val pad = sys(R.color.system_neutral2_100, R.color.system_neutral2_800)

        val thumb = ImageView(this).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_play, sys(R.color.system_accent1_50, R.color.system_accent1_100)))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                sys(R.color.system_accent1_400, R.color.system_accent1_600),
                sys(R.color.system_accent2_700, R.color.system_accent2_900),
            )).apply { cornerRadius = dp(10).toFloat() }
        }
        val title = TextView(this).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            text = "Kodi"
            setTextColor(onSurface)
        }
        val status = TextView(this).apply {
            textSize = 13f
            text = "Chiedo a Kodi…"
            setTextColor(onSurfaceVariant)
        }
        val elapsed = TextView(this).apply { textSize = 13f; setTextColor(onSurfaceVariant) }
        val total = TextView(this).apply { textSize = 13f; gravity = Gravity.END; setTextColor(onSurfaceVariant) }
        val volume = TextView(this).apply { textSize = 12f; gravity = Gravity.CENTER; setTextColor(onSurfaceVariant) }

        // Comando sul video in corso; poi si rilegge subito, così il telecomando non resta indietro di un secondo
        lateinit var refresh: () -> Unit
        fun video(action: (Kodi, Int) -> Unit) {
            val id = now?.playerId ?: return toast("Kodi non sta riproducendo niente")
            onKodi({ action(it, id) }, { refresh() })
        }
        fun kodi(action: (Kodi) -> Unit) = onKodi({ action(it) }, { refresh() })

        val bar = SeekBar(this).apply {
            isEnabled = false
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(sys(R.color.system_neutral2_300, R.color.system_neutral2_600))
            setPadding(dp(4), 0, dp(4), 0)
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
        val playPause = ImageButton(this).apply {
            contentDescription = "Play o pausa"
            tooltipText = contentDescription
            background = rounded(accent, 24)
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_pause, onAccent, 32))
            setOnClickListener { video { kodi, id -> kodi.playPause(id) } }
        }

        // "Salta sigla": avanti di quanto vale per la serie in corso (introOf), da dove si è. Al primo tocco su una serie
        // senza tempo si sceglie quanto (scelta di Pippo); la pressione lunga lo cambia, anche con un tempo scritto a mano
        val intro = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(onSurface)
            background = RippleDrawable(ColorStateList.valueOf(0x33808080), GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(pad)
            }, null)
            setCompoundDrawablesRelative(icon(it.franc.casttokodi.R.drawable.ic_skip_next, onSurface, 20), null, null, null)
            compoundDrawablePadding = dp(8)
            setPadding(dp(20), 0, dp(20), 0)
            text = introLabel(introOf(loadShows(), null, generalIntro()))
            contentDescription = "Salta la sigla. Tieni premuto per cambiare di quanto"
            tooltipText = contentDescription
            setOnClickListener {
                if (now?.playerId == null) return@setOnClickListener toast("Kodi non sta riproducendo niente")
                val seconds = introOf(loadShows(), now?.file, generalIntro())
                if (seconds != null) video { kodi, id -> kodi.skip(id, seconds) }
                else chooseIntro(now?.file) { chosen ->
                    text = introLabel(chosen)
                    video { kodi, id -> kodi.skip(id, chosen) }
                }
            }
            setOnLongClickListener {
                chooseIntro(now?.file) { seconds -> text = introLabel(seconds) }
                true
            }
        }

        // Timer di spegnimento attivo: una striscia al posto della pillola, con +15 min e Annulla
        val timerText = TextView(this).apply {
            textSize = 14f
            setTextColor(sys(R.color.system_accent1_900, R.color.system_accent1_100))
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(it.franc.casttokodi.R.drawable.ic_schedule, sys(R.color.system_accent1_900, R.color.system_accent1_100), 20), null, null, null)
            compoundDrawablePadding = dp(10)
        }
        fun timerButton(text: String, onClick: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(sys(R.color.system_accent1_900, R.color.system_accent1_100))
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(sys(R.color.system_accent1_200, R.color.system_accent1_700), 16)
            setOnClickListener { onClick() }
        }
        val timerStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(sys(R.color.system_accent1_100, R.color.system_accent1_800))
            }
            addView(timerText, LinearLayout.LayoutParams(0, -2, 1f))
            addView(timerButton("+15 min") { extendTimer() }, LinearLayout.LayoutParams(-2, dp(32)).apply { marginStart = dp(6) })
            addView(timerButton("Annulla") { cancelTimer() }, LinearLayout.LayoutParams(-2, dp(32)).apply { marginStart = dp(6) })
            visibility = View.GONE
        }

        fun show(playing: NowPlaying?, problem: String?) {
            now = playing
            onRead(playing)
            val timer = activeTimerLabel()
            timerStrip.visibility = if (timer != null) View.VISIBLE else View.GONE
            timerText.text = timer
            if (playing == null) {
                title.text = "Kodi non risponde"
                status.text = problem
                bar.isEnabled = false
                return
            }
            volume.text = if (playing.muted) "Audio muto" else "Volume ${playing.volume}%"
            if (playing.playerId == null) {
                title.text = "Niente in riproduzione"
                status.text = "Kodi è acceso e fermo"
                bar.isEnabled = false
                bar.progress = 0
                elapsed.text = clockLabel(0)
                total.text = clockLabel(0)
                return
            }
            title.text = queueLabel(playing.file, playing.title, loadTitles())
            if (!showPicture(thumb, playing.file)) {
                thumb.scaleType = ImageView.ScaleType.CENTER
                thumb.setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_play, sys(R.color.system_accent1_50, R.color.system_accent1_100)))
            }
            intro.text = introLabel(introOf(loadShows(), playing.file, generalIntro()))
            val end = if (playing.total > 0) "fino alle ${clockAt(System.currentTimeMillis() / 1000 + playing.total - playing.time)}" else "in diretta"
            status.text = if (playing.paused) "In pausa · $end" else end
            playPause.setImageDrawable(icon(
                if (playing.paused) it.franc.casttokodi.R.drawable.ic_play else it.franc.casttokodi.R.drawable.ic_pause, onAccent, 32))
            bar.isEnabled = playing.total > 0
            bar.max = playing.total.toInt().coerceAtLeast(1)
            if (!dragging) {
                bar.progress = playing.time.toInt()
                elapsed.text = clockLabel(playing.time)
            }
            total.text = clockLabel(playing.total)
        }

        // In background: il video in corso, o perché non si sa
        fun read(): Pair<NowPlaying?, String?> = try {
            quietKodi()?.nowPlaying() to "Kodi non ancora scelto"
        } catch (e: Unauthorized) {
            null to "Kodi chiede utente e password: premi un tasto per inserirli"
        } catch (e: Exception) {
            null to "Se la TV è spenta premi \"Accendi la TV\" qui sotto"
        }
        refresh = { thread { read().let { (playing, problem) -> runOnUiThread { show(playing, problem) } } } }

        // Tasti tondi: con lo sfondo della croce, o senza per quelli accanto al play
        fun round(iconId: Int?, text: String?, description: String, filled: Boolean, onClick: () -> Unit): View {
            val shape = RippleDrawable(ColorStateList.valueOf(0x33808080), circle(if (filled) pad else Color.TRANSPARENT), circle(Color.BLACK))
            return if (iconId != null) ImageButton(this).apply {
                setImageDrawable(icon(iconId, onSurface))
                background = shape
                contentDescription = description
                tooltipText = description
                setOnClickListener { onClick() }
            } else TextView(this).apply {
                this.text = text
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(onSurface)
                background = shape
                contentDescription = description
                setOnClickListener { onClick() }
            }
        }
        val transport = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            fun add(view: View, size: Int, height: Int = size) = addView(view, LinearLayout.LayoutParams(dp(size), dp(height)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            })
            add(round(it.franc.casttokodi.R.drawable.ic_skip_previous, null, "Video precedente della coda", false) { video { kodi, id -> kodi.previous(id) } }, 48)
            add(round(null, "−10", "Indietro di 10 secondi", true) { video { kodi, id -> kodi.skip(id, -10) } }, 48)
            add(playPause, 80, 64)
            add(round(null, "+10", "Avanti di 10 secondi", true) { video { kodi, id -> kodi.skip(id, 10) } }, 48)
            add(round(it.franc.casttokodi.R.drawable.ic_skip_next, null, "Video successivo della coda, o episodio dopo", false) {
                if (now?.playerId == null) return@round toast("Kodi non sta riproducendo niente")
                val file = now?.file
                onKodi({ kodi -> kodi.state().also { if (it.hasNext) kodi.next(it.playerId!!) } }, { state ->
                    if (state.hasNext) refresh() else nextEpisode(file)
                })
            }, 48)
        }

        // Croce tonda: un cerchio con le frecce sui quattro lati e OK al centro, come il telecomando della Fire TV
        fun arrow(iconId: Int, description: String, key: String) = ImageButton(this).apply {
            setImageDrawable(icon(iconId, onSurface, 28))
            setBackgroundResource(attr(R.attr.selectableItemBackgroundBorderless))
            contentDescription = description
            tooltipText = description
            setOnClickListener { kodi { it.press(key) } }
        }
        val ok = TextView(this).apply {
            text = "OK"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(onAccent)
            background = RippleDrawable(ColorStateList.valueOf(0x33808080), circle(accent), null)
            setOnClickListener { kodi { it.press("Select") } }
        }
        val dpad = FrameLayout(this).apply {
            background = circle(pad)
            fun put(view: View, size: Int, gravity: Int) = addView(view, FrameLayout.LayoutParams(dp(size), dp(size), gravity))
            put(ok, 92, Gravity.CENTER)
            put(arrow(it.franc.casttokodi.R.drawable.ic_arrow_up, "Su", "Up"), 64, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            put(arrow(it.franc.casttokodi.R.drawable.ic_arrow_down, "Giù", "Down"), 64, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            put(arrow(it.franc.casttokodi.R.drawable.ic_arrow_left, "Sinistra", "Left"), 64, Gravity.START or Gravity.CENTER_VERTICAL)
            put(arrow(it.franc.casttokodi.R.drawable.ic_arrow_right, "Destra", "Right"), 64, Gravity.END or Gravity.CENTER_VERTICAL)
        }
        val keys = LinearLayout(this).apply {
            listOf(
                round(it.franc.casttokodi.R.drawable.ic_back, null, "Indietro", true) { kodi { it.press("Back") } },
                round(it.franc.casttokodi.R.drawable.ic_home, null, "Home di Kodi", true) { kodi { it.press("Home") } },
                round(it.franc.casttokodi.R.drawable.ic_volume_down, null, "Volume giù", true) { kodi { it.volume(up = false) } },
                round(it.franc.casttokodi.R.drawable.ic_volume_up, null, "Volume su", true) { kodi { it.volume(up = true) } },
            ).forEach { button ->
                addView(FrameLayout(context).apply {
                    addView(button, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
                }, LinearLayout.LayoutParams(0, dp(56), 1f))
            }
        }

        // Il resto, meno usato: pillole in una fila che scorre di lato
        fun pill(text: String, onClick: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(onSurfaceVariant)
            setPadding(dp(16), 0, dp(16), 0)
            background = RippleDrawable(ColorStateList.valueOf(0x33808080), GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), sys(R.color.system_neutral2_400, R.color.system_neutral2_600))
            }, null)
            setOnClickListener { onClick() }
        }
        val more = LinearLayout(this).apply {
            listOf(
                pill("Timer") { chooseTimer() },
                pill("Accendi la TV") { wakeKodi() },
                pill("−30 s") { video { kodi, id -> kodi.skip(id, -30) } },
                pill("+30 s") { video { kodi, id -> kodi.skip(id, 30) } },
                pill("Vai al minuto") { now?.let { askSeek(it) { seconds -> video { kodi, id -> kodi.seekTo(id, seconds) } } } },
                pill("Audio") { now?.takeIf { it.playerId != null }?.let { chooseAudio(it) { track -> video { kodi, id -> kodi.setAudio(id, track) } } } },
                pill("Sottotitoli") {
                    val playing = now?.takeIf { it.playerId != null } ?: return@pill toast("Kodi non sta riproducendo niente")
                    showSubtitles(playing, { track -> video { kodi, id -> kodi.setSubtitle(id, track) } }, { kodi { it.openSubtitleSearch() } })
                },
                pill("Velocità") {
                    val id = now?.playerId ?: return@pill toast("Kodi non sta riproducendo niente")
                    chooseTempo { tempo -> tempoAt(tempo, id) }
                },
                pill("Muto") { kodi { it.toggleMute() } },
                pill("Menu") { kodi { it.press("ContextMenu") } },
                pill("Scrivi") { askText { text -> kodi { it.sendText(text) } } },
                pill("Ferma") {
                    if (now?.playerId == null) return@pill toast("Kodi non sta riproducendo niente")
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Fermare il video su Kodi? La coda resta.")
                        .setPositiveButton("Ferma") { _, _ -> video { kodi, id -> kodi.stop(id) } }
                        .setNegativeButton("Annulla", null)
                        .show()
                },
            ).forEach { addView(it, LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(8) }) }
        }
        val hint = label("Durante il video su e giù di Kodi saltano di 10 minuti: per spostarti usa la barra o −10 e +10.").apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(onSurfaceVariant)
        }

        val nowRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(thumb, LinearLayout.LayoutParams(dp(80), dp(48)))
            addView(section(title, status), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        }
        val times = LinearLayout(this).apply {
            setPadding(dp(4), 0, dp(4), 0)
            addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
            addView(total, LinearLayout.LayoutParams(0, -2, 1f))
        }
        fun gap(view: View, top: Int, width: Int = -1, height: Int = -2, gravity: Int = Gravity.NO_GRAVITY) =
            view to LinearLayout.LayoutParams(if (width > 0) dp(width) else width, if (height > 0) dp(height) else height).apply {
                topMargin = dp(top)
                this.gravity = gravity
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
            listOf(
                gap(nowRow, 0),
                gap(bar, 16, height = 24),
                gap(times, 2),
                gap(transport, 16),
                gap(intro, 16, width = -2, height = 44, gravity = Gravity.CENTER_HORIZONTAL),
                gap(dpad, 24, 216, 216, Gravity.CENTER_HORIZONTAL),
                gap(keys, 24),
                gap(volume, 4),
                gap(timerStrip, 20),
                gap(HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(more)
                }, 12),
                gap(hint, 16),
            ).forEach { (view, params) -> addView(view, params) }
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

    // Impostazioni: ogni voce dice com'è adesso, senza doverla aprire. Toccandola si cambia.
    private fun showSettings() {
        lateinit var dialog: Dialog
        val host = prefs.getString("host", "").orEmpty()
        val login = savedLogin(host, currentNetwork())
        val adbHost = prefs.getString("adbHost", null)
        val autoNext = prefs.getInt("autoNext", DEFAULT_AUTO_NEXT)
        fun then(action: () -> Unit): () -> Unit = { dialog.dismiss(); action() }
        val content = section(
            settingsHeading("Kodi"),
            settingsRow(it.franc.casttokodi.R.drawable.ic_remote,
                prefs.getString("name", "").orEmpty().ifBlank { "Dov'è Kodi?" },
                if (host.isBlank()) "Non ancora scelto" else listOfNotNull(
                    host,
                    if (prefs.getBoolean("manual", false)) "scritto a mano" else "trovato da solo",
                    if (login != null) "accesso con ${login.first}" else null,
                ).joinToString(" · "),
                then { askKodiAddress() }),
            settingsRow(it.franc.casttokodi.R.drawable.ic_power, "Accendi la TV e apri Kodi",
                if (adbHost != null) "Fire TV a $adbHost" else "Serve il debug ADB sulla Fire TV",
                then { wakeKodi() }),
            settingsHeading("Siti"),
            settingsRow(it.franc.casttokodi.R.drawable.ic_queue, "AnimeUnity: episodi successivi", autoNextLabel(autoNext),
                then { askAutoNext() }),
            settingsHeading("Cronologia"),
            settingsRow(it.franc.casttokodi.R.drawable.ic_history, "Ricorda dove sono arrivato", historyLabel(),
                then { toggleHistory() }),
            settingsRow(it.franc.casttokodi.R.drawable.ic_delete, "Cancella la cronologia", "Toglie le serie e l'ultima pagina dalla pagina iniziale",
                then { askClearHistory() }),
            settingsHeading("Tendina"),
            settingsRow(it.franc.casttokodi.R.drawable.ic_play, "Controlli nella tendina",
                if (KodiPlayer.enabled(this)) "Acceso: player di Android mentre Kodi riproduce" else "Spento",
                then { toggleMediaControls() }),
            settingsRow(it.franc.casttokodi.R.drawable.ic_cast, "Aggiungi il riquadro", "Couch Cast tra le impostazioni rapide",
                then { askAddTile() }),
            settingsHeading("Backup"),
            settingsRow(it.franc.casttokodi.R.drawable.ic_download, "Salva un backup", backupLabel(),
                then { askBackup() }),
            settingsRow(it.franc.casttokodi.R.drawable.ic_upload, "Ripristina da un backup", "Da un file salvato prima",
                then { askRestore() }),
            settingsHeading("Aiuto"),
            settingsRow(it.franc.casttokodi.R.drawable.ic_article, "Log di Kodi", "Le ultime righe, da condividere se un video non parte",
                then { showKodiLog() }),
            settingsRow(it.franc.casttokodi.R.drawable.ic_help, "Come si usa", "La guida passo passo",
                then { open(TUTORIAL) }),
            settingsRow(it.franc.casttokodi.R.drawable.ic_info, "Versione ${installedVersion()}", "Tocca per cercare aggiornamenti",
                then { checkUpdate(asked = true) }),
        ).apply { setPadding(dp(16), 0, dp(16), dp(8)) }
        dialog = sheet("Impostazioni", content)
    }

    private fun askAutoNext() {
        val options = (0..MAX_AUTO_NEXT).map { autoNextLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("AnimeUnity: episodi successivi")
            .setSingleChoiceItems(options, prefs.getInt("autoNext", DEFAULT_AUTO_NEXT).coerceIn(0, MAX_AUTO_NEXT)) { d, i ->
                prefs.edit().putInt("autoNext", i).apply()
                d.dismiss()
                showSettings()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun settingsHeading(text: String) = homeHeading(text).apply { setPadding(dp(8), dp(16), 0, dp(4)) }

    // Riga delle Impostazioni: icona su un cerchio, nome e, sotto, com'è adesso
    private fun settingsRow(iconId: Int, title: String, value: String, onClick: () -> Unit): View {
        val badge = ImageView(this).apply {
            setImageDrawable(icon(iconId, sys(R.color.system_accent2_900, R.color.system_accent2_100), 20))
            scaleType = ImageView.ScaleType.CENTER
            background = circle(sys(R.color.system_accent2_100, R.color.system_accent2_700))
        }
        val detail = homeText(value, 13f, bold = false).apply { maxLines = 2 }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundResource(attr(R.attr.selectableItemBackground))
            setOnClickListener { onClick() }
            addView(badge, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(section(homeText(title, 16f, bold = false).apply {
                setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
            }, detail), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        }
    }

    // Pagina iniziale al posto del sito: Kodi (stato e accensione), l'ultima pagina guardata e i preferiti come icone
    private fun showHome() {
        home.visibility = View.VISIBLE
        banner.visibility = View.GONE
        if (!address.hasFocus()) address.setText("")
        showSiteIcon(null)
        blocked.clear()
        refreshShield()
        refreshHome()
        refreshCastButton()
        readSavedPoints()
    }

    private fun refreshHome() {
        homeContent.removeAllViews()
        homeContent.addView(kodiCard())
        refreshKodiCard()

        // Continua a guardare: una scheda per serie, la più recente a sinistra, in una fila che scorre di lato
        tileUpdaters.clear()
        val shows = if (historyOn()) loadShows() else emptyList()
        if (shows.isNotEmpty()) {
            homeContent.addView(homeHeading("Continua a guardare"))
            val width = ((resources.configuration.screenWidthDp - 32) * 0.76f).toInt()
            val row = LinearLayout(this).apply { setPadding(0, dp(4), dp(16), dp(4)) }
            shows.forEachIndexed { i, show ->
                row.addView(showTile(show, width), LinearLayout.LayoutParams(dp(width), -2).apply { if (i > 0) marginStart = dp(12) })
            }
            homeContent.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(row)
            }, LinearLayout.LayoutParams(-1, -2).apply { marginEnd = dp(-16) })
        }

        // L'ultima pagina, se non è già un episodio della fila sopra; la ✕ la toglie (con Annulla)
        val last = prefs.getString("last", null)
        if (historyOn() && lastPageShown(last, shows)) {
            homeContent.addView(homeHeading("Ultima pagina"))
            homeContent.addView(lastPageCard(last!!))
        }

        homeContent.addView(homeHeading("Preferiti"))
        val favorites = loadFavorites()
        if (favorites.isEmpty()) {
            homeContent.addView(label("Apri un sito e premi ★ in alto: lo ritrovi qui."))
        }
        favorites.chunked(4).forEach { row ->
            homeContent.addView(LinearLayout(this).apply {
                row.forEach { favorite -> addView(favoriteTile(favorite), LinearLayout.LayoutParams(0, -2, 1f)) }
                repeat(4 - row.size) { addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }

        homeContent.addView(label("Per mandare un video: apri l'episodio e avvia il player sul sito. \"Manda a Kodi\" compare da solo in basso.").apply {
            textSize = 13f
            setTextColor(sys(R.color.system_neutral2_600, R.color.system_neutral2_400))
            setPadding(dp(4), dp(24), dp(4), 0)
        })
    }

    // Riquadro di Kodi in cima alla pagina iniziale. Il testo lo aggiorna refreshKodiCard a ogni lettura del mini-player.
    private fun kodiCard(): View {
        homeKodiTitle = homeText("", 15f, bold = true)
        homeKodiStatus = homeText("", 13f, bold = false).apply { maxLines = 2 }
        homeKodiButton = Button(this).apply {
            isAllCaps = false
            stateListAnimator = null
            background = rounded(sys(R.color.system_accent1_600, R.color.system_accent1_200), 18)
            setTextColor(sys(R.color.system_accent1_0, R.color.system_accent1_800))
            setPadding(dp(16), 0, dp(16), 0)
        }
        val badge = ImageView(this).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_remote, sys(R.color.system_accent2_900, R.color.system_accent2_100)))
            scaleType = ImageView.ScaleType.CENTER
            background = circle(sys(R.color.system_accent2_100, R.color.system_accent2_700))
        }
        return homeCard().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(section(homeKodiTitle, homeKodiStatus), LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(16)
                marginEnd = dp(8)
            })
            addView(homeKodiButton, LinearLayout.LayoutParams(-2, dp(36)))
        }
    }

    private fun refreshKodiCard() {
        if (!::homeKodiTitle.isInitialized) return
        val host = prefs.getString("host", "").orEmpty()
        val name = prefs.getString("name", "").orEmpty().ifBlank { "Kodi" }
        val playing = miniNow?.takeIf { it.playerId != null }
        homeKodiTitle.text = if (host.isBlank()) "Kodi non ancora scelto" else name
        homeKodiStatus.text = when {
            host.isBlank() -> "Scegli la Fire TV con Kodi per mandarle i video"
            playing != null -> "Sta riproducendo: ${queueLabel(playing.file, playing.title, loadTitles())}"
            kodiReachable == true -> "Acceso, nessun video in corso"
            kodiReachable == false -> "Spento o non raggiungibile"
            else -> "Controllo…"
        }
        val (label, action) = when {
            host.isBlank() -> "Scegli" to { askKodiAddress() }
            kodiReachable == true -> "Telecomando" to { showPlayer() }
            else -> "Accendi" to { wakeKodi() }
        }
        homeKodiButton.text = label
        homeKodiButton.setOnClickListener { action() }
    }

    private fun homeCard() = LinearLayout(this).apply {
        setPadding(dp(16), dp(12), dp(12), dp(12))
        background = rounded(sys(R.color.system_neutral1_50, R.color.system_neutral1_800), 16)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun homeHeading(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(sys(R.color.system_accent1_600, R.color.system_accent1_200))
        setPadding(dp(4), dp(24), 0, dp(4))
    }

    private fun homeText(text: String, size: Float, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (bold) sys(R.color.system_neutral1_900, R.color.system_neutral1_50) else sys(R.color.system_neutral2_600, R.color.system_neutral2_400))
    }

    // Preferito come icona con il nome sotto; pressione lunga per rinominarlo o toglierlo
    private fun favoriteTile(favorite: Favorite): View {
        val name = TextView(this).apply {
            text = favorite.title
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(sys(R.color.system_neutral2_700, R.color.system_neutral2_200))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(attr(R.attr.selectableItemBackground))
            contentDescription = favorite.title
            setOnClickListener { open(favorite.url) }
            setOnLongClickListener { tile ->
                favoriteMenu(tile, favorite) { changed ->
                    val list = loadFavorites()
                    val i = list.indexOfFirst { it.url == favorite.url && it.title == favorite.title }
                    if (i >= 0) {
                        if (changed == null) list.removeAt(i) else list[i] = changed
                        saveFavorites(list)
                    }
                }
                true
            }
            addView(favoriteIcon(favorite), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    // Barra in basso con lo stato di Kodi: se riproduce, play/pausa, nome, tempo e una riga di avanzamento; se è fermo
    // il suo nome; se non risponde, il pulsante per accenderlo. Toccandola si apre il telecomando. C'è sempre, appena
    // Kodi è scelto: è la strada diretta al telecomando e alla coda.
    private fun miniPlayer(): View {
        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_100)
        val onSurfaceVariant = sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
        miniThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        miniPlay = ImageButton(this).apply {
            contentDescription = "Play o pausa su Kodi"
            tooltipText = contentDescription
            setBackgroundResource(attr(R.attr.selectableItemBackgroundBorderless))
            setOnClickListener {
                val playing = miniNow ?: return@setOnClickListener wakeKodi()
                val id = playing.playerId ?: return@setOnClickListener
                onKodi({ kodi -> kodi.playPause(id) }, { readMini() })
            }
        }
        miniTitle = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(onSurface)
        }
        miniStatus = TextView(this).apply {
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            compoundDrawablePadding = dp(6)
            setTextColor(onSurfaceVariant)
        }
        val remote = ImageButton(this).apply {
            contentDescription = "Apri il telecomando"
            tooltipText = contentDescription
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_remote, onSurface))
            setBackgroundResource(attr(R.attr.selectableItemBackgroundBorderless))
            setOnClickListener { showPlayer() }
        }
        miniProgress = ProgressBar(this, null, R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(sys(R.color.system_accent1_600, R.color.system_accent1_200))
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        // Come nel mockup: immagine, nome e stato con il pallino, poi play/pausa e telecomando, tutti centrati sulla
        // stessa riga alta 64 dp; la riga di avanzamento è il bordo in basso della barra
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            addView(miniThumb, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(section(miniTitle, miniStatus), LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(4)
            })
            addView(miniPlay, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(remote, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        return FrameLayout(this).apply {
            // Stesso colore e stessi bordi della barra a pillola in alto
            background = rounded(sys(R.color.system_neutral1_100, R.color.system_neutral1_800), 20)
            clipToOutline = true
            isClickable = true
            contentDescription = "Kodi: tocca per il telecomando"
            setOnClickListener { showPlayer() }
            addView(row, FrameLayout.LayoutParams(-1, dp(64)))
            addView(miniProgress, FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM))
            visibility = View.GONE
        }
    }

    // Rilegge Kodi ogni 3 secondi finché l'app è in primo piano. In silenzio: se Kodi non risponde la barra sparisce
    // e basta, gli errori li mostrano l'invio e il telecomando.
    private fun startMini() {
        val mine = ++miniGeneration
        thread {
            while (mine == miniGeneration) {
                val playing = quietNowPlaying()
                runOnUiThread { if (mine == miniGeneration) showMini(playing) }
                Thread.sleep(3000)
            }
        }
    }

    // Una lettura subito, dopo un invio o un comando, per non aspettare il giro successivo
    private fun readMini() {
        thread { quietNowPlaying().let { runOnUiThread { showMini(it) } } }
    }

    private fun quietNowPlaying(): NowPlaying? {
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return null
        val login = savedLogin(host, currentNetwork())
        return try {
            val kodi = Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
            kodi.nowPlaying().also { KodiAccess.autoSubtitles(prefs, kodi, it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun showMini(playing: NowPlaying?) {
        miniNow = playing
        if (playing?.playerId != null && playing.file.isNotBlank()) trackProgress(playing)
        kodiReachable = playing != null
        refreshKodiCard()
        if (prefs.getString("host", "").isNullOrBlank()) {
            mini.visibility = View.GONE
            return
        }
        mini.visibility = View.VISIBLE
        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_100)
        val video = playing?.playerId != null
        // Immagine: quella della serie se il video è di una serie nota, se no colorata con ▶ se c'è un video,
        // altrimenti tonda neutra con il telecomando
        miniThumb.scaleType = ImageView.ScaleType.CENTER
        miniThumb.setImageDrawable(icon(
            if (video) it.franc.casttokodi.R.drawable.ic_play else it.franc.casttokodi.R.drawable.ic_remote,
            if (video) sys(R.color.system_accent1_50, R.color.system_accent1_100) else sys(R.color.system_neutral2_700, R.color.system_neutral2_200),
        ))
        miniThumb.background = if (video) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            sys(R.color.system_accent1_400, R.color.system_accent1_600),
            sys(R.color.system_accent2_700, R.color.system_accent2_900),
        )).apply { cornerRadius = dp(10).toFloat() } else circle(sys(R.color.system_neutral2_200, R.color.system_neutral2_700))
        if (video) showPicture(miniThumb, playing?.file)
        // Pallino: acceso se Kodi risponde, spento se no
        val dot = if (playing != null) sys(R.color.system_accent1_500, R.color.system_accent1_200) else sys(R.color.system_neutral2_400, R.color.system_neutral2_500)
        miniStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(circle(dot).apply { setSize(dp(8), dp(8)) }, null, null, null)
        if (playing == null || !video) {
            miniTitle.text = prefs.getString("name", "").orEmpty().ifBlank { "Kodi" }
            miniStatus.text = if (playing != null) "Acceso, niente in riproduzione" else "Spento o non raggiungibile"
            miniProgress.visibility = View.GONE
            // Spento: il pulsante accende la TV; acceso e fermo: non serve
            miniPlay.visibility = if (playing == null) View.VISIBLE else View.GONE
            miniPlay.setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_power, onSurface))
            miniPlay.contentDescription = "Accendi la TV e apri Kodi"
            return
        }
        miniProgress.visibility = View.VISIBLE
        miniPlay.visibility = View.VISIBLE
        KodiPlayer.startIfWanted(this)
        miniTitle.text = queueLabel(playing.file, playing.title, loadTitles())
        miniStatus.text = miniPlayerStatus(playing.paused, playing.time, playing.total) + (timerAt()?.let { " · timer ${clockAt(it)}" } ?: "")
        miniProgress.progress = if (playing.total > 0) (playing.time * 1000 / playing.total).toInt() else 0
        miniPlay.setImageDrawable(icon(
            if (playing.paused) it.franc.casttokodi.R.drawable.ic_play else it.franc.casttokodi.R.drawable.ic_pause,
            onSurface, 28,
        ))
        miniPlay.contentDescription = if (playing.paused) "Riprendi su Kodi" else "Metti in pausa su Kodi"
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

    // Il tempo per i video che non sono di una serie nota; null finché non lo si sceglie
    private fun generalIntro(): Int? = if (prefs.contains("intro")) prefs.getInt("intro", DEFAULT_INTRO) else null

    // Di quanto salta "Salta sigla": per la serie del video in corso, o per tutti se il video non è di una serie nota
    // Sfondo della scheda di una serie: la copertina, l'immagine dell'episodio o l'intervallo da cui prenderla
    private fun chooseBackground(key: String) {
        val show = loadShows().firstOrNull { it.key == key } ?: return
        val (from, to) = show.frameRange
        val episodeImage = show.episodes.lastOrNull { it.image != null }?.image
        choiceSheet("Sfondo", listOf(
            Choice("Copertina", "L'immagine della serie presa dal sito", show.image?.let { loadFrame(it) }),
            Choice("Immagine dell'episodio", "Presa a caso tra $from% e $to% del video, dopo la sigla", episodeImage?.let { loadFrame(it) }),
            Choice("Cambia intervallo…", "Da che punto a che punto dell'episodio prenderla"),
        ), if (show.cover) 0 else 1) { i ->
            when (i) {
                0 -> updateShows { withBackground(it, key, cover = true) }
                1 -> updateShows { withBackground(it, key, cover = false) }
                else -> askFrameRange(show) { range -> updateShows { withBackground(it, key, cover = false, range = range) } }
            }
            showPanel(key)
        }
    }

    // Una scelta di choiceSheet: titolo, riga sotto e, se c'è, un'immagine 16:9 a sinistra
    private class Choice(val title: String, val detail: String? = null, val image: Bitmap? = null)

    // Pannello dal basso con un elenco di scelte, la spunta su quella attuale (checked, -1 nessuna); al tocco si chiude
    // e chiama pick con la posizione
    private fun choiceSheet(title: String, choices: List<Choice>, checked: Int, pick: (Int) -> Unit) {
        lateinit var dialog: Dialog
        val list = section().apply { setPadding(dp(16), 0, dp(16), dp(16)) }
        choices.forEachIndexed { i, choice ->
            val selected = i == checked
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = if (selected) rounded(sys(R.color.system_accent1_100, R.color.system_accent1_800), 16)
                    else RippleDrawable(ColorStateList.valueOf(0x33808080), null, GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) })
                setOnClickListener { dialog.dismiss(); pick(i) }
            }
            choice.image?.let { picture ->
                row.addView(ImageView(this).apply {
                    setImageBitmap(picture)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(sys(R.color.system_neutral2_200, R.color.system_neutral2_700)) }
                    clipToOutline = true
                }, LinearLayout.LayoutParams(dp(96), dp(54)).apply { marginEnd = dp(16) })
            }
            val name = homeText(choice.title, 16f, bold = false).apply {
                setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
                if (selected) typeface = Typeface.DEFAULT_BOLD
            }
            val texts = choice.detail?.let { section(name, homeText(it, 13f, bold = false).apply { maxLines = 2 }) } ?: section(name)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            if (selected) row.addView(ImageView(this).apply {
                setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_check, sys(R.color.system_accent1_600, R.color.system_accent1_200)))
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(8) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        dialog = sheet(title, list)
    }

    // Impostazioni della serie dal suo pannello: tornano al pannello dopo la scelta, così si vede com'è adesso
    private fun renameShow(show: Show) {
        val field = EditText(this).apply {
            setText(show.title)
            selectAll()
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        lateinit var dialog: AlertDialog
        val go = {
            val title = field.text.toString().trim()
            if (title.isEmpty()) toast("Scrivi un nome")
            else {
                updateShows { withTitle(it, show.key, title) }
                dialog.dismiss()
                showPanel(show.key)
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Nome della serie")
            .setMessage("Il sito non lo cambierà più.")
            .setView(section(field).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        field.setOnEditorActionListener { _, _, _ -> go(); true }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { go() }
        field.requestFocus()
    }

    private fun chooseRewind(show: Show) {
        choiceSheet("Riprendi", REWIND_CHOICES.map { Choice(rewindLabel(it)) }, REWIND_CHOICES.indexOf(show.rewind)) { i ->
            updateShows { withRewind(it, show.key, REWIND_CHOICES[i]) }
            showPanel(show.key)
        }
    }

    private fun chooseAutoNext(show: Show) {
        val general = prefs.getInt("autoNext", DEFAULT_AUTO_NEXT)
        val counts = listOf<Int?>(null) + (0..MAX_AUTO_NEXT)
        val choices = counts.map { count ->
            if (count == null) Choice("Come nelle Impostazioni", autoNextLabel(general)) else Choice(autoNextLabel(count))
        }
        choiceSheet("Episodi successivi", choices, counts.indexOf(show.autoNext)) { i ->
            updateShows { withAutoNext(it, show.key, counts[i]) }
            showPanel(show.key)
        }
    }

    private fun askFrameRange(show: Show, done: (Pair<Int, Int>) -> Unit) {
        fun field(value: Int, label: String) = EditText(this).apply {
            hint = label
            setText(value.toString())
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val (from, to) = show.frameRange
        val start = field(from, "da %")
        val end = field(to, "a %")
        val row = LinearLayout(this).apply {
            addView(start, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply { text = "–"; setPadding(dp(12), 0, dp(12), 0) })
            addView(end, LinearLayout.LayoutParams(0, -2, 1f))
            gravity = Gravity.CENTER_VERTICAL
        }
        lateinit var dialog: AlertDialog
        val go = {
            val range = parseFrameRange(start.text.toString(), end.text.toString())
            if (range == null) toast("Scrivi due percentuali, la seconda almeno 5 più della prima, fino a 95")
            else {
                done(range)
                dialog.dismiss()
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Intervallo dell'immagine")
            .setMessage("Da che punto a che punto dell'episodio prendere l'immagine, in percentuale. Vale dal prossimo invio.")
            .setView(section(row).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { go() }
        start.requestFocus()
    }

    private fun chooseIntro(file: String?, done: (Int) -> Unit) = chooseIntroOf(showOfFile(loadShows(), file), done)

    private fun chooseIntroOf(show: Show?, done: (Int) -> Unit) {
        val current = if (show != null) show.intro else generalIntro()
        val title = if (show != null) "Salta sigla: ${show.title}" else "Salta sigla"
        fun save(seconds: Int) {
            if (show != null) updateShows { withIntro(it, show.key, seconds) } else prefs.edit().putInt("intro", seconds).apply()
            done(seconds)
        }
        // In fondo il tempo scritto a mano: con quello attuale se non è tra i soliti
        val custom = if (current == null || current in INTRO_CHOICES) "Altro tempo…" else "Altro tempo: ${introTime(current)}"
        val choices = INTRO_CHOICES.map { Choice("Avanti di ${introTime(it)}") } + Choice(custom)
        val checked = if (current == null) -1 else INTRO_CHOICES.indexOf(current).takeIf { it >= 0 } ?: INTRO_CHOICES.size
        choiceSheet(title, choices, checked) { i ->
            if (i < INTRO_CHOICES.size) save(INTRO_CHOICES[i]) else askIntro(title, current ?: DEFAULT_INTRO, ::save)
        }
    }

    private fun askIntro(title: String, current: Int, done: (Int) -> Unit) {
        val field = EditText(this).apply {
            hint = "es. 1:25 o 85"
            setText(clockLabel(current.toLong()))
            selectAll()
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        lateinit var dialog: AlertDialog
        val go = {
            val seconds = parseIntro(field.text.toString())
            if (seconds == null) toast("Scrivi minuti:secondi (1:25) o i secondi (85), fino a ${clockLabel(MAX_INTRO.toLong())}")
            else {
                done(seconds)
                dialog.dismiss()
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Di quanto va avanti il tasto.")
            .setView(section(field).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        field.setOnEditorActionListener { _, _, _ -> go(); true }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
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

    // Pannello dei sottotitoli del telecomando (2026-10-27, mockup approvato): le tracce di Kodi con da dove arrivano,
    // e per una serie nota "Accendili sempre" (Show.subtitles, poi KodiAccess.autoSubtitles). set(null): spenti.
    // Senza tracce, la ricerca dei sottotitoli di Kodi (search), che serve un add-on installato in Kodi
    private fun showSubtitles(playing: NowPlaying, set: (Track?) -> Unit, search: () -> Unit) {
        if (playing.subtitles.isEmpty()) {
            val content = section(
                homeText("Né il video né la pagina hanno sottotitoli.", 15f, bold = false).apply { setPadding(0, dp(4), 0, dp(8)) },
                homeText("Kodi può cercarli con un suo add-on di sottotitoli (es. OpenSubtitles), se l'hai installato: la ricerca si apre sulla TV.", 13f, bold = false),
            ).apply { setPadding(dp(24), 0, dp(24), dp(8)) }
            sheet("Sottotitoli", content, "Cerca con l'add-on di Kodi" to search)
            return
        }
        var chosen = if (playing.subtitlesOn) playing.subtitles.getOrNull(playing.currentSubtitle) else null
        val show = showOfFile(loadShows(), playing.file)
        val list = section()
        fun draw() {
            list.removeAllViews()
            fun row(view: View) = list.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
            row(pickRow("Spenti", null, chosen == null) { chosen = null; set(null); draw() })
            playing.subtitles.forEachIndexed { i, track ->
                row(pickRow(subtitleTitle(track, i + 1), subtitleSource(track), chosen?.index == track.index) { chosen = track; set(track); draw() })
            }
            val language = chosen?.let { trackLanguage(it) }
            val current = show?.let { s -> loadShows().firstOrNull { it.key == s.key } }
            if (current != null && language != null) {
                list.addView(settingsHeading("Per questa serie"))
                val name = languageName(language)?.lowercase(Locale.ITALIAN) ?: language
                val on = current.subtitles == language
                row(pickRow("Accendili sempre in $name", "Anche negli episodi dopo e in coda", on) {
                    saveShows(withShowSubtitles(loadShows(), current.key, if (on) null else language))
                    draw()
                })
            } else if (current?.subtitles != null) {
                list.addView(settingsHeading("Per questa serie"))
                row(pickRow("Accesi da soli in ${languageName(current.subtitles)?.lowercase(Locale.ITALIAN) ?: current.subtitles}", "Tocca per non accenderli più", true) {
                    saveShows(withShowSubtitles(loadShows(), current.key, null))
                    draw()
                })
            }
        }
        draw()
        sheet("Sottotitoli", list.apply { setPadding(dp(16), 0, dp(16), dp(8)) })
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
    private fun clearQueue(current: Int, done: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(if (current >= 0) "Togliere dalla coda tutti i video tranne quello in corso?" else "Togliere tutti i video dalla coda?")
            .setPositiveButton("Svuota") { _, _ ->
                onKodi({ kodi -> kodi.clearQueue(current) }, { done() })
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Nomi dei video mandati a Kodi (link senza header -> nome della pagina), per mostrarli nella coda
    private fun loadTitles(): Map<String, String> = KodiAccess.titles(prefs)

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
    private fun currentNetwork(): String? = KodiAccess.currentNetwork(this)

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
    private fun loadLogins(): List<SavedLogin> = KodiAccess.loadLogins(prefs)

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
    private fun seal(text: String): String = KodiAccess.seal(text)

    private fun unseal(secret: String): String? = KodiAccess.unseal(secret)

    // ---- Continua a guardare (2026-10-24): le serie ferme all'episodio giusto. La logica è in Watching.kt ----

    private fun historyOn() = prefs.getBoolean("history", true)

    private fun historyLabel(): String {
        if (!historyOn()) return "Spento: la pagina iniziale non mostra serie né ultima pagina"
        val series = when (val count = loadShows().size) {
            0 -> "nessuna serie"
            1 -> "1 serie"
            else -> "$count serie"
        }
        return "Acceso: " + if (prefs.getString("last", null) != null) "$series e l'ultima pagina" else series
    }

    // Spento: non si salva più niente e la pagina iniziale perde le due sezioni; quello che c'era resta, finché non
    // si cancella
    private fun toggleHistory() {
        prefs.edit().putBoolean("history", !historyOn()).apply()
        toast(if (historyOn()) "Ricordo di nuovo dove sei arrivato" else "Non ricordo più serie né ultima pagina")
        if (home.visibility == View.VISIBLE) refreshHome()
        showSettings()
    }

    private fun askClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Cancellare la cronologia?")
            .setMessage("Le serie di Continua a guardare e l'ultima pagina spariscono dalla pagina iniziale. Kodi, la coda e i preferiti restano.")
            .setPositiveButton("Cancella") { _, _ ->
                prefs.edit().remove("shows").remove("last").remove("lastTitle").remove("lastAt").apply()
                cleanFrames()
                if (home.visibility == View.VISIBLE) refreshHome()
                toast("Cronologia cancellata")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---- Timer di spegnimento (2026-10-27): scelte e comando in SleepTimer.kt, ripiego sul telefono in TimerReceiver ----

    // Ora (secondi) in cui scatta il timer attivo, null se non ce n'è
    private fun timerAt(): Long? = prefs.getLong("timerAt", 0).takeIf { it > System.currentTimeMillis() / 1000 }

    private fun activeTimerLabel(): String? = timerAt()?.let { timerLabel(timerAction(prefs.getString("timerAction", null)), it) }

    // Prima si chiede a Kodi a che punto è, per offrire "Fine di questo episodio" e "Fine della coda" con l'ora giusta
    private fun chooseTimer() {
        thread {
            val kodi = quietKodi()
            val playing = runCatching { kodi?.nowPlaying() }.getOrNull()
            val state = if (playing?.playerId != null) runCatching { kodi?.state() }.getOrNull() else null
            runOnUiThread { showTimerSheet(timerChoices(playing, state, System.currentTimeMillis() / 1000)) }
        }
    }

    // Una riga che si sceglie toccandola, evidenziata quando è quella scelta (come in choiceSheet, ma senza chiudere)
    private fun pickRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        setPadding(dp(16), dp(6), dp(16), dp(6))
        background = if (selected) rounded(sys(R.color.system_accent1_100, R.color.system_accent1_800), 16)
            else RippleDrawable(ColorStateList.valueOf(0x33808080), null, GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) })
        setOnClickListener { onClick() }
        val name = homeText(title, 16f, bold = selected).apply { setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50)) }
        addView(detail?.let { section(name, homeText(it, 13f, bold = false)) } ?: section(name), LinearLayout.LayoutParams(0, -2, 1f))
        if (selected) addView(ImageView(context).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_check, sys(R.color.system_accent1_600, R.color.system_accent1_200)))
            scaleType = ImageView.ScaleType.CENTER
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
    }

    private fun showTimerSheet(choices: List<TimerChoice>) {
        var chosen = 0
        var action = timerAction(prefs.getString("timerAction", null))
        val list = section()
        fun draw() {
            list.removeAllViews()
            choices.forEachIndexed { i, choice ->
                list.addView(pickRow(choice.title, choice.detail, i == chosen) { chosen = i; draw() },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
            }
            list.addView(settingsHeading("Poi"))
            TimerAction.entries.forEach { option ->
                list.addView(pickRow(option.label, null, option == action) { action = option; draw() },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
            }
            activeTimerLabel()?.let { list.addView(homeText("Adesso: $it. Il nuovo prende il suo posto.", 13f, bold = false).apply { setPadding(dp(16), dp(8), dp(16), 0) }) }
        }
        draw()
        sheet("Timer di spegnimento", list.apply { setPadding(dp(16), 0, dp(16), dp(8)) }, "Avvia il timer" to {
            val seconds = choices[chosen].seconds
            if (seconds != null) startTimer(seconds, action) else askTimerMinutes { startTimer(it * 60L, action) }
        })
    }

    private fun askTimerMinutes(done: (Int) -> Unit) {
        val field = EditText(this).apply {
            hint = "Minuti, es. 45"
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Tra quanti minuti?")
            .setView(FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(field) })
            .setPositiveButton("Avvia") { _, _ ->
                parseTimerMinutes(field.text.toString())?.let(done) ?: toast("Scrivi i minuti, da 1 a 720")
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // Il timer va alla Fire TV via ADB; se non risponde lo tiene il telefono, con una sveglia di Android
    private fun startTimer(seconds: Long, action: TimerAction) {
        val ip = prefs.getString("adbHost", null) ?: adbHost(prefs.getString("host", "").orEmpty())
        val token = System.currentTimeMillis()
        val at = System.currentTimeMillis() / 1000 + seconds
        prefs.edit().putString("timerAction", action.id).apply()
        toast("Avvio il timer…")
        thread {
            val onTv = ip.isNotBlank() && runCatching {
                adbShell(ip, adbKeys(), ADB_KEY_NAME, timerCommand(seconds, action, token), onConfirm = {
                    runOnUiThread { notice("Conferma sulla TV", "La Fire TV chiede se consentire il debug da questo telefono: spunta \"Consenti sempre da questo computer\" e premi OK.") }
                })
            }.isSuccess
            runOnUiThread {
                cancelPhoneTimer()
                if (!onTv) phoneTimer(seconds, action, token)
                prefs.edit().putLong("timerAt", at).putLong("timerToken", token).putBoolean("timerOnPhone", !onTv).apply()
                readMini()
                val label = timerLabel(action, at)
                if (onTv) toast("Timer avviato: $label")
                else notice("Timer sul telefono", "$label, ma la Fire TV non risponde in ADB, quindi il timer lo tiene il telefono: deve restare acceso e sul Wi-Fi di casa fino ad allora" +
                    (if (action == TimerAction.TV_OFF) ", e la TV non si spegne (Kodi si ferma soltanto)." else ".") +
                    "\n\nPer lasciarlo alla Fire TV serve il Debug ADB, come per il pulsante di accensione.")
            }
        }
    }

    private fun timerIntent(action: TimerAction? = null, token: Long = 0) = PendingIntent.getBroadcast(
        this, 0,
        Intent(this, TimerReceiver::class.java).apply { action?.let { putExtra("action", it.id) }; putExtra("token", token) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun phoneTimer(seconds: Long, action: TimerAction, token: Long) {
        getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + seconds * 1000, timerIntent(action, token))
    }

    private fun cancelPhoneTimer() {
        getSystemService(AlarmManager::class.java)?.cancel(timerIntent())
    }

    private fun cancelTimer() {
        val onPhone = prefs.getBoolean("timerOnPhone", false)
        val ip = prefs.getString("adbHost", null) ?: adbHost(prefs.getString("host", "").orEmpty())
        prefs.edit().remove("timerAt").remove("timerToken").remove("timerOnPhone").apply()
        cancelPhoneTimer()
        readMini()
        if (onPhone || ip.isBlank()) return toast("Timer annullato")
        thread {
            val done = runCatching { adbShell(ip, adbKeys(), ADB_KEY_NAME, cancelTimerCommand(), onConfirm = {}) }.isSuccess
            toast(if (done) "Timer annullato" else "Non riesco ad annullare il timer sulla Fire TV: riprova tra poco")
        }
    }

    private fun extendTimer() {
        val at = timerAt() ?: return
        startTimer(at - System.currentTimeMillis() / 1000 + TIMER_EXTEND, timerAction(prefs.getString("timerAction", null)))
    }

    // ---- Tendina (2026-10-27): player di Android in KodiPlayer.kt, riquadro in KodiTile.kt ----

    private fun toggleMediaControls() {
        val on = !KodiPlayer.enabled(this)
        prefs.edit().putBoolean("mediaControls", on).apply()
        if (on) {
            if (miniNow?.playerId != null) KodiPlayer.startIfWanted(this)
            toast("Controlli nella tendina accesi: compaiono mentre Kodi riproduce")
        } else {
            KodiPlayer.stop(this)
            toast("Controlli nella tendina spenti")
        }
        showSettings()
    }

    // Android 13 chiede "Aggiungere il riquadro?" con un tocco, senza cercarlo nella matita della tendina
    private fun askAddTile() {
        getSystemService(StatusBarManager::class.java)?.requestAddTileService(
            ComponentName(this, KodiTile::class.java), "Couch Cast",
            Icon.createWithResource(this, it.franc.casttokodi.R.drawable.ic_cast), mainExecutor,
        ) { result ->
            toast(when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Riquadro aggiunto: lo trovi nella tendina"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Il riquadro c'è già nella tendina"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Riquadro non aggiunto"
                else -> "Aggiungilo a mano: matita della tendina, poi trascina Couch Cast"
            })
        }
    }

    // ---- Backup e ripristino (2026-10-27): formato del file, unione e regole in Backup.kt ----

    // Le scelte del pannello Salva un backup, per quando torna la scelta del file
    private var backupParts = BackupPart.entries.toSet()
    private var backupWeekly = true

    private fun backupLabel(): String {
        val at = prefs.getLong("backupAt", 0)
        if (at == 0L) return "Preferiti, serie e impostazioni in un file, da tenere su Drive o in Download"
        val day = DateUtils.formatDateTime(this, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        return "Ultimo: $day" + if (prefs.getString("backupUri", null) != null) " · si aggiorna ogni settimana" else ""
    }

    // Riga con la casella: si cambia toccando tutta la riga
    private fun checkRow(title: String, detail: String?, checked: Boolean, changed: (Boolean) -> Unit): View {
        val box = CheckBox(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, on -> changed(on) }
        }
        val name = homeText(title, 16f, bold = false).apply { setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50)) }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(4), dp(4), dp(8), dp(4))
            setBackgroundResource(attr(R.attr.selectableItemBackground))
            setOnClickListener { box.toggle() }
            addView(box)
            addView(detail?.let { section(name, homeText(it, 13f, bold = false).apply { maxLines = 2 }) } ?: section(name),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        }
    }

    private fun askBackup() {
        val values = prefs.all
        val count = backupCount(values)
        val bytes = backupImages(values).sumOf { File(framesDir(), it).length() }
        val parts = BackupPart.entries.toMutableSet()
        fun part(part: BackupPart): (Boolean) -> Unit = { on -> if (on) parts += part else parts -= part }
        val series = when (count.shows) {
            0 -> "nessuna serie"
            1 -> "1 serie"
            else -> "${count.shows} serie"
        }
        val images = if (bytes > 0) ", con le immagini · ${android.text.format.Formatter.formatShortFileSize(this, bytes)}" else ""
        backupWeekly = true
        val content = section(
            homeText("Nel file ci sono:", 14f, bold = false).apply { setPadding(dp(8), 0, 0, dp(4)) },
            checkRow("Preferiti", if (count.favorites == 1) "1 sito, con nome e icona" else "${count.favorites} siti, con nomi e icone",
                true, part(BackupPart.FAVORITES)),
            checkRow("Continua a guardare", "$series$images, l'ultima pagina", true, part(BackupPart.SHOWS)),
            checkRow("Impostazioni", "Kodi, sigla, episodi successivi, nomi dei video in coda", true, part(BackupPart.SETTINGS)),
            homeText("Password di Kodi e chiave della Fire TV restano su questo telefono: dopo il ripristino l'app chiede la password una volta e la TV chiede di nuovo Consenti.", 13f, bold = false).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(sys(R.color.system_neutral2_100, R.color.system_neutral2_800))
                }
            },
            checkRow("Aggiorna questo file ogni settimana", "All'avvio dell'app, nello stesso posto", true) { backupWeekly = it },
        ).apply { setPadding(dp(16), 0, dp(16), dp(8)) }
        sheet("Salva un backup", content, "Scegli dove salvarlo" to {
            if (parts.isEmpty()) {
                toast("Scegli almeno una cosa da salvare")
            } else {
                backupParts = parts.toSet()
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "couchcast-${LocalDate.now()}.zip"), REQUEST_BACKUP)
            }
        })
    }

    private fun askRestore() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")), REQUEST_RESTORE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_BACKUP -> saveBackup(uri, backupParts, backupWeekly)
            REQUEST_RESTORE -> readRestore(uri)
        }
    }

    // Da un thread di background
    private fun writeBackupTo(uri: Uri, parts: Set<BackupPart>) {
        val values = prefs.all
        val json = backupJson(values, parts, installedVersion(), System.currentTimeMillis())
        val images: Map<String, () -> ByteArray?> = if (BackupPart.SHOWS in parts) {
            backupImages(values).associateWith { name -> { File(framesDir(), name).takeIf { it.isFile }?.readBytes() } }
        } else emptyMap()
        (contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Non riesco a scrivere il file")).use { writeBackup(it, json, images) }
        prefs.edit().putLong("backupAt", System.currentTimeMillis()).apply()
    }

    private fun saveBackup(uri: Uri, parts: Set<BackupPart>, weekly: Boolean) {
        val name = displayName(uri)
        thread {
            try {
                writeBackupTo(uri, parts)
            } catch (e: Exception) {
                runOnUiThread { notice("Backup non salvato", "Non riesco a scrivere il file (${e.message ?: e.javaClass.simpleName}). Prova a sceglierne un altro, per esempio in Download.") }
                return@thread
            }
            forgetAutoBackup(keep = if (weekly) uri else null)
            val automatic = weekly && try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                prefs.edit().putString("backupUri", uri.toString()).putString("backupParts", parts.joinToString(",") { it.id }).apply()
                true
            } catch (e: SecurityException) {
                false
            }
            toast(if (automatic || !weekly) "Backup salvato in $name"
                else "Backup salvato in $name, ma lì non posso aggiornarlo da solo: rifallo a mano ogni tanto")
        }
    }

    // Spegne il backup automatico e lascia il permesso sul vecchio file, tranne se è proprio keep
    private fun forgetAutoBackup(keep: Uri? = null) {
        val old = prefs.getString("backupUri", null)?.let(Uri::parse) ?: return
        if (old == keep) return
        runCatching { contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs.edit().remove("backupUri").remove("backupParts").apply()
    }

    // All'avvio: con il backup automatico acceso, se l'ultimo ha più di una settimana si riscrive lo stesso file
    private fun autoBackup() {
        val uri = prefs.getString("backupUri", null)?.let(Uri::parse) ?: return
        if (System.currentTimeMillis() - prefs.getLong("backupAt", 0) < BACKUP_EVERY) return
        val saved = prefs.getString("backupParts", "").orEmpty().split(",")
        val parts = BackupPart.entries.filter { it.id in saved }.toSet().ifEmpty { BackupPart.entries.toSet() }
        thread {
            try {
                writeBackupTo(uri, parts)
            } catch (e: Exception) {
                runOnUiThread {
                    forgetAutoBackup()
                    notice("Backup automatico non riuscito", "Non riesco più a scrivere il file del backup (spostato, cancellato o senza rete?). Salvane uno nuovo da ⋮ → Impostazioni → Salva un backup.")
                }
            }
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "il file scelto"

    private fun readRestore(uri: Uri) {
        val name = displayName(uri)
        thread {
            val backup = try {
                (contentResolver.openInputStream(uri) ?: throw IOException("Non riesco ad aprire il file")).use { readBackup(it) }
            } catch (e: Exception) {
                runOnUiThread { notice("Ripristino non riuscito", "${e.message ?: "Il file non si legge"}.\n\nScegli un file salvato da Couch Cast con Salva un backup.") }
                return@thread
            }
            runOnUiThread { showRestore(backup, name) }
        }
    }

    private fun showRestore(backup: Backup, name: String) {
        val mine = backupCount(prefs.all)
        val theirs = backupCount(backup.values)
        val day = if (backup.created > 0) DateUtils.formatDateTime(this, backup.created, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR) else null
        val lines = listOfNotNull(
            if (BackupPart.FAVORITES in backup.parts) "Preferiti: ${theirs.favorites} (qui ${mine.favorites})" else null,
            if (BackupPart.SHOWS in backup.parts) "Serie: ${theirs.shows} (qui ${mine.shows})" else null,
            if (BackupPart.SETTINGS in backup.parts) "Impostazioni: sì" else null,
        )
        var replace = false
        val options = section()
        fun drawOptions() {
            options.removeAllViews()
            listOf(
                Triple("Unisci", "Aggiunge quello che manca; per una serie tiene l'episodio più avanti", false),
                Triple("Sostituisci tutto", "Questo telefono torna com'era nel backup", true),
            ).forEach { (title, detail, value) ->
                val selected = replace == value
                options.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(56)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = if (selected) rounded(sys(R.color.system_accent1_100, R.color.system_accent1_800), 16)
                        else RippleDrawable(ColorStateList.valueOf(0x33808080), null, GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) })
                    setOnClickListener { replace = value; drawOptions() }
                    addView(homeText(title, 16f, bold = selected).apply { setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50)) })
                    addView(homeText(detail, 13f, bold = false))
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            }
        }
        drawOptions()
        val content = section(
            homeText(listOfNotNull(name, day, backup.version.ifBlank { null }?.let { "versione $it" }).joinToString(" · "), 13f, bold = false)
                .apply { setPadding(dp(8), 0, dp(8), dp(8)) },
            homeText(lines.joinToString("\n").ifEmpty { "Il backup è vuoto" }, 15f, bold = false).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setLineSpacing(0f, 1.3f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(sys(R.color.system_neutral2_100, R.color.system_neutral2_800))
                }
            },
            options.apply { setPadding(0, dp(12), 0, 0) },
        ).apply { setPadding(dp(16), 0, dp(16), dp(8)) }
        sheet("Ripristina il backup", content, "Ripristina" to {
            applyRestore(backup, if (replace) replaceWith(backup) else mergeWith(prefs.all, backup))
        })
    }

    private fun applyRestore(backup: Backup, restore: Restore) {
        val before = prefs.all.filterKeys { it in BACKED_UP }
        val counted = backupCount(before)
        // Le immagini hanno nomi unici: quelle già sul telefono non si toccano, quelle che non servono le toglie cleanFrames
        backup.images.forEach { (name, bytes) -> runCatching { File(framesDir(), name).takeIf { !it.exists() }?.writeBytes(bytes) } }
        writePrefs(restore.write, restore.remove)
        afterRestore()
        val now = backupCount(prefs.all)
        val added = listOfNotNull(
            (now.favorites - counted.favorites).takeIf { it > 0 }?.let { if (it == 1) "1 preferito" else "$it preferiti" },
            (now.shows - counted.shows).takeIf { it > 0 }?.let { if (it == 1) "1 serie" else "$it serie" },
        )
        val message = "Ripristinato" + (if (added.isNotEmpty()) ": ${added.joinToString(" e ")} in più" else "") +
            (if (backup.skipped > 0) " (saltati dati di una versione più nuova)" else "")
        toast(message) {
            writePrefs(before.mapNotNull { (key, value) -> value?.let { key to it } }.toMap(), BACKED_UP.keys - before.keys)
            afterRestore()
        }
    }

    private fun writePrefs(write: Map<String, Any>, remove: Set<String>) {
        val edit = prefs.edit()
        remove.forEach { edit.remove(it) }
        write.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Boolean -> edit.putBoolean(key, value)
            }
        }
        edit.apply()
    }

    private fun afterRestore() {
        refreshStar()
        if (home.visibility == View.VISIBLE) refreshHome()
    }


    // Le serie in prefs["shows"] (vedi showsToJson). Si leggono e scrivono solo dal thread principale
    private fun loadShows(): List<Show> = showsFromJson(prefs.getString("shows", null))

    private fun saveShows(list: List<Show>) {
        prefs.edit().putString("shows", showsToJson(list)).apply()
    }

    private fun updateShows(change: (List<Show>) -> List<Show>?) {
        val changed = change(loadShows()) ?: return
        saveShows(changed)
        if (home.visibility == View.VISIBLE) refreshHome()
    }

    // Ultimo dominio visto di ogni sito che ha una serie: i siti pirata cambiano dominio spesso, e una serie si riapre
    // dove il sito si è visto l'ultima volta (anche se ci si è arrivati da Google o da un preferito aggiornato)
    private fun siteHosts(): Map<String, String> {
        val json = JSONObject(prefs.getString("siteHosts", "{}")!!)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private fun rememberSite(host: String?) {
        val site = siteOf(host)
        if (host.isNullOrBlank() || site.isBlank() || loadShows().none { it.site == site }) return
        val json = JSONObject(prefs.getString("siteHosts", "{}")!!)
        if (json.optString(site) != host) prefs.edit().putString("siteHosts", json.put(site, host).toString()).apply()
    }

    // Un episodio appena mandato (playing) o messo in coda. Se è partito, da AnimeUnity si leggono anche il nome vero
    // della serie, il numero, quanti episodi sono e quello dopo; la copertina della serie dal sito e, per ogni episodio,
    // un fotogramma dal suo video
    private fun recordSent(episode: EpisodePage, host: String, file: String, playing: Boolean, video: Video?, page: String? = null) {
        val known = loadShows().firstOrNull { it.key == episode.key }
        val hasCover = known?.image?.startsWith(COVER_PREFIX) == true
        updateShows { withSent(it, episode, host, file, System.currentTimeMillis(), playing) }
        if (!playing) return
        val uri = page?.let { Uri.parse(it) }
        val series = uri?.let { animeUnitySeries(it.host, it.path) }
        if (series != null && page == web.url) {
            web.evaluateJavascript(EPISODE_INFO_JS) { result ->
                val info = runCatching { JSONObject(JSONTokener(result).nextValue() as String) }.getOrNull()
                if (info == null || info.has("error")) {
                    if (!hasCover) toast("Copertina: pagina non letta (${info?.optString("error") ?: result.take(40)})")
                    return@evaluateJavascript
                }
                val next = info.optJSONObject("next")?.let { Episode(null, it.optString("number").toIntOrNull(), "$series/${it.optString("id")}") }
                val at = info.optString("id").takeIf { it.isNotBlank() && episode.path.trimEnd('/') == series }?.let { "$series/$it" }
                updateShows {
                    withDetails(it, episode.key, title = info.optString("title").takeIf { known == null },
                        count = info.optInt("count").takeIf { c -> c > 0 }, next = next,
                        path = episode.path, number = info.optString("number").toIntOrNull(), at = at)
                }
                if (!hasCover) {
                    val image = info.optString("image")
                    // 2.2.3: sul sito vero la copertina non arrivava. Finché non è chiaro perché, l'app lo dice
                    if (image.isBlank()) toast("Copertina: la pagina non la indica") else fetchCover(episode.key, image, page!!)
                }
            }
        } else if (page != null && page == web.url && !hasCover) {
            web.evaluateJavascript(PAGE_IMAGE_JS) { result ->
                val image = runCatching { JSONTokener(result).nextValue() as String }.getOrNull()
                if (!image.isNullOrBlank()) fetchCover(episode.key, image, page)
            }
        }
        // Un fotogramma per ogni episodio, grande quanto la scheda; se non arriva resta l'anteprima piccola dell'elenco
        // dei video, se c'è, o la copertina della serie (con VixCloud non sempre si riesce: il video è protetto)
        if (video == null) return
        // Con lo sfondo "Copertina" (pannello della serie) l'immagine dell'episodio non serve: niente pezzi da scaricare
        val show = loadShows().firstOrNull { it.key == episode.key }
        if (show?.cover == true) return
        val (from, to) = show?.frameRange ?: ((FRAME_FROM * 100).toInt() to (FRAME_TO * 100).toInt())
        val userAgent = web.settings.userAgentString
        val small = previews[video.url]
        val width = resources.displayMetrics.widthPixels.coerceIn(480, 1280)
        thread {
            var reason = ""
            // Dopo la sigla: il primo punto che non dà un fotogramma quasi nero, se no il più chiaro (2.2.10, Pippo:
            // al 10% venivano tutte immagini della sigla)
            var frame: Bitmap? = null
            var light = -1.0
            for (point in episodeFramePoints(from = from / 100.0, to = to / 100.0)) {
                val picture = videoPreview(video, userAgent, width, point, failed = { reason = it }).second ?: continue
                val level = frameBrightness(picture)
                if (level > light) { frame = picture; light = level }
                if (level >= DARK_FRAME) break
            }
            frame = frame ?: small
            // 2.2.8: con la 2.2.7 l'immagine non cambiava e non si sa perché; finché non è chiaro l'app lo dice
            if (frame == null) return@thread runOnUiThread { toast("Immagine dell'episodio non presa: $reason") }
            val name = saveFrame(episode.key, frame)
            runOnUiThread { setFrame(episode.key, file, name) }
        }
    }

    // Il fotogramma va all'episodio e, se la serie non ha una copertina del sito, anche alla serie
    private fun setFrame(key: String, file: String, name: String) = updateShows { shows ->
        val withEpisode = withEpisodeImage(shows, file, name) ?: shows
        if (withEpisode.firstOrNull { it.key == key }?.image?.startsWith(COVER_PREFIX) == true) withEpisode
        else withDetails(withEpisode, key, image = name)
    }

    // La copertina della serie dall'indirizzo dato dal sito, scaricata con i cookie e lo User-Agent del WebView
    private fun fetchCover(key: String, image: String, page: String) {
        val url = runCatching { URL(URL(page), image).toString() }.getOrNull()?.takeIf { it.startsWith("http") } ?: return
        val userAgent = web.settings.userAgentString
        val cookie = CookieManager.getInstance().getCookie(url)
        thread {
            val picture = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Referer", page)
                if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: throw IOException("formato non letto (${conn.contentType})")
            } catch (e: Exception) {
                runOnUiThread { toast("Copertina non scaricata: ${e.message ?: e.javaClass.simpleName} · ${Uri.parse(url).host}") }
                return@thread
            }
            val name = saveFrame(key, picture, COVER_PREFIX)
            runOnUiThread { updateShows { withDetails(it, key, image = name) } }
        }
    }

    // Le immagini delle serie stanno in file a parte; quelle che iniziano con COVER_PREFIX sono copertine del sito, così prefs["shows"] resta piccolo (si rilegge ogni 3 secondi)
    private fun framesDir() = KodiAccess.framesDir(this)

    // Si rimpicciolisce solo finché copre ancora la scheda (larga quanto lo schermo, 16:9) dopo il taglio: le
    // copertine del sito sono spesso strisce larghe e basse, e portarle a 480 di larghezza le lasciava alte 100 pixel
    private fun saveFrame(key: String, picture: Bitmap, prefix: String = ""): String {
        val name = "$prefix${Integer.toHexString(key.hashCode())}-${System.currentTimeMillis()}.jpg"
        val width = resources.displayMetrics.widthPixels.coerceIn(480, 1280)
        val scale = minOf(1f, maxOf(width.toFloat() / picture.width.coerceAtLeast(1), width * 9f / 16 / picture.height.coerceAtLeast(1)))
        val scaled = Bitmap.createScaledBitmap(picture, (picture.width * scale).toInt().coerceAtLeast(1), (picture.height * scale).toInt().coerceAtLeast(1), true)
        File(framesDir(), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return name
    }

    private fun loadFrame(name: String): Bitmap? = runCatching { BitmapFactory.decodeFile(File(framesDir(), name).path) }.getOrNull()

    // All'avvio: via le immagini delle serie tolte (non subito, così "Annulla" le ritrova)
    private fun cleanFrames() {
        val used = usedImages(loadShows())
        framesDir().listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    // Lettura del mini-player: se Kodi riproduce un episodio di una serie, la serie prende il punto. La pagina
    // iniziale aggiorna solo la sua scheda, senza ridisegnarsi (perderebbe lo scorrimento e la ✕ già toccata)
    private fun trackProgress(playing: NowPlaying) {
        if (!historyOn()) return
        val before = loadShows()
        val after = withProgress(before, playing.file, playing.time, playing.total, System.currentTimeMillis()) ?: return
        saveShows(after)
        if (home.visibility != View.VISIBLE) return
        val show = after[0]
        val old = before.firstOrNull { it.key == show.key }
        val update = tileUpdaters[show.key]
        val sameCard = old != null && before.firstOrNull()?.key == show.key && old.current.path == show.current.path &&
            old.current.watched == show.current.watched
        if (update != null && sameCard) update(show) else refreshHome()
    }

    // Con l'app chiusa il mini-player non legge Kodi: il punto degli episodi in corso si chiede a Kodi, che lo salva
    // da sé (Files.GetFileDetails). Non ancora provato sulla Fire TV vera: se Kodi non lo sa, resta quello che c'era
    private fun readSavedPoints() {
        if (!historyOn()) return
        val kodi = quietKodi() ?: return
        val playingFile = miniNow?.file
        val files = loadShows().map { it.current }.filter { !it.watched && it.file != null && it.file != playingFile }.mapNotNull { it.file }.take(6)
        if (files.isEmpty()) return
        thread {
            val points = mutableListOf<Pair<String, Triple<Long, Long, Boolean>>>()
            for (file in files) {
                try {
                    kodi.savedPoint(file)?.let { points += file to it }
                } catch (e: IOException) {
                    break
                } catch (e: Exception) {
                    // Kodi non conosce questo video: si passa al prossimo
                }
            }
            if (points.isEmpty()) return@thread
            runOnUiThread {
                updateShows { shows ->
                    var result: List<Show>? = null
                    points.forEach { (file, p) -> withSaved(result ?: shows, file, p.first, p.second, p.third)?.let { result = it } }
                    result
                }
            }
        }
    }

    // Scheda di una serie: immagine con l'icona del sito, la ✕ e il punto, poi nome e a che episodio si è. Tocco =
    // pannello della serie. La ✕ chiede conferma sulla scheda stessa (diventa rossa, "Togli…?") per 4 secondi
    private fun showTile(show: Show, width: Int): View {
        val white = sys(R.color.system_neutral1_10, R.color.system_neutral1_10)
        val image = ImageView(this).apply {
            val frame = (show.picture?.let { loadFrame(it) } ?: show.image?.let { loadFrame(it) })
            if (frame != null) {
                setImageBitmap(frame)
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_play, sys(R.color.system_accent1_50, R.color.system_accent1_100), 36))
                scaleType = ImageView.ScaleType.CENTER
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                    sys(R.color.system_accent1_400, R.color.system_accent1_600),
                    sys(R.color.system_accent2_700, R.color.system_accent2_900),
                ))
            }
            // Finita: sbiadita
            if (show.finished) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.3f) })
        }
        val badge = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
            setTextColor(white)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.argb(180, 0, 0, 0))
            }
        }
        val bar = ProgressBar(this, null, R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(sys(R.color.system_accent1_200, R.color.system_accent1_200))
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(90, 255, 255, 255))
        }
        val close = ImageButton(this).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_close, white, 16))
            contentDescription = "Togli ${show.title} da Continua a guardare"
            background = circle(Color.argb(140, 0, 0, 0))
        }
        fun pill(text: String, filled: Boolean) = TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(6))
            val error = errorColor()
            setTextColor(if (filled) error else white)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                if (filled) setColor(white) else setStroke(dp(1), white)
            }
        }
        val yes = pill("Togli", filled = true)
        val no = pill("No", filled = false)
        val ask = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            background = GradientDrawable().apply { setColor(errorColor()) }
            addView(TextView(context).apply {
                text = "Togli ${show.title}?"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(white)
                setPadding(dp(12), 0, dp(12), dp(10))
            })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(yes)
                addView(no, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            })
            visibility = View.GONE
        }
        val revert = Runnable { ask.visibility = View.GONE }
        close.setOnClickListener {
            ask.visibility = View.VISIBLE
            ask.removeCallbacks(revert)
            ask.postDelayed(revert, 4000)
        }
        no.setOnClickListener {
            ask.removeCallbacks(revert)
            revert.run()
        }
        yes.setOnClickListener {
            ask.removeCallbacks(revert)
            removeShow(show)
        }
        val art = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(sys(R.color.system_neutral2_200, R.color.system_neutral2_700))
            }
            clipToOutline = true
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(siteBadge(show), FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.START).apply { setMargins(dp(8), dp(8), 0, 0) })
            addView(close, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
            addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(8), dp(12)) })
            addView(bar, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))
            addView(ask, FrameLayout.LayoutParams(-1, -1))
        }
        val finished = TextView(this).apply {
            text = "Finita"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(sys(R.color.system_neutral2_700, R.color.system_neutral2_200))
            setPadding(dp(8), dp(1), dp(8), dp(1))
            background = rounded(sys(R.color.system_neutral2_100, R.color.system_neutral2_700), 8)
            visibility = if (show.finished) View.VISIBLE else View.GONE
        }
        val note = homeText("", 13f, bold = false)
        fun fill(s: Show) {
            val card = showCard(s)
            note.text = card.note
            badge.text = card.badge
            badge.visibility = if (card.badge != null) View.VISIBLE else View.GONE
            bar.progress = (card.progress * 1000).toInt()
            bar.visibility = if (card.progress > 0f && !s.finished) View.VISIBLE else View.GONE
        }
        fill(show)
        tileUpdaters[show.key] = { fill(it) }
        return section(
            art,
            homeText(show.title, 16f, bold = true).apply { setPadding(dp(2), dp(8), dp(2), 0) },
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(2), dp(2), 0)
                addView(finished, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
                addView(note, LinearLayout.LayoutParams(0, -2, 1f))
            },
        ).apply {
            (art.layoutParams as LinearLayout.LayoutParams).height = dp(width * 9 / 16)
            setOnClickListener { showPanel(show.key) }
            art.setOnClickListener { showPanel(show.key) }
        }
    }

    // L'icona del sito sulla scheda: quella di un preferito dello stesso sito, se no l'iniziale
    private fun siteBadge(show: Show): View {
        val favorite = loadFavorites().firstOrNull { siteOf(Uri.parse(it.url).host) == show.site }
        return favorite?.let { favoriteIcon(it).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) } } ?: TextView(this).apply {
            text = initialOf(show.site, show.host)
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(sys(R.color.system_accent1_900, R.color.system_accent1_100))
            background = rounded(sys(R.color.system_accent1_100, R.color.system_accent1_700), 8)
        }
    }

    // Tolta subito; "Annulla" nell'avviso la rimette dov'era
    private fun removeShow(show: Show) {
        val before = loadShows()
        val index = before.indexOfFirst { it.key == show.key }.coerceAtLeast(0)
        saveShows(withoutShow(before, show.key))
        refreshHome()
        toast("${show.title}: tolta da Continua a guardare") {
            val now = loadShows()
            if (now.none { it.key == show.key }) {
                saveShows(now.toMutableList().apply { add(index.coerceAtMost(size), show) })
                if (home.visibility == View.VISIBLE) refreshHome()
            }
        }
    }

    // L'ultima pagina aperta, con la ✕: un tocco la toglie, "Annulla" la rimette (è una pagina, non serve la conferma)
    private fun lastPageCard(url: String): View {
        val host = Uri.parse(url).host
        val title = suggestedName(prefs.getString("lastTitle", null), host)
        val at = prefs.getLong("lastAt", 0)
        val saved = loadFavorites().firstOrNull { Uri.parse(it.url).host == host }
        val icon = saved?.let { favoriteIcon(it) } ?: ImageView(this).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_public, sys(R.color.system_accent2_900, R.color.system_accent2_100), 20))
            scaleType = ImageView.ScaleType.CENTER
            background = circle(sys(R.color.system_accent2_100, R.color.system_accent2_700))
        }
        val detail = listOfNotNull(
            shortHost(host),
            at.takeIf { it > 0 }?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() },
        ).joinToString(" · ")
        val close = ImageButton(this).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_close, sys(R.color.system_neutral2_700, R.color.system_neutral2_200), 20))
            contentDescription = "Togli l'ultima pagina"
            tooltipText = contentDescription
            setBackgroundResource(attr(R.attr.selectableItemBackgroundBorderless))
            setOnClickListener {
                prefs.edit().remove("last").remove("lastTitle").remove("lastAt").apply()
                refreshHome()
                toast("Ultima pagina tolta") {
                    prefs.edit().putString("last", url).putString("lastTitle", title).putLong("lastAt", at).apply()
                    if (home.visibility == View.VISIBLE) refreshHome()
                }
            }
        }
        return homeCard().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(4), dp(10))
            setOnClickListener { open(url) }
            addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(section(homeText(title, 15f, bold = true), homeText(detail, 13f, bold = false)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
            addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
    }

    // Pannello di una serie: gli episodi vicini (visto, a metà, prossimo), "Riprendi su Kodi" (solo AnimeUnity, dove
    // l'app sa leggere il player senza aprire la pagina) e "Apri qui"; poi "Segna come visto" e "Togli", con conferma
    private fun showPanel(key: String) {
        val show = loadShows().firstOrNull { it.key == key } ?: return
        lateinit var dialog: Dialog
        val hosts = siteHosts()
        val host = hosts[show.site] ?: show.host
        val target = show.upNext
        val content = section().apply { setPadding(dp(16), 0, dp(16), dp(16)) }
        content.addView(homeText(listOfNotNull(shortHost(host), show.count?.let { "$it episodi" }).joinToString(" · "), 13f, bold = false).apply {
            setPadding(dp(8), 0, dp(8), dp(8))
        })
        show.nearby.forEach { episode ->
            content.addView(episodeRow(show, episode) { dialog.dismiss(); open(episodeUrl(show, episode, hosts)) })
        }
        val canSend = target != null && isAnimeUnityEpisode(host, target.path) && !prefs.getString("host", "").isNullOrBlank()
        val resumeAt = target?.takeIf { !it.watched && it.time >= RESUME_MIN }?.time
        fun action(text: String, iconId: Int, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
            val (bg, fg) = if (primary) sys(R.color.system_accent1_600, R.color.system_accent1_200) to sys(R.color.system_accent1_0, R.color.system_accent1_800)
                else sys(R.color.system_accent2_100, R.color.system_accent2_700) to sys(R.color.system_accent2_900, R.color.system_accent2_100)
            this.text = text
            textSize = 15f
            isAllCaps = false
            stateListAnimator = null
            background = rounded(bg, 24)
            setTextColor(fg)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconId, fg, 20), null, null, null)
            compoundDrawablePadding = dp(8)
            setPadding(dp(20), 0, dp(20), 0)
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss(); onClick() }
        }
        val openHere = { open(episodeUrl(show, target ?: show.current, hosts)) }
        val actions = LinearLayout(this).apply { setPadding(0, dp(12), 0, dp(4)) }
        if (canSend) {
            val label = if (target === show.current) "Riprendi su Kodi" else "Ep. ${target!!.number} su Kodi"
            actions.addView(action(label, it.franc.casttokodi.R.drawable.ic_cast, primary = true) { resumeOnKodi(show, target!!) },
                LinearLayout.LayoutParams(0, dp(48), 1f))
            actions.addView(action("Apri qui", it.franc.casttokodi.R.drawable.ic_public, primary = false, openHere),
                LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(8) })
        } else {
            actions.addView(action("Apri qui", it.franc.casttokodi.R.drawable.ic_public, primary = true, openHere), LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        content.addView(actions)
        if (!canSend && resumeAt != null) {
            content.addView(homeText("Avvia il player e premi Manda a Kodi: riparte da ${clockLabel(resumeAt)}.", 13f, bold = false).apply {
                maxLines = 3
                setPadding(dp(8), dp(4), dp(8), 0)
            })
        }
        if (!show.current.watched) {
            val number = show.current.number?.let { "l'ep. $it" } ?: "l'episodio"
            content.addView(settingsRow(it.franc.casttokodi.R.drawable.ic_check, "Segna $number come visto", "La scheda passa al prossimo") {
                dialog.dismiss()
                updateShows { withCurrentWatched(it, key) }
            })
        }
        // Impostazioni della serie (2026-10-26, scelte di Pippo): nome, sfondo, Salta sigla, ripresa, episodi automatici
        content.addView(homeText("Impostazioni della serie", 14f, bold = true).apply { setPadding(dp(8), dp(16), dp(8), dp(4)) })
        fun setting(iconId: Int, title: String, value: String, open: () -> Unit) =
            content.addView(settingsRow(iconId, title, value) { dialog.dismiss(); open() })
        setting(it.franc.casttokodi.R.drawable.ic_article, "Nome", show.title) { renameShow(show) }
        setting(it.franc.casttokodi.R.drawable.ic_home, "Sfondo", backgroundLabel(show)) { chooseBackground(key) }
        setting(it.franc.casttokodi.R.drawable.ic_skip_next, "Salta sigla", show.intro?.let { "Avanti di ${introTime(it)}" } ?: "Non ancora scelto") {
            chooseIntroOf(show) { showPanel(key) }
        }
        setting(it.franc.casttokodi.R.drawable.ic_history, "Riprendi", rewindLabel(show.rewind)) { chooseRewind(show) }
        if (isAnimeUnityEpisode(host, show.current.path)) {
            val general = prefs.getInt("autoNext", DEFAULT_AUTO_NEXT)
            setting(it.franc.casttokodi.R.drawable.ic_queue, "Episodi successivi",
                show.autoNext?.let { autoNextLabel(it) } ?: "Come nelle Impostazioni: ${autoNextLabel(general).replaceFirstChar { it.lowercase() }}") {
                chooseAutoNext(show)
            }
        }
        var armed = false
        lateinit var remove: View
        remove = settingsRow(it.franc.casttokodi.R.drawable.ic_delete, "Togli da Continua a guardare", show.title) {
            if (armed) {
                dialog.dismiss()
                removeShow(show)
                return@settingsRow
            }
            armed = true
            val title = (remove as LinearLayout).let { (it.getChildAt(1) as LinearLayout).getChildAt(0) as TextView }
            title.text = "Tocca ancora per togliere"
            title.setTextColor(errorColor())
            remove.postDelayed({
                armed = false
                title.text = "Togli da Continua a guardare"
                title.setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
            }, 4000)
        }
        content.addView(remove)
        dialog = sheet(show.title, content)
    }

    // Una riga del pannello: tondo con la spunta (visto), pausa (a metà), play (l'ultimo mandato) o il numero
    private fun episodeRow(show: Show, episode: Episode, onClick: () -> Unit): View {
        val current = episode === show.current
        val (bg, fg) = when {
            episode.watched -> sys(R.color.system_neutral2_100, R.color.system_neutral2_800) to sys(R.color.system_neutral2_700, R.color.system_neutral2_200)
            current -> sys(R.color.system_accent1_600, R.color.system_accent1_200) to sys(R.color.system_accent1_0, R.color.system_accent1_800)
            else -> sys(R.color.system_accent2_100, R.color.system_accent2_700) to sys(R.color.system_accent2_900, R.color.system_accent2_100)
        }
        val iconId = when {
            episode.watched -> it.franc.casttokodi.R.drawable.ic_check
            current && episode.time >= RESUME_MIN -> it.franc.casttokodi.R.drawable.ic_pause
            current -> it.franc.casttokodi.R.drawable.ic_play
            else -> null
        }
        val badge: View = if (iconId != null) ImageView(this).apply {
            setImageDrawable(icon(iconId, fg, 18))
            scaleType = ImageView.ScaleType.CENTER
            background = circle(bg)
        } else TextView(this).apply {
            text = episode.number?.toString() ?: "?"
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            background = circle(bg)
        }
        val detail = when {
            episode.watched -> "Visto"
            current && episode.time >= RESUME_MIN ->
                "Fermo a ${clockLabel(episode.time)}" + if (episode.total > 0) " di ${clockLabel(episode.total)}" else ""
            current -> "L'ultimo mandato a Kodi"
            episode.seen == 0L && episode.file != null -> "In coda su Kodi"
            else -> "Prossimo"
        }
        val name = episode.number?.let { if (episode.season != null) "Stagione ${episode.season}, episodio $it" else "Episodio $it" } ?: "Episodio"
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundResource(attr(R.attr.selectableItemBackground))
            if (episode.watched) alpha = 0.7f
            setOnClickListener { onClick() }
            addView(badge, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(section(homeText(name, 15f, bold = current).apply {
                if (!current) setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_50))
            }, homeText(detail, 13f, bold = false)), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        }
    }

    // "Riprendi su Kodi" (AnimeUnity): l'episodio parte su Kodi senza aprirlo qui. La pagina si carica in un WebView
    // invisibile (stessi cookie del sito, quindi anche Cloudflare già passato), il player si legge con EPISODE_INFO_JS e
    // il link con resolveVixCloud, come per gli episodi successivi; sendToKodi riprende dal punto salvato. Se qualcosa
    // non va si apre la pagina qui, dove basta avviare il player e premere Manda a Kodi.
    // ⏭ con la coda finita: l'episodio dopo, mandato come "Riprendi su Kodi". Solo se l'app sa leggere il player senza
    // aprire la pagina (AnimeUnity); non accende l'accodamento automatico
    private fun nextEpisode(file: String?) {
        val show = showOfFile(loadShows(), file)
        val target = show?.let { episodeAfter(it, file) }
        when {
            show == null -> toast("Niente dopo nella coda")
            target == null -> toast("Niente dopo nella coda, e non so qual è l'episodio dopo")
            !isAnimeUnityEpisode(siteHosts()[show.site] ?: show.host, target.path) ->
                toast("Niente dopo nella coda: apri l'episodio ${target.number} sul sito e premi Manda a Kodi")
            else -> resumeOnKodi(show, target)
        }
    }

    private fun resumeOnKodi(show: Show, target: Episode) {
        val url = episodeUrl(show, target, siteHosts())
        val name = "${show.title} Episodio ${target.number}"
        val userAgent = web.settings.userAgentString
        toast("Preparo l'episodio ${target.number} per Kodi…")
        val hidden = WebView(this)
        hidden.settings.javaScriptEnabled = true
        hidden.settings.domStorageEnabled = true
        hidden.settings.userAgentString = userAgent
        var done = false
        fun giveUp() {
            if (done) return
            done = true
            web.post { hidden.destroy() }
            toast("Non riesco a mandarlo da qui: avvia il player e premi Manda a Kodi")
            open(url)
        }
        hidden.webViewClient = object : WebViewClient() {
            // Solo i redirect HTTP (il sito che cambia dominio): i salti fatti da JS (pubblicità) no
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !request.isRedirect

            override fun onPageFinished(view: WebView, loaded: String) {
                if (done) return
                view.evaluateJavascript(EPISODE_INFO_JS) { result ->
                    val info = runCatching { JSONObject(JSONTokener(result).nextValue() as String) }.getOrNull()
                    // Senza player (la pagina di controllo di Cloudflare) si aspetta la pagina vera o il tempo massimo
                    val embed = info?.optString("embed")?.takeIf { it.startsWith("http") } ?: return@evaluateJavascript
                    if (done) return@evaluateJavascript
                    done = true
                    web.post { hidden.destroy() }
                    rememberSite(Uri.parse(loaded).host)
                    resolveVixCloud(embed, userAgent, referer = loaded) { stream ->
                        if (stream == null) {
                            toast("Non trovo il video dell'episodio: avvia il player e premi Manda a Kodi")
                            open(loaded)
                        } else {
                            sendToKodi(Video(stream, embed, name, true), queued = false, page = loaded, name = name)
                        }
                    }
                }
            }
        }
        hidden.loadUrl(url)
        web.postDelayed({ giveUp() }, 25_000)
    }

    // Avviso sotto la barra quando si apre dal sito (o da Google) una serie che si sta guardando, ma non l'episodio da
    // guardare: "Eri all'episodio 12 · fermo a 12:40" con Vai. La ✕ lo chiude per quella serie finché l'app è aperta.
    private var bannerKey: String? = null

    private fun resumeBannerView(): View {
        val fg = sys(R.color.system_accent1_900, R.color.system_accent1_100)
        bannerTitle = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(fg)
        }
        bannerDetail = TextView(this).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(fg)
        }
        bannerGo = Button(this).apply {
            text = "Vai"
            isAllCaps = false
            stateListAnimator = null
            textSize = 14f
            background = rounded(sys(R.color.system_accent1_600, R.color.system_accent1_200), 18)
            setTextColor(sys(R.color.system_accent1_0, R.color.system_accent1_800))
            setPadding(dp(16), 0, dp(16), 0)
            minWidth = 0
            minimumWidth = 0
        }
        val close = ImageButton(this).apply {
            setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_close, fg, 18))
            contentDescription = "Chiudi l'avviso"
            setBackgroundResource(attr(R.attr.selectableItemBackgroundBorderless))
            setOnClickListener {
                bannerKey?.let { key -> bannerClosed += key }
                banner.visibility = View.GONE
            }
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(2), dp(8))
            background = rounded(sys(R.color.system_accent1_100, R.color.system_accent1_800), 16)
            addView(ImageView(context).apply {
                setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_history, fg, 22))
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(section(bannerTitle, bannerDetail), LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            })
            addView(bannerGo, LinearLayout.LayoutParams(-2, dp(36)))
            addView(close, LinearLayout.LayoutParams(dp(40), dp(40)))
            visibility = View.GONE
        }
    }

    private fun refreshBanner(uri: Uri, title: String?) {
        banner.visibility = View.GONE
        if (!historyOn() || home.visibility == View.VISIBLE) return
        val show = showOfPage(loadShows(), uri.host, uri.path, uri.encodedQuery, title) ?: return
        if (show.key in bannerClosed) return
        val path = uri.path.orEmpty().ifEmpty { "/" } + (uri.encodedQuery?.let { "?$it" } ?: "")
        val (headline, detail) = resumeBanner(show, path) ?: return
        val target = show.upNext ?: return
        bannerKey = show.key
        bannerTitle.text = headline
        bannerDetail.text = detail
        bannerGo.setOnClickListener {
            banner.visibility = View.GONE
            open("${uri.scheme}://${uri.host}${target.path}")
        }
        banner.visibility = View.VISIBLE
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
        refreshStar()
        if (home.visibility == View.VISIBLE) refreshHome()
    }

    private fun encodeIcon(icon: Bitmap): String {
        val small = if (icon.width > 64 || icon.height > 64) Bitmap.createScaledBitmap(icon, 64, 64, true) else icon
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // L'icona dei preferiti si rinfresca a ogni visita del loro sito: così la prendono anche quelli salvati senza
    // (prima di #25, o pagina non ancora caricata) e si corregge quella sbagliata (fino alla 2.2.1 poteva restare
    // quella di Google, se il preferito si salvava appena arrivati da una ricerca). Conta il nome del sito, non il
    // dominio: il preferito salvato su animeunity.so prende l'icona anche visitando animeunity.co
    private fun fillMissingIcon(url: String?, icon: Bitmap) {
        val site = siteOf(Uri.parse(url ?: return).host).ifEmpty { return }
        val encoded = encodeIcon(icon)
        val list = loadFavorites()
        var changed = false
        list.indices.forEach { i ->
            if (siteOf(Uri.parse(list[i].url).host) == site && list[i].icon != encoded) {
                list[i] = list[i].copy(icon = encoded)
                changed = true
            }
        }
        if (changed) saveFavorites(list)
    }

    // Lista dei preferiti: in cima il pulsante per aggiungere la pagina aperta, poi una riga per sito.
    // Tocco apre, ⋮ (o pressione lunga) per rinominare o togliere
    private fun showFavorites() {
        val list = loadFavorites()
        val current = web.url?.takeIf { it.startsWith("http") && home.visibility != View.VISIBLE }
        val canAdd = current != null && list.none { it.url == current }
        if (list.isEmpty() && !canAdd) return toast("Apri un sito per aggiungerlo ai preferiti")

        lateinit var dialog: Dialog
        val content = section().apply { setPadding(dp(24), 0, dp(16), dp(8)) }
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
        dialog = sheet("Preferiti", content)
    }

    private fun addFavorite(url: String) {
        val icon = pageIcon?.let { encodeIcon(it) }
        askFavoriteName("Aggiungi ai preferiti", suggestedName(web.title, Uri.parse(url).host)) { name ->
            saveFavorites(loadFavorites() + Favorite(name, url, icon))
            toast("Aggiunto ai preferiti")
        }
    }

    // Riga di un preferito: icona del sito (o iniziale su un cerchio), nome, dominio più tenue e ⋮
    // Icona di un preferito: quella del sito, o l'iniziale su un cerchio
    private fun favoriteIcon(favorite: Favorite): View {
        val host = Uri.parse(favorite.url).host
        val bitmap = favorite.icon
            ?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        return if (bitmap != null) {
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
    }

    private fun favoriteRow(favorite: Favorite, onOpen: () -> Unit, onMenu: (View) -> Unit): View {
        val host = Uri.parse(favorite.url).host
        val icon = favoriteIcon(favorite)
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

    // Pannello che sale dal basso al posto di un dialogo al centro: maniglia, titolo, contenuto che scorre e, in fondo,
    // i pulsanti vicino al pollice. Si chiude toccando fuori o con indietro.
    private fun sheet(title: String, content: View, vararg actions: Pair<String, () -> Unit>): Dialog {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val radius = dp(28).toFloat()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                setColor(sys(R.color.system_neutral1_10, R.color.system_neutral1_900))
            }
            val handle = View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(sys(R.color.system_neutral2_400, R.color.system_neutral2_600))
                }
            }
            addView(handle, LinearLayout.LayoutParams(dp(32), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            })
            addView(TextView(context).apply {
                text = title
                textSize = 20f
                setTextColor(sys(R.color.system_neutral1_900, R.color.system_neutral1_100))
                setPadding(dp(24), dp(16), dp(24), dp(12))
            })
            // Altezza 0 con peso: se il contenuto è più alto dello schermo è lo ScrollView a restringersi, non i pulsanti
            addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
            if (actions.isNotEmpty()) {
                val bar = LinearLayout(context).apply {
                    gravity = Gravity.END
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
                actions.forEach { (label, action) ->
                    bar.addView(Button(context).apply {
                        text = label
                        isAllCaps = false
                        stateListAnimator = null
                        background = rounded(sys(R.color.system_accent2_100, R.color.system_accent2_700), 20)
                        setTextColor(sys(R.color.system_accent2_900, R.color.system_accent2_100))
                        setPadding(dp(20), 0, dp(20), 0)
                        setOnClickListener { dialog.dismiss(); action() }
                    }, LinearLayout.LayoutParams(-2, dp(40)).apply { marginStart = dp(8) })
                }
                addView(bar)
            }
            // Sotto la barra dei gesti: il pannello arriva al bordo, il contenuto resta sopra
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(0, 0, 0, insets.getInsets(WindowInsets.Type.navigationBars()).bottom)
                insets
            }
        }
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(R.style.Animation_InputMethod)
        }
        dialog.show()
        return dialog
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

    // Avviso in fondo allo schermo, sopra il sito: resta più di un toast se il testo è lungo
    // Con il telecomando aperto l'avviso va nel suo, perché quello dell'app resterebbe sotto
    private val hideSnack = Runnable {
        snack.visibility = View.GONE
        playerSnack?.visibility = View.GONE
    }

    private fun snackView() = TextView(this).apply {
        textSize = 14f
        setTextColor(sys(R.color.system_neutral1_50, R.color.system_neutral1_900))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        elevation = dp(6).toFloat()
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(sys(R.color.system_neutral1_800, R.color.system_neutral1_100))
        }
        visibility = View.GONE
    }

    // undo: l'avviso dice anche "Annulla" e, toccato, lo esegue (dopo aver tolto una serie o l'ultima pagina)
    private fun toast(msg: String, undo: (() -> Unit)? = null) = runOnUiThread {
        val target = playerSnack ?: snack
        if (undo == null) {
            target.text = msg
            target.setOnClickListener(null)
            target.isClickable = false
        } else {
            target.text = SpannableStringBuilder(msg).apply {
                append("   ")
                val start = length
                append("Annulla")
                setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(sys(R.color.system_accent1_200, R.color.system_accent1_600)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            target.setOnClickListener {
                hideSnack.run()
                undo()
            }
        }
        target.visibility = View.VISIBLE
        snack.removeCallbacks(hideSnack)
        snack.postDelayed(hideSnack, if (undo != null) 6000L else (2500L + 40L * msg.length).coerceAtMost(6000L))
    }

    // Il WebView salva i cookie su disco ogni tanto: se l'app viene chiusa prima si perde la scelta sulla pagina
    // dei cookie di Google (e i login dei siti), che si ripresenterebbe a ogni avvio
    override fun onPause() {
        super.onPause()
        resumed = false
        miniGeneration++
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        startMini()
    }

    // Indietro: la pagina precedente del sito; dalla prima pagina si torna alla pagina iniziale, e da lì si esce
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            home.visibility == View.VISIBLE -> super.onBackPressed()
            web.canGoBack() -> web.goBack()
            else -> showHome()
        }
    }
}
