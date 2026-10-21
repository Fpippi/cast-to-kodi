# Novità

Ogni rilascio ha qui una sezione `## X.Y.Z`: la prima in alto dà il nome alla versione (in app e su GitHub) e il suo
elenco diventa la descrizione della release. Prima della PR `develop` → `main` si aggiunge la sezione nuova in cima:

- **X** (1.0.0 → 2.0.0) per i cambi grossi, che cambiano il modo di usare l'app;
- **Y** (1.0.0 → 1.1.0) per le funzioni nuove;
- **Z** (1.0.0 → 1.0.1) per le correzioni.

## 1.1.0

- Log di Kodi: nel dialogo **Kodi** il pulsante *Log di Kodi* legge le ultime righe del log dalla Fire TV via ADB e le
  fa condividere, così si vede perché Kodi non riproduce un video senza installare add-on.

## 1.0.3

- Popup: invece di rifiutarli, l'app dà ai popup una finestra finta e invisibile che non scarica niente. I player che
  partono solo dopo aver aperto il popup pubblicitario (dove prima compariva solo "Popup bloccato" e il video restava
  a caricare) ora partono, e la pubblicità resta fuori lo stesso.

## 1.0.2

- Manda a Kodi: se Kodi non riesce a far partire il video dalla sua playlist ("Playback failed", es. AnimeUnity con il
  player VixCloud), l'app lo riapre direttamente come nelle prime build. In quel caso il video parte senza coda dietro.

## 1.0.1

- Pulsante di accensione: trova la Fire TV da solo anche senza l'IP di Kodi salvato (prova l'ultimo IP e poi cerca in
  rete sulla porta 5555), e dopo il risveglio chiede alla TV di passare all'ingresso della Fire TV.

## 1.0.0

Prima versione con un numero al posto di "Build N" (l'ultima era la Build 18).

- Le release si chiamano "Versione X.Y.Z" e hanno l'elenco delle novità; il link per scaricare l'APK resta lo stesso.
- L'avviso di aggiornamento confronta le versioni (es. "Installata: 1.0.0, ultima: 1.1.0").
- Il pulsante di accensione ha un'icona vera: il carattere ⏻ sul Pixel non si vedeva.
- Nel README la lista dei dispositivi compatibili.
- Tutto quello che c'era fino alla Build 18: browser con blocco dei popup, invio del video a Kodi, coda nella playlist
  di Kodi, episodi successivi automatici su AnimeUnity, preferiti, credenziali di Kodi cifrate, ricerca da Google
  e pulsante ⏻ per accendere la TV.
