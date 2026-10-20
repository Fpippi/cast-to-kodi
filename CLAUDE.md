@README.md

## Stato e contesto per riprendere il lavoro

- Progetto nato il 2026-10-20. Codice scritto **senza poterlo compilare**: sul PC di lavoro mancavano Android Studio e SDK. Il primo passo a casa è aprirlo in Android Studio, far girare il sync Gradle e sistemare gli eventuali errori di build. Android Studio potrebbe proporre di aggiornare AGP (8.7.3), Kotlin (2.0.21) o Gradle (8.10.2): si può accettare.
- Mai provato su un sito reale. Non è ancora verificato che Kodi riproduca i flussi con gli header `Referer` e `User-Agent` passati come `url|Header=valore`.

## Decisioni prese e perché
- **Kodi come ricevitore** invece di un'app nostra sulla Fire TV: è gratuito, senza pubblicità, ha già un'API JSON-RPC (`Player.Open`) e riproduce HLS. Così c'è da scrivere solo l'app sul telefono.
- Scartati **Web Video Caster** (pubblicità, il Premium è a pagamento: l'utente non vuole pagare) e **AirScreen** (duplica lo schermo: qualità più bassa e telefono sempre acceso).
- Il Pixel trasmette solo con Google Cast e la Fire TV non lo supporta in modo nativo: per questo serve tutto questo giro.
- App per uso personale, minSdk 33, nessuna dipendenza oltre alla libreria standard di Kotlin, UI costruita in codice in un'unica Activity.

## Trappole note
- Il rilevamento del video guarda solo l'estensione del path. Se un sito serve la playlist senza `.m3u8`, va riconosciuta dal Content-Type.
- Il blocco dei redirect lascia passare solo il dominio corrente ("ultime due etichette" dell'host). I redirect HTTP sono ammessi apposta, perché i siti cambiano spesso dominio.
- `targetSdk 35` forza l'edge-to-edge: il padding degli insets nella root serve.
