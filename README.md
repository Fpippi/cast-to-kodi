<p align="center">
  <img src="docs/icon.svg" width="112" alt="Couch Cast icon: a phone in front of a TV with a play button">
</p>

<h1 align="center">Couch Cast</h1>

<p align="center"><a href="README.it.md">🇮🇹 Italiano</a> · 🇬🇧 English</p>

<p align="center">
  From your phone to the Fire TV, free and without ads.<br>
  Open the site on your Pixel, the video plays on <b>Kodi</b>.
</p>

<p align="center">
  <a href="https://github.com/Fpippi/cast-to-kodi/releases/latest"><img src="https://img.shields.io/github/v/release/Fpippi/cast-to-kodi?label=latest%20build&color=0D5563" alt="Latest build"></a>
  <a href="https://github.com/Fpippi/cast-to-kodi/actions/workflows/apk.yml"><img src="https://github.com/Fpippi/cast-to-kodi/actions/workflows/apk.yml/badge.svg?branch=main" alt="APK"></a>
  <a href="https://github.com/Fpippi/cast-to-kodi/actions/workflows/test.yml"><img src="https://github.com/Fpippi/cast-to-kodi/actions/workflows/test.yml/badge.svg" alt="Tests"></a>
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white" alt="Android 13+">
  <img src="https://img.shields.io/badge/Kotlin-no%20dependencies-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
</p>

<p align="center">
  <a href="https://github.com/Fpippi/cast-to-kodi/releases/latest/download/CastToKodi.apk"><b>📥 Download the APK</b></a> · <a href="docs/TUTORIAL.en.md"><b>📖 How to use it</b></a>
</p>

---

A personal Android app (Pixel 9 Pro XL) for watching videos from streaming sites on a Fire TV. On the phone it is called **Couch Cast**; the repo, releases and APK (`CastToKodi.apk`) keep the old name, so updates and Obtainium keep working.

> [!NOTE]
> The app's interface is in Italian. Below, button names are given as they appear in the app, with a translation in brackets.

## ✨ How it works

```
📱 Pixel (browser + remote)  ──video link──▶  📺 Kodi on the Fire TV  ──▶  🌐 streaming site
```

- 🔎 The phone is the browser: open the site in the app and start the player.
- 🎯 The app picks up the video stream (`.m3u8` / `.mp4`) and sends it to **Kodi**, which plays it directly.
- 🔋 The video does not go through the phone: full quality, and the phone can be locked. Only at the start does the phone hand Kodi the playlist and the key, which the Fire TV alone downloads too slowly.
- 🚫 Popups, `window.open` and jumps to other domains are blocked: they are almost all of the ads on those sites. The 🛡 shield in the top bar counts what was blocked and, when tapped, opens the one you needed.
- 📋 Episode queue in Kodi's playlist, and on AnimeUnity, if you want, the next episodes are queued automatically.
- 🎮 Kodi remote inside the app: the Kodi bar at the bottom is always there and opens it full screen, with the queue next to it.

<p align="center"><img src="docs/img/manda-a-kodi.gif" width="640" alt="Animation: Google search, opening the episode, starting the player, Manda a Kodi and the video on the TV"></p>

<p align="center"><sub>A drawing of the interface, not a recording. The other animations are in the <a href="docs/TUTORIAL.en.md">guide</a>.</sub></p>

## 📺 Compatible devices

The app talks to **Kodi**, not to the TV: it works on any device that runs Kodi with remote control over HTTP. The **⏻** button instead uses network ADB (port 5555) and HDMI-CEC, so it only works on Fire OS and Android TV.

| Device | Kodi | Send to Kodi | ⏻ Power on |
|---|---|---|---|
| Fire TV Stick / Cube with **Fire OS**, TVs with built-in Fire TV | APK via Downloader | ✅ | ✅ Network ADB debugging built in |
| Fire TV with **Vega OS** (Stick 4K Select, Stick HD 2026) | ❌ cannot be installed | ❌ | ❌ |
| **Android TV** (Sony Bravia, Philips, TCL, Hisense Android, Sharp…) | Play Store | ✅ | ⚠️ depends on the model: needs ADB debugging on port 5555 |
| **Nvidia Shield TV** | Play Store | ✅ | ✅ *Developer options → Network debugging* |
| Generic Android boxes (Xiaomi Mi Box, Homatics, Onn…) | Play Store or APK | ✅ | ⚠️ like Android TV |
| **Google TV** (Chromecast with Google TV, Google TV Streamer, Google TV sets) | Play Store | ✅ | ⚠️ usually not: *Wireless debugging* uses code pairing and a changing port, not 5555 |
| Windows / Mac / Linux PC connected to the TV | kodi.tv | ✅ | ❌ |
| Raspberry Pi, LibreELEC, CoreELEC, OSMC | kodi.tv / dedicated image | ✅ | ❌ |
| Xbox One / Series | Microsoft Store | ✅ | ❌ |
| **LG webOS** | ⚠️ community port only (Homebrew, developer mode), webOS 4+ | ⚠️ in theory yes, not tested | ❌ |
| **Apple TV** (HD and 4K) | ⚠️ sideload only, with Xcode from a Mac, must be re-signed | ⚠️ in theory yes, not tested | ❌ |
| **Samsung Tizen**, **Roku**, Chromecast without Google TV | ❌ Kodi does not exist | ❌ | ❌ |

- ✅ = works, ⚠️ = works with limits or not verified, ❌ = no. Only actually tested on a Fire TV with Fire OS.
- On Samsung, LG or Roku TVs you can still plug a device from the list into HDMI (e.g. a Fire TV Stick with Fire OS or a Chromecast with Google TV).
- **⏻ on Android TV / Google TV**: the app connects with "classic" ADB on port 5555. If the device only has Android 11+ *Wireless debugging*, port 5555 can be opened once from a PC (`adb tcpip 5555`), but it closes again on every reboot. In deep sleep many devices turn off the network: then the button has nothing to wake up.

## 🛠️ Setup (once)

### 📺 Fire TV

0. You need a Fire TV with **Fire OS** (e.g. Fire TV Stick 4K Max and earlier): check in *Settings → My Fire TV → About*. **Vega OS** models (Fire TV Stick 4K Select, Fire TV Stick HD 2026) do not allow installing Kodi.
1. Kodi is not in the Amazon Appstore: install the **Downloader** app from the Appstore, allow it to install "apps from unknown sources" (in *My Fire TV → Developer options*; if you don't see them, click the device name 7 times in *About*) and download the 32-bit ARM Android APK from kodi.tv.
2. In Kodi: *Settings → Services → Control*, turn on **Allow remote control via HTTP**. Note the port (usually 8080), username and password.
3. In Kodi: *Settings → Services → General*, turn on **Announce services to other systems**: this way the app finds Kodi by itself. If that doesn't work, note the Fire TV's IP (*Settings → My Fire TV → About → Network*) and type it in by hand.
4. To turn the TV on from the phone (power button in the Kodi bar at the bottom): turn on *My Fire TV → Developer options → **ADB debugging***, *Settings → Display & Sounds → **HDMI CEC Device Control*** and HDMI-CEC on the TV (Anynet+, SimpLink, Bravia Sync… depending on the brand). If Alexa already turns the TV on to the right input, CEC is fine.

### 📱 App on the phone, without a PC

1. On every push to `main`, GitHub Actions builds the APK and publishes it as the release "Versione X.Y.Z" (tag `vX.Y.Z`), with the list of changes taken from [CHANGELOG.md](CHANGELOG.md) (in Italian). From the Pixel open [releases/latest](https://github.com/Fpippi/cast-to-kodi/releases/latest) and download `CastToKodi.apk`.
2. Builds are signed with a fixed key (repo secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`; the original is not in the repo), so each APK installs over the previous one. If the key is lost a new one is needed, and the app must be uninstalled once.
3. 🔔 On startup the app tells you if there is a new build. To update with one tap you can use **Obtainium** with the repo address.

<details>
<summary>💻 With a PC and Android Studio (alternative)</summary>

**PC**
1. Install Android Studio (developer.android.com/studio). On first launch it downloads the SDK and JDK by itself.
2. *File → Open* on the project folder and wait for the Gradle sync.

**Pixel**
1. *Settings → About phone*: tap "Build number" 7 times to unlock Developer options.
2. *Settings → System → Developer options*: turn on **USB debugging**.
3. Connect the phone via USB, pick it in Android Studio and press ▶️ Run.

</details>

## ▶️ Usage

> [!TIP]
> First time? There is a step-by-step guide, with examples and fixes for common problems: **[📖 How to use Couch Cast](docs/TUTORIAL.en.md)**.

1. 📡 On first launch **Dov'è Kodi?** ("Where is Kodi?") opens: in *Automatico* (automatic) the app looks for Kodi on the network and you pick it; in *Manuale* (manual) you type `IP:port` (e.g. `192.168.1.50:8080`). Below, username and password if set in Kodi (if missing, the app asks for them on the first send). You can change it later with **⋮ → Impostazioni** ("Settings") **→ Kodi**. If the Fire TV changes IP, the app finds it again by itself when sending. Credentials stay on the phone, with the password encrypted, tied to that Kodi and to the home network: on another network they are asked again.
2. 🔌 With the TV off press the **power button** in the Kodi bar at the bottom (or *Accendi* on the home page): the app finds the Fire TV on the network by itself (even without Kodi open), wakes it up, the TV turns on and after a few seconds switches to its input, then Kodi opens; when it answers the app says "Kodi è pronto" ("Kodi is ready"). The first time the TV asks to allow debugging from this phone: tick *Always allow* and press OK. If the Fire TV is in deep sleep it does not answer on the network: then you need Alexa or the remote.
3. 🏠 On launch the **home page** opens: Kodi's state with a button to turn it on or open the remote, **Riprendi** ("Resume") with the last page you watched, and your favourites as icons (long-press to rename or remove). You get back to it with Back from a site's first page or with **⋮ → Pagina iniziale** ("Home page"). In the top bar type an address or a search (e.g. `jojo animeunity`, goes to Google). With **★** you save the page to favourites (with a name of your choice) and reopen them; with **⋮** next to a favourite you rename or remove it.
4. 🎬 Go to the episode and **start the player** (the stream is only detected once it starts): at the bottom right, over the site, **Manda a Kodi** ("Send to Kodi") and the round **+ Coda** ("+ Queue") button show up.
5. 📤 Press **Manda a Kodi**. Ads are already filtered out; if more than one video is left a panel slides up from the bottom with a big frame taken from each one and its length on top, so an episode stands out from a trailer (on protected videos the frame may be missing). The first, "Video completo" ("Full video"), is almost always the right one and is already selected, with *Manda* (Send) and *In coda* (Queue) under it; videos shorter than a minute are faded and the single qualities of the same video are not listed separately.
6. 📋 **Queue**: everything goes through Kodi's video playlist, which is never cleared. *Manda a Kodi* inserts the video right after the one playing and starts it; **+ Coda** ("+ Queue") puts it at the end. If the episode could not play (the current video was not started from the app, or the link expires before its turn) the app does not queue it and explains why. You see it in the remote's **Coda** ("Queue") tab (or by long-pressing **+ Coda**): the video playing (▶) and when it ends, the ones already watched, the time each one starts and, in red, links that expire before their turn; the ⋮ next to a video plays it or removes it, **Svuota la coda** ("Clear the queue") removes everything except the one playing. If Kodi is playing a video started outside the queue, it shows at the top with the reason the queue won't follow it.
7. 🎮 **Remote**: the Kodi bar is always at the bottom (what is playing with the time, play/pause and a line that moves forward; a dot that is lit if Kodi answers; "niente in riproduzione" (nothing playing) or, if Kodi is off, the button to turn it on); tap it, or tap the remote button on its right, and the remote opens full screen, with the **Telecomando** (Remote) and **Coda** (Queue) tabs. Close it with ⌄, Back or by dragging down. At the top the video with a bar to jump to the exact point and the time it ends, then previous, −10 s, a big play/pause, +10 s and next, the round D-pad with OK, Back, Home and volume; below, in a row that scrolls: power on, ±30 s, go to a minute, audio, subtitles, speed, mute, menu, text from the phone keyboard and stop. It also opens from **⋮ → Telecomando**.
8. 🪵 If Kodi says *Playback failed*: **⋮ → Impostazioni → Log di Kodi** ("Kodi log"). The app reads the last lines of the log from the Fire TV via ADB (like the power button, no add-ons) and with **Condividi** ("Share") you send them wherever you need.

> [!IMPORTANT]
> Phone and Fire TV must be on the same Wi-Fi network.

## 🧩 Site add-ons

Extra features that only apply to some sites or players. The rest of the app works on any site that serves `.m3u8` or `.mp4`.

### ⏭️ AnimeUnity

- If you turn it on, after sending the app queues the following episodes by itself (up to 3, set in **⋮ → Impostazioni**; 0 = off, which is the default). Only on `animeunity.*` episode pages.
- It reads the episode list from the page and the link of each one from the VixCloud player: if the site changes its structure the app warns you and you're left with manual **+ Coda**.

### 🎞️ VixCloud player (AnimeUnity, StreamingCommunity and other sites that use it)

- Recognises the playlist even without `.m3u8` (`/playlist/<id>`) and drops its pieces (audio, video, single qualities), so only the main playlist remains.
- The link has a token that expires (`expires=`): **+ Coda** and automatic queueing don't queue an episode that would start after it expires.

### 🔍 Google

- From Google results you can open any site: blocking jumps to other domains is off there.

## 🧪 Tests

The app's rules that don't depend on Android (address bar, jump blocking, recognising videos and ads) live in `Rules.kt`, the per-site ones (the add-ons above) in `sites/`, and they are tested in `app/src/test`. Sending, queue and the phone relay (`Kodi.kt`, `Streams.kt`) are tested against a **fake Kodi** (`FakeKodi`, answers JSON-RPC and keeps its own playlist) and a **fake streaming site** (`FakeSite`: playlist with two qualities, AES key, single-use link), with no Fire TV; the remote in `RemoteTest`, the phone relay in `RelayTest` and the power button against a fake adbd (`AdbTest`). On every PR to `develop` the **Test** workflow runs `./gradlew testDebugUnitTest`; to `main` also `assembleDebug`, and the heavier checks will go there; with Android Studio you run them with the ▶️ next to the test class.

## 🌿 Branches

- **`develop`** is where work happens: every change starts here and comes back with a PR.
- **`main`** is for releases only: a PR from `develop` to `main` publishes a new version (the APK workflow only runs on pushes to `main`).
- Before that PR, a `## X.Y.Z` section with the changes is added at the top of [CHANGELOG.md](CHANGELOG.md): the first number for big changes, the second for new features, the third for fixes. If it's missing, the PR's **test** check says so.
- The README exists in two languages: this one and [README.it.md](README.it.md). A change to one goes into the other too.

<details>
<summary>⚙️ GitHub settings (once)</summary>

1. *Settings → General → Default branch*: choose `develop`, so new PRs target it by default.
2. *Settings → Rules → Rulesets → New branch ruleset*, targeting `develop` and `main`: turn on **Restrict deletions**, **Require a pull request before merging** and **Require status checks to pass** with the **test** check. This way a PR with red tests can't be merged and nobody pushes directly to the two branches.

</details>
