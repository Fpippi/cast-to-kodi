<p align="center">
  <img src="docs/icon.svg" width="112" alt="Icona di Couch Cast: un telefono davanti a una TV con il tasto play">
</p>

<h1 align="center">Couch Cast</h1>

<p align="center">
  Dal telefono alla Fire TV, gratis e senza pubblicità.<br>
  Apri il sito sul Pixel, il video parte su <b>Kodi</b>.
</p>

<p align="center">
  <a href="https://github.com/Fpippi/cast-to-kodi/releases/latest"><img src="https://img.shields.io/github/v/release/Fpippi/cast-to-kodi?label=ultima%20build&color=0D5563" alt="Ultima build"></a>
  <a href="https://github.com/Fpippi/cast-to-kodi/actions/workflows/apk.yml"><img src="https://github.com/Fpippi/cast-to-kodi/actions/workflows/apk.yml/badge.svg?branch=main" alt="APK"></a>
  <a href="https://github.com/Fpippi/cast-to-kodi/actions/workflows/test.yml"><img src="https://github.com/Fpippi/cast-to-kodi/actions/workflows/test.yml/badge.svg" alt="Test"></a>
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white" alt="Android 13+">
  <img src="https://img.shields.io/badge/Kotlin-senza%20dipendenze-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
</p>

<p align="center">
  <a href="https://github.com/Fpippi/cast-to-kodi/releases/latest/download/CastToKodi.apk"><b>📥 Scarica l'APK</b></a>
</p>

---

App Android personale (Pixel 9 Pro XL) per guardare i video dei siti di streaming sulla Fire TV. Sul telefono si chiama **Couch Cast**; repo, release e APK (`CastToKodi.apk`) mantengono il vecchio nome, così aggiornamenti e Obtainium continuano a funzionare.

## ✨ Come funziona

```
📱 Pixel (browser + telecomando)  ──link del video──▶  📺 Kodi sulla Fire TV  ──▶  🌐 sito di streaming
```

- 🔎 Il telefono fa da browser: apri il sito nell'app e avvii il player.
- 🎯 L'app intercetta il flusso video (`.m3u8` / `.mp4`) e lo manda a **Kodi**, che lo riproduce direttamente.
- 🔋 Il video non passa dal telefono: qualità piena e telefono che si può bloccare.
- 🚫 Popup, `window.open` e salti verso altri domini vengono bloccati: sono quasi tutta la pubblicità di quei siti.
- 📋 Coda degli episodi nella playlist di Kodi, e su AnimeUnity gli episodi successivi si accodano da soli.

## 📺 Dispositivi compatibili

L'app parla con **Kodi**, non con la TV: funziona su qualunque dispositivo dove gira Kodi con il controllo remoto via HTTP. Il pulsante **⏻** invece usa ADB in rete (porta 5555) e l'HDMI-CEC, quindi va solo su Fire OS e Android TV.

| Dispositivo | Kodi | Manda a Kodi | ⏻ Accendi |
|---|---|---|---|
| Fire TV Stick / Cube con **Fire OS**, TV con Fire TV integrata | APK con Downloader | ✅ | ✅ Debug ADB in rete già previsto |
| Fire TV con **Vega OS** (Stick 4K Select, Stick HD 2026) | ❌ non si installa | ❌ | ❌ |
| **Android TV** (Sony Bravia, Philips, TCL, Hisense Android, Sharp…) | Play Store | ✅ | ⚠️ dipende dal modello: serve il debug ADB sulla porta 5555 |
| **Nvidia Shield TV** | Play Store | ✅ | ✅ *Opzioni sviluppatore → Debug di rete* |
| Box Android generici (Xiaomi Mi Box, Homatics, Onn…) | Play Store o APK | ✅ | ⚠️ come Android TV |
| **Google TV** (Chromecast con Google TV, Google TV Streamer, TV Google TV) | Play Store | ✅ | ⚠️ di solito no: il *Debug wireless* usa l'abbinamento con codice e una porta che cambia, non la 5555 |
| PC Windows / Mac / Linux collegato alla TV | kodi.tv | ✅ | ❌ |
| Raspberry Pi, LibreELEC, CoreELEC, OSMC | kodi.tv / immagine dedicata | ✅ | ❌ |
| Xbox One / Series | Microsoft Store | ✅ | ❌ |
| **LG webOS** | ⚠️ solo port della comunità (Homebrew, modalità sviluppatore), webOS 4+ | ⚠️ in teoria sì, non provato | ❌ |
| **Apple TV** (HD e 4K) | ⚠️ solo sideload con Xcode da un Mac, da rifirmare | ⚠️ in teoria sì, non provato | ❌ |
| **Samsung Tizen**, **Roku**, Chromecast senza Google TV | ❌ Kodi non esiste | ❌ | ❌ |

- ✅ = funziona, ⚠️ = funziona con limiti o non verificato, ❌ = no. Provato davvero solo sulla Fire TV con Fire OS.
- Su TV Samsung, LG, Roku si può comunque attaccare all'HDMI un dispositivo della lista (es. una Fire TV Stick con Fire OS o un Chromecast con Google TV).
- **⏻ su Android TV / Google TV**: l'app si collega in ADB "classico" alla porta 5555. Se il dispositivo ha solo il *Debug wireless* di Android 11+, la 5555 si può aprire una volta da un PC (`adb tcpip 5555`), ma si richiude a ogni riavvio. In sonno profondo molti dispositivi spengono la rete: lì il pulsante non ha nessuno da svegliare.

## 🛠️ Preparazione (una volta sola)

### 📺 Fire TV

0. Serve una Fire TV con **Fire OS** (es. Fire TV Stick 4K Max e precedenti): controlla in *Impostazioni → La mia Fire TV → Info*. I modelli con **Vega OS** (Fire TV Stick 4K Select, Fire TV Stick HD 2026) non permettono di installare Kodi.
1. Kodi non è nell'Appstore Amazon: installa l'app **Downloader** dall'Appstore, abilitala alle "app da origini sconosciute" (in *La mia Fire TV → Opzioni sviluppatore*; se non le vedi, tocca 7 volte il nome del dispositivo in *Info*) e scarica da kodi.tv l'APK Android ARM a 32 bit.
2. In Kodi: *Impostazioni → Servizi → Controllo*, attiva **Consenti il controllo remoto via HTTP**. Annota porta (di solito 8080), utente e password.
3. In Kodi: *Impostazioni → Servizi → Generale*, attiva **Annuncia servizi ad altri sistemi**: così l'app trova Kodi da sola. Se non funziona, annota l'IP della Fire TV (*Impostazioni → La mia Fire TV → Info → Rete*) e scrivilo a mano.
4. Per accendere la TV dal telefono (pulsante di accensione, accanto a ★): attiva *La mia Fire TV → Opzioni sviluppatore → **Debug ADB***, *Impostazioni → Display e audio → **Controllo dispositivi HDMI CEC*** e l'HDMI-CEC sulla TV (Anynet+, SimpLink, Bravia Sync… a seconda della marca). Se con Alexa la TV si accende già sull'ingresso giusto, il CEC è a posto.

### 📱 App sul telefono, senza PC

1. A ogni push su `main` GitHub Actions compila l'APK e lo pubblica come release "Versione X.Y.Z" (tag `vX.Y.Z`), con l'elenco delle novità preso da [CHANGELOG.md](CHANGELOG.md). Dal Pixel apri [releases/latest](https://github.com/Fpippi/cast-to-kodi/releases/latest) e scarica `CastToKodi.apk`.
2. Le build sono firmate con una chiave fissa (secrets `KEYSTORE_BASE64` e `KEYSTORE_PASSWORD` del repo; l'originale non è nel repo), quindi ogni APK si installa sopra il precedente. Se la chiave si perde, ne serve una nuova e va disinstallata l'app una volta.
3. 🔔 All'avvio l'app avvisa se c'è una build nuova. Per aggiornare con un tocco si può usare **Obtainium** con l'indirizzo del repo.

<details>
<summary>💻 Con il PC e Android Studio (in alternativa)</summary>

**PC**
1. Installa Android Studio (developer.android.com/studio). Al primo avvio scarica da solo SDK e JDK.
2. *File → Open* sulla cartella del progetto e aspetta la sincronizzazione Gradle.

**Pixel**
1. *Impostazioni → Info sul telefono*: tocca 7 volte "Numero build" per sbloccare le Opzioni sviluppatore.
2. *Impostazioni → Sistema → Opzioni sviluppatore*: attiva **Debug USB**.
3. Collega il telefono via USB, sceglilo in Android Studio e premi ▶️ Run.

</details>

## ▶️ Uso

1. 📡 Al primo avvio si apre **Dov'è Kodi?**: in *Automatico* l'app cerca Kodi in rete e lo selezioni; in *Manuale* scrivi `IP:porta` (es. `192.168.1.50:8080`). Sotto, utente e password se impostati in Kodi (se mancano, l'app li chiede al primo invio). Si cambia dopo con **Kodi → Dov'è Kodi?**. Se la Fire TV cambia IP, all'invio l'app la ritrova da sola. Le credenziali restano sul telefono, con la password cifrata, legate a quel Kodi e alla rete di casa: su un'altra rete si richiedono.
2. 🔌 Con la TV spenta premi il **pulsante di accensione** (accanto a ★): l'app trova da sola la Fire TV in rete (anche senza Kodi aperto), la sveglia, la TV si accende e dopo qualche secondo passa sul suo ingresso, poi si apre Kodi; quando risponde l'app scrive "Kodi è pronto". La prima volta la TV chiede di consentire il debug da questo telefono: spunta *Consenti sempre* e premi OK. Se la Fire TV è in sonno profondo non risponde in rete: lì servono Alexa o il telecomando.
3. 🔍 Nella barra in alto scrivi un indirizzo o una ricerca (es. `jojo animeunity`, va su Google). Con **★** salvi la pagina nei preferiti (con un nome a scelta) e li riapri; con **⋮** accanto a un preferito lo rinomini o lo togli.
4. 🎬 Vai all'episodio e **avvia il player** (il flusso si rileva solo quando parte).
5. 📤 Premi **Manda a Kodi**. La pubblicità è già scartata; se restano più video, la playlist principale è la prima.
6. 📋 **Coda**: tutto passa dalla playlist video di Kodi e non viene mai cancellata. *Manda a Kodi* inserisce il video subito dopo quello in corso e parte; **+ Coda** lo mette in fondo. Se l'episodio non potrebbe partire (video in corso non avviato dall'app, link che scade prima del suo turno) l'app non lo accoda e spiega perché. Con **Kodi → Coda di Kodi** la vedi: il video in corso (▶), quelli già visti, quando parte ognuno e se il link è scaduto; tocca un video per farlo partire o toglierlo, **Svuota** toglie tutto tranne quello in corso.
7. 🪵 Se Kodi dice *Playback failed*: pulsante **Kodi → Log di Kodi**. L'app legge le ultime righe del log dalla Fire TV via ADB (come il pulsante di accensione, niente add-on) e con **Condividi** le mandi dove serve.

> [!IMPORTANT]
> Telefono e Fire TV devono essere sulla stessa rete Wi-Fi.

## 🧩 Addon per siti

Funzioni in più che valgono solo per alcuni siti o player. Il resto dell'app funziona su qualunque sito che serve `.m3u8` o `.mp4`.

### ⏭️ AnimeUnity

- Dopo l'invio l'app accoda da sola gli episodi successivi (1 di default, fino a 3, si cambia nel dialogo **Kodi**; 0 = spento). Solo sulle pagine degli episodi di `animeunity.*`.
- Legge l'elenco degli episodi dalla pagina e il link di ognuno dal player VixCloud: se il sito cambia struttura l'app avvisa e resta **+ Coda** a mano.

### 🎞️ Player VixCloud (AnimeUnity, StreamingCommunity e altri siti che lo usano)

- Riconosce la playlist anche senza `.m3u8` (`/playlist/<id>`) e scarta i suoi pezzi (audio, video, singole qualità), così resta solo la playlist principale.
- Il link ha un token che scade (`expires=`): **+ Coda** e l'accodamento automatico non mettono in coda un episodio che partirebbe dopo la scadenza.

### 🔍 Google

- Dai risultati di Google si può aprire qualunque sito: il blocco dei salti verso altri domini lì è spento.

## 🧪 Test

Le regole dell'app che non dipendono da Android (barra degli indirizzi, blocco dei salti, riconoscimento di video e pubblicità) stanno in `Rules.kt`, quelle dei singoli siti (gli addon qui sopra) in `sites/`, e sono provate in `app/src/test`. Su ogni PR verso `develop` il workflow **Test** esegue `./gradlew testDebugUnitTest`; verso `main` anche `assembleDebug`, e lì andranno i controlli più pesanti; con Android Studio si lanciano con il ▶️ accanto alla classe di test.

## 🌿 Branch

- **`develop`** è dove si lavora: ogni modifica parte da qui e ci torna con una PR.
- **`main`** è solo per i rilasci: una PR da `develop` a `main` pubblica una nuova versione (il workflow APK parte solo con i push su `main`).
- Prima di quella PR si aggiunge in cima a [CHANGELOG.md](CHANGELOG.md) la sezione `## X.Y.Z` con le novità: il primo numero per i cambi grossi, il secondo per le funzioni nuove, il terzo per le correzioni. Se manca, il controllo **test** della PR lo dice.

<details>
<summary>⚙️ Impostazioni su GitHub (una volta sola)</summary>

1. *Settings → General → Default branch*: scegli `develop`, così le PR nuove puntano lì da sole.
2. *Settings → Rules → Rulesets → New branch ruleset*, con target `develop` e `main`: attiva **Restrict deletions**, **Require a pull request before merging** e **Require status checks to pass** con il controllo **test**. Così una PR con i test rossi non si può unire e nessuno pusha direttamente sui due branch.

</details>
