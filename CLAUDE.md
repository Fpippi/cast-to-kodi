@README.md

## Stato e contesto per riprendere il lavoro

- Progetto nato il 2026-10-20. Kodi è installato e configurato sulla Fire TV dell'utente; l'app si installa dal telefono scaricando l'APK dalla release `latest`.
- 2026-10-20: provato dall'utente su AnimeUnity (VixCloud), funziona: Kodi riproduce i flussi con gli header `Referer` e `User-Agent` passati come `url|Header=valore`. Altri siti non ancora provati.
- Solo i push su `main` producono una release (i tag e gli altri branch no). Le modifiche arrivano su `main` con una PR: il workflow `test.yml` (job `test`) deve passare. Le funzioni da fare sono nelle issue GitHub.
- Ordine concordato delle issue, una alla volta: #2 ricerca Google → #3 preferiti → #5 filtro dei video → #4 coda → #1 ricerca automatica di Kodi.
- Fatto finora (Build 9): rilevamento VixCloud, controllo aggiornamenti senza token, firma fissa, barra di caricamento, pulsante verde quando c'è un video.
- 2026-10-20: la storia git è stata riscritta (autore unico `Fpippi` con email noreply). Un clone fatto prima va riallineato con `git fetch origin && git reset --hard origin/main`.
- Si compila solo su GitHub Actions (`.github/workflows/apk.yml`): l'ambiente di Claude Code non scarica l'SDK Android. Gli errori di build si leggono dai log della run. Chi ha Android Studio può comunque aprire il progetto e compilarlo in locale.
- Controllo veloce senza SDK (es. nella sessione online, prima di pushare un branch che non va su main): compilare `MainActivity.kt` con `kotlinc` 2.0.21 (zip dalle release GitHub di JetBrains/kotlin) e `-cp` sul jar `org.robolectric:android-all:15-robolectric-*` di Maven Central. Verifica tipi e API, non manifest né risorse.
- Logica pura in `Rules.kt` (niente `android.*`: nei test JVM le API Android non funzionano), test in `app/src/test/.../RulesTest.kt` con JUnit 4, unica dipendenza ed è solo `testImplementation`. Nuova logica testabile va lì, non nell'Activity. Senza SDK si provano con `kotlinc Rules.kt RulesTest.kt -cp junit.jar` e `org.junit.runner.JUnitCore`.

## Decisioni prese e perché
- **Kodi come ricevitore** invece di un'app nostra sulla Fire TV: è gratuito, senza pubblicità, ha già un'API JSON-RPC (`Player.Open`) e riproduce HLS. Così c'è da scrivere solo l'app sul telefono.
- Scartati **Web Video Caster** (pubblicità, il Premium è a pagamento: l'utente non vuole pagare) e **AirScreen** (duplica lo schermo: qualità più bassa e telefono sempre acceso).
- Il Pixel trasmette solo con Google Cast e la Fire TV non lo supporta in modo nativo: per questo serve tutto questo giro.
- Chiave di firma fissa nei secrets di GitHub, mai nel repo (l'utente la tiene in `D:\Progetti\casttokodi-firma`): così gli aggiornamenti si installano senza disinstallare. L'app avvisa delle build nuove leggendo `releases/latest` senza token.
- **Il repo è pubblico**: niente segreti, IP di casa, credenziali o dati personali nei file committati.
- I commit portano solo l'utente come autore: niente `Co-Authored-By` né altre righe di attribuzione.
- App per uso personale, minSdk 33, nessuna dipendenza oltre alla libreria standard di Kotlin, UI costruita in codice in un'unica Activity.

## Trappole note
- Il rilevamento del video guarda l'estensione del path, più `/playlist/<id>` di VixCloud (AnimeUnity), che non ha `.m3u8`. Se un sito serve la playlist senza `.m3u8`, va riconosciuta dal Content-Type.
- Il blocco dei redirect lascia passare solo il dominio corrente ("ultime due etichette" dell'host). I redirect HTTP sono ammessi apposta, perché i siti cambiano spesso dominio.
- Ricerca di Kodi con `NsdManager` sul servizio `_xbmc-jsonrpc-h._tcp`, 3 secondi, solo IPv4. `resolveService` è deprecato ma su API 33 non c'è alternativa e accetta una risoluzione alla volta (da qui la coda). Non ancora verificato che Kodi su Fire TV annunci davvero il servizio (issue #1).
- Coda = playlist video di Kodi (id 1), mai `Playlist.Clear`. Manda a Kodi: `Playlist.Insert` dopo la posizione corrente + `Player.GoTo`, oppure (Kodi fermo o video non partito dall'app) `Playlist.Add` in fondo + `Player.Open` da lì. Se Kodi rifiuta il video in playlist si ripiega su `Player.Open {file}` senza coda (#11). "+ Coda" rifiuta con un dialogo se il video in corso non è nella playlist o se il token VixCloud (`expires=`) scade prima del turno stimato, con gli episodi lunghi come quello in corso (#12).
- AnimeUnity, episodi successivi in automatico (#15): JS nella pagina rilegge l'HTML con XHR sincrono e prende `episodes` ed `embed_url` dall'elemento `<video-player>`; il link si legge da `window.masterPlaylist` del player VixCloud caricato in un WebView invisibile. Dipende dalla struttura del sito: se cambia, si rompe qui (l'app avvisa e resta "+ Coda" manuale). Non ancora provato sul sito vero.
- Il filtro pubblicità (`AD_HOSTS`, `isAd`) è un elenco corto e approssimativo: allungarlo quando un sito mostra ads tra i video trovati.
- Colori solo da `android.R.color.system_*` (Material You) e tema `Theme.DeviceDefault.DayNight` in `res/values/themes.xml`: niente colori scritti a mano.
- La ricerca da Google apre altri domini: il blocco dei salti li lascia passare solo se la pagina corrente è `google.*` e il tocco è dell'utente (`hasGesture`).
- `targetSdk 35` forza l'edge-to-edge: il padding degli insets nella root serve.
