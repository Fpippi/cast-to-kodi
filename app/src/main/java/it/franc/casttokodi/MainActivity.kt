package it.franc.casttokodi

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

private const val REPO = "Fpippi/cast-to-kodi"
private const val GOOGLE = "https://www.google.com"

private const val NOT_FOUND_HELP = """Nessun Kodi trovato. Controlla che:

1. Kodi sia aperto sulla Fire TV;
2. in Kodi sia attivo Impostazioni → Servizi → Generale → "Annuncia servizi ad altri sistemi";
3. telefono e Fire TV siano sullo stesso Wi-Fi.

Oppure passa a "Manuale" e scrivi IP:porta."""

// ponytail: elenco corto dei circuiti pubblicitari visti nei player; allungarlo quando se ne incontrano altri
private val AD_HOSTS = listOf("doubleclick", "googlesyndication", "imasdk", "adnxs", "adsrvr", "popads", "exoclick", "juicyads")

// Kodi riproduce la coda dalla sua playlist video
private const val VIDEO_PLAYLIST = 1

private class KodiError(message: String) : Exception(message)

private class Video(val url: String, val referer: String, val label: String, val main: Boolean)

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
        paint(favorites, active = true, primary = false)
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

        open(prefs.getString("last", null) ?: GOOGLE)
        if (prefs.getString("host", "").isNullOrBlank()) askKodiAddress()
        checkUpdate()
    }

    // L'ultima release ha come titolo "Build N", con N = versionCode dell'APK (vedi .github/workflows/apk.yml).
    // Il repo è pubblico: l'API non vuole token (limite 60 richieste l'ora per IP, ne basta una per avvio).
    private fun checkUpdate() {
        val installed = packageManager.getPackageInfo(packageName, 0).longVersionCode
        thread {
            val latest = try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) return@thread
                val name = JSONObject(conn.inputStream.bufferedReader().readText()).optString("name")
                name.filter { it.isDigit() }.toLongOrNull() ?: return@thread
            } catch (e: Exception) {
                return@thread
            }
            if (latest <= installed) return@thread
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Nuova versione disponibile")
                    .setMessage("Installata: build $installed\nUltima: build $latest")
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
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                toast("Popup bloccato")
                return false
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme != "http" && url.scheme != "https") return block(url)
                // I redirect HTTP (es. il sito che cambia dominio) sono ammessi, i salti fatti da JS no
                if (!request.isForMainFrame || request.isRedirect) return false
                // Dai risultati di Google si va su altri siti per forza: lì il tocco dell'utente basta
                if (allowedDomain.startsWith("google.") && request.hasGesture()) return false
                return if (domainOf(url) == allowedDomain) false else block(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                pageUrl = url
                allowedDomain = domainOf(Uri.parse(url))
                address.setText(url)
                videos.clear()
                refreshCastButton()
            }

            override fun onPageFinished(view: WebView, url: String) {
                prefs.edit().putString("last", url).apply()
            }

            // Chiamato su un thread di background per ogni risorsa, anche dentro gli iframe e per le XHR dei player
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // ponytail: riconosce per estensione più il caso VixCloud (AnimeUnity, StreamingCommunity), che serve
                // la playlist come /playlist/<id>?token=... senza .m3u8; per altri siti così guardare il Content-Type
                val path = request.url.path?.lowercase() ?: return null
                if (path.endsWith(".m3u8") || path.endsWith(".mp4") || path.startsWith("/playlist/")) {
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
        val text = input.trim()
        val url = when {
            text.isEmpty() -> GOOGLE
            text.startsWith("http://") || text.startsWith("https://") -> text
            // "jojo animeunity" o "jojo" non sono indirizzi: si cercano su Google
            ' ' in text || '.' !in text -> "$GOOGLE/search?q=${Uri.encode(text)}"
            else -> "https://$text"
        }
        allowedDomain = domainOf(Uri.parse(url))
        web.loadUrl(url)
        web.requestFocus()
    }

    private fun block(url: Uri): Boolean {
        toast("Bloccato: ${url.host ?: url.scheme}")
        return true
    }

    // ponytail: "ultime due etichette" sbaglia con suffissi tipo .co.uk, basta per i siti che usiamo
    private fun domainOf(url: Uri) = url.host.orEmpty().split('.').takeLast(2).joinToString(".")

    // Video trovati senza pubblicità e senza i pezzi delle playlist VixCloud, con la playlist principale per prima
    private fun playable(): List<Video> = videos.mapNotNull { (url, referer) ->
        val uri = Uri.parse(url)
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty().lowercase()
        val vixcloud = path.startsWith("/playlist/")
        when {
            isAd(uri) -> null
            // VixCloud: type=video/audio e rendition=720p sono le parti della playlist principale
            vixcloud && (uri.getQueryParameter("type") != null || uri.getQueryParameter("rendition") != null) -> null
            else -> {
                val main = vixcloud || "master" in path
                val quality = Regex("\\b(\\d{3,4})p\\b").find(url)?.groupValues?.get(1)
                val kind = when {
                    main -> "Playlist principale"
                    quality != null -> "Qualità ${quality}p"
                    path.endsWith(".mp4") -> "Video MP4"
                    else -> "Playlist"
                }
                Video(url, referer, "$kind\n$host", main)
            }
        }
    }.sortedByDescending { it.main }

    private fun isAd(uri: Uri): Boolean {
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()
        return AD_HOSTS.any { it in host } || host.split('.').any { it == "ads" || it == "ad" } ||
            "/vast" in path || "/ads/" in path
    }

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
            !active -> sys(R.color.system_neutral2_100, R.color.system_neutral2_800) to
                sys(R.color.system_neutral2_500, R.color.system_neutral2_400)
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

    // Anche "Manda a Kodi" passa dalla playlist video: così "+ Coda" accoda all'episodio in corso
    private fun sendToKodi(video: Video, queued: Boolean) {
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val auth = prefs.getString("auth", "").orEmpty()

        // Kodi accetta gli header HTTP accodati all'URL: url|Header=valore&Header2=valore
        val file = "${video.url}|Referer=${enc(video.referer)}&User-Agent=${enc(web.settings.userAgentString)}"
        val item = JSONObject().put("file", file)
        val playlist = JSONObject().put("playlistid", VIDEO_PLAYLIST)

        thread {
            val result = try {
                val playing = (rpc(host, auth, "Player.GetActivePlayers") as? JSONArray)?.length() ?: 0
                if (queued && playing > 0) {
                    rpc(host, auth, "Playlist.Add", JSONObject(playlist.toString()).put("item", item))
                    "Aggiunto alla coda"
                } else {
                    // Si riparte da una coda vuota: niente episodi vecchi prima di questo
                    rpc(host, auth, "Playlist.Clear", playlist)
                    rpc(host, auth, "Playlist.Add", JSONObject(playlist.toString()).put("item", item))
                    rpc(host, auth, "Player.Open", JSONObject().put("item", playlist))
                    if (queued) "In riproduzione: ora puoi accodare gli altri episodi" else "Inviato a Kodi"
                }
            } catch (e: IOException) {
                runOnUiThread { relocateKodi(host) { sendToKodi(video, queued) } }
                return@thread
            } catch (e: KodiError) {
                runOnUiThread { notice("Kodi non ha accettato il video", e.message.orEmpty()) }
                return@thread
            } catch (e: Exception) {
                runOnUiThread { notice("Errore", e.message.orEmpty()) }
                return@thread
            }
            runOnUiThread { toast(result) }
        }
    }

    // Chiamata JSON-RPC a Kodi, da un thread di background. I problemi di rete restano IOException
    // (Kodi forse ha cambiato IP), le risposte di errore diventano KodiError con un messaggio da mostrare.
    private fun rpc(host: String, auth: String, method: String, params: JSONObject = JSONObject()): Any? {
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
            401 -> throw KodiError("Kodi rifiuta utente e password: correggili con il pulsante Kodi.")
            else -> throw KodiError("Kodi ha risposto HTTP ${conn.responseCode}.")
        }
        val reply = JSONObject(conn.inputStream.bufferedReader().readText())
        reply.optJSONObject("error")?.let { throw KodiError("$method: ${it.optString("message")}") }
        return reply.opt("result")
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
                prefs.edit().putString("host", host).putBoolean("manual", false).apply()
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
                    if (address is Inet4Address) found.add("${address.hostAddress}:${info.port}")
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

        val auth = EditText(this).apply {
            hint = "utente:password"
            setText(prefs.getString("auth", ""))
        }
        val authPanel = section(label("Accesso, se impostato in Kodi"), auth).apply { setPadding(0, dp(16), 0, 0) }

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
                    .putBoolean("manual", manual)
                    .putString("auth", auth.text.toString().trim())
                    .apply()
            }
            .setNegativeButton("Annulla", null)
            .show()
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

    // Preferiti in SharedPreferences come JSON: [{"title": ..., "url": ...}]
    private fun loadFavorites(): MutableList<Pair<String, String>> {
        val array = JSONArray(prefs.getString("favorites", "[]"))
        return MutableList(array.length()) { i -> array.getJSONObject(i).let { it.getString("title") to it.getString("url") } }
    }

    private fun saveFavorites(list: List<Pair<String, String>>) {
        val array = JSONArray(list.map { (title, url) -> JSONObject().put("title", title).put("url", url) })
        prefs.edit().putString("favorites", array.toString()).apply()
    }

    // Tocco apre, pressione lunga elimina; la prima voce aggiunge la pagina aperta se non c'è già
    private fun showFavorites() {
        val list = loadFavorites()
        val current = web.url?.takeIf { it.startsWith("http") }
        val canAdd = current != null && list.none { it.second == current }
        val labels = (if (canAdd) listOf("＋ Aggiungi questa pagina") else emptyList()) +
            list.map { (title, url) -> "$title\n${Uri.parse(url).host}" }
        if (labels.isEmpty()) return toast("Apri un sito per aggiungerlo ai preferiti")
        val offset = if (canAdd) 1 else 0

        val dialog = AlertDialog.Builder(this)
            .setTitle("Preferiti")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < offset) {
                    val title = web.title?.takeIf { it.isNotBlank() } ?: Uri.parse(current!!).host.orEmpty()
                    saveFavorites(list + (title to current!!))
                    toast("Aggiunto ai preferiti")
                } else {
                    open(list[i - offset].second)
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
        dialog.listView.setOnItemLongClickListener { _, _, i, _ ->
            if (i < offset) return@setOnItemLongClickListener false
            val (title, _) = list[i - offset]
            AlertDialog.Builder(this)
                .setMessage("Togliere \"$title\" dai preferiti?")
                .setPositiveButton("Togli") { _, _ ->
                    list.removeAt(i - offset)
                    saveFavorites(list)
                    dialog.dismiss()
                }
                .setNegativeButton("Annulla", null)
                .show()
            true
        }
    }

    // Per i messaggi lunghi o importanti: un toast sparisce prima di averlo letto
    private fun notice(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // URLEncoder usa '+' per lo spazio, Kodi vuole %20
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
