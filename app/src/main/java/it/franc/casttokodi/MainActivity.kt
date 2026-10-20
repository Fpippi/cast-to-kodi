package it.franc.casttokodi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
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
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

private const val REPO = "Fpippi/cast-to-kodi"

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var cast: Button
    private lateinit var castText: ColorStateList
    private lateinit var progress: ProgressBar
    private val prefs by lazy { getSharedPreferences("kodi", MODE_PRIVATE) }

    // url del video -> Referer con cui la pagina l'ha richiesto (molti host rifiutano richieste senza)
    private val videos = LinkedHashMap<String, String>()

    // Dominio da cui la pagina principale può navigare: tutto il resto è considerato redirect pubblicitario
    private var allowedDomain = ""
    @Volatile private var pageUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = EditText(this).apply {
            hint = "Indirizzo del sito"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> open(text.toString()); true }
        }
        val settings = Button(this).apply {
            text = "Kodi"
            setOnClickListener { askKodiAddress() }
        }
        cast = Button(this).apply {
            setOnClickListener { pickVideo() }
        }
        castText = cast.textColors
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        web = WebView(this)
        setupWebView()

        val topBar = LinearLayout(this).apply {
            addView(address, LinearLayout.LayoutParams(0, -2, 1f))
            addView(settings)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar)
            addView(progress, LinearLayout.LayoutParams(-1, -2))
            addView(web,LinearLayout.LayoutParams(-1, 0, 1f))
            addView(cast, LinearLayout.LayoutParams(-1, -2))
            // targetSdk 35 forza l'edge-to-edge: senza padding la barra finisce sotto la status bar
            setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                WindowInsets.CONSUMED
            }
        }
        setContentView(root)
        refreshCastButton()

        prefs.getString("last", null)?.let { open(it) }
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
        if (text.isEmpty()) return
        val url = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
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

    private fun refreshCastButton() {
        cast.isEnabled = videos.isNotEmpty()
        // Verde quando c'è qualcosa da mandare, così si nota anche senza leggere il testo
        cast.backgroundTintList = if (videos.isEmpty()) null else ColorStateList.valueOf(0xFF2E7D32.toInt())
        cast.setTextColor(if (videos.isEmpty()) castText else ColorStateList.valueOf(0xFFFFFFFF.toInt()))
        cast.text = if (videos.isEmpty()) "Nessun video trovato: avvia il player" else "Manda a Kodi (${videos.size})"
    }

    private fun pickVideo() {
        val list = videos.entries.toList()
        if (list.size == 1) return sendToKodi(list[0].key, list[0].value)
        // La prima playlist rilevata di solito è la master con tutte le qualità
        val labels = list.map { (url, _) -> Uri.parse(url).let { "${it.lastPathSegment}\n${it.host}" } }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Quale video?")
            .setItems(labels) { _, i -> sendToKodi(list[i].key, list[i].value) }
            .show()
    }

    private fun sendToKodi(video: String, referer: String) {
        val host = prefs.getString("host", "").orEmpty()
        if (host.isBlank()) return askKodiAddress()
        val auth = prefs.getString("auth", "").orEmpty()

        // Kodi accetta gli header HTTP accodati all'URL: url|Header=valore&Header2=valore
        val file = "$video|Referer=${enc(referer)}&User-Agent=${enc(web.settings.userAgentString)}"
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "Player.Open")
            .put("params", JSONObject().put("item", JSONObject().put("file", file)))
            .toString()

        thread {
            val result = try {
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
                    200 -> {
                        val reply = JSONObject(conn.inputStream.bufferedReader().readText())
                        reply.optJSONObject("error")?.let { "Errore Kodi: ${it.optString("message")}" } ?: "Inviato a Kodi"
                    }
                    401 -> "Kodi rifiuta le credenziali: controlla utente e password"
                    else -> "Kodi ha risposto HTTP ${conn.responseCode}"
                }
            } catch (e: Exception) {
                "Kodi non raggiungibile: ${e.message}"
            }
            runOnUiThread { toast(result) }
        }
    }

    private fun askKodiAddress() {
        val host = EditText(this).apply {
            hint = "IP:porta, es. 192.168.1.50:8080"
            setText(prefs.getString("host", ""))
        }
        val auth = EditText(this).apply {
            hint = "utente:password (se impostati in Kodi)"
            setText(prefs.getString("auth", ""))
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(host)
            addView(auth)
        }
        AlertDialog.Builder(this)
            .setTitle("Indirizzo di Kodi")
            .setView(form)
            .setPositiveButton("Salva") { _, _ ->
                prefs.edit()
                    .putString("host", host.text.toString().trim())
                    .putString("auth", auth.text.toString().trim())
                    .apply()
            }
            .setNegativeButton("Annulla", null)
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
