package it.franc.casttokodi

import java.time.ZoneId

// Timer di spegnimento (2026-10-27, mockup approvato da Pippo): a un'ora scelta Kodi va in pausa, si ferma, o si ferma
// e la Fire TV va in standby (con l'HDMI-CEC si spegne anche la TV). Il timer gira sulla Fire TV, lasciato lì via ADB
// come il pulsante di accensione, così il telefono può spegnersi; senza ADB lo tiene il telefono. Niente android.*:
// lo prova SleepTimerTest.

// Quello che fa allo scadere: i tasti mandati alla Fire TV (Kodi li riceve come quelli del telecomando)
enum class TimerAction(val id: String, val label: String, val keys: List<String>) {
    PAUSE("pause", "Pausa", listOf("KEYCODE_MEDIA_PAUSE")),
    STOP("stop", "Ferma il video", listOf("KEYCODE_MEDIA_STOP")),
    TV_OFF("tv", "Spegni la TV", listOf("KEYCODE_MEDIA_STOP", "KEYCODE_SLEEP")),
}

// Ferma e spegni la TV: la proposta del mockup approvata da Pippo
val DEFAULT_TIMER_ACTION = TimerAction.TV_OFF

fun timerAction(id: String?): TimerAction = TimerAction.entries.firstOrNull { it.id == id } ?: DEFAULT_TIMER_ACTION

// File sulla Fire TV con il codice del timer in corso: un timer nuovo lo sovrascrive e quello vecchio, al risveglio,
// vede che non è più il suo e non fa niente; Annulla lo cancella
const val TIMER_FILE = "/data/local/tmp/couchcast-timer"

// "Fine di questo episodio" scatta poco prima della fine, così Kodi non fa in tempo a partire con il successivo
const val TIMER_END_MARGIN = 3L

// +15 min del timer attivo
const val TIMER_EXTEND = 15 * 60L

val TIMER_MINUTES = listOf(30, 60)

// token: solo cifre, finisce dentro un comando di shell. nohup: il comando resta vivo quando l'app chiude il
// collegamento ADB (ipotesi, da provare sulla Fire TV vera)
fun timerCommand(seconds: Long, action: TimerAction, token: Long): String {
    val keys = action.keys.joinToString("; sleep 1; ") { "input keyevent $it" }
    val script = "echo $token > $TIMER_FILE; sleep ${seconds.coerceAtLeast(1)}; " +
        "[ \"\$(cat $TIMER_FILE 2>/dev/null)\" = \"$token\" ] || exit 0; rm -f $TIMER_FILE; $keys"
    return "nohup sh -c '$script' </dev/null >/dev/null 2>&1 &"
}

fun cancelTimerCommand(): String = "rm -f $TIMER_FILE"

// Una scelta del pannello: nome, riga sotto con l'ora e fra quanti secondi scatta (null = da scrivere a mano)
class TimerChoice(val title: String, val detail: String?, val seconds: Long?)

fun timerChoices(playing: NowPlaying?, state: KodiState?, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<TimerChoice> {
    fun at(seconds: Long) = "alle ${clockAt(now + seconds, zone)}"
    val list = mutableListOf<TimerChoice>()
    if (playing?.playerId != null && playing.total > 0) {
        val left = (playing.total - playing.time - TIMER_END_MARGIN).coerceAtLeast(1)
        list += TimerChoice("Fine di questo episodio", at(left), left)
        if (state != null && state.fromQueue && state.queueLength > state.position + 1) {
            val after = state.queueLength - state.position - 1
            val end = (state.remaining + after * state.episode - TIMER_END_MARGIN).coerceAtLeast(1)
            list += TimerChoice("Fine della coda", "${if (after == 1) "1 episodio" else "$after episodi"} dopo questo, ${at(end)}", end)
        }
    }
    TIMER_MINUTES.forEach { minutes ->
        val seconds = minutes * 60L
        list += TimerChoice(if (minutes % 60 == 0) "Tra ${minutes / 60} or${if (minutes == 60) "a" else "e"}" else "Tra $minutes minuti", at(seconds), seconds)
    }
    list += TimerChoice("Altro tempo…", "In minuti", null)
    return list
}

// I minuti scritti a mano: da 1 a 12 ore
fun parseTimerMinutes(input: String): Int? = input.trim().toIntOrNull()?.takeIf { it in 1..720 }

// Riga del timer attivo nel telecomando e nella barra di Kodi
fun timerLabel(action: TimerAction, at: Long, zone: ZoneId = ZoneId.systemDefault()): String = when (action) {
    TimerAction.PAUSE -> "Pausa alle ${clockAt(at, zone)}"
    TimerAction.STOP -> "Si ferma alle ${clockAt(at, zone)}"
    TimerAction.TV_OFF -> "Si spegne alle ${clockAt(at, zone)}"
}
