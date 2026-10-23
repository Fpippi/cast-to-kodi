package it.franc.casttokodi

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
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
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import it.franc.casttokodi.sites.MASTER_PLAYLIST_JS
import it.franc.casttokodi.sites.DEFAULT_AUTO_NEXT
import it.franc.casttokodi.sites.MAX_AUTO_NEXT
import it.franc.casttokodi.sites.autoNextLabel
import it.franc.casttokodi.sites.NEXT_EPISODES_JS
import it.franc.casttokodi.sites.isAnimeUnityEpisode
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
    // Popup e salti bloccati sulla pagina aperta: si contano nello scudo della barra invece di un avviso a ogni blocco
    private val blocked = mutableListOf<Uri>()

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(sys(R.color.system_neutral1_10, R.color.system_neutral1_900))
            addView(topBar)
            addView(progress, LinearLayout.LayoutParams(-1, -2))
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
        showHome()
        if (prefs.getString("host", "").isNullOrBlank()) askKodiAddress()
        openShared(intent)
        checkUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openShared(intent)
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

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageUrl = url
                pageIcon = favicon
                showSiteIcon(favicon)
                home.visibility = View.GONE
                allowedDomain = domainOf(Uri.parse(url))
                if (!address.hasFocus()) address.setText(addressLabel(url))
                videos.clear()
                blocked.clear()
                refreshShield()
                refreshStar()
                refreshCastButton()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!isGoogleConsent(Uri.parse(url).host)) prefs.edit().putString("last", url).putString("lastTitle", view.title).apply()
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
            val frame = FrameLayout(this).apply {
                addView(image, FrameLayout.LayoutParams(-1, -1))
                addView(duration, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(6), dp(6)) })
            }
            val host = "da ${shortHost(Uri.parse(video.url).host)}"
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
                val (seconds, picture) = videoPreview(video, userAgent)
                runOnUiThread {
                    picture?.let { image.setImageBitmap(it) }
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
    // typed: utente e password appena scritti nel dialogo dopo un 401, al posto di quelli salvati.
    // page: la pagina del video, o il video stesso quando è arrivato da Condividi senza pagina
    private fun sendToKodi(video: Video, queued: Boolean, typed: Pair<String, String>? = null, page: String = pageUrl) {
        if (sending) return
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val network = currentNetwork()
        val login = typed ?: savedLogin(host, network)
        val kodi = Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty())
        val userAgent = web.settings.userAgentString
        val title = suggestedName(web.title.takeIf { page == pageUrl }, Uri.parse(page).host)
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
                done { relocateKodi(host) { sendToKodi(video, queued, typed, page) } }
                return@thread
            } catch (e: Unauthorized) {
                done { askLogin(host, login, wrong = login != null) { sendToKodi(video, queued, it, page) } }
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
    private fun kodiItem(url: String, referer: String, userAgent: String, title: String, viaPhone: Boolean = false): JSONObject {
        val file = streams.kodiFile(url, streamHeaders(url, referer, userAgent), viaPhone)
        rememberTitle(queueKey(file), title)
        return JSONObject().put("file", file)
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

        fun show(playing: NowPlaying?, problem: String?) {
            now = playing
            onRead(playing)
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
            add(round(it.franc.casttokodi.R.drawable.ic_skip_next, null, "Video successivo della coda", false) { video { kodi, id -> kodi.next(id) } }, 48)
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
                pill("Accendi la TV") { wakeKodi() },
                pill("−30 s") { video { kodi, id -> kodi.skip(id, -30) } },
                pill("+30 s") { video { kodi, id -> kodi.skip(id, 30) } },
                pill("Vai al minuto") { now?.let { askSeek(it) { seconds -> video { kodi, id -> kodi.seekTo(id, seconds) } } } },
                pill("Audio") { now?.takeIf { it.playerId != null }?.let { chooseAudio(it) { track -> video { kodi, id -> kodi.setAudio(id, track) } } } },
                pill("Sottotitoli") { now?.takeIf { it.playerId != null }?.let { chooseSubtitle(it) { track -> video { kodi, id -> kodi.setSubtitle(id, track) } } } },
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
                gap(dpad, 24, 216, 216, Gravity.CENTER_HORIZONTAL),
                gap(keys, 24),
                gap(volume, 4),
                gap(HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(more)
                }, 20),
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
        if (!address.hasFocus()) address.setText("")
        showSiteIcon(null)
        blocked.clear()
        refreshShield()
        refreshHome()
        refreshCastButton()
    }

    private fun refreshHome() {
        homeContent.removeAllViews()
        homeContent.addView(kodiCard())
        refreshKodiCard()

        val last = prefs.getString("last", null)
        if (last != null) {
            homeContent.addView(homeHeading("Riprendi"))
            val host = Uri.parse(last).host
            val title = prefs.getString("lastTitle", null)?.takeIf { it.isNotBlank() } ?: shortHost(host)
            val saved = loadFavorites().firstOrNull { Uri.parse(it.url).host == host }
            val icon = saved?.let { favoriteIcon(it) } ?: ImageView(this).apply {
                setImageDrawable(icon(it.franc.casttokodi.R.drawable.ic_public, sys(R.color.system_neutral2_700, R.color.system_neutral2_200)))
                scaleType = ImageView.ScaleType.CENTER
            }
            homeContent.addView(homeCard().apply {
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { open(last) }
                addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(section(
                    homeText(title, 15f, bold = true),
                    homeText(shortHost(host), 13f, bold = false),
                ), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
            })
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
            Kodi(host, login?.let { (user, password) -> "$user:$password" }.orEmpty()).nowPlaying()
        } catch (e: Exception) {
            null
        }
    }

    private fun showMini(playing: NowPlaying?) {
        miniNow = playing
        kodiReachable = playing != null
        refreshKodiCard()
        if (prefs.getString("host", "").isNullOrBlank()) {
            mini.visibility = View.GONE
            return
        }
        mini.visibility = View.VISIBLE
        val onSurface = sys(R.color.system_neutral1_900, R.color.system_neutral1_100)
        val video = playing?.playerId != null
        // Immagine: colorata con ▶ se c'è un video, altrimenti tonda neutra con il telecomando
        miniThumb.setImageDrawable(icon(
            if (video) it.franc.casttokodi.R.drawable.ic_play else it.franc.casttokodi.R.drawable.ic_remote,
            if (video) sys(R.color.system_accent1_50, R.color.system_accent1_100) else sys(R.color.system_neutral2_700, R.color.system_neutral2_200),
        ))
        miniThumb.background = if (video) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            sys(R.color.system_accent1_400, R.color.system_accent1_600),
            sys(R.color.system_accent2_700, R.color.system_accent2_900),
        )).apply { cornerRadius = dp(10).toFloat() } else circle(sys(R.color.system_neutral2_200, R.color.system_neutral2_700))
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
        miniTitle.text = queueLabel(playing.file, playing.title, loadTitles())
        miniStatus.text = miniPlayerStatus(playing.paused, playing.time, playing.total)
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
        refreshStar()
        if (home.visibility == View.VISIBLE) refreshHome()
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

    private fun toast(msg: String) = runOnUiThread {
        val target = playerSnack ?: snack
        target.text = msg
        target.visibility = View.VISIBLE
        snack.removeCallbacks(hideSnack)
        snack.postDelayed(hideSnack, (2500L + 40L * msg.length).coerceAtMost(6000L))
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
