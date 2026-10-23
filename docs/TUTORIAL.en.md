# 📖 How to use Couch Cast

[🇮🇹 Italiano](TUTORIAL.md) · 🇬🇧 English

A step-by-step guide, without technical terms. If something goes wrong, jump to [Common problems](#-common-problems).

**The idea in one line:** the phone is the browser and the remote, the TV shows the video with **Kodi**. You open the site
on the phone, start the video, press a button and the video plays on the TV. Then you can lock the phone.

> [!IMPORTANT]
> Phone and Fire TV must be connected **to the same Wi-Fi**.

> [!NOTE]
> The app's interface is in Italian. In this guide button names and messages are written as they appear in the app,
> with a translation in brackets. The animations are drawings that show the steps, not screen recordings: colours and
> details of the real app may differ a little.

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
│ (🌐 Cerca o scrivi un indirizzo  🛡3)  ★ ⏻ ⋮  │  ← top bar
│▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬                            │  ← page loading
│                                              │
│                 site page                    │
│                                              │
│                 (+≡)  ( 📺 Manda a Kodi )     │  ← only when there's a video
└──────────────────────────────────────────────┘
```

| Button | What it does |
|---|---|
| **Address bar** ("Cerca o scrivi un indirizzo") | Type a site (`animeunity.so`) or a search (`jojo animeunity`, goes to Google). The site's icon is on the left. |
| **🛡 with a number** | Shows up when the app blocked popups or jumps to other sites on this page. Tap it to see which ones and open one, if you needed it. |
| **★** | Your favourite sites. Filled if the open page is already a favourite. |
| **⏻** | Turns the TV on and opens Kodi. |
| **⋮** | A small menu: *Telecomando* (Remote), *Coda di Kodi* (Kodi queue), *Dov'è Kodi?* (Where is Kodi?), *Log di Kodi* (Kodi log). |
| **Manda a Kodi** (Send to Kodi) | Plays the page's video on the TV right away. It shows up at the bottom right, over the site, only when a video starts on the page. |
| **+ Coda** (+ Queue: the round icon with lines and a +, next to *Manda a Kodi*) | Puts the video at the end of Kodi's queue, without interrupting what you are watching. Long-press it to open the queue. |

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

<p align="center"><img src="img/configura.gif" width="640" alt="Animation: the Dov'è Kodi? window finds Kodi, you tap it and save"></p>

1. **Open Kodi on the TV** (it must be on to be found).
2. **Open Couch Cast** on the phone. On first launch the **Dov'è Kodi?** (Where is Kodi?) window appears.
3. **Automatico** (automatic) tab: the app says *"Cerco Kodi sul Wi-Fi…"* ("Looking for Kodi on Wi-Fi…") and after a
   few seconds shows what it found. Tap your Kodi and press **Salva** (Save).
4. If it finds nothing, use the **Manuale** (manual) tab and type `IP:port`, for example `192.168.1.50:8080`.
   You can read the IP on the Fire TV in **Settings → My Fire TV → About → Network**.
5. **Login, if set in Kodi**: if you set a username and password in Kodi, type them here. If you skip them, the app asks
   for them by itself (*Accesso a Kodi*, "Kodi login" window) the first time you send a video.

Done. To change these choices later: **⋮ → Dov'è Kodi?** button.

> [!TIP]
> - If the Fire TV changes address (it happens after a router restart), the app finds it again by itself when sending.
> - The password stays only on the phone, encrypted, and is tied to the home network: on another network the app asks again.

---

## 4. Turn the TV on from the phone

<p align="center"><img src="img/accendi.gif" width="640" alt="Animation: with the TV off you press the power button, the TV turns on and opens Kodi"></p>

With the TV off (in standby) press **⏻**, at the top next to ★.

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
   - `animeunity.so` → opens the site.
   - `jojo animeunity` → searches Google; from Google results you can open any site.
2. Go to the episode or movie page.
3. **Start the player** (press play on the video). This step is essential: the app only sees the video once it starts.
4. The bottom button turns coloured and says **Manda a Kodi**. Now you can also pause the video on the phone.

> [!TIP]
> Ads that open windows or take you to other sites are blocked silently.
> If the site seems "not to react" to a tap, it was probably an ad link: tap the real play button again.

---

## 6. Send the video to the TV

Press **Manda a Kodi** (Send to Kodi).

- The button says *"Invio a Kodi…"* ("Sending to Kodi…") until Kodi answers: no need to press again.
- When done, **"Inviato a Kodi"** ("Sent to Kodi") appears and the video starts on the TV after a few seconds.
- If the page has more than one video the button says e.g. **Manda a Kodi (2)** and lets you choose. Each entry says
  what it is and which site it comes from: *Video completo, tutte le qualità (consigliato)* ("Full video, all qualities,
  recommended") is almost always the right one and comes first. The others are usually a trailer, an ad that slipped
  past the filter or another player on the page.
- Next to each entry, after a couple of seconds, a **frame taken from the video** and its **length** appear: an episode
  lasts about twenty minutes, a trailer one or two. On some protected videos the frame does not arrive and only the
  length is shown (or *Anteprima non disponibile*, "Preview not available"): you can still pick it.
- From here on the phone **is no longer needed**: you can lock it or use it for something else. The video reaches the
  TV directly from the site.

### The remote in the app: ⋮ → Telecomando

To control Kodi without the TV remote press **⋮ → Telecomando** (Remote). At the top there is the video playing,
which updates by itself:

- **Bar**: drag it to the point you want; next to it are the time watched and the length.
- **−30 s, −10 s, +10 s, +30 s**: always skip by what they say. The TV remote uses Kodi's steps instead, and with the
  up and down arrows it jumps 10 minutes.
- **⏮ ⏯ ⏹ ⏭**: previous video in the queue, play/pause, stop (asks first, the queue stays), next video.
- **Vai al minuto** (Go to minute): type e.g. `12:30` (12 minutes 30 seconds) or `1:02:30`.
- **Velocità** (Speed): normal, 1.25x or 1.5x. With some videos Kodi won't change it, and the app says so.
- **Audio** and **Sottotitoli** (Subtitles): pick the language among the video's own, or turn subtitles off.
- **Arrows and OK**, **Indietro** (Back), **Home**, **Menu**: to move around Kodi's menus.
- **Scrivi** (Type): first open a text field in Kodi (e.g. search), then type with the phone keyboard.
- **Volume** down, mute and up: Kodi's volume, not the TV's.
- **Accendi la TV e Kodi** (Turn on the TV and Kodi): like the ⏻ button.

---

## 7. The episode queue

The queue **lives inside Kodi**, not on the phone: it is Kodi's video playlist. When a video ends, Kodi moves on to the
next one by itself. The app never clears it.

### Manda a Kodi and + Coda: the difference

<p align="center"><img src="img/coda.gif" width="640" alt="Animation: + Coda on episode 2, Coda di Kodi shows episode 1 playing and 2 waiting"></p>

| | What it does |
|---|---|
| **Manda a Kodi** (Send to Kodi) | Puts the video **right after** the one playing and **starts it now**. |
| **+ Coda** (+ Queue) | Puts the video **at the end** of the queue. What you are watching keeps going. |

**Example to watch three episodes in a row:**

1. Open episode 1, start the player, press **Manda a Kodi**. It plays on the TV.
2. Open episode 2, start the player, press **+ Coda** → *"Aggiunto alla coda"* ("Added to the queue").
3. Open episode 3, start the player, press **+ Coda**.
4. Put the phone down: the TV goes on by itself 1 → 2 → 3.

### On AnimeUnity it can do it by itself (if you turn it on)

On AnimeUnity the app can queue the following episodes by itself, so you don't have to press **+ Coda** for each one.
**It is off by default.** To turn it on: **⋮ → Dov'è Kodi?**, in the last box at the bottom
(*"AnimeUnity: episodi successivi…"*, "next episodes") type how many episodes to queue (at most 3, `0` = off) and
press **Salva** (Save).

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

### Look at and tidy the queue: ⋮ → Coda di Kodi

Press **⋮ → Coda di Kodi** (Kodi queue). You see the list of videos:

- **▶ In riproduzione** (Playing): the one playing now.
- **Già visto** (Already watched): the finished ones.
- **Parte tra circa N min** (Starts in about N min): the waiting ones, with the estimated time.
- **Link scaduto** (Link expired): must be sent again from the episode page.

Tap a video for **Riproduci ora** (Play now) or **Togli dalla coda** (Remove from queue). **Svuota** (Clear) removes
everything except the one playing. **Aggiorna** (Refresh) reloads the queue from Kodi.

---

## 8. Favourites

<p align="center"><img src="img/preferiti.gif" width="640" alt="Animation: ★, add this page, choose the name, then reopen from the favourite"></p>

- **Save a page**: open it, press **★** and then **＋ Aggiungi questa pagina** (Add this page). Choose a name and save.
- **Reopen it**: **★** and tap the name.
- **Rename or remove**: **★**, then **⋮** next to the favourite → **Rinomina** (Rename) or **Togli** (Remove).

The app also reopens the last page you had open by itself.

---

## 9. Kodi log: when something doesn't play

If **Playback failed** appears on the TV or the video doesn't start:

1. Press **⋮ → Log di Kodi** (Kodi log). The app reads the last lines of Kodi's log from the Fire TV
   (it needs the *ADB debugging* from [step 2](#2-first-time-set-up-the-fire-tv); the first time the TV asks to confirm,
   as for ⏻).
2. Press **Condividi** (Share) and send it wherever you need (WhatsApp, email, a GitHub issue…), together with the name
   of the site and the episode.

---

## 10. Update the app

On startup, if there is a new version, **Nuova versione disponibile** ("New version available") appears: press
**Scarica** (Download) and install the APK over the old one (no need to uninstall). To update with one tap you can use
**Obtainium** with the repo address.

---

## 🩺 Common problems

| What you see | What to do |
|---|---|
| **Manda a Kodi** doesn't show up | Press play on the page's video and wait until it really starts. If there's an ad before the video, wait for it to end. |
| **"Kodi non risponde"** ("Kodi doesn't answer") / **"Nessun Kodi trovato"** ("No Kodi found") | Kodi must be **open** on the TV, with *Announce services to other systems* on, and phone and TV on the same Wi-Fi. Otherwise **⋮ → Dov'è Kodi? → Manuale** and type `IP:port`. |
| **Accesso a Kodi** (Kodi login) window | Type the username and password you set in Kodi (*Services → Control*). If it says it doesn't accept them, check them again in Kodi. |
| **Playback failed** on the TV | Try **Manda a Kodi** once more. If it happens again, [⋮ → Log di Kodi → Condividi](#9-kodi-log-when-something-doesnt-play). |
| **"Kodi non ha accettato il video"** ("Kodi didn't accept the video") | The site gave a link Kodi can't open. Reload the page, restart the player and try again; if it happens again, send the log. |
| **"Inviato a Kodi, ma senza coda"** ("Sent to Kodi, but without queue") | The video plays, but Kodi doesn't want it in the playlist: for that video the queue doesn't work. |
| **"Non messo in coda"** ("Not queued") | Read the reason in the message: see [When the app doesn't queue](#when-the-app-doesnt-queue-and-why). |
| **"Link scaduto"** ("Link expired") in Coda di Kodi | Reopen the episode page, start the player and send it again. |
| **⏻**: **"Fire TV non trovata"** ("Fire TV not found") or **"Fire TV non raggiungibile"** ("Fire TV unreachable") | Check *ADB debugging* on the Fire TV and the same Wi-Fi. If the Fire TV has been off for a long time it's in deep sleep: turn it on with Alexa or the remote. |
| **⏻**: **"La TV ha rifiutato il telefono"** ("The TV refused the phone") or **"non ha confermato in tempo"** ("didn't confirm in time") | Press ⏻ again and on the TV tick *Always allow* and press OK. |
| **⏻**: the TV turns on but stays on another input | Check that HDMI-CEC is on both on the Fire TV and on the TV, then switch input with the TV remote. |
| A tap on the site "does nothing" | It was almost certainly a blocked ad. Tap the real button. |
