# Novità

Ogni rilascio ha qui una sezione `## X.Y.Z`: la prima in alto dà il nome alla versione (in app e su GitHub) e il suo
elenco diventa la descrizione della release. Prima della PR `develop` → `main` si aggiunge la sezione nuova in cima:

- **X** (1.0.0 → 2.0.0) per i cambi grossi, che cambiano il modo di usare l'app;
- **Y** (1.0.0 → 1.1.0) per le funzioni nuove;
- **Z** (1.0.0 → 1.0.1) per le correzioni.

## 1.2.2

- Il pulsante di accensione ha la stessa altezza di ★ e Kodi.
- AnimeUnity: l'accodamento automatico degli episodi successivi parte spento (si accende da Kodi → Dov'è Kodi?). Anche chi
  l'aveva già acceso lo ritrova spento una volta.
- Tutorial passo passo con animazioni in docs/TUTORIAL.md, collegato dal README.
- Test con un Kodi finto e un sito finto che provano invio, coda e password.

## 1.2.1

- StreamingCommunity (player vidxgo): le playlist del video le scarica l'app al posto del browser e se ne tiene una
  copia, così all'invio Kodi la riceve dal telefono invece di richiederla al sito, che rispondeva 403.

## 1.2.0

- Manda a Kodi: all'avvio il telefono scarica la playlist e la chiave del video e le passa a Kodi in rete locale. La
  Fire TV ci metteva 10-20 secondi per ognuna e Kodi rinuncia dopo 30 ("Playback failed"). I pezzi del video Kodi li
  prende sempre dal sito: il telefono serve solo nei primi secondi.
- Manda a Kodi: con il video si mandano anche Origin e cookie del sito, come fa il browser (StreamingCommunity con il
  player vidxgo rispondeva 403).
- Manda a Kodi e + Coda restano spenti, con "Invio a Kodi…", finché Kodi non risponde: i tocchi ripetuti non mandano
  altri video.
- Niente più avviso "Popup bloccato" sopra al pulsante: i popup restano bloccati, in silenzio.
- Pulsante Kodi → Coda di Kodi: elenco dei video in coda con quando parte ognuno; si può far partire o togliere un
  video, o svuotare la coda tranne quello in corso.

## 1.1.2

- Manda a Kodi: tolto il controllo "è partito?" aggiunto nella 1.0.2. Con Kodi lento a rispondere riapriva lo stesso
  video mentre si stava ancora caricando, e la seconda apertura interrompeva la prima.

## 1.1.1

- Manda a Kodi: per i video HLS (es. AnimeUnity/VixCloud) l'app manda a Kodi una sola qualità, la migliore, invece della
  playlist con tutte: Kodi sulla Fire TV le apriva tutte per scegliere e andava oltre il suo limite di 30 secondi
  ("Playback failed").
- Manda a Kodi: niente più seconda apertura dello stesso video mentre Kodi lo sta ancora caricando (interrompeva la prima).

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
