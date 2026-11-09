// I pezzi di JavaScript che l'app esegue dentro le pagine dei siti (con evaluateJavascript del WebView) provati in un
// Chromium vero, lo stesso motore del WebView di Android, su pagine finte con la struttura dei siti veri.
// Gli script si leggono dai file .kt (const val NOME_JS = """..."""), così si prova proprio il testo che va nell'app.
// Non provano i siti veri (se cambiano struttura questi test restano verdi): provano che gli script leggano bene la
// struttura che conosciamo e che restino JavaScript valido dopo ogni modifica.
//
// Si lancia con: node --test app/src/test/js/  (serve Playwright; nel workflow Test usa il Chrome già installato)

const { test, before, after } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const http = require('node:http')
const path = require('node:path')

let playwright
try { playwright = require('playwright-core') } catch (e) { playwright = require('playwright') }

const SOURCES = path.join(__dirname, '../../main/java/it/franc/casttokodi')

// Il testo di const val <name> = """...""" in un file .kt
function script(file, name) {
  const text = fs.readFileSync(path.join(SOURCES, file), 'utf8')
  const match = text.match(new RegExp(`const val ${name} = """([\\s\\S]*?)"""`))
  assert.ok(match, `${name} non trovato in ${file}`)
  // In una stringa raw di Kotlin "\${" diventerebbe un valore del codice: qui il testo non sarebbe più quello dell'app
  assert.ok(!match[1].includes('${'), `${name} usa \${ : il test leggerebbe un testo diverso da quello dell'app`)
  return match[1]
}

const EPISODE_INFO_JS = script('sites/AnimeUnity.kt', 'EPISODE_INFO_JS')
const NEXT_EPISODES_JS = script('sites/AnimeUnity.kt', 'NEXT_EPISODES_JS')
const SATURN_STREAM_JS = script('sites/AnimeSaturn.kt', 'SATURN_STREAM_JS')
const MASTER_PLAYLIST_JS = script('sites/VixCloud.kt', 'MASTER_PLAYLIST_JS')
const PAGE_IMAGE_JS = script('Watching.kt', 'PAGE_IMAGE_JS')

// --- Il sito finto ---

const attr = (value) => JSON.stringify(value).replace(/&/g, '&amp;').replace(/'/g, '&#39;')

const EPISODES = [
  { id: 101, number: '1' },
  { id: 102, number: '2' },
  { id: 103, number: '3' },
  { id: 104, number: '4' },
]

const ANIME = { title_eng: 'Fire Force', title: 'Enen no Shouboutai', episodes_count: 24, imageurl_cover: 'https://img.example/cover.jpg', mal_id: 38671, anilist_id: 105310 }

// La pagina di un episodio (o della serie) del sito di anime con player VixCloud: tutto sta negli attributi di <video-player>
function animePage({ episode, anime = ANIME, episodes = EPISODES, og = '', current = null }) {
  const extra = current ? ` episode='${attr(current)}'` : ''
  return `<!doctype html><html><head><title>Episodio</title>${og}</head><body>
    <video-player anime='${attr(anime)}' episodes='${attr(episodes)}' embed_url="https://vix.example/embed/${episode}"${extra}></video-player>
  </body></html>`
}

const pages = {
  // Sito di anime con player VixCloud
  '/anime/1234-fire-force/101': () => animePage({ episode: 101 }),
  '/anime/1234-fire-force/102': () => animePage({ episode: 102 }),
  '/anime/1234-fire-force/103': () => animePage({ episode: 103 }),
  '/anime/1234-fire-force/104': () => animePage({ episode: 104 }),
  '/anime/1234-fire-force': () => animePage({ episode: 102, current: { id: 102, number: '2' } }),
  '/anime/55-senza-copertina/1': () => animePage({
    episode: 1, anime: { title: 'Senza copertina' }, episodes: [{ id: 1, number: '13' }],
    og: '<meta property="og:image" content="https://img.example/og.jpg">',
  }),
  '/anime/66-vuota/1': () => '<!doctype html><html><body><p>Niente player</p></body></html>',

  // Player VixCloud
  '/embed/1': () => `<!doctype html><html><body><script>
    window.masterPlaylist = { url: 'https://vix.example/playlist/1?b=1', params: { token: 'abc', expires: '1800000000', asn: '', x: null } };
    window.canPlayFHD = true;
  </script></body></html>`,
  '/embed/2': () => `<!doctype html><html><body><script>
    window.masterPlaylist = { url: 'https://vix.example/playlist/2', params: { token: 't' } };
  </script></body></html>`,
  '/embed/vuoto': () => '<!doctype html><html><body></body></html>',

  // Sito di anime con pagine /<serie>-ep-<numero>/: player in un iframe (struttura di adesso)
  '/serie-ita-ep-1/': () => `<!doctype html><html><body>
    <iframe src="/wp-content/themes/tema/player/?source=svcms&url=abc"></iframe></body></html>`,
  // Struttura di prima: link alla pagina del player /watch?file=
  '/ep/Serie-ep-2': () => '<!doctype html><html><body><a href="/watch?file=AAA">Guarda</a></body></html>',
  '/ep/Serie-ep-3': () => '<!doctype html><html><body><a href="/watch?file=BBB">Guarda</a></body></html>',
  '/ep/Serie-ep-4': () => '<!doctype html><html><body><a href="/watch?file=CCC">Guarda</a></body></html>',
  '/ep/Serie-ep-5': () => '<!doctype html><html><body><p>Controllo del browser…</p></body></html>',
  '/watch': (url) => {
    const file = url.searchParams.get('file')
    const alt = url.searchParams.get('s') === 'alt'
    if (file === 'AAA') return `<!doctype html><html><body><script>
      jwplayer('p').setup({ file: "https://cdn.example/AAA/playlist.m3u8", image: "x.jpg" });</script></body></html>`
    if (file === 'BBB' && alt) return '<!doctype html><html><body><video><source src="/video/BBB.mp4"></video></body></html>'
    return '<!doctype html><html><body><p>Player non disponibile</p></body></html>'
  },

  // Immagine di anteprima degli altri siti
  '/og': () => '<!doctype html><html><head><meta property="og:image" content="https://img.example/og.jpg"></head><body></body></html>',
  '/twitter': () => '<!doctype html><html><head><meta name="twitter:image" content="https://img.example/tw.jpg"></head><body></body></html>',
  '/senza-immagine': () => '<!doctype html><html><head></head><body></body></html>',
}

let server
let browser
let base

before(async () => {
  server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost')
    const page = pages[url.pathname]
    if (!page) {
      res.writeHead(404, { 'Content-Type': 'text/html' })
      return res.end(req.method === 'HEAD' ? undefined : '<!doctype html><html><body>404</body></html>')
    }
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
    res.end(req.method === 'HEAD' ? undefined : page(url))
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  base = `http://127.0.0.1:${server.address().port}`
  // Su GitHub Actions c'è già Google Chrome: niente browser da scaricare
  browser = await playwright.chromium.launch(process.env.CI ? { channel: 'chrome' } : {})
})

after(async () => {
  await browser?.close()
  server?.close()
})

// Apre la pagina ed esegue lo script come fa evaluateJavascript del WebView
async function run(pagePath, js) {
  const page = await browser.newPage()
  try {
    await page.goto(base + pagePath)
    return await page.evaluate(js)
  } finally {
    await page.close()
  }
}

const json = async (pagePath, js) => JSON.parse(await run(pagePath, js))

// --- Sito di anime con player VixCloud: EPISODE_INFO_JS ---

test('episodio: serie, numero, episodio dopo, player, copertina e id MyAnimeList', async () => {
  const info = await json('/anime/1234-fire-force/102', EPISODE_INFO_JS)
  assert.deepEqual(info, {
    title: 'Fire Force',
    id: '102',
    number: '2',
    count: 24,
    next: { id: '103', number: '3' },
    embed: 'https://vix.example/embed/102',
    image: 'https://img.example/cover.jpg',
    mal: '38671',
    anilist: '105310',
    first: '1',
  })
})

test("ultimo episodio: niente episodio dopo", async () => {
  const info = await json('/anime/1234-fire-force/104', EPISODE_INFO_JS)
  assert.equal(info.number, '4')
  assert.equal(info.next, null)
})

test('pagina della serie senza episodio nell’indirizzo: quello aperto dal player', async () => {
  const info = await json('/anime/1234-fire-force', EPISODE_INFO_JS)
  assert.equal(info.id, '102')
  assert.equal(info.number, '2')
  assert.deepEqual(info.next, { id: '103', number: '3' })
})

test('senza copertina né id: og:image, numero del primo episodio, conto dagli episodi', async () => {
  const info = await json('/anime/55-senza-copertina/1', EPISODE_INFO_JS)
  assert.equal(info.title, 'Senza copertina')
  assert.equal(info.image, 'https://img.example/og.jpg')
  assert.equal(info.count, 1)
  assert.equal(info.mal, '')
  assert.equal(info.anilist, '')
  assert.equal(info.first, '13')
})

test('pagina senza player: errore, non dati sbagliati', async () => {
  assert.deepEqual(await json('/anime/66-vuota/1', EPISODE_INFO_JS), { error: 'player' })
})

// --- NEXT_EPISODES_JS: episodi successivi in automatico ---

test('episodi successivi: i prossimi COUNT con il player di ognuno', async () => {
  const result = await json('/anime/1234-fire-force/101', NEXT_EPISODES_JS.replace('COUNT', '2'))
  assert.deepEqual(result, {
    next: [
      { id: '102', number: '2', embed: 'https://vix.example/embed/102' },
      { id: '103', number: '3', embed: 'https://vix.example/embed/103' },
    ],
  })
})

test('episodi successivi vicino alla fine: solo quelli che ci sono', async () => {
  const result = await json('/anime/1234-fire-force/103', NEXT_EPISODES_JS.replace('COUNT', '3'))
  assert.deepEqual(result.next.map((e) => e.id), ['104'])
  assert.deepEqual(await json('/anime/1234-fire-force/104', NEXT_EPISODES_JS.replace('COUNT', '3')), { next: [] })
})

test('episodi successivi senza player o con un episodio che non è in elenco: errore', async () => {
  assert.deepEqual(await json('/anime/66-vuota/1', NEXT_EPISODES_JS.replace('COUNT', '1')), { error: 'player' })
  assert.deepEqual(await json('/anime/1234-fire-force', NEXT_EPISODES_JS.replace('COUNT', '1')), { error: 'episode' })
})

// --- Player VixCloud: MASTER_PLAYLIST_JS ---

test('player VixCloud: playlist principale con token, senza parametri vuoti, h=1 se c’è il Full HD', async () => {
  const url = new URL(await run('/embed/1', MASTER_PLAYLIST_JS))
  assert.equal(url.origin + url.pathname, 'https://vix.example/playlist/1')
  assert.equal(url.searchParams.get('b'), '1')
  assert.equal(url.searchParams.get('token'), 'abc')
  assert.equal(url.searchParams.get('expires'), '1800000000')
  assert.equal(url.searchParams.get('h'), '1')
  assert.equal(url.searchParams.has('asn'), false)
  assert.equal(url.searchParams.has('x'), false)
  assert.equal(await run('/embed/2', MASTER_PLAYLIST_JS), 'https://vix.example/playlist/2?token=t')
  assert.equal(await run('/embed/vuoto', MASTER_PLAYLIST_JS), null)
})

// --- Sito di anime con pagine /<serie>-ep-<numero>/: SATURN_STREAM_JS ---

test('episodio con il player in un iframe: lo deve prendere il WebView invisibile', async () => {
  assert.deepEqual(await json('/serie-ita-ep-1/', SATURN_STREAM_JS), { frame: true })
})

test('episodio con la pagina /watch: link del video dal player', async () => {
  assert.deepEqual(await json('/ep/Serie-ep-2', SATURN_STREAM_JS), {
    stream: 'https://cdn.example/AAA/playlist.m3u8',
    watch: `${base}/watch?file=AAA`,
  })
})

test('player principale vuoto: si prova quello alternativo (&s=alt), link reso assoluto', async () => {
  assert.deepEqual(await json('/ep/Serie-ep-3', SATURN_STREAM_JS), {
    stream: `${base}/video/BBB.mp4`,
    watch: `${base}/watch?file=BBB&s=alt`,
  })
})

test('nessun player risponde: si restituisce la pagina /watch da aprire nel WebView invisibile', async () => {
  assert.deepEqual(await json('/ep/Serie-ep-4', SATURN_STREAM_JS), { watch: `${base}/watch?file=CCC` })
})

test('dentro la pagina /watch: link dal documento, se no si passa all’alternativo', async () => {
  assert.deepEqual(await json('/watch?file=AAA', SATURN_STREAM_JS), {
    stream: 'https://cdn.example/AAA/playlist.m3u8',
    watch: `${base}/watch?file=AAA`,
  })
  assert.deepEqual(await json('/watch?file=CCC', SATURN_STREAM_JS), { watch: `${base}/watch?file=CCC&s=alt` })
  assert.deepEqual(await json('/watch?file=CCC&s=alt', SATURN_STREAM_JS), { error: 'stream' })
})

test('episodio che non è ancora uscito (404) e pagina di controllo senza player', async () => {
  assert.deepEqual(await json('/serie-ita-ep-99/', SATURN_STREAM_JS), { missing: true })
  assert.deepEqual(await json('/ep/Serie-ep-5', SATURN_STREAM_JS), { error: 'watch' })
})

// --- Altri siti: PAGE_IMAGE_JS ---

test('immagine di anteprima della pagina: og:image, twitter:image o niente', async () => {
  assert.equal(await run('/og', PAGE_IMAGE_JS), 'https://img.example/og.jpg')
  assert.equal(await run('/twitter', PAGE_IMAGE_JS), 'https://img.example/tw.jpg')
  assert.equal(await run('/senza-immagine', PAGE_IMAGE_JS), '')
})
