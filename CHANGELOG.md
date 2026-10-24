# Novità

Ogni rilascio ha qui una sezione `## X.Y.Z`: la prima in alto dà il nome alla versione (in app e su GitHub) e il suo
elenco diventa la descrizione della release. Prima della PR `develop` → `main` si aggiunge la sezione nuova in cima:

- **X** (1.0.0 → 2.0.0) per i cambi grossi, che cambiano il modo di usare l'app;
- **Y** (1.0.0 → 1.1.0) per le funzioni nuove;
- **Z** (1.0.0 → 1.0.1) per le correzioni.

## 2.2.4

- Copertina delle serie più nitida: prima veniva rimpicciolita troppo prima di essere tagliata per la scheda. Quelle
  già salvate si riscaricano al prossimo episodio mandato.
- L'icona di un sito nei preferiti e in **Continua a guardare** si aggiorna davvero quando lo visiti, anche se il
  preferito era salvato su un altro dominio dello stesso sito (prima poteva restare quella di Google).

## 2.2.3

- **Riprendi su Kodi** e il rinvio di un episodio a metà ripartono dal minuto anche quando Kodi, mentre apre il video,
  per qualche secondo risponde con un errore (prima la ripresa si arrendeva al primo errore).
- Se la copertina della serie non arriva, l'app dice perché (pagina che non la indica, scaricamento fallito).

## 2.2.2

- **Continua a guardare**: la scheda della serie ha la copertina presa dal sito (i siti di anime con player VixCloud dalla pagina della serie,
  gli altri siti dall'immagine di anteprima della pagina). Il fotogramma del video resta solo se la copertina manca.
- I preferiti non prendono più l'icona di Google quando si salvano appena arrivati da una ricerca, e quelli che
  l'avevano presa si correggono alla prossima visita del sito.
- Il nome di pagine e video non comincia più con il nome del sito e "~" (ultima pagina, barra di Kodi, coda, nuovi preferiti).

## 2.2.1

- **Continua a guardare** sul sito di anime con player VixCloud: la serie non compariva. Il sito cambia episodio senza caricare una pagina nuova,
  e l'app restava ferma all'indirizzo di prima (spesso la pagina della serie, che non contava come episodio). Ora segue
  l'indirizzo, e anche un episodio mandato dalla pagina della serie entra nella fila.
- Se su quel sito la serie non si salva, l'app ora lo dice con il motivo, invece di tacere.
- Il nome della serie non comincia più con il nome del sito e "~".

## 2.2.0

- **Continua a guardare** nella pagina iniziale: una scheda per ogni serie mandata a Kodi, ferma all'episodio e al minuto
  giusti (*Ep. 12 · fermo a 12:40*). Un episodio è visto quando Kodi passa il 95% o mancano meno di 2 minuti (la sigla),
  e la scheda passa al prossimo. Una serie finita resta con **Finita** finché non la togli.
- Toccando una serie: episodio prima, in corso e prossimo, **Riprendi su Kodi** (sito di anime con player VixCloud: parte senza aprire la
  pagina, dal minuto in cui eri) e **Apri qui**. Rimandando un episodio a metà da qualunque sito, Kodi riparte da lì.
- La **✕** sulla scheda chiede conferma (la scheda diventa rossa per 4 secondi), poi c'è ancora **Annulla**.
- **Ultima pagina** con la ✕ per toglierla, al posto di "Riprendi" che non si poteva cancellare.
- Sul sito, se apri un altro episodio di una serie che stai guardando: **Eri all'episodio 12** con **Vai**.
- La serie si riconosce anche quando il sito cambia dominio, e si riapre sull'ultimo dominio visto.
- **Impostazioni → Cronologia**: *Ricorda dove sono arrivato* e *Cancella la cronologia*.

## 2.1.2

- **Condividi → Couch Cast**: da Chrome, WhatsApp o un'altra app un link si apre nell'app, dove il video si trova come
  al solito. Se nel testo c'è una frase si prende il primo link; senza link il testo si cerca su Google.
- Se il link condiviso è già un video (`.m3u8`, `.mp4`) un pannello propone **Manda a Kodi** subito o **Apri qui**.
- README e guida aggiornati all'interfaccia 2.1, in italiano e in inglese, con le animazioni ridisegnate.

## 2.1.1

- **Barra di Kodi** in fondo rifatta come nel mockup: immagine a sinistra, nome e stato con il pallino (acceso o
  spento), play/pausa e telecomando della stessa misura, tutto centrato su una riga; l'avanzamento è una linea sottile
  sul bordo in basso. Stesso colore e stessi margini della barra in alto.

## 2.1.0

L'interfaccia come nel mockup scelto, meno piatta, e il telecomando a portata di dito.

- **Barra di Kodi sempre in fondo**: cosa riproduce Kodi, "niente in riproduzione" o, se è spento, il pulsante per
  accenderlo. Toccandola si apre il telecomando, senza passare dal menu.
- **Telecomando a schermo intero** con le schede *Telecomando* e *Coda*: il video in corso con l'ora in cui finisce,
  play/pausa grande tra −10 e +10 s, la croce tonda con OK, tasti tondi per Indietro, Home e volume, il resto in una
  fila che scorre. Si chiude con ⌄, con indietro o trascinandolo giù.
- **Coda** con l'ora in cui parte ogni episodio ("Parte alle 22:16"), i link che scadono in rosso e ⋮ accanto a ogni
  video per farlo partire o toglierlo.
- **Scelta del video** con immagini grandi e la durata sopra: il primo è già scelto, con *Manda* e *In coda* sotto; i
  video sotto il minuto sono sbiaditi.
- Barra in alto più pulita: solo il nome del sito (l'indirizzo intero torna toccandola) e niente ⏻, che ora sta nella
  barra di Kodi.

## 2.0.0

Nuova interfaccia: l'app cambia nel modo in cui si usa. Riassume anche la 1.3.0.

- **Pagina iniziale** all'avvio: Kodi (acceso, spento o cosa sta riproducendo, con *Accendi* o *Telecomando*),
  **Riprendi** con l'ultima pagina guardata e i preferiti come icone.
- **Telecomando** di Kodi nell'app e **barra di Kodi** in fondo mentre un video è in riproduzione (nome, tempo,
  play/pausa; toccandola si apre il telecomando).
- **Impostazioni** (⋮ → Impostazioni) al posto del dialogo "Dov'è Kodi?": Kodi e accesso, accensione della TV, episodi
  successivi automatici, log di Kodi, guida e versione (con "cerca aggiornamenti"). Ogni voce dice com'è adesso.
- Il menu ⋮ ora ha Telecomando, Coda di Kodi, Pagina iniziale e Impostazioni.
- Barra in alto a pillola con l'icona del sito e lo scudo dei popup bloccati; **Manda a Kodi** compare solo quando c'è
  un video; pannelli dal basso per scelta del video, preferiti, coda, telecomando e impostazioni.

## 1.3.0

- Interfaccia nuova. All'avvio c'è una **pagina iniziale** con Kodi (acceso, spento o cosa sta riproducendo, con il
  pulsante *Accendi* o *Telecomando*), **Riprendi** con l'ultima pagina guardata e i preferiti come icone.
- **Telecomando** di Kodi nell'app (⋮ → Telecomando): barra di avanzamento, salti di ±10 e ±30 s, vai al minuto,
  play/pausa/stop, episodio precedente e successivo, velocità, audio e sottotitoli, volume, frecce/OK/Indietro/Home/Menu,
  testo dalla tastiera e accensione.
- **Barra di Kodi** in fondo quando Kodi riproduce un video: nome, tempo, play/pausa; toccandola si apre il telecomando.
- Barra in alto a pillola con l'icona del sito; i popup bloccati si contano in uno scudo (toccalo per vedere quali)
  invece di un avviso a ogni blocco. Pulsanti tondi ★ (piena se la pagina è tra i preferiti), ⏻ e ⋮ (il vecchio
  pulsante "Kodi").
- **Manda a Kodi** e **+ Coda** compaiono sopra il sito solo quando c'è un video, invece della striscia grigia.
- Scelta del video, preferiti, coda e telecomando si aprono come pannelli dal basso; gli avvisi brevi compaiono in fondo
  allo schermo.
- Indietro dalla prima pagina di un sito torna alla pagina iniziale.

## 1.2.4

- Elenco dei video più chiaro: ogni voce dice cos'è ("Video completo, tutte le qualità (consigliato)", "Solo la
  qualità 720p", …) e da quale sito arriva; le singole qualità di un video non compaiono più a parte.
- Accanto a ogni video dell'elenco un'immagine presa dal video e la durata, per distinguere l'episodio da un trailer
  (sui video protetti può esserci solo la durata).
- README e tutorial anche in inglese.

## 1.2.3

- Coda di Kodi: se Kodi sta riproducendo un video partito fuori dalla coda, compare in cima con il motivo per cui la
  coda non lo segue, invece di una lista vuota senza spiegazioni.
- Tenendo premuto **+ Coda** si apre la coda; dopo "+ Coda" l'avviso dice dove trovarla (Kodi → Coda di Kodi).

## 1.2.2

- Il pulsante di accensione ha la stessa altezza di ★ e Kodi.
- L'accodamento automatico degli episodi successivi parte spento (si accende da Kodi → Dov'è Kodi?). Anche chi
  l'aveva già acceso lo ritrova spento una volta.
- Tutorial passo passo con animazioni in docs/TUTORIAL.md, collegato dal README.
- Test con un Kodi finto e un sito finto che provano invio, coda e password.

## 1.2.1

- Siti con player vidxgo: le playlist del video le scarica l'app al posto del browser e se ne tiene una
  copia, così all'invio Kodi la riceve dal telefono invece di richiederla al sito, che rispondeva 403.

## 1.2.0

- Manda a Kodi: all'avvio il telefono scarica la playlist e la chiave del video e le passa a Kodi in rete locale. La
  Fire TV ci metteva 10-20 secondi per ognuna e Kodi rinuncia dopo 30 ("Playback failed"). I pezzi del video Kodi li
  prende sempre dal sito: il telefono serve solo nei primi secondi.
- Manda a Kodi: con il video si mandano anche Origin e cookie del sito, come fa il browser (un sito con il
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

- Manda a Kodi: per i video HLS (es. player VixCloud) l'app manda a Kodi una sola qualità, la migliore, invece della
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

- Manda a Kodi: se Kodi non riesce a far partire il video dalla sua playlist ("Playback failed", es. con il
  player VixCloud), l'app lo riapre direttamente come nelle prime build. In quel caso il video parte senza coda dietro.

## 1.0.1

- Pulsante di accensione: trova la Fire TV da solo anche senza l'IP di Kodi salvato (prova l'ultimo IP e poi cerca in
  rete sulla porta 5555), e dopo il risveglio chiede alla TV di passare all'ingresso della Fire TV.

## 1.0.0

Prima versione con un numero al posto di "Build N" (l'ultima era la Build 18).

- Le release si chiamano "Versione X.Y.Z" e hanno l'elenco delle novità; il link per scaricare l'APK resta lo stesso.
- L'avviso di aggiornamento confronta le versioni (es. "Installata: 1.0.0, ultima: 1.1.0").
- Il pulsante di accensione ha un'icona vera: il carattere ⏻ sul telefono non si vedeva.
- Nel README la lista dei dispositivi compatibili.
- Tutto quello che c'era fino alla Build 18: browser con blocco dei popup, invio del video a Kodi, coda nella playlist
  di Kodi, episodi successivi automatici sui siti di anime, preferiti, credenziali di Kodi cifrate, ricerca da Google
  e pulsante ⏻ per accendere la TV.
