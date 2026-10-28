package it.franc.casttokodi

// Scorciatoie tenendo premuta l'icona dell'app (2026-10-28, mockup approvato da Pippo insieme al widget): Accendi la TV,
// Telecomando, Riprendi <serie> e Coda di Kodi. Quali e con che nomi lo decide appShortcuts, senza android.*: lo prova
// ShortcutsTest. Le pubblica l'Activity con ShortcutManager (publishShortcuts), all'avvio e quando cambiano le serie.

// Android ne mostra di solito 4: l'ordine è quello del menu, dall'alto
const val MAX_SHORTCUTS = 4

enum class ShortcutKind { WAKE, REMOTE, RESUME, QUEUE }

// short: il nome breve (sotto l'icona trascinata sulla Home); long: quello nel menu. show: la serie, solo per RESUME
class AppShortcut(val kind: ShortcutKind, val short: String, val long: String, val show: String? = null) {
    // Id stabile per Android: la serie cambia, la scorciatoia "Riprendi" resta la stessa
    val id: String get() = kind.name.lowercase()
}

fun appShortcuts(kodiChosen: Boolean, shows: List<Show>): List<AppShortcut> {
    // Senza Kodi scelto le scorciatoie non avrebbero niente da comandare: l'app si apre con il tocco normale
    if (!kodiChosen) return emptyList()
    val show = shows.firstOrNull { it.upNext != null }
    return listOfNotNull(
        AppShortcut(ShortcutKind.WAKE, tr("Accendi TV", "TV on"), tr("Accendi la TV", "Turn on the TV")),
        AppShortcut(ShortcutKind.REMOTE, tr("Telecomando", "Remote"), tr("Telecomando", "Remote")),
        show?.let {
            AppShortcut(ShortcutKind.RESUME, tr("Riprendi", "Resume"), tr("Riprendi ${it.title}", "Resume ${it.title}").take(25), it.key)
        },
        AppShortcut(ShortcutKind.QUEUE, tr("Coda", "Queue"), tr("Coda di Kodi", "Kodi queue")),
    ).take(MAX_SHORTCUTS)
}
