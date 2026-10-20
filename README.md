# Cast to Kodi

App Android personale (Pixel 9 Pro XL) per guardare i video dei siti di streaming sulla Fire TV, gratis e senza pubblicità.

Il telefono fa da browser e da telecomando: apri il sito nell'app, avvii il player, l'app intercetta il flusso video (`.m3u8` / `.mp4`) e lo manda a **Kodi** sulla Fire TV, che lo riproduce direttamente. Il video non passa dal telefono, quindi la qualità è piena e il telefono si può bloccare.

Nel browser dell'app popup, `window.open` e salti verso altri domini vengono bloccati: sono quasi tutta la pubblicità di quei siti.

## Preparazione (una volta sola)

**Fire TV**
1. Installa Kodi (dall'Appstore Amazon, oppure con l'app Downloader da kodi.tv).
2. In Kodi: *Impostazioni → Servizi → Controllo*, attiva **Consenti il controllo remoto via HTTP**. Annota porta (di solito 8080), utente e password.
3. Annota l'IP della Fire TV: *Impostazioni → La mia Fire TV → Info → Rete*.

**PC**
1. Installa Android Studio (developer.android.com/studio). Al primo avvio scarica da solo SDK e JDK.
2. *File → Open* sulla cartella del progetto e aspetta la sincronizzazione Gradle.

**Pixel**
1. *Impostazioni → Info sul telefono*: tocca 7 volte "Numero build" per sbloccare le Opzioni sviluppatore.
2. *Impostazioni → Sistema → Opzioni sviluppatore*: attiva **Debug USB**.
3. Collega il telefono via USB, sceglilo in Android Studio e premi ▶️ Run.

## Uso
1. Al primo avvio inserisci `IP:porta` di Kodi (es. `192.168.1.50:8080`) ed eventualmente `utente:password`. Si cambia dopo con il pulsante **Kodi**.
2. Scrivi l'indirizzo del sito, vai all'episodio e **avvia il player** (il flusso si rileva solo quando parte).
3. Premi **Manda a Kodi**. Se ci sono più flussi scegli il primo: di solito è la playlist master.

Telefono e Fire TV devono essere sulla stessa rete Wi-Fi.
