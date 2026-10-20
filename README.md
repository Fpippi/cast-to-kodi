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
1. Al primo avvio l'app cerca Kodi in rete e compila `IP:porta` (oppure lo scrivi a mano, es. `192.168.1.50:8080`); aggiungi `utente:password`. Si cambia dopo con il pulsante **Kodi**. Se la Fire TV cambia IP, all'invio l'app la ritrova da sola.
2. Scrivi l'indirizzo del sito, vai all'episodio e **avvia il player** (il flusso si rileva solo quando parte).
3. Premi **Manda a Kodi**. Se ci sono più flussi scegli il primo: di solito è la playlist master.

Telefono e Fire TV devono essere sulla stessa rete Wi-Fi.
