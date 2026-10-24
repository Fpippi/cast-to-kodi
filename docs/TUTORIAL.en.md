# 📖 How to use Couch Cast

[🇮🇹 Italiano](TUTORIAL.md) · 🇬🇧 English

A step-by-step guide, without technical terms. If something goes wrong, jump to [Common problems](#-common-problems).

**The idea in one line:** the phone is the browser and the remote, the TV shows the video with **Kodi**. You open the site
on the phone, start the video, press a button and the video plays on the TV. Then you can lock the phone.

> [!IMPORTANT]
> Phone and Fire TV must be connected **to the same Wi-Fi**.

> [!NOTE]
> The app's interface is in Italian. In this guide button names and messages are written as they appear in the app,
> with a translation in brackets. The animations are drawings of the version 2.1 interface, not screen recordings:
> colours and details of the real app may differ a little.

## Contents

1. [What's on the screen](#1-whats-on-the-screen)
2. [First time: set up the Fire TV](#2-first-time-set-up-the-fire-tv)
3. [First time: connect the app to Kodi](#3-first-time-connect-the-app-to-kodi)
4. [Turn the TV on from the phone](#4-turn-the-tv-on-from-the-phone)
5. [Find and open a video](#5-find-and-open-a-video)
6. [Send the video to the TV](#6-send-the-video-to-the-tv)
7. [The episode queue](#7-the-episode-queue)
8. [Favourites](#8-favourites)
9. [Kodi log: when something doesn't play](#9-kodi-log-when-something-doesnt-play)
10. [Update the app](#10-update-the-app)
11. [Common problems](#-common-problems)

---

## 1. What's on the screen

```
┌──────────────────────────────────────────────┐
│ (🌐 animetv.example              🛡3)   ★  ⋮  │  ← top bar
│▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬                            │  ← page loading
│                                              │
│                 site page                    │
│                                              │
│                 (+≡)  ( 📺 Manda a Kodi )     │  ← only when there's a video
│ ╭──────────────────────────────────────────╮ │
│ │ [▶] Episodio 11                   ‖   ▯  │ │  ← Kodi bar, always
│ │     ● Su Kodi · 12:40 di 23:50           │ │
│ ╰━━━━━━━━━━━━━━━━──────────────────────────╯ │
└──────────────────────────────────────────────┘
```

| Button | What it does |
|---|---|
| **Address bar** ("Cerca o scrivi un indirizzo") | Type a site (`wikipedia.org`) or a search (`jojo episode 1`, goes to Google). The site's icon is on the left. Until you tap it, it shows only the site's name; tapping it brings back the full address. |
| **🛡 with a number** | Shows up when the app blocked popups or jumps to other sites on this page. Tap it to see which ones and open one, if you needed it. |
| **★** | Your favourite sites. Filled if the open page is already a favourite. |
| **Kodi bar** (at the bottom) | Always there, as soon as the app knows where Kodi is. It shows what is playing, with the time, play/pause and a thin line at the bottom that moves forward. The dot next to the status is lit if Kodi answers. If Kodi is on but idle it says *Acceso, niente in riproduzione* (On, nothing playing); if it is off or not answering there is the **⏻** button to turn the TV on. The button on the right, or a tap on the bar, opens the remote with the queue. |
| **⋮** | A small menu: *Telecomando* (Remote), *Coda di Kodi* (Kodi queue), *Pagina iniziale* (Home page), *Impostazioni* (Settings: where Kodi is, power on, next episodes, history, Kodi log, guide, version). |
| **Manda a Kodi** (Send to Kodi) | Plays the page's video on the TV right away. It shows up at the bottom right, over the site, only when a video starts on the page. |
| **+ Coda** (+ Queue: the round icon with lines and a +, next to *Manda a Kodi*) | Puts the video at the end of Kodi's queue, without interrupting what you are watching. Long-press it to open the queue. |

On launch the **home page** opens: Kodi at the top (on, off or what it is playing, with an *Accendi* (Turn on) or *Telecomando* (Remote) button), then **Continua a guardare** (Keep watching) with the series you are following, the **Ultima pagina** (Last page) you opened and your favourites as icons (see [Keep watching](#keep-watching)). You get back to it with Back from a site's first page or with **⋮ → Pagina iniziale** (Home page).

Short messages ("Aggiunto alla coda", "Kodi è pronto"…) appear at the bottom of the screen, over the site.

---

## 2. First time: set up the Fire TV

To do **once only**, with the Fire TV remote. The detailed steps to install Kodi are in the
[README](../README.md#-fire-tv); here is what the app needs.

1. **Install Kodi** on the Fire TV (with the *Downloader* app, see README) and open it.
2. In Kodi go to **Settings → Services → Control** and turn on **Allow remote control via HTTP**.
   - Leave the port as it is (usually `8080`).
   - If you set a **username and password**, write them down: the app will ask for them.
3. Still in Kodi, **Settings → Services → General**: turn on **Announce services to other systems**.
   It lets the app find Kodi by itself.
4. Only if you want to turn the TV on from the phone (**⏻** button) and read Kodi's log:
   - Fire TV: **Settings → My Fire TV → Developer options → ADB debugging** = on.
     If *Developer options* is missing, go to **My Fire TV → About** and click the device name 7 times.
   - Fire TV: **Settings → Display & Sounds → HDMI CEC Device Control** = on.
   - On the TV turn on HDMI-CEC (called Anynet+, SimpLink, Bravia Sync… depending on the brand).
     If Alexa already turns the TV on to the right input, it's already fine.

---

## 3. First time: connect the app to Kodi

<p align="center"><img src="img/configura.gif" width="640" alt="Animation: the Dov'è Kodi? window finds Kodi, you tap it and save; the Kodi bar shows up at the bottom"></p>

1. **Open Kodi on the TV** (it must be on to be found).
2. **Open Couch Cast** on the phone. On first launch the **Dov'è Kodi?** (Where is Kodi?) window appears.
3. **Automatico** (automatic) tab: the app says *"Cerco Kodi sul Wi-Fi…"* ("Looking for Kodi on Wi-Fi…") and after a
   few seconds shows what it found. Tap your Kodi and press **Salva** (Save).
4. If it finds nothing, use the **Manuale** (manual) tab and type `IP:port`, for example `192.168.1.50:8080`.
   You can read the IP on the Fire TV in **Settings → My Fire TV → About → Network**.
5. **Login, if set in Kodi**: if you set a username and password in Kodi, type them here. If you skip them, the app asks
   for them by itself (*Accesso a Kodi*, "Kodi login" window) the first time you send a video.

Done: the **Kodi bar** shows up at the bottom, and on the home page the Kodi card shows its name. To change these
choices later: **⋮ → Impostazioni → Kodi**.

> [!TIP]
> - If the Fire TV changes address (it happens after a router restart), the app finds it again by itself when sending.
> - The password stays only on the phone, encrypted, and is tied to the home network: on another network the app asks again.

---

## 4. Turn the TV on from the phone

<p align="center"><img src="img/accendi.gif" width="640" alt="Animation: with the TV off you press the power button in the Kodi bar, the TV turns on and opens Kodi"></p>

With the TV off (in standby) press **⏻** in the Kodi bar at the bottom (or *Accendi* on the home page).

1. The app looks for the Fire TV on the network (*"Cerco la Fire TV in rete…"*), even if Kodi is closed.
2. **Only the first time**: the TV asks whether to allow debugging from this phone.
   Tick **Always allow** and press **OK** with the remote.
3. The TV turns on, after a few seconds switches to the Fire TV input and Kodi opens.
4. When Kodi answers, the phone says **"Kodi è pronto"** ("Kodi is ready").

> [!NOTE]
> If the Fire TV is in *deep sleep* (off for a long time) its Wi-Fi is off and it answers no one:
> in that case turn it on with Alexa or the remote.

---

## 5. Find and open a video

<p align="center"><img src="img/manda-a-kodi.gif" width="640" alt="Animation: Google search, opening the episode, starting the player, Manda a Kodi and the video on the TV"></p>

1. Tap the **top bar** and type the site or a search, then press Enter.
   - `wikipedia.org` → opens the site.
   - `jojo episode 1` → searches Google; from Google results you can open any site.
2. Go to the episode or movie page.
3. **Start the player** (press play on the video). This step is essential: the app only sees the video once it starts.
4. At the bottom right, over the site, **Manda a Kodi** and the round **+ Coda** button show up. Now you can also pause
   the video on the phone.

### From Chrome or another app: Share → Couch Cast

Found the episode page in Chrome, or someone sent it to you on WhatsApp? Tap **Share** and pick **Couch Cast**: the
page opens in the app and you carry on from step 3 (start the player, then Manda a Kodi).

- A message with the link in the middle of the text is fine too: the app takes the first link.
- If you share plain text with no link, the app searches it on Google.
- If the link is already the video (it ends in `.m3u8` or `.mp4`), a panel opens: **Manda a Kodi** ("Send to Kodi")
  starts it on the TV right away, **Apri qui** ("Open here") opens it in the app.

> [!TIP]
> Ads that open windows or take you to other sites are blocked silently.
> If the site seems "not to react" to a tap, it was probably an ad link: tap the real play button again.

---

## 6. Send the video to the TV

Press **Manda a Kodi** (Send to Kodi).

- The button says *"Invio a Kodi…"* ("Sending to Kodi…") until Kodi answers: no need to press again.
- When done, **"Inviato a Kodi"** ("Sent to Kodi") appears and the video starts on the TV after a few seconds.
- If the page has more than one video the button says e.g. **Manda a Kodi · 2** and a panel opens with all the
  videos, each with a **frame taken from the video** and its **length** on top (after a couple of seconds). The first,
  *Video completo* (Full video), is almost always the right one: it is already picked, marked *Consigliato*
  (Recommended), with the **Manda** (Send) and **In coda** (Queue) buttons under it. Tap another video to move the
  buttons there. Videos under a minute are faded (*Probabile pubblicità*, "Probably an ad"). On some protected videos
  the frame does not arrive and only the length is shown: you can still pick it.
- From here on the phone **is no longer needed**: you can lock it or use it for something else. The video reaches the
  TV directly from the site.

### The remote in the app

To control Kodi without the TV remote tap the **Kodi bar** at the bottom (or **⋮ → Telecomando**). It opens full
screen with two tabs, **Telecomando** (Remote) and **Coda** (Queue); close it with ⌄ at the top, with Back or by
dragging down. At the top right there is Kodi's name with a lit dot if it answers. In the Remote tab, from the top:

- **The video playing** with the time it ends (*fino alle 21:52*, "until 21:52"), and the **bar**: drag it to the
  point you want; below are the time watched and the length.
- **⏮ −10 ⏯ +10 ⏭**: previous video in the queue, back 10 seconds, play/pause (the big button), forward 10 seconds,
  next video. Skips are always what they say: the TV remote uses Kodi's steps instead, and with the up and down arrows
  it jumps 10 minutes.
- **⏭ when the queue is over**: if nothing is queued after the video playing, on anime sites with the VixCloud player
  ⏭ sends Kodi the next episode of the same series (the phone prepares it in a couple of seconds, like *Riprendi su
  Kodi*). On other sites the app tells you which episode to open. It does not turn on automatic queueing.
- **Salta sigla** (Skip intro): when the opening starts, tap it and the video jumps 1:25 forward from where it is. If
  a series' opening is longer or shorter, tap **Cambia** (Change) next to it (or long-press it) and pick how much, or
  type the exact time with *Altro tempo…* (Other time, e.g. `1:12` or `72`): it applies to that series (or to every
  video, if the video is not from a series in *Continua a guardare*). It never skips on its own: the opening does not
  always start at the same minute.
- **The round D-pad with OK** and below it **Back**, **Home**, **volume down** and **volume up** (Kodi's volume, not
  the TV's): to move around Kodi's menus.
- In a row that scrolls sideways: **Accendi la TV** (Turn on the TV), **−30 s**, **+30 s**, **Vai al minuto**,
  **Audio**, **Sottotitoli**, **Velocità**, **Muto** (Mute), **Menu**, **Scrivi** and **Ferma** (Stop: asks first, the
  queue stays).
- **Vai al minuto** (Go to minute): type e.g. `12:30` (12 minutes 30 seconds) or `1:02:30`.
- **Velocità** (Speed): normal, 1.25x or 1.5x. With some videos Kodi won't change it, and the app says so.
- **Audio** and **Sottotitoli** (Subtitles): pick the language among the video's own, or turn subtitles off.
- **Scrivi** (Type): first open a text field in Kodi (e.g. search), then type with the phone keyboard.
- **Accendi la TV** (Turn on the TV): like the ⏻ button.

---

## 7. The episode queue

The queue **lives inside Kodi**, not on the phone: it is Kodi's video playlist. When a video ends, Kodi moves on to the
next one by itself. The app never clears it.

### Manda a Kodi and + Coda: the difference

<p align="center"><img src="img/coda.gif" width="640" alt="Animation: + Coda on episode 2, then the Kodi bar opens the Coda tab with episode 1 playing and 2 waiting"></p>

| | What it does |
|---|---|
| **Manda a Kodi** (Send to Kodi) | Puts the video **right after** the one playing and **starts it now**. |
| **+ Coda** (+ Queue) | Puts the video **at the end** of the queue. What you are watching keeps going. |

**Example to watch three episodes in a row:**

1. Open episode 1, start the player, press **Manda a Kodi**. It plays on the TV.
2. Open episode 2, start the player, press **+ Coda** → *"Aggiunto alla coda"* ("Added to the queue").
3. Open episode 3, start the player, press **+ Coda**.
4. Put the phone down: the TV goes on by itself 1 → 2 → 3.

### On anime sites with the VixCloud player it can do it by itself (if you turn it on)

On the anime site it knows (VixCloud player) the app can queue the following episodes by itself, so you don't have to press **+ Coda** for each one.
**It is off by default.** To turn it on: **⋮ → Impostazioni →** the **episodi successivi** item (Settings, next episodes) and pick how many
episodes to queue (at most 3).

From then on, after **Manda a Kodi** (or **+ Coda**) the app queues the following episodes and says
*"In coda anche: episodio …"* ("Also queued: episode …"). If the site changes and the app can't do it, it tells you:
in that case use **+ Coda** by hand.

### When the app doesn't queue (and why)

Sometimes **+ Coda** answers **"Non messo in coda"** ("Not queued"). It's not a fault, the app saves you a queue that
wouldn't play:

- *"Il video in corso su Kodi non è partito dall'app"* ("The video playing on Kodi wasn't started from the app"): Kodi is
  playing something started another way, and at the end it wouldn't move on to the queue. Press **Manda a Kodi** on the
  episode to watch, then queue the others.
- *"Il link di questo episodio scade alle …"* ("This episode's link expires at …"): some sites give links that last a
  few hours. If the episode would come after the expiry, queue it later.

### Look at and tidy the queue: the Coda tab

Open the remote and tap the **Coda** (Queue) tab (the number next to it says how many videos are waiting), or
long-press **+ Coda**. You see the list of videos:

- **▶ In riproduzione · finisce alle 21:52** (Playing · ends at 21:52): the one playing now.
- **Già visto** (Already watched): the finished ones, lighter.
- **Parte alle 22:16** (Starts at 22:16): the waiting ones, with the estimated time (episodes as long as the current one).
- In red, with a clock, **Il link scade prima del suo turno** (The link expires before its turn) or **Link scaduto**
  (Link expired): must be sent again from the episode page.

With the **⋮** next to a video pick **Riproduci ora** (Play now) or **Togli dalla coda** (Remove from queue).
**Svuota la coda** (Clear the queue) removes everything except the one playing, **↻** reloads the queue from Kodi.

---

## 8. Favourites

<p align="center"><img src="img/preferiti.gif" width="640" alt="Animation: ★, add this page, choose the name, then reopen from its icon on the home page"></p>

- **Save a page**: open it, press **★** and then **＋ Aggiungi questa pagina** (Add this page). Choose a name and save.
- **Reopen it**: tap its icon on the **home page**, or **★** and tap the name.
- **Rename or remove**: long-press the icon on the home page, or **★** and **⋮** next to the favourite →
  **Rinomina** (Rename) or **Togli** (Remove).
- The **★** at the top is filled when the open page is already a favourite.

### Keep watching

When you send an episode to Kodi, the app remembers it. On the home page, below Kodi, you find **Continua a guardare**
(Keep watching): a row that scrolls sideways, with a card for each series, the most recent on the left.

- The card says which episode you are at and the minute you stopped at (e.g. *Ep. 12 · fermo a 12:40*, stopped at
  12:40), with a line showing how much you have watched. When Kodi passes 95% of the episode, or there are less than 2
  minutes to the end (the ending theme), the episode counts as watched and the card moves to the next one
  (*Visto il 12 · tocca al 13*, watched 12, 13 is next).
- **Tap it**: a panel slides up with the previous episode, the current one and the next one. **Riprendi su Kodi**
  (Resume on Kodi) sends the episode without opening the page and Kodi resumes from the minute you stopped at
  (only on the anime site with the VixCloud player). **Apri qui** (Open here) opens the episode page: on other sites start the player, press
  *Manda a Kodi* and Kodi still resumes from where you were. From the panel you can also mark the episode as watched.
- A series that reached its last episode stays faded, marked **Finita** (Finished), until you remove it.
- **Remove a series**: the **✕** at the top right of the card. The card turns red and asks *Togli …?* (Remove …?): tap
  **Togli** (Remove) (if you tap nothing, it goes back to normal after 4 seconds). Right after, you can still press
  **Annulla** (Undo) in the notice at the bottom.
- Below is the **Ultima pagina** (Last page) you opened, if it is not already an episode in the row: the ✕ removes it
  with one tap, with *Annulla* (Undo).
- If you open from the site (or from Google) a series you are watching but on another episode, below the bar you see
  **Eri all'episodio 12** (You were at episode 12) with **Vai** (Go).
- Sites change domain often: the app recognises the series from the site's name and the part of the address that stays
  the same, and reopens it on the last domain it saw working.
- **⋮ → Impostazioni → Cronologia** (Settings → History): *Ricorda dove sono arrivato* (Remember where I got to; when
  off the app saves nothing and the home page shows no series and no last page) and *Cancella la cronologia*
  (Clear history).

The app reads the minute from Kodi while it is open; if the phone was locked, when you go back to the home page it asks
Kodi, which usually remembers it. On that site series and number come from the address and the player; on other sites
from the page title ("Episodio 5", "S2E5"): if the title doesn't say them, the page is only kept as the last page.

---

## 9. Kodi log: when something doesn't play

If **Playback failed** appears on the TV or the video doesn't start:

1. Press **⋮ → Impostazioni → Log di Kodi** (Settings, Kodi log). The app reads the last lines of Kodi's log from the Fire TV
   (it needs the *ADB debugging* from [step 2](#2-first-time-set-up-the-fire-tv); the first time the TV asks to confirm,
   as for ⏻).
2. Press **Condividi** (Share) and send it wherever you need (WhatsApp, email, a GitHub issue…), together with the name
   of the site and the episode.

---

## 10. Update the app

On startup, if there is a new version, **Nuova versione disponibile** ("New version available") appears: press
**Scarica** (Download) and install the APK over the old one (no need to uninstall). To check by hand: **⋮ → Impostazioni →
Versione** (Settings, Version). To update with one tap you can use **Obtainium** with the repo address.

---

## 🩺 Common problems

| What you see | What to do |
|---|---|
| **Manda a Kodi** doesn't show up | Press play on the page's video and wait until it really starts. If there's an ad before the video, wait for it to end. |
| **"Kodi non risponde"** ("Kodi doesn't answer") / **"Nessun Kodi trovato"** ("No Kodi found") | Kodi must be **open** on the TV, with *Announce services to other systems* on, and phone and TV on the same Wi-Fi. Otherwise **⋮ → Impostazioni → Kodi → Manuale** and type `IP:port`. |
| **Accesso a Kodi** (Kodi login) window | Type the username and password you set in Kodi (*Services → Control*). If it says it doesn't accept them, check them again in Kodi. |
| **Playback failed** on the TV | Try **Manda a Kodi** once more. If it happens again, [⋮ → Impostazioni → Log di Kodi → Condividi](#9-kodi-log-when-something-doesnt-play). |
| **"Kodi non ha accettato il video"** ("Kodi didn't accept the video") | The site gave a link Kodi can't open. Reload the page, restart the player and try again; if it happens again, send the log. |
| **"Inviato a Kodi, ma senza coda"** ("Sent to Kodi, but without queue") | The video plays, but Kodi doesn't want it in the playlist: for that video the queue doesn't work. |
| **"Non messo in coda"** ("Not queued") | Read the reason in the message: see [When the app doesn't queue](#when-the-app-doesnt-queue-and-why). |
| **"Link scaduto"** ("Link expired") in the Coda tab | Reopen the episode page, start the player and send it again. |
| **⏻**: **"Fire TV non trovata"** ("Fire TV not found") or **"Fire TV non raggiungibile"** ("Fire TV unreachable") | Check *ADB debugging* on the Fire TV and the same Wi-Fi. If the Fire TV has been off for a long time it's in deep sleep: turn it on with Alexa or the remote. |
| **⏻**: **"La TV ha rifiutato il telefono"** ("The TV refused the phone") or **"non ha confermato in tempo"** ("didn't confirm in time") | Press ⏻ again and on the TV tick *Always allow* and press OK. |
| **⏻**: the TV turns on but stays on another input | Check that HDMI-CEC is on both on the Fire TV and on the TV, then switch input with the TV remote. |
| A tap on the site "does nothing" | It was almost certainly a blocked ad. Tap the real button. |
