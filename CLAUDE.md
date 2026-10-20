@README.md

## Stato e contesto per riprendere il lavoro

- Progetto nato il 2026-10-20. Kodi è installato e configurato sulla Fire TV dell'utente; l'app si installa dal telefono scaricando l'APK dalla release `latest`.
- 2026-10-20: provato dall'utente su AnimeUnity (VixCloud), funziona: Kodi riproduce i flussi con gli header `Referer` e `User-Agent` passati come `url|Header=valore`. Altri siti non ancora provati.
- Si compila solo su GitHub Actions (`.github/workflows/apk.yml`): l'ambiente di Claude Code non scarica l'SDK Android. Gli errori di build si leggono dai log della run. Chi ha Android Studio può comunque aprire il progetto e compilarlo in locale.

## Decisioni prese e perché
- **Kodi come ricevitore** invece di un'app nostra sulla Fire TV: è gratuito, senza pubblicità, ha già un'API JSON-RPC (`Player.Open`) e riproduce HLS. Così c'è da scrivere solo l'app sul telefono.
- Scartati **Web Video Caster** (pubblicità, il Premium è a pagamento: l'utente non vuole pagare) e **AirScreen** (duplica lo schermo: qualità più bassa e telefono sempre acceso).
- Il Pixel trasmette solo con Google Cast e la Fire TV non lo supporta in modo nativo: per questo serve tutto questo giro.
- Chiave di firma fissa nei secrets di GitHub, mai nel repo (l'utente la tiene in `D:\Progetti\casttokodi-firma`): così gli aggiornamenti si installano senza disinstallare. L'app avvisa delle build nuove leggendo `releases/latest` con un token GitHub inserito dall'utente.
- I commit portano solo l'utente come autore: niente `Co-Authored-By` né altre righe di attribuzione.
- App per uso personale, minSdk 33, nessuna dipendenza oltre alla libreria standard di Kotlin, UI costruita in codice in un'unica Activity.

## Trappole note
- Il rilevamento del video guarda l'estensione del path, più `/playlist/<id>` di VixCloud (AnimeUnity), che non ha `.m3u8`. Se un sito serve la playlist senza `.m3u8`, va riconosciuta dal Content-Type.
- Il blocco dei redirect lascia passare solo il dominio corrente ("ultime due etichette" dell'host). I redirect HTTP sono ammessi apposta, perché i siti cambiano spesso dominio.
- `targetSdk 35` forza l'edge-to-edge: il padding degli insets nella root serve.
