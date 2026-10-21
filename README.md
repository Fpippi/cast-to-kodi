# Cast to Kodi

App Android personale (Pixel 9 Pro XL) per guardare i video dei siti di streaming sulla Fire TV, gratis e senza pubblicità.

Il telefono fa da browser e da telecomando: apri il sito nell'app, avvii il player, l'app intercetta il flusso video (`.m3u8` / `.mp4`) e lo manda a **Kodi** sulla Fire TV, che lo riproduce direttamente. Il video non passa dal telefono, quindi la qualità è piena e il telefono si può bloccare.

Nel browser dell'app popup, `window.open` e salti verso altri domini vengono bloccati: sono quasi tutta la pubblicità di quei siti.

## Preparazione (una volta sola)

**Fire TV**
0. Serve una Fire TV con **Fire OS** (es. Fire TV Stick 4K Max e precedenti): controlla in *Impostazioni → La mia Fire TV → Info*. I modelli con **Vega OS** (Fire TV Stick 4K Select, Fire TV Stick HD 2026) non permettono di installare Kodi.
1. Kodi non è nell'Appstore Amazon: installa l'app **Downloader** dall'Appstore, abilitala alle "app da origini sconosciute" (in *La mia Fire TV → Opzioni sviluppatore*; se non le vedi, tocca 7 volte il nome del dispositivo in *Info*) e scarica da kodi.tv l'APK Android ARM a 32 bit.
2. In Kodi: *Impostazioni → Servizi → Controllo*, attiva **Consenti il controllo remoto via HTTP**. Annota porta (di solito 8080), utente e password.
3. In Kodi: *Impostazioni → Servizi → Generale*, attiva **Annuncia servizi ad altri sistemi**: così l'app trova Kodi da sola. Se non funziona, annota l'IP della Fire TV (*Impostazioni → La mia Fire TV → Info → Rete*) e scrivilo a mano.

**App sul telefono, senza PC**
1. A ogni push GitHub Actions compila l'APK e lo pubblica come release "Build N" (tag `build-N`). Dal Pixel apri `github.com/Fpippi/cast-to-kodi/releases/latest` e scarica `CastToKodi.apk`.
2. Le build sono firmate con una chiave fissa (secrets `KEYSTORE_BASE64` e `KEYSTORE_PASSWORD` del repo; l'originale non è nel repo), quindi ogni APK si installa sopra il precedente. Se la chiave si perde, ne serve una nuova e va disinstallata l'app una volta.
3. All'avvio l'app avvisa se c'è una build nuova. Per aggiornare con un tocco si può usare **Obtainium** con l'indirizzo del repo.

**PC (in alternativa)**
1. Installa Android Studio (developer.android.com/studio). Al primo avvio scarica da solo SDK e JDK.
2. *File → Open* sulla cartella del progetto e aspetta la sincronizzazione Gradle.

**Pixel**
1. *Impostazioni → Info sul telefono*: tocca 7 volte "Numero build" per sbloccare le Opzioni sviluppatore.
2. *Impostazioni → Sistema → Opzioni sviluppatore*: attiva **Debug USB**.
3. Collega il telefono via USB, sceglilo in Android Studio e premi ▶️ Run.

## Uso
1. Al primo avvio si apre **Dov'è Kodi?**: in *Automatico* l'app cerca Kodi in rete e lo selezioni; in *Manuale* scrivi `IP:porta` (es. `192.168.1.50:8080`). Sotto, `utente:password` se impostati in Kodi. Si cambia dopo con il pulsante **Kodi**. Se la Fire TV cambia IP, all'invio l'app la ritrova da sola.
2. Nella barra in alto scrivi un indirizzo o una ricerca (es. `jojo animeunity`, va su Google). Con **★** salvi la pagina nei preferiti e li riapri; pressione lunga per toglierne uno.
3. Vai all'episodio e **avvia il player** (il flusso si rileva solo quando parte).
4. Premi **Manda a Kodi**. Pubblicità e pezzi della playlist VixCloud sono già scartati; se restano più video, la playlist principale è la prima.
5. **Coda**: tutto passa dalla playlist video di Kodi e non viene mai cancellata. *Manda a Kodi* inserisce il video subito dopo quello in corso e parte; **+ Coda** lo mette in fondo. Se l'episodio non potrebbe partire (video in corso non avviato dall'app, link che scade prima del suo turno) l'app non lo accoda e spiega perché.
6. **AnimeUnity**: dopo l'invio l'app accoda da sola gli episodi successivi (1 di default, fino a 3, si cambia nel dialogo **Kodi**; 0 = spento).

Telefono e Fire TV devono essere sulla stessa rete Wi-Fi.

## Test

Le regole dell'app che non dipendono da Android (barra degli indirizzi, blocco dei salti, riconoscimento di video e pubblicità) stanno in `Rules.kt` e sono provate in `app/src/test`. Su ogni PR verso `main` il workflow **Test** esegue `./gradlew testDebugUnitTest assembleDebug`; con Android Studio si lanciano con il ▶️ accanto alla classe di test.

Perché una PR con i test rossi non si possa unire: su GitHub *Settings → Branches → Add branch ruleset* (o *Add rule*) per `main`, attiva **Require a pull request before merging** e **Require status checks to pass**, e aggiungi il controllo **test**.
