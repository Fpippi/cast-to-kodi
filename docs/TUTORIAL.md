# 📖 Come si usa Couch Cast

🇮🇹 Italiano · [🇬🇧 English](TUTORIAL.en.md)

Guida passo passo, senza termini tecnici. Se qualcosa non va, salta a [Problemi comuni](#-problemi-comuni).

**L'idea in una riga:** il telefono fa da browser e da telecomando, la TV guarda il video con **Kodi**. Tu apri il sito
sul telefono, avvii il video, premi un pulsante e il video parte sulla TV. Poi puoi bloccare il telefono.

> [!IMPORTANT]
> Telefono e Fire TV devono essere collegati **allo stesso Wi-Fi**.

_Le animazioni di questa guida sono disegni che mostrano i passaggi, non registrazioni dello schermo: colori e dettagli dell'app vera possono essere un po' diversi._

## Indice

1. [Cosa c'è sullo schermo](#1-cosa-cè-sullo-schermo)
2. [Prima volta: preparare la Fire TV](#2-prima-volta-preparare-la-fire-tv)
3. [Prima volta: collegare l'app a Kodi](#3-prima-volta-collegare-lapp-a-kodi)
4. [Accendere la TV dal telefono](#4-accendere-la-tv-dal-telefono)
5. [Trovare e aprire un video](#5-trovare-e-aprire-un-video)
6. [Mandare il video alla TV](#6-mandare-il-video-alla-tv)
7. [La coda degli episodi](#7-la-coda-degli-episodi)
8. [Preferiti](#8-preferiti)
9. [Log di Kodi: quando qualcosa non parte](#9-log-di-kodi-quando-qualcosa-non-parte)
10. [Aggiornare l'app](#10-aggiornare-lapp)
11. [Problemi comuni](#-problemi-comuni)

---

## 1. Cosa c'è sullo schermo

```
┌──────────────────────────────────────────────┐
│ [Cerca o scrivi un indirizzo]  [★] [⏻] [Kodi] │  ← barra in alto
│▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬                            │  ← caricamento della pagina
│                                              │
│              pagina del sito                 │
│                                              │
│ [      Manda a Kodi       ]  [ + Coda ]      │  ← barra in basso
└──────────────────────────────────────────────┘
```

| Pulsante | A cosa serve |
|---|---|
| **Barra degli indirizzi** | Scrivi un sito (`animeunity.so`) o una ricerca (`jojo animeunity`, va su Google). |
| **★** | I tuoi siti preferiti. |
| **⏻** | Accende la TV e apre Kodi. |
| **Kodi** | Un piccolo menu: *Coda di Kodi*, *Dov'è Kodi?*, *Log di Kodi*. |
| **Manda a Kodi** | Fa partire subito sulla TV il video della pagina. È grigio con scritto *"Nessun video: avvia il player"* finché sulla pagina non parte un video; quando c'è diventa colorato. |
| **+ Coda** | Mette il video in fondo alla coda di Kodi, senza interrompere quello che stai guardando. |

---

## 2. Prima volta: preparare la Fire TV

Da fare **una volta sola**, con il telecomando della Fire TV. I passaggi dettagliati per installare Kodi sono nel
[README](../README.it.md#-fire-tv); qui c'è quello che serve all'app.

1. **Installa Kodi** sulla Fire TV (con l'app *Downloader*, vedi README) e aprilo.
2. In Kodi vai su **Impostazioni → Servizi → Controllo** e attiva **Consenti il controllo remoto via HTTP**.
   - Lascia la porta com'è (di solito `8080`).
   - Se imposti **utente e password**, segnateli: l'app te li chiederà.
3. Sempre in Kodi, **Impostazioni → Servizi → Generale**: attiva **Annuncia servizi ad altri sistemi**.
   Serve perché l'app trovi Kodi da sola.
4. Solo se vuoi accendere la TV dal telefono (pulsante **⏻**) e leggere il log di Kodi:
   - Fire TV: **Impostazioni → La mia Fire TV → Opzioni sviluppatore → Debug ADB** = attivo.
     Se *Opzioni sviluppatore* non c'è, vai in **La mia Fire TV → Info** e premi 7 volte sul nome del dispositivo.
   - Fire TV: **Impostazioni → Display e audio → Controllo dispositivi HDMI CEC** = attivo.
   - Sulla TV attiva l'HDMI-CEC (si chiama Anynet+, SimpLink, Bravia Sync… a seconda della marca).
     Se con Alexa la TV si accende già sull'ingresso giusto, è già a posto.

---

## 3. Prima volta: collegare l'app a Kodi

<p align="center"><img src="img/configura.gif" width="640" alt="Animazione: la finestra Dov'è Kodi? trova Kodi, si tocca e si salva"></p>

1. **Apri Kodi sulla TV** (deve essere acceso per farsi trovare).
2. **Apri Couch Cast** sul telefono. Al primo avvio compare la finestra **Dov'è Kodi?**.
3. Scheda **Automatico**: l'app scrive *"Cerco Kodi sul Wi-Fi…"* e dopo qualche secondo mostra quello che ha trovato.
   Tocca il tuo Kodi e premi **Salva**.
4. Se non trova niente, usa la scheda **Manuale** e scrivi `IP:porta`, per esempio `192.168.1.50:8080`.
   L'IP si legge sulla Fire TV in **Impostazioni → La mia Fire TV → Info → Rete**.
5. **Accesso, se impostato in Kodi**: se in Kodi hai messo utente e password scrivili qui. Se li salti, l'app li chiede
   da sola (finestra *Accesso a Kodi*) la prima volta che mandi un video.

Fatto. Per cambiare queste scelte in futuro: pulsante **Kodi → Dov'è Kodi?**.

> [!TIP]
> - Se la Fire TV cambia indirizzo (succede dopo un riavvio del router), all'invio l'app la ritrova da sola.
> - La password resta solo sul telefono, cifrata, ed è legata alla rete di casa: su un'altra rete l'app la richiede.

---

## 4. Accendere la TV dal telefono

<p align="center"><img src="img/accendi.gif" width="640" alt="Animazione: con la TV spenta si preme il pulsante di accensione, la TV si accende e apre Kodi"></p>

Con la TV spenta (in standby) premi **⏻**, in alto accanto a ★.

1. L'app cerca la Fire TV in rete (*"Cerco la Fire TV in rete…"*), anche se Kodi è chiuso.
2. **Solo la prima volta**: sulla TV compare la domanda se consentire il debug da questo telefono.
   Spunta **Consenti sempre** e premi **OK** con il telecomando.
3. La TV si accende, dopo qualche secondo passa sull'ingresso della Fire TV e si apre Kodi.
4. Quando Kodi risponde, il telefono scrive **"Kodi è pronto"**.

> [!NOTE]
> Se la Fire TV è in *sonno profondo* (spenta da molto tempo) il suo Wi-Fi è spento e non risponde a nessuno:
> in quel caso accendila con Alexa o con il telecomando.

---

## 5. Trovare e aprire un video

<p align="center"><img src="img/manda-a-kodi.gif" width="640" alt="Animazione: ricerca su Google, apertura dell'episodio, avvio del player, Manda a Kodi e video sulla TV"></p>

1. Tocca la **barra in alto** e scrivi il sito o una ricerca, poi premi Invio.
   - `animeunity.so` → apre il sito.
   - `jojo animeunity` → cerca su Google; dai risultati di Google puoi aprire qualunque sito.
2. Vai alla pagina dell'episodio o del film.
3. **Avvia il player** (premi play sul video). Questo passaggio è indispensabile: l'app vede il video solo quando parte.
4. Il pulsante in basso diventa colorato e dice **Manda a Kodi**. Ora puoi anche mettere in pausa il video sul telefono.

> [!TIP]
> La pubblicità che apre finestre o ti porta su altri siti viene bloccata da sola, senza avvisi.
> Se il sito ti sembra "non reagire" a un tocco, probabilmente era un link pubblicitario: tocca di nuovo il play vero.

---

## 6. Mandare il video alla TV

Premi **Manda a Kodi**.

- Il pulsante scrive *"Invio a Kodi…"* finché Kodi non risponde: non serve premere di nuovo.
- Quando ha finito compare **"Inviato a Kodi"** e il video parte sulla TV dopo qualche secondo.
- Se la pagina ha più video il pulsante dice per esempio **Manda a Kodi (2)** e ti fa scegliere: il primo dell'elenco è
  quasi sempre quello giusto.
- Da qui il telefono **non serve più**: puoi bloccarlo o usarlo per altro. Il video arriva alla TV direttamente dal sito.

Per mettere in pausa, andare avanti o regolare il volume usa il telecomando della Fire TV (o un'app telecomando per Kodi).

---

## 7. La coda degli episodi

La coda **vive dentro Kodi**, non nel telefono: è la playlist video di Kodi. Quando un video finisce, Kodi passa da solo
al successivo. L'app non la cancella mai.

### Manda a Kodi e + Coda: la differenza

<p align="center"><img src="img/coda.gif" width="640" alt="Animazione: + Coda sull'episodio 2, Coda di Kodi mostra l'episodio 1 in riproduzione e il 2 in attesa"></p>

| | Cosa fa |
|---|---|
| **Manda a Kodi** | Mette il video **subito dopo** quello in corso e lo **fa partire ora**. |
| **+ Coda** | Mette il video **in fondo** alla coda. Quello che stai guardando continua. |

**Esempio per vedere tre episodi di fila:**

1. Apri l'episodio 1, avvia il player, premi **Manda a Kodi**. Parte sulla TV.
2. Apri l'episodio 2, avvia il player, premi **+ Coda** → *"Aggiunto alla coda"*.
3. Apri l'episodio 3, avvia il player, premi **+ Coda**.
4. Metti giù il telefono: la TV va avanti da sola 1 → 2 → 3.

### Su AnimeUnity può fare da sola (se la accendi)

Su AnimeUnity l'app può mettere in coda da sola gli episodi successivi, così non devi premere **+ Coda** per ognuno.
**Di default è spenta.** Per accenderla: **Kodi → Dov'è Kodi?**, nell'ultima casella in fondo
(*"AnimeUnity: episodi successivi…"*) scrivi quanti episodi accodare (massimo 3, `0` = spenta) e premi **Salva**.

Da quel momento, dopo **Manda a Kodi** (o **+ Coda**) l'app accoda gli episodi dopo e scrive
*"In coda anche: episodio …"*. Se il sito cambia e l'app non ci riesce, te lo dice: in quel caso usa **+ Coda** a mano.

### Quando l'app non mette in coda (e perché)

A volte **+ Coda** risponde con **"Non messo in coda"**. Non è un guasto, l'app ti evita una coda che non partirebbe:

- *"Il video in corso su Kodi non è partito dall'app"*: Kodi sta riproducendo qualcosa avviato in un altro modo, e alla
  fine non passerebbe alla coda. Premi **Manda a Kodi** sull'episodio da vedere, poi accoda gli altri.
- *"Il link di questo episodio scade alle …"*: alcuni siti danno link che valgono poche ore. Se l'episodio arriverebbe
  dopo la scadenza, mettilo in coda più tardi.

### Guardare e sistemare la coda: Kodi → Coda di Kodi

Premi **Kodi → Coda di Kodi**. Vedi l'elenco dei video:

- **▶ In riproduzione**: quello che va ora.
- **Già visto**: quelli finiti.
- **Parte tra circa N min**: quelli in attesa, con l'orario stimato.
- **Link scaduto**: va rimandato dalla pagina dell'episodio.

Tocca un video per **Riproduci ora** o **Togli dalla coda**. **Svuota** toglie tutto tranne quello in corso.
**Aggiorna** rilegge la coda da Kodi.

---

## 8. Preferiti

<p align="center"><img src="img/preferiti.gif" width="640" alt="Animazione: ★, Aggiungi questa pagina, scelta del nome, poi riapertura dal preferito"></p>

- **Salvare una pagina**: aprila, premi **★** e poi **＋ Aggiungi questa pagina**. Scegli un nome e salva.
- **Riaprirla**: **★** e tocca il nome.
- **Rinominare o togliere**: **★**, poi **⋮** accanto al preferito → **Rinomina** o **Togli**.

L'app riapre anche da sola l'ultima pagina che avevi aperto.

---

## 9. Log di Kodi: quando qualcosa non parte

Se sulla TV compare **Playback failed** o il video non parte:

1. Premi **Kodi → Log di Kodi**. L'app legge dalla Fire TV le ultime righe del registro di Kodi
   (serve il *Debug ADB* del [passo 2](#2-prima-volta-preparare-la-fire-tv); la prima volta la TV chiede di confermare,
   come per ⏻).
2. Premi **Condividi** e mandalo dove ti serve (WhatsApp, email, una issue su GitHub…), insieme al nome del sito e
   dell'episodio.

---

## 10. Aggiornare l'app

All'avvio, se c'è una versione nuova, compare **Nuova versione disponibile**: premi **Scarica** e installa l'APK sopra
quello vecchio (non serve disinstallare). Per aggiornare con un tocco si può usare **Obtainium** con l'indirizzo del repo.

---

## 🩺 Problemi comuni

| Cosa vedi | Cosa fare |
|---|---|
| Il pulsante resta **"Nessun video: avvia il player"** | Premi play sul video della pagina e aspetta che parta davvero. Se c'è una pubblicità prima del video, aspetta che finisca. |
| **"Kodi non risponde"** / **"Nessun Kodi trovato"** | Kodi deve essere **aperto** sulla TV, con *Annuncia servizi ad altri sistemi* attivo, e telefono e TV sullo stesso Wi-Fi. Altrimenti **Kodi → Dov'è Kodi? → Manuale** e scrivi `IP:porta`. |
| Finestra **Accesso a Kodi** | Scrivi utente e password che hai impostato in Kodi (*Servizi → Controllo*). Se dice che non li accetta, ricontrollali in Kodi. |
| Sulla TV **Playback failed** | Riprova una volta **Manda a Kodi**. Se ricapita, [Log di Kodi → Condividi](#9-log-di-kodi-quando-qualcosa-non-parte). |
| **"Kodi non ha accettato il video"** | Il sito ha dato un link che Kodi non sa aprire. Ricarica la pagina, riavvia il player e riprova; se ricapita, manda il log. |
| **"Inviato a Kodi, ma senza coda"** | Il video parte, ma Kodi non lo vuole nella playlist: per quel video la coda non funziona. |
| **"Non messo in coda"** | Leggi il motivo nel messaggio: vedi [Quando l'app non mette in coda](#quando-lapp-non-mette-in-coda-e-perché). |
| **"Link scaduto"** nella Coda di Kodi | Riapri la pagina dell'episodio, avvia il player e rimandalo. |
| **⏻**: **"Fire TV non trovata"** o **"Fire TV non raggiungibile"** | Controlla *Debug ADB* sulla Fire TV e lo stesso Wi-Fi. Se la Fire TV è spenta da tanto è in sonno profondo: accendila con Alexa o con il telecomando. |
| **⏻**: **"La TV ha rifiutato il telefono"** o **"non ha confermato in tempo"** | Premi di nuovo ⏻ e sulla TV spunta *Consenti sempre* e premi OK. |
| **⏻**: la TV si accende ma resta su un altro ingresso | Controlla che l'HDMI-CEC sia attivo sia sulla Fire TV sia sulla TV, poi cambia ingresso con il telecomando della TV. |
| Un tocco sul sito "non fa niente" | Era quasi sicuramente pubblicità bloccata. Tocca il pulsante vero. |
